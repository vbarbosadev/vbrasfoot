package br.vbrasfoot.hooks;

public final class AsyncCreationTransformerSelfTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("async transformer " + checks); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.AsyncBoundaryFixture", bytes, 0, bytes.length); }
    }
    public static void main(String[] args) throws Exception {
        byte[] input;
        try (var stream = AsyncCreationTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/AsyncBoundaryFixture.class")) { input = stream.readAllBytes(); }
        boolean refused = false;
        try { AsyncCreationTransformer.transform("a.hk", input); } catch (java.io.IOException expected) { refused = true; }
        check(refused);
        byte[] scheduler = AsyncCreationTransformer.transformFixture(input, true, "br/vbrasfoot/AsyncBoundaryFixture$Task", "fixture", false);
        Class<?> type = new Loader().define(scheduler);
        Object instance = type.getConstructor().newInstance(); type.getMethod("oR").invoke(instance); check(true);
        type.getField("fail").set(null, true);
        boolean propagated = false;
        try { type.getMethod("oR").invoke(instance); } catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
        check(propagated);
        byte[] run = AsyncCreationTransformer.transformFixture(input, false, "ignored", "fixture", false);
        type = new Loader().define(run); instance = type.getConstructor().newInstance(); type.getMethod("run").invoke(instance);
        check(type.getField("runs").getInt(null) == 1);
        type.getField("fail").set(null, true); propagated = false;
        try { type.getMethod("run").invoke(instance); } catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
        check(propagated);
        Class<?> plain = new Loader().define(input);
        check(type.getDeclaredMethods().length == plain.getDeclaredMethods().length && type.getDeclaredFields().length == plain.getDeclaredFields().length);
        refused = false;
        try { AsyncCreationTransformer.transformFixture(input, true, "wrong/Task", "fixture", false); } catch (java.io.IOException expected) { refused = true; }
        check(refused);
        System.out.println("PASS: " + checks + " async synthetic bytecode checks; no original task execution.");
    }
}
