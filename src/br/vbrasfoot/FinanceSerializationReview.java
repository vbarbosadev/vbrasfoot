package br.vbrasfoot;

import java.util.*;

/** Pure diagnostic of stored facts. No positive inclusion branch or inferred live coverage for legacy schema2. */
final class FinanceSerializationReview {
    enum Gap { OUTPUT_REVISION_DIFFERS, PARTIAL_OPERATION_INVENTORY, EXECUTION_UNCERTAIN, RECEIPT_UNCERTAIN,
        ROOT_SAMPLES_NOT_MATCHED, EXECUTION_CONTINUITY_UNAVAILABLE, EXECUTION_INTERFERENCE,
        SERIALIZER_CONTINUITY_UNAVAILABLE, SERIALIZER_INTERFERENCE, NUMERIC_ARGUMENTS_INCOMPLETE,
        NUMERIC_ARGUMENT_MISMATCH, NUMERIC_ARGUMENTS_AMBIGUOUS, LEDGER_REFERENCE_INCOMPLETE,
        LEDGER_REFERENCE_MISMATCH, LEDGER_REFERENCE_AMBIGUOUS, UNSUPPORTED_LEDGER_SERIALIZER,
        LIVE_SERIALIZER_PROFILE_NOT_RECORDED, CLUB_MEMBERSHIP_CHECK_NOT_RECORDED,
        MUTATION_EXCLUSIVITY_UNPROVEN, REFERENCE_TABLE_AND_ALIASES_UNPROVEN, MULTI_ROOT_RECOVERY_UNPROVEN }
    record Operation(UUID writerObservation, UUID operation, Set<Gap> gaps) {
        Operation { Objects.requireNonNull(writerObservation); Objects.requireNonNull(operation); gaps = Set.copyOf(gaps); }
        boolean blocksInclusion() { return true; }
        boolean writeAuthority() { return false; }
    }
    private FinanceSerializationReview() { }
    static List<Operation> inspect(FinanceExecutionWitnessStore.Snapshot history, CareerSidecarStore.SaveRevision observedSave) {
        Objects.requireNonNull(history); Objects.requireNonNull(observedSave);
        if (history.serializations().size() > FinanceSerializationEvidence.MAX_OBSERVATIONS) throw new IllegalArgumentException("Bounded observations required");
        var result = new ArrayList<Operation>();
        for (var writer : history.serializations()) {
            if (writer.operations().size() > FinanceSerializationTracker.MAX_OPERATIONS) throw new IllegalArgumentException("Bounded operation inventory required");
            for (var entry : writer.operations()) {
                var gaps = EnumSet.of(Gap.LIVE_SERIALIZER_PROFILE_NOT_RECORDED, Gap.CLUB_MEMBERSHIP_CHECK_NOT_RECORDED,
                        Gap.MUTATION_EXCLUSIVITY_UNPROVEN, Gap.REFERENCE_TABLE_AND_ALIASES_UNPROVEN, Gap.MULTI_ROOT_RECOVERY_UNPROVEN);
                // No field in schemas1/2 proves these obligations; do not read current runtime or reinterpret old labels.
                if (!writer.output().equals(observedSave)) gaps.add(Gap.OUTPUT_REVISION_DIFFERS);
                if (writer.capacityExceeded()) gaps.add(Gap.PARTIAL_OPERATION_INVENTORY);
                if (entry.execution() != FinanceExecutionWitnessStore.State.MEMORY_APPLIED) gaps.add(Gap.EXECUTION_UNCERTAIN);
                if (entry.receipt() != CareerCommandJournal.State.APPLIED) gaps.add(Gap.RECEIPT_UNCERTAIN);
                var op = entry.capture();
                if (op.before() != FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE || op.after() != FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE)
                    gaps.add(Gap.ROOT_SAMPLES_NOT_MATCHED);
                switch (op.executionToSerializer()) {
                    case UNAVAILABLE -> gaps.add(Gap.EXECUTION_CONTINUITY_UNAVAILABLE);
                    case OBSERVED_INTERFERENCE -> gaps.add(Gap.EXECUTION_INTERFERENCE);
                    default -> { }
                }
                switch (writer.serializerWindow()) {
                    case UNAVAILABLE -> gaps.add(Gap.SERIALIZER_CONTINUITY_UNAVAILABLE);
                    case OBSERVED_INTERFERENCE -> gaps.add(Gap.SERIALIZER_INTERFERENCE);
                    default -> { }
                }
                switch (op.fields().status()) {
                    case VALUE_MISMATCH -> gaps.add(Gap.NUMERIC_ARGUMENT_MISMATCH);
                    case AMBIGUOUS -> gaps.add(Gap.NUMERIC_ARGUMENTS_AMBIGUOUS);
                    case EXPECTED_NUMERIC_ARGUMENTS_OBSERVED -> { }
                    default -> gaps.add(Gap.NUMERIC_ARGUMENTS_INCOMPLETE);
                }
                switch (op.edge().status()) {
                    case REFERENCE_MISMATCH -> gaps.add(Gap.LEDGER_REFERENCE_MISMATCH);
                    case AMBIGUOUS -> gaps.add(Gap.LEDGER_REFERENCE_AMBIGUOUS);
                    case UNSUPPORTED_SERIALIZER -> gaps.add(Gap.UNSUPPORTED_LEDGER_SERIALIZER);
                    case EXPECTED_LEDGER_ARGUMENT_OBSERVED -> { }
                    default -> gaps.add(Gap.LEDGER_REFERENCE_INCOMPLETE);
                }
                result.add(new Operation(writer.id(), op.operationId(), gaps));
            }
        }
        return List.copyOf(result);
    }
}
