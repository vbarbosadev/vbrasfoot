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
    private static final AtomicLong sequence = new AtomicLong();
    private static final ThreadLocal<ArrayDeque<Load>> loads = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Save>> saves = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Load {
        String name, actualName;
        boolean backup, decoded, actualBackup, successful;
        Load(String name, boolean backup) { this.name = name; this.backup = backup; }
    }
    private static final class Save {
        String name;
        boolean closed, backup;
        Save(String name) { this.name = name; }
    }
    private HookProbe() { }

    static AutoCloseable attach(Consumer<Observation> sink) {
        if (!observer.compareAndSet(null, sink)) throw new IllegalStateException("Uma sonda de carreira ja esta ativa nesta JVM");
        return () -> observer.compareAndSet(sink, null);
    }
    private static void emit(String operation, String file, boolean completed, boolean backup, boolean returned, boolean fallback, boolean aborted) {
        Consumer<Observation> sink = observer.get();
        if (sink != null) sink.accept(new Observation(operation, file, completed, backup, returned, fallback, aborted,
                Thread.currentThread().getName(), sequence.incrementAndGet()));
    }
    public static void enterLoad(String name, boolean backup) {
        try { loads.get().push(new Load(name, backup)); } catch (Throwable ignored) { }
    }
    public static void decodedLoad() {
        try { Load frame = loads.get().peek(); if (frame != null) frame.decoded = true; } catch (Throwable ignored) { }
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
            }
            if (!stack.isEmpty()) {
                if (frame.successful && !aborted) {
                    Load parent = stack.peek();
                    parent.successful = true; parent.actualName = frame.actualName; parent.actualBackup = frame.actualBackup;
                }
                return;
            }
            loads.remove();
            emit("load", frame.successful ? frame.actualName : frame.name, frame.successful && !aborted,
                    frame.successful ? frame.actualBackup : frame.backup, returned, frame.successful && frame.actualBackup && !frame.backup, aborted);
        } catch (Throwable ignored) { /* Never interrupt legacy code for probe/log failures. */ }
    }
    public static void enterSave(String name) {
        try { saves.get().push(new Save(name)); } catch (Throwable ignored) { }
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
            emit("save", frame.name, frame.closed && !aborted, frame.backup, !aborted, false, aborted);
        } catch (Throwable ignored) { }
    }
}
