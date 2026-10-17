package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.util.*;
import java.util.function.Consumer;
import java.lang.reflect.Modifier;

public final class CreationPathTransformerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.CreationTransformFixture", bytes, 0, bytes.length); }
    }
    private static String shape(Class<?> type) {
        List<String> shape = new ArrayList<>();
        for (var f : type.getDeclaredFields()) shape.add(f.getName() + f.getType().getName() + f.getModifiers());
        for (var m : type.getDeclaredMethods()) shape.add(m.getName() + m.getReturnType().getName() + Arrays.toString(m.getParameterTypes()) + m.getModifiers());
        Collections.sort(shape); return shape.toString();
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = CreationPathTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/CreationTransformFixture.class")) { original = input.readAllBytes(); }
        for (String name : List.of("a.bV", "a.cR", "other")) {
            boolean refused = false;
            try { CreationPathTransformer.transform(name, original); } catch (java.io.IOException expected) { refused = true; }
            check(refused, "production fingerprint/allowlist refused fixture");
        }
        Map<String, Integer> methods = Map.of("AB", Modifier.PUBLIC, "AC", Modifier.PUBLIC | Modifier.STATIC, "pn", Modifier.PUBLIC);
        Class<?> plain = new Loader().define(original);
        Class<?> instrumented = new Loader().define(CreationPathTransformer.transformFixture(original, methods));
        check(shape(plain).equals(shape(instrumented)), "names/fields/methods/access preserved");
        Object object = instrumented.getConstructor().newInstance();
        var attach = HookProbe.class.getDeclaredMethod("attach", Consumer.class); attach.setAccessible(true);
        var events = new ArrayList<HookProbe.Observation>();
        try (var subscription = (AutoCloseable) attach.invoke(null, (Consumer<HookProbe.Observation>) events::add)) {
            instrumented.getMethod("AB").invoke(object);
            check(events.size() == 2 && events.get(0).operation().equals("creation-candidate-start") && events.get(1).operation().equals("creation-candidate-return"), "candidate boundaries ordered");
            check(events.stream().noneMatch(HookProbe.Observation::completed), "return never claims completed creation");
            check(events.get(1).returnedSuccess() && !events.get(1).aborted(), "normal return semantics");
            instrumented.getField("fail").setBoolean(null, true);
            boolean propagated = false;
            try { instrumented.getMethod("AC").invoke(null); }
            catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
            check(propagated && events.get(3).aborted() && events.get(3).operation().equals("creation-candidate-failed"), "original exception propagated; no return marker");
            instrumented.getField("fail").setBoolean(null, false);
            instrumented.getMethod("pn").invoke(object);
            check(events.size() == 6 && instrumented.getField("calls").getInt(null) == 3, "legacy calls preserved and next invocation works");
        }
        try (var subscription = (AutoCloseable) attach.invoke(null, (Consumer<HookProbe.Observation>) event -> { throw new AssertionError("owned-observer-failure"); })) {
            instrumented.getMethod("AB").invoke(object);
            check(instrumented.getField("calls").getInt(null) == 4, "observer errors do not interrupt legacy");
        }
        boolean missing = false;
        try { CreationPathTransformer.transformFixture(original, Map.of("missing", Modifier.PUBLIC)); } catch (java.io.IOException expected) { missing = true; }
        check(missing, "missing method refused before define");
        System.out.println("PASS: " + checks + " creation candidate bytecode checks; synthetic, not ready-career events.");
    }
}
