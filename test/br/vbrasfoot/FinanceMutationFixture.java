package br.vbrasfoot;

/** Owned class for body-only mutation transformer tests; never loaded by original game. */
public final class FinanceMutationFixture {
    public long nb;
    public Object nx;
    public int dO;
    public long dK;
    public int untouched;
    public void update(long balance, Object ledger, int amount, long total) { nb = balance; nx = ledger; dO = amount; dK = total; }
    public void unrelated(int value) { untouched = value; }
    public static void fail(FinanceMutationFixture missing) { missing.nb = 4; }
}
