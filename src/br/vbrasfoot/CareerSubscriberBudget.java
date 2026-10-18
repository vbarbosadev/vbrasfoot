package br.vbrasfoot;

/** One worker budget for both career streams; retired callbacks retain their slot until exit. */
final class CareerSubscriberBudget {
    private final int maximum;
    private final java.util.concurrent.atomic.AtomicInteger workers = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong();
    CareerSubscriberBudget(int maximum) { if (maximum < 1) throw new IllegalArgumentException(); this.maximum = maximum; }
    boolean acquire() {
        for (;;) {
            int value = workers.get(); if (value >= maximum) return false;
            if (workers.compareAndSet(value, value + 1)) return true;
        }
    }
    void release() { workers.decrementAndGet(); }
    long nextId() { return ids.incrementAndGet(); }
}
