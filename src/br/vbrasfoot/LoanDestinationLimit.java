package br.vbrasfoot;

/** Applies to both original listed loans and mod-negotiated loans; existing contracts are untouched. */
final class LoanDestinationLimit {
    static final int MAX = 3;
    private LoanDestinationLimit() { }
    static boolean reached(int current) {
        if (current < 0) throw new IllegalArgumentException("Negative loan count");
        return current >= MAX;
    }
}
