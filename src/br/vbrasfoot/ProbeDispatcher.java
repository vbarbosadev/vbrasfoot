package br.vbrasfoot;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Internal bounded delivery; no public events, no EDT calls and no unbounded shutdown wait. */
final class ProbeDispatcher implements AutoCloseable {
    record Stats(long accepted, long dropped, long delivered, long failed, long discarded, boolean workerAlive) { }
    private final ArrayBlockingQueue<HookProbe.Observation> queue;
    private final Consumer<HookProbe.Observation> sink;
    private final AtomicLong accepted = new AtomicLong(), dropped = new AtomicLong(), delivered = new AtomicLong(),
            failed = new AtomicLong(), discarded = new AtomicLong();
    private final Thread worker;
    private final long closeMillis;
    private volatile boolean closed;

    ProbeDispatcher(int capacity, Duration grace, Consumer<HookProbe.Observation> sink) {
        if (capacity < 1 || grace.isNegative() || grace.toMillis() > 1000) throw new IllegalArgumentException("Invalid capacity/grace");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.sink = Objects.requireNonNull(sink);
        closeMillis = grace.toMillis();
        worker = new Thread(this::run, "vbrasfoot-probe-dispatch");
        worker.setDaemon(true);
        worker.start();
    }
    synchronized void offer(HookProbe.Observation observation) {
        Objects.requireNonNull(observation);
        if (closed || !queue.offer(observation)) { dropped.incrementAndGet(); return; }
        accepted.incrementAndGet();
    }
    private void run() {
        try {
            while (!closed || !queue.isEmpty()) {
                HookProbe.Observation observation = queue.poll(25, TimeUnit.MILLISECONDS);
                if (observation == null) continue;
                try { sink.accept(observation); delivered.incrementAndGet(); }
                catch (Throwable failure) { failed.incrementAndGet(); }
            }
        } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
    }
    Stats stats() { return new Stats(accepted.get(), dropped.get(), delivered.get(), failed.get(), discarded.get(), worker.isAlive()); }
    @Override public void close() {
        synchronized (this) { if (closed) return; closed = true; }
        if (Thread.currentThread() == worker) { discardPending(); return; }
        try { if (closeMillis > 0) worker.join(closeMillis); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (worker.isAlive()) { discardPending(); worker.interrupt(); }
    }
    private void discardPending() {
        HookProbe.Observation item;
        while ((item = queue.poll()) != null) discarded.incrementAndGet();
    }
}
