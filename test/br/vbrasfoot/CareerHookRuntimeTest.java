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
    private static HookProbe.Observation only(List<HookProbe.Observation> events, String operation) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        synchronized (events) {
            while (events.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new AssertionError("asynchronous observation timeout");
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(events, remaining);
            }
            check(events.size() == 1 && events.get(0).operation().equals(operation), "exactly one " + operation);
            return events.get(0);
        }
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
        List<HookProbe.Observation> events = Collections.synchronizedList(new ArrayList<>());
        List<HookProbe.Observation> transcript = new ArrayList<>();
        List<String> callbackThreads = new ArrayList<>();
        CareerLifecycle lifecycle = new CareerLifecycle();
        try (var dispatcher = new ProbeDispatcher(128, java.time.Duration.ofMillis(500), event -> {
                 synchronized (events) { events.add(event); transcript.add(event); callbackThreads.add(Thread.currentThread().getName()); events.notifyAll(); }
             });
             var attached = HookProbe.attach(dispatcher::offer);
             var guarded = HookProbe.attachLifecycle(lifecycle);
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
                check(lifecycle.snapshot().phase() == CareerLifecycle.Phase.ACTIVE && lifecycle.snapshot().loadedFile().equals("input")
                        && lifecycle.snapshot().loads() == 1 && lifecycle.snapshot().inFlight() == 0, "original load binds synchronous guard");
                GameAccess.Snapshot baseline = new CareerBridge(runtime).snapshot();
                check(baseline.loaded(), "decoded career readable");
                CareerBridge bridge = new CareerBridge(runtime);
                var scoped = bridge.scopedClubs("", 0, 1);
                var originalReference = scoped.items().get(0).reference();
                check(bridge.scopedPlayers(originalReference, 0, 500).items().equals(bridge.playersAt(0, 0, 500).items()), "scoped player values agree with current legacy query");
                check(scoped.scope().equals(bridge.scopedClubs("", 0, 1).scope()), "same roots keep query scope");
                events.clear();
                CareerBridge.onEdt(() -> { save.invoke(null, "output"); return null; });
                observation = only(events, "save");
                check(observation.completed() && observation.backup(), "primary close and legacy backup completed");
                check(lifecycle.snapshot().saves() == 1 && lifecycle.snapshot().lastSavedFile().equals("output"), "original save binds current guard");
                check(Files.isRegularFile(saves.resolve("output.s22")) && Files.mismatch(saves.resolve("output.s22"), saves.resolve("output.sbck")) == -1, "save and backup bytes agree");
                events.clear();
                var beforeReload = bridge.scopedClubs("", 0, 1).items().get(0).reference();
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "output", false)), "produced save reloads by original method");
                check(only(events, "load").completed(), "reload observation");
                check(lifecycle.snapshot().loads() == 2 && lifecycle.snapshot().lastSavedFile() == null, "original reload replaces guard incarnation");
                boolean rejectedReference = false;
                try { bridge.scopedPlayers(beforeReload, 0, 20); }
                catch (br.vbrasfoot.api.GameQueryException expected) { rejectedReference = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE; }
                check(rejectedReference, "reference from previous decoded graph refused");
                // Original save preconditions assign IDs. Compare values while allowing those original ID changes.
                GameAccess.Snapshot restored = new CareerBridge(runtime).snapshot();
                check(baseline.clubs().size() == restored.clubs().size(), "club count preserved");
                check(baseline.clubs().stream().mapToInt(c -> c.players().size()).sum() == restored.clubs().stream().mapToInt(c -> c.players().size()).sum(), "player count preserved");
                events.clear();
                Files.createDirectory(saves.resolve("write-failed.s22"));
                CareerBridge.onEdt(() -> { save.invoke(null, "write-failed"); return null; });
                observation = only(events, "save");
                check(!observation.completed() && !observation.backup() && observation.returnedSuccess(), "swallowed primary failure not marked completed");
                check(lifecycle.snapshot().saves() == 1, "failed primary does not increment guard saves");
                events.clear();
                Files.createDirectory(saves.resolve("backup-failed.sbck"));
                Files.writeString(saves.resolve("backup-failed.sbck/keep"), "nonempty fixture directory");
                CareerBridge.onEdt(() -> { save.invoke(null, "backup-failed"); return null; });
                observation = only(events, "save");
                check(observation.completed() && !observation.backup(), "backup failure distinct from primary success");
                check(lifecycle.snapshot().saves() == 2, "guard tracks primary close despite backup failure, not durable commit");
                events.clear();
                Files.write(saves.resolve("fallback.s22"), new byte[] {0});
                Files.copy(saves.resolve("output.s22"), saves.resolve("fallback.sbck"));
                boolean outerResult = CareerBridge.onEdt(() -> (boolean) load.invoke(null, "fallback", false));
                observation = only(events, "load");
                check(!outerResult && observation.completed() && observation.backup() && observation.fallback() && !observation.returnedSuccess(), "real recursive fallback deduplicated despite false outer return");
                check(lifecycle.snapshot().loads() == 3 && lifecycle.snapshot().loadedBackup() && lifecycle.snapshot().inFlight() == 0, "original fallback bound once");
                events.clear();
                CareerBridge.onEdt(() -> (boolean) load.invoke(null, null, false));
                check(!only(events, "load").completed(), "null argument is not decoded save");
                check(lifecycle.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && lifecycle.snapshot().inFlight() == 0, "failed attempt conservatively invalidates guard");
                events.clear();
                var beforeReset = bridge.scopedClubs("", 0, 1).items().get(0).reference();
                Object previousRoot = state.getField("SR").get(null);
                CareerBridge.onEdt(() -> { state.getConstructor().newInstance(); return null; });
                check(only(events, "roots-reset").completed(), "actual root reallocation completed");
                check(lifecycle.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && lifecycle.snapshot().inFlight() == 0, "original constructor not mistaken for career load/unload");
                check(previousRoot != state.getField("SR").get(null) && !new CareerBridge(runtime).snapshot().loaded(), "new roots replace old career but do not imply loaded career");
                rejectedReference = false;
                try { bridge.scopedPlayers(beforeReset, 0, 20); }
                catch (br.vbrasfoot.api.GameQueryException expected) { rejectedReference = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE; }
                check(rejectedReference && !bridge.scopedClubs("", 0, 1).loaded(), "root reset invalidates prior query references");
                events.clear();
                Object resetRoot = state.getField("SR").get(null);
                CareerBridge.onEdt(() -> { state.getMethod("a", Class.forName("best.f", false, runtime)).invoke(null, resetRoot); return null; });
                check(only(events, "root-setter").completed(), "original root setter instrumented");
                check(lifecycle.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && lifecycle.snapshot().inFlight() == 0
                        && lifecycle.snapshot().healthy(), "setter conservatively invalidates without leaking attempt");
                check(callbackThreads.stream().allMatch("vbrasfoot-probe-dispatch"::equals), "actual I/O observations delivered off game thread");
                check(dispatcher.stats().dropped() == 0 && dispatcher.stats().failed() == 0, "actual I/O dispatcher without losses or callback failures");
                check(Profile.digest(original).equals(before) && Profile.digest(game.resolve("bf22-23.exe")).equals(executableBefore), "fixture input/exe unchanged");
                Files.writeString(home.resolve("runtime-observations.txt"), "Checks=" + checks + "\nCoverage=Original I/O methods; synthetic frame bypasses full UI lifecycle; no gameplay/reopening EXE test\nInput.sha256=" + before + "\nProduced.sha256=" + Profile.digest(saves.resolve("output.s22")) + "\n" + transcript.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")) + "\n");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; }); }
        System.out.println("PASS: " + checks + " original career I/O probe checks; no gameplay gate approved.");
    }
}
