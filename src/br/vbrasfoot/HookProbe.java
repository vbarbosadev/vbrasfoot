package br.vbrasfoot;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Internal diagnostic bridge, NOT a public mod event API. All injected calls are best-effort. */
public final class HookProbe {
    public record Observation(String operation, String file, boolean completed, boolean backup,
                              boolean returnedSuccess, boolean fallback, boolean aborted, String thread, long sequence) { }
    private static final AtomicReference<Consumer<Observation>> observer = new AtomicReference<>();
    private static final AtomicReference<CareerLifecycle> lifecycle = new AtomicReference<>();
    private static final AtomicReference<CareerIoInterlock> ioInterlock = new AtomicReference<>();
    private record IoFrame(CareerIoInterlock owner, CareerIoInterlock.Entry entry) { }
    private static final ThreadLocal<ArrayDeque<IoFrame>> originalIo = ThreadLocal.withInitial(ArrayDeque::new);
    private static int originalIoActive;
    private static final AtomicLong sequence = new AtomicLong();
    private static final CreationContentTracker creationContent = new CreationContentTracker();
    private static final AsyncCreationTracker asyncCreation = new AsyncCreationTracker();
    private static final CreationDialogTracker creationDialogs = new CreationDialogTracker();
    private static final CreationEntryTracker creationEntries = new CreationEntryTracker();
    private static final DecodedSaveProvenance decodedProvenance = new DecodedSaveProvenance();
    private static final WrittenSaveProvenance writtenProvenance = new WrittenSaveProvenance();
    private static final FinanceMutationTracker financeMutations = new FinanceMutationTracker();
    private static final AtomicReference<FinanceSerializationTracker> financeSerialization = new AtomicReference<>();
    private static final ThreadLocal<ArrayDeque<Load>> loads = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Save>> saves = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Reset>> resets = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Load {
        String name, actualName;
        boolean backup, decoded, actualBackup, successful, initializedPath, actualInitializedPath;
        CareerLifecycle.Ticket decodedTicket, actualTicket;
        CareerLifecycle.Ticket assignedTicket;
        CareerLifecycle.Attempt attempt;
        java.nio.file.Path source, actualSource;
        LoadInputTrace trace;
        LoadInputTrace.Result actualStream;
        Object decodedPrimary, decodedSecondary, actualPrimary, actualSecondary;
        long provenanceRevision, actualProvenanceRevision;
        Load(String name, boolean backup) { this.name = name; this.backup = backup; }
    }
    private static final class Reset { CareerLifecycle.Attempt attempt; }
    private static final class Save {
        String name;
        boolean metadata, closed, backup, streamClosed, streamCloseFailed;
        Object output;
        CareerLifecycle.SaveAttempt attempt;
        CareerLifecycle.Ticket ticket;
        long evidenceRevision;
        java.nio.file.Path destination;
        SaveOutputTrace trace;
        java.lang.ref.WeakReference<Object> serializedPrimary, serializedSecondary;
        int rootsStarted, rootsFinished, destinations;
        boolean sourceRejected;
        FinanceMutationTracker.Snapshot financeBefore, financeAfter;
        FinanceSerializationTracker serializationTracker;
        FinanceSerializationTracker.Window serializationWindow;
        FinanceSerializationTracker.Capture operationCapture = FinanceSerializationTracker.Capture.unavailable();
        Save(String name) { this.name = name; }
    }
    private HookProbe() { }
    static void financeInstrumented(Class<?> type) { financeMutations.instrumented(type.getName(), type.getClassLoader()); }
    static void financeKryoInstrumented(Class<?> type) { financeMutations.kryoInstrumented(type.getName(), type.getClassLoader()); }
    static FinanceMutationTracker.Snapshot financeMutationSnapshot(ClassLoader runtime) { return financeMutations.snapshot(runtime); }
    static AutoCloseable attachFinancialSerialization(FinanceSerializationTracker tracker) {
        java.util.Objects.requireNonNull(tracker);
        if (!financeSerialization.compareAndSet(null, tracker)) throw new IllegalStateException("Financial serialization tracker already attached");
        return () -> { if (financeSerialization.compareAndSet(tracker, null)) tracker.close(); };
    }
    static void observeFinancialSerialization(java.util.UUID operation, CareerCommandJournal.Intent intent, FinanceSerializationTracker.Target target,
                                              FinanceMutationTracker.Snapshot checkpoint) {
        var tracker = financeSerialization.get(); if (tracker != null) tracker.observe(operation, intent, target, checkpoint);
    }
    private static void invalidateFinancialSerialization() { var tracker = financeSerialization.get(); if (tracker != null) tracker.invalidate(); }
    /** Called after a fingerprinted numeric cached-field Output write returned, never on read/copy. */
    public static void financeFieldEmitted(Object cachedField, Object receiver, Object output, long value) {
        Save frame = null;
        try {
            if (receiver == null || !(receiver.getClass().getName().equals("best.ah") || receiver.getClass().getName().equals("best.C"))) return;
            frame = saves.get().peek();
            if (frame == null) { saves.remove(); return; }
            if (frame.serializationTracker == null || frame.serializationWindow == null || frame.rootsStarted != frame.rootsFinished + 1) return;
            // Attribute to this writer's traced stream, not a nested/custom Output on the same thread.
            if (output == null || output.getClass().getMethod("getOutputStream").invoke(output) != frame.trace || frame.trace == null) {
                frame.serializationTracker.emissionFailed(frame.serializationWindow); return;
            }
            ClassLoader loader = receiver.getClass().getClassLoader();
            if (cachedField == null || cachedField.getClass().getClassLoader() != loader || output.getClass().getClassLoader() != loader) {
                frame.serializationTracker.emissionFailed(frame.serializationWindow); return;
            }
            var base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", false, loader);
            var field = (java.lang.reflect.Field) base.getMethod("getField").invoke(cachedField);
            frame.serializationTracker.emitted(frame.serializationWindow, field, receiver, value, cachedField.getClass().getSimpleName());
        } catch (Throwable ignored) {
            if (frame != null && frame.serializationTracker != null) frame.serializationTracker.emissionFailed(frame.serializationWindow);
        }
    }
    /** Exact reference/serializer arguments, observed after ObjectField's original Kryo call returned. */
    public static void financeReferenceEmitted(Object cachedField, Object receiver, Object output, Object value, Object serializer, String route) {
        Save frame = null;
        try {
            if (receiver == null || !receiver.getClass().getName().equals("best.ah")) return;
            frame = saves.get().peek(); if (frame == null) { saves.remove(); return; }
            if (frame.serializationTracker == null || frame.serializationWindow == null || frame.rootsStarted != frame.rootsFinished + 1) return;
            ClassLoader loader = receiver.getClass().getClassLoader();
            if (cachedField == null || cachedField.getClass().getClassLoader() != loader || output == null || output.getClass().getClassLoader() != loader
                    || frame.trace == null || output.getClass().getMethod("getOutputStream").invoke(output) != frame.trace) {
                frame.serializationTracker.referenceFailed(frame.serializationWindow); return;
            }
            var base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", false, loader);
            var field = (java.lang.reflect.Field) base.getMethod("getField").invoke(cachedField);
            var profile = LedgerSerializerProfile.inspect(serializer, value, loader);
            var kind = serializer == null ? FinanceSerializationTracker.SerializerKind.NONE
                    : profile == LedgerSerializerProfile.Status.SUPPORTED_SAMPLED_LAYOUT
                    ? FinanceSerializationTracker.SerializerKind.FIELD_SERIALIZER : FinanceSerializationTracker.SerializerKind.OTHER;
            frame.serializationTracker.referenced(frame.serializationWindow, field, receiver, value, route, kind, java.util.Optional.of(profile));
        } catch (Throwable ignored) {
            try { if (frame != null && frame.serializationTracker != null) frame.serializationTracker.referenceFailed(frame.serializationWindow); }
            catch (Throwable alsoIgnored) { /* Diagnostic failure must not escape into the original serializer. */ }
        }
    }
    public static void financeMutationStarting() {
        try { financeMutations.begin(); } catch (Throwable ignored) { try { financeMutations.failed(); } catch (Throwable alsoIgnored) { } }
    }
    public static void financeMutationFinished() {
        try { financeMutations.end(); } catch (Throwable ignored) { try { financeMutations.failed(); } catch (Throwable alsoIgnored) { } }
    }
    public static boolean financeKryoMutationStarting(Object cachedField, Object receiver) {
        try {
            if (receiver == null || !(receiver.getClass().getName().equals("best.ah") || receiver.getClass().getName().equals("best.C"))) return false;
            var loader = receiver.getClass().getClassLoader();
            if (cachedField == null || cachedField.getClass().getClassLoader() != loader) { financeMutations.failed(); return false; }
            var base = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializer$CachedField", false, loader);
            var field = (java.lang.reflect.Field) base.getMethod("getField").invoke(cachedField);
            if (field == null) { financeMutations.failed(); return false; }
            boolean watched = field.getDeclaringClass().getName().equals("best.ah")
                    ? field.getName().equals("nb") && field.getType() == long.class || field.getName().equals("nx") && field.getType().getName().equals("best.C")
                    : field.getDeclaringClass().getName().equals("best.C") && FinanceDelta.FIELDS.contains(field.getName()) && (field.getType() == int.class || field.getType() == long.class);
            if (!watched) return false;
            if (field.getDeclaringClass() != receiver.getClass()) { financeMutations.failed(); return false; }
            financeMutations.beginKryo(); return true;
        } catch (Throwable ignored) { try { financeMutations.failed(); } catch (Throwable alsoIgnored) { } return false; }
    }
    public static void financeKryoMutationFinished(boolean started) {
        if (started) try { financeMutations.endKryo(); } catch (Throwable ignored) { try { financeMutations.failed(); } catch (Throwable alsoIgnored) { } }
    }

