package br.vbrasfoot;

import java.util.*;

public final class CreationDialogTrackerSelfTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        var tracker = new CreationDialogTracker(); Object a = new Object(), b = new Object(); tracker.reset();
        tracker.marker("pl", "DIALOG_RETURN", -1, a, b, 1); check(tracker.capture().outside() == 1 && tracker.capture().events().isEmpty());
        tracker.marker("oq", "ENTER", 0, a, b, 1); tracker.marker("pl", "ENTER", 0, a, b, 1);
        tracker.marker("pl", "DIALOG_RETURN", -1, a, b, 1); tracker.marker("AB", "CANDIDATE_ENTER", 0, a, b, 1);
        tracker.marker("pl", "RETURN", 0, a, b, 1); tracker.marker("oq", "RETURN", 0, a, b, 1);
        var snapshot = tracker.capture();
        check(snapshot.events().size() == 6 && snapshot.events().stream().allMatch(CreationDialogTracker.Event::rootsMatched));
        check(snapshot.events().get(2).resultKnown() && snapshot.events().get(2).result() == -1);
        check(snapshot.events().get(1).parent() == snapshot.events().get(0).frame());
        check(snapshot.events().get(3).frame() == snapshot.events().get(2).frame());
        tracker.marker("pl", "ENTER", 0, a, b, 1); tracker.marker("pl", "RETURN", 0, a, b, 2);
        check(!tracker.capture().events().get(7).rootsMatched() && snapshot.events().size() == 6);
        tracker.marker("pl", "ENTER", 0, a, b, 1); tracker.marker("pl", "RETURN", 0, new Object(), b, 1);
        check(!tracker.capture().events().get(9).rootsMatched());
        tracker.marker("unknown", "ENTER", 0, a, b, 1); check(tracker.capture().rejected() == 1);
        tracker.marker("pl", "ENTER", 0, a, b, 1); tracker.marker("oq", "RETURN", 0, a, b, 1);
        check(tracker.capture().rejected() == 2);
        tracker.reset();
        for (int i = 0; i < 300; i++) { tracker.marker("pl", "ENTER", 0, a, b, 1); tracker.marker("pl", "RETURN", 0, a, b, 1); }
        check(tracker.capture().events().size() == 512 && tracker.capture().dropped() == 88 && tracker.capture().events().get(0).sequence() == 89);
        tracker.reset(); for (int i = 0; i < 9; i++) tracker.marker("oq", "ENTER", 0, a, b, 1);
        check(tracker.capture().rejected() == 1 && tracker.capture().events().get(8).parent() == 0);
        tracker.reset();
        var entered = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        Thread old = new Thread(() -> { tracker.marker("oq", "ENTER", 0, a, b, 1); entered.countDown();
            try { resume.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            tracker.marker("oq", "RETURN", 0, a, b, 1); });
        old.start(); entered.await(); tracker.reset(); resume.countDown(); old.join();
        check(tracker.capture().events().isEmpty() && tracker.capture().outside() == 1);
        tracker.reset(); tracker.marker("oq", "ENTER", 0, a, b, 1);
        Thread other = new Thread(() -> tracker.marker("AB", "CANDIDATE_ENTER", 0, a, b, 1)); other.start(); other.join();
        check(tracker.capture().events().size() == 1 && tracker.capture().outside() == 1);
        var guard = new CareerLifecycle();
        try (var probe = HookProbe.attach(event -> { }); var attached = HookProbe.attachLifecycle(guard)) {
            var before = guard.snapshot(); HookProbe.creationDialog("pl", "ENTER", 0, a, b);
            HookProbe.creationDialog("pl", "DIALOG_RETURN", 2, a, b); HookProbe.creationDialog("pl", "RETURN", 0, a, b);
            check(before.equals(guard.snapshot()) && HookProbe.creationDialogs().events().size() == 3);
            HookProbe.creationDialog("pl", "ENTER", 0, a, b);
            HookProbe.creationPathStarted("a.bV.AB", a, b); HookProbe.creationPathReturned("a.bV.AB", a, b);
            HookProbe.creationDialog("pl", "RETURN", 0, a, b);
            check(HookProbe.creationDialogs().events().get(4).method().equals("AB") && before.equals(guard.snapshot()));
        }
        System.out.println("PASS: " + checks + " bounded dialog tracker checks; no lifecycle promotion.");
    }
}
