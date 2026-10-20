package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;

/** Conservative GLOBAL version for selected direct field writes. Not coverage of reflection/Unsafe/Kryo or save inclusion. */
final class FinanceMutationTracker {
    record Snapshot(UUID tracker, long revision, long started, long completed, int active, boolean healthy, boolean definingClassesObserved) {
        boolean stableSince(Snapshot previous) {
            return previous != null && tracker.equals(previous.tracker) && revision == previous.revision
                    && healthy && previous.healthy && definingClassesObserved && previous.definingClassesObserved
                    && active == 0 && previous.active == 0 && started == completed && previous.started == previous.completed;
        }
    }
    private final UUID id = UUID.randomUUID();
    private final Map<Thread, Integer> writers = new IdentityHashMap<>();
    private WeakReference<ClassLoader> loader = new WeakReference<>(null);
    private boolean clubObserved, ledgerObserved, healthy = true;
    private long revision, started, completed;
    private int active;
    synchronized void instrumented(String name, ClassLoader definingLoader) {
        if (definingLoader == null || !(name.equals("best.ah") || name.equals("best.C"))) { healthy = false; return; }
        ClassLoader previous = loader.get();
        if (previous != null && previous != definingLoader || previous == null && (clubObserved || ledgerObserved)) { healthy = false; return; }
        loader = new WeakReference<>(definingLoader);
        if (name.equals("best.ah")) clubObserved = true; else ledgerObserved = true;
        advance();
    }
    private void advance() { if (revision == Long.MAX_VALUE) healthy = false; else revision++; }
    synchronized void invalidate() { advance(); if (active != 0) healthy = false; }
    synchronized void begin() {
        advance();
        if (started == Long.MAX_VALUE || active == Integer.MAX_VALUE || !writers.containsKey(Thread.currentThread()) && writers.size() >= 64) { healthy = false; return; }
        int depth = writers.getOrDefault(Thread.currentThread(), 0);
        if (depth >= 64) { healthy = false; return; }
        writers.put(Thread.currentThread(), depth + 1); active++; started++;
    }
    synchronized void end() {
        advance(); Integer depth = writers.get(Thread.currentThread());
        if (depth == null || active == 0 || completed == Long.MAX_VALUE) { healthy = false; return; }
        if (depth == 1) writers.remove(Thread.currentThread()); else writers.put(Thread.currentThread(), depth - 1);
        active--; completed++;
    }
    synchronized void failed() { healthy = false; advance(); }
    synchronized Snapshot snapshot(ClassLoader expected) {
        return new Snapshot(id, revision, started, completed, active, healthy,
                expected != null && loader.get() == expected && clubObserved && ledgerObserved);
    }
}
