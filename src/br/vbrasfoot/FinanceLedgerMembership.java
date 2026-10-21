package br.vbrasfoot;

import java.util.*;
import javax.swing.SwingUtilities;

/** Bounded current club-list identity check only. Not exclusive ownership of the whole serialized graph. */
final class FinanceLedgerMembership {
    static final int MAX_CLUBS = 16384;
    enum Status { UNIQUE_IN_CLUB_LIST, SHARED_LEDGER, TARGET_NOT_UNIQUE, LEDGER_REPLACED, INVALID_CLUB_LIST, READ_FAILED, BUDGET_EXCEEDED, WRONG_THREAD }
    interface LedgerSource { Object ledger(Object club) throws Exception; }
    private FinanceLedgerMembership() { }
    static Status inspect(List<?> clubs, Class<?> clubType, Object target, Object ledger, LedgerSource source) {
        if (!SwingUtilities.isEventDispatchThread()) return Status.WRONG_THREAD;
        if (clubs == null || clubType == null || target == null || target.getClass() != clubType || ledger == null || source == null) return Status.INVALID_CLUB_LIST;
        long started = System.nanoTime();
        try {
            int size = clubs.size(); if (size < 1) return Status.INVALID_CLUB_LIST;
            if (size > MAX_CLUBS) return Status.BUDGET_EXCEEDED;
            Object[] selected = new Object[size]; var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            int targets = 0;
            for (int i = 0; i < size; i++) {
                Object club = clubs.get(i);
                if (club == null || club.getClass() != clubType) return Status.INVALID_CLUB_LIST;
                if (!seen.add(club)) return Status.TARGET_NOT_UNIQUE;
                if (club == target) targets++;
                selected[i] = club;
            }
            if (targets != 1) return Status.TARGET_NOT_UNIQUE;
            for (int i = 0; i < size; i++) {
                if (System.nanoTime() - started > 250_000_000L) return Status.BUDGET_EXCEEDED;
                Object current = source.ledger(selected[i]);
                if (selected[i] == target && current != ledger) return Status.LEDGER_REPLACED;
                if (selected[i] != target && current == ledger) return Status.SHARED_LEDGER;
                if (current == null) return Status.INVALID_CLUB_LIST;
            }
            if (clubs.size() != size) return Status.INVALID_CLUB_LIST;
            for (int i = 0; i < size; i++) if (clubs.get(i) != selected[i]) return Status.INVALID_CLUB_LIST;
            if (System.nanoTime() - started > 250_000_000L) return Status.BUDGET_EXCEEDED;
            return Status.UNIQUE_IN_CLUB_LIST;
        } catch (Throwable failure) { return Status.READ_FAILED; }
    }
}
