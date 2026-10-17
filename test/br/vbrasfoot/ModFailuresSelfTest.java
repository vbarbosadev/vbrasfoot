package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Failure summaries through actual loader boundaries, without game classes or disk writes. */
public final class ModFailuresSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void until(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("failure fixture deadline");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        }
    }
    public static final class Fixture implements Mod {
        static String mode = "";
        static CountDownLatch entered, release;
        public void initialize(ModContext context) {
            if (mode.equals("init")) throw new IllegalArgumentException("fixture-init");
            if (mode.equals("action")) context.registerAction("bad", "Fixture failure", () -> { throw new AssertionError("fixture-action"); });
            if (mode.equals("callback")) context.careerObservations().subscribe(update -> { throw new IllegalStateException("fixture-callback"); });
            if (mode.equals("overflow")) context.careerObservations().subscribe(update -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
        }
        public void shutdown() { if (mode.equals("shutdown")) throw new IllegalStateException("fixture-shutdown"); }
    }
    public static final class ConstructorFailure implements Mod {
        public ConstructorFailure() { throw new IllegalArgumentException("fixture-constructor"); }
        public void initialize(ModContext context) { }
    }
    private static ModManifest manifest(Class<?> type) {
        return new ModManifest(Path.of("build/failures-fixture.jar"), "failures", "Fixture", "1", type.getName(), List.of(), Set.of("ui.register", "events.career.read"));
    }
    public static void main(String[] args) throws Exception {
        ModFailures bounded = new ModFailures(2);
        bounded.record("file.jar", "id", ModFailures.Stage.UI_ACTION, new IllegalArgumentException("first"));
        var oldSnapshot = bounded.snapshot();
        bounded.record("file.jar", "id", ModFailures.Stage.UI_ACTION, new IllegalArgumentException("second"));
        bounded.record("file.jar", "id", ModFailures.Stage.UI_ACTION, new IllegalArgumentException("third"));
        check(bounded.snapshot().entries().size() == 2 && bounded.snapshot().evicted() == 1 && bounded.snapshot().entries().get(0).sequence() == 2, "bounded eviction/sequence");
        check(oldSnapshot.entries().size() == 1 && oldSnapshot.entries().get(0).message().equals("first"), "immutable detached snapshot");
        boolean immutable = false; try { oldSnapshot.entries().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "snapshot list immutable");
        bounded.record("f".repeat(200), "i".repeat(200), ModFailures.Stage.INITIALIZE, new IllegalArgumentException("secret\n" + "x".repeat(2000)));
        var last = bounded.snapshot().entries().get(1);
        check(last.file().length() <= 163 && last.id().length() <= 163 && last.message().length() <= 515 && !last.message().contains("\n"), "bounded flattened strings");
        bounded.record("file.jar", "id", ModFailures.Stage.INITIALIZE, new IllegalStateException() {
            @Override public String getMessage() { throw new AssertionError("message accessor failed"); }
        });
        check(bounded.snapshot().entries().get(1).message().equals("Mensagem indisponivel"), "diagnostics tolerate broken exception message");
        check(Arrays.stream(ModFailures.Entry.class.getRecordComponents()).noneMatch(c -> Throwable.class.isAssignableFrom(c.getType()) || c.getType().equals(Class.class)), "record retains no Throwable/Class");
        ModFailures concurrent = new ModFailures();
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> { for (int j = 0; j < 100; j++) concurrent.record("f.jar", "id", ModFailures.Stage.UI_ACTION, "Fixture", "error"); }));
            for (var job : jobs) job.get(3, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var history = concurrent.snapshot();
        check(history.entries().size() == 128 && history.evicted() == 272 && history.entries().get(127).sequence() == 400, "concurrent append bounded and ordered");
        check(ModManagerPanel.failureText(history).contains("272"), "history panel exposes evicted count");
        Logger logger = Logger.getLogger("mod-failures-test"); logger.setUseParentHandlers(false);
        Fixture.mode = "init";
        try (var loader = new ModLoader(logger)) {
            boolean rejected = false; try { loader.load(List.of(manifest(Fixture.class)), null, null, Path.of("build")); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected && loader.failures().entries().get(0).stage() == ModFailures.Stage.INITIALIZE, "init failure captured while remaining fail-closed");
            check(loader.fileStates().get("failures-fixture.jar") == ModState.FAILED, "init failure state retained");
        }
        Fixture.mode = "";
        try (var loader = new ModLoader(logger)) {
            try { loader.load(List.of(manifest(ConstructorFailure.class)), null, null, Path.of("build")); } catch (java.lang.reflect.InvocationTargetException expected) { }
            check(loader.failures().entries().get(0).type().equals("java.lang.IllegalArgumentException") && loader.failures().entries().get(0).message().equals("fixture-constructor"), "constructor wrapper unwrapped without retention");
        }
        Fixture.mode = "action";
        try (var ui = new ModUi(new ClassLoader() { }, logger); var loader = new ModLoader(logger)) {
            loader.load(List.of(manifest(Fixture.class)), null, ui, Path.of("build"));
            try { ui.invoke("failures:bad"); } catch (AssertionError expected) { }
            var failure = loader.failures().entries().get(0);
            check(failure.stage() == ModFailures.Stage.UI_ACTION && failure.file().equals("failures-fixture.jar") && failure.id().equals("failures"), "UI action attributed to owning mod/file");
            check(loader.fileStates().get("failures-fixture.jar") == ModState.ACTIVE, "action failure not silently disabling entire mod");
            CareerBridge.onEdt(() -> {
                var entry = new ModSelection.Entry("failures-fixture.jar", manifest(Fixture.class), true, "");
                var panel = ModManagerPanel.content(List.of(entry), loader.fileStates(), loader.failures());
                var table = (javax.swing.JTable) ((javax.swing.JScrollPane) panel.getComponent(0)).getViewport().getView();
                check(table.getValueAt(0, 7).equals(1) && table.getValueAt(0, 8).toString().contains("UI_ACTION"), "management table contains recent summary");
                check(!table.isCellEditable(0, 8), "failure cells read-only");
                return null;
            });
        }
        Fixture.mode = "callback";
        try (var hub = new CareerObservationHub(); var loader = new ModLoader(logger, hub)) {
            new CareerLifecycle(hub);
            loader.load(List.of(manifest(Fixture.class)), null, null, Path.of("build"));
            until(() -> !loader.failures().entries().isEmpty());
            check(loader.failures().entries().get(0).stage() == ModFailures.Stage.CAREER_CALLBACK, "subscriber callback error captured before hub isolates it");
            check(loader.failures().entries().size() == 1, "polling does not duplicate callback failure");
        }
        Fixture.mode = "overflow"; Fixture.entered = new CountDownLatch(1); Fixture.release = new CountDownLatch(1);
        try (var hub = new CareerObservationHub(1, 4, Duration.ofMillis(100)); var loader = new ModLoader(logger, hub)) {
            var lifecycle = new CareerLifecycle(hub);
            loader.load(List.of(manifest(Fixture.class)), null, null, Path.of("build"));
            check(Fixture.entered.await(3, TimeUnit.SECONDS), "owned callback started");
            lifecycle.rootsChanged(); lifecycle.rootsChanged();
            check(loader.failures().entries().get(0).stage() == ModFailures.Stage.CAREER_OVERFLOW, "panel polling discovers overflow without mod cooperation");
            check(loader.failures().entries().size() == 1, "overflow recorded once");
        } finally { Fixture.release.countDown(); }
        Fixture.mode = "shutdown";
        try (var loader = new ModLoader(logger)) {
            loader.load(List.of(manifest(Fixture.class)), null, null, Path.of("build")); loader.close();
            check(loader.failures().entries().get(0).stage() == ModFailures.Stage.SHUTDOWN && loader.fileStates().get("failures-fixture.jar") == ModState.FAILED, "shutdown failures remain available after disposal");
            loader.close(); check(loader.failures().entries().size() == 1, "idempotent disposal does not duplicate failure");
        } finally { Fixture.mode = ""; }
        check(new ModFailures().snapshot().entries().isEmpty(), "new session history starts empty");
        System.out.println("PASS: " + checks + " bounded mod failure history checks (synthetic; no game).");
    }
}
