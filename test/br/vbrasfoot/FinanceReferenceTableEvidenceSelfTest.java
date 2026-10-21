package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.KryoLedgerReferenceTrace.*;

/** Owned scalar facts only; no live capture or financial authority. */
public final class FinanceReferenceTableEvidenceSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Action action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void roundtrip(Summary summary) throws Exception {
        var fact = new FinanceReferenceTableEvidence(1, summary); var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { fact.write(out); }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            check(FinanceReferenceTableEvidence.read(in).equals(fact) && in.available() == 0, "exact bounded summary roundtrip");
        }
    }
    private static FinanceSerializationEvidence.Observation observation(Optional<FinanceReferenceTableEvidence> table, FinanceSerializationTracker.Reference reference) {
        var intent = new CareerCommandJournal.Intent("owned-ref-fact", CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64));
        var capture = new FinanceSerializationTracker.Operation(UUID.randomUUID(), intent, FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE,
                FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE, FinanceSerializationTracker.Continuity.NO_OBSERVED_DIRECT_WRITE,
                new FinanceSerializationTracker.Fields(FinanceSerializationTracker.Emission.MISSING, 0, Set.of()),
                new FinanceSerializationTracker.Edge(reference, reference == FinanceSerializationTracker.Reference.MISSING ? 0 : 1,
                        reference == FinanceSerializationTracker.Reference.MISSING ? Set.of() : Set.of("writeObject")));
        return new FinanceSerializationEvidence.Observation(UUID.randomUUID(), UUID.randomUUID().toString(), 1, 1, new CareerSidecarStore.SaveRevision("b".repeat(64), 4),
                "a".repeat(64), "b".repeat(64), "c".repeat(64), false, FinanceMutationAssessment.Status.UNAVAILABLE,
                List.of(new FinanceSerializationEvidence.Operation(capture, FinanceExecutionWitnessStore.State.MEMORY_APPLIED, CareerCommandJournal.State.APPLIED, table)));
    }
    public static void main(String[] args) throws Exception {
        roundtrip(Summary.missing());
        roundtrip(new Summary(Resolution.NEW_CONTENT_RETURNED, 1, 0, 1, 0, 2, 1, true));
        roundtrip(new Summary(Resolution.PRIOR_CONTENT_REFERENCE, 1, 1, 1, 2, 2, 1, true));
        roundtrip(new Summary(Resolution.PRIOR_REFERENCE_UNPROVEN, 0, 1, 0, 0, 0, 1, true));
        roundtrip(new Summary(Resolution.UNTRACKED_CONTENT_RETURNED, 0, 0, 2, 1, 2, 1, false));
        roundtrip(new Summary(Resolution.AMBIGUOUS, 1, 1, 1, 0, 2, 2, true));
        roundtrip(new Summary(Resolution.UNAVAILABLE, 0, 0, 0, 0, 0, 0, true));
        roundtrip(new Summary(Resolution.CAPACITY_EXCEEDED, 0, 128, 0, 128, 0, 0, true));
        rejects(() -> new FinanceReferenceTableEvidence(0, Summary.missing()), "absence must be optional, not invented version0 facts");
        rejects(() -> new FinanceReferenceTableEvidence(2, Summary.missing()), "future version refused");
        for (var status : List.of(Resolution.NEW_CONTENT_RETURNED, Resolution.PRIOR_CONTENT_REFERENCE, Resolution.PRIOR_REFERENCE_UNPROVEN, Resolution.UNTRACKED_CONTENT_RETURNED))
            rejects(() -> new FinanceReferenceTableEvidence(1, new Summary(status, 0, 0, 0, 0, 0, 1, false)), "positive encoding needs supporting counts/resolver");
        rejects(() -> new FinanceReferenceTableEvidence(1, new Summary(Resolution.MISSING, 0, 0, 0, 0, 0, 1, false)), "missing cannot hide field call");
        rejects(() -> new FinanceReferenceTableEvidence(1, new Summary(Resolution.UNAVAILABLE, -1, 0, 0, 0, 0, 0, false)), "negative count refused");
        rejects(() -> new FinanceReferenceTableEvidence(1, new Summary(Resolution.UNAVAILABLE, 1, 0, 0, 2, 0, 0, false)), "outside events bounded by markers/bodies");
        rejects(() -> new FinanceReferenceTableEvidence(1, new Summary(Resolution.CAPACITY_EXCEEDED, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0, 0, true)), "overflow cannot bypass budget");
        var trace = new KryoLedgerReferenceTrace(); var call = trace.beginField(); trace.marker(3, 4, 1, true); trace.contentReturned(true); trace.endField(call); trace.reset(); trace.resetCompleted();
        roundtrip(trace.snapshot());
        var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeUTF("MISSING"); for (int i = 0; i < 6; i++) out.writeInt(0); out.writeByte(2);
        }
        rejects(() -> FinanceReferenceTableEvidence.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))), "boolean marker not coerced from arbitrary byte");
        byte[] truncated = Arrays.copyOf(bytes.toByteArray(), 5);
        rejects(() -> FinanceReferenceTableEvidence.read(new DataInputStream(new ByteArrayInputStream(truncated))), "truncated facts refused");
        for (int mode : List.of(0, 1)) {
            var invalid = new ByteArrayOutputStream(); try (var out = new DataOutputStream(invalid)) {
                out.writeInt(mode == 0 ? 2 : 1); out.writeUTF(mode == 1 ? "FUTURE_RESOLUTION" : "MISSING");
                for (int i = 0; i < 6; i++) out.writeInt(0); out.writeBoolean(false);
            }
            rejects(() -> FinanceReferenceTableEvidence.read(new DataInputStream(new ByteArrayInputStream(invalid.toByteArray()))), "future wire version/status refused");
        }
        var legacy = observation(Optional.empty(), FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED);
        for (int schema : List.of(2, 3, 4)) {
            var oldBytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(oldBytes)) { FinanceSerializationEvidence.write(out, List.of(legacy), schema); }
            var restored = FinanceSerializationEvidence.read(new DataInputStream(new ByteArrayInputStream(oldBytes.toByteArray())), schema);
            check(restored.equals(List.of(legacy)) && restored.get(0).operations().get(0).referenceTable().isEmpty(), "old format absence stays absent through each supported codec");
        }
        var positive = new FinanceReferenceTableEvidence(1, trace.snapshot());
        var observed = observation(Optional.of(positive), FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED);
        var mixedBytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(mixedBytes)) { FinanceSerializationEvidence.write(out, List.of(legacy, observed), 4); }
        check(FinanceSerializationEvidence.read(new DataInputStream(new ByteArrayInputStream(mixedBytes.toByteArray())), 4).equals(List.of(legacy, observed)), "schema4 preserves per-observation absence/current version in mixed history");
        for (int schema : List.of(2, 3)) rejects(() -> FinanceSerializationEvidence.write(new DataOutputStream(new ByteArrayOutputStream()), List.of(observed), schema), "new facts cannot silently downgrade");
        rejects(() -> observation(Optional.of(positive), FinanceSerializationTracker.Reference.MISSING), "positive field encoding cannot contradict missing nx call");
        var missing = observation(Optional.of(new FinanceReferenceTableEvidence(1, Summary.missing())), FinanceSerializationTracker.Reference.MISSING);
        var presenceBytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(presenceBytes)) { FinanceSerializationEvidence.write(out, List.of(missing), 4); }
        var encodedFact = new ByteArrayOutputStream(); try (var out = new DataOutputStream(encodedFact)) { missing.operations().get(0).referenceTable().orElseThrow().write(out); }
        byte[] corrupt = presenceBytes.toByteArray(); corrupt[corrupt.length - encodedFact.size() - 1] = 2;
        rejects(() -> FinanceSerializationEvidence.read(new DataInputStream(new ByteArrayInputStream(corrupt)), 4), "invalid optional presence not coerced");
        var op = observed.operations().get(0).capture(); var entry = new FinanceSerializationTracker.ReferenceTrace(op.operationId(), trace.snapshot());
        rejects(() -> new FinanceSerializationTracker.Capture(true, false, List.of(op), List.of(entry, entry)), "duplicate table UUID refused");
        rejects(() -> new FinanceSerializationTracker.Capture(true, false, List.of(op), List.of(new FinanceSerializationTracker.ReferenceTrace(UUID.randomUUID(), trace.snapshot()))), "foreign table UUID cannot join by list position");
        for (var writer : List.of(legacy, observed, missing)) {
            var review = FinanceSerializationReview.inspect(new FinanceExecutionWitnessStore.Snapshot("a".repeat(64), List.of(), List.of(writer)), writer.output()).get(0);
            check(review.blocksInclusion() && !review.writeAuthority() && review.gaps().contains(FinanceSerializationReview.Gap.REFERENCE_TABLE_AND_ALIASES_UNPROVEN), "all persisted fact variants still block global graph/inclusion authority");
            check(review.gaps().contains(FinanceSerializationReview.Gap.REFERENCE_TABLE_NOT_RECORDED) == writer.operations().get(0).referenceTable().isEmpty(), "absent fact and captured missing encoding have distinct gaps");
            check(review.gaps().contains(FinanceSerializationReview.Gap.REFERENCE_TABLE_ENCODING_NOT_QUALIFIED) == (writer == missing), "missing encoding not promoted to qualifying table fact");
        }
        var aliased = observation(Optional.of(new FinanceReferenceTableEvidence(1, new Summary(Resolution.PRIOR_CONTENT_REFERENCE, 1, 1, 1, 2, 0, 1, true))),
                FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED);
        var aliasReview = FinanceSerializationReview.inspect(new FinanceExecutionWitnessStore.Snapshot("a".repeat(64), List.of(), List.of(aliased)), aliased.output()).get(0);
        check(aliasReview.gaps().contains(FinanceSerializationReview.Gap.ADDITIONAL_LEDGER_USES_OBSERVED)
                && aliasReview.gaps().contains(FinanceSerializationReview.Gap.REFERENCE_TABLE_AND_ALIASES_UNPROVEN)
                && !aliasReview.gaps().contains(FinanceSerializationReview.Gap.REFERENCE_TABLE_ENCODING_NOT_QUALIFIED), "qualified earlier content reference still reports additional uses and unproven ownership");
        System.out.println("PASS: " + checks + " versioned reference-table fact checks; scalar only, no graph ownership/inclusion/authority.");
    }
}
