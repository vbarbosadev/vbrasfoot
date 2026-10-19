package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Owned control flow and transformed synthetic bodies only; not production career qualification. */
public final class CareerIoInterlockSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void refused(Action action, String label) throws Exception {
        try { action.run(); } catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void invalid(Action action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static class FixtureLoader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass("br.vbrasfoot.HookTransformFixture", bytes, 0, bytes.length); }
    }
    private static Throwable invoke(java.lang.reflect.Method method, String name) throws Exception {
        try { method.invoke(null, name); return null; }
        catch (java.lang.reflect.InvocationTargetException failure) { return failure.getCause(); }
    }
    public static void main(String[] args) throws Exception {
        var save = new CareerSidecarStore.SaveRevision("a".repeat(64), 1);
        UUID career = UUID.randomUUID(), binding = UUID.randomUUID(); Object primary = new Object(), secondary = new Object();
        try (var gate = new CareerCommandGate(); var interlock = new CareerIoInterlock(gate)) {
            var owner = gate.owner("br.owned.interlock", Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var context = gate.installContext(career, binding, 1, save, primary, secondary);
            var request = new CareerCommandGate.Request(context.token(), "reserved", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            var permit = gate.reserve(owner, request, primary, secondary).permit().orElseThrow();
            for (var kind : CareerCommandGate.IoKind.values()) refused(() -> interlock.enter(kind), "reserved command must block original I/O");
            gate.cancel(permit);
            var persistedRequest = new CareerCommandGate.Request(context.token(), "persisting", request.operation(), request.payloadDigest());
            var persisting = gate.reserve(owner, persistedRequest, primary, secondary).permit().orElseThrow();
            gate.retainDispatch(persisting); gate.cancel(persisting);
            refused(() -> interlock.enter(CareerCommandGate.IoKind.SAVE), "post-handler journal persistence still excludes original writer");
            refused(() -> interlock.enter(CareerCommandGate.IoKind.LOAD), "post-handler journal persistence still excludes original decoder");
            gate.releaseDispatch(persisting);
            var load = interlock.enter(CareerCommandGate.IoKind.LOAD);
            check(gate.reserve(owner, request, primary, secondary).receipt().reason() == CareerCommandGate.Reason.ORIGINAL_IO_IN_PROGRESS, "load excludes commands before any root mutation");
            var fallback = interlock.enter(CareerCommandGate.IoKind.LOAD); var reset = interlock.enter(CareerCommandGate.IoKind.RESET);
            check(interlock.snapshot().depth() == 3, "same-thread recursive fallback and root reset share outer fence");
            invalid(() -> interlock.exit(load), "out-of-order exit cannot release exclusion");
            refused(() -> interlock.enter(CareerCommandGate.IoKind.SAVE), "nested save during decode refused");
            var executor = Executors.newSingleThreadExecutor();
            try {
                check(executor.submit(() -> { try { interlock.enter(CareerCommandGate.IoKind.LOAD); return false; } catch (IllegalStateException expected) { return true; } }).get(2, TimeUnit.SECONDS), "competing thread cannot enter while fallback active");
            } finally { executor.shutdownNow(); }
            interlock.exit(reset); interlock.exit(fallback);
            refused(() -> gate.installContext(career, binding, 2, save, primary, secondary), "child return never releases outer fence");
            interlock.exit(load);
            var fresh = new CareerCommandGate.Request(context.token(), "fresh", request.operation(), request.payloadDigest());
            check(gate.reserve(owner, fresh, primary, secondary).receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT, "return from load never restores previous context");
            invalid(() -> interlock.exit(load), "duplicate exit refused");
            var writing = interlock.enter(CareerCommandGate.IoKind.SAVE);
            check(gate.reserve(owner, fresh, primary, secondary).receipt().reason() == CareerCommandGate.Reason.SAVE_IN_PROGRESS, "original writer uses save-specific exclusion");
            for (var kind : CareerCommandGate.IoKind.values()) refused(() -> interlock.enter(kind), "writer cannot reenter mutation");
            interlock.close(); refused(() -> interlock.enter(CareerCommandGate.IoKind.LOAD), "closing owner denies new calls");
            check(interlock.snapshot().depth() == 1, "closing does not release running original writer");
            interlock.exit(writing); check(interlock.snapshot().depth() == 0, "actual writer return releases its own captured fence after close");
        }
        // Attach/detach across an in-flight call cannot silently hand it to a new command gate.
        try (var gate = new CareerCommandGate(); var other = new CareerCommandGate()) {
            var interlock = new CareerIoInterlock(gate); var replacement = new CareerIoInterlock(other);
            var attached = HookProbe.attachIoInterlock(interlock);
            HookProbe.beforeOriginalIo("LOAD"); attached.close();
            refused(() -> HookProbe.attachIoInterlock(replacement), "cannot replace detached owner until original call returns");
            refused(() -> HookProbe.beforeOriginalIo("LOAD"), "closed captured owner still denies new entries");
            HookProbe.afterOriginalIo();
            try (var next = HookProbe.attachIoInterlock(replacement)) {
                HookProbe.beforeOriginalIo("SAVE"); HookProbe.afterOriginalIo();
                check(replacement.snapshot().completed() == 1, "fresh attach possible only after previous work drained");
            }
        }
        HookProbe.beforeOriginalIo("LOAD");
        try (var gate = new CareerCommandGate(); var interlock = new CareerIoInterlock(gate)) {
            refused(() -> HookProbe.attachIoInterlock(interlock), "unowned original call also prevents mid-call attach");
        } finally { HookProbe.afterOriginalIo(); }
        refused(HookProbe::afterOriginalIo, "unmatched exit cannot silently close another window");
        try (var gate = new CareerCommandGate(); var closed = new CareerIoInterlock(gate)) {
            closed.close(); invalid(() -> HookProbe.attachIoInterlock(closed), "closed interlock cannot be attached as if live");
        }
        try (var gate = new CareerCommandGate(); var bounded = new CareerIoInterlock(gate)) {
            var entries = new ArrayDeque<CareerIoInterlock.Entry>();
            for (int i = 0; i < CareerIoInterlock.MAX_DEPTH; i++) entries.push(bounded.enter(CareerCommandGate.IoKind.LOAD));
            refused(() -> bounded.enter(CareerCommandGate.IoKind.LOAD), "recursive admission bounded without evicting outstanding entries");
            while (!entries.isEmpty()) bounded.exit(entries.pop());
            check(bounded.snapshot().depth() == 0 && bounded.snapshot().admitted() == CareerIoInterlock.MAX_DEPTH, "bounded stack unwinds all existing entries after rejected overflow");
        }
        byte[] original;
        try (var input = CareerIoInterlockSelfTest.class.getResourceAsStream("/br/vbrasfoot/HookTransformFixture.class")) { original = input.readAllBytes(); }
        var transform = Class.forName("br.vbrasfoot.hooks.CareerTransformer").getDeclaredMethod("transformFixture", byte[].class); transform.setAccessible(true);
        Class<?> type = new FixtureLoader().define((byte[]) transform.invoke(null, (Object) original));
        var loadMethod = type.getMethod("c", String.class, boolean.class); var saveMethod = type.getMethod("ae", String.class);
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path fixture = Files.createTempDirectory(parent, "original-io-interlock-");
        Path source = Files.writeString(fixture.resolve("source"), "owned bytes"), target = fixture.resolve("target");
        type.getField("source").set(null, source); type.getField("target").set(null, target);
        try (var gate = new CareerCommandGate(); var interlock = new CareerIoInterlock(gate); var attached = HookProbe.attachIoInterlock(interlock)) {
            var owner = gate.owner("br.owned.transformed", Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var context = gate.installContext(career, binding, 1, save, primary, secondary);
            var request = new CareerCommandGate.Request(context.token(), "block", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            var permit = gate.reserve(owner, request, primary, secondary).permit().orElseThrow();
            check(invoke(saveMethod, "ok") instanceof IllegalStateException && !Files.exists(target), "transformed writer refused before file copy or original body");
            boolean loadRefused = false;
            try { loadMethod.invoke(null, "fallback", false); } catch (java.lang.reflect.InvocationTargetException failure) { loadRefused = failure.getCause() instanceof IllegalStateException; }
            check(loadRefused && interlock.snapshot().depth() == 0, "refused outer load neither enters fallback nor creates unmatched cleanup");
            boolean resetRefused = false;
            try { type.getConstructor().newInstance(); } catch (java.lang.reflect.InvocationTargetException failure) { resetRefused = failure.getCause() instanceof IllegalStateException; }
            check(resetRefused, "constructor reset refused after super but before original state changes");
            gate.cancel(permit);
            check(!(boolean) loadMethod.invoke(null, "fallback", false) && interlock.snapshot().depth() == 0, "transformed recursive fallback preserves false outer return and drains both entries");
            check(invoke(saveMethod, "abort") instanceof IllegalStateException && interlock.snapshot().depth() == 0, "original Throwable preserved and releases writer fence");
            check(invoke(saveMethod, "ok") == null && Files.readString(target).equals("owned bytes") && interlock.snapshot().depth() == 0, "uncontended original writer body still runs unchanged");
            check(invoke(saveMethod, "backup-failed") == null && interlock.snapshot().depth() == 0, "legacy swallowed exception still returns while releasing exclusion");
            type.getField("failReset").set(null, true); boolean resetFailed = false;
            try { type.getConstructor().newInstance(); } catch (java.lang.reflect.InvocationTargetException failure) { resetFailed = failure.getCause() instanceof IllegalStateException; }
            check(resetFailed && interlock.snapshot().depth() == 0, "constructor original failure drains fence without hiding original error");
            var runningContext = gate.installContext(career, binding, 2, save, primary, secondary);
            var runningRequest = new CareerCommandGate.Request(runningContext.token(), "running", request.operation(), request.payloadDigest());
            var running = gate.reserve(owner, runningRequest, primary, secondary).permit().orElseThrow();
            SwingUtilities.invokeAndWait(() -> gate.begin(running, primary, secondary)); gate.uncertain(running);
            check(invoke(saveMethod, "another") instanceof IllegalStateException, "timed-out still-running handler blocks actual transformed writer entry");
            gate.finish(running, CareerCommandGate.Status.APPLIED, primary, secondary);
            check(invoke(saveMethod, null) == null && interlock.snapshot().depth() == 0, "early original return releases fence after handler actually returns");
            check(interlock.snapshot().admitted() == interlock.snapshot().completed(), "all successful admissions balanced across return/failure/fallback");
        }
        System.out.println("PASS: " + checks + " original I/O interlock checks; owned fixture " + fixture + "; no production write qualification.");
    }
}
