package br.vbrasfoot;

import java.util.*;

/** Diagnostic comparison only, never an effect/save witness, lease or retry decision. */
final class FinanceContractReview {
    enum Status {
        REQUEST_WITHOUT_RECEIPT, RECEIPT_WITHOUT_REQUEST, PAYLOAD_CONFLICT,
        MATCHED_NO_EFFECT, MATCHED_HISTORICAL_EFFECT, MATCHED_UNCERTAIN, LEGACY_NO_EFFECT_WITHOUT_REQUEST
    }
    record Link(String key, Status status) {
        boolean blocksContext() { return status != Status.MATCHED_NO_EFFECT && status != Status.LEGACY_NO_EFFECT_WITHOUT_REQUEST; }
    }
    private FinanceContractReview() { }
    static List<Link> compare(FinanceRequestStore.Snapshot requests, CareerCommandJournal.Snapshot journal) {
        var payloads = new TreeMap<String, FinanceRequest>();
        for (var request : requests.requests()) payloads.put(request.key(), request);
        var receipts = new TreeMap<String, CareerCommandJournal.Receipt>();
        for (var receipt : journal.receipts()) receipts.put(receipt.intent().key(), receipt);
        var keys = new TreeSet<>(payloads.keySet()); keys.addAll(receipts.keySet());
        var result = new ArrayList<Link>();
        for (String key : keys) {
            var request = payloads.get(key); var receipt = receipts.get(key); Status status;
            if (receipt == null) status = Status.REQUEST_WITHOUT_RECEIPT;
            else if (request == null) status = receipt.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED
                    ? Status.LEGACY_NO_EFFECT_WITHOUT_REQUEST : Status.RECEIPT_WITHOUT_REQUEST;
            else if (!request.intent().equals(receipt.intent())) status = Status.PAYLOAD_CONFLICT;
            else status = switch (receipt.state()) {
                case NO_EFFECT_CONFIRMED -> Status.MATCHED_NO_EFFECT;
                case APPLIED -> Status.MATCHED_HISTORICAL_EFFECT;
                default -> Status.MATCHED_UNCERTAIN;
            };
            result.add(new Link(key, status));
        }
        return List.copyOf(result);
    }
}
