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
    private static final ThreadLocal<ArrayDeque<Load>> loads = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Save>> saves = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Reset>> resets = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Load {
        String name, actualName;
        boolean backup, decoded, actualBackup, successful;
        CareerLifecycle.Ticket decodedTicket, actualTicket;
        CareerLifecycle.Ticket assignedTicket;
        CareerLifecycle.Attempt attempt;
        Load(String name, boolean backup) { this.name = name; this.backup = backup; }
    }
    private static final class Reset { CareerLifecycle.Attempt attempt; }
    private static final class Save {
        String name;
        boolean closed, backup;
        CareerLifecycle.Ticket ticket;
        Save(String name) { this.name = name; }
    }
    private HookProbe() { }

    static AutoCloseable attach(Consumer<Observation> sink) {
        if (!observer.compareAndSet(null, sink)) throw new IllegalStateException("Uma sonda de carreira ja esta ativa nesta JVM");
        creationContent.reset();
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
        try {
            creationContent.step(label, stage, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                    javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation());
        } catch (Throwable ignored) { }
    }
    public static void creationPathStarted(String label, Object primary, Object secondary) {
        try { creationContent.enter(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation()); } catch (Throwable ignored) { }
        try { emit("creation-candidate-start", label, false, false, false, false, false); } catch (Throwable ignored) { }
    }
    public static void creationPathReturned(String label) {
        creationPathReturned(label, null, null);
    }
    public static void creationPathReturned(String label, Object primary, Object secondary) {
        try { creationContent.finish(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation(), true); } catch (Throwable ignored) { }
        // Return of a UI candidate is not proof that creation finished; do not touch the lifecycle guard.
        try { emit("creation-candidate-return", label, false, false, true, false, false); } catch (Throwable ignored) { }
    }
    public static void creationPathFailed(String label) {
        creationPathFailed(label, null, null);
    }
    public static void creationPathFailed(String label, Object primary, Object secondary) {
        try { creationContent.finish(label, javax.swing.SwingUtilities.isEventDispatchThread() ? primary : null,
                javax.swing.SwingUtilities.isEventDispatchThread() ? secondary : null, generation(), false); } catch (Throwable ignored) { }
        try { emit("creation-candidate-failed", label, false, false, false, false, true); } catch (Throwable ignored) { }
    }
    public static void enterLoad(String name, boolean backup) {
        try {
            ArrayDeque<Load> stack = loads.get();
            Load frame = new Load(name, backup);
            boolean outer = stack.isEmpty();
            stack.push(frame);
            CareerLifecycle state = lifecycle.get();
            if (outer && state != null) frame.attempt = state.beginMutation();
        } catch (Throwable ignored) { }
    }
    public static void enterReset() {
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
            }
            if (!stack.isEmpty()) {
                if (frame.successful && !aborted) {
                    Load parent = stack.peek();
                    parent.successful = true; parent.actualName = frame.actualName; parent.actualBackup = frame.actualBackup;
                    parent.actualTicket = frame.actualTicket;
                }
                return;
            }
            loads.remove();
            CareerLifecycle state = lifecycle.get();
            if (state != null) state.loaded(frame.attempt, frame.actualTicket, frame.actualName, frame.actualBackup, frame.successful && !aborted);
            emit("load", frame.successful ? frame.actualName : frame.name, frame.successful && !aborted,
                    frame.successful ? frame.actualBackup : frame.backup, returned, frame.successful && frame.actualBackup && !frame.backup, aborted);
        } catch (Throwable ignored) { /* Never interrupt legacy code for probe/log failures. */ }
    }
    public static void enterSave(String name) {
        try { Save frame = new Save(name); frame.ticket = ticket(); saves.get().push(frame); } catch (Throwable ignored) { }
    }
    public static void primaryClosed() {
        try { Save frame = saves.get().peek(); if (frame != null) frame.closed = true; } catch (Throwable ignored) { }
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
            if (state != null && frame.closed && !aborted) state.saved(frame.ticket, frame.name);
            emit("save", frame.name, frame.closed && !aborted, frame.backup, !aborted, false, aborted);
        } catch (Throwable ignored) { }
    }
}
