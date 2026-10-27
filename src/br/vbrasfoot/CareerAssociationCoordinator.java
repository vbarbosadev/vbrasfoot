package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Loader-owned metadata/contract review and explicit internal context preparation; never gameplay mutation. */
final class CareerAssociationCoordinator {
    enum Status { ASSOCIATION_REQUIRED, ASSOCIATION_CONFLICT, REVISION_REVIEW_REQUIRED, READ_ONLY_LINKED }
    enum Action { CREATE_NEW, FORK_COPY, RENAME_PROVEN, ASSERT_RENAME, ACCEPT_REVISION, RESTORE_PREVIOUS, QUARANTINE_PENDING,
        REANCHOR_NO_EFFECT_JOURNAL, RECONCILE_PROVEN_NO_EFFECT, PREPARE_INTERNAL_CONTEXT }
    record JournalReview(String owner, boolean configured, Optional<CareerCommandJournal.Recovery> recovery, Optional<CareerSidecarStore.Reason> failure) { }
    record RequestReview(String owner, boolean configured, Optional<FinanceRequestStore.Recovery> recovery,
                         Optional<CareerSidecarStore.Reason> failure, List<FinanceContractReview.Link> links) {
        RequestReview { links = List.copyOf(links); }
    }
    record ExecutionReview(String owner, boolean configured, Optional<FinanceExecutionWitnessStore.Recovery> recovery,
                           Optional<CareerSidecarStore.Reason> failure, List<FinanceExecutionRecovery.Operation> operations) {
        ExecutionReview { operations = List.copyOf(operations); }
    }
    record LoanReview(String owner, boolean configured, Optional<LoanRequestStore.Recovery> recovery,
                      Optional<CareerSidecarStore.Reason> failure, List<LoanContractReview.Link> links) {
        LoanReview { links = List.copyOf(links); }
    }
    static final class Review {
        private final UUID coordinator;
        private final SaveFileObserver.Observation observed;
        private final LoadedSaveVerifier.Verified decoded;
        private final Optional<CareerSaveAssociations.Association> association;
        private final Optional<CareerSaveAssociations.Match> match;
        private final List<JournalReview> journals;
        private final List<RequestReview> requests;
        private final List<ExecutionReview> executions;
        private final List<LoanReview> loans;
        private final Status status;
        private final Optional<CareerSidecarStore.NamespaceInventory> inventory;
        private final Optional<CareerSidecarStore.NamespaceInventory> requestInventory;
        private final Optional<CareerSidecarStore.NamespaceInventory> executionInventory;
        private final Optional<CareerSidecarStore.NamespaceInventory> loanInventory;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private Review(UUID coordinator, SaveFileObserver.Observation observed, LoadedSaveVerifier.Verified decoded,
                       Optional<CareerSaveAssociations.Association> association, Optional<CareerSaveAssociations.Match> match,
                        List<JournalReview> journals, List<RequestReview> requests, Status status,
                         Optional<CareerSidecarStore.NamespaceInventory> inventory, Optional<CareerSidecarStore.NamespaceInventory> requestInventory,
                          List<ExecutionReview> executions, Optional<CareerSidecarStore.NamespaceInventory> executionInventory,
                          List<LoanReview> loans, Optional<CareerSidecarStore.NamespaceInventory> loanInventory) {
            this.coordinator = coordinator; this.observed = observed; this.decoded = decoded; this.association = association;
            this.match = match; this.journals = List.copyOf(journals); this.status = status;
            this.inventory = inventory; this.requests = List.copyOf(requests); this.requestInventory = requestInventory;
            this.executions = List.copyOf(executions); this.executionInventory = executionInventory;
            this.loans = List.copyOf(loans); this.loanInventory = loanInventory;
        }
        LoadedSaveVerifier.Verified decoded() { return decoded; }
        Optional<CareerSaveAssociations.Association> association() { return association; }
        Optional<CareerSaveAssociations.Match> match() { return match; }
        List<JournalReview> journals() { return journals; }
        List<RequestReview> requests() { return requests; }
        List<ExecutionReview> executions() { return executions; }
        List<LoanReview> loans() { return loans; }
        Status status() { return status; }
    }
    private final UUID instance = UUID.randomUUID();
    private final Path saves;
    private final CareerSaveAssociations associations;
    private final CareerSidecarStore journalStore;
    private final CareerSidecarStore requestStore;
    private final CareerSidecarStore executionStore;
    private final CareerSidecarStore loanStore;
    private final CareerSidecarStore continuationStore;
    private final CareerSidecarStore sponsorActivationStore;
    private final SaveFileObserver observer;
    private final LoadedSaveVerifier verifier;
    private final List<String> owners;
    private final AtomicBoolean mutating = new AtomicBoolean();
    private volatile CareerOperationalContext operational;
    private TransferWindowService transferWindows;
    CareerAssociationCoordinator transferWindows(TransferWindowService service) {
        if (transferWindows != null) throw new IllegalStateException("Politica ja configurada");
        transferWindows = Objects.requireNonNull(service); return this;
    }
    private void checkLoanWindow(CareerSaveAssociations.Handle handle) throws Exception { if (transferWindows != null) transferWindows.checkLoan(handle); }
    private boolean loanWindowOpen(CareerSaveAssociations.Handle handle) { return transferWindows == null || transferWindows.allowedNow(handle); }
    private CareerCommandGate.Owner loanOwner;
    private CareerCommandGate.Owner sponsorOwner;
    private CareerCommandGate.Owner sponsorEndOwner;
    private record CareerDataSelection(CareerSaveAssociations.Handle handle, UUID token) { }
    private final java.util.concurrent.atomic.AtomicReference<CareerDataSelection> careerDataSelection = new java.util.concurrent.atomic.AtomicReference<>();
    synchronized CareerAssociationCoordinator operational(CareerOperationalContext context) {
        if (operational != null) throw new IllegalStateException("Operational session already assigned");
        operational = Objects.requireNonNull(context); return this;
    }
    CareerOperationalContext.Snapshot operationalSnapshot() {
        return operational == null ? new CareerOperationalContext.Snapshot(CareerOperationalContext.Status.NOT_BOUND, Optional.empty(), Optional.empty(), 0) : operational.snapshot();
    }
    CareerAssociationCoordinator(Path saves, Path metadata, LegacyRuntime runtime, Collection<String> owners) {
        this(saves, new CareerSaveAssociations(metadata.resolve("associations"), saves), new CareerSidecarStore(metadata.resolve("journals")),
                 new CareerSidecarStore(metadata.resolve("finance-requests")), new CareerSidecarStore(metadata.resolve("finance-execution-witnesses")),
                 new CareerSidecarStore(metadata.resolve("loan-requests")), new CareerSidecarStore(metadata.resolve("loan-continuation")),
                 new SaveFileObserver(), new LoadedSaveVerifier(runtime), owners);
    }
    CareerAssociationCoordinator(Path saves, CareerSaveAssociations associations, CareerSidecarStore journalStore,
                                  CareerSidecarStore requestStore, CareerSidecarStore executionStore, SaveFileObserver observer, LoadedSaveVerifier verifier, Collection<String> owners) {
        this(saves, associations, journalStore, requestStore, executionStore, null, observer, verifier, owners);
    }
    CareerAssociationCoordinator(Path saves, CareerSaveAssociations associations, CareerSidecarStore journalStore,
                                 CareerSidecarStore requestStore, CareerSidecarStore executionStore, CareerSidecarStore loanStore,
                                  SaveFileObserver observer, LoadedSaveVerifier verifier, Collection<String> owners) {
        this(saves, associations, journalStore, requestStore, executionStore, loanStore, null, observer, verifier, owners);
    }
    CareerAssociationCoordinator(Path saves, CareerSaveAssociations associations, CareerSidecarStore journalStore,
                                 CareerSidecarStore requestStore, CareerSidecarStore executionStore, CareerSidecarStore loanStore,
                                 CareerSidecarStore continuationStore, SaveFileObserver observer, LoadedSaveVerifier verifier, Collection<String> owners) {
        this.saves = Objects.requireNonNull(saves).toAbsolutePath();
        if (!this.saves.equals(this.saves.normalize())) throw new IllegalArgumentException("Normalized save directory required");
        this.associations = Objects.requireNonNull(associations); this.journalStore = Objects.requireNonNull(journalStore);
        this.requestStore = Objects.requireNonNull(requestStore);
        this.executionStore = Objects.requireNonNull(executionStore);
        this.loanStore = loanStore; // Legacy fixture constructor has no configured loan root; Main always supplies one.
        this.continuationStore = continuationStore;
        this.sponsorActivationStore = new CareerSidecarStore(journalStore.root().getParent().resolve("sponsor-activations"));
        this.observer = Objects.requireNonNull(observer); this.verifier = Objects.requireNonNull(verifier);
        if (owners == null || owners.size() > 64 || new HashSet<>(owners).size() != owners.size()) throw new IllegalArgumentException("At most64 distinct explicitly configured owners required");
        for (String owner : owners) new CareerSidecarStore.Key(instance, instance, owner);
        this.owners = owners.stream().sorted().toList();
    }
    Review review(Optional<CareerSaveAssociations.Handle> selected) throws IOException, InterruptedException {
        return reviewMember(selected, true);
    }
    /** Explicit session-only binding for a mod's external data; never installs a game-write context. */
    CareerSaveAssociations.Handle selectCareerData(Review review) throws IOException, InterruptedException {
        offEdt();
        if (review == null || !instance.equals(review.coordinator)) throw new IllegalArgumentException("Foreign review");
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra revisao/confirmacao esta em andamento");
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao consumida; consulte novamente");
            var association = validateCareerDataReview(review);
            var selected = new CareerDataSelection(association.handle(), UUID.randomUUID());
            careerDataSelection.set(selected);
            try {
                if (!selected.handle().equals(validateCareerDataReview(review).handle()))
                    throw conflict("Associacao mudou durante a selecao de dados");
                return selected.handle();
            } catch (IOException | InterruptedException | RuntimeException failure) {
                careerDataSelection.compareAndSet(selected, null);
                throw failure;
            }
        } finally { mutating.set(false); }
    }
    void clearCareerDataSelection() { careerDataSelection.set(null); }
    boolean careerDataSelected(CareerSaveAssociations.Handle handle) {
        var value = careerDataSelection.get(); return value != null && value.handle().equals(handle);
    }
    boolean careerDataSelected(br.vbrasfoot.api.CareerDataAccess.Scope scope) {
        if (scope == null) return false;
        var value = careerDataSelection.get();
        return value != null && value.handle().career().equals(scope.career())
                && value.handle().binding().equals(scope.binding()) && value.token().equals(scope.selection());
    }
    boolean hasCareerDataSelection() { return careerDataSelection.get() != null; }
    br.vbrasfoot.api.CareerDataAccess.Scope currentCareerDataScope() throws IOException, InterruptedException {
        offEdt(); var selected = careerDataSelection.get();
        if (selected == null) return null;
        Review latest = review(Optional.of(selected.handle()));
        var association = validateCareerDataReview(latest);
        if (!selected.equals(careerDataSelection.get())) throw conflict("Selecao de carreira mudou; consulte novamente");
        return new br.vbrasfoot.api.CareerDataAccess.Scope(selected.handle().career(), selected.handle().binding(), selected.token(),
                latest.decoded.bytes().sha256(), latest.decoded.bytes().size());
    }
    void validateCareerDataScope(br.vbrasfoot.api.CareerDataAccess.Scope scope) throws IOException, InterruptedException {
        Objects.requireNonNull(scope); var selected = careerDataSelection.get();
        if (selected == null || !selected.handle().career().equals(scope.career()) || !selected.handle().binding().equals(scope.binding())
                || !selected.token().equals(scope.selection())) throw conflict("Dados de mod recusados: associacao nao esta selecionada nesta sessao");
        var current = currentCareerDataScope();
        if (!Optional.of(scope).equals(Optional.ofNullable(current))) throw conflict("Save/revisao mudou; releia a associacao e use um novo escopo");
    }
    private CareerSaveAssociations.Association validateCareerDataReview(Review review) throws IOException, InterruptedException {
        if (review.status() != Status.READ_ONLY_LINKED || !review.decoded.initializationPathObserved())
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                    "Dados por carreira exigem associacao exata e carga original observada nesta sessao");
        var association = review.association().orElseThrow(() -> conflict("Associe explicitamente o save antes de usar dados por carreira"));
        var handle = association.handle();
        if (!association.save().equals(review.decoded.bytes()) || !review.decoded.equals(verifier.verify(review.observed))
                || !associations.inspect(handle).token().equals(association.token())
                || !review.inventory.orElseThrow().equals(journalStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.loanInventory.orElseThrow().equals(loanInventory(handle)))
            throw conflict("Save, associacao ou inventario mudou; revise antes de usar dados por carreira");
        return association;
    }
    enum UnknownCareerChoice { NEW_CAREER, CONTINUATION, CANCEL }
    @FunctionalInterface interface UnknownCareerPrompt {
        UnknownCareerChoice choose(Path source, List<CareerSaveAssociations.Association> known) throws Exception;
    }
    /** Shared metadata identity for sponsors and native loans. No financial operation or context is installed. */
    CareerSaveAssociations.Handle resolveLoadedCareer(Review first, UnknownCareerPrompt prompt) throws Exception {
        offEdt();
        if (first == null || !instance.equals(first.coordinator) || !first.decoded.initializationPathObserved() || first.decoded.backup())
            throw conflict("Carregue o save principal da carreira antes de vincular os mods");
        if (!first.decoded.equals(verifier.verify(first.observed))) throw conflict("Carreira mudou antes da identificacao");
        preparePortableCareerLocation(first);
        var group = associations.group(first.decoded.source());
        if (!group.members().isEmpty()) return group.primary();
        var inventory = associations.inventory();
        Path directory = first.decoded.source().getParent();
        if (inventory.stream().anyMatch(a -> !a.directory().equals(directory)))
            throw conflict("Historicos apontam para outra pasta. Revise a copia/mudanca em Carreira e diarios; nenhum vinculo novo criado");
        // A matching digest or physical file is evidence against an independent career, not proof of a new identity.
        if (inventory.stream().anyMatch(a -> a.save().equals(first.decoded.bytes())
                || !a.fileKey().isEmpty() && a.fileKey().equals(first.observed.fileKey())))
            throw conflict("Possivel copia ou renomeacao de carreira conhecida. Revise em Carreira e diarios para preservar pagamentos; nao criar historico vazio");
        UnknownCareerChoice choice = Objects.requireNonNull(prompt.choose(first.decoded.source(), List.copyOf(inventory)));
        if (choice == UnknownCareerChoice.CANCEL) throw conflict("Identificacao cancelada; historicos preservados");
        if (choice == UnknownCareerChoice.CONTINUATION)
            throw conflict("Copia/continuacao nao sera tratada como carreira nova. Abra vbrasfoot > Carreira e diarios para revisar o vinculo anterior; pagamentos preservados");
        if (!inventory.equals(associations.inventory()) || !first.decoded.equals(verifier.verify(first.observed)))
            throw conflict("Save ou vinculos mudaram durante a confirmacao; revise novamente");
        return confirm(first, Action.CREATE_NEW, Optional.empty());
    }
    CareerSaveAssociations.Handle resolveLoadedCareer(Review first) throws Exception {
        return resolveLoadedCareer(first, (source, known) -> CareerBridge.onEdt(() -> {
            Object[] options = {"Nova carreira independente", "Copia / continuacao", "Cancelar"};
            int answer = javax.swing.JOptionPane.showOptionDialog(null,
                    "Este save ainda nao tem vinculo com os mods:\n" + source.getFileName()
                    + "\n\nE uma NOVA carreira criada no jogo ou uma copia/Salvar como de outra?"
                    + "\nNova carreira: cria dados separados para patrocinio e emprestimos."
                    + "\nNao escolha nova carreira para repetir pagamentos ou zerar historicos."
                    + "\nNenhum dinheiro, jogador ou estadio sera alterado nesta etapa.",
                    "Identificar carreira para os mods", javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE, null, options, options[2]);
            return answer == 0 ? UnknownCareerChoice.NEW_CAREER : answer == 1
                    ? UnknownCareerChoice.CONTINUATION : UnknownCareerChoice.CANCEL;
        }));
    }
    /** Recognized paths reuse their identity; only unknown careers/revision changes need confirmation. */
    br.vbrasfoot.api.CareerDataAccess.Scope bindSponsorCareer() throws Exception {
        offEdt(); var first = review(Optional.empty());
        Optional<CareerSaveAssociations.Handle> handle = Optional.of(resolveLoadedCareer(first));
        var selected = review(handle);
        if (selected.status != Status.READ_ONLY_LINKED) {
            boolean metadataOnly = selected.status == Status.ASSOCIATION_CONFLICT && selected.match.isPresent()
                    && (selected.match.orElseThrow() == CareerSaveAssociations.Match.METADATA_CHANGED
                    || selected.match.orElseThrow() == CareerSaveAssociations.Match.FILE_IDENTITY_UNAVAILABLE
                    || selected.match.orElseThrow() == CareerSaveAssociations.Match.FILE_REPLACED_OR_COPIED);
            if (selected.status != Status.REVISION_REVIEW_REQUIRED && !metadataOnly) throw conflict("Associacao exige revisao manual; nenhum efeito repetido");
            final var changed = selected;
            boolean accept = CareerBridge.onEdt(() -> javax.swing.JOptionPane.showConfirmDialog(null,
                    "A revisao do save mudou. Usar o carregamento atual como referencia de dados?\n"
                    + "Historicos de pagamentos serao preservados; valores antigos NAO serao repetidos.\n"
                    + changed.decoded.source().getFileName(), "Revisar save atual", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE) == javax.swing.JOptionPane.OK_OPTION);
            if (!accept) throw conflict("Revisao cancelada");
            // Association metadata only: preserve every journal, activation tombstone and grouped loan history unchanged.
            if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
            try {
                if (!selected.consumed.compareAndSet(false, true) || !selected.decoded.equals(verifier.verify(selected.observed)))
                    throw conflict("Save mudou durante confirmacao de revisao");
                var associated = selected.association.orElseThrow();
                associations.acceptRevision(associated.handle(), associated.token(), selected.observed,
                        CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION);
            } finally { mutating.set(false); }
            selected = review(handle);
        }
        selectCareerData(selected); return currentCareerDataScope();
    }
    /** User asserts a complete installation move/copy, not a new career or a financial reconciliation. */
    void preparePortableCareerLocation(Review review) throws Exception {
        offEdt();
        if (review == null || !instance.equals(review.coordinator) || !review.decoded.initializationPathObserved())
            throw conflict("Carregue uma carreira existente antes de vincular a pasta portatil");
        if (!associations.group(review.decoded.source()).members().isEmpty()) {
            associations.rememberCurrentInstallation(review.decoded.source().getParent()); return;
        }
        var inventory = associations.inventory();
        if (inventory.isEmpty()) return;
        var directories = inventory.stream().map(CareerSaveAssociations.Association::directory).distinct().toList();
        Path currentDirectory = review.decoded.source().getParent();
        // A different filename in the same installation may be a genuinely new career, not a relocation.
        if (directories.size() == 1 && directories.get(0).equals(currentDirectory)) return;
        if (directories.size() != 1 || directories.get(0).equals(currentDirectory)
                || inventory.stream().noneMatch(a -> a.name().equals(review.decoded.source().getFileName().toString())))
            throw conflict("Pasta/save ambiguo: revise as associacoes em Carreira e diarios; historico preservado");
        Path legacy = directories.get(0);
        boolean confirmed = CareerBridge.onEdt(() -> javax.swing.JOptionPane.showConfirmDialog(null,
                "Esta pasta do jogo foi movida ou copiada COM os dados do loader?\n"
                + "Local antigo: " + legacy + "\nLocal atual: " + currentDirectory
                + "\n\nVincular os mesmos IDs e TODOS os historicos a esta instalacao?"
                + "\nNao cria carreira vazia, nao paga e nao repete creditos."
                + "\nConfirme somente se copiou/moveu a pasta completa, incluindo vbrasfoot/data."
                + "\nDepois disso, os vinculos acompanharao a pasta para outro local/PC.",
                "Confirmar pasta portatil", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE) == javax.swing.JOptionPane.OK_OPTION);
        if (!confirmed) throw conflict("Vinculo portatil cancelado; nenhum historico alterado");
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
        try {
            if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Carreira mudou durante confirmacao da pasta");
            associations.enablePortable(legacy);
        } finally { mutating.set(false); }
    }
    static final class SponsorQuote {
        final br.vbrasfoot.api.CareerDataAccess.Scope scope;
        final Review review; final FinanceRequest request; final String terms, beforeStadium, afterStadium;
        final UUID sponsor; final SponsorActivationStore.Snapshot ledger; final NativeHistory history;
        final Optional<SponsorOfferStore.Offer> offer;
        final AtomicBoolean consumed = new AtomicBoolean();
        SponsorQuote(br.vbrasfoot.api.CareerDataAccess.Scope scope, Review review, UUID sponsor, FinanceRequest request, String terms, String before, String after,
                      SponsorActivationStore.Snapshot ledger, NativeHistory history, Optional<SponsorOfferStore.Offer> offer) {
            this.scope = scope; this.review = review; this.sponsor = sponsor; this.request = request; this.terms = terms;
            beforeStadium = before; afterStadium = after; this.ledger = ledger; this.history = history;
            this.offer = offer;
        }
    }
    SponsorQuote quoteSponsor(br.vbrasfoot.api.CareerDataAccess.Scope scope, UUID sponsor, int clubId, String clubName,
                            String sponsorName, int amount, String stadiumName, LegacySponsorAdapter adapter, LegacyLoanAdapter loans) throws Exception {
        offEdt(); requireNativeSession(); validateCareerDataScope(scope);
        var handle = new CareerSaveAssociations.Handle(scope.career(), scope.binding());
        var current = review(Optional.of(handle)); validateSponsorReview(current);
        var ledger = new SponsorActivationStore(sponsorActivationStore, handle).read();
        if (ledger.entries().stream().anyMatch(e -> e.sponsor().equals(sponsor)) || !ledger.permitsNextSponsor())
            throw conflict("Patrocinio ja tentado ou historico aplicado/incerto. Nunca repetir credito, mesmo apos reabrir");
        var offer = offers(handle).read().entries().stream().filter(e -> e.sponsor().equals(sponsor)).findFirst();
        if (offer.isPresent()) {
            var e = offer.orElseThrow();
            if (e.cancelled() || e.clubId() != clubId || !e.club().equals(clubName) || !e.name().equals(sponsorName)
                    || e.credit() != amount || !e.stadium().equals(stadiumName)) throw conflict("Mensagem cancelada ou termos diferentes; nao creditar");
        } else if (hasUnpaidOffer(handle, ledger)) throw conflict("Ha mensagem de patrocinio pendente; receba ou cancele antes de outro contrato");
        var history = nativeHistory(current, continuation(handle).read(), true);
        var captured = CareerBridge.onEdt(() -> {
            checkHistoricalLoans(loans, history.active, history.retired);
            Object club = adapter.finance.humanClub(clubId, clubName);
            var request = new FinanceRequest(handle.career(), handle.binding(), current.decoded.bytes(), SponsorActivationStore.OWNER,
                    SponsorActivationStore.key(sponsor), new FinanceRequest.Club(((List<?>) CareerBridge.call(adapter.finance.roots().primary(), "P")).indexOf(club),
                    clubId, clubName), FinanceDelta.Direction.CREDIT_RAW_6, amount, adapter.finance.snapshot(club));
            return new Object[] {request, adapter.stadium(club)};
        });
        String before = (String) captured[1], after = stadiumName.isBlank() ? before : stadiumName.trim();
        if (after.length() > 120 || after.isBlank() || before == null || before.length() > 120) throw new IllegalArgumentException("Nome de estadio invalido (1..120)");
        var request = (FinanceRequest) captured[0];
        String terms = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                (request.digest() + "\n" + sponsorName + "\n" + before + "\n" + after).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        validateCareerDataScope(scope);
        return new SponsorQuote(scope, current, sponsor, request, terms, before, after, ledger, history, offer);
    }
    SponsorActivationPipeline.Result executeSponsor(SponsorQuote quote, LegacySponsorAdapter adapter, LegacyLoanAdapter loans) throws Exception {
        offEdt(); requireNativeSession();
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
        CareerCommandGate.Preparation preparation = null;
        try {
            if (quote == null || !quote.consumed.compareAndSet(false, true)) throw conflict("Cotacao consumida; nao repetir");
            validateCareerDataScope(quote.scope);
            var handle = validateSponsorReview(quote.review);
            var ledger = new SponsorActivationStore(sponsorActivationStore, handle);
            if (!ledger.read().token().equals(quote.ledger.token())) throw conflict("Historico de patrocinio mudou");
            if (!offers(handle).read().entries().stream().filter(e -> e.sponsor().equals(quote.sponsor)).findFirst().equals(quote.offer))
                throw conflict("Mensagem mudou ou foi cancelada desde a cotacao; nenhum credito solicitado");
            var history = nativeHistory(quote.review, continuation(handle).read(), true);
            if (!history.sources.equals(quote.history.sources)) throw conflict("Historico de carreira mudou");
            var nativeMessages = quote.offer.isPresent() ? adapter.nativeMessages() : null;
            if (nativeMessages != null) {
                if (!CareerBridge.onEdt(() -> nativeMessages.present(quote.offer.orElseThrow()))) throw conflict("Mensagem nao esta mais no save carregado; nenhum credito solicitado");
            }
            var handler = CareerBridge.onEdt(() -> {
                checkHistoricalLoans(loans, history.active, history.retired);
                return adapter.handler(quote.request, quote.beforeStadium, quote.afterStadium, () -> careerDataSelected(quote.scope),
                        () -> { if (nativeMessages != null) nativeMessages.update(quote.offer.orElseThrow(), br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY, false); });
            });
            preparation = operational.gate().prepareSponsorContinuation(history.accounted, completedSponsorIntents(handle));
            var context = verifier.installReviewedContext(quote.review.decoded, handle, operational.gate(), preparation);
            operational.accepted(context);
            if (sponsorOwner == null) sponsorOwner = operational.gate().owner(SponsorActivationStore.OWNER, Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var root = sponsorActivationStore.root().getParent().resolve("sponsor-operations").resolve(quote.sponsor.toString());
            var key = new CareerSidecarStore.Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER);
            var ownJournal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journals")), key);
            var ownRequests = new FinanceRequestStore(new CareerSidecarStore(root.resolve("requests")), key);
            var ownWitness = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("witnesses")), key);
            var pipeline = new SponsorActivationPipeline(operational.gate(), ownJournal, ownRequests, ownWitness, ledger,
                    adapter.finance::roots, java.time.Duration.ofSeconds(10));
            return pipeline.execute(quote.sponsor, quote.terms, sponsorOwner, context, quote.request, quote.ledger.token(),
                    ownJournal.read(context.save()).token(), ownRequests.read(context.save()).token(), ownWitness.read(context.save()).token(),
                    handler, quote.beforeStadium, quote.afterStadium);
        } finally {
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    br.vbrasfoot.api.SponsorAccess.State sponsorState(br.vbrasfoot.api.CareerDataAccess.Scope scope, UUID sponsor) throws Exception {
        validateCareerDataScope(scope);
        var ledger = new SponsorActivationStore(sponsorActivationStore, new CareerSaveAssociations.Handle(scope.career(), scope.binding())).read();
        var offer = offers(new CareerSaveAssociations.Handle(scope.career(), scope.binding())).read().entries().stream()
                .filter(e -> e.sponsor().equals(sponsor)).findFirst();
        return sponsorMessageState(ledger, sponsor, offer);
    }
    static br.vbrasfoot.api.SponsorAccess.State sponsorMessageState(SponsorActivationStore.Snapshot ledger, UUID sponsor,
                                                                   Optional<SponsorOfferStore.Offer> offer) {
        var recorded = ledger.entries().stream().filter(e -> e.sponsor().equals(sponsor)).findFirst().map(e -> e.ending().isPresent()
                ? e.ending().orElseThrow().state() == SponsorActivationStore.EndState.ENDED ? br.vbrasfoot.api.SponsorAccess.State.ENDED
                    : br.vbrasfoot.api.SponsorAccess.State.REVIEW_REQUIRED : switch (e.state()) {
            case MEMORY_APPLIED -> br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY;
            case NO_EFFECT -> br.vbrasfoot.api.SponsorAccess.State.NO_EFFECT;
            default -> br.vbrasfoot.api.SponsorAccess.State.REVIEW_REQUIRED;
        });
        if (recorded.isPresent()) return recorded.orElseThrow();
        return offer.isEmpty() ? br.vbrasfoot.api.SponsorAccess.State.NOT_ACTIVATED : offer.orElseThrow().cancelled()
                ? br.vbrasfoot.api.SponsorAccess.State.ENDED : br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT;
    }
    private SponsorOfferStore offers(CareerSaveAssociations.Handle handle) {
        return new SponsorOfferStore(new CareerSidecarStore(sponsorActivationStore.root().getParent().resolve("sponsor-offers")), handle);
    }
    void updateNativeSponsorMessage(br.vbrasfoot.api.CareerDataAccess.Scope scope, SponsorOfferStore.Offer offer,
                                   br.vbrasfoot.api.SponsorAccess.State state, boolean create, NativeSponsorSaveMessage nativeMessages) throws Exception {
        offEdt(); requireNativeSession(); validateCareerDataScope(scope);
        var binding = HookProbe.decodedProvenance().binding().orElseThrow(() -> conflict("Save sem origem observada"));
        CareerBridge.onEdt(() -> {
            operational.gate().updateSponsorMessage(() -> {
                if (!careerDataSelected(scope) || !binding.bytes().sha256().equals(scope.saveSha256())
                        || !HookProbe.currentProvenance(binding, nativeMessages.rootsPrimary(), nativeMessages.rootsSecondary()))
                    throw conflict("Carreira mudou antes da mensagem; nenhum registro criado");
                nativeMessages.update(offer, state, create);
            });
            return null;
        });
    }
    private boolean hasUnpaidOffer(CareerSaveAssociations.Handle handle, SponsorActivationStore.Snapshot ledger) throws IOException {
        return offers(handle).read().entries().stream().anyMatch(e -> !e.cancelled()
                && ledger.entries().stream().noneMatch(a -> a.sponsor().equals(e.sponsor())));
    }
    void publishSponsorOffer(br.vbrasfoot.api.CareerDataAccess.Scope scope, SponsorOfferStore.Offer offer) throws Exception {
        offEdt();
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
        try {
            validateCareerDataScope(scope);
            var review = review(Optional.of(new CareerSaveAssociations.Handle(scope.career(), scope.binding())));
            var handle = validateSponsorReview(review);
            for (var member : primaryGroup(review.association.orElseThrow()).members()) {
                var ledger = new SponsorActivationStore(sponsorActivationStore, member.handle()).read();
                if (!ledger.permitsNextSponsor() || hasUnpaidOffer(member.handle(), ledger))
                    throw conflict("Encerre/cancele o patrocinio anterior; historico ativo, pendente ou incerto");
                if (ledger.entries().stream().anyMatch(e -> e.sponsor().equals(offer.sponsor()))) throw conflict("Contrato ja tentado; nunca repetir pagamento");
            }
            var store = offers(handle); var before = store.read();
            validateCareerDataScope(scope); store.publish(offer, before.token());
        } finally { mutating.set(false); }
    }
    void cancelSponsorOffer(br.vbrasfoot.api.CareerDataAccess.Scope scope, UUID sponsor) throws Exception {
        offEdt();
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
        try {
            validateCareerDataScope(scope);
            var handle = new CareerSaveAssociations.Handle(scope.career(), scope.binding());
            validateSponsorReview(review(Optional.of(handle)));
            var ledger = new SponsorActivationStore(sponsorActivationStore, handle).read();
            if (ledger.entries().stream().anyMatch(e -> e.sponsor().equals(sponsor))) throw conflict("Tentativa financeira retida: nao cancelar nem repetir; revise");
            var store = offers(handle); var before = store.read();
            validateCareerDataScope(scope); store.cancel(sponsor, before.token());
        } finally { mutating.set(false); }
    }
    record SponsorInbox(br.vbrasfoot.api.CareerDataAccess.Scope scope, List<SponsorOfferStore.Offer> offers, SponsorActivationStore.Snapshot ledger) { }
    SponsorInbox sponsorInbox() throws Exception {
        offEdt(); var first = review(Optional.empty());
        if (associations.group(first.decoded.source()).members().isEmpty()) return new SponsorInbox(null, List.of(), new SponsorActivationStore.Snapshot(CareerSidecarStore.EMPTY, List.of()));
        var scope = currentCareerDataScope();
        if (scope == null) scope = bindSponsorCareer();
        validateCareerDataScope(scope);
        var handle = new CareerSaveAssociations.Handle(scope.career(), scope.binding());
        var inbox = new SponsorInbox(scope, offers(handle).read().entries(), new SponsorActivationStore(sponsorActivationStore, handle).read());
        validateCareerDataScope(scope); return inbox;
    }
    static final class SponsorEndQuote {
        final br.vbrasfoot.api.CareerDataAccess.Scope scope; final Review review;
        final SponsorActivationStore.Snapshot ledger; final SponsorActivationStore.Entry activation;
        final SponsorActivationStore.EndAttempt ending; final String groupToken;
        final AtomicBoolean consumed = new AtomicBoolean();
        SponsorEndQuote(br.vbrasfoot.api.CareerDataAccess.Scope scope, Review review, SponsorActivationStore.Snapshot ledger,
                        SponsorActivationStore.Entry activation, SponsorActivationStore.EndAttempt ending, String groupToken) {
            this.scope = scope; this.review = review; this.ledger = ledger; this.activation = activation; this.ending = ending; this.groupToken = groupToken;
        }
    }
    SponsorEndQuote quoteSponsorEnd(br.vbrasfoot.api.CareerDataAccess.Scope scope, UUID sponsor, LegacySponsorAdapter adapter) throws Exception {
        offEdt(); requireNativeSession(); validateCareerDataScope(scope);
        var handle = new CareerSaveAssociations.Handle(scope.career(), scope.binding());
        var review = review(Optional.of(handle)); var association = validateCareerDataReview(review);
        var group = primaryGroup(association);
        var ledger = new SponsorActivationStore(sponsorActivationStore, handle).read();
        var activation = ledger.entries().stream().filter(e -> e.sponsor().equals(sponsor)).findFirst()
                .orElseThrow(() -> conflict("Patrocinio sem ativacao registrada"));
        if (activation.state() != SponsorActivationStore.State.MEMORY_APPLIED || activation.ending().isPresent()
                || activation.stadiumBefore().isBlank() || activation.stadiumAfter().isBlank())
            throw conflict("Encerramento exige ativacao aplicada com nome original; tentativa anterior/incerta nao pode ser repetida");
        var ending = CareerBridge.onEdt(() -> {
            var club = adapter.finance.humanClub(activation.request().club().id(), activation.request().club().name());
            return new SponsorActivationStore.EndAttempt(UUID.randomUUID(), review.decoded.bytes(), adapter.stadium(club),
                    adapter.finance.snapshot(club), SponsorActivationStore.EndState.CLAIMED);
        });
        validateCareerDataScope(scope);
        return new SponsorEndQuote(scope, review, ledger, activation, ending, group.token());
    }
    SponsorEndPipeline.Result executeSponsorEnd(SponsorEndQuote quote, LegacySponsorAdapter adapter) throws Exception {
        offEdt(); requireNativeSession();
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em andamento");
        CareerCommandGate.Preparation preparation = null;
        try {
            if (quote == null || !quote.consumed.compareAndSet(false, true)) throw conflict("Cotacao de encerramento consumida");
            validateCareerDataScope(quote.scope);
            var association = validateCareerDataReview(quote.review); var handle = association.handle();
            if (!primaryGroup(association).token().equals(quote.groupToken)) throw conflict("Grupo da carreira mudou");
            var ledger = new SponsorActivationStore(sponsorActivationStore, handle);
            if (!ledger.read().token().equals(quote.ledger.token())) throw conflict("Historico de patrocinio mudou");
            var handler = CareerBridge.onEdt(() -> adapter.endHandler(quote.activation, quote.ending, () -> careerDataSelected(quote.scope)));
            preparation = operational.gate().prepareSponsorContinuation(null, completedSponsorIntents(handle));
            var context = verifier.installReviewedContext(quote.review.decoded, handle, operational.gate(), preparation);
            operational.accepted(context);
            if (sponsorEndOwner == null) sponsorEndOwner = operational.gate().owner(SponsorEndPipeline.OWNER, Set.of(CareerCommandGate.Operation.SPONSOR_END));
            var journal = new CareerCommandJournal(new CareerSidecarStore(sponsorActivationStore.root().getParent().resolve("sponsor-operations")
                    .resolve(quote.activation.sponsor().toString()).resolve("end-journal")),
                    new CareerSidecarStore.Key(handle.career(), handle.binding(), SponsorEndPipeline.OWNER));
            var pipeline = new SponsorEndPipeline(operational.gate(), journal, ledger, adapter.finance::roots, java.time.Duration.ofSeconds(10));
            return pipeline.execute(sponsorEndOwner, context, quote.activation, quote.ending, quote.ledger.token(), journal.read(context.save()).token(), handler);
        } finally {
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private Review reviewMember(Optional<CareerSaveAssociations.Handle> selected, boolean requirePrimary) throws IOException, InterruptedException {
        return reviewMember(selected, requirePrimary, null);
    }
    /** Alias metadata shares this invocation's observation, never a cross-request cache. */
    private Review reviewMember(Optional<CareerSaveAssociations.Handle> selected, boolean requirePrimary, Review shared) throws IOException, InterruptedException {
        offEdt(); Objects.requireNonNull(selected);
        var binding = HookProbe.decodedProvenance().binding().orElseThrow(() ->
                new LoadedSaveVerifier.Failure(LoadedSaveVerifier.Reason.NO_PROVENANCE, "Carregue um save existente com a sonda; sem origem decodificada aceita"));
        CareerSidecarStore.plain(binding.source()); CareerSidecarStore.plain(saves);
        if (!binding.source().getParent().toRealPath().equals(saves.toRealPath()))
            throw new LoadedSaveVerifier.Failure(LoadedSaveVerifier.Reason.FILE_MISMATCH, "Origem decodificada fora do diretorio de saves selecionado");
        if (shared != null && (!instance.equals(shared.coordinator) || !binding.source().equals(shared.decoded.source())))
            throw conflict("Origem mudou durante inventario compartilhado");
        var observed = shared == null ? observer.observe(saves, binding.source().getFileName().toString()) : shared.observed;
        var decoded = shared == null ? verifier.verify(observed) : shared.decoded;
        Optional<CareerSaveAssociations.Association> association = selected.isEmpty() ? Optional.empty() : Optional.of(associations.inspect(selected.orElseThrow()));
        if (requirePrimary && association.isPresent()) primaryGroup(association.orElseThrow());
        Optional<CareerSaveAssociations.Match> match = association.map(value -> associations.assess(value, observed));
        var journals = new ArrayList<JournalReview>();
        var requests = new ArrayList<RequestReview>();
        var executions = new ArrayList<ExecutionReview>();
        var loans = new ArrayList<LoanReview>();
        Optional<CareerSidecarStore.NamespaceInventory> reviewedInventory = Optional.empty();
        Optional<CareerSidecarStore.NamespaceInventory> reviewedRequests = Optional.empty();
        Optional<CareerSidecarStore.NamespaceInventory> reviewedExecutions = Optional.empty();
        Optional<CareerSidecarStore.NamespaceInventory> reviewedLoans = Optional.empty();
        if (association.isPresent()) {
            var known = association.orElseThrow();
            var inventory = journalStore.inventoryBinding(known.handle().career(), known.handle().binding());
            reviewedInventory = Optional.of(inventory);
            var contractInventory = requestStore.inventoryBinding(known.handle().career(), known.handle().binding());
            reviewedRequests = Optional.of(contractInventory);
            var executionInventory = executionStore.inventoryBinding(known.handle().career(), known.handle().binding());
            reviewedExecutions = Optional.of(executionInventory);
            var loanInventory = loanInventory(known.handle()); reviewedLoans = Optional.of(loanInventory);
            var registered = new TreeSet<>(owners); registered.addAll(inventory.tokens().keySet());
            registered.addAll(contractInventory.tokens().keySet());
            registered.addAll(executionInventory.tokens().keySet());
            registered.addAll(loanInventory.tokens().keySet());
            if (registered.size() > CareerSidecarStore.MAX_BINDING_NAMESPACES) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.QUOTA, "Configured owners, journals, contracts and execution witnesses exceed binding inventory capacity; no partial review accepted");
            for (String owner : registered) {
                boolean configured = owners.contains(owner);
                String expectedToken = inventory.tokens().getOrDefault(owner, CareerSidecarStore.EMPTY);
                if (inventory.tokens().containsKey(owner) && expectedToken.equals(CareerSidecarStore.EMPTY)) {
                    journals.add(new JournalReview(owner, configured, Optional.empty(), Optional.of(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED)));
                } else try {
                    var recovery = journal(known.handle(), owner).inspectRecordedRecovery(observed.revision());
                    if (!recovery.snapshot().token().equals(expectedToken)) throw conflict("Diario mudou durante inventario; revisar novamente");
                    journals.add(new JournalReview(owner, configured, Optional.of(recovery), Optional.empty()));
                } catch (CareerSidecarStore.Failure failure) {
                    if (failure.reason() == CareerSidecarStore.Reason.CONFLICT) throw failure;
                    journals.add(new JournalReview(owner, configured, Optional.empty(), Optional.of(failure.reason())));
                }
                var journalReview = journals.get(journals.size() - 1);
                try {
                    var recovery = loanStore == null ? new LoanRequestStore.Recovery(new LoanRequestStore.Snapshot(CareerSidecarStore.EMPTY, List.of()), observed.revision(), observed.revision())
                            : loans(known.handle(), owner).inspectRecordedRecovery(observed.revision());
                    if (!recovery.snapshot().token().equals(loanInventory.tokens().getOrDefault(owner, CareerSidecarStore.EMPTY)))
                        throw conflict("Contratos de emprestimo mudaram durante inventario");
                    var links = journalReview.recovery().map(value -> LoanContractReview.compare(recovery.snapshot(), value.snapshot())).orElse(List.of());
                    loans.add(new LoanReview(owner, configured, Optional.of(recovery), Optional.empty(), links));
                } catch (CareerSidecarStore.Failure failure) {
                    if (failure.reason() == CareerSidecarStore.Reason.CONFLICT) throw failure;
                    loans.add(new LoanReview(owner, configured, Optional.empty(), Optional.of(failure.reason()), List.of()));
                }
                try {
                    var recovery = requests(known.handle(), owner).inspectRecordedRecovery(observed.revision());
                    if (!recovery.snapshot().token().equals(contractInventory.tokens().getOrDefault(owner, CareerSidecarStore.EMPTY)))
                        throw conflict("Contratos mudaram durante inventario; revisar novamente");
                    var links = journalReview.recovery().map(value -> FinanceContractReview.compare(recovery.snapshot(), value.snapshot())).orElse(List.of());
                    requests.add(new RequestReview(owner, configured, Optional.of(recovery), Optional.empty(), links));
                } catch (CareerSidecarStore.Failure failure) {
                    if (failure.reason() == CareerSidecarStore.Reason.CONFLICT) throw failure;
                    requests.add(new RequestReview(owner, configured, Optional.empty(), Optional.of(failure.reason()), List.of()));
                }
                try {
                    var recovery = executions(known.handle(), owner).inspectRecordedRecovery(observed.revision());
                    if (!recovery.snapshot().token().equals(executionInventory.tokens().getOrDefault(owner, CareerSidecarStore.EMPTY)))
                        throw conflict("Evidencias de execucao mudaram durante inventario");
                    var operations = journalReview.recovery().map(value -> FinanceExecutionRecovery.inspect(value.snapshot(), recovery.snapshot())).orElse(List.of());
                    executions.add(new ExecutionReview(owner, configured, Optional.of(recovery), Optional.empty(), operations));
                } catch (CareerSidecarStore.Failure failure) {
                    if (failure.reason() == CareerSidecarStore.Reason.CONFLICT) throw failure;
                    executions.add(new ExecutionReview(owner, configured, Optional.empty(), Optional.of(failure.reason()), List.of()));
                }
            }
            if (!inventory.equals(journalStore.inventoryBinding(known.handle().career(), known.handle().binding()))) throw conflict("Namespaces ou HEADs mudaram durante revisao");
            if (!contractInventory.equals(requestStore.inventoryBinding(known.handle().career(), known.handle().binding()))) throw conflict("Contratos ou HEADs mudaram durante revisao");
            if (!executionInventory.equals(executionStore.inventoryBinding(known.handle().career(), known.handle().binding()))) throw conflict("Evidencias ou HEADs mudaram durante revisao");
            if (!loanInventory.equals(loanInventory(known.handle()))) throw conflict("Emprestimos ou HEADs mudaram durante revisao");
            if (!associations.inspect(known.handle()).token().equals(known.token())) throw conflict("Associacao mudou durante revisao");
        }
        if (shared == null && !decoded.equals(verifier.verify(observed))) throw conflict("Carregamento mudou durante revisao");
        boolean metadataChanged = association.isPresent() && (!association.orElseThrow().modified().equals(observed.modified())
                || !association.orElseThrow().created().equals(observed.created()));
        Status status = match.isEmpty() ? Status.ASSOCIATION_REQUIRED
                : match.orElseThrow() == CareerSaveAssociations.Match.REVISION_CHANGED ? Status.REVISION_REVIEW_REQUIRED
                : metadataChanged ? Status.ASSOCIATION_CONFLICT
                : match.orElseThrow() == CareerSaveAssociations.Match.EXACT_OBSERVATION || match.orElseThrow() == CareerSaveAssociations.Match.FILE_IDENTITY_UNAVAILABLE
                ? Status.READ_ONLY_LINKED : Status.ASSOCIATION_CONFLICT;
        return new Review(instance, observed, decoded, association, match, journals, requests, status, reviewedInventory, reviewedRequests, executions, reviewedExecutions, loans, reviewedLoans);
    }
    /** Call only after showing this exact review and receiving an explicit action-specific human confirmation.
     * One-shot even on error. A returned handle is metadata identity, never game readiness. */
    CareerSaveAssociations.Handle confirm(Review review, Action action, Optional<String> journalOwner) throws IOException, InterruptedException {
        offEdt(); Objects.requireNonNull(action); Objects.requireNonNull(journalOwner);
        if (review == null || !instance.equals(review.coordinator)) throw new IllegalArgumentException("Foreign review");
        if (!mutating.compareAndSet(false, true)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.BUSY, "Outra confirmacao ainda esta em curso");
        CareerCommandGate.Preparation preparation = null; boolean bound = false;
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao ja consumida; verificar novamente antes de confirmar");
            if (operational != null) {
                preparation = operational.begin();
            }
            if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Carreira recarregada desde a revisao; confirmar de novo");
            var selected = review.association;
            if (selected.isPresent() && !associations.inspect(selected.orElseThrow().handle()).token().equals(selected.orElseThrow().token()))
                throw conflict("Associacao mudou desde a revisao");
            if (selected.isPresent()) {
                var handle = selected.orElseThrow().handle();
                if (!review.inventory.orElseThrow().equals(journalStore.inventoryBinding(handle.career(), handle.binding())))
                    throw conflict("Diarios ou namespaces mudaram desde a revisao; confirmar novamente");
                if (!review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(handle.career(), handle.binding())))
                    throw conflict("Contratos ou namespaces mudaram desde a revisao; confirmar novamente");
                if (!review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(handle.career(), handle.binding())))
                    throw conflict("Evidencias de execucao mudaram desde a revisao; confirmar novamente");
                if (!review.loanInventory.orElseThrow().equals(loanInventory(handle)))
                    throw conflict("Contratos de emprestimo mudaram desde a revisao; confirmar novamente");
            }
            if (action == Action.CREATE_NEW) {
                if (selected.isPresent()) throw conflict("Criar IDs novos requer nenhuma associacao selecionada; copia deve usar fork explicito");
                return associations.create(review.observed, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER).handle();
            }
            var known = selected.orElseThrow(() -> new IllegalArgumentException("Select an explicit association handle first"));
            var handle = known.handle(); String token = known.token();
            blockGroupedLegacy(handle);
            return switch (action) {
                case FORK_COPY -> associations.fork(handle, token, review.observed, CareerSaveAssociations.Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA).handle();
                case RENAME_PROVEN -> associations.rename(handle, token, review.observed, CareerSaveAssociations.Confirmation.RENAME_SAME_FILE).handle();
                case ASSERT_RENAME -> associations.rename(handle, token, review.observed, CareerSaveAssociations.Confirmation.ASSERT_RENAME_WITHOUT_FILE_IDENTITY).handle();
                case ACCEPT_REVISION -> associations.acceptRevision(handle, token, review.observed, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION).handle();
                case RESTORE_PREVIOUS -> associations.restorePrevious(handle, token, review.observed, CareerSaveAssociations.Confirmation.RESTORE_RECORDED_PREVIOUS).handle();
                case QUARANTINE_PENDING -> {
                    String owner = journalOwner.orElseThrow(() -> new IllegalArgumentException("Explicit journal owner required"));
                    var row = review.journals.stream().filter(value -> value.owner().equals(owner)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Journal owner not present in reviewed binding inventory"));
                    var recovery = row.recovery().orElseThrow(() -> conflict("Diario indisponivel ou ancora divergente; nao ignorar erro"));
                    journal(handle, owner).quarantinePending(recovery.recordedSave(), recovery.snapshot().token());
                    yield handle;
                }
                case REANCHOR_NO_EFFECT_JOURNAL -> {
                    if (review.status != Status.READ_ONLY_LINKED || !known.save().equals(review.decoded.bytes())) throw conflict("Confirmar associacao atual antes de reconciliar diario");
                    String owner = journalOwner.orElseThrow(() -> new IllegalArgumentException("Explicit journal owner required"));
                    var row = review.journals.stream().filter(value -> value.owner().equals(owner)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Journal owner not reviewed"));
                    var recovery = row.recovery().orElseThrow(() -> conflict("Diario indisponivel; nao ignorar erro"));
                    journal(handle, owner).reanchorNoEffects(recovery.recordedSave(), review.decoded.bytes(), recovery.snapshot().token());
                    yield handle;
                }
                case RECONCILE_PROVEN_NO_EFFECT -> {
                    if (review.status != Status.READ_ONLY_LINKED || !known.save().equals(review.decoded.bytes())) throw conflict("Associacao e revisao atuais obrigatorias para reconciliar ausencia de efeito");
                    String owner = journalOwner.orElseThrow(() -> new IllegalArgumentException("Explicit journal owner required"));
                    var history = review.journals.stream().filter(row -> row.owner().equals(owner)).findFirst().orElseThrow().recovery()
                            .orElseThrow(() -> conflict("Diario indisponivel"));
                    var evidence = review.executions.stream().filter(row -> row.owner().equals(owner)).findFirst().orElseThrow().recovery()
                            .orElseThrow(() -> conflict("Evidencia indisponivel"));
                    if (history.revisionChanged() || evidence.revisionChanged()) throw conflict("Nao reconciliar provas de outra revisao");
                    var proofs = new ArrayList<FinanceExecutionWitnessStore.NoEffectProof>();
                    var witnessStore = executions(handle, owner);
                    for (var receipt : history.snapshot().receipts()) {
                        boolean alreadyResolved = history.snapshot().noEffectResolutions().stream().anyMatch(value -> value.operationId().equals(receipt.operationId()));
                        boolean proven = evidence.snapshot().witnesses().stream().anyMatch(value -> value.operationId().equals(receipt.operationId()) && value.intent().equals(receipt.intent())
                                && value.state() == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED);
                        if (!alreadyResolved && receipt.state() == CareerCommandJournal.State.OUTCOME_UNKNOWN && proven)
                            proofs.add(witnessStore.proveNoEffect(review.decoded.bytes(), evidence.snapshot().token(), receipt.operationId(), receipt.intent()));
                    }
                    if (proofs.isEmpty()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Nenhuma operacao incerta possui prova duravel NO_EFFECT exata");
                    journal(handle, owner).reconcileNoEffects(proofs, history.snapshot().token()); yield handle;
                }
                case PREPARE_INTERNAL_CONTEXT -> {
                    blockContinuationEffects(handle);
                    if (operational == null || preparation == null) throw conflict("Sessao operacional nao conectada");
                    if (review.status != Status.READ_ONLY_LINKED || !review.decoded.initializationPathObserved() || !known.save().equals(review.decoded.bytes()))
                        throw conflict("Carga inicializada e associacao atual obrigatorias; snapshot nao e prontidao global");
                    for (var row : review.journals) {
                        var recovery = row.recovery().orElseThrow(() -> conflict("Diario incompleto ou corrompido bloqueia contexto"));
                        if (recovery.revisionChanged() || recovery.status() == CareerCommandJournal.RecoveryStatus.UNCERTAIN
                                || recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS)
                            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Efeito historico/incerto ou ancora divergente bloqueia contexto");
                    }
                    for (var row : review.requests) {
                        var recovery = row.recovery().orElseThrow(() -> new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Contrato incompleto/corrompido bloqueia contexto"));
                        if (recovery.revisionChanged() || row.links().stream().anyMatch(FinanceContractReview.Link::blocksContext))
                            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Contrato/diario divergente ou historico incompleto bloqueia contexto");
                    }
                    for (var row : review.loans) {
                        var recovery = row.recovery().orElseThrow(() -> conflict("Contrato de emprestimo indisponivel bloqueia contexto"));
                        if (recovery.revisionChanged() || row.links().stream().anyMatch(LoanContractReview.Link::blocksContext))
                            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                                    "Emprestimo pendente/historico/incerto ou revisao divergente requer revisao; sem replay");
                    }
                    for (var row : review.executions) {
                        var recovery = row.recovery().orElseThrow(() -> new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Evidencia incompleta/corrompida bloqueia contexto"));
                        if (recovery.revisionChanged()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Ancora de execucao divergente bloqueia contexto");
                        var receipts = review.journals.stream().filter(value -> value.owner().equals(row.owner())).findFirst().orElseThrow()
                                .recovery().orElseThrow().snapshot().receipts();
                        for (var operation : row.operations()) {
                            boolean absentLegacy = operation.status() == FinanceExecutionRecovery.Status.NO_EXECUTION_WITNESS
                                    && receipts.stream().anyMatch(value -> value.operationId().equals(operation.operationId())
                                    && value.intent().equals(operation.intent()) && value.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED);
                            if (operation.status() != FinanceExecutionRecovery.Status.NO_EFFECT_CONFIRMED && !absentLegacy)
                                throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Execucao historica/incerta ou conflitante nao prova inclusao no save");
                        }
                    }
                    if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Origem mudou durante preparacao de contexto");
                     if (!review.inventory.orElseThrow().equals(journalStore.inventoryBinding(handle.career(), handle.binding()))
                            || !review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(handle.career(), handle.binding()))
                             || !review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(handle.career(), handle.binding()))
                             || !review.loanInventory.orElseThrow().equals(loanInventory(handle))
                             || !associations.inspect(handle).token().equals(token)) throw conflict("Metadados ou diarios mudaram antes da instalacao");
                     blockContinuationEffects(handle);
                     var installed = verifier.installReviewedContext(review.decoded, handle, operational.gate(), preparation);
                    operational.accepted(installed); bound = true; yield handle;
                }
                default -> throw new IllegalArgumentException("Unsupported confirmation");
            };
        } finally {
            if (preparation != null && !bound) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private CareerCommandJournal journal(CareerSaveAssociations.Handle handle, String owner) {
        return new CareerCommandJournal(journalStore, new CareerSidecarStore.Key(handle.career(), handle.binding(), owner));
    }
    private FinanceRequestStore requests(CareerSaveAssociations.Handle handle, String owner) {
        return new FinanceRequestStore(requestStore, new CareerSidecarStore.Key(handle.career(), handle.binding(), owner));
    }
    private FinanceExecutionWitnessStore executions(CareerSaveAssociations.Handle handle, String owner) {
        return new FinanceExecutionWitnessStore(executionStore, new CareerSidecarStore.Key(handle.career(), handle.binding(), owner));
    }
    private CareerSidecarStore.NamespaceInventory loanInventory(CareerSaveAssociations.Handle handle) throws IOException {
        return loanStore == null ? new CareerSidecarStore.NamespaceInventory(Map.of()) : loanStore.inventoryBinding(handle.career(), handle.binding());
    }
    private LoanRequestStore loans(CareerSaveAssociations.Handle handle, String owner) {
        return new LoanRequestStore(Objects.requireNonNull(loanStore), new CareerSidecarStore.Key(handle.career(), handle.binding(), owner));
    }
    LoanRequestStore.Snapshot submitLoan(Review review, String owner, String key, CareerBridge bridge, LegacyLoanAdapter adapter,
                                        br.vbrasfoot.api.GameAccess.PlayerRef player, br.vbrasfoot.api.GameAccess.ClubRef origin,
                                        br.vbrasfoot.api.GameAccess.ClubRef destination) throws Exception {
        return mutateLoans(review, owner, (store, handle, token) -> {
            var request = CareerBridge.onEdt(() -> adapter.request(handle.career(), handle.binding(), review.decoded.bytes(), owner, key,
                    bridge, player, origin, destination));
            validateLoanReview(review);
            var pending = store.persist(request, token);
            return store.evaluate(review.decoded.bytes(), request.key(), pending.token());
        });
    }
    LoanRequestStore.Snapshot decideLoan(Review review, String owner, String key, boolean cancel) throws Exception {
        return mutateLoans(review, owner, (store, handle, token) -> cancel ? store.cancel(review.decoded.bytes(), key, token)
                : store.evaluate(review.decoded.bytes(), key, token));
    }
    /** Explicit built-in UI command only. Uses the session's original-I/O interlocked gate, never fixture authority. */
    CareerCommandDispatcher.StoredLoanResult executeLoan(Review review, String key, LegacyLoanAdapter adapter) throws Exception {
        offEdt();
        if (operational == null || loanStore == null || review == null || !instance.equals(review.coordinator))
            throw new IllegalStateException("Sessao de emprestimos nao conectada; use --probe-career-hooks");
        if (!mutating.compareAndSet(false, true)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.BUSY, "Outra operacao em curso");
        CareerCommandGate.Preparation preparation = null;
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao consumida; revise novamente");
            var handle = validateLoanReview(review);
            blockLegacyLoansAfterContinuation(handle);
            checkLoanWindow(handle);
            String owner = LoanManagerPanel.OWNER;
            var row = review.loans.stream().filter(value -> value.owner().equals(owner)).findFirst().orElseThrow();
            var recovery = row.recovery().orElseThrow(() -> conflict("Pedidos indisponiveis"));
            var request = recovery.snapshot().requests().stream().filter(value -> value.key().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Pedido nao encontrado"));
            // Historical/uncertain decisions are visible even across save revisions; they never become actionable again.
            var state = recovery.snapshot().state(key);
            if (!recovery.executable(key))
                throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                        "Pedido nao executavel: " + state + ". Historico/revisao nunca e repetido automaticamente");
            validateLoanExecution(review, owner, key);
            preparation = operational.begin();
            validateLoanReview(review);
            var plan = CareerBridge.onEdt(() -> adapter.prepare(request).policyGuard(() -> loanWindowOpen(handle)));
            var context = verifier.installReviewedContext(review.decoded, handle, operational.gate(), preparation);
            operational.accepted(context);
            if (loanOwner == null) loanOwner = operational.gate().owner(owner, Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
            var history = review.journals.stream().filter(value -> value.owner().equals(owner)).findFirst().orElseThrow().recovery().orElseThrow();
            var dispatcher = new CareerCommandDispatcher(operational.gate(), journal(handle, owner), adapter::roots, java.time.Duration.ofSeconds(10));
            var result = dispatcher.executeStoredLoan(loanOwner, context, request, history.snapshot().token(), loans(handle, owner), recovery.snapshot().token(), plan);
            return new CareerCommandDispatcher.StoredLoanResult(result.dispatch(), result.requests(), plan.diagnostic());
        } finally {
            // No durable authority is exposed after the one explicit command, even on uncertain completion.
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private void validateLoanExecution(Review review, String owner, String key) throws IOException {
        if (!review.decoded.initializationPathObserved()) throw conflict("Carga original inicializada obrigatoria para transferencia");
        for (var row : review.journals) {
            var recovery = row.recovery().orElseThrow(() -> conflict("Diario indisponivel"));
            if (recovery.revisionChanged() || recovery.status() == CareerCommandJournal.RecoveryStatus.UNCERTAIN
                    || recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS)
                throw conflict("Diario historico/incerto/revisao divergente: transferencia bloqueada");
        }
        for (var row : review.requests) {
            var recovery = row.recovery().orElseThrow(() -> conflict("Contratos financeiros indisponiveis"));
            if (recovery.revisionChanged() || row.links().stream().anyMatch(FinanceContractReview.Link::blocksContext)) throw conflict("Contratos financeiros bloqueiam contexto");
        }
        for (var row : review.executions) {
            var recovery = row.recovery().orElseThrow(() -> conflict("Evidencias indisponiveis"));
            if (recovery.revisionChanged() || !recovery.snapshot().witnesses().isEmpty()) throw conflict("Evidencias financeiras exigem reconciliacao");
        }
        for (var row : review.loans) {
            var recovery = row.recovery().orElseThrow(() -> conflict("Contratos de emprestimo indisponiveis"));
            if (recovery.revisionChanged()) throw conflict("Revisao de emprestimo divergente");
            for (var link : row.links()) if (link.blocksContext()
                    && !(row.owner().equals(owner) && link.key().equals(key) && link.status() == LoanContractReview.Status.PENDING_WITHOUT_RECEIPT))
                throw conflict("Outro pedido pendente/historico/incerto bloqueia execucao: " + link.key());
        }
    }
    private interface LoanMetadataChange {
        LoanRequestStore.Snapshot apply(LoanRequestStore store, CareerSaveAssociations.Handle handle, String token) throws Exception;
    }
    private static final class NativeAuthorization {
        final UUID coordinator;
        final Review review;
        final LoanRequest request;
        final LegacyLoanAdapter adapter;
        final LoanContinuationStore.Snapshot ledger;
        final Map<String, String> sources;
        final List<LoanRequest> active;
        final List<LoanRequest> retired;
        final Set<CareerCommandGate.AppliedLoan> accounted;
        final AtomicBoolean consumed = new AtomicBoolean();
        NativeAuthorization(UUID coordinator, Review review, LoanRequest request, LegacyLoanAdapter adapter,
                            LoanContinuationStore.Snapshot ledger, NativeHistory history) {
            this.coordinator = coordinator; this.review = review; this.request = request; this.adapter = adapter;
            this.ledger = ledger; sources = history.sources; active = history.active; retired = history.retired; accounted = history.accounted;
        }
    }
    record NativeLoanQuote(CareerSaveAssociations.Handle handle, LoanRequest request, String report,
                           NativeAuthorization authorization) { }
    record NativeLoanResult(CareerCommandDispatcher.Kind kind, String diagnostic, LoanContinuationStore.Snapshot durable) { }
    private record NativeHistory(Map<String, String> sources, List<LoanRequest> all, List<LoanRequest> active,
                                 Set<CareerCommandGate.AppliedLoan> accounted, List<LoanRequest> retired) { }

    /** Read-only quote; no new authority, persistence, historical replay or save-inclusion inference. */
    NativeLoanQuote quoteNativeLoan(CareerSaveAssociations.Handle handle, CareerBridge bridge, LegacyLoanAdapter adapter,
                                   br.vbrasfoot.api.GameAccess.PlayerRef player, br.vbrasfoot.api.GameAccess.ClubRef origin,
                                   br.vbrasfoot.api.GameAccess.ClubRef destination) throws Exception {
        offEdt(); requireNativeSession(); checkLoanWindow(handle);
        var review = review(Optional.of(handle)); validateLoanReview(review);
        if (!review.decoded.initializationPathObserved()) throw conflict("Recarregue o save pela carga original inicializada");
        var ledger = continuation(handle).read();
        var history = nativeHistory(review, ledger);
        var request = CareerBridge.onEdt(() -> {
            checkHistoricalLoans(adapter, history.active, history.retired);
            var captured = adapter.request(handle.career(), handle.binding(), review.decoded.bytes(), LoanManagerPanel.OWNER,
                    "native_" + UUID.randomUUID().toString().replace("-", ""), bridge, player, origin, destination);
            adapter.prepare(captured); return captured;
        });
        for (var old : history.all) if (LoanContinuationStore.samePlayer(old, request))
            throw conflict("Jogador/origem ja registrado no historico; nunca repetir mesmo com chave nova");
        if (ledger.entries().size() >= LoanContinuationStore.LIMIT)
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.QUOTA, "Limite de continuacoes atingido; historico nao e removido");
        validateLoanReview(review);
        if (!ledger.token().equals(continuation(handle).read().token())) throw conflict("Ledger mudou durante cotacao");
        var report = new StringBuilder(request.termsReport())
                .append("\nRevisao atual: ").append(request.save().sha256()).append(" / ").append(request.save().size())
                .append("\nLedger revisado: ").append(ledger.token())
                .append("\nHistorico retido: ").append(history.all.size()).append(" pedidos; ").append(history.active.size()).append(" ativos consistentes.")
                .append("\nFontes revisadas (confirmar este inventario exato; nenhuma ancora antiga sera alterada):");
        for (var source : new TreeMap<>(history.sources).entrySet())
            report.append("\n").append(ledger.sources().containsKey(source.getKey()) ? "RETIDA " : "NOVA FONTE ")
                    .append(source.getKey()).append(" = ").append(source.getValue());
        return new NativeLoanQuote(handle, request, report.toString()
                + "\nNovo pedido distinto; historico nao sera repetido. Baseline: save atualmente carregado."
                + "\nNao prova inclusao de taxas antigas no save. Apos salvar, recarregue antes de nova cotacao.",
                new NativeAuthorization(instance, review, request, adapter, ledger, history));
    }

    /** Explicit changed-revision confirmation for this loan-only bounded path, not generic reconciliation.
     * Caller must show this exact review and obtain separate ACCEPT_REVISION confirmation first. */
    CareerSaveAssociations.Handle acceptNativeLoanRevision(Review review, LegacyLoanAdapter adapter) throws Exception {
        offEdt(); requireNativeSession();
        if (review == null || !instance.equals(review.coordinator)) throw new IllegalArgumentException("Foreign review");
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em curso");
        CareerCommandGate.Preparation preparation = null;
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao consumida");
            validateNativeRevisionReview(review);
            var handle = review.association.orElseThrow().handle(); var ledger = continuation(handle).read();
            var history = nativeHistory(review, ledger);
            CareerBridge.onEdt(() -> { checkHistoricalLoans(adapter, history.active, history.retired); return null; });
            preparation = operational.gate().prepareLoanContinuation(history.accounted);
            validateNativeRevisionReview(review);
            if (!ledger.token().equals(continuation(handle).read().token())) throw conflict("Ledger mudou durante revisao");
            if (!history.sources.equals(nativeHistory(review, ledger).sources)) throw conflict("Historicos mudaram durante revisao");
            return associations.acceptRevision(handle, review.association.orElseThrow().token(), review.observed,
                    CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION).handle();
        } finally {
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private void validateNativeRevisionReview(Review review) throws IOException, InterruptedException {
        var known = review.association.orElseThrow(() -> new IllegalArgumentException("Explicit association required"));
        var handle = known.handle();
        boolean metadataOnly = review.status == Status.ASSOCIATION_CONFLICT && review.match.isPresent()
                && (review.match.orElseThrow() == CareerSaveAssociations.Match.METADATA_CHANGED
                    || review.match.orElseThrow() == CareerSaveAssociations.Match.FILE_IDENTITY_UNAVAILABLE
                    || review.match.orElseThrow() == CareerSaveAssociations.Match.FILE_REPLACED_OR_COPIED);
        if (!(review.status == Status.REVISION_REVIEW_REQUIRED || metadataOnly) || !review.decoded.initializationPathObserved()
                || !review.decoded.equals(verifier.verify(review.observed))
                || !associations.inspect(handle).token().equals(known.token())
                || !review.inventory.orElseThrow().equals(journalStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.loanInventory.orElseThrow().equals(loanInventory(handle)))
            throw conflict("Revisao nativa mudou; salvar exige recarregamento e nova confirmacao explicita");
    }

    /** One explicit confirmation, one invocation, one terminal commit; original I/O stays excluded through commit. */
    NativeLoanResult executeNativeLoan(NativeLoanQuote quote, LegacyLoanAdapter adapter) throws Exception {
        offEdt(); requireNativeSession();
        var auth = quote == null ? null : quote.authorization;
        if (auth == null || !instance.equals(auth.coordinator) || auth.adapter != adapter
                || !auth.request.equals(quote.request) || !auth.review.association.orElseThrow().handle().equals(quote.handle))
            throw new IllegalArgumentException("Foreign native loan quote");
        if (!mutating.compareAndSet(false, true)) throw conflict("Outra operacao em curso");
        var gate = operational.gate(); CareerCommandGate.Preparation preparation = null;
        CareerCommandGate.Permit permit = null;
        try {
            if (!auth.consumed.compareAndSet(false, true)) throw conflict("Cotacao consumida; nunca repetir automaticamente");
            checkLoanWindow(quote.handle);
            validateLoanReview(auth.review);
            var store = continuation(quote.handle); var ledger = store.read();
            if (!auth.ledger.token().equals(ledger.token())) throw conflict("Ledger mudou; cotacao obsoleta");
            var history = nativeHistory(auth.review, ledger);
            if (!history.sources.equals(auth.sources)) throw conflict("Fontes historicas mudaram");
            var plan = CareerBridge.onEdt(() -> { checkHistoricalLoans(adapter, auth.active, auth.retired); return adapter.prepare(auth.request).policyGuard(() -> loanWindowOpen(quote.handle)); });
            preparation = gate.prepareLoanContinuation(auth.accounted);
            var context = verifier.installReviewedContext(auth.review.decoded, quote.handle, gate, preparation);
            operational.accepted(context);
            if (loanOwner == null) loanOwner = gate.owner(LoanManagerPanel.OWNER, Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
            final var owner = loanOwner;
            var reserved = CareerBridge.onEdt(() -> {
                var roots = adapter.roots();
                var result = gate.reserve(owner, new CareerCommandGate.Request(context.token(), auth.request.key(),
                        CareerCommandGate.Operation.LOAN_ACCEPT, auth.request.digest()), roots.primary(), roots.secondary());
                result.permit().ifPresent(gate::retainDispatch); return result;
            });
            if (reserved.permit().isEmpty()) throw conflict("Gate recusou cotacao; nenhum pedido executado");
            permit = reserved.permit().orElseThrow();
            // The reservation plus dispatch hold now exclude original load/reset/save, including all disk writes below.
            validateLoanReview(auth.review);
            if (!auth.ledger.token().equals(store.read().token())) throw conflict("Ledger mudou antes do claim");
            if (!auth.sources.equals(nativeHistory(auth.review, ledger).sources)) throw conflict("Historicos mudaram antes do claim");
            var claimed = store.claim(auth.request, ledger.token(), auth.sources);
            var dispatch = new NativeDispatch(gate, permit, adapter, plan, auth.active, auth.retired);
            SwingUtilities.invokeLater(dispatch);
            var receipt = dispatch.await();
            boolean interrupted = Thread.interrupted(); // Finish durable state before restoring caller interruption.
            var terminal = switch (receipt.status()) {
                case APPLIED -> LoanContinuationStore.State.APPLIED;
                case REJECTED -> LoanContinuationStore.State.NO_EFFECT;
                default -> LoanContinuationStore.State.UNKNOWN;
            };
            try {
                var completed = store.complete(auth.request, claimed.token(), terminal);
                var kind = terminal == LoanContinuationStore.State.APPLIED ? CareerCommandDispatcher.Kind.EXECUTED
                        : terminal == LoanContinuationStore.State.NO_EFFECT ? CareerCommandDispatcher.Kind.NO_EFFECT : CareerCommandDispatcher.Kind.UNCERTAIN;
                return new NativeLoanResult(kind, plan.diagnostic(), completed);
            } catch (IOException | RuntimeException uncertainCommit) {
                gate.uncertain(permit);
                return new NativeLoanResult(CareerCommandDispatcher.Kind.UNCERTAIN,
                        plan.diagnostic() + " / commit terminal incerto; nao repetir. Revisar ledger antes de continuar.", claimed);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        } finally {
            if (permit != null) { gate.cancel(permit); gate.releaseDispatch(permit); }
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private void requireNativeSession() {
        if (operational == null || loanStore == null || continuationStore == null)
            throw new IllegalStateException("Continuacao nao configurada; use sessao original com --probe-career-hooks");
    }
    private LoanContinuationStore continuation(CareerSaveAssociations.Handle handle) {
        return new LoanContinuationStore(Objects.requireNonNull(continuationStore), handle);
    }
    private void blockContinuationEffects(CareerSaveAssociations.Handle handle) throws IOException {
        blockGroupedLegacy(handle);
        blockSponsorEffects(handle);
        if (continuationStore == null) return; // Old synthetic constructors have no continuation root.
        var ledger = continuation(handle).read();
        if (ledger.uncertain() || ledger.entries().stream().anyMatch(e -> e.state() == LoanContinuationStore.State.APPLIED))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                    "Ledger de continuacao possui efeitos/incerteza; contexto generico bloqueado, sem replay");
    }
    private void blockLegacyLoansAfterContinuation(CareerSaveAssociations.Handle handle) throws IOException {
        blockGroupedLegacy(handle);
        if (continuationStore != null && !continuation(handle).read().entries().isEmpty())
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                    "Historico de continuacao presente; painel antigo somente leitura. Use novo pedido nativo distinto");
    }
    static final class HistoricalLoanFailure extends IllegalStateException {
        private final List<String> players;
        HistoricalLoanFailure(List<String> players) {
            super("Emprestimo historico sem jogador/tracker ativo exato: " + String.join(", ", players) + "; continuacao bloqueada");
            this.players = List.copyOf(players);
        }
        List<String> players() { return players; }
    }
    private static void checkHistoricalLoans(LegacyLoanAdapter adapter, List<LoanRequest> active, List<LoanRequest> retired) throws Exception {
        var invalid = adapter.invalidHistoricalLoans(active, retired);
        var missing = invalid.stream().filter(item -> !item.retired()).map(LoanHistoryBatch.Expected::name).toList();
        if (!missing.isEmpty()) throw new HistoricalLoanFailure(missing);
        if (!invalid.isEmpty()) throw conflict("Referencia escolhida mudou para " + invalid.get(0).name() + "; nao ignorar emprestimo restaurado ou jogador divergente");
    }

    /** Pure metadata qualification; adapter checks supplement it, never establish old fee save inclusion. */
    private NativeHistory nativeHistory(Review review, LoanContinuationStore.Snapshot ledger) throws IOException, InterruptedException {
        return nativeHistory(review, ledger, false);
    }
    private NativeHistory nativeHistory(Review review, LoanContinuationStore.Snapshot ledger, boolean nextSponsor) throws IOException, InterruptedException {
        var group = primaryGroup(review.association.orElseThrow());
        for (var member : group.members()) {
            if (nextSponsor) {
                if (!new SponsorActivationStore(sponsorActivationStore, member.handle()).read().permitsNextSponsor()) throw conflict("Patrocinio ativo/incerto bloqueia novo contrato");
            } else blockSponsorEffects(member.handle());
        }
        var base = memberHistory(nextSponsor ? withoutCompletedSponsors(review) : review, ledger);
        var sources = new TreeMap<>(base.sources);
        var all = new ArrayList<>(base.all); var active = new ArrayList<>(base.active);
        var accounted = new HashSet<>(base.accounted);
        for (var member : group.members()) {
            if (member.handle().equals(group.primary())) continue;
            var aliasReview = reviewMember(Optional.of(member.handle()), false, review);
            var aliasLedger = continuation(member.handle()).read();
            var alias = memberHistory(nextSponsor ? withoutCompletedSponsors(aliasReview) : aliasReview, aliasLedger);
            LoanContinuationStore.validateSources(aliasLedger, alias.sources);
            String prefix = "alias/" + member.handle().career() + "/" + member.handle().binding() + "/";
            sources.put(prefix + "association", member.token());
            sources.put(prefix + "continuation", aliasLedger.token().equals(CareerSidecarStore.EMPTY) ? "0".repeat(64) : aliasLedger.token());
            for (var source : alias.sources.entrySet()) sources.put(prefix + source.getKey(), source.getValue());
            for (var request : alias.all) {
                for (var old : all) if (old.key().equals(request.key()) || LoanContinuationStore.samePlayer(old, request))
                    throw conflict("Historicos de associacoes sobrepostos; recuperacao obrigatoria");
                all.add(request);
            }
            active.addAll(alias.active); accounted.addAll(alias.accounted);
            if (!aliasLedger.token().equals(continuation(member.handle()).read().token())) throw conflict("Ledger historico mudou");
        }
        if (all.size() > 64) throw conflict("Historico agregado excede limite; nenhuma fonte ignorada");
        if (!group.token().equals(primaryGroup(review.association.orElseThrow()).token())) throw conflict("Grupo mudou durante inventario");
        if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Save/carregamento mudou durante inventario historico");
        List<LoanRequest> retired = List.of();
        if (continuationStore != null) {
            var baseline = new LoanBaselineStore(new CareerSidecarStore(continuationStore.root().getParent().resolve("loan-baselines")), group.primary()).read();
            if (baseline.isPresent()) {
                retired = LoanBaselineStore.validate(baseline.orElseThrow(), active, sources);
                active.removeAll(retired);
                sources.put("baseline", baseline.orElseThrow().token());
            }
        }
        LoanContinuationStore.validateSources(ledger, sources);
        return new NativeHistory(Map.copyOf(sources), List.copyOf(all), List.copyOf(active), Set.copyOf(accounted), retired);
    }
    private CareerSaveAssociations.Group primaryGroup(CareerSaveAssociations.Association association) throws IOException {
        var group = associations.group(association.directory().resolve(association.name()));
        if (!association.handle().equals(group.primary())) throw conflict("Associacao historica somente leitura; use a principal");
        return group;
    }
    private void blockGroupedLegacy(CareerSaveAssociations.Handle handle) throws IOException {
        if (continuationStore != null && new LoanBaselineStore(new CareerSidecarStore(continuationStore.root().getParent().resolve("loan-baselines")), handle).read().isPresent())
            throw conflict("Referencia atual escolhida: fluxo legado somente leitura; use novo pedido nativo distinto");
        if (primaryGroup(associations.inspect(handle)).members().size() > 1)
            throw conflict("Grupo consolidado: fluxo legado somente leitura; todos os historicos devem ser validados no fluxo nativo");
    }
    private static NativeHistory memberHistory(Review review, LoanContinuationStore.Snapshot ledger) throws IOException {
        if (ledger.uncertain()) throw conflict("Ledger EXECUTING/UNKNOWN bloqueia todos os novos emprestimos");
        var sources = new TreeMap<String, String>();
        collectSources(sources, "journal", review.inventory.orElseThrow());
        collectSources(sources, "finance", review.requestInventory.orElseThrow());
        collectSources(sources, "execution", review.executionInventory.orElseThrow());
        collectSources(sources, "loan", review.loanInventory.orElseThrow());
        for (var row : review.requests) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Contrato financeiro indisponivel"));
            if (recovery.revisionChanged() || !recovery.snapshot().requests().isEmpty()) throw conflict("Historico financeiro nao pertence a continuacao de emprestimos");
        }
        for (var row : review.executions) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Evidencia financeira indisponivel"));
            if (recovery.revisionChanged() || !recovery.snapshot().witnesses().isEmpty()) throw conflict("Historico de execucao financeira bloqueia continuacao");
        }
        var all = new ArrayList<LoanRequest>(); var active = new ArrayList<LoanRequest>();
        var accounted = new HashSet<CareerCommandGate.AppliedLoan>();
        for (var row : review.loans) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Emprestimos historicos indisponiveis"));
            var journal = review.journals.stream().filter(j -> j.owner.equals(row.owner)).findFirst().orElseThrow()
                    .recovery.orElseThrow(() -> conflict("Diario historico indisponivel"));
            var contracts = recovery.snapshot(); var receipts = journal.snapshot().receipts();
            if (!journal.snapshot().noEffectResolutions().isEmpty()) throw conflict("Reconciliacao alheia bloqueia continuacao");
            if (!row.owner.equals(LoanManagerPanel.OWNER) && (!contracts.requests().isEmpty() || !receipts.isEmpty()))
                throw conflict("Historico de outro owner bloqueia continuacao");
            if (journal.revisionChanged() && receipts.isEmpty() || recovery.revisionChanged() && contracts.requests().isEmpty())
                throw conflict("Ancora vazia divergente nao e historico de emprestimo aplicado");
            for (var request : contracts.requests()) {
                for (var old : all) if (old.key().equals(request.key()) || LoanContinuationStore.samePlayer(old, request))
                    throw conflict("Historico legado contem chaves/jogadores sobrepostos");
                all.add(request);
                var receipt = receipts.stream().filter(r -> r.intent().key().equals(request.key())).findFirst();
                var state = contracts.state(request.key());
                if (receipt.isPresent() && (!receipt.orElseThrow().intent().equals(request.intent())
                        || !journal.recordedSave().equals(request.save()))) throw conflict("Contrato/diario divergente");
                if (state == LoanRequestStore.State.APPLIED && receipt.isPresent()
                        && receipt.orElseThrow().state() == CareerCommandJournal.State.APPLIED) {
                    active.add(request); accounted.add(appliedLoan(request));
                } else {
                    boolean noEffect = state == LoanRequestStore.State.NO_EFFECT && receipt.isPresent()
                            && receipt.orElseThrow().state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED;
                    boolean closed = receipt.isEmpty() && (state == LoanRequestStore.State.CANCELLED
                            || state == LoanRequestStore.State.REFUSED || state == LoanRequestStore.State.INSUFFICIENT_DATA);
                    // Closed/no-effect history never becomes actionable again; an older anchor does not
                    // make it an unresolved effect. Preserve its exact source token without reanchoring.
                    if (!(noEffect || closed))
                        throw conflict("Pedido legado pendente/incerto/conflitante ou revisao divergente bloqueia continuacao");
                }
            }
            for (var receipt : receipts) if (receipt.intent().operation() != CareerCommandGate.Operation.LOAN_ACCEPT
                    || contracts.requests().stream().noneMatch(r -> r.intent().equals(receipt.intent())))
                throw conflict("Recibo alheio ou sem contrato bloqueia continuacao");
        }
        for (var entry : ledger.entries()) {
            for (var old : all) if (old.key().equals(entry.request().key()) || LoanContinuationStore.samePlayer(old, entry.request()))
                throw conflict("Historicos sobrepostos; nao escolher uma fonte silenciosamente");
            all.add(entry.request());
            if (entry.state() == LoanContinuationStore.State.APPLIED) { active.add(entry.request()); accounted.add(appliedLoan(entry.request())); }
        }
        // Legacy roots and this ledger are each bounded to 32 contracts; all checks are complete, never sampled.
        if (all.size() > 64) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.QUOTA, "Historico de emprestimos excede limite");
        return new NativeHistory(Map.copyOf(sources), List.copyOf(all), List.copyOf(active), Set.copyOf(accounted), List.of());
    }
    private static CareerCommandGate.AppliedLoan appliedLoan(LoanRequest request) {
        return new CareerCommandGate.AppliedLoan(request.owner(), request.key(), request.digest());
    }
    private void blockSponsorEffects(CareerSaveAssociations.Handle handle) throws IOException {
        if (new SponsorActivationStore(sponsorActivationStore, handle).read().blocksNewActivation())
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED,
                    "Patrocinio aplicado/incerto sem inclusao comprovada no save; contexto e continuacao bloqueados, sem repetir credito");
    }
    private static void collectSources(Map<String, String> sources, String type, CareerSidecarStore.NamespaceInventory inventory) throws IOException {
        for (var entry : inventory.tokens().entrySet()) {
            if (entry.getValue().equals(CareerSidecarStore.EMPTY)) throw conflict("Namespace incompleto bloqueia continuacao");
            sources.put(type + "/" + entry.getKey(), entry.getValue());
        }
    }
    private static final class NativeDispatch implements Runnable {
        private final CareerCommandGate gate;
        private final CareerCommandGate.Permit permit;
        private final LegacyLoanAdapter adapter;
        private final LegacyLoanAdapter.Plan plan;
        private final List<LoanRequest> active;
        private final List<LoanRequest> retired;
        private final java.util.concurrent.CompletableFuture<CareerCommandGate.Receipt> completion = new java.util.concurrent.CompletableFuture<>();
        private boolean entered, cancelled;
        NativeDispatch(CareerCommandGate gate, CareerCommandGate.Permit permit, LegacyLoanAdapter adapter,
                       LegacyLoanAdapter.Plan plan, List<LoanRequest> active, List<LoanRequest> retired) {
            this.gate = gate; this.permit = permit; this.adapter = adapter; this.plan = plan; this.active = active; this.retired = retired;
        }
        @Override public void run() {
            synchronized (this) { if (cancelled) return; entered = true; }
            CareerCommandDispatcher.Roots roots = null; boolean invoked = false;
            try {
                checkHistoricalLoans(adapter, active, retired); roots = adapter.roots();
                var start = gate.begin(permit, roots.primary(), roots.secondary());
                if (!start.invocationGranted()) { completion.complete(start.receipt()); return; }
                invoked = true; var status = plan.apply(); var after = adapter.roots();
                completion.complete(gate.finish(permit, status, after.primary(), after.secondary()));
            } catch (Throwable failure) {
                completion.complete(invoked ? gate.finish(permit, CareerCommandGate.Status.OUTCOME_UNKNOWN,
                        roots == null ? null : roots.primary(), roots == null ? null : roots.secondary()) : gate.cancel(permit));
            }
        }
        CareerCommandGate.Receipt await() {
            boolean interrupted = Thread.interrupted(); long deadline = System.nanoTime() + 10_000_000_000L;
            try {
                for (;;) try {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) return timeout();
                    return completion.get(left, java.util.concurrent.TimeUnit.NANOSECONDS);
                } catch (InterruptedException ignored) { interrupted = true; }
                catch (java.util.concurrent.TimeoutException timeout) { return timeout(); }
                catch (java.util.concurrent.ExecutionException impossible) { return gate.uncertain(permit); }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        private synchronized CareerCommandGate.Receipt timeout() {
            if (completion.isDone()) return completion.join();
            if (!entered) { cancelled = true; return gate.cancel(permit); }
            return gate.uncertain(permit);
        }
    }
    private LoanRequestStore.Snapshot mutateLoans(Review review, String owner, LoanMetadataChange change) throws Exception {
        offEdt();
        if (loanStore == null || review == null || !instance.equals(review.coordinator)) throw new IllegalArgumentException("Configured loan root and owned review required");
        if (!mutating.compareAndSet(false, true)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.BUSY, "Outra decisao ainda esta em curso");
        CareerCommandGate.Preparation preparation = null;
        try {
            if (!review.consumed.compareAndSet(false, true)) throw conflict("Revisao consumida; verificar novamente");
            if (operational != null) preparation = operational.begin();
            var handle = validateLoanReview(review);
            blockLegacyLoansAfterContinuation(handle);
            String token = review.loanInventory.orElseThrow().tokens().getOrDefault(owner, CareerSidecarStore.EMPTY);
            var result = change.apply(loans(handle, owner), handle, token);
            // A late reload does not undo committed metadata; surface uncertainty, never silently retarget it.
            if (!review.decoded.equals(verifier.verify(review.observed)))
                throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.OUTCOME_UNKNOWN, "Metadados podem ter sido gravados antes da troca de carreira; revisar sem repetir automaticamente");
            return result;
        } finally {
            if (preparation != null) operational.cancel(preparation);
            mutating.set(false);
        }
    }
    private CareerSaveAssociations.Handle validateLoanReview(Review review) throws IOException, InterruptedException {
        if (!review.decoded.equals(verifier.verify(review.observed))) throw conflict("Carreira mudou desde a revisao");
        var association = review.association.orElseThrow(() -> new IllegalArgumentException("Select an explicit career association"));
        var handle = association.handle();
        blockSponsorEffects(handle);
        primaryGroup(association);
        if (review.status != Status.READ_ONLY_LINKED || !association.save().equals(review.decoded.bytes())
                || !associations.inspect(handle).token().equals(association.token())
                || !review.inventory.orElseThrow().equals(journalStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(handle.career(), handle.binding()))
                || !review.loanInventory.orElseThrow().equals(loanInventory(handle))) throw conflict("Associacao, revisao ou inventario mudou; revisar novamente");
        return handle;
    }
    private CareerSaveAssociations.Handle validateSponsorReview(Review review) throws IOException, InterruptedException {
        var association = validateCareerDataReview(review); primaryGroup(association);
        if (!review.inventory.orElseThrow().equals(journalStore.inventoryBinding(association.handle().career(), association.handle().binding()))
                || !review.requestInventory.orElseThrow().equals(requestStore.inventoryBinding(association.handle().career(), association.handle().binding()))
                || !review.executionInventory.orElseThrow().equals(executionStore.inventoryBinding(association.handle().career(), association.handle().binding()))
                || !review.loanInventory.orElseThrow().equals(loanInventory(association.handle()))) throw conflict("Historicos mudaram; consulte novamente");
        return association.handle();
    }
    private Set<CareerCommandJournal.Intent> completedSponsorIntents(CareerSaveAssociations.Handle handle) throws IOException {
        var intents = new HashSet<CareerCommandJournal.Intent>();
        for (var entry : new SponsorActivationStore(sponsorActivationStore, handle).read().entries()) {
            if (entry.state() == SponsorActivationStore.State.MEMORY_APPLIED) {
                intents.add(entry.request().intent());
                if (entry.ending().isPresent()) {
                    var end = entry.ending().orElseThrow();
                    if (end.state() != SponsorActivationStore.EndState.ENDED) throw conflict("Encerramento incerto impede novo contexto");
                    intents.add(SponsorActivationStore.endIntent(entry, end));
                }
            } else if (entry.state() != SponsorActivationStore.State.NO_EFFECT) throw conflict("Credito incerto impede novo contexto");
        }
        return Set.copyOf(intents);
    }
    /** Excludes only exact completed legacy sponsor payloads from the loan-only baseline view.
     * Never edits/reanchors their journals or claims that old credits are included in the loaded save. */
    private Review withoutCompletedSponsors(Review review) throws IOException {
        var ledger = new SponsorActivationStore(sponsorActivationStore, review.association.orElseThrow().handle()).read();
        if (!ledger.permitsNextSponsor()) throw conflict("Encerre o patrocinio anterior antes de ativar outro");
        var known = new HashSet<CareerCommandJournal.Intent>();
        for (var entry : ledger.entries()) known.add(entry.request().intent());
        String owner = SponsorActivationStore.OWNER;
        for (var row : review.requests) if (row.owner.equals(owner)) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Contrato financeiro antigo indisponivel"));
            for (var request : recovery.snapshot().requests()) if (!ledger.entries().stream().anyMatch(e -> e.request().equals(request)))
                throw conflict("Contrato financeiro alheio ao historico encerrado");
        }
        for (var row : review.journals) if (row.owner.equals(owner)) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Diario antigo indisponivel"));
            if (!recovery.snapshot().noEffectResolutions().isEmpty()) throw conflict("Reconciliacao financeira exige revisao");
            for (var receipt : recovery.snapshot().receipts()) if (!known.contains(receipt.intent())
                    || receipt.state() != CareerCommandJournal.State.APPLIED && receipt.state() != CareerCommandJournal.State.NO_EFFECT_CONFIRMED)
                throw conflict("Diario financeiro pendente/incerto nao pode ser ignorado");
        }
        for (var row : review.executions) if (row.owner.equals(owner)) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Witness antigo indisponivel"));
            for (var witness : recovery.snapshot().witnesses()) if (!known.contains(witness.intent())
                    || witness.state() != FinanceExecutionWitnessStore.State.MEMORY_APPLIED && witness.state() != FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED)
                throw conflict("Witness pendente/incerto nao pode ser ignorado");
        }
        for (var row : review.loans) if (row.owner.equals(owner)) {
            var recovery = row.recovery.orElseThrow(() -> conflict("Namespace de emprestimos do patrocinador indisponivel"));
            if (!recovery.snapshot().requests().isEmpty()) throw conflict("Emprestimo alheio no namespace do patrocinador");
        }
        java.util.function.Function<Optional<CareerSidecarStore.NamespaceInventory>, Optional<CareerSidecarStore.NamespaceInventory>> filtered = inventory -> inventory.map(i -> {
            var tokens = new HashMap<>(i.tokens()); tokens.remove(owner); return new CareerSidecarStore.NamespaceInventory(tokens);
        });
        return new Review(review.coordinator, review.observed, review.decoded, review.association, review.match,
                review.journals.stream().filter(r -> !r.owner.equals(owner)).toList(), review.requests.stream().filter(r -> !r.owner.equals(owner)).toList(),
                review.status, filtered.apply(review.inventory), filtered.apply(review.requestInventory),
                review.executions.stream().filter(r -> !r.owner.equals(owner)).toList(), filtered.apply(review.executionInventory),
                review.loans.stream().filter(r -> !r.owner.equals(owner)).toList(), filtered.apply(review.loanInventory));
    }
    private static CareerSidecarStore.Failure conflict(String message) { return new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, message); }
    private static void offEdt() throws CareerSidecarStore.Failure {
        if (SwingUtilities.isEventDispatchThread()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.WRONG_THREAD, "Review career metadata off EDT");
    }
}
