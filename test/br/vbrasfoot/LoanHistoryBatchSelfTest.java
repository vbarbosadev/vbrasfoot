package br.vbrasfoot;

import java.util.*;

/** Synthetic graph: complete checks in one pass, never sampling or cross-request caching. */
public final class LoanHistoryBatchSelfTest {
    record Player(int id, String name, Object club) { }
    record Tracker(Object player, Object origin, long due) { }
    static final class Graph implements LoanHistoryBatch.Access {
        Object origin = new Object(), destination = new Object();
        List<Object> home = new ArrayList<>(), away = new ArrayList<>(), juniors = new ArrayList<>(), trackers = new ArrayList<>();
        int reads;
        public List<?> clubs() { return List.of(origin,destination); }
        public List<?> roster(Object club, boolean junior) { return junior ? juniors : club == origin ? home : away; }
        public int id(Object p) { reads++; return ((Player)p).id(); }
        public String name(Object p) { return ((Player)p).name(); }
        public Object club(Object p) { return ((Player)p).club(); }
        public List<?> trackers() { return trackers; }
        public Object trackedPlayer(Object t) { return ((Tracker)t).player(); }
        public Object origin(Object t) { return ((Tracker)t).origin(); }
        public long due(Object t) { return ((Tracker)t).due(); }
    }
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        var g = new Graph(); var retired = new Player(1,"Retired",g.origin); var active = new Player(2,"Active",g.destination);
        g.home.add(retired); g.away.add(active);
        for (int i=3;i<400;i++) g.home.add(new Player(i,"Other",g.origin));
        var r = new LoanHistoryBatch.Expected(1,"Retired",g.origin,g.origin,0,true);
        var a = new LoanHistoryBatch.Expected(2,"Active",g.destination,g.origin,366,false);
        var expected = List.of(a,r); var tracker = new Tracker(active,g.origin,366); g.trackers.add(tracker);
        check(LoanHistoryBatch.invalid(expected,g).isEmpty(), "mixed active/retired validated together");
        check(g.reads == 399, "one id read per player regardless of two targets");
        g.trackers.clear(); check(LoanHistoryBatch.invalid(expected,g).equals(List.of(a)), "missing active tracker refused");
        g.trackers.add(tracker); g.trackers.add(tracker);
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(a)), "duplicate tracker refused");
        g.trackers.clear(); g.trackers.add(new Tracker(active,g.origin,367));
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(a)), "wrong due date refused");
        g.trackers.clear(); g.trackers.add(new Tracker(active,g.destination,366));
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(a)), "wrong tracker origin refused");
        g.trackers.clear(); g.trackers.add(tracker); g.trackers.add(new Tracker(retired,g.origin,366));
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "retired tracker appearance refuses restored historical effect");
        g.trackers.remove(1); g.away.add(retired);
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "duplicate membership refused");
        g.away.remove(retired); g.home.add(new Player(1,"Other name",g.origin));
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "duplicate id even with other name refused");
        g.home.remove(g.home.size()-1); g.home.remove(retired); g.away.add(retired);
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "retired player at wrong club refused");
        g.away.remove(retired); g.home.add(new Player(1,"Wrong name",g.origin));
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "name mismatch refused");
        g.home.remove(g.home.size()-1); g.juniors.add(retired);
        check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "junior membership not accepted as professional");
        g.juniors.clear(); check(LoanHistoryBatch.invalid(expected,g).equals(List.of(r)), "missing player refused");
        int previous = g.reads; check(LoanHistoryBatch.invalid(List.of(),g).isEmpty() && previous == g.reads, "empty history does not scan world");
        var atOrigin = new Player(2,"Active",g.origin);
        g.home.add(atOrigin); g.away.remove(active); g.trackers.clear(); g.home.add(retired);
        var returned = new LoanHistoryBatch.Expected(2,"Active",g.destination,g.origin,366,false,true);
        check(LoanHistoryBatch.invalid(List.of(returned,r),g).isEmpty(), "qualified return uniquely at origin without tracker is closed");
        check(LoanHistoryBatch.invalid(List.of(a,r),g).equals(List.of(a)), "origin without native return evidence remains blocked");
        g.trackers.add(new Tracker(atOrigin,g.origin,366));
        check(LoanHistoryBatch.invalid(List.of(returned,r),g).equals(List.of(returned)), "return history with lingering tracker remains blocked");
        g.trackers.clear(); g.home.remove(atOrigin); g.away.add(active);
        check(LoanHistoryBatch.invalid(List.of(returned,r),g).equals(List.of(returned)), "return evidence but player still at destination is inconsistent");
        try { LoanHistoryBatch.invalid(List.of(a,a),g); throw new AssertionError("Expected overlap refusal"); }
        catch (IllegalStateException expectedFailure) { checks++; }
        System.out.println("PASS: " + checks + " single-pass history checks; 399 id reads instead of 798 for two targets");
    }
}
