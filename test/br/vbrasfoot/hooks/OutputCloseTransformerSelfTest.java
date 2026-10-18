package br.vbrasfoot.hooks;

import br.vbrasfoot.HookProbe;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class OutputCloseTransformerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class FixtureLoader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.OutputCloseFixture", bytes, 0, bytes.length); }
    }
    private static final class Stream extends OutputStream {
        final boolean fail;
        int closes;
        Stream(boolean fail) { this.fail = fail; }
        public void write(int value) { }
        public void close() throws IOException { closes++; if (fail) throw new IOException("owned close fixture"); }
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (var input = OutputCloseTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/OutputCloseFixture.class")) { original = input.readAllBytes(); }
        boolean refused = false;
        try { OutputCloseTransformer.transform(original); } catch (IOException expected) { refused = true; }
        check(refused, "production fingerprint rejects owned synthetic class");
        Class<?> plain = new FixtureLoader().define(original), transformed = new FixtureLoader().define(OutputCloseTransformer.transformFixture(original));
        check(plain.getDeclaredFields().length == transformed.getDeclaredFields().length
                && plain.getDeclaredMethods().length == transformed.getDeclaredMethods().length, "schema shape preserved");
        var constructor = transformed.getConstructor(OutputStream.class); var close = transformed.getMethod("close");
        var attach = HookProbe.class.getDeclaredMethod("attach", java.util.function.Consumer.class); attach.setAccessible(true);
        List<HookProbe.Observation> events = new ArrayList<>();
        try (var subscription = (AutoCloseable) attach.invoke(null, (java.util.function.Consumer<HookProbe.Observation>) events::add)) {
            Stream good = new Stream(false); Object output = constructor.newInstance(good);
            HookProbe.enterSave("good"); HookProbe.primaryOutputClosing(output); close.invoke(output); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(events.size() == 1 && events.get(0).completed() && good.closes == 1, "normal close observed without extra close calls");
            Stream bad = new Stream(true); Object failing = constructor.newInstance(bad);
            HookProbe.enterSave("bad"); HookProbe.primaryOutputClosing(failing); close.invoke(failing); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(events.size() == 2 && !events.get(1).completed() && events.get(1).returnedSuccess() && events.get(1).backup()
                    && !events.get(1).aborted() && bad.closes == 1, "swallowed IOException detected without changing return/backup policy");
            Stream other = new Stream(true); Object unrelated = constructor.newInstance(other);
            HookProbe.enterSave("identity"); HookProbe.primaryOutputClosing(output); close.invoke(unrelated); close.invoke(output); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(events.get(2).completed() && other.closes == 1, "unrelated output failure cannot poison primary output");
            close.invoke(failing);
            check(events.size() == 3 && bad.closes == 2, "library close outside a save never publishes an event");
            HookProbe.enterSave("null-stream"); Object empty = constructor.newInstance((Object) null); HookProbe.primaryOutputClosing(empty);
            close.invoke(empty); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(events.get(3).returnedSuccess(), "null stream preserves original wrapper return semantics");
            HookProbe.enterSave("sticky"); HookProbe.primaryOutputClosing(failing); close.invoke(failing);
            HookProbe.primaryStreamClosed(failing); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(!events.get(4).completed(), "later success marker never erases observed failure");
        }
        byte[] changed;
        try (var input = OutputCloseTransformerSelfTest.class.getResourceAsStream("/br/vbrasfoot/OutputMissingCatchFixture.class")) { changed = input.readAllBytes(); }
        refused = false;
        try { OutputCloseTransformer.transformFixture(changed); } catch (IOException expected) { refused = true; }
        check(refused, "missing IOException handler rejected before class definition");
        System.out.println("PASS: " + checks + " synthetic Output close instrumentation checks; no proprietary bytecode shipped.");
    }
}
