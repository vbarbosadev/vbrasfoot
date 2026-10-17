package br.vbrasfoot;

/** Own control-flow model, not decompiled game code. */
public final class HookTransformFixture {
    public static java.nio.file.Path source, target;
    public static boolean c(String name, boolean backup) {
        if ("abort".equals(name)) throw new IllegalStateException("fixture-abort");
        if (name == null) return true;
        if ("fallback".equals(name) && !backup) { c(name, true); return false; }
        new com.esotericsoftware.kryo.io.Input().close();
        return !"false".equals(name);
    }
    public static void ae(String name) {
        if (name == null) return;
        if ("abort".equals(name)) throw new IllegalStateException("fixture-abort");
        try {
            if ("fail".equals(name)) throw new java.io.IOException("fixture-write-failure");
            new com.esotericsoftware.kryo.io.Output().close();
            java.nio.file.Files.copy(source, target);
        } catch (java.io.IOException expected) { /* Models a swallowed write failure. */ }
    }
}
