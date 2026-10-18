package br.vbrasfoot;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Internal journal-before-effect pipeline. No public ModContext exposure or automatic career qualification. */
final class CareerCommandDispatcher {
    enum Kind { EXECUTED, NO_EFFECT, RECORDED_ONLY, DENIED, UNCERTAIN }
    enum Detail { NONE, BINDING_MISMATCH, HANDLER_MISMATCH, GATE_REFUSED, QUEUE_TIMEOUT, EXECUTION_TIMEOUT, HANDLER_FAILURE, JOURNAL_FAILURE }
    record Roots(Object primary, Object secondary) { }
    record Result(Kind kind, Detail detail, CareerCommandGate.Receipt receipt, Optional<CareerCommandJournal.Snapshot> journal) { }
    interface RootsSource { Roots current() throws Exception; }
    /** Trusted adapter only. REJECTED means the adapter can prove that no mutation occurred. */
    interface Handler {
        CareerCommandGate.Status apply() throws Exception;
        default boolean accepts(CareerCommandJournal.Intent intent) { return false; }
    }
    private final CareerCommandGate gate;
    private final CareerCommandJournal journal;
    private final RootsSource roots;
    private final long timeoutNanos;
    CareerCommandDispatcher(CareerCommandGate gate, CareerCommandJournal journal, RootsSource roots, Duration timeout) {
        this.gate = Objects.requireNonNull(gate); this.journal = Objects.requireNonNull(journal); this.roots = Objects.requireNonNull(roots);
        Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0) throw new IllegalArgumentException("Timeout must be >0 and <=1 minute");
        timeoutNanos = timeout.toNanos();
    }
    Result execute(CareerCommandGate.Owner owner, CareerCommandGate.Context context, CareerCommandJournal.Intent intent,
                   String expectedJournalToken, Handler handler) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Journal dispatch must start off EDT");
        Objects.requireNonNull(context); Objects.requireNonNull(intent); Objects.requireNonNull(handler);
        if (!journal.matches(context, gate.ownerId(owner)))
            return new Result(Kind.DENIED, Detail.BINDING_MISMATCH, null, Optional.empty());
        if (!handler.accepts(intent)) return new Result(Kind.DENIED, Detail.HANDLER_MISMATCH, null, Optional.empty());
        boolean interrupted = Thread.interrupted();
        CareerCommandGate.Permit permit = null;
        try {
            // Bounded read-only EDT preflight. A timed-out roots lookup cannot dispatch a handler.
            FutureTask<Roots> query = new FutureTask<>(roots::current); SwingUtilities.invokeLater(query);
            Roots before;
            try { before = await(query); }
            catch (TimeoutException timeout) { query.cancel(false); return new Result(Kind.DENIED, Detail.QUEUE_TIMEOUT, null, Optional.empty()); }
            var request = new CareerCommandGate.Request(context.token(), intent.key(), intent.operation(), intent.payloadDigest());
            var reserved = gate.reserve(owner, request, before.primary(), before.secondary());
            if (reserved.permit().isEmpty())
                return new Result(reserved.receipt().status() == CareerCommandGate.Status.OUTCOME_UNKNOWN ? Kind.UNCERTAIN : Kind.DENIED,
                        Detail.GATE_REFUSED, reserved.receipt(), Optional.empty());
            permit = reserved.permit().orElseThrow();
            rememberInterruption();
            CareerCommandJournal.Prepared prepared;
            try { prepared = journal.prepare(context.save(), expectedJournalToken, intent); }
            catch (IOException | RuntimeException failure) {
                var receipt = gate.cancel(permit); gate.invalidate();
                return new Result(Kind.UNCERTAIN, Detail.JOURNAL_FAILURE, receipt, Optional.empty()); // Handler never enqueued; persisted intent may exist.
            }
            if (prepared.lease().isEmpty()) {
                var receipt = gate.cancel(permit); gate.invalidate(); // Historical result is not proof of application to current decoded roots.
                return new Result(Kind.RECORDED_ONLY, Detail.NONE, receipt, Optional.of(prepared.snapshot()));
            }
            var dispatch = new Dispatch(permit, handler); SwingUtilities.invokeLater(dispatch);
            Execution executed;
            try { executed = await(dispatch.completion); }
            catch (TimeoutException timeout) { executed = dispatch.timeout(); }
            CareerCommandJournal.State terminal = switch (executed.receipt().status()) {
                case APPLIED -> CareerCommandJournal.State.APPLIED;
                case REJECTED -> CareerCommandJournal.State.NO_EFFECT_CONFIRMED;
                default -> CareerCommandJournal.State.OUTCOME_UNKNOWN;
            };
            try {
                rememberInterruption();
                var completed = journal.complete(prepared.lease().orElseThrow(), terminal, prepared.snapshot().token());
                Kind kind = terminal == CareerCommandJournal.State.APPLIED ? Kind.EXECUTED
                        : terminal == CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? Kind.NO_EFFECT : Kind.UNCERTAIN;
                return new Result(kind, executed.detail(), executed.receipt(), Optional.of(completed));
            } catch (IOException failure) {
                var receipt = gate.uncertain(permit); gate.invalidate();
                return new Result(Kind.UNCERTAIN, Detail.JOURNAL_FAILURE, receipt, Optional.empty());
            }
        } finally {
            if (permit != null) gate.cancel(permit); // Only still-RESERVED work can be cancelled without assuming rollback.
            // Keep interruption cleared during journal completion and restore it for the caller on exit.
            interrupted |= interruptedDuringWait.get(); interruptedDuringWait.remove();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    private final ThreadLocal<Boolean> interruptedDuringWait = ThreadLocal.withInitial(() -> false);
    private void rememberInterruption() { if (Thread.interrupted()) interruptedDuringWait.set(true); }
    private <T> T await(Future<T> future) throws Exception {
        long start = System.nanoTime();
        while (true) {
            long remaining = timeoutNanos - (System.nanoTime() - start);
            if (remaining <= 0) throw new TimeoutException();
            try { return future.get(remaining, TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) { interruptedDuringWait.set(true); } // Do not abandon an already scheduled effect.
            catch (ExecutionException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw new IllegalStateException("EDT preflight failed", failure.getCause());
            }
        }
    }
    private record Execution(CareerCommandGate.Receipt receipt, Detail detail) { }
    private final class Dispatch implements Runnable {
        private final CareerCommandGate.Permit permit;
        private final Handler handler;
        private final CompletableFuture<Execution> completion = new CompletableFuture<>();
        private boolean entered, cancelled;
        Dispatch(CareerCommandGate.Permit permit, Handler handler) { this.permit = permit; this.handler = handler; }
        @Override public void run() {
            synchronized (this) { if (cancelled) return; entered = true; }
            Roots current = null; boolean invocation = false;
            try {
                current = roots.current(); var start = gate.begin(permit, current.primary(), current.secondary());
                if (!start.invocationGranted()) { completion.complete(new Execution(start.receipt(), Detail.GATE_REFUSED)); return; }
                invocation = true;
                var outcome = handler.apply();
                if (outcome != CareerCommandGate.Status.APPLIED && outcome != CareerCommandGate.Status.REJECTED && outcome != CareerCommandGate.Status.OUTCOME_UNKNOWN)
                    throw new IllegalStateException("Handler returned nonterminal result");
                Roots after = roots.current();
                completion.complete(new Execution(gate.finish(permit, outcome, after.primary(), after.secondary()), Detail.NONE));
            } catch (Throwable failure) {
                var receipt = invocation ? gate.finish(permit, CareerCommandGate.Status.OUTCOME_UNKNOWN,
                        current == null ? null : current.primary(), current == null ? null : current.secondary()) : gate.cancel(permit);
                completion.complete(new Execution(receipt, Detail.HANDLER_FAILURE));
            }
        }
        synchronized Execution timeout() {
            if (completion.isDone()) return completion.join();
            if (!entered) { cancelled = true; return new Execution(gate.cancel(permit), Detail.QUEUE_TIMEOUT); }
            return new Execution(gate.uncertain(permit), Detail.EXECUTION_TIMEOUT);
        }
    }
}
