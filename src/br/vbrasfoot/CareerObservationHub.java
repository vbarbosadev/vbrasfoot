package br.vbrasfoot;

import br.vbrasfoot.api.CareerObservations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

/** Immutable replay + isolated bounded subscribers. No user code runs on publishing threads. */
final class CareerObservationHub implements CareerObservations, AutoCloseable {
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final int capacity, maxListeners;
    private final long closeMillis;
    private volatile State state;
    private boolean closed;
    private long nextListener;

    CareerObservationHub() { this(64, 32, Duration.ofMillis(500)); }
    CareerObservationHub(int capacity, int maxListeners, Duration closeGrace) {
        if (capacity < 1 || maxListeners < 1 || closeGrace.isNegative()) throw new IllegalArgumentException();
        this.capacity = capacity; this.maxListeners = maxListeners; this.closeMillis = closeGrace.toMillis();
    }
    synchronized void initialize(State initial) {
        if (state != null) throw new IllegalStateException("Hub ja inicializado");
        state = initial;
    }
    synchronized void publish(Update update) {
        if (closed) return;
        if (state == null || !state.session().equals(update.state().session()) || update.state().revision() <= state.revision()) {
            throw new IllegalStateException("Revisao de observacao fora de ordem");
        }
        state = update.state();
        for (Listener listener : new ArrayList<>(listeners)) listener.offer(update);
    }
    @Override public State state() {
        State current = state;
        if (current == null) throw new IllegalStateException("Hub ainda nao inicializado");
        return current;
    }
    synchronized void finishState(State finalState) {
        if (!state().session().equals(finalState.session()) || finalState.revision() <= state.revision()) throw new IllegalStateException();
        state = finalState; // No promised callback on shutdown.
    }
    @Override public synchronized Subscription subscribe(Consumer<Update> callback) {
        java.util.Objects.requireNonNull(callback);
        if (closed || state().phase() == Phase.CLOSED) throw new IllegalStateException("Sessao encerrada");
        if (listeners.size() >= maxListeners) throw new IllegalStateException("Limite de assinantes de carreira atingido");
        Listener listener = new Listener(callback, ++nextListener);
        listener.offer(new Update(Kind.CURRENT_STATE, state(), false));
        listeners.add(listener);
        try { listener.worker.start(); }
        catch (RuntimeException | Error failure) { listeners.remove(listener); listener.stop(Status.CLOSED); throw failure; }
        return listener;
    }
    private final class Listener implements Subscription {
        final ArrayBlockingQueue<Update> queue = new ArrayBlockingQueue<>(capacity);
        final Consumer<Update> callback;
        final Thread worker;
        volatile Status status = Status.ACTIVE;
        Listener(Consumer<Update> callback, long id) {
            this.callback = callback;
            worker = new Thread(this::run, "vbrasfoot-career-subscriber-" + id); worker.setDaemon(true);
        }
        void offer(Update update) {
            if (status == Status.ACTIVE && !queue.offer(update)) stop(Status.OVERFLOW);
        }
        void run() {
            try {
                while (status == Status.ACTIVE) {
                    Update update = queue.take();
                    if (status != Status.ACTIVE) break;
                    try { callback.accept(update); }
                    catch (Throwable failure) { stop(Status.CALLBACK_FAILED); }
                }
            } catch (InterruptedException interrupted) { stop(Status.CLOSED); Thread.currentThread().interrupt(); }
            finally { synchronized (CareerObservationHub.this) { listeners.remove(this); } }
        }
        void stop(Status reason) {
            synchronized (CareerObservationHub.this) {
                if (status != Status.ACTIVE) return;
                status = reason; queue.clear(); worker.interrupt();
            }
        }
        @Override public Status status() { return status; }
        @Override public void close() { stop(Status.CLOSED); }
    }
    @Override public void close() {
        ArrayList<Listener> closing;
        synchronized (this) {
            if (closed) return;
            closed = true; closing = new ArrayList<>(listeners);
            for (Listener listener : closing) listener.stop(Status.CLOSED);
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(closeMillis);
        for (Listener listener : closing) {
            if (Thread.currentThread() == listener.worker) continue;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { java.util.concurrent.TimeUnit.NANOSECONDS.timedJoin(listener.worker, remaining); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
    }
}
