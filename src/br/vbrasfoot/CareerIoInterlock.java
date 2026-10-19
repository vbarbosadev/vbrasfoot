package br.vbrasfoot;

import java.util.*;

/** Synchronous, memory-only cooperative original-I/O exclusion. No writer, user callback or file I/O under lock. */
final class CareerIoInterlock implements AutoCloseable {
    static final int MAX_DEPTH = 64;
    static final class Entry {
        private final CareerIoInterlock owner;
        private final Thread thread;
        private Entry(CareerIoInterlock owner) { this.owner = owner; thread = Thread.currentThread(); }
    }
    record Snapshot(boolean closed, int depth, long admitted, long refused, long completed) { }
    private final CareerCommandGate gate;
    private final ArrayDeque<Entry> stack = new ArrayDeque<>();
    private CareerCommandGate.SaveFence fence;
    private CareerCommandGate.IoKind outerKind;
    private boolean closed;
    private long admitted, refused, completed;
    CareerIoInterlock(CareerCommandGate gate) { this.gate = Objects.requireNonNull(gate); }
    boolean owns(CareerCommandGate candidate) { return gate == candidate; }
    void invalidateContext() { gate.invalidate(); }
    synchronized Entry enter(CareerCommandGate.IoKind kind) {
        Objects.requireNonNull(kind);
        if (closed || stack.size() >= MAX_DEPTH || !stack.isEmpty() && (stack.peek().thread != Thread.currentThread()
                || outerKind == CareerCommandGate.IoKind.SAVE || kind == CareerCommandGate.IoKind.SAVE)) {
            refused++; throw new IllegalStateException("Original I/O refused: closed, nested save, competing thread or depth limit");
        }
        if (stack.isEmpty()) {
            try { fence = gate.beginOriginalIo(kind); }
            catch (RuntimeException failure) { refused++; throw failure; }
            outerKind = kind;
        }
        Entry entry = new Entry(this); stack.push(entry); admitted++; return entry;
    }
    synchronized void exit(Entry entry) {
        if (entry == null || entry.owner != this || entry.thread != Thread.currentThread() || stack.peek() != entry)
            throw new IllegalArgumentException("Foreign, out-of-order or repeated original I/O exit");
        stack.pop(); completed++;
        if (stack.isEmpty()) { gate.endSave(fence); fence = null; outerKind = null; }
    }
    synchronized Snapshot snapshot() { return new Snapshot(closed, stack.size(), admitted, refused, completed); }
    /** Do not release an outstanding writer just because the owner is shutting down. */
    @Override public synchronized void close() { closed = true; gate.invalidate(); }
}
