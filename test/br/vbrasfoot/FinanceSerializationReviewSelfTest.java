package br.vbrasfoot;

import java.util.*;
import static br.vbrasfoot.FinanceSerializationTracker.*;
import static br.vbrasfoot.FinanceSerializationReview.Gap;

public final class FinanceSerializationReviewSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static FinanceSerializationReview.Operation review(Emission numeric, Reference edge, Continuity execution, FinanceMutationAssessment.Status window,
            boolean matched, boolean capacity, boolean sameOutput, boolean applied) {
        UUID operation = UUID.randomUUID();
        var intent = new CareerCommandJournal.Intent("owned", CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64));
        var capture = new Operation(operation, intent, matched ? Sample.MATCHED_EXPECTED_STATE : Sample.STATE_MISMATCH, Sample.MATCHED_EXPECTED_STATE, execution,
                new Fields(numeric, numeric == Emission.MISSING || numeric == Emission.UNAVAILABLE ? 0 : numeric == Emission.PARTIAL ? 1 : 15,
                        numeric == Emission.MISSING || numeric == Emission.UNAVAILABLE ? Set.of() : Set.of("UnsafeIntField")),
                new Edge(edge, edge == Reference.MISSING || edge == Reference.UNAVAILABLE ? 0 : edge == Reference.AMBIGUOUS ? 2 : 1,
                        edge == Reference.MISSING || edge == Reference.UNAVAILABLE ? Set.of() : Set.of("writeObject")));
        var entry = new FinanceSerializationEvidence.Operation(capture, applied ? FinanceExecutionWitnessStore.State.MEMORY_APPLIED : FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN,
                applied ? CareerCommandJournal.State.APPLIED : CareerCommandJournal.State.OUTCOME_UNKNOWN);
        var output = new CareerSidecarStore.SaveRevision("b".repeat(64), 4);
        var observation = new FinanceSerializationEvidence.Observation(UUID.randomUUID(), UUID.randomUUID().toString(), 1, 1, output,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), capacity, window, List.of(entry));
        var history = new FinanceExecutionWitnessStore.Snapshot("d".repeat(64), List.of(new FinanceExecutionWitnessStore.Witness(operation, intent, UUID.randomUUID(), 1, entry.execution())), List.of(observation));
        var result = FinanceSerializationReview.inspect(history, sameOutput ? output : new CareerSidecarStore.SaveRevision("c".repeat(64), 4)).get(0);
        check(result.writerObservation().equals(observation.id()) && result.operation().equals(operation), "review retains exact writer/operation UUID pair");
        check(result.blocksInclusion() && !result.writeAuthority(), "every diagnostic remains blocking and read-only");
        return result;
    }
    public static void main(String[] args) {
        var ideal = review(Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED,
                Continuity.NO_OBSERVED_DIRECT_WRITE, FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE, true, false, true, true);
        check(ideal.gaps().equals(Set.of(Gap.LIVE_SERIALIZER_PROFILE_NOT_RECORDED, Gap.CLUB_MEMBERSHIP_CHECK_NOT_RECORDED,
                Gap.MUTATION_EXCLUSIVITY_UNPROVEN, Gap.REFERENCE_TABLE_AND_ALIASES_UNPROVEN, Gap.MULTI_ROOT_RECOVERY_UNPROVEN)), "ideal legacy captured facts never borrow newer live qualification or claim causal inclusion");
        boolean immutable = false; try { ideal.gaps().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "published gap set immutable");
        var uncertain = review(Emission.VALUE_MISMATCH, Reference.REFERENCE_MISMATCH, Continuity.OBSERVED_INTERFERENCE,
                FinanceMutationAssessment.Status.OBSERVED_INTERFERENCE, false, true, false, false);
        check(uncertain.gaps().containsAll(Set.of(Gap.OUTPUT_REVISION_DIFFERS, Gap.PARTIAL_OPERATION_INVENTORY, Gap.EXECUTION_UNCERTAIN,
                Gap.RECEIPT_UNCERTAIN, Gap.ROOT_SAMPLES_NOT_MATCHED, Gap.EXECUTION_INTERFERENCE, Gap.SERIALIZER_INTERFERENCE,
                Gap.NUMERIC_ARGUMENT_MISMATCH, Gap.LEDGER_REFERENCE_MISMATCH)), "negative causal facts retained independently, not collapsed into generic unproven");
        for (var numeric : List.of(Emission.MISSING, Emission.PARTIAL, Emission.UNAVAILABLE)) {
            var report = review(numeric, Reference.MISSING, Continuity.UNAVAILABLE, FinanceMutationAssessment.Status.UNAVAILABLE, true, false, true, true);
            check(report.gaps().containsAll(Set.of(Gap.NUMERIC_ARGUMENTS_INCOMPLETE, Gap.LEDGER_REFERENCE_INCOMPLETE,
                    Gap.EXECUTION_CONTINUITY_UNAVAILABLE, Gap.SERIALIZER_CONTINUITY_UNAVAILABLE)), "missing/partial/unavailable never upgraded by root samples: " + numeric);
        }
        var ambiguous = review(Emission.AMBIGUOUS, Reference.AMBIGUOUS, Continuity.NO_OBSERVED_DIRECT_WRITE,
                FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE, true, false, true, true);
        check(ambiguous.gaps().containsAll(Set.of(Gap.NUMERIC_ARGUMENTS_AMBIGUOUS, Gap.LEDGER_REFERENCE_AMBIGUOUS)), "duplicate argument ambiguity shown separately");
        var unsupported = review(Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, Reference.UNSUPPORTED_SERIALIZER, Continuity.NO_OBSERVED_DIRECT_WRITE,
                FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE, true, false, true, true);
        check(unsupported.gaps().contains(Gap.UNSUPPORTED_LEDGER_SERIALIZER), "unsupported serializer shown even with all numeric arguments");
        check(FinanceSerializationReview.inspect(new FinanceExecutionWitnessStore.Snapshot("EMPTY", List.of()), new CareerSidecarStore.SaveRevision("a".repeat(64), 1)).isEmpty(),
                "legacy empty history creates no invented observations or positive evidence");
        System.out.println("PASS: " + checks + " persisted serialization gap review checks; own records, no inclusion or authority.");
    }
}
