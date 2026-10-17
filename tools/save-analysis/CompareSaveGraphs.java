import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Offline comparison of decoded copies using original Kryo's actual cached fields. */
public final class CompareSaveGraphs {
    public static final class Fixture {
        public int amount;
        public Object first, second;
        public Fixture() { }
    }
    private final Kryo schema = new Kryo();
    private final IdentityHashMap<Object, Object> forward = new IdentityHashMap<>(), reverse = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Object> stringsForward = new IdentityHashMap<>(), stringsReverse = new IdentityHashMap<>();
    private final List<String> differences = new ArrayList<>(), limitations = new ArrayList<>();
    private long compared, fields, strings, stringAliasChanges, uniqueMutable;
    private final Map<String, Long> types = new TreeMap<>();
    private final Set<String> unsupported = new TreeSet<>();

    private void different(String path, String reason) {
        if (differences.size() < 100) differences.add(path + ": " + reason);
    }

    private void compare(Object a, Object b, String path, int depth) throws Exception {
        compared++;
        if (compared > 200_000_000 || depth > 512) {
            if (limitations.isEmpty()) limitations.add("Traversal budget exceeded; graph comparison incomplete");
            return;
        }
        if (a == null || b == null) { if (a != b) different(path, "null changed"); return; }
        Class<?> type = a.getClass();
        if (type != b.getClass()) { different(path, "runtime type changed"); return; }
        types.merge(type.getName(), 1L, Long::sum);
        if (a instanceof String) {
            strings++;
            if (!a.equals(b)) different(path, "string value changed (not printed)");
            Object old = stringsForward.putIfAbsent(a, b), inverse = stringsReverse.putIfAbsent(b, a);
            if ((old != null && old != b) || (inverse != null && inverse != a)) stringAliasChanges++;
            return;
        }
        if (type.isEnum() || a instanceof Boolean || a instanceof Character || a instanceof Number) {
            if (a instanceof Double x && b instanceof Double y) {
                if (Double.doubleToRawLongBits(x) != Double.doubleToRawLongBits(y)) different(path, "raw double bits changed");
            } else if (a instanceof Float x && b instanceof Float y) {
                if (Float.floatToRawIntBits(x) != Float.floatToRawIntBits(y)) different(path, "raw float bits changed");
            } else if (!a.equals(b)) different(path, "scalar value changed");
            return;
        }
        Object seen = forward.get(a), inverse = reverse.get(b);
        if (seen != null) { if (seen != b) different(path, "mutable reference split"); return; }
        if (inverse != null && inverse != a) { different(path, "mutable references merged"); return; }
        forward.put(a, b); reverse.put(b, a);
        if (type.isArray()) {
            int length = Array.getLength(a);
            if (length != Array.getLength(b)) { different(path, "array length changed"); return; }
            for (int i = 0; i < length; i++) compare(Array.get(a, i), Array.get(b, i), path + "[" + i + "]", depth + 1);
        } else if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) { different(path, "list size changed"); return; }
            for (int i = 0; i < x.size(); i++) compare(x.get(i), y.get(i), path + "[" + i + "]", depth + 1);
        } else if (a instanceof Calendar x && b instanceof Calendar y) {
            if (x.getTimeInMillis() != y.getTimeInMillis() || x.isLenient() != y.isLenient()
                    || x.getFirstDayOfWeek() != y.getFirstDayOfWeek() || x.getMinimalDaysInFirstWeek() != y.getMinimalDaysInFirstWeek()
                    || !x.getTimeZone().getID().equals(y.getTimeZone().getID()) || !x.getTimeZone().hasSameRules(y.getTimeZone())) different(path, "calendar observed properties changed");
            if (x instanceof GregorianCalendar gx && y instanceof GregorianCalendar gy && !gx.getGregorianChange().equals(gy.getGregorianChange())) different(path, "Gregorian cutover changed");
        } else if (a instanceof TimeZone x && b instanceof TimeZone y) {
            if (!x.getID().equals(y.getID()) || !x.hasSameRules(y)) different(path, "timezone observed rules changed");
        } else if (a instanceof Date x && b instanceof Date y) {
            if (x.getTime() != y.getTime()) different(path, "date changed");
        } else {
            Serializer<?> serializer = schema.getSerializer(type);
            if (!(serializer instanceof FieldSerializer<?> fs)) {
                unsupported.add(type.getName() + " serializer=" + serializer.getClass().getName());
                return;
            }
            for (FieldSerializer.CachedField<?> cached : fs.getFields()) {
                Field field = cached.getField();
                try { field.setAccessible(true); }
                catch (RuntimeException inaccessible) { unsupported.add(type.getName() + "." + field.getName() + " inaccessible"); continue; }
                fields++;
                compare(field.get(a), field.get(b), path + "." + field.getName(), depth + 1);
            }
        }
    }

    private static Object[] read(Path path) throws Exception {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        try (Input input = new Input(Files.newInputStream(path))) {
            Object[] result = {kryo.readClassAndObject(input), kryo.readClassAndObject(input)};
            if (input.read() != -1) throw new IllegalStateException("Extra bytes after roots");
            return result;
        }
    }

    private static void expect(Object a, Object b, boolean changed, String label) throws Exception {
        CompareSaveGraphs probe = new CompareSaveGraphs();
        probe.schema.setRegistrationRequired(false);
        probe.compare(a, b, "fixture", 0);
        if (probe.differences.isEmpty() == changed || !probe.unsupported.isEmpty() || !probe.limitations.isEmpty()) throw new AssertionError(label);
        System.out.println("PASS: " + label);
    }

    private static void selfTest() throws Exception {
        Fixture a = new Fixture(), b = new Fixture();
        a.amount = b.amount = 2;
        a.first = new String(new char[0]); a.second = new String(new char[0]);
        b.first = b.second = "";
        expect(a, b, false, "same values with empty String identity normalization");
        b.amount = 3;
        expect(a, b, true, "serialized primitive mutation");
        a.first = a.second = new int[] {1,2};
        b.amount = 2; b.first = new int[] {1,2}; b.second = new int[] {1,2};
        expect(a, b, true, "mutable alias split");
        expect(List.of(1,2), List.of(2,1), true, "list order change");
        expect(new int[] {1}, new int[] {1,2}, true, "array length change");
        expect(new double[] {Double.longBitsToDouble(0x7ff8000000000001L)},
                new double[] {Double.longBitsToDouble(0x7ff8000000000002L)}, true, "NaN payload change");
        Fixture cycleA = new Fixture(), cycleB = new Fixture();
        cycleA.first = cycleA; cycleB.first = cycleB;
        expect(cycleA, cycleB, false, "equal cyclic graph");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
        Class.forName("best.M").getConstructor(boolean.class).newInstance(true);
        Object[] first = read(Path.of(args[0])), second = read(Path.of(args[1]));
        CompareSaveGraphs comparison = new CompareSaveGraphs();
        comparison.schema.setRegistrationRequired(false);
        for (int i = 0; i < first.length; i++) {
            // The game resets Kryo references between root writes; compare root alias topology separately.
            comparison.forward.clear(); comparison.reverse.clear();
            comparison.stringsForward.clear(); comparison.stringsReverse.clear();
            comparison.compare(first[i], second[i], "root[" + i + "]", 0);
            comparison.uniqueMutable += comparison.forward.size();
        }
        System.out.println("compared=" + comparison.compared + " serializedFields=" + comparison.fields + " uniqueMutableObjects=" + comparison.uniqueMutable);
        System.out.println("stringOccurrences=" + comparison.strings + " stringAliasChanges=" + comparison.stringAliasChanges);
        System.out.println("differences=" + comparison.differences.size() + " limitations=" + comparison.limitations.size() + " unsupportedTypes=" + comparison.unsupported.size());
        comparison.differences.forEach(System.out::println);
        comparison.limitations.forEach(System.out::println);
        comparison.unsupported.forEach(System.out::println);
        System.out.println("Observed types: " + comparison.types);
        System.out.println("Coverage: original Kryo serialized fields, ordered lists/arrays, scalar values, mutable alias topology; String identity changes counted separately. Transient/static fields excluded. Calendar/timezone compared via listed public properties, not private implementation internals. No gameplay compatibility conclusion.");
        if (!comparison.differences.isEmpty() || !comparison.limitations.isEmpty() || !comparison.unsupported.isEmpty()) System.exit(2);
    }
}
