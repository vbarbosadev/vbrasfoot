package br.vbrasfoot;

/** Owned method graph only; never copied game code. */
public final class CreationProgressFixture {
    public static boolean fail, failPanel;
    public static final java.util.List<Integer> values = new java.util.ArrayList<>();
    public void V() { aa(); }
    private void aa() { ap(); }
    public void ap() { panel(2); at(); }
    public void at() { panel(5); ax(); }
    public void ax() { panel(6); if (fail) throw new IllegalStateException("owned progression failure"); }
    public static javax.swing.JPanel panel(int code) {
        if (failPanel) throw new IllegalStateException("owned panel failure");
        values.add(code); return new javax.swing.JPanel();
    }
}
