package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;

import java.awt.Window;
import java.nio.file.*;
import java.util.*;
import javax.swing.JFrame;

/** Real original I/O methods on newly named fixture saves, not a gameplay/UI test. */
public final class CareerHookRuntimeTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static HookProbe.Observation only(List<HookProbe.Observation> events, String operation) {
        check(events.size() == 1 && events.get(0).operation().equals(operation), "exactly one " + operation);
        return events.get(0);
    }
    private static List<String> schema(Class<?> type) {
        List<String> result = new ArrayList<>();
        result.add(type.getName() + ":" + type.getSuperclass().getName());
        for (var field : type.getDeclaredFields()) result.add("f:" + field.getName() + ":" + field.getType().getName() + ":" + field.getModifiers());
        for (var method : type.getDeclaredMethods()) result.add("m:" + method.getName() + ":" + Arrays.toString(method.getParameterTypes()) + ":" + method.getReturnType().getName() + ":" + method.getModifiers());
        Collections.sort(result); return result;
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".hook-test-fixture")) || !Path.of("").toAbsolutePath().toRealPath().equals(game)) {
            throw new IllegalArgumentException("Requires caller-created fixture marker and real fixture cwd");
        }
        Path saves = game.resolve("sav"), original = saves.resolve("input.s22");
        String before = Profile.digest(original), executableBefore = Profile.digest(game.resolve("bf22-23.exe"));
        List<HookProbe.Observation> events = new ArrayList<>();
        List<HookProbe.Observation> transcript = new ArrayList<>();
        try (var attached = HookProbe.attach(event -> { events.add(event); transcript.add(event); });
             LegacyRuntime plain = LegacyRuntime.prepare(Profile.verify(game), home.resolve("plain"));
             LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("probe"), true)) {
            Class<?> state = Class.forName("c.a", false, runtime);
            check(schema(Class.forName("c.a", false, plain)).equals(schema(state)), "c.a schema unchanged");
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            // Set a private synthetic frame to exercise original decode/fallback without creating the game UI.
            JFrame frame = CareerBridge.onEdt(() -> {
                JFrame value = new JFrame("vbrasfoot hook I/O fixture — not gameplay");
                value.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, value);
                return value;
            });
            try {
                var load = state.getMethod("c", String.class, boolean.class);
                var save = state.getMethod("ae", String.class);
                boolean loaded = CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false));
                HookProbe.Observation observation = only(events, "load");
                check(loaded && observation.completed() && !observation.backup() && observation.thread().startsWith("AWT-EventQueue"), "real primary load on EDT");
                GameAccess.Snapshot baseline = new CareerBridge(runtime).snapshot();
                check(baseline.loaded(), "decoded career readable");
                events.clear();
                CareerBridge.onEdt(() -> { save.invoke(null, "output"); return null; });
                observation = only(events, "save");
                check(observation.completed() && observation.backup(), "primary close and legacy backup completed");
                check(Files.isRegularFile(saves.resolve("output.s22")) && Files.mismatch(saves.resolve("output.s22"), saves.resolve("output.sbck")) == -1, "save and backup bytes agree");
                events.clear();
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "output", false)), "produced save reloads by original method");
                check(only(events, "load").completed(), "reload observation");
                // Original save preconditions assign IDs. Compare values while allowing those original ID changes.
                GameAccess.Snapshot restored = new CareerBridge(runtime).snapshot();
                check(baseline.clubs().size() == restored.clubs().size(), "club count preserved");
                check(baseline.clubs().stream().mapToInt(c -> c.players().size()).sum() == restored.clubs().stream().mapToInt(c -> c.players().size()).sum(), "player count preserved");
                events.clear();
                Files.createDirectory(saves.resolve("write-failed.s22"));
                CareerBridge.onEdt(() -> { save.invoke(null, "write-failed"); return null; });
                observation = only(events, "save");
                check(!observation.completed() && !observation.backup() && observation.returnedSuccess(), "swallowed primary failure not marked completed");
                events.clear();
                Files.createDirectory(saves.resolve("backup-failed.sbck"));
                Files.writeString(saves.resolve("backup-failed.sbck/keep"), "nonempty fixture directory");
                CareerBridge.onEdt(() -> { save.invoke(null, "backup-failed"); return null; });
                observation = only(events, "save");
                check(observation.completed() && !observation.backup(), "backup failure distinct from primary success");
                events.clear();
                Files.write(saves.resolve("fallback.s22"), new byte[] {0});
                Files.copy(saves.resolve("output.s22"), saves.resolve("fallback.sbck"));
                boolean outerResult = CareerBridge.onEdt(() -> (boolean) load.invoke(null, "fallback", false));
                observation = only(events, "load");
                check(!outerResult && observation.completed() && observation.backup() && observation.fallback() && !observation.returnedSuccess(), "real recursive fallback deduplicated despite false outer return");
                events.clear();
                CareerBridge.onEdt(() -> (boolean) load.invoke(null, null, false));
                check(!only(events, "load").completed(), "null argument is not decoded save");
                check(Profile.digest(original).equals(before) && Profile.digest(game.resolve("bf22-23.exe")).equals(executableBefore), "fixture input/exe unchanged");
                Files.writeString(home.resolve("runtime-observations.txt"), "Checks=" + checks + "\nCoverage=Original I/O methods; synthetic frame bypasses full UI lifecycle; no gameplay/reopening EXE test\nInput.sha256=" + before + "\nProduced.sha256=" + Profile.digest(saves.resolve("output.s22")) + "\n" + transcript.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")) + "\n");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; }); }
        System.out.println("PASS: " + checks + " original career I/O probe checks; no gameplay gate approved.");
    }
}
