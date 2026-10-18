package br.vbrasfoot;

/** Owned graph, no original code or UI startup. */
public final class CreationEntryFixture {
    public static Object gL;
    public static boolean valid = true, failSettings, failAfterAssignment;
    public static int completed, invalid;
    public static final IllegalStateException failure = new IllegalStateException("owned preparation failure");
    public void prepare() { oq(); }
    private void oq() {
        if (valid) { settings(); aY(1); completed++; } else invalid++;
    }
    public static void settings() { if (failSettings) throw failure; }
    public static javax.swing.JPanel aY(int panel) {
        if (panel == 1) { gL = new Object(); if (failAfterAssignment) throw failure; }
        return null;
    }
}
