package br.vbrasfoot;

import java.util.*;
import java.util.concurrent.atomic.*;

public final class FinanceLedgerMembershipSelfTest {
    private static int checks;
    private static final class Club { Object ledger; Club(Object ledger) { this.ledger = ledger; } }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static FinanceLedgerMembership.Status inspect(List<?> clubs, Club target, Object ledger) throws Exception {
        return CareerBridge.onEdt(() -> FinanceLedgerMembership.inspect(clubs, Club.class, target, ledger, value -> ((Club) value).ledger));
    }
    public static void main(String[] args) throws Exception {
        Object ledger = new Object(); var a = new Club(ledger); var b = new Club(new Object());
        check(inspect(List.of(a, b), a, ledger) == FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST, "distinct ledger identities in complete list pass bounded membership");
        b.ledger = ledger;
        check(inspect(List.of(a, b), a, ledger) == FinanceLedgerMembership.Status.SHARED_LEDGER, "another club sharing exact ledger refused");
        b.ledger = new Object();
        check(inspect(List.of(a, b), a, new Object()) == FinanceLedgerMembership.Status.LEDGER_REPLACED, "different expected ledger never matched by class/value");
        check(inspect(List.of(a, a), a, ledger) == FinanceLedgerMembership.Status.TARGET_NOT_UNIQUE, "duplicate target refused");
        check(inspect(List.of(a, b, b), a, ledger) == FinanceLedgerMembership.Status.TARGET_NOT_UNIQUE, "duplicate non-target also invalidates list identity");
        check(inspect(List.of(b), a, ledger) == FinanceLedgerMembership.Status.TARGET_NOT_UNIQUE, "target missing refused");
        check(inspect(List.of(a, new Object()), a, ledger) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "foreign object in club list refused");
        check(inspect(Arrays.asList(a, null), a, ledger) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "null entry refused");
        b.ledger = null;
        check(inspect(List.of(a, b), a, ledger) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "unknown other ledger cannot qualify ownership");
        check(inspect(List.of(), a, ledger) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "empty club list refused");
        check(inspect(null, a, ledger) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "missing club list refused");
        check(inspect(List.of(a), a, null) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "missing expected ledger refused");
        check(inspect(Collections.nCopies(FinanceLedgerMembership.MAX_CLUBS + 1, a), a, ledger) == FinanceLedgerMembership.Status.BUDGET_EXCEEDED, "oversized list refused without partial success");
        check(FinanceLedgerMembership.inspect(List.of(a), Club.class, a, ledger, value -> { throw new AssertionError("Must not run off EDT"); })
                == FinanceLedgerMembership.Status.WRONG_THREAD, "off EDT never reads live targets");
        check(CareerBridge.onEdt(() -> FinanceLedgerMembership.inspect(List.of(a), Club.class, a, ledger, value -> { throw new IllegalStateException("Owned getter failure"); }))
                == FinanceLedgerMembership.Status.READ_FAILED, "getter failure never promotes uniqueness");
        var changing = new ArrayList<Object>(List.of(a, b)); var reads = new AtomicInteger(); b.ledger = new Object();
        check(CareerBridge.onEdt(() -> FinanceLedgerMembership.inspect(changing, Club.class, a, ledger, value -> {
            if (reads.incrementAndGet() == 1) Collections.swap(changing, 0, 1); return ((Club) value).ledger;
        })) == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "membership reordering during scan rejected");
        var growing = new ArrayList<Object>(List.of(a));
        check(CareerBridge.onEdt(() -> FinanceLedgerMembership.inspect(growing, Club.class, a, ledger, value -> { growing.add(b); return ledger; }))
                == FinanceLedgerMembership.Status.INVALID_CLUB_LIST, "growth during scan rejected");
        check(inspect(List.of(a, b), a, ledger) == FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST, "prior failures never mutate own objects or retain a sticky ownership assertion");
        System.out.println("PASS: " + checks + " bounded club ledger membership checks; own objects, not exclusive whole-graph ownership.");
    }
}
