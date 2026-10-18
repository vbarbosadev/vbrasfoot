package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;

import java.awt.Window;
import java.nio.file.*;
import java.util.*;
import javax.swing.JFrame;

/** Real original I/O methods on newly named fixture saves, not a gameplay/UI test. */
public final class CareerHookRuntimeTest {
    private static final class OutputStreamFixture extends java.io.OutputStream {
        final boolean failWrite, failClose;
        int closes, writes;
        OutputStreamFixture(boolean failWrite, boolean failClose) { this.failWrite = failWrite; this.failClose = failClose; }
        public void write(int value) throws java.io.IOException { writes++; if (failWrite) throw new java.io.IOException("owned write failure"); }
        public void close() throws java.io.IOException { closes++; if (failClose) throw new java.io.IOException("owned close failure"); }
    }
    private static void outputCloseChecks(Path game, Path home) throws Exception {
        CareerLifecycle state = new CareerLifecycle();
        List<HookProbe.Observation> events = new ArrayList<>();
        try (var guard = HookProbe.attachLifecycle(state); var sink = HookProbe.attach(events::add);
             LegacyRuntime plain = LegacyRuntime.prepare(Profile.verify(game), home.resolve("output-plain"));
             LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("output-probe"), true)) {
            Class<?> type = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime);
            check(schema(type).equals(schema(Class.forName("com.esotericsoftware.kryo.io.Output", false, plain))), "original Output schema unchanged");
            var constructor = type.getConstructor(java.io.OutputStream.class); var close = type.getMethod("close"); var write = type.getMethod("writeInt", int.class);
            // Synthetic accepted ticket only for these owned stream tests, not a complete game/UI load.
            HookProbe.enterLoad("owned-stream-ticket", false); HookProbe.rootAssigned(); HookProbe.decodedLoad(); HookProbe.exitLoad(true); events.clear();
            OutputStreamFixture good = new OutputStreamFixture(false, false); Object output = constructor.newInstance(good); write.invoke(output, 42);
            HookProbe.enterSave("owned-good"); HookProbe.metadataClosed(); HookProbe.primaryOutputClosing(output); close.invoke(output);
            HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(good.closes == 1 && good.writes == 4 && state.snapshot().lastSaveOutcome().streamClosed()
                    && state.snapshot().lastSaveOutcome().completeSequence(), "original Output successful underlying close observed once");
            check(events.size() == 1 && events.get(0).completed() && state.snapshot().saves() == 1, "normal original Output path keeps observed write");
            OutputStreamFixture bad = new OutputStreamFixture(false, true); Object failing = constructor.newInstance(bad); write.invoke(failing, 42);
            HookProbe.enterSave("owned-close-failed"); HookProbe.metadataClosed(); HookProbe.primaryOutputClosing(failing); close.invoke(failing);
            HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(bad.closes == 1 && bad.writes == 4 && events.size() == 2 && events.get(1).returnedSuccess() && !events.get(1).completed()
                    && !events.get(1).aborted(), "original Output still swallows close IOException but probe refuses success");
            check(state.snapshot().lastSaveOutcome().streamCloseFailed() && !state.snapshot().lastSaveOutcome().streamClosed()
                    && !state.snapshot().lastSaveOutcome().bound() && state.snapshot().saves() == 1
                    && state.snapshot().completeSaveSequences() == 1 && state.snapshot().streamCloseFailures() == 1, "hidden original close failure cannot count bound or complete save");
            OutputStreamFixture noWrite = new OutputStreamFixture(true, false); Object flushing = constructor.newInstance(noWrite); write.invoke(flushing, 42);
            HookProbe.enterSave("owned-flush-failed"); HookProbe.primaryOutputClosing(flushing);
            boolean propagated = false;
            try { close.invoke(flushing); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                propagated = expected.getCause().getClass().getName().equals("com.esotericsoftware.kryo.KryoException");
                HookProbe.abortSave(expected.getCause());
            }
            check(propagated && noWrite.closes == 0 && events.get(2).aborted() && state.snapshot().saves() == 1,
                    "original flush exception remains propagated, no added close/retry");
            OutputStreamFixture recovery = new OutputStreamFixture(false, false); Object recovered = constructor.newInstance(recovery);
            HookProbe.enterSave("owned-recovery"); HookProbe.metadataClosed(); HookProbe.primaryOutputClosing(recovered); close.invoke(failing); close.invoke(recovered);
            HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(state.snapshot().lastSaveOutcome().completeSequence() && state.snapshot().saves() == 2
                    && state.snapshot().streamCloseFailures() == 1 && state.snapshot().inFlightSaves() == 0,
                    "unrelated Output failure ignored and later matched success recovers");
            var report = new Properties();
            report.setProperty("coverage", "Original library Output.close with owned injected streams and synthetic ticket/metadata/backup markers; not full original save under disk failure or fsync");
            report.setProperty("saves", Long.toString(state.snapshot().saves()));
            report.setProperty("completeSequences", Long.toString(state.snapshot().completeSaveSequences()));
            report.setProperty("streamCloseFailures", Long.toString(state.snapshot().streamCloseFailures()));
            report.setProperty("inFlightSaves", Integer.toString(state.snapshot().inFlightSaves()));
            report.setProperty("originalCloseFailureSwallowed", "true");
            report.setProperty("probeWriteSuccessRefused", "true");
            try (var writer = Files.newBufferedWriter(home.resolve("output-close-observations.properties"))) { report.store(writer, "Private original library close fixture"); }
        } finally { state.close(); }
    }
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
        var hub = new CareerObservationHub();
        var ioHub = new CareerIoEventHub();
        CareerLifecycle lifecycle = new CareerLifecycle(hub, ioHub);
        List<String> modObservations = Collections.synchronizedList(new ArrayList<>());
        var modLogger = java.util.logging.Logger.getLogger("real-io-observer-fixture"); modLogger.setUseParentHandlers(false);
        var modHandler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) {
                synchronized (modObservations) { modObservations.add(record.getMessage()); modObservations.notifyAll(); }
            }
            public void flush() { }
            public void close() { }
        };
        modLogger.addHandler(modHandler);
        try (hub; ioHub; var observerMod = new ModLoader(modLogger, hub, ioHub);
             var dispatcher = new ProbeDispatcher(128, java.time.Duration.ofMillis(500), event -> {
                 synchronized (events) { events.add(event); transcript.add(event); callbackThreads.add(Thread.currentThread().getName()); events.notifyAll(); }
             });
             var attached = HookProbe.attach(dispatcher::offer);
             var guarded = HookProbe.attachLifecycle(lifecycle);
             LegacyRuntime plain = LegacyRuntime.prepare(Profile.verify(game), home.resolve("plain"));
             LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("probe"), true)) {
             observerMod.load(List.of(ModManifest.read(Path.of(args[2]))), null, null, home.resolve("mod-data"));
            Class<?> state = Class.forName("c.a", false, runtime);
             check(schema(Class.forName("c.a", false, plain)).equals(schema(state)), "c.a schema unchanged");
             check(schema(Class.forName("best.f", false, plain)).equals(schema(Class.forName("best.f", false, runtime))), "serialized root best.f schema unchanged by body-only trace");
            for (String candidate : List.of("a.bV", "a.cR", "a.cS", "a.hk", "a.ho")) {
                check(schema(Class.forName(candidate, false, plain)).equals(schema(Class.forName(candidate, false, runtime))), candidate + " candidate schema unchanged, not initialized");
            }
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
                check(lifecycle.snapshot().initializedReadPaths() == 0, "synthetic frame skips initialization path, not mistaken for complete UI load");
                check(ioHub.state().existingLoads() == 0 && ioHub.state().phase() == br.vbrasfoot.api.CareerIoEvents.Phase.UNKNOWN, "public IO stream rejects synthetic-frame read as qualified load");
                GameAccess.Snapshot baseline = new CareerBridge(runtime).snapshot();
                check(baseline.loaded(), "decoded career readable");
                CareerBridge bridge = new CareerBridge(runtime);
                var scoped = bridge.scopedClubs("", 0, 1);
                var calendar = bridge.scopedCalendar();
                check(calendar.scope().equals(scoped.scope()) && calendar.date().year() > 0 && calendar.date().month() >= 1 && calendar.date().month() <= 12, "original calendar scoped and valid");
                Object primary = state.getField("SR").get(null);
                check(calendar.dayIndex() == ((Number) CareerBridge.call(primary, "J")).intValue(), "calendar index matches original getter");
                var calendarClone = (java.util.Calendar) ((java.util.Calendar) CareerBridge.call(primary, "bb")).clone();
                check(calendar.date().equals(CalendarSnapshots.copy(calendarClone)), "calendar defensive view matches original clone");
                var balance = bridge.scopedClubBalance(scoped.items().get(0).reference());
                Object firstClub = ((java.util.List<?>) CareerBridge.call(primary, "P")).get(0);
                check(balance.scope().equals(scoped.scope()) && balance.balance() == ((Number) CareerBridge.call(firstClub, "kb")).longValue(), "club balance matches original long getter");
                var originalReference = scoped.items().get(0).reference();
                check(bridge.scopedPlayers(originalReference, 0, 500).items().equals(bridge.playersAt(0, 0, 500).items()), "scoped player values agree with current legacy query");
                var details = bridge.scopedPlayerDetails(originalReference, 0, 500);
                check(details.items().stream().map(GameAccess.PlayerDetails::player).toList().equals(bridge.scopedPlayers(originalReference, 0, 500).items()), "detailed and old player DTOs agree");
                boolean skillsEnabled = (Boolean) CareerBridge.call(primary, "isHabilidadeIndividual");
                var originalPlayers = (java.util.List<?>) CareerBridge.call(firstClub, "kc");
                for (int i = 0; i < details.items().size(); i++) {
                    var skills = details.items().get(i).skills(); Object player = originalPlayers.get(i);
                    check(skills.isPresent() == skillsEnabled, "skills availability follows original career setting");
                    if (skillsEnabled) check(skills.orElseThrow().equals(new GameAccess.IndividualSkills(
                            (int) CareerBridge.call(player, "gK"), (int) CareerBridge.call(player, "gN"),
                            (int) CareerBridge.call(player, "gM"), (int) CareerBridge.call(player, "gO"),
                            (int) CareerBridge.call(player, "gP"), (int) CareerBridge.call(player, "gJ"),
                            (int) CareerBridge.call(player, "gL"))), "all skills equal original getter values");
                }
                check(bridge.scopedPlayerDetails(originalReference, details.total() + 1, 1).items().isEmpty(), "detail offset past end empty");
                boolean invalidDetailPage = false;
                try { bridge.scopedPlayerDetails(originalReference, 0, 501); } catch (IllegalArgumentException expected) { invalidDetailPage = true; }
                check(invalidDetailPage, "detail page bound enforced");
                check(scoped.scope().equals(bridge.scopedClubs("", 0, 1).scope()), "same roots keep query scope");
                events.clear();
                CareerBridge.onEdt(() -> { save.invoke(null, "output"); return null; });
                observation = only(events, "save");
                check(observation.completed() && observation.backup(), "primary close and legacy backup completed");
                check(lifecycle.snapshot().saves() == 1 && lifecycle.snapshot().lastSavedFile().equals("output"), "original save binds current guard");
                check(lifecycle.snapshot().completeSaveSequences() == 1 && lifecycle.snapshot().lastSaveOutcome().metadataClosed()
                        && lifecycle.snapshot().lastSaveOutcome().completeSequence(), "original metadata/primary/backup return observed together");
                check(lifecycle.snapshot().lastSaveOutcome().streamClosed() && !lifecycle.snapshot().lastSaveOutcome().streamCloseFailed(), "original save underlying stream close observed");
                check(ioHub.state().completedSaves() == 0, "all original save stages insufficient without qualified load generation");
                check(Files.isRegularFile(saves.resolve("output.s22")) && Files.mismatch(saves.resolve("output.s22"), saves.resolve("output.sbck")) == -1, "save and backup bytes agree");
                events.clear();
                var beforeReload = bridge.scopedClubs("", 0, 1).items().get(0).reference();
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "output", false)), "produced save reloads by original method");
                check(only(events, "load").completed(), "reload observation");
                check(lifecycle.snapshot().loads() == 2 && lifecycle.snapshot().lastSavedFile() == null, "original reload replaces guard incarnation");
                boolean rejectedReference = false;
                try { bridge.scopedPlayers(beforeReload, 0, 20); }
                catch (br.vbrasfoot.api.GameQueryException expected) { rejectedReference = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE; }
                boolean balanceRejected = false;
                try { bridge.scopedClubBalance(beforeReload); } catch (br.vbrasfoot.api.GameQueryException expected) { balanceRejected = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE; }
                check(balanceRejected, "old balance handle refused after original reload");
                boolean detailsRejected = false;
                try { bridge.scopedPlayerDetails(beforeReload, 0, 20); } catch (br.vbrasfoot.api.GameQueryException expected) { detailsRejected = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE; }
                check(detailsRejected, "old detail handle refused after original reload");
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
                check(lifecycle.snapshot().lastSaveOutcome().metadataClosed() && !lifecycle.snapshot().lastSaveOutcome().primaryClosed()
                        && lifecycle.snapshot().failedSaveSequences() == 1, "successful metadata with failed primary is not complete save");
                events.clear();
                Files.createDirectory(saves.resolve("backup-failed.sbck"));
                Files.writeString(saves.resolve("backup-failed.sbck/keep"), "nonempty fixture directory");
                CareerBridge.onEdt(() -> { save.invoke(null, "backup-failed"); return null; });
                observation = only(events, "save");
                check(observation.completed() && !observation.backup(), "backup failure distinct from primary success");
                check(lifecycle.snapshot().saves() == 2, "guard tracks primary close despite backup failure, not durable commit");
                check(!lifecycle.snapshot().lastSaveOutcome().completeSequence() && lifecycle.snapshot().partialSaveSequences() == 1, "backup failure cannot complete full save sequence");
                events.clear();
                Files.createDirectory(saves.resolve("metadata-failed.info"));
                CareerBridge.onEdt(() -> { save.invoke(null, "metadata-failed"); return null; });
                observation = only(events, "save");
                check(observation.completed() && observation.backup() && !lifecycle.snapshot().lastSaveOutcome().metadataClosed(), "original swallowed metadata failure distinguished despite primary and backup success");
                check(lifecycle.snapshot().saves() == 3 && lifecycle.snapshot().completeSaveSequences() == 1
                        && lifecycle.snapshot().partialSaveSequences() == 2 && lifecycle.snapshot().inFlightSaves() == 0, "only fully observed sequence counted complete, attempts cleaned");
                events.clear();
                CareerBridge.onEdt(() -> { save.invoke(null, (Object) null); return null; });
                observation = only(events, "save");
                check(!observation.completed() && !lifecycle.snapshot().lastSaveOutcome().metadataClosed()
                        && lifecycle.snapshot().saves() == 3 && lifecycle.snapshot().failedSaveSequences() == 2, "null save return contains no write markers or completed sequence");
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
                boolean calendarRejected = false;
                try { bridge.scopedCalendar(); } catch (br.vbrasfoot.api.GameQueryException expected) { calendarRejected = expected.reason() == br.vbrasfoot.api.GameQueryException.Reason.NO_CAREER; }
                check(calendarRejected, "empty original reset has no calendar snapshot");
                events.clear();
                Object resetRoot = state.getField("SR").get(null);
                CareerBridge.onEdt(() -> { state.getMethod("a", Class.forName("best.f", false, runtime)).invoke(null, resetRoot); return null; });
                check(only(events, "root-setter").completed(), "original root setter instrumented");
                check(lifecycle.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && lifecycle.snapshot().inFlight() == 0
                        && lifecycle.snapshot().healthy(), "setter conservatively invalidates without leaking attempt");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                synchronized (modObservations) {
                    while (modObservations.stream().filter(s -> s.contains("career-observer kind=INVALIDATED")).count() < lifecycle.snapshot().invalidations()) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) throw new AssertionError("SDK observer mod deadline");
                        java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(modObservations, remaining);
                    }
                    check(modObservations.stream().filter(s -> s.contains("career-observer kind=READ_COMPLETED")).count() == 3, "SDK-only mod receives three original reads including fallback once");
                    check(modObservations.stream().filter(s -> s.contains("career-observer kind=PRIMARY_WRITE_COMPLETED")).count() == 3, "SDK-only mod receives primary writes, distinct from full save sequence");
                    check(modObservations.stream().noneMatch(s -> s.contains("career-io kind=EXISTING_LOAD_COMPLETED") || s.contains("career-io kind=SAVE_SEQUENCE_COMPLETED")), "SDK-only new API consumer receives no falsely qualified events in original I/O fixture");
                }
                 check(hub.state().reads() == 3 && hub.state().primaryWrites() == 3 && hub.state().phase() == br.vbrasfoot.api.CareerObservations.Phase.UNKNOWN,
                        "public state matches original hook guard without exposing save paths");
                check(callbackThreads.stream().allMatch("vbrasfoot-probe-dispatch"::equals), "actual I/O observations delivered off game thread");
                check(dispatcher.stats().dropped() == 0 && dispatcher.stats().failed() == 0, "actual I/O dispatcher without losses or callback failures");
                check(Profile.digest(original).equals(before) && Profile.digest(game.resolve("bf22-23.exe")).equals(executableBefore), "fixture input/exe unchanged");
                Files.writeString(home.resolve("runtime-observations.txt"), "Checks=" + checks + "\nCoverage=Original I/O methods; synthetic frame bypasses full UI lifecycle; no gameplay/reopening EXE test\nInput.sha256=" + before + "\nProduced.sha256=" + Profile.digest(saves.resolve("output.s22")) + "\nGuard=" + lifecycle.snapshot() + "\n" + transcript.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("\n")) + "\n");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { lifecycle.close(); modLogger.removeHandler(modHandler); CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; }); }
        outputCloseChecks(game, home);
        System.out.println("PASS: " + checks + " original career I/O probe checks; no gameplay gate approved.");
    }
}
