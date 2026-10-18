package br.vbrasfoot;

import java.util.*;

/** Exact arithmetic for two fingerprinted raw ledger categories only; no sponsorship semantics asserted. */
final class FinanceDelta {
    enum Direction { CREDIT_RAW_6, DEBIT_RAW_7 }
    static final Set<String> FIELDS = Set.of("dK", "dL", "dM", "dN", "dO", "dP", "dQ", "dR", "dS", "dT", "dU", "dV", "dW", "dX");
    private static final Set<String> INTS = Set.of("dL", "dM", "dO", "dP", "dQ", "dT", "dU", "dV", "dW", "dX");
    record Snapshot(long balance, Map<String, Long> ledger) {
        Snapshot {
            ledger = Map.copyOf(ledger);
            if (!ledger.keySet().equals(FIELDS)) throw new IllegalArgumentException("Complete mapped ledger required");
            for (String field : INTS) Math.toIntExact(ledger.get(field));
        }
    }
    private FinanceDelta() { }
    static Snapshot predict(Snapshot before, Direction direction, int amount) {
        Objects.requireNonNull(before); Objects.requireNonNull(direction);
        if (amount <= 0) throw new IllegalArgumentException("Positive original int amount required");
        validateTotals(before.ledger());
        var changed = new HashMap<>(before.ledger()); String field = direction == Direction.CREDIT_RAW_6 ? "dO" : "dQ";
        changed.put(field, (long) Math.addExact(Math.toIntExact(changed.get(field)), amount));
        long balance = direction == Direction.CREDIT_RAW_6 ? Math.addExact(before.balance(), amount) : Math.subtractExact(before.balance(), amount);
        if (direction == Direction.DEBIT_RAW_7 && balance < 0) throw new IllegalArgumentException("Debit cannot create debt in this limited adapter");
        validateTotals(changed);
        return new Snapshot(balance, changed);
    }
    private static void validateTotals(Map<String, Long> values) {
        // Original C.ez starts with IADD (dL+dM), not a long addition.
        long income = Math.addExact(Math.toIntExact(values.get("dL")), Math.toIntExact(values.get("dM")));
        for (String field : List.of("dN", "dO", "dP")) income = Math.addExact(income, values.get(field));
        long expense = 0;
        for (String field : List.of("dQ", "dR", "dS", "dT", "dU", "dV")) expense = Math.addExact(expense, values.get(field));
        Math.subtractExact(income, expense);
    }
}
