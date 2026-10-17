package br.vbrasfoot;

public final class CreationTransformFixture {
    public static boolean fail;
    public static int calls;
    public void AB() { calls++; if (fail) throw new IllegalStateException("owned-path-failure"); }
    public static void AC() { calls++; if (fail) throw new IllegalStateException("owned-static-path-failure"); }
    public void pn() { calls++; }
}
