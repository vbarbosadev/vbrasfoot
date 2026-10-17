package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded diagnostic metadata only. Never activates lifecycle or retains a game graph. */
final class CreationContentTracker {
    private static final List<String> LABELS = List.of("a.bV.AB", "a.cR.AC", "a.cR.pn");
    record Report(String label, long starts, long returns, long failures, long rejected,
                  boolean rootsStable, boolean listsStable, boolean generationStable,
                  int clubsBefore, int humansBefore, int clubsAfter, int humansAfter,
                  boolean readableAfter, String outcome) { }
    record Step(String label, String stage, long generation, int clubs, int humans,
                int dayIndex, int year, int month, int day, boolean calendarAvailable) { }
    private static final Set<String> STAGES = Set.of("before-az", "after-az", "before-V", "after-V");
    private static final class Counts {
        long starts, returns, failures, rejected;
        Report latest;
        List<Step> latestSteps = List.of();
    }
    private record Sample(Object clubs, Object humans, int clubCount, int humanCount, boolean readable) { }
    private record Entry(String label, long session, long generation, WeakReference<Object> primary,
                         WeakReference<Object> secondary, WeakReference<Object> clubs,
                         WeakReference<Object> humans, int clubCount, int humanCount, List<Step> steps) { }
    private final Map<String, Counts> counts = new LinkedHashMap<>();
    private final ThreadLocal<ArrayDeque<Entry>> entries = ThreadLocal.withInitial(ArrayDeque::new);
    private long session;
    CreationContentTracker() { reset(); }
    synchronized void reset() {
        session++;
        counts.clear();
        for (String label : LABELS) counts.put(label, new Counts());
        entries.remove();
    }
    private static Sample sample(Object primary, Object secondary) throws Exception {
        if (primary == null || secondary == null) return new Sample(null, null, -1, -1, false);
        Object clubs = CareerBridge.call(primary, "P"), humans = CareerBridge.call(primary, "aN");
        if (!(clubs instanceof List<?> clubList) || !(humans instanceof List<?> humanList)) return new Sample(clubs, humans, -1, -1, false);
        int clubCount = clubList.size(), humanCount = humanList.size();
        return new Sample(clubs, humans, clubCount, humanCount, clubCount > 0 && humanCount > 0);
    }
    void enter(String label, Object primary, Object secondary, long generation) {
        long owner;
        synchronized (this) {
            Counts value = counts.get(label);
            if (value == null) return;
            value.starts++;
            owner = session;
        }
        ArrayDeque<Entry> stack = entries.get();
        if (stack.size() >= 8) {
            // Discard pairing evidence rather than pairing an inner return with an outer start.
            stack.clear(); entries.remove();
            synchronized (this) { counts.get(label).rejected++; }
            return;
        }
        Sample before;
        try { before = sample(primary, secondary); }
        catch (Exception | LinkageError unavailable) { before = new Sample(null, null, -1, -1, false); }
        stack.push(new Entry(label, owner, generation, new WeakReference<>(primary), new WeakReference<>(secondary),
                new WeakReference<>(before.clubs()), new WeakReference<>(before.humans()), before.clubCount(), before.humanCount(), new ArrayList<>()));
    }
    void step(String label, String stage, Object primary, Object secondary, long generation) {
        if (!LABELS.contains(label) || !STAGES.contains(stage)) return;
        ArrayDeque<Entry> stack = entries.get();
        if (stack.isEmpty()) { entries.remove(); return; }
        Entry entry = stack.peek();
        synchronized (this) {
            if (!entry.label().equals(label) || entry.session() != session || entry.steps().size() >= 8) { counts.get(label).rejected++; return; }
        }
        Sample value;
        try { value = sample(primary, secondary); } catch (Exception | LinkageError unavailable) { value = new Sample(null, null, -1, -1, false); }
        int index = -1, year = 0, month = 0, day = 0; boolean available = false;
        if (primary != null) {
            try {
                index = ((Number) CareerBridge.call(primary, "J")).intValue();
                Object calendar = CareerBridge.call(primary, "bb");
                if (calendar instanceof java.util.Calendar source) {
                    var date = CalendarSnapshots.copy(source);
                    year = date.year(); month = date.month(); day = date.day(); available = true;
                }
            } catch (Exception | LinkageError unavailable) { /* Report unavailable scalar fields, never fabricate a date. */ }
        }
        synchronized (this) {
            if (entry.session() != session) { counts.get(label).rejected++; return; }
            entry.steps().add(new Step(label, stage, generation, value.clubCount(), value.humanCount(), index, year, month, day, available));
        }
    }
    void finish(String label, Object primary, Object secondary, long generation, boolean returned) {
        if (!LABELS.contains(label)) return;
        ArrayDeque<Entry> stack = entries.get();
        if (stack.isEmpty() || !stack.peek().label().equals(label)) {
            synchronized (this) { counts.get(label).rejected++; }
            if (stack.isEmpty()) entries.remove();
            return;
        }
        Entry before = stack.pop(); if (stack.isEmpty()) entries.remove();
        Sample after;
        String outcome = returned ? "RETURNED_WITH_CONTENT" : "THREW";
        try { after = sample(primary, secondary); }
        catch (Exception | LinkageError unavailable) { after = new Sample(null, null, -1, -1, false); outcome = "READ_UNAVAILABLE"; }
        if (returned && !after.readable() && !outcome.equals("READ_UNAVAILABLE")) outcome = "RETURNED_WITHOUT_CONTENT";
        synchronized (this) {
            if (before.session() != session) { counts.get(label).rejected++; return; }
            Counts value = counts.get(label);
            value.latestSteps = List.copyOf(before.steps());
            if (returned) value.returns++; else value.failures++;
            boolean rootsStable = primary != null && secondary != null && before.primary().get() == primary && before.secondary().get() == secondary;
            boolean listsStable = after.clubs() != null && after.humans() != null && before.clubs().get() == after.clubs() && before.humans().get() == after.humans();
            value.latest = new Report(label, value.starts, value.returns, value.failures, value.rejected,
                    rootsStable, listsStable, before.generation() >= 0 && before.generation() == generation,
                    before.clubCount(), before.humanCount(), after.clubCount(), after.humanCount(), returned && after.readable(), outcome);
        }
    }
    synchronized List<Report> snapshot() {
        List<Report> result = new ArrayList<>();
        counts.forEach((label, value) -> {
            Report previous = value.latest;
            result.add(previous == null ? new Report(label, value.starts, value.returns, value.failures, value.rejected,
                    false, false, false, -1, -1, -1, -1, false, "UNOBSERVED_OR_IN_FLIGHT")
                    : new Report(label, value.starts, value.returns, value.failures, value.rejected,
                    previous.rootsStable(), previous.listsStable(), previous.generationStable(), previous.clubsBefore(), previous.humansBefore(),
                    previous.clubsAfter(), previous.humansAfter(), previous.readableAfter(), previous.outcome()));
        });
        return List.copyOf(result);
    }
    synchronized List<Step> steps() {
        List<Step> result = new ArrayList<>();
        counts.values().forEach(value -> result.addAll(value.latestSteps));
        return List.copyOf(result);
    }
}
