package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Owned synthetic evidence only, not original UI, durable persistence or complete lifecycle. */
public final class CareerIoEventsSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "callback deadline"); }
    private static void load(boolean initialized) {
        HookProbe.enterLoad("private-input", false); HookProbe.rootAssigned(); HookProbe.decodedLoad();
        if (initialized) HookProbe.loadInitializationPathFinished();
        HookProbe.exitLoad(true);
    }
    private static void save(boolean metadata, boolean backup, boolean failure) {
        HookProbe.enterSave("private-output"); Object output = new Object(); HookProbe.primaryOutputClosing(output);
        if (metadata) HookProbe.metadataClosed();
        if (failure) HookProbe.primaryStreamCloseFailed(output); else HookProbe.primaryStreamClosed(output);
        HookProbe.primaryClosed(); if (backup) HookProbe.backupCopied(); HookProbe.exitSave();
    }
    public static final class ModFixture implements Mod {
        static CareerIoEvents io;
        static CareerObservations old;
        static CareerObservations.Subscription subscription;
        static boolean fail;
        public void initialize(ModContext context) {
            io = context.careerIoEvents(); old = context.careerObservations();
            subscription = io.subscribe(update -> { });
            if (fail) throw new IllegalStateException("owned init failure");
        }
    }
    private static ModManifest manifest(Set<String> permissions, Set<String> capabilities) {
        return new ModManifest(Path.of("build/io-fixture.jar"), "io.fixture", "IO", "1", ModFixture.class.getName(),
                List.of(), permissions, List.of(), List.of(), capabilities);
    }
    public static void main(String[] args) throws Exception {
        var budget = new CareerSubscriberBudget(32);
        try (var old = new CareerObservationHub(budget); var io = new CareerIoEventHub(budget)) {
                var lifecycle = new CareerLifecycle(old, io);
            try (var guard = HookProbe.attachLifecycle(lifecycle)) {
                List<CareerIoEvents.Update> updates = Collections.synchronizedList(new ArrayList<>());
                CountDownLatch replay = new CountDownLatch(1), complete = new CountDownLatch(1);
                String producer = Thread.currentThread().getName();
                var subscription = io.subscribe(update -> {
                    if (Thread.currentThread().getName().equals(producer)) throw new AssertionError("game thread callback");
                    updates.add(update);
                    if (update.kind() == CareerIoEvents.Kind.CURRENT_STATE) replay.countDown();
                    if (update.kind() == CareerIoEvents.Kind.SAVE_SEQUENCE_COMPLETED) complete.countDown();
                });
                await(replay); load(false); save(true, true, false);
                check(io.state().existingLoads() == 0 && io.state().completedSaves() == 0 && io.state().phase() == CareerIoEvents.Phase.UNKNOWN,
                        "skipped initialization cannot qualify a read or following save");
                check(old.state().reads() == 1 && old.state().primaryWrites() == 1, "legacy observation semantics preserved");
                load(true);
                check(io.state().existingLoads() == 1 && io.state().phase() == CareerIoEvents.Phase.ACTIVE, "qualified existing read");
                save(false, true, false); save(true, false, false); save(true, true, true);
                check(io.state().completedSaves() == 0, "metadata/backup/hidden close failures excluded");
                save(true, true, false); await(complete);
                check(io.state().completedSaves() == 1 && old.state().primaryWrites() == 4, "complete event separate from primary write observations");
                synchronized (updates) {
                    check(updates.stream().filter(u -> u.kind() == CareerIoEvents.Kind.EXISTING_LOAD_COMPLETED).count() == 1
                            && updates.stream().filter(u -> u.kind() == CareerIoEvents.Kind.SAVE_SEQUENCE_COMPLETED).count() == 1, "no duplicates or partial-success events");
                    long previous = -1;
                    for (var update : updates) { check(update.state().revision() > previous, "strict FIFO revisions"); previous = update.state().revision(); }
                    check(!updates.toString().contains("private-"), "no paths or game objects in public updates");
                }
                var pending = lifecycle.beginSave(); load(true);
                lifecycle.finishSave(pending, "stale", true, true, true, true, true, false);
                check(io.state().completedSaves() == 1, "late save from old generation excluded");
                var first = lifecycle.beginSave(); var second = lifecycle.beginSave();
                lifecycle.finishSave(second, "overlap", true, true, true, true, true, false);
                lifecycle.finishSave(first, "overlap", true, true, true, true, true, false);
                check(io.state().completedSaves() == 1, "both overlapping saves excluded");
                long loads = io.state().existingLoads();
                HookProbe.enterLoad("fallback", false); HookProbe.enterLoad("fallback", true); HookProbe.rootAssigned(); HookProbe.decodedLoad();
                HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true); HookProbe.exitLoad(false);
                check(io.state().existingLoads() == loads + 1, "qualified fallback once despite false outer return");
                HookProbe.enterReset(); HookProbe.rootAssigned(); HookProbe.rootsReset();
                check(io.state().phase() == CareerIoEvents.Phase.UNKNOWN, "reset invalidates, never unload or ready creation");
                var logger = Logger.getLogger("io-contract-fixture"); logger.setUseParentHandlers(false);
                try (var loader = new ModLoader(logger, old, io)) {
                    loader.load(List.of(manifest(Set.of("events.career.read"), Set.of("events.career.io"))), null, null, Path.of("build"));
                    ModFixture.old.subscribe(update -> { }); ModFixture.io.subscribe(update -> { }); ModFixture.old.subscribe(update -> { });
                    boolean refused = false;
                    try { ModFixture.io.subscribe(update -> { }); } catch (IllegalStateException expected) { refused = true; }
                    check(refused, "four subscriptions total across old/new APIs per mod");
                    loader.close(); check(ModFixture.subscription.status() == CareerObservations.Status.CLOSED, "owned cleanup");
                    refused = false; try { ModFixture.io.state(); } catch (IllegalStateException expected) { refused = true; }
                    check(refused, "retained qualified service revoked after dispose");
                }
                for (int mode = 0; mode < 3; mode++) {
                    boolean refused = false;
                    try (var loader = mode == 2 ? new ModLoader(logger, old) : new ModLoader(logger, old, io)) {
                        loader.load(List.of(manifest(mode == 0 ? Set.of() : Set.of("events.career.read"),
                                mode == 1 ? Set.of() : Set.of("events.career.io"))), null, null, Path.of("build"));
                    } catch (SecurityException | UnsupportedOperationException expected) { refused = true; }
                    check(refused, "permission/capability/provider enforced " + mode);
                }
                ModFixture.fail = true;
                try (var loader = new ModLoader(logger, old, io)) {
                    boolean refused = false;
                    try { loader.load(List.of(manifest(Set.of("events.career.read"), Set.of("events.career.io"))), null, null, Path.of("build")); }
                    catch (IllegalStateException expected) { refused = true; }
                    check(refused && ModFixture.subscription.status() == CareerObservations.Status.CLOSED, "failed init releases new API subscription");
                } finally { ModFixture.fail = false; }
                boolean refused = false;
                try { ModLoader.requireObservationMode(List.of(manifest(Set.of("events.career.read"), Set.of("events.career.io"))), false); }
                catch (java.io.IOException expected) { refused = true; }
                check(refused, "conditional capability preflight before initialization");
                subscription.close(); lifecycle.close();
                check(io.state().phase() == CareerIoEvents.Phase.CLOSED, "dispose state, no invented unload event");
            }
        }
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var io = new CareerIoEventHub()) {
            var state = new CareerLifecycle(null, io);
            CountDownLatch failed = new CountDownLatch(1);
            var bad = io.subscribe(update -> { failed.countDown(); throw new AssertionError("owned callback failure"); });
            await(failed);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (bad.status() == CareerObservations.Status.ACTIVE && System.nanoTime() < deadline) Thread.onSpinWait();
            check(bad.status() == CareerObservations.Status.CALLBACK_FAILED, "typed callback failure isolated");
            var attempt = state.beginMutation(); var ticket = state.rootsChanged(); state.loaded(attempt, ticket, "qualified", false, true, true);
            check(io.state().existingLoads() == 1, "callback failure cannot interrupt qualified state");
            CountDownLatch recovered = new CountDownLatch(1);
            try (var next = io.subscribe(update -> {
                if (update.kind() == CareerIoEvents.Kind.CURRENT_STATE && update.state().existingLoads() == 1) recovered.countDown();
            })) { await(recovered); }
            state.close();
        }
        var shared = new CareerSubscriberBudget(1);
        try (var old = new CareerObservationHub(1, 1, java.time.Duration.ofMillis(20), shared); var io = new CareerIoEventHub(shared)) {
            var state = new CareerLifecycle(old, io);
            var slow = old.subscribe(update -> {
                blocked.countDown(); boolean done = false;
                while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            }); await(blocked); state.rootsChanged(); state.rootsChanged();
            check(slow.status() == CareerObservations.Status.OVERFLOW, "old stream overflow observed");
            boolean refused = false; try { io.subscribe(update -> { }); } catch (IllegalStateException expected) { refused = true; }
            check(refused, "retired worker reserves shared budget across APIs until exit");
        } finally { release.countDown(); }
        System.out.println("PASS: " + checks + " qualified career I/O contract checks (synthetic, not durable lifecycle).");
    }
}
