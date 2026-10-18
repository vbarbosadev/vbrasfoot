package br.vbrasfoot;

import java.util.*;

public final class CreationQualificationIntegrationSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void prepare(CreationEntryTracker tracker, Object panel, Object primary, Object secondary) {
        for (String stage : List.of("ENTER", "SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN", "RETURN"))
            tracker.preparation(stage, stage.equals("PANEL_ASSIGNED") ? panel : null, primary, secondary, 4);
    }
    private static void accept(CreationEntryTracker tracker, CreationPathQualificationSelfTest.Signal s, Object primary, Object secondary, long generation) {
        switch (s.kind()) {
            case "dialog" -> tracker.dialog(s.method(), s.stage(), s.value(), primary, secondary, generation);
            case "candidate" -> tracker.candidate(s.method(), s.stage(), primary, secondary, generation);
            case "step" -> tracker.step(s.method(), s.stage(), primary, secondary, generation);
            case "progress" -> tracker.progress(s.method(), s.stage(), s.value(), s.stage().equals("ENTER") ? primary : null, primary, secondary, generation);
        }
    }
    private static CreationPathQualification.Snapshot last(CreationEntryTracker tracker) {
        var attempts = tracker.qualificationSnapshot().attempts(); return attempts.get(attempts.size() - 1).path();
    }
    public static void main(String[] args) throws Exception {
        var tracker = new CreationEntryTracker(); Object panel = new Object(), primary = new Object(), secondary = new Object();
        var signals = CreationPathQualificationSelfTest.path(12); tracker.reset(); prepare(tracker, panel, primary, secondary);
        tracker.attempt(panel, primary, secondary, 4, true); signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        check(last(tracker).status() == CreationPathQualification.Status.PATH_RETURNED && tracker.qualificationSnapshot().active() == 0, "bound online full path recognized and released");
        tracker.attempt(panel, primary, secondary, 4, true); signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        check(last(tracker).reason() == CreationPathQualification.Reason.REUSED_BINDING, "binding single-use for strict recognition");
        tracker.reset(); tracker.attempt(panel, primary, secondary, 4, true); signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        check(last(tracker).reason() == CreationPathQualification.Reason.UNBOUND, "unbound cannot qualify from method shape alone");
        tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, false);
        signals.forEach(s -> accept(tracker, s, primary, secondary, 4)); check(last(tracker).reason() == CreationPathQualification.Reason.NOT_EDT, "non EDT refused");
        for (int mismatch = 0; mismatch < signals.size(); mismatch++) {
            tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
            for (int i = 0; i < signals.size(); i++) accept(tracker, signals.get(i), primary, secondary, i == mismatch ? 5 : 4);
            check(last(tracker).reason() == CreationPathQualification.Reason.STALE_BINDING, "generation mismatch at signal " + mismatch);
        }
        for (var reason : List.of(CreationPathQualification.Reason.ASYNC_OBSERVED, CreationPathQualification.Reason.SAVE_OBSERVED, CreationPathQualification.Reason.MUTATION_OBSERVED)) {
            tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
            tracker.interference(reason); signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
            check(last(tracker).reason() == reason, "observed interference refused " + reason);
        }
        for (int root = 0; root < 2; root++) for (int mismatch = 0; mismatch < signals.size(); mismatch++) {
            tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
            for (int i = 0; i < signals.size(); i++) accept(tracker, signals.get(i),
                    root == 0 && i == mismatch ? new Object() : primary, root == 1 && i == mismatch ? new Object() : secondary, 4);
            check(last(tracker).reason() == CreationPathQualification.Reason.STALE_BINDING, "root identity mismatch " + root + " at signal " + mismatch);
        }
        tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
        signals.subList(0, 7).forEach(s -> accept(tracker, s, primary, secondary, 4));
        var incomplete = tracker.qualificationSnapshot();
        check(last(tracker).status() == CreationPathQualification.Status.RUNNING && incomplete.active() == 1, "shutdown capture incomplete is RUNNING, never completed");
        tracker.reset(); check(tracker.qualificationSnapshot().attempts().isEmpty() && incomplete.attempts().size() == 1, "reset clears qualifications, capture detached");
        for (int i = 0; i < 130; i++) {
            tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4); // Revoke previous binding explicitly, no GC dependency.
            prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
            signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        }
        check(tracker.qualificationSnapshot().attempts().size() == 128 && tracker.qualificationSnapshot().dropped() == 2, "128completed reports bounded separately from512diagnostic events");
        check(tracker.qualificationSnapshot().attempts().stream().allMatch(a -> a.path().status() == CreationPathQualification.Status.PATH_RETURNED), "online state does not depend on retained event ring");
        tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
        Thread other = new Thread(() -> { tracker.attempt(new Object(), primary, secondary, 4, true); tracker.dialog("oq", "RETURN", 0, primary, secondary, 4); });
        other.start(); other.join(); signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        check(last(tracker).reason() == CreationPathQualification.Reason.OVERLAP, "cross-thread overlapping operation rejects original too");
        tracker.reset(); prepare(tracker, panel, primary, secondary); tracker.attempt(panel, primary, secondary, 4, true);
        Thread orphan = new Thread(() -> tracker.progress("V", "ENTER", -1, primary, primary, secondary, 4)); orphan.start(); orphan.join();
        signals.forEach(s -> accept(tracker, s, primary, secondary, 4));
        check(last(tracker).reason() == CreationPathQualification.Reason.OVERLAP, "progression arriving on another thread cannot be silently ignored");
        tracker.reset();
        var entered = new java.util.concurrent.CountDownLatch(65); var release = new java.util.concurrent.CountDownLatch(1);
        var failures = new java.util.concurrent.ConcurrentLinkedQueue<Throwable>(); var workers = new ArrayList<Thread>();
        Object[] panels = new Object[65];
        for (int i = 0; i < panels.length; i++) { panels[i] = new Object(); prepare(tracker, panels[i], primary, secondary); }
        for (Object ownedPanel : panels) {
            Thread worker = new Thread(() -> {
                try { tracker.attempt(ownedPanel, primary, secondary, 4, true); entered.countDown();
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("owned release timeout");
                    tracker.dialog("oq", "RETURN", 0, primary, secondary, 4);
                } catch (Throwable failure) { failures.add(failure); }
            }); workers.add(worker); worker.start();
        }
        try {
            check(entered.await(8, java.util.concurrent.TimeUnit.SECONDS), "owned capacity workers entered");
            check(tracker.qualificationSnapshot().active() == 64 && tracker.qualificationSnapshot().capacityRejected() == 1, "64active recognition limit explicit, overflow refused");
        } finally { release.countDown(); for (Thread worker : workers) worker.join(11000); }
        check(failures.isEmpty() && workers.stream().noneMatch(Thread::isAlive) && tracker.qualificationSnapshot().active() == 0,
                "owned workers closed, no active recognition leak");
        var guard = new CareerLifecycle();
        try (var probe = HookProbe.attach(event -> { }); var attached = HookProbe.attachLifecycle(guard)) {
            var before = guard.snapshot();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                for (String stage : List.of("ENTER", "SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN", "RETURN"))
                    HookProbe.creationEntry(stage, stage.equals("PANEL_ASSIGNED") ? panel : null, primary, secondary);
                HookProbe.creationEntryAttempt(panel, primary, secondary);
                for (var s : signals) switch (s.kind()) {
                    case "dialog" -> HookProbe.creationDialog(s.method(), s.stage(), s.value(), primary, secondary);
                    case "candidate" -> { if (s.stage().equals("ENTER")) HookProbe.creationPathStarted(s.method(), primary, secondary); else HookProbe.creationPathReturned(s.method(), primary, secondary); }
                    case "step" -> HookProbe.creationPathStep(s.method(), s.stage(), primary, secondary);
                    case "progress" -> HookProbe.creationProgress(s.method(), s.stage(), s.value(), s.stage().equals("ENTER") ? primary : null, primary, secondary);
                }
            });
            check(HookProbe.creationQualifications().attempts().get(0).path().status() == CreationPathQualification.Status.PATH_RETURNED, "actual bridge routes all existing hooks into one online qualifier on EDT");
            check(guard.snapshot().equals(before), "even full strict recognition never activates lifecycle or publishes qualified IO");
        }
        for (String interruption : List.of("schedule", "async-run", "save", "root")) {
            try (var probe = HookProbe.attach(event -> { }); var attached = HookProbe.attachLifecycle(new CareerLifecycle())) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    for (String stage : List.of("ENTER", "SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN", "RETURN"))
                        HookProbe.creationEntry(stage, stage.equals("PANEL_ASSIGNED") ? panel : null, primary, secondary);
                    HookProbe.creationEntryAttempt(panel, primary, secondary);
                    switch (interruption) {
                        case "schedule" -> { HookProbe.asyncScheduleStarted("owned"); HookProbe.asyncScheduleFailed("owned"); }
                        case "async-run" -> { Object task = new Object(); HookProbe.asyncRunStarted("owned", task, primary, secondary); HookProbe.asyncRunFailed("owned", task, primary, secondary); }
                        case "save" -> { HookProbe.enterSave("owned-no-file-written"); HookProbe.abortSave(new IllegalStateException("owned")); }
                        case "root" -> HookProbe.rootAssigned();
                    }
                    HookProbe.creationDialog("oq", "RETURN", 0, primary, secondary);
                });
                var reason = HookProbe.creationQualifications().attempts().get(0).path().reason();
                check(reason == (interruption.equals("root") ? CreationPathQualification.Reason.MUTATION_OBSERVED
                        : interruption.equals("save") ? CreationPathQualification.Reason.SAVE_OBSERVED : CreationPathQualification.Reason.ASYNC_OBSERVED), "actual hook rejects interruption " + interruption);
            }
        }
        System.out.println("PASS: " + checks + " online qualification integration checks; owned objects only, no game creation.");
    }
}
