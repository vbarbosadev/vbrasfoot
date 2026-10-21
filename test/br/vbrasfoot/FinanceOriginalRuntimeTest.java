package br.vbrasfoot;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.swing.JFrame;
import static br.vbrasfoot.CareerCommandDispatcher.*;

/** Original credit/debit+ledger and original save/reload on an explicitly marked fresh private fixture only. */
public final class FinanceOriginalRuntimeTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static final class NumericFixture { public int number = Integer.MIN_VALUE + 17; public long wide = Long.MIN_VALUE + 19; public NumericLedger nx = new NumericLedger(); }
    public static final class NumericLedger { public long sum = 19; }
    private record ReferenceBytes(byte[] bytes, String failure) { }
    private static ReferenceBytes referenceBytes(LegacyRuntime runtime, String mode, boolean nullLedger, boolean overflow) throws Exception {
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Class<?> fieldsClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Class<?> cachedClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", true, runtime);
        Class<?> outputClass = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime);
        Object kryo = kryoClass.getConstructor().newInstance();
        Object serializer = kryoClass.getMethod("getSerializer", Class.class).invoke(kryo, NumericFixture.class);
        Object field = fieldsClass.getMethod("getField", String.class).invoke(serializer, "nx");
        cachedClass.getMethod("setClass", Class.class).invoke(field, mode.equals("dynamic") ? null : NumericLedger.class);
        cachedClass.getMethod("setCanBeNull", boolean.class).invoke(field, !mode.equals("required"));
        NumericFixture fixture = new NumericFixture(); if (nullLedger) fixture.nx = null;
        var bytes = new java.io.ByteArrayOutputStream();
        Object output = overflow ? outputClass.getConstructor(int.class, int.class).newInstance(1, 1)
                : outputClass.getConstructor(java.io.OutputStream.class).newInstance(bytes);
        String failure = "";
        // Exercise nx itself: a leading primitive field must not consume the overflow first.
        try { cachedClass.getMethod("write", outputClass, Object.class).invoke(field, output, fixture); }
        catch (java.lang.reflect.InvocationTargetException expected) { failure = expected.getCause().getClass().getName(); }
        if (!overflow) outputClass.getMethod("close").invoke(output);
        return new ReferenceBytes(bytes.toByteArray(), failure);
    }
    private static void referenceRoutes(LegacyRuntime probe, LegacyRuntime plain) throws Exception {
        String name = "com.esotericsoftware.kryo.serializers.ObjectField";
        byte[] changed; try (var input = probe.getResourceAsStream(name.replace('.', '/') + ".class")) { changed = input.readAllBytes(); }
        changed[changed.length - 1] ^= 1; boolean refused = false;
        try { Class.forName("br.vbrasfoot.hooks.FinanceReferenceTransformer").getMethod("transform", byte[].class).invoke(null, (Object) changed); }
        catch (java.lang.reflect.InvocationTargetException failure) { refused = failure.getCause() instanceof java.io.IOException; }
        check(refused, "changed ObjectField fingerprint refused");
        for (String mode : List.of("dynamic", "nullable", "required")) {
            for (boolean nullLedger : List.of(false, true)) {
                var actual = referenceBytes(probe, mode, nullLedger, false); var expected = referenceBytes(plain, mode, nullLedger, false);
                check(actual.failure().equals(expected.failure()) && Arrays.equals(actual.bytes(), expected.bytes()), "reference " + mode + " null=" + nullLedger + " preserves original bytes/failure");
                check(actual.failure().isEmpty() == !(mode.equals("required") && nullLedger), "original required/null semantics retained");
            }
            var actual = referenceBytes(probe, mode, false, true); var expected = referenceBytes(plain, mode, false, true);
            check(actual.failure().equals("com.esotericsoftware.kryo.KryoException") && actual.failure().equals(expected.failure()), "reference output overflow remains original failure: " + mode);
        }
    }
    private static void numericRoutes(LegacyRuntime runtime) throws Exception {
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Object kryo = kryoClass.getConstructor().newInstance();
        Class<?> serializerClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Object serializer = serializerClass.getConstructor(kryoClass, Class.class).newInstance(kryo, NumericFixture.class);
        Class<?> base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", true, runtime);
        var fieldSlot = base.getDeclaredField("field"); fieldSlot.setAccessible(true);
        var varints = base.getDeclaredField("varIntsEnabled"); varints.setAccessible(true);
        Class<?> outputClass = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime);
        Class<?> accessClass = Class.forName("com.esotericsoftware.reflectasm.FieldAccess", true, runtime);
        Object access = accessClass.getMethod("get", Class.class).invoke(null, NumericFixture.class);
        NumericFixture fixture = new NumericFixture();
        for (String family : List.of("UnsafeCacheFields$Unsafe", "AsmCacheFields$Asm", "ObjectField$Object")) {
            for (boolean wide : List.of(false, true)) {
                var field = NumericFixture.class.getField(wide ? "wide" : "number");
                Class<?> route = Class.forName("com.esotericsoftware.kryo.serializers." + family + (wide ? "LongField" : "IntField"), true, runtime);
                byte[] original;
                try (var input = runtime.getResourceAsStream(route.getName().replace('.', '/') + ".class")) { original = input.readAllBytes(); }
                original[original.length - 1] ^= 1;
                boolean fingerprintRefused = false;
                try { Class.forName("br.vbrasfoot.hooks.FinanceFieldTransformer").getMethod("transform", String.class, byte[].class).invoke(null, route.getName(), original); }
                catch (java.lang.reflect.InvocationTargetException failure) { fingerprintRefused = failure.getCause() instanceof java.io.IOException; }
                check(fingerprintRefused, "changed fingerprint refused for " + route.getSimpleName());
                var ctor = family.startsWith("Unsafe") ? route.getDeclaredConstructor(java.lang.reflect.Field.class)
                        : family.startsWith("Asm") ? route.getDeclaredConstructor() : route.getDeclaredConstructor(serializerClass);
                ctor.setAccessible(true);
                Object cached = family.startsWith("Unsafe") ? ctor.newInstance(field) : family.startsWith("Asm") ? ctor.newInstance() : ctor.newInstance(serializer);
                fieldSlot.set(cached, field);
                if (family.startsWith("Asm")) {
                    var accessSlot = base.getDeclaredField("access"); accessSlot.setAccessible(true); accessSlot.set(cached, access);
                    var indexSlot = base.getDeclaredField("accessIndex"); indexSlot.setAccessible(true);
                    indexSlot.setInt(cached, (int) accessClass.getMethod("getIndex", String.class).invoke(access, field.getName()));
                }
                var write = route.getMethod("write", outputClass, Object.class); write.setAccessible(true);
                for (boolean variable : List.of(false, true)) {
                    varints.setBoolean(cached, variable);
                    var actual = new java.io.ByteArrayOutputStream(); var expected = new java.io.ByteArrayOutputStream();
                    Object out = outputClass.getConstructor(java.io.OutputStream.class).newInstance(actual);
                    Object reference = outputClass.getConstructor(java.io.OutputStream.class).newInstance(expected);
                    write.invoke(cached, out, fixture);
                    String method = wide ? "writeLong" : "writeInt";
                    Class<?> type = wide ? long.class : int.class;
                    Object value = field.get(fixture);
                    if (variable) outputClass.getMethod(method, type, boolean.class).invoke(reference, value, false);
                    else outputClass.getMethod(method, type).invoke(reference, value);
                    outputClass.getMethod("close").invoke(out); outputClass.getMethod("close").invoke(reference);
                    check(Arrays.equals(actual.toByteArray(), expected.toByteArray()), "transformed " + route.getSimpleName() + " preserves exact " + (variable ? "varint" : "fixed") + " bytes");
                    Object full = outputClass.getConstructor(int.class, int.class).newInstance(1, 1);
                    boolean refused = false;
                    try { write.invoke(cached, full, fixture); } catch (java.lang.reflect.InvocationTargetException failure) {
                        refused = failure.getCause().getClass().getName().equals("com.esotericsoftware.kryo.KryoException");
                    }
                    check(refused, "transformed " + route.getSimpleName() + " preserves output overflow failure");
                    Class<?> inputClass = Class.forName("com.esotericsoftware.kryo.io.Input", true, runtime);
                    var read = route.getMethod("read", inputClass, Object.class); read.setAccessible(true);
                    var copy = route.getMethod("copy", Object.class, Object.class); copy.setAccessible(true);
                    var beforeIndirect = HookProbe.financeMutationSnapshot(runtime);
                    NumericFixture decoded = new NumericFixture(), copied = new NumericFixture();
                    field.set(decoded, wide ? (Object) Long.valueOf(0) : Integer.valueOf(0));
                    field.set(copied, wide ? (Object) Long.valueOf(0) : Integer.valueOf(0));
                    Object input = inputClass.getConstructor(byte[].class).newInstance((Object) actual.toByteArray());
                    read.invoke(cached, input, decoded);
                    check(field.get(decoded).equals(value), "original " + route.getSimpleName() + " read preserves exact fixed/varint value");
                    copy.invoke(cached, fixture, copied);
                    check(field.get(copied).equals(value), "original " + route.getSimpleName() + " copy preserves exact value");
                    boolean inputRefused = false;
                    try { read.invoke(cached, inputClass.getConstructor(byte[].class).newInstance((Object) new byte[0]), decoded); }
                    catch (java.lang.reflect.InvocationTargetException failure) { inputRefused = failure.getCause().getClass().getName().equals("com.esotericsoftware.kryo.KryoException"); }
                    check(inputRefused && HookProbe.financeMutationSnapshot(runtime).kryoStarted() == beforeIndirect.kryoStarted(),
                            "unrelated own field/truncated input does not invent selected financial writes: " + route.getSimpleName());
                }
            }
        }
    }
    private static void relevantKryoMutations(LegacyRuntime runtime, LegacyFinanceAdapter adapter, Object club) throws Exception {
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Class<?> fieldsClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Class<?> base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", true, runtime);
        Class<?> outputClass = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime), inputClass = Class.forName("com.esotericsoftware.kryo.io.Input", true, runtime);
        var fieldSlot = base.getDeclaredField("field"); fieldSlot.setAccessible(true);
        var varints = base.getDeclaredField("varIntsEnabled"); varints.setAccessible(true);
        Object ledger = CareerBridge.onEdt(() -> CareerBridge.call(club, "kL")); var expected = CareerBridge.onEdt(() -> adapter.snapshot(club));
        for (String fieldName : List.of("nb", "dK", "dL")) {
            Object receiver = fieldName.equals("nb") ? club : ledger;
            var field = receiver.getClass().getDeclaredField(fieldName); field.setAccessible(true); boolean wide = field.getType() == long.class;
            Object value = CareerBridge.onEdt(() -> field.get(receiver));
            Object kryo = kryoClass.getConstructor().newInstance(), serializer = fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryo, receiver.getClass());
            for (String family : List.of("UnsafeCacheFields$Unsafe", "ObjectField$Object")) {
                Class<?> route = Class.forName("com.esotericsoftware.kryo.serializers." + family + (wide ? "LongField" : "IntField"), true, runtime);
                var ctor = family.startsWith("Unsafe") ? route.getDeclaredConstructor(java.lang.reflect.Field.class) : route.getDeclaredConstructor(fieldsClass); ctor.setAccessible(true);
                Object cached = family.startsWith("Unsafe") ? ctor.newInstance(field) : ctor.newInstance(serializer);
                fieldSlot.set(cached, field); varints.setBoolean(cached, true);
                var bytes = new java.io.ByteArrayOutputStream(); Object output = outputClass.getConstructor(java.io.OutputStream.class).newInstance(bytes);
                outputClass.getMethod(wide ? "writeLong" : "writeInt", wide ? long.class : int.class, boolean.class).invoke(output, value, false);
                outputClass.getMethod("close").invoke(output);
                Object input = inputClass.getConstructor(byte[].class).newInstance((Object) bytes.toByteArray());
                var read = route.getMethod("read", inputClass, Object.class); read.setAccessible(true);
                var copy = route.getMethod("copy", Object.class, Object.class); copy.setAccessible(true);
                var beforeRead = HookProbe.financeMutationSnapshot(runtime);
                CareerBridge.onEdt(() -> { read.invoke(cached, input, receiver); return null; });
                var afterRead = HookProbe.financeMutationSnapshot(runtime);
                check(afterRead.healthy() && afterRead.kryoStarted() == beforeRead.kryoStarted() + 1 && afterRead.kryoCompleted() == beforeRead.kryoCompleted() + 1
                        && !afterRead.stableSince(beforeRead), "actual same-value " + route.getSimpleName() + " read invalidates financial checkpoint for " + fieldName);
                var beforeCopy = HookProbe.financeMutationSnapshot(runtime);
                CareerBridge.onEdt(() -> { copy.invoke(cached, receiver, receiver); return null; });
                var afterCopy = HookProbe.financeMutationSnapshot(runtime);
                check(afterCopy.healthy() && afterCopy.kryoStarted() == beforeCopy.kryoStarted() + 1 && afterCopy.kryoCompleted() == beforeCopy.kryoCompleted() + 1
                        && !afterCopy.stableSince(beforeCopy), "actual same-value " + route.getSimpleName() + " copy invalidates financial checkpoint for " + fieldName);
                check(CareerBridge.onEdt(() -> adapter.snapshot(club)).equals(expected) && CareerBridge.onEdt(() -> CareerBridge.call(club, "kL")) == ledger,
                        "selected decoder/copy test leaves complete original balance/ledger and reference unchanged");
                var beforeTruncated = HookProbe.financeMutationSnapshot(runtime);
                boolean truncated = CareerBridge.onEdt(() -> {
                    try { read.invoke(cached, inputClass.getConstructor(byte[].class).newInstance((Object) new byte[0]), receiver); }
                    catch (java.lang.reflect.InvocationTargetException failure) { return failure.getCause().getClass().getName().equals("com.esotericsoftware.kryo.KryoException"); }
                    return false;
                });
                check(truncated && HookProbe.financeMutationSnapshot(runtime).kryoStarted() == beforeTruncated.kryoStarted()
                        && CareerBridge.onEdt(() -> adapter.snapshot(club)).equals(expected), "truncated input fails before original selected setter, without invented mutation");
            }
        }
        Object kryo = kryoClass.getConstructor().newInstance(), serializer = fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryo, club.getClass());
        Object originalCached = fieldsClass.getMethod("getField", String.class).invoke(serializer, "nx");
        Class<?> objectClass = Class.forName("com.esotericsoftware.kryo.serializers.ObjectField", true, runtime);
        var ctor = objectClass.getDeclaredConstructor(fieldsClass); ctor.setAccessible(true); Object reflective = ctor.newInstance(serializer);
        var nx = club.getClass().getDeclaredField("nx"); nx.setAccessible(true); fieldSlot.set(reflective, nx);
        for (Object cached : List.of(originalCached, reflective)) {
            var setter = cached.getClass().getMethod("setField", Object.class, Object.class); setter.setAccessible(true);
            var before = HookProbe.financeMutationSnapshot(runtime);
            CareerBridge.onEdt(() -> { setter.invoke(cached, club, ledger); return null; });
            var after = HookProbe.financeMutationSnapshot(runtime);
            check(after.healthy() && after.kryoStarted() == before.kryoStarted() + 1 && after.kryoCompleted() == before.kryoCompleted() + 1 && !after.stableSince(before),
                    "actual same-reference setter invalidates checkpoint: " + cached.getClass().getSimpleName());
        }
        check(CareerBridge.onEdt(() -> adapter.snapshot(club)).equals(expected) && CareerBridge.onEdt(() -> CareerBridge.call(club, "kL")) == ledger,
                "all actual non-direct mutation probes preserve original financial values and ledger identity");
    }
    private static void externalReflectionBoundary(LegacyRuntime runtime, LegacyFinanceAdapter adapter, Object club) throws Exception {
        var field = club.getClass().getDeclaredField("nb"); field.setAccessible(true);
        var value = CareerBridge.onEdt(() -> adapter.snapshot(club)); var before = HookProbe.financeMutationSnapshot(runtime);
        CareerBridge.onEdt(() -> { field.setLong(club, value.balance()); return null; });
        check(HookProbe.financeMutationSnapshot(runtime).stableSince(before) && CareerBridge.onEdt(() -> adapter.snapshot(club)).equals(value),
                "explicit residual gap: arbitrary Field.setLong outside fingerprinted Kryo sites is NOT observed even for equal-value balance write; no universal coverage");
    }
    private static void interruptedSelectedSetter(LegacyRuntime runtime, LegacyFinanceAdapter adapter, Object club) throws Exception {
        Object ledger = CareerBridge.onEdt(() -> CareerBridge.call(club, "kL")); var expected = CareerBridge.onEdt(() -> adapter.snapshot(club));
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime), fieldsClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Class<?> base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", true, runtime);
        Class<?> route = Class.forName("com.esotericsoftware.kryo.serializers.ObjectField$ObjectIntField", true, runtime);
        Class<?> inputClass = Class.forName("com.esotericsoftware.kryo.io.Input", true, runtime);
        var ctor = route.getDeclaredConstructor(fieldsClass); ctor.setAccessible(true);
        Object cached = ctor.newInstance(fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryoClass.getConstructor().newInstance(), ledger.getClass()));
        var field = ledger.getClass().getDeclaredField("dL"); // Deliberately inaccessible; do not change the original class or value.
        var fieldSlot = base.getDeclaredField("field"); fieldSlot.setAccessible(true); fieldSlot.set(cached, field);
        var varints = base.getDeclaredField("varIntsEnabled"); varints.setAccessible(true); varints.setBoolean(cached, false);
        var read = route.getMethod("read", inputClass, Object.class); read.setAccessible(true);
        var before = HookProbe.financeMutationSnapshot(runtime);
        boolean rejected = CareerBridge.onEdt(() -> {
            try { read.invoke(cached, inputClass.getConstructor(byte[].class).newInstance((Object) new byte[]{0, 0, 0, 0}), ledger); }
            catch (java.lang.reflect.InvocationTargetException failure) { return failure.getCause().getClass().getName().equals("com.esotericsoftware.kryo.KryoException"); }
            return false;
        });
        var after = HookProbe.financeMutationSnapshot(runtime);
        check(rejected && after.kryoStarted() == before.kryoStarted() + 1 && after.kryoCompleted() == before.kryoCompleted()
                && after.active() > 0 && !after.stableSince(after), "selected setter failure preserves original exception and undrained observation; never manufacture clean coverage");
        check(CareerBridge.onEdt(() -> adapter.snapshot(club)).equals(expected), "controlled inaccessible-setter failure does not change original financial values");
    }
    private static void mutationRouteFingerprints(LegacyRuntime runtime) throws Exception {
        for (String name : FinanceMutationTracker.KRYO_CLASSES) {
            Class.forName(name, true, runtime); byte[] changed;
            try (var input = runtime.getResourceAsStream(name.replace('.', '/') + ".class")) { changed = input.readAllBytes(); }
            changed[changed.length - 1] ^= 1; boolean refused = false;
            try { Class.forName("br.vbrasfoot.hooks.KryoFinanceMutationTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, changed); }
            catch (java.lang.reflect.InvocationTargetException failure) { refused = failure.getCause() instanceof java.io.IOException; }
            check(refused, "changed non-direct mutation fingerprint refused: " + name);
        }
        check(HookProbe.financeMutationSnapshot(runtime).kryoClassesObserved().size() == 9, "all nine selected original decoder route classes defined with verified hooks, not universal Java coverage");
    }
    private static void ledgerProfiles(LegacyRuntime runtime, Object club) throws Exception {
        Object ledger = CareerBridge.onEdt(() -> CareerBridge.call(club, "kL"));
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Class<?> fieldsClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Object kryo = kryoClass.getConstructor().newInstance();
        Object serializer = fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryo, ledger.getClass());
        check(LedgerSerializerProfile.inspect(serializer, ledger, runtime) == LedgerSerializerProfile.Status.WRONG_THREAD, "profile never queries original metadata off EDT");
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT,
                "actual original ledger serializer has exact fourteen supported mapped fields with regions/transients disabled");
        var regions = fieldsClass.getDeclaredField("useMemRegions"); regions.setAccessible(true);
        regions.setBoolean(serializer, true);
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.MEMORY_REGIONS,
                "even exact FieldSerializer and numeric field samples do not qualify memory-region mode");
        regions.setBoolean(serializer, false);
        fieldsClass.getMethod("setSerializeTransient", boolean.class).invoke(serializer, true);
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.TRANSIENT_SERIALIZATION,
                "transient serialization is explicitly unsupported");
        fieldsClass.getMethod("setSerializeTransient", boolean.class).invoke(serializer, false);
        Object wrongType = fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryo, NumericFixture.class);
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(wrongType, ledger, runtime)) == LedgerSerializerProfile.Status.WRONG_TYPE,
                "serializer instance for another type cannot qualify by class alone");
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, new ClassLoader() { })) == LedgerSerializerProfile.Status.WRONG_TYPE,
                "another runtime cannot borrow original serializer metadata");
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(null, ledger, runtime)) == LedgerSerializerProfile.Status.WRONG_TYPE,
                "null serializer never qualifies");
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, null, runtime)) == LedgerSerializerProfile.Status.WRONG_TYPE,
                "null ledger never qualifies");
        Object fields = fieldsClass.getMethod("getFields").invoke(serializer); Object first = java.lang.reflect.Array.get(fields, 0);
        java.lang.reflect.Array.set(fields, 0, null);
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.UNSUPPORTED_FIELD_ROUTE,
                "missing numeric cached-field route is rejected");
        java.lang.reflect.Array.set(fields, 0, java.lang.reflect.Array.get(fields, 1));
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.FIELD_SET_MISMATCH,
                "duplicate cached fields cannot replace complete unique field inventory");
        java.lang.reflect.Array.set(fields, 0, first);
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT,
                "only disposable serializer restored in fixture, no game serializer configuration altered");
        fieldsClass.getMethod("removeField", String.class).invoke(serializer, "dK");
        check(CareerBridge.onEdt(() -> LedgerSerializerProfile.inspect(serializer, ledger, runtime)) == LedgerSerializerProfile.Status.FIELD_SET_MISMATCH,
                "removed field rejects even when class/other fields/values still match");
    }
    private static void objectMutationRoutes(LegacyRuntime runtime) throws Exception {
        Class<?> kryoClass = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Class<?> fieldsClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer", true, runtime);
        Class<?> base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", true, runtime);
        Class<?> accessClass = Class.forName("com.esotericsoftware.reflectasm.FieldAccess", true, runtime);
        Class<?> outputClass = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime), inputClass = Class.forName("com.esotericsoftware.kryo.io.Input", true, runtime);
        var fieldSlot = base.getDeclaredField("field"); fieldSlot.setAccessible(true);
        var accessSlot = base.getDeclaredField("access"); accessSlot.setAccessible(true);
        var indexSlot = base.getDeclaredField("accessIndex"); indexSlot.setAccessible(true);
        var offsetSlot = base.getDeclaredField("offset"); offsetSlot.setAccessible(true);
        var serializerAccess = fieldsClass.getDeclaredField("access"); serializerAccess.setAccessible(true);
        Object access = accessClass.getMethod("get", Class.class).invoke(null, NumericFixture.class);
        int index = (int) accessClass.getMethod("getIndex", String.class).invoke(access, "nx");
        for (String simple : List.of("ObjectField", "ObjectField-fast", "UnsafeCacheFields$UnsafeObjectField", "AsmCacheFields$AsmObjectField")) {
            Object kryo = kryoClass.getConstructor().newInstance();
            Object serializer = fieldsClass.getConstructor(kryoClass, Class.class).newInstance(kryo, NumericFixture.class);
            Object original = fieldsClass.getMethod("getField", String.class).invoke(serializer, "nx");
            Class<?> route = Class.forName("com.esotericsoftware.kryo.serializers." + simple.replace("-fast", ""), true, runtime);
            var ctor = route.getDeclaredConstructor(fieldsClass); ctor.setAccessible(true); Object cached = ctor.newInstance(serializer);
            fieldSlot.set(cached, NumericFixture.class.getField("nx")); accessSlot.set(cached, access);
            indexSlot.setInt(cached, simple.equals("ObjectField") ? -1 : index); offsetSlot.setLong(cached, offsetSlot.getLong(original)); serializerAccess.set(serializer, access);
            base.getMethod("setClass", Class.class).invoke(cached, NumericLedger.class);
            var setter = route.getMethod("setField", Object.class, Object.class); setter.setAccessible(true);
            var copy = route.getMethod("copy", Object.class, Object.class); copy.setAccessible(true);
            var write = route.getMethod("write", outputClass, Object.class); write.setAccessible(true);
            var read = route.getMethod("read", inputClass, Object.class); read.setAccessible(true);
            var source = new NumericFixture(); var destination = new NumericFixture(); var before = HookProbe.financeMutationSnapshot(runtime);
            setter.invoke(cached, destination, source.nx);
            check(destination.nx == source.nx, "original object setter preserves exact argument: " + simple);
            copy.invoke(cached, source, destination);
            check(destination.nx != source.nx && destination.nx.sum == source.nx.sum, "original object copy preserves cloned value: " + simple);
            var bytes = new java.io.ByteArrayOutputStream(); Object output = outputClass.getConstructor(java.io.OutputStream.class).newInstance(bytes);
            write.invoke(cached, output, source); outputClass.getMethod("close").invoke(output);
            read.invoke(cached, inputClass.getConstructor(byte[].class).newInstance((Object) bytes.toByteArray()), destination);
            check(destination.nx != null && destination.nx.sum == source.nx.sum, "original object read dispatches matching setter: " + simple);
            check(HookProbe.financeMutationSnapshot(runtime).kryoStarted() == before.kryoStarted(), "unrelated own object slots do not count as selected financial writes: " + simple);
        }
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".finance-test-fixture")) || !Path.of("").toAbsolutePath().toRealPath().equals(game)
                || Files.exists(game.resolve("sav/finance-output.s22")) || Files.exists(home.resolve("finance-journal"))
                || Files.exists(home.resolve("finance-requests")) || Files.exists(home.resolve("finance-execution-witnesses")))
            throw new IllegalArgumentException("Fresh private fixture/cwd required; no user saves may be targeted");
        String inputHash = Profile.digest(game.resolve("sav/input.s22"));
        Thread current = Thread.currentThread(); ClassLoader previous = current.getContextClassLoader();
        boolean probeWriter = args.length == 3 && args[2].equals("--probe-writer");
        var writerLifecycle = new CareerLifecycle();
        try (LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("runtime"), probeWriter);
             AutoCloseable writerGuard = probeWriter ? HookProbe.attachLifecycle(writerLifecycle) : () -> { };
             AutoCloseable serializationGuard = probeWriter ? HookProbe.attachFinancialSerialization(new FinanceSerializationTracker()) : () -> { }) {
            current.setContextClassLoader(runtime);
            if (probeWriter) numericRoutes(runtime);
            if (probeWriter) mutationRouteFingerprints(runtime);
            if (probeWriter) objectMutationRoutes(runtime);
            if (probeWriter) try (var plain = LegacyRuntime.prepare(Profile.verify(game), home.resolve("reference-plain"), false)) { referenceRoutes(runtime, plain); }
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            Class<?> state = Class.forName("c.a", false, runtime);
            JFrame frame = CareerBridge.onEdt(() -> {
                JFrame value = new JFrame("Owned finance fixture — not game UI"); value.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, value); return value;
            });
            try {
                var load = state.getMethod("c", String.class, boolean.class); var saveMethod = state.getMethod("ae", String.class);
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false)), "private original save decoded by original loader");
                var adapter = new LegacyFinanceAdapter(runtime);
                var roots = CareerBridge.onEdt(adapter::roots);
                Object human = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(roots.primary(), "aN")).get(0));
                if (probeWriter) {
                    var decodedMutations = HookProbe.financeMutationSnapshot(runtime);
                    check(decodedMutations.healthy() && decodedMutations.kryoStarted() > 0 && decodedMutations.kryoStarted() == decodedMutations.kryoCompleted(),
                            "original initial decoder observed selected non-direct financial setters, no outstanding mutation");
                    relevantKryoMutations(runtime, adapter, human);
                    externalReflectionBoundary(runtime, adapter, human);
                    ledgerProfiles(runtime, human);
                }
                var before = CareerBridge.onEdt(() -> adapter.snapshot(human));
                int clubCount = new CareerBridge(runtime).snapshot().clubs().size();
                var observer = new SaveFileObserver(); var sourceObservation = observer.observe(game.resolve("sav"), "input.s22");
                var associations = new CareerSaveAssociations(home.resolve("association-metadata"));
                var associated = associations.create(sourceObservation, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
                check(associated.save().sha256().equals(inputHash) && associations.inspect(associated.handle()).token().equals(associated.token()), "original bytes observed and explicitly associated only inside private fixture");
                var revision = sourceObservation.revision();
                UUID career = associated.handle().career(), binding = associated.handle().binding(); var key = new CareerSidecarStore.Key(career, binding, "br.owned.finance-original");
                var journal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-journal")), key);
                try (var gate = new CareerCommandGate()) {
                    var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT, CareerCommandGate.Operation.FINANCE_DEBIT));
                    // Explicit fixture authority only; this is NOT production discovery/qualification of a career.
                    var context = gate.installContext(career, binding, 1, revision, roots.primary(), roots.secondary());
                    var dispatcher = new CareerCommandDispatcher(gate, journal, adapter::roots, Duration.ofSeconds(5));
                    var requestStore = new FinanceRequestStore(new CareerSidecarStore(home.resolve("finance-requests")), key);
                    var witnessStore = new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("finance-execution-witnesses")), key);
                    var creditRequest = CareerBridge.onEdt(() -> adapter.request(context, key.mod(), "owned-credit", human, FinanceDelta.Direction.CREDIT_RAW_6, 123));
                    boolean duplicateRefused = CareerBridge.onEdt(() -> {
                        @SuppressWarnings("unchecked") var clubs = (List<Object>) CareerBridge.call(roots.primary(), "P");
                        int size = clubs.size(); clubs.add(human); // Owned graph only, removed before any financial effect/save.
                        try { adapter.prepare(creditRequest); return false; }
                        catch (IllegalStateException expected) { return true; }
                        finally { clubs.remove(size); }
                    });
                    check(duplicateRefused && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(before), "repeated compound locator refuses plan in owned graph without financial effect");
                    Path requestFile = Files.write(home.resolve("owned-credit.request"), creditRequest.encode());
                    var decodedRequest = FinanceRequest.decode(Files.readAllBytes(requestFile));
                    var credit = CareerBridge.onEdt(() -> adapter.prepare(decodedRequest));
                    var recoveryKey = new CareerSidecarStore.Key(career, binding, "br.owned.no-effect-recovery");
                    var recoveryRequest = CareerBridge.onEdt(() -> adapter.request(context, recoveryKey.mod(), "rejected-before-effect", human, FinanceDelta.Direction.CREDIT_RAW_6, 11));
                    var recoveryPlan = CareerBridge.onEdt(() -> adapter.prepare(recoveryRequest));
                    var reconstructed = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(Files.readAllBytes(requestFile))));
                    check(credit.intent("owned-credit").equals(reconstructed.intent("owned-credit")), "separate weak plans reconstruct same versioned durable intent from stored request");
                    check(!credit.acceptsContext(new CareerCommandGate.Context(UUID.randomUUID(), career, UUID.randomUUID(), 1, revision), key.mod())
                                    && !credit.acceptsContext(context, "br.other.owner"), "persistent handler refuses wrong binding or owner");
                    boolean replacedKey = false;
                    try { credit.intent("other-key"); } catch (IllegalArgumentException expected) { replacedKey = true; }
                    check(replacedKey, "durable operation key cannot be substituted by plan caller");
                    var mismatchedIntent = new CareerCommandJournal.Intent("owned-credit", CareerCommandGate.Operation.FINANCE_DEBIT, creditRequest.digest());
                    check(dispatcher.execute(owner, context, mismatchedIntent, CareerSidecarStore.EMPTY, credit).detail() == Detail.HANDLER_MISMATCH,
                            "original credit adapter cannot be dispatched under debit authority");
                    check(journal.read(revision).receipts().isEmpty(), "mismatched adapter never persists intent");
                    var wrongOwnerRequest = new FinanceRequest(career, binding, revision, "br.other.owner", "owned-credit", creditRequest.club(), creditRequest.direction(), creditRequest.amount(), creditRequest.before());
                    var wrongOwnerPlan = CareerBridge.onEdt(() -> adapter.prepare(wrongOwnerRequest));
                    check(dispatcher.execute(owner, context, wrongOwnerRequest.intent(), CareerSidecarStore.EMPTY, wrongOwnerPlan).detail() == Detail.HANDLER_MISMATCH
                                    && journal.read(revision).receipts().isEmpty(), "dispatcher rejects foreign durable owner before journal or effect");
                    for (var wrongScope : List.of(
                            new FinanceRequest(UUID.randomUUID(), binding, revision, key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before),
                            new FinanceRequest(career, UUID.randomUUID(), revision, key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before),
                            new FinanceRequest(career, binding, new CareerSidecarStore.SaveRevision("0".repeat(64), revision.size()), key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before))) {
                        var wrongScopePlan = CareerBridge.onEdt(() -> adapter.prepare(wrongScope));
                        check(dispatcher.execute(owner, context, wrongScope.intent(), CareerSidecarStore.EMPTY, wrongScopePlan).detail() == Detail.HANDLER_MISMATCH
                                        && journal.read(revision).receipts().isEmpty(), "dispatcher refuses durable scope mismatch before any journal intent");
                    }
                    var wrongLocator = new FinanceRequest(career, binding, revision, key.mod(), creditRequest.key(),
                            new FinanceRequest.Club(creditRequest.club().index(), creditRequest.club().id(), "Not the original name"),
                            creditRequest.direction(), creditRequest.amount(), before);
                    boolean wrongTarget = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(wrongLocator)); } catch (IllegalStateException expected) { wrongTarget = true; }
                    check(wrongTarget && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(before), "exact original locator mismatch refuses plan without financial mutation");
                    check(dispatcher.execute(owner, context, creditRequest.intent(), CareerSidecarStore.EMPTY, credit).detail() == Detail.REQUEST_REQUIRED
                                    && journal.read(revision).receipts().isEmpty(), "persistent financial plan cannot bypass payload storage");
                    var storedCredit = dispatcher.executeWitnessedFinance(owner, context, creditRequest, CareerSidecarStore.EMPTY,
                            requestStore, CareerSidecarStore.EMPTY, witnessStore, CareerSidecarStore.EMPTY, credit);
                    var applied = storedCredit.dispatch();
                    check(requestStore.read(revision).requests().equals(List.of(creditRequest)), "complete credit payload committed before original effect");
                    check(applied.kind() == Kind.EXECUTED, "journaled original v(int,int) invoked on EDT");
                    var creditWitness = witnessStore.read(revision);
                    check(creditWitness.witnesses().get(0).operationId().equals(applied.journal().orElseThrow().receipts().get(0).operationId())
                                    && creditWitness.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED,
                            "original applied credit has independent durable causal execution evidence with exact operation UUID");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(credit.expected()), "original balance and all14numeric ledger fields match exact predicted credit");
                    check(credit.expected().balance() == before.balance() + 123 && credit.expected().ledger().get("dO") == before.ledger().get("dO") + 123, "original raw category6 changes correct accumulator");
                    boolean staleRequest = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(decodedRequest)); } catch (IllegalStateException expected) { staleRequest = true; }
                    check(staleRequest && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(credit.expected()), "reconstructing request after financial state changed cannot refresh its precondition");
                    check(dispatcher.executeStoredFinance(owner, context, creditRequest, applied.journal().orElseThrow().token(),
                                    requestStore, storedCredit.requests().token(), credit).dispatch().kind() == Kind.DENIED,
                            "duplicate original command not invoked twice");
                    var debitRequest = CareerBridge.onEdt(() -> adapter.request(context, key.mod(), "owned-debit", human, FinanceDelta.Direction.DEBIT_RAW_7, 23));
                    var debit = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(debitRequest.encode())));
                    check(debit.intent("owned-debit").equals(debitRequest.intent()), "raw debit also uses canonical durable request identity");
                    var storedDebit = dispatcher.executeWitnessedFinance(owner, context, debitRequest, applied.journal().orElseThrow().token(),
                            requestStore, storedCredit.requests().token(), witnessStore, creditWitness.token(), debit);
                    var debited = storedDebit.dispatch();
                    check(requestStore.read(revision).requests().equals(List.of(creditRequest, debitRequest)), "stored immutable credit and debit payloads retain order and exact scope");
                    check(debited.kind() == Kind.EXECUTED, "journaled original w(int,int) invoked");
                    check(witnessStore.read(revision).witnesses().size() == 2 && witnessStore.read(revision).witnesses().stream()
                                    .allMatch(value -> value.state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED), "credit and debit execution facts persist without declaring save inclusion");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(debit.expected()) && debit.expected().balance() == before.balance() + 100,
                            "original debit preserves exact ledger and net100balance");
                    if (probeWriter) {
                        var checkpoint = debit.executionMutationEnd().orElseThrow();
                        check(checkpoint.definingClassesObserved() && checkpoint.healthy() && checkpoint.active() == 0,
                                "both fingerprinted defining classes expose quiescent direct-write observation after original effect");
                        check(HookProbe.financeMutationSnapshot(runtime).stableSince(checkpoint), "unchanged diagnostic checkpoint remains stable before further original writes");
                        CareerBridge.onEdt(() -> { human.getClass().getMethod("e", long.class).invoke(human, debit.expected().balance()); return null; });
                        check(!HookProbe.financeMutationSnapshot(runtime).stableSince(checkpoint)
                                        && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(debit.expected()),
                                "original equal-value balance setter invalidates causal checkpoint even though balance and full ledger remain equal");
                    }
                    var stale = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, 11));
                    var changed = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, 5));
                    var changedResult = dispatcher.execute(owner, context, changed.intent("owned-change"), debited.journal().orElseThrow().token(), changed);
                    check(changedResult.kind() == Kind.EXECUTED, "owned valid change before stale plan");
                    var staleResult = dispatcher.execute(owner, context, stale.intent("owned-stale"), changedResult.journal().orElseThrow().token(), stale);
                    check(staleResult.kind() == Kind.NO_EFFECT && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()), "stale financial expectation rejected before original mutation");
                    check(journal.read(revision).receipts().stream().filter(r -> r.state() == CareerCommandJournal.State.APPLIED).count() == 3,
                            "journal records exactly3original effects,not stale or duplicate");
                    boolean negative = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, -1)); }
                    catch (IllegalArgumentException expected) { negative = true; }
                    check(negative && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()), "negative credit refused without touching original graph");
                    boolean nonEdt = false; try { adapter.snapshot(human); } catch (IllegalStateException expected) { nonEdt = true; }
                    check(nonEdt, "original finance reads refuse caller outside EDT");
                    var noEffectKey = new CareerSidecarStore.Key(career, binding, "br.owned.finance-no-effect");
                    var noEffectJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-no-effect-journal")), noEffectKey);
                    var noEffectOwner = gate.owner(noEffectKey.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                    var noEffectDispatcher = new CareerCommandDispatcher(gate, noEffectJournal, adapter::roots, Duration.ofSeconds(5));
                    var noEffectResult = noEffectDispatcher.execute(noEffectOwner, context, stale.intent("no-effect-only"), CareerSidecarStore.EMPTY, stale);
                    check(noEffectResult.kind() == Kind.NO_EFFECT && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()),
                            "original adapter rejects consumed/stale plan with no mutation in separate no-effect journal");
                    var finalSerializerRequest = CareerBridge.onEdt(() -> adapter.request(context, key.mod(), "owned-serializer-final", human, FinanceDelta.Direction.CREDIT_RAW_6, 1));
                    var finalSerializerPlan = CareerBridge.onEdt(() -> adapter.prepare(finalSerializerRequest));
                    var finalSerializerResult = dispatcher.executeWitnessedFinance(owner, context, finalSerializerRequest, journal.read(revision).token(), requestStore,
                            requestStore.read(revision).token(), witnessStore, witnessStore.read(revision).token(), finalSerializerPlan);
                    check(finalSerializerResult.dispatch().kind() == Kind.EXECUTED, "last original witnessed operation prepares an exact live serializer target in private fixture");
                    var beforeRecovery = CareerBridge.onEdt(() -> adapter.snapshot(human));
                    var recoveryWrites = new java.util.concurrent.atomic.AtomicInteger();
                    var interruptedJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("no-effect-recovery-journals"), stage -> {
                        if (stage == CareerSidecarStore.Stage.RECORD_WRITTEN) recoveryWrites.incrementAndGet();
                        if (recoveryWrites.get() == 2 && stage == CareerSidecarStore.Stage.HEAD_STAGED) throw new java.io.IOException("Owned no-effect terminal receipt interruption");
                    }), recoveryKey);
                    var recoveryWitnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("no-effect-recovery-witnesses")), recoveryKey);
                    var recoveryRequests = new FinanceRequestStore(new CareerSidecarStore(home.resolve("no-effect-recovery-contracts")), recoveryKey);
                    var recoveryOwner = gate.owner(recoveryKey.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                    var interruptedResult = new CareerCommandDispatcher(gate, interruptedJournal, adapter::roots, Duration.ofSeconds(5))
                            .executeWitnessedFinance(recoveryOwner, context, recoveryRequest, CareerSidecarStore.EMPTY, recoveryRequests, CareerSidecarStore.EMPTY,
                                    recoveryWitnesses, CareerSidecarStore.EMPTY, recoveryPlan);
                    check(interruptedResult.dispatch().kind() == Kind.UNCERTAIN && interruptedResult.dispatch().detail() == Detail.JOURNAL_FAILURE
                                    && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(beforeRecovery),
                            "original stale persistent plan proves no effect, but injected terminal journal failure remains UNKNOWN");
                    var recoveryHistory = new CareerCommandJournal(new CareerSidecarStore(home.resolve("no-effect-recovery-journals")), recoveryKey);
                    var pendingRecovery = recoveryHistory.read(revision); var noEffectEvidence = recoveryWitnesses.read(revision);
                    check(pendingRecovery.receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN
                                    && noEffectEvidence.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED,
                            "separate committed no-effect witness survives original handler refusal and missing terminal receipt");
                    var proofNoEffect = recoveryWitnesses.proveNoEffect(revision, noEffectEvidence.token(), pendingRecovery.receipts().get(0).operationId(), recoveryRequest.intent());
                    var adjudicated = recoveryHistory.reconcileNoEffect(proofNoEffect, pendingRecovery.token());
                    check(adjudicated.receipts().equals(pendingRecovery.receipts()) && adjudicated.noEffectResolutions().size() == 1
                                    && adjudicated.effectiveReceipts().get(0).state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED,
                            "proven original no-effect can be adjudicated without changing raw UNKNOWN/UUID/payload");
                    check(recoveryHistory.prepare(revision, adjudicated.token(), recoveryRequest.intent()).lease().isEmpty(),
                            "original rejected operation never becomes replayable after adjudication");
                    var beforeSave = CareerBridge.onEdt(() -> adapter.snapshot(human));
                    var preWriterMutations = probeWriter ? HookProbe.financeMutationSnapshot(runtime) : null;
                    var saveFence = gate.beginSave();
                    try { CareerBridge.onEdt(() -> { saveMethod.invoke(null, "finance-output"); return null; }); }
                    finally { gate.endSave(saveFence); } // Even writer failure must not restore the old context.
                    check(Files.isRegularFile(game.resolve("sav/finance-output.s22")) && Files.mismatch(game.resolve("sav/finance-output.s22"), game.resolve("sav/finance-output.sbck")) == -1,
                            "original writer produces private output and matching backup");
                    var writtenStream = probeWriter ? new WrittenSaveVerifier(runtime).verify(observer.observe(game.resolve("sav"), "finance-output.s22")) : null;
                    if (probeWriter) {
                        check(writtenStream.bytes().sha256().equals(Profile.digest(game.resolve("sav/finance-output.s22"))), "post-financial-effect output is linked to actual original written stream, not balance inference");
                        var bindingWritten = HookProbe.writtenSaveProvenance().binding().orElseThrow();
                        check(bindingWritten.financeBefore().orElseThrow().definingClassesObserved() && bindingWritten.financeAfter().orElseThrow().definingClassesObserved(),
                                "serializer boundary captures checkpoints from the same instrumented runtime as original roots");
                        check(bindingWritten.noObservedFinanceWriteDuringSerialization(), "private original serializer observed no selected direct financial writes during the two root writes; not full mutation coverage");
                        var mutationAssessment = FinanceMutationAssessment.assess(changed.executionMutationEnd(), bindingWritten);
                        check(mutationAssessment.executionToSerializer() != FinanceMutationAssessment.Status.UNAVAILABLE
                                && mutationAssessment.serializerWindow() == FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE,
                                "original financial effect and serializer checkpoints can be compared without treating sampled ledger equality as causal proof");
                        check(preWriterMutations != null && preWriterMutations.healthy(), "original pre-writer mutation checkpoint remains diagnostic, not a save grant");
                        var operationCapture = bindingWritten.operationCapture();
                        check(operationCapture.validWindow() && !operationCapture.capacityExceeded() && operationCapture.operations().size() == 3,
                                "original root serializer captures bounded operation UUIDs only for three witnessed applied plans, not stale/rejected or legacy commands");
                        var creditOperation = applied.journal().orElseThrow().receipts().get(0).operationId();
                        var debitOperation = debited.journal().orElseThrow().receipts().get(1).operationId();
                        var finalOperation = finalSerializerResult.dispatch().journal().orElseThrow().receipts().stream()
                                .filter(value -> value.intent().equals(finalSerializerRequest.intent())).findFirst().orElseThrow().operationId();
                        check(operationCapture.operations().stream().map(FinanceSerializationTracker.Operation::operationId).collect(java.util.stream.Collectors.toSet())
                                        .equals(Set.of(creditOperation, debitOperation, finalOperation)), "captured original-root observations retain exact journal UUIDs and no invented identity");
                        check(operationCapture.operations().stream().filter(value -> !value.operationId().equals(finalOperation)).allMatch(value -> value.before() == FinanceSerializationTracker.Sample.STATE_MISMATCH
                                        && value.after() == FinanceSerializationTracker.Sample.STATE_MISMATCH),
                                "later original financial operations prevent earlier expected snapshots being presented as unchanged at serialization");
                        check(operationCapture.operations().stream().filter(value -> !value.operationId().equals(finalOperation)).allMatch(value -> value.executionToSerializer() == FinanceSerializationTracker.Continuity.OBSERVED_INTERFERENCE),
                                "witnessed operations report direct-write interference before serializer even if output writes are stable");
                        var lastCapture = operationCapture.operations().stream().filter(value -> value.operationId().equals(finalOperation)).findFirst().orElseThrow();
                        check(lastCapture.before() == FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE && lastCapture.after() == FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE,
                                "exact final original club/ledger expected snapshot is sampled around root serialization without asserting individual field emission");
                        check(lastCapture.fields().status() == FinanceSerializationTracker.Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED && lastCapture.fields().observed() == 15,
                                "all fifteen exact final financial numeric arguments observed after original cached-field Output calls returned: " + lastCapture.fields());
                        check(operationCapture.operations().stream().filter(value -> !value.operationId().equals(finalOperation))
                                        .allMatch(value -> value.fields().status() == FinanceSerializationTracker.Emission.VALUE_MISMATCH && value.fields().observed() == 15),
                                "actual numeric emissions mismatch previous operations expected states, not inferred from root samples");
                        check(!lastCapture.fields().routes().isEmpty(), "runtime identifies actual numeric cached-field routes without exporting financial values");
                        check(operationCapture.operations().stream().allMatch(value -> value.edge().status() == FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED
                                        && value.edge().observed() == 1), "all witnessed plans identify same exact original ledger argument after original Kryo reference write returned: " + lastCapture.edge());
                        check(!lastCapture.edge().routes().isEmpty(), "original ledger reference route observed separately from numeric values");
                        System.out.println("Observed original numeric routes: " + new TreeSet<>(lastCapture.fields().routes()) + "; fields=" + lastCapture.fields().observed() + "; operation inclusion remains unproven.");
                        System.out.println("Observed original ledger reference routes: " + new TreeSet<>(lastCapture.edge().routes()) + "; operation inclusion remains unproven.");
                        var originalJournal = journal.read(revision); var originalContracts = requestStore.read(revision);
                        var capturedWriter = new WrittenSaveVerifier(runtime).capture(observer.observe(game.resolve("sav"), "finance-output.s22"));
                        var persistedWriter = witnessStore.observeSerialization(revision, witnessStore.read(revision).token(), capturedWriter, requestStore, journal);
                        check(persistedWriter.serializations().size() == 1 && persistedWriter.serializations().get(0).operations().size() == 3,
                                "durable observation links all three exact witnessed UUIDs to original writer capture");
                        check(persistedWriter.serializations().get(0).output().equals(bindingWritten.bytes())
                                        && !persistedWriter.serializations().get(0).operationInclusionProven() && !persistedWriter.serializations().get(0).writeAuthority(),
                                "committed observed numeric/reference arguments retain exact stream bytes without claiming inclusion or authority");
                        check(witnessStore.observeSerialization(revision, persistedWriter.token(), capturedWriter, requestStore, journal).equals(persistedWriter),
                                "same live writer capture confirms read-only without duplicate observation or effect retry");
                        check(journal.read(revision).equals(originalJournal) && requestStore.read(revision).equals(originalContracts),
                                "durable writer observation never modifies receipt, contract, anchor or payload");
                        check(new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("finance-execution-witnesses")), key).read(revision).equals(persistedWriter),
                                "reopened schema2 reconstructs exact original writer evidence without runtime objects");
                        java.lang.ref.Reference.reachabilityFence(credit); java.lang.ref.Reference.reachabilityFence(debit); java.lang.ref.Reference.reachabilityFence(finalSerializerPlan);
                    }
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "finance-output", false)), "original loader reloads private financially modified save");
                    var restoredRoots = CareerBridge.onEdt(adapter::roots);
                    Object restoredHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(restoredRoots.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.snapshot(restoredHuman)).equals(beforeSave), "exact balance and all ledger fields survive original write/reload");
                    check(new CareerBridge(runtime).snapshot().clubs().size() == clubCount, "club count retained after original finance roundtrip");
                    var outputObservation = observer.observe(game.resolve("sav"), "finance-output.s22");
                    var reopenedWitnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("finance-execution-witnesses")), key);
                    check(reopenedWitnesses.read(revision).equals(witnessStore.read(revision)), "execution evidence reopens unchanged after original writer/reload");
                    boolean witnessRevisionRefused = false;
                    try { reopenedWitnesses.read(outputObservation.revision()); } catch (CareerSidecarStore.Failure expected) { witnessRevisionRefused = expected.reason() == CareerSidecarStore.Reason.SAVE_REVISION_MISMATCH; }
                    check(witnessRevisionRefused, "memory applied evidence never migrates to output save revision by balance/hash comparison");
                    check(!outputObservation.revision().equals(sourceObservation.revision())
                            && associations.assess(associated, outputObservation) != CareerSaveAssociations.Match.EXACT_OBSERVATION,
                            "newly written original save is not silently the old file/revision association");
                    boolean needsReconciliation = false;
                    try { associations.acceptRevision(associated.handle(), associated.token(), outputObservation, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION); }
                    catch (CareerSidecarStore.Failure expected) { needsReconciliation = expected.reason() == CareerSidecarStore.Reason.RECONCILIATION_REQUIRED; }
                    check(needsReconciliation && associations.inspect(associated.handle()).token().equals(associated.token()), "save-as with new name and bytes requires separate policy, cannot silently move association");
                    boolean journalMismatch = false;
                    try { journal.read(outputObservation.revision()); }
                    catch (CareerSidecarStore.Failure expected) { journalMismatch = expected.reason() == CareerSidecarStore.Reason.SAVE_REVISION_MISMATCH; }
                    check(journalMismatch, "journal not silently reanchored to financially modified output save");
                    boolean appliedMigrationRefused = false;
                    try { journal.reanchorNoEffects(revision, outputObservation.revision(), journal.read(revision).token()); }
                    catch (CareerSidecarStore.Failure expected) { appliedMigrationRefused = expected.reason() == CareerSidecarStore.Reason.RECONCILIATION_REQUIRED; }
                    check(appliedMigrationRefused, "original APPLIED financial effects remain historical; cannot use no-effect migration");
                    var preservedNoEffects = noEffectJournal.reanchorNoEffects(revision, outputObservation.revision(), noEffectResult.journal().orElseThrow().token());
                    check(preservedNoEffects.receipts().equals(noEffectResult.journal().orElseThrow().receipts())
                            && noEffectJournal.read(outputObservation.revision()).token().equals(preservedNoEffects.token()),
                            "only verified no-effect receipts reanchor across original save/reload without replay or graph write");
                    var recovery = journal.inspectRecovery(revision, outputObservation.revision());
                    check(recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS && recovery.revisionChanged(),
                            "original roundtrip still classified as historical effects, not automatic journal reconciliation");
                    if (probeWriter) {
                        var decodedOutput = new LoadedSaveVerifier(runtime).verify(outputObservation);
                        var assessment = FinanceSaveAssessment.assess(key, requestStore.inspectRecordedRecovery(outputObservation.revision()), recovery,
                                decodedOutput, Optional.of(writtenStream));
                        check(assessment.relation() == FinanceSaveAssessment.SaveRelation.EXACT_WRITTEN_BYTES_RELOADED && assessment.blocksContext(),
                                "original writer/decoder bytes correlate but do not clear historical operation quarantine");
                        check(assessment.operations().stream().filter(operation -> operation.receipt().orElse(null) == CareerCommandJournal.State.APPLIED)
                                        .allMatch(operation -> operation.inclusion() == FinanceSaveAssessment.Inclusion.UNPROVEN && operation.operationId().isPresent()),
                                "per-operation APPLIED UUIDs retain unproven inclusion even with matching original output/reload bytes");
                    }
                    check(journal.quarantinePending(revision, recovery.snapshot().token()).token().equals(recovery.snapshot().token()),
                            "startup quarantine cannot rewrite terminal original financial receipts");
                    var oldPlanResult = dispatcher.execute(owner, context, changed.intent("old-roots"), journal.read(revision).token(), changed);
                    check(oldPlanResult.kind() == Kind.DENIED && oldPlanResult.receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT, "save window invalidates old context; reloaded roots never automatically authorize command");
                    check(!decodedRequest.matches(new CareerCommandGate.Context(UUID.randomUUID(), career, binding, 2, outputObservation.revision()), key.mod()),
                            "persistent request cannot cross into output revision even after original write/reload");
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false)), "original input can be decoded again only inside fixture");
                    var inputRoots = CareerBridge.onEdt(adapter::roots);
                    Object inputHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(inputRoots.primary(), "aN")).get(0));
                    var restartPlan = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(Files.readAllBytes(requestFile))));
                    check(restartPlan.intent("owned-credit").equals(creditRequest.intent()), "request identity survives original graph reload without session nonce");
                    try (var restartGate = new CareerCommandGate()) {
                        var restartOwner = restartGate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                        var restartContext = restartGate.installContext(career, binding, 2, revision, inputRoots.primary(), inputRoots.secondary());
                        var restartJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-journal")), key);
                        var reopenedRequests = new FinanceRequestStore(new CareerSidecarStore(home.resolve("finance-requests")), key);
                        var retry = new CareerCommandDispatcher(restartGate, restartJournal, adapter::roots, Duration.ofSeconds(5))
                                .executeWitnessedFinance(restartOwner, restartContext, decodedRequest, restartJournal.read(revision).token(),
                                        reopenedRequests, reopenedRequests.read(revision).token(), reopenedWitnesses, reopenedWitnesses.read(revision).token(), restartPlan).dispatch();
                        check(retry.kind() == Kind.RECORDED_ONLY && CareerBridge.onEdt(() -> adapter.snapshot(inputHuman)).equals(before),
                                "reopened journal and fresh gate never replay historical APPLIED request on restored input graph");
                    }
                    if (probeWriter) interruptedSelectedSetter(runtime, adapter, inputHuman);
                }
                check(Profile.digest(game.resolve("sav/input.s22")).equals(inputHash), "private input save untouched");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { current.setContextClassLoader(previous); }
        System.out.println("PASS: " + checks + " original finance/ledger dispatch roundtrip checks; private fixture only, not standalone EXE reopening or production context acceptance.");
    }
}
