package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerCommandJournal.*;

/** Owned synthetic journals/roots. No loaded-career authority or original save is inferred here. */
public final class CareerRecoverySelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static void invalidState(Action action, String label) throws Exception {
        try { action.run(); } catch (IllegalStateException expected) { checks++; return; } throw new AssertionError(label);
    }
    private static void invalidAuthority(Action action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(label);
    }
    private static Intent intent(String name) { return new Intent(name, CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64)); }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "command-recovery-").toRealPath();
        var store = new CareerSidecarStore(root);
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.recovery");
        var save = new SaveRevision("a".repeat(64), 10); var next = new SaveRevision("b".repeat(64), 11);
        var journal = new CareerCommandJournal(store, key);
        var empty = journal.inspectRecovery(save, next);
        check(empty.status() == RecoveryStatus.EMPTY && empty.revisionChanged() && !Files.exists(root.resolve(key.career().toString())), "empty assessment is read-only, not an association");
        check(journal.quarantinePending(save, EMPTY).token().equals(EMPTY), "empty recovery makes no metadata generation");
        var safe = journal.prepare(save, EMPTY, intent("no-effect"));
        var safeDone = journal.complete(safe.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, safe.snapshot().token());
        check(journal.inspectRecovery(save, save).status() == RecoveryStatus.NO_EFFECT_ONLY, "known no-effect differs from historical effect");
        var applied = journal.prepare(save, safeDone.token(), intent("applied"));
        var appliedDone = journal.complete(applied.lease().orElseThrow(), State.APPLIED, applied.snapshot().token());
        var historical = journal.inspectRecovery(save, next);
        check(historical.status() == RecoveryStatus.HISTORICAL_EFFECTS && historical.revisionChanged(), "APPLIED is history, not proof of inclusion in changed save");
        check(journal.inspectRecovery(save, save).status() == RecoveryStatus.HISTORICAL_EFFECTS, "even equal bytes do not establish effect in loaded graph");
        check(journal.quarantinePending(save, appliedDone.token()).token().equals(appliedDone.token()), "terminal receipts never rewritten by recovery");
        var pending = journal.prepare(save, appliedDone.token(), intent("interrupted"));
        var restarted = new CareerCommandJournal(store, key);
        var before = restarted.inspectRecovery(save, next);
        check(before.status() == RecoveryStatus.UNCERTAIN && before.snapshot().receipts().size() == 3, "startup assessment prioritizes pending uncertainty over applied history");
        var quarantined = restarted.quarantinePending(save, before.snapshot().token());
        check(!quarantined.token().equals(before.snapshot().token()) && quarantined.receipts().equals(before.snapshot().receipts()), "quarantine changes durable PREPARED without changing conservative receipts or identities");
        check(restarted.quarantinePending(save, quarantined.token()).token().equals(quarantined.token()), "recovery is idempotent without eviction or new generation");
        rejects(Reason.CONFLICT, () -> journal.complete(pending.lease().orElseThrow(), State.APPLIED, pending.snapshot().token()));
        rejects(Reason.CONFLICT, () -> journal.complete(pending.lease().orElseThrow(), State.APPLIED, quarantined.token()));
        check(journal.complete(pending.lease().orElseThrow(), State.OUTCOME_UNKNOWN, quarantined.token()).token().equals(quarantined.token()), "late unknown can agree, never erase quarantine");
        check(restarted.prepare(save, quarantined.token(), intent("interrupted")).lease().isEmpty(), "recovered command never grants another lease");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> restarted.prepare(save, quarantined.token(), intent("another")));
        rejects(Reason.CONFLICT, () -> restarted.quarantinePending(save, pending.snapshot().token()));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> restarted.quarantinePending(next, quarantined.token()));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> restarted.inspectRecovery(next, save));
        check(restarted.read(save).token().equals(quarantined.token()), "assessment cannot migrate journal anchor to observed revision");
        var edtFailure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { restarted.quarantinePending(save, quarantined.token()); } catch (Throwable failure) { edtFailure.set(failure); } });
        check(edtFailure.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "startup recovery never performs disk I/O on EDT");
        for (Stage interrupted : Stage.values()) {
            var faultKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.fault");
            var original = new CareerCommandJournal(store, faultKey);
            var prepared = original.prepare(save, EMPTY, intent("once"));
            var failing = new CareerCommandJournal(new CareerSidecarStore(root, stage -> { if (stage == interrupted) throw new IOException("owned interruption"); }), faultKey);
            boolean failed = false;
            try { failing.quarantinePending(save, prepared.snapshot().token()); } catch (IOException expected) { failed = true; }
            check(failed, "injected recovery interruption " + interrupted);
            var observed = original.read(save);
            check(observed.receipts().equals(prepared.snapshot().receipts()) && original.prepare(save, observed.token(), intent("once")).lease().isEmpty(), "failure never loses receipt or grants replay " + interrupted);
            var normalized = original.quarantinePending(save, observed.token());
            rejects(Reason.CONFLICT, () -> original.complete(prepared.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, normalized.token()));
        }
        var raceKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.race");
        var racer = new CareerCommandJournal(store, raceKey); var racePending = racer.prepare(save, EMPTY, intent("race"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1); var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) results.add(executor.submit(() -> {
                start.await();
                try { new CareerCommandJournal(store, raceKey).quarantinePending(save, racePending.snapshot().token()); return true; }
                catch (Failure expected) { if (expected.reason() != Reason.BUSY && expected.reason() != Reason.CONFLICT) throw expected; return false; }
            }));
            start.countDown(); int winners = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) winners++;
            check(winners == 1 && racer.read(save).receipts().equals(racePending.snapshot().receipts()), "concurrent startup recovery has one CAS winner and preserves receipt");
        } finally { executor.shutdownNow(); }
        var completeRaceKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.complete-race");
        var live = new CareerCommandJournal(store, completeRaceKey); var livePending = live.prepare(save, EMPTY, intent("live"));
        var completionExecutor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var completion = completionExecutor.submit(() -> {
                start.await();
                try { live.complete(livePending.lease().orElseThrow(), State.APPLIED, livePending.snapshot().token()); return true; }
                catch (Failure expected) { if (expected.reason() != Reason.BUSY && expected.reason() != Reason.CONFLICT) throw expected; return false; }
            });
            var quarantine = completionExecutor.submit(() -> {
                start.await();
                try { new CareerCommandJournal(store, completeRaceKey).quarantinePending(save, livePending.snapshot().token()); return true; }
                catch (Failure expected) { if (expected.reason() != Reason.BUSY && expected.reason() != Reason.CONFLICT) throw expected; return false; }
            });
            start.countDown(); boolean completed = completion.get(10, TimeUnit.SECONDS), recovered = quarantine.get(10, TimeUnit.SECONDS);
            var finalSnapshot = live.read(save);
            check(completed != recovered && finalSnapshot.receipts().size() == 1, "completion racing recovery has one CAS winner, no lost record");
            check(finalSnapshot.receipts().get(0).state() == (completed ? State.APPLIED : State.OUTCOME_UNKNOWN), "race preserves actual winning terminal state, never guesses no effect");
            check(live.prepare(save, finalSnapshot.token(), intent("live")).lease().isEmpty(), "neither race winner reissues existing command");
        } finally { completionExecutor.shutdownNow(); }
        Object primary = new Object(), secondary = new Object();
        try (var gate = new CareerCommandGate(); var foreign = new CareerCommandGate()) {
            var owner = gate.owner("br.owned.recovery", Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
            var request = new CareerCommandGate.Request(context.token(), "pending", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            var queued = gate.reserve(owner, request, primary, secondary).permit().orElseThrow();
            invalidState(gate::beginSave, "queued dispatch must prevent starting save");
            gate.cancel(queued);
            var runningRequest = new CareerCommandGate.Request(context.token(), "running", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            var running = gate.reserve(owner, runningRequest, primary, secondary).permit().orElseThrow();
            SwingUtilities.invokeAndWait(() -> gate.begin(running, primary, secondary));
            invalidState(gate::beginSave, "running handler must prevent starting save");
            gate.uncertain(running);
            invalidState(gate::beginSave, "timed-out original handler must return before save window");
            gate.finish(running, CareerCommandGate.Status.APPLIED, primary, secondary);
            var fence = gate.beginSave();
            check(gate.reserve(owner, request, primary, secondary).receipt().reason() == CareerCommandGate.Reason.SAVE_IN_PROGRESS, "save window denies commands even for historical keys");
            invalidState(() -> gate.installContext(key.career(), key.binding(), 2, next, primary, secondary), "cannot install roots during original write");
            invalidState(gate::beginSave, "nested save refused without silently closing first");
            var otherFence = foreign.beginSave();
            invalidAuthority(() -> gate.endSave(otherFence), "foreign fence cannot release original save window");
            invalidAuthority(() -> gate.endSave(null), "null fence cannot release original save window");
            gate.invalidate();
            check(gate.reserve(owner, request, primary, secondary).receipt().reason() == CareerCommandGate.Reason.SAVE_IN_PROGRESS, "invalidation cannot clear save exclusion");
            gate.endSave(fence);
            invalidAuthority(() -> gate.endSave(fence), "fence cannot be reused");
            var freshRequest = new CareerCommandGate.Request(context.token(), "fresh", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            check(gate.reserve(owner, freshRequest, primary, secondary).receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT, "closing save window never restores old context");
            var newContext = gate.installContext(key.career(), key.binding(), 2, next, primary, secondary);
            check(!newContext.token().equals(context.token()), "only separately supplied fixture qualification installs new context");
            var finalFence = gate.beginSave(); gate.close(); gate.endSave(finalFence);
            invalidState(() -> gate.installContext(key.career(), key.binding(), 3, next, primary, secondary), "closed gate stays closed after fence release");
            invalidState(gate::beginSave, "closed gate cannot start save window");
        }
        var fenceKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.fence-dispatch");
        var fenceJournal = new CareerCommandJournal(store, fenceKey);
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner(fenceKey.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var context = gate.installContext(fenceKey.career(), fenceKey.binding(), 1, save, primary, secondary);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var dispatcher = new CareerCommandDispatcher(gate, fenceJournal,
                    () -> new CareerCommandDispatcher.Roots(primary, secondary), java.time.Duration.ofSeconds(2));
            var handler = new CareerCommandDispatcher.Handler() {
                @Override public CareerCommandGate.Status apply() { calls.incrementAndGet(); return CareerCommandGate.Status.APPLIED; }
                @Override public boolean accepts(Intent request) { return true; }
            };
            var fence = gate.beginSave();
            var denied = dispatcher.execute(owner, context, intent("during-save"), EMPTY, handler);
            check(denied.kind() == CareerCommandDispatcher.Kind.DENIED && denied.receipt().reason() == CareerCommandGate.Reason.SAVE_IN_PROGRESS,
                    "dispatcher obeys save exclusion before preparing intent");
            check(calls.get() == 0 && fenceJournal.read(save).token().equals(EMPTY), "no effect or disk intent during fenced original save");
            gate.endSave(fence); // Represents a writer failure too: no success assumption and no context restoration.
            var after = dispatcher.execute(owner, context, intent("after-failed-save"), EMPTY, handler);
            check(after.receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT && calls.get() == 0, "ending failed writer still denies old context at dispatch");
        }
        var ioEntered = new CountDownLatch(1); var releaseIo = new CountDownLatch(1);
        var commits = new java.util.concurrent.atomic.AtomicInteger();
        var ioKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.completion-fence");
        var ioJournal = new CareerCommandJournal(new CareerSidecarStore(root, stage -> {
            if (stage == Stage.HEAD_COMMITTED && commits.incrementAndGet() == 2) {
                ioEntered.countDown();
                try { if (!releaseIo.await(10, TimeUnit.SECONDS)) throw new IOException("owned completion wait timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
        }), ioKey);
        var ioExecutor = Executors.newSingleThreadExecutor();
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner(ioKey.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var context = gate.installContext(ioKey.career(), ioKey.binding(), 1, save, primary, secondary);
            var dispatcher = new CareerCommandDispatcher(gate, ioJournal,
                    () -> new CareerCommandDispatcher.Roots(primary, secondary), java.time.Duration.ofSeconds(2));
            var handler = new CareerCommandDispatcher.Handler() {
                @Override public CareerCommandGate.Status apply() { return CareerCommandGate.Status.APPLIED; }
                @Override public boolean accepts(Intent request) { return true; }
            };
            var result = ioExecutor.submit(() -> dispatcher.execute(owner, context, intent("completion"), EMPTY, handler));
            check(ioEntered.await(10, TimeUnit.SECONDS), "original handler finished while owned journal completion is parked");
            invalidState(gate::beginSave, "writer cannot start while post-effect journal I/O still active");
            invalidState(() -> gate.installContext(ioKey.career(), ioKey.binding(), 2, next, primary, secondary), "cannot switch career context during post-effect persistence");
            releaseIo.countDown();
            check(result.get(10, TimeUnit.SECONDS).kind() == CareerCommandDispatcher.Kind.EXECUTED, "normal completion returns after persistence before releasing dispatch reservation");
            var fence = gate.beginSave(); gate.endSave(fence);
            check(ioJournal.inspectRecovery(save, save).status() == RecoveryStatus.HISTORICAL_EFFECTS, "fence opens after persistence without rewriting applied receipt");
        } finally { releaseIo.countDown(); ioExecutor.shutdownNow(); }
        System.out.println("PASS: " + checks + " journal recovery/save-fence checks; owned fixture " + root + "; no recovered-effect proof or production save interception.");
    }
}
