package br.vbrasfoot;

import java.lang.reflect.*;
import java.util.*;

/** Sampled original metadata only, never an exclusive execution boundary or proof of bytes/references. */
final class LedgerSerializerProfile {
    enum Status { SUPPORTED_SAMPLED_LAYOUT, WRONG_THREAD, WRONG_TYPE, MEMORY_REGIONS, TRANSIENT_SERIALIZATION,
        FIELD_SET_MISMATCH, UNSUPPORTED_FIELD_ROUTE, METADATA_UNAVAILABLE }
    private static final String PREFIX = "com.esotericsoftware.kryo.serializers.";
    private static final Set<String> LONGS = Set.of("dK", "dN", "dR", "dS");
    private LedgerSerializerProfile() { }
    static Status inspect(Object serializer, Object ledger, ClassLoader runtime) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) return Status.WRONG_THREAD;
        try {
            if (runtime == null || ledger == null || serializer == null || ledger.getClass().getClassLoader() != runtime
                    || !ledger.getClass().getName().equals("best.C")) return Status.WRONG_TYPE;
            Class<?> expected = Class.forName(PREFIX + "FieldSerializer", false, runtime);
            if (serializer.getClass() != expected || expected.getMethod("getType").invoke(serializer) != ledger.getClass()) return Status.WRONG_TYPE;
            if (!Boolean.FALSE.equals(expected.getMethod("getUseMemRegions").invoke(serializer))) return Status.MEMORY_REGIONS;
            if (!Boolean.FALSE.equals(expected.getMethod("getSerializeTransient").invoke(serializer))) return Status.TRANSIENT_SERIALIZATION;
            Object fields = expected.getMethod("getFields").invoke(serializer);
            if (fields == null || !fields.getClass().isArray() || Array.getLength(fields) != FinanceDelta.FIELDS.size()) return Status.FIELD_SET_MISMATCH;
            Class<?> cachedBase = Class.forName(PREFIX + "FieldSerializer$CachedField", false, runtime);
            var names = new HashSet<String>();
            for (int i = 0; i < FinanceDelta.FIELDS.size(); i++) {
                Object cached = Array.get(fields, i);
                if (cached == null || cached.getClass().getClassLoader() != runtime || !cachedBase.isInstance(cached)) return Status.UNSUPPORTED_FIELD_ROUTE;
                var field = (Field) cachedBase.getMethod("getField").invoke(cached);
                if (field == null || field.getDeclaringClass() != ledger.getClass() || !FinanceDelta.FIELDS.contains(field.getName()) || !names.add(field.getName())
                        || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()
                        || field.getType() != (LONGS.contains(field.getName()) ? long.class : int.class)) return Status.FIELD_SET_MISMATCH;
                String suffix = LONGS.contains(field.getName()) ? "LongField" : "IntField";
                String route = cached.getClass().getName();
                if (!(route.equals(PREFIX + "UnsafeCacheFields$Unsafe" + suffix) || route.equals(PREFIX + "AsmCacheFields$Asm" + suffix)
                        || route.equals(PREFIX + "ObjectField$Object" + suffix))) return Status.UNSUPPORTED_FIELD_ROUTE;
                if (cached.getClass() != Class.forName(route, false, runtime)) return Status.UNSUPPORTED_FIELD_ROUTE;
            }
            return names.equals(FinanceDelta.FIELDS) ? Status.SUPPORTED_SAMPLED_LAYOUT : Status.FIELD_SET_MISMATCH;
        } catch (Throwable failure) { return Status.METADATA_UNAVAILABLE; }
    }
}
