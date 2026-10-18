package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerCommandGate.*;
import static br.vbrasfoot.CareerCommandDispatcher.*;

/** Owned effect adapters and private journal directories only, no original game writes. */
public final class CareerCommandDispatcherSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Harness implements AutoCloseable {
        final CareerCommandGate gate = new CareerCommandGate();
        final Object primary = new Object(), secondary = new Object();
        final UUID career = UUID.randomUUID(), binding = UUID.randomUUID();
        final CareerSidecarStore.SaveRevision save = new CareerSidecarStore.SaveRevision("a".repeat(64), 10);
        final CareerSidecarStore.Key key = new CareerSidecarStore.Key(career, binding, "br.owned.dispatch");
        final Owner owner = gate.owner(key.mod(), Set.of(Operation.FINANCE_CREDIT));
        final CareerCommandGate.Context context;
        final CareerCommandJournal journal;
        Harness(Path root) { this(root, stage -> { }); }
        Harness(Path root, CareerSidecarStore.FaultProbe probe) {
            context = gate.installContext(career, binding, 4, save, primary, secondary);
            journal = new CareerCommandJournal(new CareerSidecarStore(root, probe), key);
        }
        CareerCommandDispatcher dispatcher(Duration timeout) { return new CareerCommandDispatcher(gate, journal, () -> new Roots(primary, secondary), timeout); }
        CareerCommandJournal.Intent intent(String key) { return new CareerCommandJournal.Intent(key, Operation.FINANCE_CREDIT, "b".repeat(64)); }
        @Override public void close() { gate.close(); }
    }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
    private static Result execute(CareerCommandDispatcher dispatcher, Owner owner, Context context, CareerCommandJournal.Intent intent,
                                  String token, Handler effect) throws Exception {
        return dispatcher.execute(owner, context, intent, token, new Handler() {
            @Override public boolean accepts(CareerCommandJournal.Intent proposed) { return intent.equals(proposed); }
            @Override public Status apply() throws Exception { return effect.apply(); }
        });
    }
    public static void main(String[] args) throws Exception {
        Path parent = System.getenv("LOCALAPPDATA") == null ? Files.createDirectories(Path.of("build"))
                : Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "journal-dispatch-").toRealPath(); var effects = new AtomicInteger();
        try (var h = new Harness(root.resolve("normal"))) {
            var d = h.dispatcher(Duration.ofSeconds(3));
            check(d.execute(h.owner, h.context, h.intent("unbound-handler"), CareerSidecarStore.EMPTY, () -> Status.APPLIED).detail() == Detail.HANDLER_MISMATCH, "unbound effect cannot claim arbitrary operation authority");
            var result = execute(d, h.owner, h.context, h.intent("one"), CareerSidecarStore.EMPTY, () -> {
                check(SwingUtilities.isEventDispatchThread(), "handler invoked on EDT");
                check(Files.exists(root.resolve("normal").resolve(h.career.toString()).resolve(h.binding.toString()).resolve(h.key.mod()).resolve("HEAD")), "intent HEAD exists before effect");
                effects.incrementAndGet(); return Status.APPLIED;
            });
            check(result.kind() == Kind.EXECUTED && result.journal().orElseThrow().receipts().get(0).state() == CareerCommandJournal.State.APPLIED, "gate+journal+EDT connected");
            var replay = execute(d, h.owner, h.context, h.intent("one"), result.journal().orElseThrow().token(), () -> { effects.incrementAndGet(); return Status.APPLIED; });
            check(replay.kind() == Kind.DENIED && effects.get() == 1, "same live gate never reruns duplicate");
            var changed = new CareerCommandJournal.Intent("one", Operation.FINANCE_CREDIT, "c".repeat(64));
            check(execute(d, h.owner, h.context, changed, result.journal().orElseThrow().token(), () -> Status.APPLIED).receipt().reason() == Reason.REQUEST_CONFLICT, "payload conflict denied");
            var noEffect = execute(d, h.owner, h.context, h.intent("two"), result.journal().orElseThrow().token(), () -> Status.REJECTED);
            check(noEffect.kind() == Kind.NO_EFFECT && noEffect.journal().orElseThrow().receipts().get(1).state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED, "trusted no-effect persisted separately");
            Owner readonly = h.gate.owner("br.owned.readonly", Set.of());
            check(execute(d, readonly, h.context, h.intent("denied-owner"), noEffect.journal().orElseThrow().token(), () -> Status.APPLIED).detail() == Detail.BINDING_MISMATCH, "journal namespace and owner must match");
            var wrongBinding = new Context(h.context.token(), UUID.randomUUID(), h.binding, 4, h.save);
            check(execute(d, h.owner, wrongBinding, h.intent("wrong-binding"), noEffect.journal().orElseThrow().token(), () -> Status.APPLIED).detail() == Detail.BINDING_MISMATCH, "journal binding checked");
            var failure = execute(d, h.owner, h.context, h.intent("three"), noEffect.journal().orElseThrow().token(), () -> { effects.incrementAndGet(); throw new IllegalStateException("owned mutation then failure"); });
            check(failure.kind() == Kind.UNCERTAIN && failure.detail() == Detail.HANDLER_FAILURE && effects.get() == 2, "post-effect exception uncertain, never rollback");
            check(h.journal.read(h.save).receipts().get(2).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN, "unknown persisted");
        }
        try (var h = new Harness(root.resolve("thread"))) {
            var failure = new AtomicReference<Throwable>();
            SwingUtilities.invokeAndWait(() -> { try { execute(h.dispatcher(Duration.ofSeconds(1)), h.owner, h.context, h.intent("on-edt"), CareerSidecarStore.EMPTY, () -> Status.APPLIED); }
                catch (Throwable expected) { failure.set(expected); } });
            check(failure.get() instanceof IllegalStateException && h.journal.read(h.save).receipts().isEmpty(), "caller EDT refused before journal creation");
            Thread.currentThread().interrupt();
            var result = execute(h.dispatcher(Duration.ofSeconds(3)), h.owner, h.context, h.intent("interrupted"), CareerSidecarStore.EMPTY, () -> Status.APPLIED);
            check(result.kind() == Kind.EXECUTED && Thread.interrupted(), "preexisting interruption restored only after outcome journal completion");
        }
        try (var h = new Harness(root.resolve("revocation"))) {
            var d = new CareerCommandDispatcher(h.gate, h.journal, () -> { h.gate.revoke(h.owner); return new Roots(h.primary, h.secondary); }, Duration.ofSeconds(2));
            var denied = execute(d, h.owner, h.context, h.intent("revoked"), CareerSidecarStore.EMPTY, () -> { effects.incrementAndGet(); return Status.APPLIED; });
            check(denied.receipt().reason() == Reason.OWNER_REVOKED && h.journal.read(h.save).receipts().isEmpty(), "revoked before reserve never persists intent or dispatches");
        }
        try (var h = new Harness(root.resolve("root-change"))) {
            var reads = new AtomicInteger(); int before = effects.get();
            var d = new CareerCommandDispatcher(h.gate, h.journal, () -> new Roots(reads.incrementAndGet() == 1 ? h.primary : new Object(), h.secondary), Duration.ofSeconds(3));
            var result = execute(d, h.owner, h.context, h.intent("changed"), CareerSidecarStore.EMPTY, () -> { effects.incrementAndGet(); return Status.APPLIED; });
            check(result.kind() == Kind.NO_EFFECT && result.receipt().reason() == Reason.STALE_CONTEXT && effects.get() == before,
                    "root change between durable intent and execution denies effect and records no-effect");
        }
        try (var h = new Harness(root.resolve("stale-journal"))) {
            var p = h.journal.prepare(h.save, CareerSidecarStore.EMPTY, h.intent("already-pending")); int before = effects.get();
            var result = execute(h.dispatcher(Duration.ofSeconds(3)), h.owner, h.context, h.intent("new"), CareerSidecarStore.EMPTY, () -> { effects.incrementAndGet(); return Status.APPLIED; });
            check(result.kind() == Kind.UNCERTAIN && result.detail() == Detail.JOURNAL_FAILURE && effects.get() == before,
                    "journal CAS error cannot enqueue a mutation");
            check(h.journal.read(h.save).token().equals(p.snapshot().token()), "failed dispatcher preserves preexisting intent");
        }
        try (var h = new Harness(root.resolve("queued-timeout"))) {
            var parked = new CountDownLatch(1); var releasePreflight = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> { parked.countDown(); try { releasePreflight.await(5, TimeUnit.SECONDS); } catch (InterruptedException failure) { throw new AssertionError(failure); } });
            try {
                check(parked.await(2, TimeUnit.SECONDS), "owned EDT parked before preflight");
                var timeout = execute(h.dispatcher(Duration.ofMillis(300)), h.owner, h.context, h.intent("preflight"), CareerSidecarStore.EMPTY, () -> Status.APPLIED);
                check(timeout.kind() == Kind.DENIED && timeout.detail() == Detail.QUEUE_TIMEOUT && h.journal.read(h.save).receipts().isEmpty(), "timed-out preflight never reserves or persists intent");
            } finally { releasePreflight.countDown(); flush(); }
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
            var d = new CareerCommandDispatcher(h.gate, h.journal, () -> {
                if (calls.incrementAndGet() == 1) SwingUtilities.invokeLater(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException failure) { throw new AssertionError(failure); } });
                return new Roots(h.primary, h.secondary);
            }, Duration.ofMillis(300));
            int before = effects.get();
            try {
                var result = execute(d, h.owner, h.context, h.intent("queued"), CareerSidecarStore.EMPTY, () -> { effects.incrementAndGet(); return Status.APPLIED; });
                check(entered.await(1, TimeUnit.SECONDS) && result.kind() == Kind.NO_EFFECT && result.detail() == Detail.QUEUE_TIMEOUT, "queued handler cancelled before invocation");
            } finally { release.countDown(); flush(); }
            check(effects.get() == before && h.journal.read(h.save).receipts().get(0).state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED, "cancelled queued callback remains inert after EDT unblocks");
        }
        try (var h = new Harness(root.resolve("running-timeout"))) {
            var release = new CountDownLatch(1); int before = effects.get();
            try {
                var result = execute(h.dispatcher(Duration.ofMillis(300)), h.owner, h.context, h.intent("running"), CareerSidecarStore.EMPTY, () -> {
                    effects.incrementAndGet(); if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("owned release timeout"); return Status.APPLIED;
                });
                check(result.kind() == Kind.UNCERTAIN && result.detail() == Detail.EXECUTION_TIMEOUT && effects.get() == before + 1, "running handler timeout cannot imply no effect");
                boolean refused = false;
                try { h.gate.installContext(h.career, h.binding, 5, h.save, h.primary, h.secondary); } catch (IllegalStateException expected) { refused = true; }
                check(refused, "cannot install fresh context while original timed-out handler still running");
            } finally { release.countDown(); flush(); }
            check(h.gate.receipts().get(0).status() == Status.OUTCOME_UNKNOWN && h.journal.read(h.save).receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN, "late success cannot erase recorded uncertainty");
        }
        for (boolean afterEffect : List.of(false, true)) {
            var commits = new AtomicInteger();
            try (var h = new Harness(root.resolve("fault-" + afterEffect), stage -> {
                if (stage == CareerSidecarStore.Stage.HEAD_COMMITTED && commits.incrementAndGet() == (afterEffect ? 2 : 1)) throw new IOException("owned post-commit fault");
            })) {
                int before = effects.get();
                var result = execute(h.dispatcher(Duration.ofSeconds(3)), h.owner, h.context, h.intent("fault"), CareerSidecarStore.EMPTY, () -> { effects.incrementAndGet(); return Status.APPLIED; });
                check(result.kind() == Kind.UNCERTAIN && result.detail() == Detail.JOURNAL_FAILURE, "journal failure reported explicitly " + afterEffect);
                check(effects.get() == before + (afterEffect ? 1 : 0), "prepare must succeed before effect " + afterEffect);
            }
        }
        Path restartRoot = root.resolve("restart"); UUID career, binding; CareerSidecarStore.SaveRevision save;
        try (var h = new Harness(restartRoot)) {
            career = h.career; binding = h.binding; save = h.save;
            execute(h.dispatcher(Duration.ofSeconds(3)), h.owner, h.context, h.intent("recorded"), CareerSidecarStore.EMPTY, () -> Status.APPLIED);
        }
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object(); var owner = gate.owner("br.owned.dispatch", Set.of(Operation.FINANCE_CREDIT));
            var context = gate.installContext(career, binding, 5, save, primary, secondary);
            var journal = new CareerCommandJournal(new CareerSidecarStore(restartRoot), new CareerSidecarStore.Key(career, binding, "br.owned.dispatch"));
            var d = new CareerCommandDispatcher(gate, journal, () -> new Roots(primary, secondary), Duration.ofSeconds(3)); int before = effects.get();
            var result = execute(d, owner, context, new CareerCommandJournal.Intent("recorded", Operation.FINANCE_CREDIT, "b".repeat(64)), journal.read(save).token(), () -> { effects.incrementAndGet(); return Status.APPLIED; });
            check(result.kind() == Kind.RECORDED_ONLY && effects.get() == before, "historical applied intent not claimed executed on fresh decoded roots");
        }
        System.out.println("PASS: " + checks + " journaled dispatch checks; owned effects only, fixture " + root + ".");
    }
}
