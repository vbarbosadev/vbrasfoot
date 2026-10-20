package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.SwingUtilities;

/** Bounded weak-target samples and numeric Output arguments for witnessed operations.
 * Neither establishes ledger reference encoding, complete mutation coverage or durable effect inclusion. */
final class FinanceSerializationTracker implements AutoCloseable {
    static final int MAX_OPERATIONS = 32;
    private static final Set<String> NUMERIC_ROUTES = Set.of("UnsafeIntField", "UnsafeLongField", "AsmIntField", "AsmLongField", "ObjectIntField", "ObjectLongField");
    enum Sample { MATCHED_EXPECTED_STATE, TARGET_UNAVAILABLE, ROOT_MISMATCH, LEDGER_MISMATCH, STATE_MISMATCH, WRONG_THREAD, READ_FAILED }
    enum Continuity { UNAVAILABLE, OBSERVED_INTERFERENCE, NO_OBSERVED_DIRECT_WRITE }
    enum Argument { IRRELEVANT, MATCHED, MISMATCH }
    enum Emission { MISSING, PARTIAL, EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, VALUE_MISMATCH, AMBIGUOUS, UNAVAILABLE }
    interface Target {
        Sample sample(Object serializedPrimary) throws Exception;
        default Argument emitted(java.lang.reflect.Field field, Object receiver, long value) throws Exception { return Argument.IRRELEVANT; }
    }
    record Fields(Emission status, int observed, Set<String> routes) {
        Fields { routes = Set.copyOf(routes); }
    }
    record Operation(UUID operationId, CareerCommandJournal.Intent intent, Sample before, Sample after, Continuity executionToSerializer, Fields fields) { }
    record Capture(boolean validWindow, boolean capacityExceeded, List<Operation> operations) {
        Capture { operations = List.copyOf(operations); }
        static Capture unavailable() { return new Capture(false, false, List.of()); }
    }
    private record Entry(UUID operationId, CareerCommandJournal.Intent intent, WeakReference<Target> target,
                         FinanceMutationTracker.Snapshot executionEnd) { }
    static final class Window {
        private final UUID tracker;
        private final long revision;
        private final boolean exceeded;
        private final WeakReference<Object> primary;
        private final List<Entry> entries;
        private final List<Operation> before;
        private final List<Set<String>> emitted = new ArrayList<>();
        private final List<Set<String>> routes = new ArrayList<>();
        private final boolean[] mismatched, ambiguous, failed;
        private boolean consumed;
        private Window(UUID tracker, long revision, boolean exceeded, Object primary, List<Entry> entries, List<Operation> before) {
            this.tracker = tracker; this.revision = revision; this.exceeded = exceeded; this.primary = new WeakReference<>(primary);
            this.entries = entries; this.before = before;
            mismatched = new boolean[entries.size()]; ambiguous = new boolean[entries.size()]; failed = new boolean[entries.size()];
            for (int i = 0; i < entries.size(); i++) { emitted.add(new HashSet<>()); routes.add(new HashSet<>()); }
        }
    }
    private final UUID id = UUID.randomUUID();
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private long revision;
    private boolean closed, exceeded;
    synchronized void observe(UUID operation, CareerCommandJournal.Intent intent, Target target, FinanceMutationTracker.Snapshot checkpoint) {
        Objects.requireNonNull(operation); Objects.requireNonNull(intent); Objects.requireNonNull(target);
        if (closed) return;
        if (entries.containsKey(operation) || entries.values().stream().anyMatch(value -> value.intent().key().equals(intent.key()) && value.intent().equals(intent))) {
            exceeded = true; revision++; return; // Ambiguous registration never overwrites original evidence.
        }
        if (entries.size() >= MAX_OPERATIONS) { exceeded = true; revision++; return; }
        entries.put(operation, new Entry(operation, intent, new WeakReference<>(target), checkpoint)); revision++;
    }
    synchronized void invalidate() { revision++; entries.clear(); exceeded = false; }
    Window begin(Object primary, FinanceMutationTracker.Snapshot serializationStart) {
        List<Entry> selected; long expected; boolean overflow;
        synchronized (this) {
            if (closed) return null;
            selected = List.copyOf(entries.values()); expected = revision; overflow = exceeded;
        }
        var operations = new ArrayList<Operation>();
        for (var entry : selected) operations.add(new Operation(entry.operationId(), entry.intent(), sample(entry, primary), Sample.TARGET_UNAVAILABLE,
                continuity(entry.executionEnd(), serializationStart), new Fields(Emission.MISSING, 0, Set.of())));
        return new Window(id, expected, overflow, primary, selected, List.copyOf(operations));
    }
    Capture finish(Window window, Object primary) {
        if (window == null || !id.equals(window.tracker)) return Capture.unavailable();
        synchronized (this) {
            if (closed || window.consumed || revision != window.revision) return Capture.unavailable();
            window.consumed = true;
            if (primary == null || window.primary.get() != primary) return Capture.unavailable();
        }
        var operations = new ArrayList<Operation>();
        for (int i = 0; i < window.entries.size(); i++) {
            var before = window.before.get(i);
            operations.add(new Operation(before.operationId(), before.intent(), before.before(), sample(window.entries.get(i), primary), before.executionToSerializer(), fields(window, i)));
        }
        synchronized (this) { if (closed || revision != window.revision) return Capture.unavailable(); }
        return new Capture(true, window.exceeded, operations);
    }
    void emitted(Window window, java.lang.reflect.Field field, Object receiver, long value, String route) {
        if (window == null || !id.equals(window.tracker)) return;
        for (int i = 0; i < window.entries.size(); i++) {
            synchronized (this) { if (closed || window.consumed || revision != window.revision) return; }
            Argument result = null;
            var target = window.entries.get(i).target().get();
            if (SwingUtilities.isEventDispatchThread() && target != null) {
                try { result = target.emitted(field, receiver, value); } catch (Throwable failure) { /* unavailable below */ }
            }
            synchronized (this) {
                if (closed || window.consumed || revision != window.revision) return;
                if (result == null) { window.failed[i] = true; continue; }
                if (result == Argument.IRRELEVANT) continue;
                try {
                    String key = field.getDeclaringClass().getName() + "." + field.getName();
                    if (!NUMERIC_ROUTES.contains(route) || !(key.equals("best.ah.nb") || field.getDeclaringClass().getName().equals("best.C") && FinanceDelta.FIELDS.contains(field.getName()))) {
                        window.failed[i] = true; continue;
                    }
                    if (!window.emitted.get(i).add(key)) window.ambiguous[i] = true;
                    // Only the six bounded, fingerprinted route names can reach this internal observer.
                    window.routes.get(i).add(route);
                    if (result != Argument.MATCHED) window.mismatched[i] = true;
                } catch (Throwable failure) { window.failed[i] = true; }
            }
        }
    }
    synchronized void emissionFailed(Window window) {
        if (!closed && window != null && id.equals(window.tracker) && !window.consumed && revision == window.revision) Arrays.fill(window.failed, true);
    }
    private static Fields fields(Window window, int i) {
        int count = window.emitted.get(i).size();
        Emission status = window.failed[i] ? Emission.UNAVAILABLE : window.ambiguous[i] ? Emission.AMBIGUOUS
                : window.mismatched[i] ? Emission.VALUE_MISMATCH : count == 15 ? Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED
                : count == 0 ? Emission.MISSING : Emission.PARTIAL;
        return new Fields(status, count, window.routes.get(i));
    }
    private static Sample sample(Entry entry, Object primary) {
        if (!SwingUtilities.isEventDispatchThread()) return Sample.WRONG_THREAD;
        var target = entry.target().get(); if (target == null) return Sample.TARGET_UNAVAILABLE;
        try { var result = target.sample(primary); return result == null ? Sample.READ_FAILED : result; }
        catch (Throwable failure) { return Sample.READ_FAILED; }
    }
    private static Continuity continuity(FinanceMutationTracker.Snapshot execution, FinanceMutationTracker.Snapshot serialization) {
        if (execution == null || serialization == null || !execution.tracker().equals(serialization.tracker())
                || !execution.healthy() || !serialization.healthy() || !execution.definingClassesObserved() || !serialization.definingClassesObserved()) return Continuity.UNAVAILABLE;
        return serialization.stableSince(execution) ? Continuity.NO_OBSERVED_DIRECT_WRITE : Continuity.OBSERVED_INTERFERENCE;
    }
    @Override public synchronized void close() { closed = true; invalidate(); }
}
