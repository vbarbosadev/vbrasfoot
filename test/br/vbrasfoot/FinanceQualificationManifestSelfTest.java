package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.FinanceSerializationTracker.*;

/** Own scalar facts only. Schema3 does not turn sampled facts into exclusive runtime coverage. */
public final class FinanceQualificationManifestSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Action action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { check(true, label); return; }
        throw new AssertionError(label);
    }
    private static FinanceQualificationManifest roundtrip(FinanceQualificationManifest value) throws Exception {
        var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { value.write(out); }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = FinanceQualificationManifest.read(in); check(in.available() == 0 && restored.equals(value), "exact manifest roundtrip including absence/negative facts"); return restored;
        }
    }
    private static FinanceSerializationEvidence.Observation observation(FinanceQualificationManifest facts, Sample sample, Reference reference) {
        var intent = new CareerCommandJournal.Intent("owned", CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64));
        var capture = new Operation(UUID.randomUUID(), intent, sample, sample, Continuity.NO_OBSERVED_DIRECT_WRITE,
                new Fields(Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, 15, Set.of("UnsafeIntField", "UnsafeLongField")), new Edge(reference, 1, Set.of("writeObject")), facts);
        var operation = new FinanceSerializationEvidence.Operation(capture, FinanceExecutionWitnessStore.State.MEMORY_APPLIED, CareerCommandJournal.State.APPLIED);
        return new FinanceSerializationEvidence.Observation(UUID.randomUUID(), UUID.randomUUID().toString(), 1, 1, new CareerSidecarStore.SaveRevision("a".repeat(64), 4),
                "a".repeat(64), "b".repeat(64), "c".repeat(64), false, FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE, List.of(operation));
    }
    private static FinanceSerializationReview.Operation review(FinanceSerializationEvidence.Observation observation) {
        return FinanceSerializationReview.inspect(new FinanceExecutionWitnessStore.Snapshot("a".repeat(64), List.of(), List.of(observation)), observation.output()).get(0);
    }
    public static void main(String[] args) throws Exception {
        roundtrip(FinanceQualificationManifest.legacy());
        roundtrip(FinanceQualificationManifest.sampled(Optional.empty(), Optional.empty(), Optional.empty()));
        for (var membership : FinanceLedgerMembership.Status.values()) roundtrip(FinanceQualificationManifest.sampled(Optional.of(membership), Optional.of(membership), Optional.empty()));
        for (var profile : LedgerSerializerProfile.Status.values()) roundtrip(FinanceQualificationManifest.sampled(Optional.empty(), Optional.empty(), Optional.of(profile)));
        rejects(() -> new FinanceQualificationManifest(2, Optional.empty(), Optional.empty(), Optional.empty()), "future manifest version rejected");
        rejects(() -> new FinanceQualificationManifest(0, Optional.of(FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST), Optional.empty(), Optional.empty()), "legacy manifest cannot contain invented membership");
        for (int mode = 0; mode < 4; mode++) {
            var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) {
                out.writeInt(mode == 0 ? 2 : mode == 3 ? 0 : 1);
                if (mode == 1) out.writeByte(2); else { out.writeBoolean(true); out.writeUTF(mode == 2 ? "FUTURE_STATUS" : "UNIQUE_IN_CLUB_LIST"); out.writeBoolean(false); out.writeBoolean(false); }
            }
            rejects(() -> FinanceQualificationManifest.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))), "unknown version/status/presence or invented legacy fact rejected");
        }
        var qualified = FinanceQualificationManifest.sampled(Optional.of(FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST), Optional.of(FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST),
                Optional.of(LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT));
        var observation = observation(qualified, Sample.MATCHED_EXPECTED_STATE, Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED);
        var report = review(observation);
         check(report.gaps().equals(Set.of(FinanceSerializationReview.Gap.MUTATION_EXCLUSIVITY_UNPROVEN, FinanceSerializationReview.Gap.REFERENCE_TABLE_AND_ALIASES_UNPROVEN,
                 FinanceSerializationReview.Gap.MULTI_ROOT_RECOVERY_UNPROVEN, FinanceSerializationReview.Gap.REFERENCE_TABLE_NOT_RECORDED)) && report.blocksInclusion() && !report.writeAuthority(), "positive sampled manifest removes only sampled-fact gaps, never causal obligations");
        var legacy = observation(FinanceQualificationManifest.legacy(), Sample.MATCHED_EXPECTED_STATE, Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED);
        check(review(legacy).gaps().containsAll(Set.of(FinanceSerializationReview.Gap.LIVE_SERIALIZER_PROFILE_NOT_RECORDED, FinanceSerializationReview.Gap.CLUB_MEMBERSHIP_CHECK_NOT_RECORDED)),
                "same apparent positive legacy values never borrow new facts");
        var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { FinanceSerializationEvidence.write(out, List.of(legacy, observation), 3); }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            check(FinanceSerializationEvidence.read(in, 3).equals(List.of(legacy, observation)) && in.available() == 0, "mixed legacy/current observations roundtrip with exact per-operation versions");
        }
        var oldBytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(oldBytes)) { FinanceSerializationEvidence.write(out, List.of(legacy), 2); }
        try (var in = new DataInputStream(new ByteArrayInputStream(oldBytes.toByteArray()))) {
            check(FinanceSerializationEvidence.read(in, 2).equals(List.of(legacy)), "schema2 decode explicitly supplies empty legacy manifest only");
        }
        rejects(() -> FinanceSerializationEvidence.write(new DataOutputStream(new ByteArrayOutputStream()), List.of(observation), 2), "versioned facts cannot be silently downgraded to schema2");
        var negative = FinanceQualificationManifest.sampled(Optional.of(FinanceLedgerMembership.Status.SHARED_LEDGER), Optional.empty(), Optional.of(LedgerSerializerProfile.Status.MEMORY_REGIONS));
        var negativeReport = review(observation(negative, Sample.LEDGER_MISMATCH, Reference.UNSUPPORTED_SERIALIZER));
        check(negativeReport.gaps().containsAll(Set.of(FinanceSerializationReview.Gap.CLUB_MEMBERSHIP_CHECK_NOT_RECORDED, FinanceSerializationReview.Gap.CLUB_MEMBERSHIP_NOT_QUALIFIED,
                FinanceSerializationReview.Gap.SAMPLED_SERIALIZER_PROFILE_UNSUPPORTED)), "negative before and missing after remain distinct; unsupported profile retained");
        rejects(() -> observation(negative, Sample.MATCHED_EXPECTED_STATE, Reference.UNSUPPORTED_SERIALIZER), "matched root cannot contradict negative membership");
        rejects(() -> observation(qualified, Sample.MATCHED_EXPECTED_STATE, Reference.UNSUPPORTED_SERIALIZER), "unsupported reference cannot carry supported profile");
        rejects(() -> observation(FinanceQualificationManifest.sampled(Optional.empty(), Optional.empty(), Optional.of(LedgerSerializerProfile.Status.MEMORY_REGIONS)),
                Sample.MATCHED_EXPECTED_STATE, Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED), "supported reference cannot carry memory-regions profile");
        System.out.println("PASS: " + checks + " versioned qualification manifest checks; own facts, no inclusion/authority.");
    }
}
