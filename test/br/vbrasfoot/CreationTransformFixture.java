package br.vbrasfoot;

public final class CreationTransformFixture {
    public static boolean fail;
    public static int calls;
    public static int selectedIndex;
    public int az() { return 7; }
    public void V() { selectedIndex++; }
    public void AB() { calls++; if (fail) throw new IllegalStateException("owned-path-failure"); selectedIndex = az(); V(); }
    public static void AC() { calls++; if (fail) throw new IllegalStateException("owned-static-path-failure"); }
    public void pn() { calls++; }
}
