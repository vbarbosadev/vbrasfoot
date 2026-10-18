package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.util.*;

public final class CreationProgressTransformerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] input) { return defineClass("br.vbrasfoot.CreationProgressFixture", input, 0, input.length); }
    }
    private static String shape(Class<?> type) {
        List<String> items = new ArrayList<>();
        for (var field : type.getDeclaredFields()) items.add(field.getName() + field.getType().getName() + field.getModifiers());
        for (var method : type.getDeclaredMethods()) items.add(method.getName() + Arrays.toString(method.getParameterTypes()) + method.getReturnType() + method.getModifiers());
        Collections.sort(items); return items.toString();
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = CreationProgressTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/CreationProgressFixture.class")) { original = input.readAllBytes(); }
        boolean refused = false;
        try { CreationProgressTransformer.transform(original); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "production fingerprint rejects synthetic class");
        Class<?> plain = new Loader().define(original), hooked = new Loader().define(CreationProgressTransformer.transformFixture(original, false));
        check(shape(plain).equals(shape(hooked)), "all declared fields and method signatures preserved");
        Object value = hooked.getConstructor().newInstance();
        var attach = HookProbe.class.getDeclaredMethod("attach", java.util.function.Consumer.class); attach.setAccessible(true);
        var snapshot = HookProbe.class.getDeclaredMethod("creationProgress"); snapshot.setAccessible(true);
        try (var sink = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) update -> { })) {
            hooked.getMethod("V").invoke(value);
            check(((List<?>) snapshot.invoke(null)).isEmpty(), "no active candidate, no progression record");
            HookProbe.creationPathStarted("a.bV.AB", value, new Object());
            try { hooked.getMethod("V").invoke(value); }
            finally { HookProbe.creationPathReturned("a.bV.AB", value, new Object()); }
            var records = (List<?>) snapshot.invoke(null);
            check(records.size() == 16, "five method entry/returns and three panel request/returns");
            var panelCodes = new ArrayList<Integer>();
            for (Object item : records) {
                var stage = item.getClass().getDeclaredMethod("stage"); stage.setAccessible(true);
                var panel = item.getClass().getDeclaredMethod("panel"); panel.setAccessible(true);
                if (stage.invoke(item).equals("PANEL_REQUEST")) panelCodes.add((Integer) panel.invoke(item));
            }
            check(panelCodes.equals(List.of(2, 5, 6)), "original int argument preserved and captured before each panel call");
            check(hooked.getField("values").get(null).equals(List.of(2, 5, 6, 2, 5, 6)), "original panel code effects preserved");
            hooked.getField("fail").set(null, true);
            HookProbe.creationPathStarted("a.bV.AB", value, new Object());
            boolean propagated = false;
            try { hooked.getMethod("V").invoke(value); }
            catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
            finally { HookProbe.creationPathFailed("a.bV.AB", value, new Object()); }
            check(propagated, "original progression Throwable repropagated through nested handlers");
            records = (List<?>) snapshot.invoke(null);
            long failed = 0;
            for (Object item : records) {
                var stage = item.getClass().getDeclaredMethod("stage"); stage.setAccessible(true);
                if (stage.invoke(item).equals("FAILED")) failed++;
            }
            check(failed == 5, "each failing method boundary reported, no fabricated normal return");
            hooked.getField("fail").set(null, false); hooked.getField("failPanel").set(null, true);
            int before = records.size();
            HookProbe.creationPathStarted("a.bV.AB", value, new Object()); propagated = false;
            try { hooked.getMethod("V").invoke(value); }
            catch (java.lang.reflect.InvocationTargetException expected) { propagated = expected.getCause() instanceof IllegalStateException; }
            finally { HookProbe.creationPathFailed("a.bV.AB", value, new Object()); }
            check(propagated, "panel constructor failure remains original failure");
            var after = (List<?>) snapshot.invoke(null);
            long panelReturns = 0;
            for (Object item : after.subList(before, after.size())) {
                var stage = item.getClass().getDeclaredMethod("stage"); stage.setAccessible(true);
                if (stage.invoke(item).equals("PANEL_RETURN")) panelReturns++;
            }
            check(panelReturns == 0, "no panel-return marker after failed original call");
        }
        byte[] changed = new String(original, java.nio.charset.StandardCharsets.ISO_8859_1).replace("panel", "other").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        refused = false;
        try { CreationProgressTransformer.transformFixture(changed, false); } catch (java.io.IOException expected) { refused = true; }
        check(refused, "changed panel site refused before class definition");
        System.out.println("PASS: " + checks + " synchronous creation bytecode checks; no original progression execution.");
    }
}
