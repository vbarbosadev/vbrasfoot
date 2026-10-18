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
    private static final AtomicLong sequence = new AtomicLong();
    private static final CreationContentTracker creationContent = new CreationContentTracker();
    private static final AsyncCreationTracker asyncCreation = new AsyncCreationTracker();
    private static final CreationDialogTracker creationDialogs = new CreationDialogTracker();
    private static final CreationEntryTracker creationEntries = new CreationEntryTracker();
    private static final ThreadLocal<ArrayDeque<Load>> loads = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Save>> saves = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Reset>> resets = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Load {
        String name, actualName;
        boolean backup, decoded, actualBackup, successful, initializedPath, actualInitializedPath;
        CareerLifecycle.Ticket decodedTicket, actualTicket;
        CareerLifecycle.Ticket assignedTicket;
        CareerLifecycle.Attempt attempt;
        Load(String name, boolean backup) { this.name = name; this.backup = backup; }
    }
    private static final class Reset { CareerLifecycle.Attempt attempt; }
    private static final class Save {
        String name;
        boolean metadata, closed, backup, streamClosed, streamCloseFailed;
        Object output;
        CareerLifecycle.SaveAttempt attempt;
        Save(String name) { this.name = name; }
    }
    private HookProbe() { }

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
        return () -> lifecycle.compareAndSet(state, null);
    }
    private static CareerLifecycle.Ticket ticket() {
        CareerLifecycle state = lifecycle.get();
        return state == null ? null : state.ticket();
    }
    public static void rootAssigned() {
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
        try {
            Load frame = loads.get().peek();
            if (frame != null) { frame.decodedTicket = frame.assignedTicket; frame.decoded = true; }
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
            }
            if (!stack.isEmpty()) {
                if (frame.successful && !aborted) {
                    Load parent = stack.peek();
                    parent.successful = true; parent.actualName = frame.actualName; parent.actualBackup = frame.actualBackup;
                    parent.actualTicket = frame.actualTicket;
                    parent.actualInitializedPath = frame.actualInitializedPath;
                }
                return;
            }
            loads.remove();
            CareerLifecycle state = lifecycle.get();
            if (state != null) state.loaded(frame.attempt, frame.actualTicket, frame.actualName, frame.actualBackup, frame.successful && !aborted, frame.actualInitializedPath);
            emit("load", frame.successful ? frame.actualName : frame.name, frame.successful && !aborted,
                    frame.successful ? frame.actualBackup : frame.backup, returned, frame.successful && frame.actualBackup && !frame.backup, aborted);
        } catch (Throwable ignored) { /* Never interrupt legacy code for probe/log failures. */ }
    }
    public static void enterSave(String name) {
        creationInterference(CreationPathQualification.Reason.SAVE_OBSERVED);
        try {
            Save frame = new Save(name); saves.get().push(frame);
            CareerLifecycle state = lifecycle.get(); if (state != null) frame.attempt = state.beginSave();
        } catch (Throwable ignored) { }
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
            if (state != null) state.finishSave(frame.attempt, frame.name, frame.metadata, frame.closed, frame.backup, !aborted,
                    frame.streamClosed, frame.streamCloseFailed);
            emit("save", frame.name, frame.closed && !aborted && !frame.streamCloseFailed, frame.backup, !aborted, false, aborted);
        } catch (Throwable ignored) { }
    }
}
