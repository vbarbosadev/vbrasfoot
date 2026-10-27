package br.vbrasfoot;

import java.awt.Component;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Resolves only the verified exact loaded path; never discovers identity by hash/name. */
final class NativeLoanAssociation {
    enum Reason { AMBIGUOUS, REMEMBERED_MISMATCH, UNKNOWN_PATH }
    static final class Failure extends IOException {
        private final Reason reason;
        Failure(Reason reason, String message) { super(message); this.reason = reason; }
        Reason reason() { return reason; }
    }
    private final Path metadata, preference;
    private final CareerAssociationCoordinator coordinator;
    NativeLoanAssociation(Path metadata, Path preference, CareerAssociationCoordinator coordinator) {
        this.metadata = metadata; this.preference = preference; this.coordinator = coordinator;
    }
    CareerSaveAssociations.Handle resolve(Component parent, LegacyLoanAdapter adapter) throws Exception {
        var unloadedSelection = coordinator.review(Optional.empty());
        Path source = unloadedSelection.decoded().source().toRealPath();
        Optional<CareerSaveAssociations.Handle> saved = selected(source);
        var store = new CareerSaveAssociations(metadata.resolve("associations"), source.getParent());
        if (saved.isPresent() && store.group(source).members().isEmpty())
            throw new Failure(Reason.REMEMBERED_MISMATCH, "Selecao lembrada sem vinculo existente; revise o historico sem recriar IDs");
        CareerSaveAssociations.Handle handle = coordinator.resolveLoadedCareer(unloadedSelection);
        var group = store.group(source);
        var remembered = saved;
        if (saved.isPresent() && group.members().stream().anyMatch(a -> a.handle().equals(remembered.orElseThrow())))
            saved = Optional.ofNullable(group.primary());
        var choices = group.members().stream().filter(a -> a.handle().equals(group.primary())).toList();
        if (!automatic(source, saved, choices).equals(Optional.of(handle)))
            throw new Failure(Reason.AMBIGUOUS, "Vinculo mudou durante reconhecimento da carreira");
        var review = coordinator.review(Optional.of(handle));
        if (!review.decoded().source().toRealPath().equals(source)) throw new IOException("Carreira mudou durante selecao");
        boolean metadataOnly = review.status() == CareerAssociationCoordinator.Status.ASSOCIATION_CONFLICT && review.match().isPresent()
                && (review.match().orElseThrow() == CareerSaveAssociations.Match.METADATA_CHANGED
                    || review.match().orElseThrow() == CareerSaveAssociations.Match.FILE_IDENTITY_UNAVAILABLE
                    || review.match().orElseThrow() == CareerSaveAssociations.Match.FILE_REPLACED_OR_COPIED);
        if (review.status() == CareerAssociationCoordinator.Status.REVISION_REVIEW_REQUIRED || metadataOnly) {
            // Current actual load becomes the baseline only after the coordinator's full history checks.
            // This never asserts inclusion of an earlier fee and never replays a historical command.
            coordinator.acceptNativeLoanRevision(review, adapter);
            review = coordinator.review(Optional.of(handle));
        }
        if (review.status() != CareerAssociationCoordinator.Status.READ_ONLY_LINKED)
            throw new IOException("Associacao exige revisao em Carreira e diarios; nao criar outra para contornar o bloqueio");
        if (!review.decoded().source().toRealPath().equals(source)) throw new IOException("Carreira mudou durante revisao");
        if (!selected(source).equals(Optional.of(handle))) remember(source, handle);
        return handle;
    }
    static Optional<CareerSaveAssociations.Handle> automatic(Path source, Optional<CareerSaveAssociations.Handle> saved,
                                                            List<CareerSaveAssociations.Association> inventory) throws IOException {
        var exact = inventory.stream().filter(value -> value.directory().resolve(value.name()).equals(source)).toList();
        if (exact.size() > 1) throw new Failure(Reason.AMBIGUOUS, "Associacoes ambiguas para o caminho carregado");
        if (saved.isPresent() && (exact.isEmpty() || !exact.get(0).handle().equals(saved.orElseThrow())))
            throw new Failure(Reason.REMEMBERED_MISMATCH, "Selecao lembrada nao corresponde ao caminho carregado");
        if (!exact.isEmpty()) return Optional.of(exact.get(0).handle());
        // Neither matching bytes nor their absence prove that an unknown path is a new career.
        // Refuse even changed copies/renames rather than creating an empty history bypass.
        if (!inventory.isEmpty()) throw new Failure(Reason.UNKNOWN_PATH, "Caminho desconhecido com carreiras ja associadas; revisar identidade");
        return Optional.empty();
    }
    private static UUID uuid(Path path) throws IOException {
        try { return UUID.fromString(path.getFileName().toString()); }
        catch (IllegalArgumentException invalid) { throw new IOException("Associacao com caminho desconhecido; revisar sem ignorar", invalid); }
    }
    Optional<CareerSaveAssociations.Handle> selected(Path source) throws IOException {
        CareerSidecarStore.plain(preference);
        if (!Files.exists(preference)) return Optional.empty();
        if (Files.size(preference) > 8192) throw new IOException("Selecao de carreira excede limite");
        var values = new Properties(); try (var input = Files.newInputStream(preference)) { values.load(input); }
        if (!"1".equals(values.getProperty("format")) || !values.stringPropertyNames().equals(Set.of("format", "source", "career", "binding")))
            throw new IOException("Selecao de carreira invalida; revisar sem sobrescrever");
        try {
            Path recorded = Path.of(values.getProperty("source"));
            if (!recorded.isAbsolute() || !recorded.equals(recorded.normalize())) throw new IllegalArgumentException("Invalid source path");
            var handle = new CareerSaveAssociations.Handle(UUID.fromString(values.getProperty("career")), UUID.fromString(values.getProperty("binding")));
            return source.equals(recorded) ? Optional.of(handle) : Optional.empty();
        }
        catch (IllegalArgumentException invalid) { throw new IOException("Selecao de carreira invalida", invalid); }
    }
    void remember(Path source, CareerSaveAssociations.Handle handle) throws IOException {
        selected(source); // Validate existing file before replacing only our own preference format.
        CareerSidecarStore.plain(preference.getParent()); Files.createDirectories(preference.getParent());
        var values = new Properties(); values.setProperty("format", "1"); values.setProperty("source", source.toString());
        values.setProperty("career", handle.career().toString()); values.setProperty("binding", handle.binding().toString());
        Path temporary = Files.createTempFile(preference.getParent(), "native-loan-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "Explicit native loan association selection"); }
            CareerSidecarStore.plain(preference);
            Files.move(temporary, preference, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
