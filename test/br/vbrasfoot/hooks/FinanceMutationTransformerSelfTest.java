package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.lang.reflect.*;
import java.util.*;

public final class FinanceMutationTransformerSelfTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("mutation transformer check " + checks); checks++; }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.FinanceMutationFixture", bytes, 0, bytes.length); }
    }
    private static String shape(Class<?> type) {
        var values = new ArrayList<String>();
        for (var field : type.getDeclaredFields()) values.add(field.toString());
        for (var method : type.getDeclaredMethods()) values.add(method.toString());
        Collections.sort(values); return values.toString();
    }
    private static long count(String name) throws Exception {
        var snapshotMethod = HookProbe.class.getDeclaredMethod("financeMutationSnapshot", ClassLoader.class); snapshotMethod.setAccessible(true);
        Object snapshot = snapshotMethod.invoke(null, FinanceMutationTransformerSelfTest.class.getClassLoader());
        var accessor = snapshot.getClass().getDeclaredMethod(name); accessor.setAccessible(true);
        return ((Number) accessor.invoke(snapshot)).longValue();
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = FinanceMutationTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/FinanceMutationFixture.class")) { original = input.readAllBytes(); }
        boolean rejected = false;
        try { FinanceMutationTransformer.transform("best.ah", original); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected);
        Class<?> plain = new Loader().define(original), changed = new Loader().define(FinanceMutationTransformer.transformFixture(original));
        check(shape(plain).equals(shape(changed)));
        Object target = changed.getConstructor().newInstance(), ledger = new Object(); long started = count("started"), completed = count("completed");
        changed.getMethod("update", long.class, Object.class, int.class, long.class).invoke(target, Long.MAX_VALUE - 4, ledger, 7, 10L);
        check(changed.getField("nb").getLong(target) == Long.MAX_VALUE - 4);
        check(changed.getField("nx").get(target) == ledger);
        check(changed.getField("dO").getInt(target) == 7 && changed.getField("dK").getLong(target) == 10);
        check(count("started") == started + 4 && count("completed") == completed + 4 && count("active") == 0);
        changed.getMethod("unrelated", int.class).invoke(target, 9);
        check(changed.getField("untouched").getInt(target) == 9 && count("started") == started + 4);
        boolean originalNull = false;
        try { changed.getMethod("fail", changed).invoke(null, new Object[] {null}); }
        catch (InvocationTargetException failure) { originalNull = failure.getCause() instanceof NullPointerException; }
        check(originalNull && count("started") == started + 5 && count("completed") == completed + 4 && count("active") == 1);
        System.out.println("PASS: " + checks + " financial mutation transformer checks; owned fields/exception only; no original coverage claim.");
    }
}
