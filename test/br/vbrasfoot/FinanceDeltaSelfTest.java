package br.vbrasfoot;

import java.util.*;

public final class FinanceDeltaSelfTest {
    private static int checks;
    private interface Action { void run(); }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void refuses(Action action, String label) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { checks++; return; } throw new AssertionError(label);
    }
    private static FinanceDelta.Snapshot snapshot(long balance, String field, long value) {
        var ledger = new HashMap<String, Long>(); FinanceDelta.FIELDS.forEach(name -> ledger.put(name, 0L));
        if (field != null) ledger.put(field, value); return new FinanceDelta.Snapshot(balance, ledger);
    }
    public static void main(String[] args) {
        var before = snapshot(1000, null, 0);
        var credited = FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, 100);
        check(credited.balance() == 1100 && credited.ledger().get("dO") == 100 && before.balance() == 1000, "credit predicts balance and exact ledger delta without mutation");
        var debited = FinanceDelta.predict(credited, FinanceDelta.Direction.DEBIT_RAW_7, 20);
        check(debited.balance() == 1080 && debited.ledger().get("dQ") == 20 && debited.ledger().get("dO") == 100, "debit touches only correct mapped field");
        refuses(() -> FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, 0), "zero amount");
        refuses(() -> FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, -1), "negative credit");
        refuses(() -> FinanceDelta.predict(snapshot(Long.MAX_VALUE, null, 0), FinanceDelta.Direction.CREDIT_RAW_6, 1), "long balance overflow");
        refuses(() -> FinanceDelta.predict(snapshot(Long.MIN_VALUE, null, 0), FinanceDelta.Direction.DEBIT_RAW_7, 1), "long balance underflow");
        refuses(() -> FinanceDelta.predict(snapshot(0, null, 0), FinanceDelta.Direction.DEBIT_RAW_7, 1), "adapter never creates debt");
        refuses(() -> FinanceDelta.predict(snapshot(1000, "dO", Integer.MAX_VALUE), FinanceDelta.Direction.CREDIT_RAW_6, 1), "credit int ledger overflow");
        refuses(() -> FinanceDelta.predict(snapshot(1000, "dQ", Integer.MAX_VALUE), FinanceDelta.Direction.DEBIT_RAW_7, 1), "debit int ledger overflow");
        var intSum = new HashMap<>(before.ledger()); intSum.put("dL", (long) Integer.MAX_VALUE); intSum.put("dM", 1L);
        refuses(() -> FinanceDelta.predict(new FinanceDelta.Snapshot(1000, intSum), FinanceDelta.Direction.CREDIT_RAW_6, 1), "original IADD overflow refused before long conversion");
        refuses(() -> FinanceDelta.predict(snapshot(1000, "dN", Long.MAX_VALUE), FinanceDelta.Direction.CREDIT_RAW_6, 1), "income aggregate overflow after int update");
        var expense = new HashMap<>(before.ledger()); expense.put("dR", Long.MAX_VALUE); expense.put("dS", 1L);
        refuses(() -> FinanceDelta.predict(new FinanceDelta.Snapshot(1000, expense), FinanceDelta.Direction.DEBIT_RAW_7, 1), "expense aggregate overflow refused");
        var net = new HashMap<>(before.ledger()); net.put("dN", Long.MIN_VALUE); net.put("dR", 1L);
        refuses(() -> FinanceDelta.predict(new FinanceDelta.Snapshot(1000, net), FinanceDelta.Direction.CREDIT_RAW_6, 1), "already corrupt net overflow refused before changes");
        var input = new HashMap<>(before.ledger()); var detached = new FinanceDelta.Snapshot(1, input); input.put("dO", 9L);
        check(detached.ledger().get("dO") == 0, "snapshot detached");
        check(FinanceDelta.predict(snapshot(5, null, 0), FinanceDelta.Direction.DEBIT_RAW_7, 5).balance() == 0, "exactly available balance allowed");
        System.out.println("PASS: " + checks + " financial delta arithmetic checks; no original game mutation.");
    }
}
