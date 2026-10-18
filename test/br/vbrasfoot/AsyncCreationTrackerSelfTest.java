package br.vbrasfoot;

import java.util.concurrent.*;

public final class AsyncCreationTrackerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        var tracker = new AsyncCreationTracker(); tracker.reset();
        Object primary = new Object(), secondary = new Object(), task = new Object(), child = new Object();
        tracker.origin("candidate", primary, secondary, 3); tracker.scheduleStarted("timer");
        tracker.taskCreated("timer", task, primary, secondary, 3);
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                tracker.runStarted("timer", task, primary, secondary, 3); began.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("owned worker deadline");
                tracker.scheduleStarted("child"); tracker.taskCreated("child", child, primary, secondary, 3); tracker.scheduleFinished("child", true);
                tracker.runFinished("timer", task, primary, secondary, 3, true);
            } catch (Throwable error) { failure.set(error); began.countDown(); }
        }, "owned-async-candidate-fixture");
        worker.start();
        try {
            check(began.await(3, TimeUnit.SECONDS), "run may start before scheduler returns");
            tracker.scheduleFinished("timer", true); tracker.endOrigin("candidate");
        } finally { release.countDown(); worker.join(4000); }
        check(!worker.isAlive() && failure.get() == null, "cross-thread fixture completed");
        var reports = tracker.snapshot(); var parent = reports.get(0); var descendant = reports.get(1);
        check(parent.started() && parent.returned() && parent.scheduleReturned() && !parent.stale(), "task matched independently of originating ThreadLocal");
        check(descendant.origin() == parent.origin() && descendant.parent() == parent.id(), "descendant inherits causal origin and parent task ID");
        check(descendant.originLabel().equals("candidate") && parent.originLabel().equals("candidate"), "origin candidate label survives cross-thread descendants");
        tracker.runStarted("child", child, primary, secondary, 3); tracker.runFinished("child", child, primary, secondary, 3, true);
        check(tracker.snapshot().get(1).returned(), "child run after original candidate returned retains weak causal identity");
        tracker.runStarted("timer", task, primary, secondary, 3); tracker.runFinished("timer", task, primary, secondary, 3, true);
        check(tracker.snapshot().get(0).stale() && tracker.rejected() == 1, "duplicate run rejected rather than reused");
        Object staleTask = new Object(); tracker.origin("new", primary, secondary, 3); tracker.scheduleStarted("stale");
        tracker.taskCreated("stale", staleTask, primary, secondary, 3); tracker.scheduleFinished("stale", true); tracker.endOrigin("new");
        tracker.runStarted("stale", staleTask, primary, secondary, 4); tracker.runFinished("stale", staleTask, primary, secondary, 4, true);
        check(tracker.snapshot().get(2).stale() && !tracker.snapshot().get(2).started(), "changed generation rejects start");
        Object failedTask = new Object(); tracker.origin("new", primary, secondary, 4); tracker.scheduleStarted("fail");
        tracker.taskCreated("fail", failedTask, primary, secondary, 4); tracker.scheduleFinished("fail", false); tracker.endOrigin("new");
        tracker.runStarted("fail", failedTask, primary, secondary, 4); tracker.runFinished("fail", failedTask, primary, secondary, 4, false);
        check(tracker.snapshot().get(3).scheduleFailed() && tracker.snapshot().get(3).stale(), "failed scheduling never becomes a valid run");
        tracker.scheduleStarted("unbound"); tracker.taskCreated("unbound", new Object(), primary, secondary, 4); tracker.scheduleFinished("unbound", true);
        check(tracker.unbound() == 1 && tracker.snapshot().size() == 4, "no candidate origin cannot fabricate task association");
        tracker.runStarted("unbound", new Object(), primary, secondary, 4); tracker.runFinished("unbound", new Object(), primary, secondary, 4, true);
        check(tracker.unbound() == 2, "unknown task is unbound without altering existing reports");
        tracker.reset(); check(tracker.snapshot().isEmpty(), "new session clears old reports");
        tracker.runStarted("timer", task, primary, secondary, 3); tracker.runFinished("timer", task, primary, secondary, 3, true);
        check(tracker.snapshot().isEmpty(), "old task cannot resurrect previous session");
        Object rootChangedTask = new Object(); tracker.origin("changed", primary, secondary, 5); tracker.scheduleStarted("changed");
        tracker.taskCreated("changed", rootChangedTask, primary, secondary, 5); tracker.scheduleFinished("changed", true); tracker.endOrigin("changed");
        tracker.runStarted("changed", rootChangedTask, primary, secondary, 5);
        tracker.runFinished("changed", rootChangedTask, new Object(), secondary, 5, true);
        check(tracker.snapshot().get(0).returned() && tracker.snapshot().get(0).stale(), "root change during task recorded stale, return not readiness");
        Object throwingTask = new Object(); tracker.origin("throw", primary, secondary, 5); tracker.scheduleStarted("throw");
        tracker.taskCreated("throw", throwingTask, primary, secondary, 5); tracker.scheduleFinished("throw", true); tracker.endOrigin("throw");
        tracker.runStarted("throw", throwingTask, primary, secondary, 5); tracker.runFinished("throw", throwingTask, primary, secondary, 5, false);
        check(tracker.snapshot().get(1).failed() && !tracker.snapshot().get(1).returned(), "task failure distinct from scheduling failure");
        var capped = new AsyncCreationTracker(); capped.reset(); capped.origin("cap", primary, secondary, 1);
        var retained = new java.util.ArrayList<Object>();
        for (int i = 0; i < 65; i++) {
            Object identity = new Object(); retained.add(identity); capped.scheduleStarted("cap");
            capped.taskCreated("cap", identity, primary, secondary, 1); capped.scheduleFinished("cap", true);
        }
        check(capped.snapshot().size() == 64 && capped.rejected() == 1, "64 live tasks limit, no unbounded weak ledger"); capped.endOrigin("cap");
        var ledger = new AsyncCreationTracker(); ledger.reset(); ledger.origin("ledger", primary, secondary, 1);
        for (int i = 0; i < 140; i++) {
            Object identity = new Object(); ledger.scheduleStarted("ledger"); ledger.taskCreated("ledger", identity, primary, secondary, 1);
            ledger.scheduleFinished("ledger", true); ledger.runStarted("ledger", identity, primary, secondary, 1);
            ledger.runFinished("ledger", identity, primary, secondary, 1, true);
        }
        check(ledger.snapshot().size() == 128, "completed report ledger bounded128"); ledger.endOrigin("ledger");
        check(!ledger.snapshot().toString().contains(primary.toString()), "snapshot never exposes root/task objects");
        var guard = new CareerLifecycle(); guard.rootsChanged(); var before = guard.snapshot();
        try (var attach = HookProbe.attachLifecycle(guard); var sink = HookProbe.attach(event -> { })) {
            HookProbe.creationPathStarted("a.bV.AB", primary, secondary);
            HookProbe.asyncScheduleStarted("a.hk.timer"); HookProbe.asyncTaskCreated("a.hk.timer", task, primary, secondary);
            HookProbe.asyncScheduleReturned("a.hk.timer"); HookProbe.creationPathReturned("a.bV.AB", primary, secondary);
            HookProbe.asyncRunStarted("a.hk.timer", task, primary, secondary); HookProbe.asyncRunReturned("a.hk.timer", task, primary, secondary);
            check(HookProbe.asyncCreationReports().size() == 1 && guard.snapshot().equals(before), "causal diagnostics never activate lifecycle/qualified I/O events");
        }
        System.out.println("PASS: " + checks + " bounded async candidate tracker checks; no readiness or unload events.");
    }
}
