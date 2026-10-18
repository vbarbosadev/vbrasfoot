package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.util.*;

public final class CreationDialogTransformerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] input) { return defineClass("br.vbrasfoot.CreationDialogFixture", input, 0, input.length); }
    }
    private static String shape(Class<?> type) {
        List<String> items = new ArrayList<>();
        for (var f : type.getDeclaredFields()) items.add(f.getName() + f.getType() + f.getModifiers());
        for (var m : type.getDeclaredMethods()) items.add(m.getName() + m.getReturnType() + Arrays.toString(m.getParameterTypes()) + m.getModifiers());
        Collections.sort(items); return items.toString();
    }
    private static Object get(Object object, String field) throws Exception {
        var method = object.getClass().getDeclaredMethod(field); method.setAccessible(true); return method.invoke(object);
    }
    private static List<?> events() throws Exception {
        var method = HookProbe.class.getDeclaredMethod("creationDialogs"); method.setAccessible(true);
        return (List<?>) get(method.invoke(null), "events");
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = CreationDialogTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/CreationDialogFixture.class")) { original = input.readAllBytes(); }
        boolean refused = false;
        try { CreationDialogTransformer.transform(original); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "production refuses synthetic fingerprint");
        var hookedBytes = CreationDialogTransformer.transformFixture(CreationPathTransformer.transformFixture(original, Map.of("AB", 1)), false);
        Class<?> plain = new Loader().define(original), hooked = new Loader().define(hookedBytes);
        check(shape(plain).equals(shape(hooked)), "all declared fields/methods preserved");
        var attach = HookProbe.class.getDeclaredMethod("attach", java.util.function.Consumer.class); attach.setAccessible(true);
        for (int result : new int[] {0, 1, 2, -1, Integer.MIN_VALUE}) {
            try (var ignored = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) event -> { })) {
                hooked.getField("choice").setInt(null, result);
                hooked.getMethod("oq").invoke(hooked.getConstructor().newInstance());
                check(hooked.getField("seen").getInt(null) == result, "original int preserved " + result);
                var events = events();
                check(events.size() == (result == 0 ? 5 : 6), "nested boundary/result/candidate counts " + result);
                var dialog = events.get(2);
                check(get(dialog, "stage").equals("DIALOG_RETURN") && get(dialog, "resultKnown").equals(true)
                        && get(dialog, "result").equals(result), "raw return captured " + result);
                check(get(events.get(1), "parent").equals(get(events.get(0), "frame")), "pl linked to oq frame");
                check(events.stream().filter(e -> { try { return get(e, "method").equals("AB"); } catch (Exception failure) { throw new AssertionError(failure); } }).count() == (result == 0 ? 0 : 1), "AB only nonzero original branch");
            }
        }
        check(hooked.getField("accepted").getInt(null) == 1 && hooked.getField("proceeded").getInt(null) == 4, "branch effects preserved");
        for (boolean failDialog : List.of(true, false)) {
            try (var ignored = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) event -> { })) {
                hooked.getField("failDialog").setBoolean(null, failDialog);
                hooked.getField("failCandidate").setBoolean(null, !failDialog);
                hooked.getField("choice").setInt(null, 1); Throwable failure = null;
                try { hooked.getMethod("oq").invoke(hooked.getConstructor().newInstance()); }
                catch (java.lang.reflect.InvocationTargetException expected) { failure = expected.getCause(); }
                check(failDialog ? failure instanceof IllegalArgumentException : failure instanceof IllegalStateException, "original exception type preserved");
                var events = events();
                check(get(events.get(events.size() - 1), "stage").equals("FAILED")
                        && get(events.get(events.size() - 2), "stage").equals("FAILED"), "failed pl and oq, no fabricated normal return");
                check(events.stream().noneMatch(e -> { try { return get(e, "stage").equals("RETURN"); } catch (Exception problem) { throw new AssertionError(problem); } }), "no return on failure");
                check(events.stream().filter(e -> { try { return get(e, "resultKnown").equals(true); } catch (Exception problem) { throw new AssertionError(problem); } }).count() == (failDialog ? 0 : 1), "dialog throw never fabricates result");
            }
        }
        byte[] changed = new String(original, java.nio.charset.StandardCharsets.ISO_8859_1).replace("confirm", "altered").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        refused = false; try { CreationDialogTransformer.transformFixture(changed, false); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "missing exact dialog site refused");
        System.out.println("PASS: " + checks + " dialog bytecode checks; no real dialog or career execution.");
    }
}