    static synchronized AutoCloseable attachIoInterlock(CareerIoInterlock interlock) {
        java.util.Objects.requireNonNull(interlock);
        if (interlock.snapshot().closed()) throw new IllegalArgumentException("Cannot attach a closed original I/O interlock");
        if (originalIoActive != 0) throw new IllegalStateException("Cannot attach while original I/O is in flight");
        if (!ioInterlock.compareAndSet(null, interlock)) throw new IllegalStateException("Original I/O interlock already attached");
        return () -> {
            synchronized (HookProbe.class) {
                if (ioInterlock.get() != interlock) return;
                interlock.close();
                if (originalIoActive == 0) ioInterlock.compareAndSet(interlock, null);
            }
        };
    }
    /** Unlike diagnostic hooks, admission MUST propagate refusal before original code changes state. */
    public static synchronized void beforeOriginalIo(String kind) {
        CareerCommandGate.IoKind operation = CareerCommandGate.IoKind.valueOf(kind);
        var stack = originalIo.get();
        if (stack.size() >= CareerIoInterlock.MAX_DEPTH) throw new IllegalStateException("Original I/O bridge depth exceeded");
        CareerIoInterlock owner = ioInterlock.get();
        CareerIoInterlock.Entry entry = owner == null ? null : owner.enter(operation);
        try { stack.push(new IoFrame(owner, entry)); originalIoActive++; }
        catch (Throwable failure) { if (owner != null) owner.exit(entry); throw failure; }
    }
    /** Uses the owner captured at entry, not a possibly replaced/detached session. */
    public static synchronized void afterOriginalIo() {
        var stack = originalIo.get();
        if (stack.isEmpty()) { originalIo.remove(); throw new IllegalStateException("Unmatched original I/O exit"); }
        IoFrame frame = stack.pop(); if (stack.isEmpty()) originalIo.remove();
        if (frame.owner() != null) frame.owner().exit(frame.entry());
        originalIoActive--;
        CareerIoInterlock attached = ioInterlock.get();
        if (originalIoActive == 0 && attached != null && attached.snapshot().closed()) ioInterlock.compareAndSet(attached, null);
    }

