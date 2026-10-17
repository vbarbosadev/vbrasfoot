package br.vbrasfoot;

import java.util.*;

public final class CreationContentTrackerSelfTest {
    public static final class Roots {
        public List<Object> clubs = new ArrayList<>(), humans = new ArrayList<>();
        public List<Object> P() { return clubs; }
        public List<Object> aN() { return humans; }
    }
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static CreationContentTracker.Report report(CreationContentTracker tracker) { return tracker.snapshot().get(0); }
    public static void main(String[] args) {
        String label = "a.bV.AB";
        var tracker = new CreationContentTracker();
        Roots roots = new Roots(); Object secondary = new Object();
        check(tracker.snapshot().size() == 3 && report(tracker).outcome().equals("UNOBSERVED_OR_IN_FLIGHT"), "bounded unobserved labels");
        tracker.enter(label, roots, secondary, 4);
        roots.clubs.add(new Object()); roots.humans.add(roots.clubs.get(0));
        tracker.finish(label, roots, secondary, 4, true);
        var after = report(tracker);
        check(after.starts() == 1 && after.returns() == 1 && after.failures() == 0, "paired counts");
        check(after.rootsStable() && after.listsStable() && after.generationStable(), "identities stable");
        check(after.clubsBefore() == 0 && after.clubsAfter() == 1 && after.humansAfter() == 1 && after.readableAfter(), "in-place population metadata");
        tracker.enter(label, roots, secondary, 4); tracker.finish(label, new Roots(), secondary, 5, true);
        check(!report(tracker).rootsStable() && !report(tracker).generationStable() && !report(tracker).readableAfter(), "root and generation replacement");
        tracker.enter(label, roots, secondary, 5); roots.clubs = new ArrayList<>(roots.clubs); tracker.finish(label, roots, secondary, 5, true);
        check(report(tracker).rootsStable() && !report(tracker).listsStable(), "list replacement separate from root replacement");
        tracker.enter(label, roots, secondary, 5); tracker.finish(label, roots, secondary, 5, false);
        check(report(tracker).failures() == 1 && !report(tracker).readableAfter() && report(tracker).outcome().equals("THREW"), "failure never readiness");
        tracker.enter(label, null, null, -1); tracker.finish(label, null, null, -1, true);
        check(!report(tracker).generationStable() && report(tracker).outcome().equals("RETURNED_WITHOUT_CONTENT"), "missing root or guard explicit");
        tracker.enter(label, new Object(), secondary, 1); tracker.finish(label, new Object(), secondary, 1, true);
        check(report(tracker).outcome().equals("READ_UNAVAILABLE"), "missing getter diagnostic only");
        var immutable = tracker.snapshot(); boolean refused = false;
        try { immutable.clear(); } catch (UnsupportedOperationException expected) { refused = true; }
        check(refused, "immutable metadata");
        for (var field : CreationContentTracker.Report.class.getRecordComponents()) check(field.getType().isPrimitive() || field.getType().equals(String.class), "report has no game graph/class/Throwable");
        tracker.reset(); tracker.enter(label, roots, secondary, 1); tracker.reset(); tracker.finish(label, roots, secondary, 1, true);
        check(report(tracker).returns() == 0 && report(tracker).rejected() == 1, "old session cannot populate new report");
        tracker.reset();
        for (int i = 0; i < 9; i++) tracker.enter(label, roots, secondary, 1);
        for (int i = 0; i < 9; i++) tracker.finish(label, roots, secondary, 1, true);
        check(report(tracker).returns() == 0 && report(tracker).rejected() == 10, "overflow pairing fail closed");
        tracker.enter("untrusted-label", roots, secondary, 1); tracker.finish("untrusted-label", roots, secondary, 1, true);
        check(tracker.snapshot().size() == 3, "unknown labels cannot grow report");
        System.out.println("PASS: " + checks + " creation content metadata checks; synthetic, not initialized-career proof.");
    }
}
