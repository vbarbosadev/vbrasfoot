package br.vbrasfoot;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Internal journal-before-effect pipeline. No public ModContext exposure or automatic career qualification. */
final class CareerCommandDispatcher {
    enum Kind { EXECUTED, NO_EFFECT, RECORDED_ONLY, DENIED, UNCERTAIN }
    enum Detail { NONE, BINDING_MISMATCH, HANDLER_MISMATCH, REQUEST_REQUIRED, GATE_REFUSED, QUEUE_TIMEOUT, EXECUTION_TIMEOUT, HANDLER_FAILURE, JOURNAL_FAILURE, WITNESS_FAILURE, REQUEST_STATE_FAILURE }
    record Roots(Object primary, Object secondary) { }
    record Result(Kind kind, Detail detail, CareerCommandGate.Receipt receipt, Optional<CareerCommandJournal.Snapshot> journal) { }
    record StoredResult(Result dispatch, FinanceRequestStore.Snapshot requests) { }
    record StoredLoanResult(Result dispatch, LoanRequestStore.Snapshot requests, String diagnostic) {
        StoredLoanResult(Result dispatch, LoanRequestStore.Snapshot requests) { this(dispatch, requests, LoanDiagnostics.historical()); }
    }
    interface RootsSource { Roots current() throws Exception; }
    /** Trusted adapter only. REJECTED means the adapter can prove that no mutation occurred. */
    interface Handler {
        CareerCommandGate.Status apply() throws Exception;
        default boolean accepts(CareerCommandJournal.Intent intent) { return false; }
        /** Payload-level scope check in addition to gate/journal checks; legacy handlers remain compatible. */
        default boolean acceptsContext(CareerCommandGate.Context context, String owner) { return true; }
        default Optional<FinanceRequest> persistentRequest() { return Optional.empty(); }
        default Optional<LoanRequest> persistentLoanRequest() { return Optional.empty(); }
        default boolean supportsExecutionWitness() { return false; }
        /** Internal diagnostic only; never grants authority, changes receipt status or declares save inclusion. */
        default void observeSerialization(CareerCommandJournal.ExecutionPermit prepared) { }
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
        return executeInternal(owner, context, intent, expectedJournalToken, handler, null);
    }
    /** Payload commit/read-confirmation precedes journal and effect. A store failure never enqueues a handler.
     * No atomicity with journal: a committed request alone is not permission to retry an operation. */
    StoredResult executeStoredFinance(CareerCommandGate.Owner owner, CareerCommandGate.Context context, FinanceRequest request,
                                     String expectedJournalToken, FinanceRequestStore requests, String expectedRequestToken, Handler handler) throws Exception {
        return executeFinance(owner, context, request, expectedJournalToken, requests, expectedRequestToken, handler, null);
    }
    StoredResult executeWitnessedFinance(CareerCommandGate.Owner owner, CareerCommandGate.Context context, FinanceRequest request,
                                        String expectedJournalToken, FinanceRequestStore requests, String expectedRequestToken,
                                        FinanceExecutionWitnessStore witnesses, String expectedWitnessToken, Handler handler) throws Exception {
        if (!Objects.requireNonNull(handler).supportsExecutionWitness() || !Objects.requireNonNull(witnesses).matches(context, gate.ownerId(owner)))
            throw new IllegalArgumentException("Trusted witnessed financial handler/scope required");
        return executeFinance(owner, context, request, expectedJournalToken, requests, expectedRequestToken, handler, new WitnessTarget(witnesses, expectedWitnessToken));
    }
    private record WitnessTarget(FinanceExecutionWitnessStore store, String token) { }
    StoredLoanResult executeStoredLoan(CareerCommandGate.Owner owner, CareerCommandGate.Context context, LoanRequest request,
                                      String journalToken, LoanRequestStore requests, String requestToken, Handler handler) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Persist loan requests off EDT");
        Objects.requireNonNull(request); Objects.requireNonNull(requests); Objects.requireNonNull(handler);
        String ownerId = gate.ownerId(owner);
        if (request.assessment().decision() != LoanClubAssessment.Decision.APPROVE)
            throw new IllegalArgumentException("Loan policy did not approve this request");
        if (!requests.matches(context, ownerId) || !journal.matches(context, ownerId) || !request.matches(context, ownerId)
                || !handler.persistentLoanRequest().equals(Optional.of(request)) || handler.persistentRequest().isPresent()
                || !handler.accepts(request.intent()) || !handler.acceptsContext(context, ownerId))
            throw new IllegalArgumentException("Stored loan request/handler/context mismatch");
        journal.reviewStoredRequest(context.save(), journalToken, request.intent());
        var committed = requests.persist(request, requestToken);
        committed = requests.evaluate(context.save(), request.key(), committed.token());
        var claim = requests.claim(context.save(), request.key(), committed.token());
        if (claim.lease().isEmpty()) {
            var state = claim.snapshot().state(request.key());
            Kind kind = state == LoanRequestStore.State.EXECUTING || state == LoanRequestStore.State.UNKNOWN ? Kind.UNCERTAIN
                    : state == LoanRequestStore.State.APPLIED || state == LoanRequestStore.State.NO_EFFECT ? Kind.RECORDED_ONLY : Kind.DENIED;
            if (kind != Kind.DENIED) gate.invalidate();
            return new StoredLoanResult(new Result(kind, Detail.REQUEST_REQUIRED, null, Optional.empty()), claim.snapshot());
        }
        journal.reviewStoredRequest(context.save(), journalToken, request.intent());
        var result = executeInternal(owner, context, request.intent(), journalToken, handler, request);
        var outcome = switch (result.kind()) {
            case EXECUTED -> LoanRequestStore.State.APPLIED;
            case NO_EFFECT, DENIED -> LoanRequestStore.State.NO_EFFECT;
            default -> LoanRequestStore.State.UNKNOWN;
        };
        try {
            return new StoredLoanResult(result, requests.complete(context.save(), claim.lease().orElseThrow(), outcome, claim.snapshot().token()));
        } catch (IOException | RuntimeException stateFailure) {
            gate.invalidate();
            return new StoredLoanResult(new Result(Kind.UNCERTAIN, Detail.REQUEST_STATE_FAILURE, result.receipt(), result.journal()), claim.snapshot());
        }
    }
    private StoredResult executeFinance(CareerCommandGate.Owner owner, CareerCommandGate.Context context, FinanceRequest request,
                                       String expectedJournalToken, FinanceRequestStore requests, String expectedRequestToken, Handler handler, WitnessTarget witness) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Persist requests off EDT");
        Objects.requireNonNull(request); Objects.requireNonNull(requests); Objects.requireNonNull(handler);
        String ownerId = gate.ownerId(owner);
        if (!requests.matches(context, ownerId) || !journal.matches(context, ownerId) || !request.matches(context, ownerId)
                || !handler.persistentRequest().equals(Optional.of(request)) || !handler.accepts(request.intent()) || !handler.acceptsContext(context, ownerId))
            throw new IllegalArgumentException("Stored financial request/handler/context scope mismatch");
        journal.reviewStoredRequest(context.save(), expectedJournalToken, request.intent());
        var committed = requests.persist(request, expectedRequestToken);
        journal.reviewStoredRequest(context.save(), expectedJournalToken, request.intent());
        return new StoredResult(executeInternal(owner, context, request.intent(), expectedJournalToken, handler, request, witness), committed);
    }
    private Result executeInternal(CareerCommandGate.Owner owner, CareerCommandGate.Context context, CareerCommandJournal.Intent intent,
                                    String expectedJournalToken, Handler handler, Object stored) throws Exception {
        return executeInternal(owner, context, intent, expectedJournalToken, handler, stored, null);
    }
    private Result executeInternal(CareerCommandGate.Owner owner, CareerCommandGate.Context context, CareerCommandJournal.Intent intent,
                                    String expectedJournalToken, Handler handler, Object stored, WitnessTarget witness) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Journal dispatch must start off EDT");
        Objects.requireNonNull(context); Objects.requireNonNull(intent); Objects.requireNonNull(handler);
        if (!journal.matches(context, gate.ownerId(owner)))
            return new Result(Kind.DENIED, Detail.BINDING_MISMATCH, null, Optional.empty());
        if (!handler.accepts(intent) || !handler.acceptsContext(context, gate.ownerId(owner)))
            return new Result(Kind.DENIED, Detail.HANDLER_MISMATCH, null, Optional.empty());
        if ((handler.persistentRequest().isPresent() && !handler.persistentRequest().equals(Optional.ofNullable(stored)))
                || (handler.persistentLoanRequest().isPresent() && !handler.persistentLoanRequest().equals(Optional.ofNullable(stored))))
            return new Result(Kind.DENIED, Detail.REQUEST_REQUIRED, null, Optional.empty());
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
            gate.retainDispatch(permit);
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
            FinanceExecutionWitnessStore.Armed armed = null;
            CareerCommandJournal.ExecutionPermit executionProof = null;
            if (witness != null) {
                try {
                    executionProof = journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token());
                    armed = witness.store().arm(context, executionProof, witness.token());
                } catch (IOException | RuntimeException failure) {
                    var receipt = gate.cancel(permit); gate.invalidate();
                    return new Result(Kind.UNCERTAIN, Detail.WITNESS_FAILURE, receipt, Optional.of(prepared.snapshot()));
                }
            }
            var dispatch = new Dispatch(permit, handler, executionProof); SwingUtilities.invokeLater(dispatch);
            Execution executed;
            try { executed = await(dispatch.completion); }
            catch (TimeoutException timeout) { executed = dispatch.timeout(); }
            if (armed != null) {
                var state = switch (executed.observedOutcome()) {
                    case APPLIED -> FinanceExecutionWitnessStore.State.MEMORY_APPLIED;
                    case REJECTED -> FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED;
                    default -> FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN;
                };
                try { rememberInterruption(); witness.store().complete(armed.lease(), state, armed.snapshot().token()); }
                catch (IOException | RuntimeException failure) {
                    var receipt = gate.uncertain(permit); gate.invalidate();
                    return new Result(Kind.UNCERTAIN, Detail.WITNESS_FAILURE, receipt, Optional.of(prepared.snapshot()));
                }
            }
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
            if (permit != null) {
                gate.cancel(permit); // Only still-RESERVED work can be cancelled without assuming rollback.
                gate.releaseDispatch(permit);
            }
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
    private record Execution(CareerCommandGate.Receipt receipt, Detail detail, CareerCommandGate.Status observedOutcome) { }
    private final class Dispatch implements Runnable {
        private final CareerCommandGate.Permit permit;
        private final Handler handler;
        private final CareerCommandJournal.ExecutionPermit executionProof;
        private final CompletableFuture<Execution> completion = new CompletableFuture<>();
        private boolean entered, cancelled;
        Dispatch(CareerCommandGate.Permit permit, Handler handler, CareerCommandJournal.ExecutionPermit executionProof) {
            this.permit = permit; this.handler = handler; this.executionProof = executionProof;
        }
        @Override public void run() {
            synchronized (this) { if (cancelled) return; entered = true; }
            Roots current = null; boolean invocation = false;
            try {
                current = roots.current(); var start = gate.begin(permit, current.primary(), current.secondary());
                if (!start.invocationGranted()) { completion.complete(new Execution(start.receipt(), Detail.GATE_REFUSED, CareerCommandGate.Status.REJECTED)); return; }
                invocation = true;
                var outcome = handler.apply();
                if (outcome != CareerCommandGate.Status.APPLIED && outcome != CareerCommandGate.Status.REJECTED && outcome != CareerCommandGate.Status.OUTCOME_UNKNOWN)
                    throw new IllegalStateException("Handler returned nonterminal result");
                Roots after = roots.current();
                var observed = current != null && after != null && current.primary() == after.primary() && current.secondary() == after.secondary()
                        ? outcome : CareerCommandGate.Status.OUTCOME_UNKNOWN;
                var receipt = gate.finish(permit, outcome, after.primary(), after.secondary());
                if (executionProof != null && observed == CareerCommandGate.Status.APPLIED && receipt.status() == CareerCommandGate.Status.APPLIED) {
                    try { handler.observeSerialization(executionProof); } catch (Throwable diagnosticFailure) { /* No change to effect/receipt. */ }
                }
                completion.complete(new Execution(receipt, Detail.NONE, observed));
            } catch (Throwable failure) {
                var receipt = invocation ? gate.finish(permit, CareerCommandGate.Status.OUTCOME_UNKNOWN,
                        current == null ? null : current.primary(), current == null ? null : current.secondary()) : gate.cancel(permit);
                completion.complete(new Execution(receipt, Detail.HANDLER_FAILURE, invocation ? CareerCommandGate.Status.OUTCOME_UNKNOWN : CareerCommandGate.Status.REJECTED));
            }
        }
        synchronized Execution timeout() {
            if (completion.isDone()) return completion.join();
            if (!entered) { cancelled = true; return new Execution(gate.cancel(permit), Detail.QUEUE_TIMEOUT, CareerCommandGate.Status.REJECTED); }
            return new Execution(gate.uncertain(permit), Detail.EXECUTION_TIMEOUT, CareerCommandGate.Status.OUTCOME_UNKNOWN);
        }
    }
}
