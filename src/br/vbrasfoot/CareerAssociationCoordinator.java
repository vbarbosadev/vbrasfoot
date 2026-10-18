package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Loader-owned metadata review only. Does not discover identity, install command context or mutate gameplay. */
final class CareerAssociationCoordinator {
    enum Status { ASSOCIATION_REQUIRED, ASSOCIATION_CONFLICT, REVISION_REVIEW_REQUIRED, READ_ONLY_LINKED }
    enum Action { CREATE_NEW, FORK_COPY, RENAME_PROVEN, ASSERT_RENAME, ACCEPT_REVISION, RESTORE_PREVIOUS, QUARANTINE_PENDING }
    record JournalReview(String owner, Optional<CareerCommandJournal.Recovery> recovery, Optional<CareerSidecarStore.Reason> failure) { }
    static final class Review {
        private final UUID coordinator;
        private final SaveFileObserver.Observation observed;
        private final LoadedSaveVerifier.Verified decoded;
        private final Optional<CareerSaveAssociations.Association> association;
        private final Optional<CareerSaveAssociations.Match> match;
        private final List<JournalReview> journals;
        private final Status status;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private Review(UUID coordinator, SaveFileObserver.Observation observed, LoadedSaveVerifier.Verified decoded,
                       Optional<CareerSaveAssociations.Association> association, Optional<CareerSaveAssociations.Match> match,
                       List<JournalReview> journals, Status status) {
            this.coordinator = coordinator; this.observed = observed; this.decoded = decoded; this.association = association;
            this.match = match; this.journals = List.copyOf(journals); this.status = status;
        }
        LoadedSaveVerifier.Verified decoded() { return decoded; }
        Optional<CareerSaveAssociations.Association> association() { return association; }
        Optional<CareerSaveAssociations.Match> match() { return match; }
        List<JournalReview> journals() { return journals; }
        Status status() { return status; }
    }
    private final UUID instance = UUID.randomUUID();
    private final Path saves;
    private final CareerSaveAssociations associations;
    private final CareerSidecarStore journalStore;
    private final SaveFileObserver observer;
    private final LoadedSaveVerifier verifier;
    private final List<String> owners;
    private final AtomicBoolean mutating = new AtomicBoolean();
    CareerAssociationCoordinator(Path saves, Path metadata, LegacyRuntime runtime, Collection<String> owners) {
        this(saves, new CareerSaveAssociations(metadata.resolve("associations")), new CareerSidecarStore(metadata.resolve("journals")),
                new SaveFileObserver(), new LoadedSaveVerifier(runtime), owners);
    }
    CareerAssociationCoordinator(Path saves, CareerSaveAssociations associations, CareerSidecarStore journalStore,
                                 SaveFileObserver observer, LoadedSaveVerifier verifier, Collection<String> owners) {
        this.saves = Objects.requireNonNull(saves).toAbsolutePath();
        if (!this.saves.equals(this.saves.normalize())) throw new IllegalArgumentException("Normalized save directory required");
        this.associations = Objects.requireNonNull(associations); this.journalStore = Objects.requireNonNull(journalStore);
        this.observer = Objects.requireNonNull(observer); this.verifier = Objects.requireNonNull(verifier);
        if (owners == null || owners.size() > 64 || new HashSet<>(owners).size() != owners.size()) throw new IllegalArgumentException("At most64 distinct explicitly configured owners required");
        for (String owner : owners) new CareerSidecarStore.Key(instance, instance, owner);
        this.owners = owners.stream().sorted().toList();
    }
    Review review(Optional<CareerSaveAssociations.Handle> selected) throws IOException, InterruptedException {
        offEdt(); Objects.requireNonNull(selected);
        var binding = HookProbe.decodedProvenance().binding().orElseThrow(() ->
                new LoadedSaveVerifier.Failure(LoadedSaveVerifier.Reason.NO_PROVENANCE, "Carregue um save existente com a sonda; sem origem decodificada aceita"));
        CareerSidecarStore.plain(binding.source()); CareerSidecarStore.plain(saves);
        if (!binding.source().getParent().toRealPath().equals(saves.toRealPath()))
            throw new LoadedSaveVerifier.Failure(LoadedSaveVerifier.Reason.FILE_MISMATCH, "Origem decodificada fora do diretorio de saves selecionado");
        var observed = observer.observe(saves, binding.source().getFileName().toString());
        var decoded = verifier.verify(observed);
        Optional<CareerSaveAssociations.Association> association = selected.isEmpty() ? Optional.empty() : Optional.of(associations.inspect(selected.orElseThrow()));
        Optional<CareerSaveAssociations.Match> match = association.map(value -> associations.assess(value, observed));
        var journals = new ArrayList<JournalReview>();
        if (association.isPresent()) {
            var known = association.orElseThrow();
            for (String owner : owners) {
                try { journals.add(new JournalReview(owner, Optional.of(journal(known.handle(), owner).inspectRecovery(known.save(), observed.revision())), Optional.empty())); }
                catch (CareerSidecarStore.Failure failure) { journals.add(new JournalReview(owner, Optional.empty(), Optional.of(failure.reason()))); }
            }
            if (!associations.inspect(known.handle()).token().equals(known.token())) throw conflict("Associacao mudou durante revisao");
        }
        if (!decoded.equals(verifier.verify(observed))) throw conflict("Carregamento mudou durante revisao");
        boolean metadataChanged = association.isPresent() && (!association.orElseThrow().modified().equals(observed.modified())
                || !association.orElseThrow().created().equals(observed.created()));
        Status status = match.isEmpty() ? Status.ASSOCIATION_REQUIRED
                : match.orElseThrow() == CareerSaveAssociations.Match.REVISION_CHANGED ? Status.REVISION_REVIEW_REQUIRED
                : metadataChanged ? Status.ASSOCIATION_CONFLICT
                : match.orElseThrow() == CareerSaveAssociations.Match.EXACT_OBSERVATION || match.orElseThrow() == CareerSaveAssociations.Match.FILE_IDENTITY_UNAVAILABLE
                ? Status.READ_ONLY_LINKED : Status.ASSOCIATION_CONFLICT;
        return new Review(instance, observed, decoded, association, match, journals, status);
    }
    /** Call only after showing this exact review and receiving an explicit action-specific human confirmation.
     * One-shot even on error. A returned handle is metadata identity, never game readiness. */
    CareerSaveAssociations.Handle confirm(Review review, Action action, Optional<String> journalOwner) throws IOException, InterruptedException {
        offEdt(); Objects.requireNonNull(action); Objects.requireNonNull(journalOwner);
        if (review == null || !instance.equals(review.coordinator)) throw new IllegalArgumentException("Foreign review");
        if (!mutating.compareAndSet(false, true)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.BUSY, "Outra confirmacao ainda esta em curso");
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao ja consumida; verificar novamente antes de confirmar");
            if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Carreira recarregada desde a revisao; confirmar de novo");
            var selected = review.association;
            if (selected.isPresent() && !associations.inspect(selected.orElseThrow().handle()).token().equals(selected.orElseThrow().token()))
                throw conflict("Associacao mudou desde a revisao");
            if (action == Action.CREATE_NEW) {
                if (selected.isPresent()) throw conflict("Criar IDs novos requer nenhuma associacao selecionada; copia deve usar fork explicito");
                return associations.create(review.observed, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER).handle();
            }
            var known = selected.orElseThrow(() -> new IllegalArgumentException("Select an explicit association handle first"));
            var handle = known.handle(); String token = known.token();
            return switch (action) {
                case FORK_COPY -> associations.fork(handle, token, review.observed, CareerSaveAssociations.Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA).handle();
                case RENAME_PROVEN -> associations.rename(handle, token, review.observed, CareerSaveAssociations.Confirmation.RENAME_SAME_FILE).handle();
                case ASSERT_RENAME -> associations.rename(handle, token, review.observed, CareerSaveAssociations.Confirmation.ASSERT_RENAME_WITHOUT_FILE_IDENTITY).handle();
                case ACCEPT_REVISION -> associations.acceptRevision(handle, token, review.observed, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION).handle();
                case RESTORE_PREVIOUS -> associations.restorePrevious(handle, token, review.observed, CareerSaveAssociations.Confirmation.RESTORE_RECORDED_PREVIOUS).handle();
                case QUARANTINE_PENDING -> {
                    String owner = journalOwner.orElseThrow(() -> new IllegalArgumentException("Explicit journal owner required"));
                    var row = review.journals.stream().filter(value -> value.owner().equals(owner)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Journal owner not present in reviewed registry"));
                    var recovery = row.recovery().orElseThrow(() -> conflict("Diario indisponivel ou ancora divergente; nao ignorar erro"));
                    journal(handle, owner).quarantinePending(known.save(), recovery.snapshot().token());
                    yield handle;
                }
                default -> throw new IllegalArgumentException("Unsupported confirmation");
            };
        } finally { mutating.set(false); }
    }
    private CareerCommandJournal journal(CareerSaveAssociations.Handle handle, String owner) {
        return new CareerCommandJournal(journalStore, new CareerSidecarStore.Key(handle.career(), handle.binding(), owner));
    }
    private static CareerSidecarStore.Failure conflict(String message) { return new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, message); }
    private static void offEdt() throws CareerSidecarStore.Failure {
        if (SwingUtilities.isEventDispatchThread()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.WRONG_THREAD, "Review career metadata off EDT");
    }
}
