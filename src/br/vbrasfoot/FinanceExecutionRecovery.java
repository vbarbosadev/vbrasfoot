package br.vbrasfoot;

import java.util.*;

/** Distinguishes lost journal acknowledgement from missing execution evidence, without rewriting history or claiming save inclusion. */
final class FinanceExecutionRecovery {
    enum Status { NO_EXECUTION_WITNESS, ARMED_UNCERTAIN, EXECUTION_UNCERTAIN, MEMORY_APPLIED_SAVE_UNPROVEN, NO_EFFECT_CONFIRMED, CONFLICT }
    record Operation(UUID operationId, CareerCommandJournal.Intent intent, Status status) { }
    private FinanceExecutionRecovery() { }
    static List<Operation> inspect(CareerCommandJournal.Snapshot journal, FinanceExecutionWitnessStore.Snapshot evidence) {
        Objects.requireNonNull(journal); Objects.requireNonNull(evidence);
        var witnesses = new HashMap<UUID, FinanceExecutionWitnessStore.Witness>(); var keys = new HashSet<String>();
        for (var value : evidence.witnesses()) {
            if (witnesses.put(value.operationId(), value) != null || !keys.add(value.intent().key())) throw new IllegalArgumentException("Duplicate witness identity");
        }
        var result = new ArrayList<Operation>(); var journalIds = new HashSet<UUID>(); var journalKeys = new HashSet<String>();
        for (var receipt : journal.receipts()) {
            if (!journalIds.add(receipt.operationId()) || !journalKeys.add(receipt.intent().key())) throw new IllegalArgumentException("Duplicate journal identity");
            var witness = witnesses.remove(receipt.operationId()); Status status;
            if (witness == null) status = keys.contains(receipt.intent().key()) ? Status.CONFLICT : Status.NO_EXECUTION_WITNESS;
            else if (!witness.intent().equals(receipt.intent())) status = Status.CONFLICT;
            else status = switch (witness.state()) {
                case ARMED -> Status.ARMED_UNCERTAIN;
                case OUTCOME_UNKNOWN -> Status.EXECUTION_UNCERTAIN;
                case MEMORY_APPLIED -> receipt.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? Status.CONFLICT : Status.MEMORY_APPLIED_SAVE_UNPROVEN;
                case NO_EFFECT_CONFIRMED -> receipt.state() == CareerCommandJournal.State.APPLIED ? Status.CONFLICT : Status.NO_EFFECT_CONFIRMED;
            };
            result.add(new Operation(receipt.operationId(), receipt.intent(), status));
        }
        for (var orphan : witnesses.values()) result.add(new Operation(orphan.operationId(), orphan.intent(), Status.CONFLICT));
        return List.copyOf(result);
    }
}
