package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Deterministic owned subscribers; no game, persistence or public unload inference. */
public final class CareerObservationsSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(3, TimeUnit.SECONDS), "fixture callback deadline"); }
    private static void until(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("fixture state deadline");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000);
        }
    }
    private static void load(CareerLifecycle state, String file) {
        var attempt = state.beginMutation(); var ticket = state.rootsChanged();
        check(state.loaded(attempt, ticket, file, false, true), "exclusive observed read");
    }
    public static final class ModFixture implements Mod {
        static CareerObservations service;
        static CareerObservations.Subscription subscription;
        static boolean fail;
        public void initialize(ModContext context) {
            service = context.careerObservations(); subscription = service.subscribe(update -> { });
            if (fail) throw new IllegalStateException("fixture initialization failure");
        }
    }
    private static ModManifest manifest(Set<String> permissions) {
        return new ModManifest(Path.of("build/observation-fixture.jar"), "fixture", "Fixture", "1", ModFixture.class.getName(), List.of(), permissions,
                List.of(), List.of(), Set.of("events.career.observed"));
    }
    public static void main(String[] args) throws Exception {
        ModManifest example = ModManifest.read(Path.of("dist/career-observer.jar"));
        check(example.requiredCapabilities().contains("events.career.observed") && example.permissions().equals(Set.of("events.career.read")), "observer manifest parsed with minimal permission/conditional capability");
        ModValidator.validateClass(example);
        try (var hub = new CareerObservationHub()) {
            CareerLifecycle lifecycle = new CareerLifecycle(hub);
            List<CareerObservations.Update> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch replay = new CountDownLatch(1), delivered = new CountDownLatch(1);
            String producer = Thread.currentThread().getName();
            var subscription = hub.subscribe(update -> {
                if (Thread.currentThread().getName().equals(producer)) throw new AssertionError("callback on producer");
                received.add(update);
                if (update.kind() == CareerObservations.Kind.CURRENT_STATE) replay.countDown();
                if (update.kind() == CareerObservations.Kind.PRIMARY_WRITE_COMPLETED) delivered.countDown();
            });
            await(replay); load(lifecycle, "private-path-not-public");
            check(lifecycle.saved(lifecycle.ticket(), "private-output"), "bound write"); await(delivered);
            check(received.size() == 5 && received.get(0).kind() == CareerObservations.Kind.CURRENT_STATE
                    && received.get(3).kind() == CareerObservations.Kind.READ_COMPLETED, "initial replay + invalidations + read + write");
            check(received.stream().map(u -> u.state().revision()).toList().equals(List.of(0L, 1L, 2L, 3L, 4L)), "ordered revisions with no duplicate replay gap");
            check(!received.toString().contains("private-path") && hub.state().primaryWrites() == 1, "public state has counters but no paths");
            subscription.close(); subscription.close();
            check(subscription.status() == CareerObservations.Status.CLOSED, "idempotent cancellation");
            var ticket = lifecycle.ticket(); load(lifecycle, "next");
            long revision = hub.state().revision();
            check(!lifecycle.saved(ticket, "old") && hub.state().revision() == revision, "stale completion emits no write update");
            check(hub.state().reads() == 2, "state catches up independently of closed subscriber");
            lifecycle.close(); check(hub.state().phase() == CareerObservations.Phase.CLOSED, "close is state, not unload event");
            boolean refused = false; try { hub.subscribe(u -> { }); } catch (IllegalStateException expected) { refused = true; }
            check(refused, "closed session refuses subscribe");
        }

        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1), healthyRead = new CountDownLatch(1);
        try (var hub = new CareerObservationHub(1, 2, Duration.ofMillis(50))) {
            CareerLifecycle lifecycle = new CareerLifecycle(hub);
            var slow = hub.subscribe(update -> {
                blocked.countDown();
                // Deliberately ignores interruption: cancellation cannot kill third-party Java code.
                boolean done = false;
                while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            }); await(blocked);
            lifecycle.rootsChanged(); lifecycle.rootsChanged();
            check(slow.status() == CareerObservations.Status.OVERFLOW, "slow subscriber terminated on overflow");
            var healthy = hub.subscribe(update -> { if (update.kind() == CareerObservations.Kind.CURRENT_STATE) healthyRead.countDown(); });
            await(healthyRead);
            boolean limited = false; try { hub.subscribe(u -> { }); } catch (IllegalStateException expected) { limited = true; }
            check(limited, "interruption-ignoring retired worker still counts toward resource cap");
            check(healthy.status() == CareerObservations.Status.ACTIVE && hub.state().revision() == 2, "one overflow does not terminate other subscriber/state");
            long start = System.nanoTime(); hub.close();
            check(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "global bounded close without waiting indefinitely");
            check(slow.status() == CareerObservations.Status.OVERFLOW && healthy.status() == CareerObservations.Status.CLOSED, "failure status retained on close");
        } finally { release.countDown(); }

        try (var hub = new CareerObservationHub()) {
            CareerLifecycle lifecycle = new CareerLifecycle(hub);
            var bad = hub.subscribe(update -> { throw new AssertionError("fixture callback failure"); });
            until(() -> bad.status() == CareerObservations.Status.CALLBACK_FAILED);
            load(lifecycle, "still-loads"); check(hub.state().phase() == CareerObservations.Phase.ACTIVE, "subscriber exception never interrupts producer");
            CountDownLatch replay = new CountDownLatch(1);
            var replacement = hub.subscribe(update -> { if (update.kind() == CareerObservations.Kind.CURRENT_STATE && update.state().reads() == 1) replay.countDown(); });
            await(replay); replacement.close();
            var interrupted = hub.subscribe(update -> Thread.currentThread().interrupt());
            until(() -> interrupted.status() == CareerObservations.Status.CLOSED);
            check(interrupted.status() == CareerObservations.Status.CLOSED, "self-interrupted worker does not leave active zombie subscription");

            Logger logger = Logger.getLogger("observation-mod-fixture"); logger.setUseParentHandlers(false);
            try (var loader = new ModLoader(logger, hub)) {
                loader.load(List.of(manifest(Set.of("events.career.read"))), null, null, Path.of("build"));
                check(ModFixture.subscription.status() == CareerObservations.Status.ACTIVE, "mod subscribes via permission checked SDK wrapper");
                for (int i = 0; i < 3; i++) ModFixture.service.subscribe(update -> { });
                boolean limited = false;
                try { ModFixture.service.subscribe(update -> { }); } catch (IllegalStateException expected) { limited = true; }
                check(limited, "per-mod limit enforced independently of global limit");
                loader.close(); check(ModFixture.subscription.status() == CareerObservations.Status.CLOSED, "loader owns subscriptions on shutdown");
                boolean rejected = false; try { ModFixture.service.subscribe(update -> { }); } catch (IllegalStateException expected) { rejected = true; }
                check(rejected, "retained service cannot create subscribers after mod disposal");
            }
            ModFixture.fail = true;
            try (var loader = new ModLoader(logger, hub)) {
                boolean rejected = false; try { loader.load(List.of(manifest(Set.of("events.career.read"))), null, null, Path.of("build")); }
                catch (IllegalStateException expected) { rejected = true; }
                check(rejected && ModFixture.subscription.status() == CareerObservations.Status.CLOSED, "failed initialization releases subscription");
            } finally { ModFixture.fail = false; }
            try (var loader = new ModLoader(logger, hub)) {
                boolean rejected = false; try { loader.load(List.of(manifest(Set.of())), null, null, Path.of("build")); } catch (SecurityException expected) { rejected = true; }
                check(rejected, "events permission enforced");
            }
            try (var loader = new ModLoader(logger)) {
                boolean rejected = false; try { loader.load(List.of(manifest(Set.of("events.career.read"))), null, null, Path.of("build")); } catch (UnsupportedOperationException expected) { rejected = true; }
                check(rejected, "normal mode exposes no observation provider");
            }
            boolean rejected = false; try { ModLoader.requireObservationMode(List.of(manifest(Set.of())), false); } catch (java.io.IOException expected) { rejected = true; }
            check(rejected, "required capability refused before mod initialization without flag");
            ModLoader.requireObservationMode(List.of(manifest(Set.of())), true);
        }
        System.out.println("PASS: " + checks + " experimental career subscription checks (synthetic).");
    }
}
