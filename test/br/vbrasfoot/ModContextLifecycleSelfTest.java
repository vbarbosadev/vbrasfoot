package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Actual loader boundaries with synthetic mods; no original game/runtime/save. */
public final class ModContextLifecycleSelfTest {
    private static int checks, calls, shutdowns;
    private static boolean failInitialize, failShutdown;
    private static ModContext retained;
    private static GameAccess retainedGame;
    private static CareerObservations retainedObservations;
    private static AutoCloseable registration;
    private static final List<String> stopped = new ArrayList<>();
    public static final class First implements Mod {
        public void initialize(ModContext context) { context.registerAction("shared", "First", () -> calls++); }
        public void shutdown() { stopped.add("first"); }
    }
    public static final class Second implements Mod {
        public void initialize(ModContext context) { context.registerAction("shared", "Second", () -> calls++); }
        public void shutdown() { stopped.add("second"); throw new IllegalArgumentException("second-shutdown"); }
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejected(Runnable operation, String label) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, label);
    }
    public static final class Fixture implements Mod {
        public void initialize(ModContext context) {
            retained = context; retainedGame = context.game(); retainedObservations = context.careerObservations();
            context.registerAction("legacy", "Legacy", () -> calls++);
            registration = context.registerScopedAction("scoped", "Scoped", () -> calls++);
            if (failInitialize) throw new IllegalArgumentException("owned-init-failure");
        }
        public void shutdown() throws Exception {
            shutdowns++;
            if (!failInitialize) {
                retained.writeData("shutdown.txt", new byte[] {42});
                check(retained.readData("shutdown.txt")[0] == 42, "data remains usable during normal shutdown");
            }
            rejected(() -> retained.registerAction("late", "Late", () -> calls++), "no registration during shutdown");
            if (failShutdown) throw new IllegalArgumentException("owned-shutdown-failure");
        }
    }
    private static ModManifest manifest() {
        return new ModManifest(Path.of("build/context-fixture.jar"), "context", "Fixture", "1", Fixture.class.getName(), List.of(),
                Set.of("ui.register", "game.read", "events.career.read", "data.read", "data.write"));
    }
    private static void revoked() throws Exception {
        rejected(() -> retained.game(), "context game revoked");
        rejected(() -> retainedGame.snapshot(), "previously obtained game facade revoked");
        rejected(() -> retainedGame.scopedCalendar(), "calendar facade revoked after cleanup");
        rejected(() -> retainedGame.scopedClubBalance(null), "balance facade revoked before delegating");
        rejected(() -> retainedObservations.state(), "previously obtained observation facade revoked");
        rejected(() -> retainedObservations.subscribe(update -> { }), "retained observation subscribe revoked");
        rejected(() -> retained.registerAction("later", "Later", () -> calls++), "late action rejected");
        rejected(() -> {
            try { retained.writeData("later.txt", new byte[0]); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }, "late data write rejected");
        rejected(() -> {
            try { retained.readData("shutdown.txt"); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }, "late data read rejected");
        registration.close(); registration.close();
    }
    public static void main(String[] args) throws Exception {
        Logger logger = Logger.getLogger("context-lifecycle-test"); logger.setUseParentHandlers(false);
        Path data = Files.createTempDirectory(Path.of("build"), "context-lifecycle-");
        GameAccess game = () -> new GameAccess.Snapshot(false, List.of());
        try (var hub = new CareerObservationHub(); var ui = new ModUi(new ClassLoader() { }, logger)) {
            // Use no EDT flush before invoke: dispatch must be revoked synchronously.
            AutoCloseable old = ui.registerOwned("reuse", "Old", () -> calls++);
            old.close(); ui.invoke("reuse"); check(calls == 0, "close revokes before EDT cleanup");
            AutoCloseable replacement = ui.registerOwned("reuse", "New", () -> calls++);
            old.close(); ui.invoke("reuse"); check(calls == 1, "old handle cannot remove replacement");
            replacement.close();
            Runnable identicalCallback = () -> calls++;
            AutoCloseable identicalOld = ui.registerOwned("identical", "Same", identicalCallback);
            identicalOld.close();
            AutoCloseable identicalNew = ui.registerOwned("identical", "Same", identicalCallback);
            identicalOld.close(); ui.invoke("identical");
            check(calls == 2, "identity ownership even with identical label and callback");
            identicalNew.close(); calls = 1;
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            javax.swing.SwingUtilities.invokeLater(() -> {
                entered.countDown();
                try { release.await(3, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                check(entered.await(3, java.util.concurrent.TimeUnit.SECONDS), "EDT parked for nonblocking cleanup fixture");
                AutoCloseable blockedEdt = ui.registerOwned("blocked", "Blocked", identicalCallback);
                executor.submit(() -> { try { blockedEdt.close(); } catch (Exception failure) { throw new AssertionError(failure); } }).get(1, java.util.concurrent.TimeUnit.SECONDS);
                ui.invoke("blocked"); check(calls == 1, "dispatch revoked without waiting for parked EDT");
            } finally { release.countDown(); executor.shutdownNow(); }
            var loader = new ModLoader(logger, hub);
            loader.load(List.of(manifest()), game, ui, data);
            ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "both action APIs active");
            registration.close(); ui.invoke("context:scoped"); check(calls == 3, "scoped close immediate");
            loader.close(); ui.invoke("context:legacy"); check(calls == 3, "legacy action owned and removed on close");
            check(loader.fileStates().get("context-fixture.jar") == ModState.STOPPED, "normal stop state");
            revoked(); loader.close(); check(shutdowns == 1, "idempotent shutdown");
            rejected(() -> {
                try { loader.load(List.of(manifest()), game, ui, data); } catch (IllegalStateException expected) { throw expected; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, "closed loader cannot reload");
            for (boolean initFailure : List.of(true, false)) {
                failInitialize = initFailure; failShutdown = !initFailure;
                var failed = new ModLoader(logger, hub);
                try { failed.load(List.of(manifest()), game, ui, data); }
                catch (IllegalArgumentException expected) { if (!initFailure) throw expected; }
                if (initFailure) {
                    ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "partial initialization actions revoked");
                    revoked();
                }
                failed.close(); ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "failure cleanup actions revoked");
                check(failed.fileStates().get("context-fixture.jar") == ModState.FAILED, "failure state survives cleanup");
                revoked();
            }
            var multiple = new ModLoader(logger);
            multiple.load(List.of(
                    new ModManifest(Path.of("first.jar"), "first", "First", "1", First.class.getName(), List.of(), Set.of("ui.register")),
                    new ModManifest(Path.of("second.jar"), "second", "Second", "1", Second.class.getName(), List.of("first"), Set.of("ui.register"))), game, ui, data);
            ui.invoke("first:shared"); ui.invoke("second:shared"); check(calls == 5, "two owners can use same local action id");
            multiple.close(); ui.invoke("first:shared"); ui.invoke("second:shared");
            check(calls == 5, "two owners cleaned despite one shutdown failure");
            check(stopped.equals(List.of("second", "first")), "reverse shutdown continues after failure");
            check(multiple.fileStates().get("first.jar") == ModState.STOPPED && multiple.fileStates().get("second.jar") == ModState.FAILED, "independent terminal states");
        }
        System.out.println("PASS: " + checks + " mod context/resource lifecycle checks (synthetic; no game).");
    }
}
