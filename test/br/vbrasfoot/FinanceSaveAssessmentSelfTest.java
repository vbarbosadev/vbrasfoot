package br.vbrasfoot;

import java.nio.file.Path;
import java.util.*;

/** Synthetic facts deliberately cannot produce INCLUDED, a lease, an automatic retry or a journal rewrite. */
public final class FinanceSaveAssessmentSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        var owner = new CareerSidecarStore.Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.assessment");
        var original = new CareerSidecarStore.SaveRevision("a".repeat(64), 12);
        var output = new CareerSidecarStore.SaveRevision("b".repeat(64), 13);
        var ledger = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) ledger.put(field, 0L);
        var request = new FinanceRequest(owner.career(), owner.binding(), original, owner.mod(), "once", new FinanceRequest.Club(0, 1, "Owned"),
                FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, ledger));
        Path path = Path.of("build", "output.s22").toAbsolutePath();
        var decoded = new LoadedSaveVerifier.Verified("new-session", 2, 3, path, output, false, true);
        var written = new WrittenSaveVerifier.Verified("old-session", 1, 2, path, output);
        var requests = new FinanceRequestStore.Recovery(new FinanceRequestStore.Snapshot("r", List.of(request)), original, output);
        UUID operation = UUID.randomUUID();
        for (var state : CareerCommandJournal.State.values()) {
            var receipt = new CareerCommandJournal.Receipt(operation, request.intent(), state);
            var journal = new CareerCommandJournal.Recovery(new CareerCommandJournal.Snapshot("j", List.of(receipt)), original, output,
                    state == CareerCommandJournal.State.APPLIED ? CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS
                            : state == CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? CareerCommandJournal.RecoveryStatus.NO_EFFECT_ONLY : CareerCommandJournal.RecoveryStatus.UNCERTAIN);
            var report = FinanceSaveAssessment.assess(owner, requests, journal, decoded, Optional.of(written));
            check(report.relation() == FinanceSaveAssessment.SaveRelation.EXACT_WRITTEN_BYTES_RELOADED && report.operations().get(0).operationId().orElseThrow().equals(operation),
                    "exact writer/reload bytes link to diagnostic operation UUID " + state);
            check(report.operations().get(0).inclusion() == (state == CareerCommandJournal.State.NO_EFFECT_CONFIRMED
                    ? FinanceSaveAssessment.Inclusion.NO_EFFECT_CONFIRMED : FinanceSaveAssessment.Inclusion.UNPROVEN), "even exact bytes never resolve causal inclusion " + state);
            check(report.blocksContext(), "old contract/journal anchors block regardless of exact output bytes " + state);
        }
        var applied = new CareerCommandJournal.Recovery(new CareerCommandJournal.Snapshot("j", List.of(new CareerCommandJournal.Receipt(operation, request.intent(), CareerCommandJournal.State.APPLIED))),
                original, output, CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS);
        var noWriter = FinanceSaveAssessment.assess(owner, requests, applied, decoded, Optional.empty());
        check(noWriter.relation() == FinanceSaveAssessment.SaveRelation.NO_WRITER_EVIDENCE && noWriter.blocksContext(), "ordinary original reload alone does not establish a save witness");
        var copy = new WrittenSaveVerifier.Verified("old-session", 1, 2, path.resolveSibling("copy.s22"), output);
        check(FinanceSaveAssessment.assess(owner, requests, applied, decoded, Optional.of(copy)).relation() == FinanceSaveAssessment.SaveRelation.DIFFERENT_DESTINATION,
                "byte-identical copy/save-as does not silently select original output destination");
        var beforeRestore = new WrittenSaveVerifier.Verified("old-session", 1, 2, path, original);
        check(FinanceSaveAssessment.assess(owner, requests, applied, decoded, Optional.of(beforeRestore)).relation() == FinanceSaveAssessment.SaveRelation.DIFFERENT_BYTES,
                "restore or changed output bytes stay distinct from witnessed writer bytes");
        var conflicting = new CareerCommandJournal.Intent(request.key(), request.operation(), "c".repeat(64));
        var wrongPayload = new CareerCommandJournal.Recovery(new CareerCommandJournal.Snapshot("j", List.of(new CareerCommandJournal.Receipt(operation, conflicting, CareerCommandJournal.State.NO_EFFECT_CONFIRMED))), original, output,
                CareerCommandJournal.RecoveryStatus.NO_EFFECT_ONLY);
        check(FinanceSaveAssessment.assess(owner, requests, wrongPayload, decoded, Optional.of(written)).operations().get(0).inclusion() == FinanceSaveAssessment.Inclusion.CONFLICT,
                "NO_EFFECT for a different payload cannot reconcile the stored operation");
        var empty = new CareerCommandJournal.Recovery(new CareerCommandJournal.Snapshot("j", List.of()), original, output, CareerCommandJournal.RecoveryStatus.EMPTY);
        check(FinanceSaveAssessment.assess(owner, requests, empty, decoded, Optional.of(written)).operations().get(0).operationId().isEmpty(),
                "contract without receipt never gets an invented operation UUID");
        boolean refused = false;
        try { FinanceSaveAssessment.assess(owner, requests, applied, new LoadedSaveVerifier.Verified("session", 1, 1, path, original, false, true), Optional.of(written)); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "mixed observed revisions cannot enter one report");
        System.out.println("PASS: " + checks + " per-operation save assessment checks; exact bytes never imply effect inclusion.");
    }
}
