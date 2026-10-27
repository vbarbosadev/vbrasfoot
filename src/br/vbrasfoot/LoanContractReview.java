package br.vbrasfoot;

import java.util.*;

/** Read-only correlation. Never reanchors, replays, or converts observed state into save-inclusion proof. */
final class LoanContractReview {
    enum Status { PENDING_WITHOUT_RECEIPT, CLOSED_WITHOUT_EXECUTION, NO_EFFECT_CONFIRMED, APPLIED_SAVE_UNPROVEN, OUTCOME_UNKNOWN,
        INTENT_CONFLICT, RECEIPT_WITHOUT_REQUEST }
    record Link(String key, Status status) {
        boolean blocksContext() { return status != Status.NO_EFFECT_CONFIRMED && status != Status.CLOSED_WITHOUT_EXECUTION; }
    }
    private LoanContractReview() { }
    static List<Link> compare(LoanRequestStore.Snapshot requests, CareerCommandJournal.Snapshot journal) {
        var receipts = new HashMap<String, CareerCommandJournal.Receipt>();
        for (var receipt : journal.receipts()) receipts.put(receipt.intent().key(), receipt);
        var seen = new HashSet<String>(); var links = new ArrayList<Link>();
        for (var request : requests.requests()) {
            seen.add(request.key()); var receipt = receipts.get(request.key());
            var decision = requests.state(request.key());
            boolean closed = decision == LoanRequestStore.State.CANCELLED || decision == LoanRequestStore.State.REFUSED
                    || decision == LoanRequestStore.State.INSUFFICIENT_DATA;
            boolean uncertain = decision == LoanRequestStore.State.EXECUTING || decision == LoanRequestStore.State.UNKNOWN;
            Status status = uncertain ? Status.OUTCOME_UNKNOWN
                    : receipt == null ? closed ? Status.CLOSED_WITHOUT_EXECUTION
                        : decision == LoanRequestStore.State.APPLIED || decision == LoanRequestStore.State.NO_EFFECT ? Status.INTENT_CONFLICT : Status.PENDING_WITHOUT_RECEIPT
                    : !receipt.intent().equals(request.intent()) ? Status.INTENT_CONFLICT
                    : closed ? Status.INTENT_CONFLICT
                    : decision == LoanRequestStore.State.APPLIED && receipt.state() != CareerCommandJournal.State.APPLIED ? Status.INTENT_CONFLICT
                    : decision == LoanRequestStore.State.NO_EFFECT && receipt.state() != CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? Status.INTENT_CONFLICT
                    : switch (receipt.state()) {
                        case NO_EFFECT_CONFIRMED -> Status.NO_EFFECT_CONFIRMED;
                        case APPLIED -> Status.APPLIED_SAVE_UNPROVEN;
                        default -> Status.OUTCOME_UNKNOWN;
                    };
            links.add(new Link(request.key(), status));
        }
        for (var receipt : journal.receipts()) if (receipt.intent().operation() == CareerCommandGate.Operation.LOAN_ACCEPT && !seen.contains(receipt.intent().key()))
            links.add(new Link(receipt.intent().key(), Status.RECEIPT_WITHOUT_REQUEST));
        return List.copyOf(links);
    }
}