    static AutoCloseable attach(Consumer<Observation> sink) {
        if (!observer.compareAndSet(null, sink)) throw new IllegalStateException("Uma sonda de carreira ja esta ativa nesta JVM");
        creationContent.reset();
        asyncCreation.reset();
        creationDialogs.reset();
        creationEntries.reset();
        return () -> observer.compareAndSet(sink, null);
    }
    static AutoCloseable attachLifecycle(CareerLifecycle state) {
        java.util.Objects.requireNonNull(state);
        if (!lifecycle.compareAndSet(null, state)) throw new IllegalStateException("Lifecycle guard ja ativo");
        decodedProvenance.invalidate(DecodedSaveProvenance.Reason.DETACHED);
        writtenProvenance.invalidate();
        financeMutations.invalidate();
        invalidateFinancialSerialization();
        return () -> { if (lifecycle.compareAndSet(state, null)) { decodedProvenance.invalidate(DecodedSaveProvenance.Reason.DETACHED); writtenProvenance.invalidate(); financeMutations.invalidate(); invalidateFinancialSerialization(); } };
    }
    private static CareerLifecycle.Ticket ticket() {
        CareerLifecycle state = lifecycle.get();
        return state == null ? null : state.ticket();
    }
    public static void rootAssigned() {
        invalidateFinancialSerialization();
        financeMutations.invalidate();
        writtenProvenance.invalidate();
        var attachedIo = ioInterlock.get(); if (attachedIo != null) attachedIo.invalidateContext();
        decodedProvenance.invalidate(DecodedSaveProvenance.Reason.ROOT_CHANGED);
        creationInterference(CreationPathQualification.Reason.MUTATION_OBSERVED);
        try {
            CareerLifecycle state = lifecycle.get();
            if (state != null) {
                CareerLifecycle.Ticket assigned = state.rootsChanged();
                Load frame = loads.get().peek();
                if (frame != null) frame.assignedTicket = assigned;
                else loads.remove();
            }
        }
        catch (Throwable ignored) { }
    }
    private static void emit(String operation, String file, boolean completed, boolean backup, boolean returned, boolean fallback, boolean aborted) {
        Consumer<Observation> sink = observer.get();
        if (sink != null) sink.accept(new Observation(operation, file, completed, backup, returned, fallback, aborted,
                Thread.currentThread().getName(), sequence.incrementAndGet()));
    }
    public static void creationPathStarted(String label) {
        creationPathStarted(label, null, null);
    }
    private static long generation() { CareerLifecycle.Ticket value = ticket(); return value == null ? -1 : value.generation(); }
    static java.util.List<CreationContentTracker.Report> creationContent() { return creationContent.snapshot(); }
    static java.util.List<CreationContentTracker.Step> creationSteps() { return creationContent.steps(); }
    public static void creationPathStep(String label, String stage, Object primary, Object secondary) {
        try { creationEntries.step(label, stage, primary, secondary, generation()); } catch (Throwable ignored) { }
        try {
            creationContent.step(label, stage, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                    javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation());
        } catch (Throwable ignored) { }
    }
    public static void creationPathStarted(String label, Object primary, Object secondary) {
        if (label.equals("a.bV.AB")) creationDialog("AB", "CANDIDATE_ENTER", 0, primary, secondary);
        try { creationEntries.candidate(label, "ENTER", primary, secondary, generation()); } catch (Throwable ignored) { }
        try { asyncCreation.origin(label, primary, secondary, generation()); } catch (Throwable ignored) { }
        try { creationContent.enter(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation()); } catch (Throwable ignored) { }
        try { emit("creation-candidate-start", label, false, false, false, false, false); } catch (Throwable ignored) { }
    }
    public static void creationPathReturned(String label) {
        creationPathReturned(label, null, null);
    }
    public static void creationPathReturned(String label, Object primary, Object secondary) {
        try { creationEntries.candidate(label, "RETURN", primary, secondary, generation()); } catch (Throwable ignored) { }
        try { asyncCreation.endOrigin(label); } catch (Throwable ignored) { }
        try { creationContent.finish(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation(), true); } catch (Throwable ignored) { }
        // Return of a UI candidate is not proof that creation finished; do not touch the lifecycle guard.
        try { emit("creation-candidate-return", label, false, false, true, false, false); } catch (Throwable ignored) { }
    }
    public static void creationPathFailed(String label) {
        creationPathFailed(label, null, null);
    }
    public static void creationPathFailed(String label, Object primary, Object secondary) {
        try { creationEntries.candidate(label, "FAILED", primary, secondary, generation()); } catch (Throwable ignored) { }
        try { asyncCreation.endOrigin(label); } catch (Throwable ignored) { }
        try { creationContent.finish(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation(), false); } catch (Throwable ignored) { }
        try { emit("creation-candidate-failed", label, false, false, false, false, true); } catch (Throwable ignored) { }
    }
    public static void enterLoad(String name, boolean backup) {
        invalidateFinancialSerialization();
        financeMutations.invalidate();
        writtenProvenance.invalidate();
        decodedProvenance.invalidate(DecodedSaveProvenance.Reason.LOAD_STARTED);
        creationInterference(CreationPathQualification.Reason.MUTATION_OBSERVED);
        try {
            ArrayDeque<Load> stack = loads.get();
            Load frame = new Load(name, backup);
            boolean outer = stack.isEmpty();
            stack.push(frame);
            CareerLifecycle state = lifecycle.get();
            if (outer && state != null) frame.attempt = state.beginMutation();
        } catch (Throwable ignored) { }
    }
    public static void asyncScheduleStarted(String label) {
        creationInterference(CreationPathQualification.Reason.ASYNC_OBSERVED);
        try { asyncCreation.scheduleStarted(label); } catch (Throwable ignored) { }
    }
    public static void asyncTaskCreated(String label, Object task, Object primary, Object secondary) {
        try { asyncCreation.taskCreated(label, task, primary, secondary, generation()); } catch (Throwable ignored) { }
    }
    public static void asyncScheduleReturned(String label) {
        try { asyncCreation.scheduleFinished(label, true); } catch (Throwable ignored) { }
    }
    public static void asyncScheduleFailed(String label) {
        try { asyncCreation.scheduleFinished(label, false); } catch (Throwable ignored) { }
    }
    public static void asyncRunStarted(String label, Object task, Object primary, Object secondary) {
        creationInterference(CreationPathQualification.Reason.ASYNC_OBSERVED);
        try { asyncCreation.runStarted(label, task, primary, secondary, generation()); } catch (Throwable ignored) { }
    }
    public static void asyncRunReturned(String label, Object task, Object primary, Object secondary) {
        try { asyncCreation.runFinished(label, task, primary, secondary, generation(), true); } catch (Throwable ignored) { }
    }
    public static void asyncRunFailed(String label, Object task, Object primary, Object secondary) {
        try { asyncCreation.runFinished(label, task, primary, secondary, generation(), false); } catch (Throwable ignored) { }
    }
    static java.util.List<AsyncCreationTracker.Report> asyncCreationReports() { return asyncCreation.snapshot(); }
    static long asyncCreationRejected() { return asyncCreation.rejected(); }
    static long asyncCreationUnbound() { return asyncCreation.unbound(); }
    public static void creationDialog(String method, String stage, int result, Object primary, Object secondary) {
        try { creationEntries.dialog(method, stage, result, primary, secondary, generation()); } catch (Throwable ignored) { }
        try { creationDialogs.marker(method, stage, result, primary, secondary, generation()); } catch (Throwable ignored) { }
    }
    public static void creationEntry(String stage, Object panel, Object primary, Object secondary) {
        try { creationEntries.preparation(stage, panel, primary, secondary, generation()); } catch (Throwable ignored) { }
    }
    public static void creationEntryAttempt(Object panel, Object primary, Object secondary) {
        try { creationEntries.attempt(panel, primary, secondary, generation(), javax.swing.SwingUtilities.isEventDispatchThread()); } catch (Throwable ignored) { }
    }
    static CreationEntryTracker.Snapshot creationEntries() { return creationEntries.capture(); }
    static CreationEntryTracker.QualificationSnapshot creationQualifications() { return creationEntries.qualificationSnapshot(); }
    private static void creationInterference(CreationPathQualification.Reason reason) {
        try { creationEntries.interference(reason); } catch (Throwable ignored) { }
    }
    static CreationDialogTracker.Snapshot creationDialogs() { return creationDialogs.capture(); }
    public static void creationProgress(String method, String stage, int panel, Object receiver, Object primary, Object secondary) {
        try { creationEntries.progress(method, stage, panel, receiver, primary, secondary, generation()); } catch (Throwable ignored) { }
        try { asyncCreation.progress(method, stage, panel, receiver, primary, secondary, generation()); } catch (Throwable ignored) { }
    }
    static java.util.List<AsyncCreationTracker.Progress> creationProgress() { return asyncCreation.progress(); }
    static long creationProgressDropped() { return asyncCreation.progressDropped(); }
    static long creationProgressOutside() { return asyncCreation.progressOutside(); }
    static AsyncCreationTracker.Snapshot asyncCreationSnapshot() { return asyncCreation.capture(); }
    public static void enterReset() {
        invalidateFinancialSerialization();
        financeMutations.invalidate();
        writtenProvenance.invalidate();
        decodedProvenance.invalidate(DecodedSaveProvenance.Reason.RESET_STARTED);
        creationInterference(CreationPathQualification.Reason.MUTATION_OBSERVED);
        try {
            Reset frame = new Reset(); resets.get().push(frame);
            CareerLifecycle state = lifecycle.get();
            if (state != null) frame.attempt = state.beginMutation();
        } catch (Throwable ignored) { }
    }
    private static void finishReset() {
        ArrayDeque<Reset> stack = resets.get();
        if (stack.isEmpty()) return;
        Reset frame = stack.pop(); if (stack.isEmpty()) resets.remove();
        CareerLifecycle state = lifecycle.get();
        if (state != null) state.resetFinished(frame.attempt);
    }
    public static void rootsReset() {
        try {
            finishReset();
            emit("roots-reset", null, true, false, true, false, false);
        }
        catch (Throwable ignored) { }
    }
    public static void rootSetterFinished() {
        try { finishReset(); emit("root-setter", null, true, false, true, false, false); }
        catch (Throwable ignored) { }
    }
    public static void abortReset(Throwable failure) {
        try { finishReset(); emit("root-reset-failed", null, false, false, false, false, true); }
        catch (Throwable ignored) { }
    }
    public static void decodedLoad() {
        decodedLoad(null, null);
    }
    public static void loadSource(String source) {
        try { Load frame = loads.get().peek(); if (frame != null) frame.source = java.nio.file.Path.of(source); }
        catch (Throwable ignored) { }
    }
    public static java.io.InputStream traceLoadInput(java.io.InputStream input) {
        try {
            Load frame = loads.get().peek();
            if (frame != null && lifecycle.get() != null) {
                if (frame.trace != null) { frame.source = null; return input; }
                frame.trace = new LoadInputTrace(input); return frame.trace;
            }
        } catch (Throwable ignored) { }
        return input;
    }
    public static void decodedLoad(Object primary, Object secondary) {
        try {
            Load frame = loads.get().peek();
            if (frame != null) { frame.decodedTicket = frame.assignedTicket; frame.decoded = true;
                frame.decodedPrimary = primary; frame.decodedSecondary = secondary;
                frame.provenanceRevision = decodedProvenance.snapshot().revision();
            }
        } catch (Throwable ignored) { }
    }
    public static void loadInitializationPathFinished() {
        try { Load frame = loads.get().peek(); if (frame != null) frame.initializedPath = true; } catch (Throwable ignored) { }
    }
    public static void exitLoad(boolean returned) { finishLoad(returned, false); }
    public static void abortLoad(Throwable failure) { finishLoad(false, true); }
    private static void finishLoad(boolean returned, boolean aborted) {
        try {
            ArrayDeque<Load> stack = loads.get();
            if (stack.isEmpty()) return;
            Load frame = stack.pop();
            if (frame.decoded && returned && !aborted) {
                frame.successful = true; frame.actualName = frame.name; frame.actualBackup = frame.backup;
                frame.actualTicket = frame.decodedTicket;
                frame.actualInitializedPath = frame.initializedPath;
                frame.actualSource = frame.source; frame.actualStream = frame.trace == null ? null : frame.trace.finish();
                frame.actualPrimary = frame.decodedPrimary; frame.actualSecondary = frame.decodedSecondary;
                frame.actualProvenanceRevision = frame.provenanceRevision;
            }
            if (!stack.isEmpty()) {
                if (frame.successful && !aborted) {
                    Load parent = stack.peek();
                    parent.successful = true; parent.actualName = frame.actualName; parent.actualBackup = frame.actualBackup;
                    parent.actualTicket = frame.actualTicket;
                    parent.actualInitializedPath = frame.actualInitializedPath;
                    parent.actualSource = frame.actualSource; parent.actualStream = frame.actualStream;
                    parent.actualPrimary = frame.actualPrimary; parent.actualSecondary = frame.actualSecondary;
                    parent.actualProvenanceRevision = frame.actualProvenanceRevision;
                }
                return;
            }
            loads.remove();
            CareerLifecycle state = lifecycle.get();
            boolean accepted = state != null && state.loaded(frame.attempt, frame.actualTicket, frame.actualName, frame.actualBackup, frame.successful && !aborted, frame.actualInitializedPath);
            var now = state == null ? null : state.ticket();
            accepted &= now != null && frame.actualTicket != null && now.session().equals(frame.actualTicket.session()) && now.generation() == frame.actualTicket.generation();
            decodedProvenance.accept(frame.actualProvenanceRevision, now, frame.actualSource, frame.actualStream, frame.actualBackup,
                    frame.actualInitializedPath, frame.actualPrimary, frame.actualSecondary, accepted);
            emit("load", frame.successful ? frame.actualName : frame.name, frame.successful && !aborted,
                    frame.successful ? frame.actualBackup : frame.backup, returned, frame.successful && frame.actualBackup && !frame.backup, aborted);
        } catch (Throwable ignored) { /* Never interrupt legacy code for probe/log failures. */ }
    }
    public static void enterSave(String name) {
        decodedProvenance.invalidate(DecodedSaveProvenance.Reason.SAVE_STARTED);
        long writtenRevision = writtenProvenance.invalidate();
        creationInterference(CreationPathQualification.Reason.SAVE_OBSERVED);
        try {
            Save frame = new Save(name); saves.get().push(frame);
            frame.ticket = ticket(); frame.evidenceRevision = writtenRevision;
            CareerLifecycle state = lifecycle.get(); if (state != null) frame.attempt = state.beginSave();
        } catch (Throwable ignored) { }
    }
    static DecodedSaveProvenance.Snapshot decodedProvenance() { return decodedProvenance.snapshot(); }
    static WrittenSaveProvenance.Snapshot writtenSaveProvenance() { return writtenProvenance.snapshot(); }
    static boolean currentWrittenProvenance(WrittenSaveProvenance.Binding binding, Object primary, Object secondary) {
        return writtenProvenance.current(binding, ticket(), primary, secondary);
    }
    public static void saveDestination(String path) {
        try {
            Save frame = saves.get().peek(); if (frame == null) { saves.remove(); return; }
            frame.destinations++;
            if (frame.trace != null) { frame.sourceRejected = true; return; }
            frame.destination = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        } catch (Throwable ignored) { var frame = saves.get().peek(); if (frame != null) frame.sourceRejected = true; }
    }
    public static java.io.OutputStream traceSaveOutput(java.io.OutputStream output) {
        try {
            Save frame = saves.get().peek(); if (frame == null) { saves.remove(); return output; }
            if (frame.trace != null || frame.destination == null || frame.destinations != 2
                    || !frame.destination.getFileName().toString().endsWith(".s22")) { frame.sourceRejected = true; return output; }
            frame.trace = new SaveOutputTrace(output); return frame.trace;
        } catch (Throwable ignored) { return output; }
    }
    public static void saveRootSerializing(Object root) {
        try {
            Save frame = saves.get().peek(); if (frame == null) { saves.remove(); return; }
            if (frame.rootsStarted != frame.rootsFinished || root == null) frame.sourceRejected = true;
            if (frame.rootsStarted++ == 0) {
                frame.serializedPrimary = new java.lang.ref.WeakReference<>(root);
                frame.financeBefore = financeMutations.snapshot(root == null ? null : root.getClass().getClassLoader());
                frame.serializationTracker = financeSerialization.get();
                if (frame.serializationTracker != null) frame.serializationWindow = frame.serializationTracker.begin(root, frame.financeBefore);
            }
            else if (frame.rootsStarted == 2) frame.serializedSecondary = new java.lang.ref.WeakReference<>(root);
            else frame.sourceRejected = true;
        } catch (Throwable ignored) { }
    }
    public static void saveRootSerialized() {
        try {
            Save frame = saves.get().peek();
            if (frame != null) {
                frame.rootsFinished++;
                Object primary = frame.serializedPrimary == null ? null : frame.serializedPrimary.get();
                if (frame.rootsFinished == 2) {
                    if (frame.serializationTracker != null) frame.operationCapture = frame.serializationTracker.finish(frame.serializationWindow, primary);
                    frame.financeAfter = financeMutations.snapshot(primary == null ? null : primary.getClass().getClassLoader());
                }
            } else saves.remove();
        } catch (Throwable ignored) { }
    }
    static boolean currentProvenance(DecodedSaveProvenance.Binding binding, Object primary, Object secondary) {
        return decodedProvenance.current(binding, ticket(), primary, secondary);
    }
    static boolean qualifiedOperationalLoad(LoadedSaveVerifier.Verified expected, Object primary, Object secondary, CareerCommandGate gate) {
        var attachedIo = ioInterlock.get(); var state = lifecycle.get(); var current = decodedProvenance.snapshot().binding();
        if (attachedIo == null || !attachedIo.owns(gate) || state == null || current.isEmpty()) return false;
        var io = attachedIo.snapshot(); var snapshot = state.snapshot(); var binding = current.orElseThrow();
        return !io.closed() && io.depth() == 0 && io.refused() == 0 && io.admitted() > 0 && io.admitted() == io.completed()
                && snapshot.healthy() && snapshot.inFlight() == 0 && snapshot.inFlightSaves() == 0
                && snapshot.ioState().phase() == br.vbrasfoot.api.CareerIoEvents.Phase.ACTIVE && binding.initializedPath()
                && binding.session().equals(expected.session()) && binding.generation() == expected.generation()
                && binding.revision() == expected.evidenceRevision() && binding.bytes().equals(expected.bytes())
                && binding.source().equals(expected.source()) && binding.backup() == expected.backup()
                && currentProvenance(binding, primary, secondary);
    }
    public static void metadataClosed() {
        try { Save frame = saves.get().peek(); if (frame != null) frame.metadata = true; } catch (Throwable ignored) { }
    }
    public static void primaryClosed() {
        try { Save frame = saves.get().peek(); if (frame != null) frame.closed = true; } catch (Throwable ignored) { }
    }
    public static void primaryOutputClosing(Object output) {
        try {
            Save frame = saves.get().peek();
            if (frame != null && frame.output == null) frame.output = output;
            else if (frame == null) saves.remove();
        } catch (Throwable ignored) { }
    }
    public static void primaryStreamClosed(Object output) { streamClosed(output, false); }
    public static void primaryStreamCloseFailed(Object output) { streamClosed(output, true); }
    private static void streamClosed(Object output, boolean failed) {
        try {
            Save frame = saves.get().peek();
            if (frame == null) { saves.remove(); return; }
            if (output == null || frame.output != output) return;
            if (failed) frame.streamCloseFailed = true;
            else frame.streamClosed = true;
        } catch (Throwable ignored) { }
    }
    public static void backupCopied() {
        try { Save frame = saves.get().peek(); if (frame != null) frame.backup = true; } catch (Throwable ignored) { }
    }
    public static void exitSave() { finishSave(false); }
    public static void abortSave(Throwable failure) { finishSave(true); }
    private static void finishSave(boolean aborted) {
        try {
            ArrayDeque<Save> stack = saves.get();
            if (stack.isEmpty()) return;
            Save frame = stack.pop();
            if (stack.isEmpty()) saves.remove();
            CareerLifecycle state = lifecycle.get();
            var outcome = state == null ? null : state.finishSave(frame.attempt, frame.name, frame.metadata, frame.closed, frame.backup, !aborted,
                    frame.streamClosed, frame.streamCloseFailed);
            writtenProvenance.complete(frame.evidenceRevision, frame.ticket, ticket(), frame.destination,
                    frame.trace == null ? null : frame.trace.finish(), outcome != null && outcome.completeSequence() && !frame.sourceRejected
                            && frame.rootsStarted == 2 && frame.rootsFinished == 2,
                    frame.serializedPrimary == null ? null : frame.serializedPrimary.get(), frame.serializedSecondary == null ? null : frame.serializedSecondary.get(),
                    frame.financeBefore, frame.financeAfter, frame.operationCapture);
            emit("save", frame.name, frame.closed && !aborted && !frame.streamCloseFailed, frame.backup, !aborted, false, aborted);
        } catch (Throwable ignored) { }
    }
}
