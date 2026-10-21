package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.SwingUtilities;

/** Bounded weak-target samples, numeric Output arguments and ledger arguments for witnessed operations.
 * Separate memory-only scalar reference-table observations; no byte-range decoding,
 * complete mutation coverage or durable effect inclusion. */
final class FinanceSerializationTracker implements AutoCloseable {
    static final int MAX_OPERATIONS = 32;
    private static final Set<String> NUMERIC_ROUTES = Set.of("UnsafeIntField", "UnsafeLongField", "AsmIntField", "AsmLongField", "ObjectIntField", "ObjectLongField");
    private static final Set<String> REFERENCE_ROUTES = Set.of("writeObject", "writeObjectOrNull", "writeClass(null)");
    enum Sample { MATCHED_EXPECTED_STATE, TARGET_UNAVAILABLE, ROOT_MISMATCH, LEDGER_MISMATCH, STATE_MISMATCH, WRONG_THREAD, READ_FAILED }
    enum Continuity { UNAVAILABLE, OBSERVED_INTERFERENCE, NO_OBSERVED_DIRECT_WRITE }
    enum Argument { IRRELEVANT, MATCHED, MISMATCH }
    enum Emission { MISSING, PARTIAL, EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, VALUE_MISMATCH, AMBIGUOUS, UNAVAILABLE }
    enum Reference { MISSING, EXPECTED_LEDGER_ARGUMENT_OBSERVED, REFERENCE_MISMATCH, AMBIGUOUS, UNAVAILABLE, UNSUPPORTED_SERIALIZER }
    enum SerializerKind { FIELD_SERIALIZER, NONE, OTHER }
    record SampleFact(Sample sample, Optional<FinanceLedgerMembership.Status> membership) {
        SampleFact { Objects.requireNonNull(sample); Objects.requireNonNull(membership); }
        static SampleFact unavailable(Sample sample) { return new SampleFact(sample, Optional.empty()); }
    }
    interface Target {
        Sample sample(Object serializedPrimary) throws Exception;
        default SampleFact sampleFact(Object serializedPrimary) throws Exception { return SampleFact.unavailable(sample(serializedPrimary)); }
        default Argument emitted(java.lang.reflect.Field field, Object receiver, long value) throws Exception { return Argument.IRRELEVANT; }
        default Argument referenced(java.lang.reflect.Field field, Object receiver, Object value) throws Exception { return Argument.IRRELEVANT; }
        default boolean tracksLedger(Object value) { return false; }
    }
    record Fields(Emission status, int observed, Set<String> routes) {
        Fields { routes = Set.copyOf(routes); }
    }
    record Edge(Reference status, int observed, Set<String> routes) {
        Edge { routes = Set.copyOf(routes); }
        static Edge missing() { return new Edge(Reference.MISSING, 0, Set.of()); }
    }
    record Operation(UUID operationId, CareerCommandJournal.Intent intent, Sample before, Sample after, Continuity executionToSerializer, Fields fields, Edge edge,
                     FinanceQualificationManifest qualifications) {
        Operation { Objects.requireNonNull(qualifications); }
        Operation(UUID id, CareerCommandJournal.Intent intent, Sample before, Sample after, Continuity continuity, Fields fields, Edge edge) {
            this(id, intent, before, after, continuity, fields, edge, FinanceQualificationManifest.legacy());
        }
    }
    record ReferenceTrace(UUID operationId, KryoLedgerReferenceTrace.Summary summary) { }
    record Capture(boolean validWindow, boolean capacityExceeded, List<Operation> operations, List<ReferenceTrace> referenceTraces) {
        Capture { operations = List.copyOf(operations); referenceTraces = List.copyOf(referenceTraces); }
        Capture(boolean valid, boolean exceeded, List<Operation> operations) { this(valid, exceeded, operations, List.of()); }
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
        private final Edge[] edges;
        private final List<Optional<LedgerSerializerProfile.Status>> profiles;
        private final List<KryoLedgerReferenceTrace> references;
        private boolean consumed;
        private Window(UUID tracker, long revision, boolean exceeded, Object primary, List<Entry> entries, List<Operation> before) {
            this.tracker = tracker; this.revision = revision; this.exceeded = exceeded; this.primary = new WeakReference<>(primary);
            this.entries = entries; this.before = before;
            mismatched = new boolean[entries.size()]; ambiguous = new boolean[entries.size()]; failed = new boolean[entries.size()];
            edges = new Edge[entries.size()]; Arrays.fill(edges, Edge.missing());
            profiles = new ArrayList<>(Collections.nCopies(entries.size(), Optional.empty()));
            references = new ArrayList<>(); for (int i = 0; i < entries.size(); i++) references.add(new KryoLedgerReferenceTrace());
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
        for (var entry : selected) {
            var fact = sample(entry, primary);
            operations.add(new Operation(entry.operationId(), entry.intent(), fact.sample(), Sample.TARGET_UNAVAILABLE,
                    continuity(entry.executionEnd(), serializationStart), new Fields(Emission.MISSING, 0, Set.of()), Edge.missing(),
                    FinanceQualificationManifest.sampled(fact.membership(), Optional.empty(), Optional.empty())));
        }
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
            var after = sample(window.entries.get(i), primary);
            operations.add(new Operation(before.operationId(), before.intent(), before.before(), after.sample(), before.executionToSerializer(), fields(window, i), window.edges[i],
                    FinanceQualificationManifest.sampled(before.qualifications().membershipBefore(), after.membership(), window.profiles.get(i))));
        }
        synchronized (this) { if (closed || revision != window.revision) return Capture.unavailable(); }
        var references = new ArrayList<ReferenceTrace>();
        synchronized (this) {
            if (closed || revision != window.revision) return Capture.unavailable();
            for (int i = 0; i < window.entries.size(); i++) references.add(new ReferenceTrace(window.entries.get(i).operationId(), window.references.get(i).snapshot()));
        }
        return new Capture(true, window.exceeded, operations, references);
    }
    static final class ReferenceCall {
        private final Window window;
        private final List<KryoLedgerReferenceTrace.Call> calls;
        private ReferenceCall(Window window, List<KryoLedgerReferenceTrace.Call> calls) { this.window = window; this.calls = calls; }
    }
    ReferenceCall beginReferenceCall(Window window, java.lang.reflect.Field field, Object receiver, Object value) {
        if (window == null || !id.equals(window.tracker)) return null;
        var calls = new ArrayList<KryoLedgerReferenceTrace.Call>(); boolean selected = false;
        for (int i = 0; i < window.entries.size(); i++) {
            synchronized (this) { if (closed || window.consumed || revision != window.revision) return null; }
            Argument result = null; var target = window.entries.get(i).target().get();
            if (SwingUtilities.isEventDispatchThread() && target != null) try { result = target.referenced(field, receiver, value); } catch (Throwable ignored) { }
            synchronized (this) {
                if (closed || window.consumed || revision != window.revision) return null;
                if (result == null) window.references.get(i).fail();
                var call = result == Argument.MATCHED ? window.references.get(i).beginField() : null;
                calls.add(call); selected |= call != null;
            }
        }
        return selected ? new ReferenceCall(window, Collections.unmodifiableList(calls)) : null;
    }
    synchronized void endReferenceCall(ReferenceCall call) {
        if (call == null || !id.equals(call.window.tracker)) return;
        var window = call.window; if (closed || window.consumed || revision != window.revision) return;
        for (int i = 0; i < call.calls.size(); i++) if (call.calls.get(i) != null) window.references.get(i).endField(call.calls.get(i));
    }
    void referenceMarker(Window window, Object ledger, int kind, int referenceId, int marker, boolean supportedResolver) {
        referenceEvent(window, ledger, trace -> trace.marker(kind, referenceId, marker, supportedResolver));
    }
    void referenceContent(Window window, Object ledger, boolean supportedSerializer) {
        referenceEvent(window, ledger, trace -> trace.contentReturned(supportedSerializer));
    }
    private void referenceEvent(Window window, Object ledger, java.util.function.Consumer<KryoLedgerReferenceTrace> event) {
        if (window == null || !id.equals(window.tracker)) return;
        for (int i = 0; i < window.entries.size(); i++) {
            synchronized (this) { if (closed || window.consumed || revision != window.revision) return; }
            var target = window.entries.get(i).target().get(); Boolean selected = null;
            if (SwingUtilities.isEventDispatchThread() && target != null) try { selected = target.tracksLedger(ledger); } catch (Throwable ignored) { }
            synchronized (this) {
                if (closed || window.consumed || revision != window.revision) return;
                if (selected == null) window.references.get(i).fail();
                else if (selected) event.accept(window.references.get(i));
            }
        }
    }
    synchronized void referenceReset(Window window) {
        if (closed || window == null || !id.equals(window.tracker) || window.consumed || revision != window.revision) return;
        for (var trace : window.references) trace.reset();
    }
    synchronized void referenceResetCompleted(Window window) {
        if (closed || window == null || !id.equals(window.tracker) || window.consumed || revision != window.revision) return;
        for (var trace : window.references) trace.resetCompleted();
    }
    synchronized void referenceTableFailed(Window window) {
        if (closed || window == null || !id.equals(window.tracker) || window.consumed || revision != window.revision) return;
        for (var trace : window.references) trace.fail();
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
    void referenced(Window window, java.lang.reflect.Field field, Object receiver, Object value, String route, SerializerKind serializer) {
        referenced(window, field, receiver, value, route, serializer, Optional.empty());
    }
    void referenced(Window window, java.lang.reflect.Field field, Object receiver, Object value, String route, SerializerKind serializer,
                    Optional<LedgerSerializerProfile.Status> profile) {
        if (window == null || !id.equals(window.tracker)) return;
        for (int i = 0; i < window.entries.size(); i++) {
            synchronized (this) { if (closed || window.consumed || revision != window.revision) return; }
            Argument result = null; var target = window.entries.get(i).target().get();
            if (SwingUtilities.isEventDispatchThread() && target != null) {
                try { result = target.referenced(field, receiver, value); } catch (Throwable failure) { /* unavailable below */ }
            }
            synchronized (this) {
                if (closed || window.consumed || revision != window.revision) return;
                if (result == Argument.IRRELEVANT) continue;
                var prior = window.edges[i];
                boolean supported = route != null && REFERENCE_ROUTES.contains(route);
                boolean validField = field != null && field.getDeclaringClass().getName().equals("best.ah") && field.getName().equals("nx") && field.getType().getName().equals("best.C");
                var routes = new HashSet<>(prior.routes()); if (supported) routes.add(route);
                Reference status = result == null || !validField || !supported || serializer == null || prior.status() == Reference.UNAVAILABLE ? Reference.UNAVAILABLE
                        : prior.observed() > 0 ? Reference.AMBIGUOUS : result != Argument.MATCHED ? Reference.REFERENCE_MISMATCH
                        : serializer != SerializerKind.FIELD_SERIALIZER ? Reference.UNSUPPORTED_SERIALIZER : Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED;
                window.edges[i] = new Edge(status, Math.min(2, prior.observed() + (result == null ? 0 : 1)), routes);
                window.profiles.set(i, result != null && validField && supported && prior.observed() == 0 && prior.status() != Reference.UNAVAILABLE
                        && profile != null ? profile : Optional.empty());
            }
        }
    }
    synchronized void referenceFailed(Window window) {
        if (closed || window == null || !id.equals(window.tracker) || window.consumed || revision != window.revision) return;
        for (int i = 0; i < window.edges.length; i++) {
            window.edges[i] = new Edge(Reference.UNAVAILABLE, window.edges[i].observed(), window.edges[i].routes());
            window.profiles.set(i, Optional.empty());
        }
    }
    private static Fields fields(Window window, int i) {
        int count = window.emitted.get(i).size();
        Emission status = window.failed[i] ? Emission.UNAVAILABLE : window.ambiguous[i] ? Emission.AMBIGUOUS
                : window.mismatched[i] ? Emission.VALUE_MISMATCH : count == 15 ? Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED
                : count == 0 ? Emission.MISSING : Emission.PARTIAL;
        return new Fields(status, count, window.routes.get(i));
    }
    private static SampleFact sample(Entry entry, Object primary) {
        if (!SwingUtilities.isEventDispatchThread()) return SampleFact.unavailable(Sample.WRONG_THREAD);
        var target = entry.target().get(); if (target == null) return SampleFact.unavailable(Sample.TARGET_UNAVAILABLE);
        try { var result = target.sampleFact(primary); return result == null ? SampleFact.unavailable(Sample.READ_FAILED) : result; }
        catch (Throwable failure) { return SampleFact.unavailable(Sample.READ_FAILED); }
    }
    private static Continuity continuity(FinanceMutationTracker.Snapshot execution, FinanceMutationTracker.Snapshot serialization) {
        if (execution == null || serialization == null || !execution.tracker().equals(serialization.tracker())
                || !execution.healthy() || !serialization.healthy() || !execution.definingClassesObserved() || !serialization.definingClassesObserved()) return Continuity.UNAVAILABLE;
        return serialization.stableSince(execution) ? Continuity.NO_OBSERVED_DIRECT_WRITE : Continuity.OBSERVED_INTERFERENCE;
    }
    @Override public synchronized void close() { closed = true; invalidate(); }
}
