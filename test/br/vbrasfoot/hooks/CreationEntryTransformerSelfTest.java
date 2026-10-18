package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.util.*;

public final class CreationEntryTransformerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.CreationEntryFixture", bytes, 0, bytes.length); }
    }
    private static String shape(Class<?> type) {
        var items = new ArrayList<String>();
        for (var f : type.getDeclaredFields()) items.add(f.getName() + f.getType() + f.getModifiers());
        for (var m : type.getDeclaredMethods()) items.add(m.getName() + m.getReturnType() + Arrays.toString(m.getParameterTypes()) + m.getModifiers());
        Collections.sort(items); return items.toString();
    }
    private static Object get(Object value, String name) throws Exception {
        var method = value.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(value);
    }
    private static List<?> events() throws Exception {
        var method = HookProbe.class.getDeclaredMethod("creationEntries"); method.setAccessible(true);
        return (List<?>) get(method.invoke(null), "events");
    }
    private static List<String> stages(List<?> events) throws Exception {
        var values = new ArrayList<String>(); for (var event : events) values.add((String) get(event, "stage")); return values;
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = CreationEntryTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/CreationEntryFixture.class")) { original = input.readAllBytes(); }
        for (String name : List.of("a.hU", "best.S", "unknown")) {
            boolean refused = false; try { CreationEntryTransformer.transform(name, original); } catch (java.io.IOException expected) { refused = true; }
            check(refused, "original fingerprint/allowlist refuses fixture " + name);
        }
        byte[] instrumented = CreationEntryTransformer.transformFixture(CreationEntryTransformer.transformFixture(original, true, false), false, false);
        var plain = new Loader().define(original); var hooked = new Loader().define(instrumented);
        check(shape(plain).equals(shape(hooked)), "field/method/access/shape unchanged");
        var attach = HookProbe.class.getDeclaredMethod("attach", java.util.function.Consumer.class); attach.setAccessible(true);
        try (var ignored = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) event -> { })) {
            hooked.getMethod("prepare").invoke(hooked.getConstructor().newInstance());
            check(hooked.getField("gL").get(null) != null && hooked.getField("completed").getInt(null) == 1, "assignment and original caller after null result preserved");
            check(stages(events()).equals(List.of("PREPARE_ENTER", "PREPARE_SETTINGS_RETURN", "PREPARE_PANEL_ASSIGNED", "PREPARE_PANEL_RETURN", "PREPARE_RETURN")), "settings/assignment/return nesting preserved");
            check(hooked.getMethod("aY", int.class).invoke(null, 2) == null, "original null return not replaced with captured panel");
        }
        try (var ignored = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) event -> { })) {
            hooked.getField("valid").setBoolean(null, false); hooked.getMethod("prepare").invoke(hooked.getConstructor().newInstance());
            check(stages(events()).equals(List.of("PREPARE_ENTER", "PREPARE_RETURN")) && hooked.getField("invalid").getInt(null) == 1, "validation-only return never invents assignment/settings");
        }
        for (boolean settings : List.of(true, false)) {
            try (var ignored = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) event -> { })) {
                hooked.getField("valid").setBoolean(null, true); hooked.getField("failSettings").setBoolean(null, settings);
                hooked.getField("failAfterAssignment").setBoolean(null, !settings); Throwable failure = null;
                try { hooked.getMethod("prepare").invoke(hooked.getConstructor().newInstance()); }
                catch (java.lang.reflect.InvocationTargetException expected) { failure = expected.getCause(); }
                check(failure == hooked.getField("failure").get(null), "same original Throwable identity");
                var stages = stages(events());
                check(stages.get(stages.size() - 1).equals("PREPARE_FAILED") && !stages.contains("PREPARE_RETURN") && !stages.contains("PREPARE_PANEL_RETURN"), "no fabricated normal/panel return on failure");
                check(stages.contains("PREPARE_PANEL_ASSIGNED") != settings, "assignment observed only if original field write ran");
            }
        }
        byte[] changed = new String(original, java.nio.charset.StandardCharsets.ISO_8859_1).replace("settings", "modified").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean refused = false; try { CreationEntryTransformer.transformFixture(changed, true, false); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "missing settings call refused before define");
        System.out.println("PASS: " + checks + " entry binding bytecode checks; no original UI/creation execution.");
    }
}
