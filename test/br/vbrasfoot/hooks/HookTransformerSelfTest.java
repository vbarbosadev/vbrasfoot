package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.nio.file.*;
import java.util.*;

public final class HookTransformerSelfTest {
    private static int checks;
    private static void check(boolean condition) { if (!condition) throw new AssertionError("transformer check " + checks); checks++; }
    private static final class FixtureLoader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.HookTransformFixture", bytes, 0, bytes.length); }
    }
    private static String shape(Class<?> type) {
        List<String> result = new ArrayList<>();
        for (var field : type.getDeclaredFields()) result.add(field.getName() + ":" + field.getType().getName() + ":" + field.getModifiers());
        for (var method : type.getDeclaredMethods()) result.add(method.getName() + Arrays.toString(method.getParameterTypes()) + method.getReturnType() + method.getModifiers());
        Collections.sort(result); return result.toString();
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var stream = HookTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/HookTransformFixture.class")) { original = stream.readAllBytes(); }
        boolean rejected = false;
        try { CareerTransformer.transform(original); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected); // Production route cannot accept this synthetic class fingerprint.
        Class<?> plain = new FixtureLoader().define(original);
        Class<?> instrumented = new FixtureLoader().define(CareerTransformer.transformFixture(original));
        check(shape(plain).equals(shape(instrumented)));
        var attach = HookProbe.class.getDeclaredMethod("attach", java.util.function.Consumer.class);
        attach.setAccessible(true);
        List<HookProbe.Observation> events = new ArrayList<>();
        java.util.function.Consumer<HookProbe.Observation> sink = events::add;
        Path temp = Files.createTempDirectory(Path.of("build"), "hook-fixture-");
        Path source = Files.writeString(temp.resolve("source"), "fixture"), target = temp.resolve("target");
        instrumented.getField("source").set(null, source); instrumented.getField("target").set(null, target);
        try (var subscription = (AutoCloseable) attach.invoke(null, sink)) {
            var load = instrumented.getMethod("c", String.class, boolean.class);
            var save = instrumented.getMethod("ae", String.class);
            check((boolean) load.invoke(null, "ok", false));
            check(events.size() == 1 && events.get(0).completed());
            events.clear();
            check(!(boolean) load.invoke(null, "fallback", false));
            check(events.size() == 1 && events.get(0).completed() && events.get(0).fallback());
            events.clear();
            load.invoke(null, null, false);
            check(!events.get(0).completed());
            save.invoke(null, "ok");
            check(events.get(1).completed() && events.get(1).backup() && Files.readString(target).equals("fixture"));
            save.invoke(null, "fail");
            check(!events.get(2).completed());
            save.invoke(null, "backup-failed"); // Target already exists, Files.copy fails after close.
            check(events.get(3).completed() && !events.get(3).backup());
            boolean propagated = false;
            try { load.invoke(null, "abort", false); } catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
            check(propagated && events.get(4).aborted() && !events.get(4).completed());
            load.invoke(null, "ok", false);
            check(events.get(5).completed());
            instrumented.getConstructor().newInstance();
            check(events.get(6).operation().equals("roots-reset"));
        }
        System.out.println("PASS: " + checks + " transformed synthetic bytecode tests; no game execution.");
    }
}
