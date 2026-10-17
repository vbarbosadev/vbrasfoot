import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Analyst-only offline probe. Never use this modified serializer in the launcher/game. */
public final class TraceSaveFields {
    private static long focus;
    private static int records;
    public static final class EmptyNames {
        public String first, second;
        public EmptyNames() { }
    }

    private static void demonstrateEmptyStrings() {
        EmptyNames value = new EmptyNames();
        value.first = new String(new char[0]); value.second = new String(new char[0]);
        Kryo kryo = new Kryo(); kryo.setRegistrationRequired(false);
        Output first = new Output(256, -1);
        kryo.writeClassAndObject(first, value);
        byte[] original = first.toBytes();
        EmptyNames decoded = (EmptyNames) kryo.readClassAndObject(new Input(original));
        Output second = new Output(256, -1);
        kryo.writeClassAndObject(second, decoded);
        System.out.println("emptyStringFixture.referenceMerged=" + (decoded.first == decoded.second)
                + " valuesPreserved=" + (value.first.equals(decoded.first) && value.second.equals(decoded.second))
                + " byteIdentical=" + java.util.Arrays.equals(original, second.toBytes()));
    }

    private static final class TraceKryo extends Kryo {
        @Override protected Serializer newDefaultSerializer(Class type) {
            Serializer serializer = super.newDefaultSerializer(type);
            if (serializer instanceof FieldSerializer fieldSerializer) {
                FieldSerializer.CachedField[] fields = fieldSerializer.getFields();
                for (int i = 0; i < fields.length; i++) fields[i] = new TraceField(fields[i]);
            }
            return serializer;
        }
    }

    private static final class TraceField extends FieldSerializer.CachedField {
        private final FieldSerializer.CachedField delegate;
        TraceField(FieldSerializer.CachedField delegate) { this.delegate = delegate; }
        @Override public Field getField() { return delegate.getField(); }
        @Override public void copy(Object from, Object to) { delegate.copy(from, to); }
        @Override public void read(Input input, Object object) {
            long before = input.total();
            delegate.read(input, object);
            report("read", before, input.total(), object);
        }
        @Override public void write(Output output, Object object) {
            long before = output.total();
            delegate.write(output, object);
            report("write", before, output.total(), object);
        }
        private void report(String direction, long before, long after, Object object) {
            if (!(before <= focus && focus < after) || records++ >= 100) return;
            Field field = delegate.getField();
            String value = "opaque";
            try {
                field.setAccessible(true);
                Object item = field.get(object);
                if (item instanceof String text) value = "String[length=" + text.length() + ",empty=" + text.isEmpty() + "]";
                else if (item == null || field.getType().isPrimitive() || item instanceof Number || item instanceof Boolean) value = String.valueOf(item);
                else value = item.getClass().getName();
            } catch (Exception failure) { value = failure.getClass().getSimpleName(); }
            System.out.println(direction + " " + before + ".." + after + " " + field.getDeclaringClass().getName() + "." + field.getName() + "=" + value);
        }
    }

    public static void main(String[] args) throws Exception {
        Path original = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
        focus = Long.parseLong(args[2]);
        if (Files.exists(output)) throw new IllegalArgumentException("Probe output must be new");
        Class.forName("best.M").getConstructor(boolean.class).newInstance(true);
        TraceKryo reader = new TraceKryo();
        reader.setRegistrationRequired(false);
        Object first, second;
        try (Input input = new Input(Files.newInputStream(original))) {
            first = reader.readClassAndObject(input);
            second = reader.readClassAndObject(input);
            if (input.read() != -1) throw new IllegalStateException("Extra save bytes");
        }
        TraceKryo writer = new TraceKryo();
        writer.setRegistrationRequired(false);
        try (Output out = new Output(Files.newOutputStream(output, java.nio.file.StandardOpenOption.CREATE_NEW))) {
            writer.writeClassAndObject(out, first);
            writer.writeClassAndObject(out, second);
        }
        System.out.println("Probe serializer is modified: corroborate output hash with the unmodified SaveCheck result.");
        demonstrateEmptyStrings();
    }
}
