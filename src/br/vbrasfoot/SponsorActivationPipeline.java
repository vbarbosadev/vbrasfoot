package br.vbrasfoot;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import javax.swing.SwingUtilities;

/** Durable sponsor tombstone before the existing finance journal/witness pipeline.
 * No save-inclusion claim, retry, association discovery, or automatic activation. */
final class SponsorActivationPipeline {
    enum Detail { NONE, FINANCE_FAILURE, ACTIVATION_COMMIT_FAILURE }
    record Result(CareerCommandDispatcher.Kind kind, Optional<CareerCommandDispatcher.Result> finance,
                  SponsorActivationStore.Snapshot activation, Detail detail) { }
    private final CareerCommandGate gate;
    private final CareerCommandJournal journal;
    private final FinanceRequestStore contracts;
    private final FinanceExecutionWitnessStore witnesses;
    private final SponsorActivationStore activations;
    private final CareerCommandDispatcher dispatcher;

    SponsorActivationPipeline(CareerCommandGate gate, CareerCommandJournal journal, FinanceRequestStore contracts,
            FinanceExecutionWitnessStore witnesses, SponsorActivationStore activations,
            CareerCommandDispatcher.RootsSource roots, Duration timeout) {
        this.gate = Objects.requireNonNull(gate); this.journal = Objects.requireNonNull(journal);
        this.contracts = Objects.requireNonNull(contracts); this.witnesses = Objects.requireNonNull(witnesses);
        this.activations = Objects.requireNonNull(activations);
        dispatcher = new CareerCommandDispatcher(gate, journal, roots, timeout);
    }

    /** Caller needs an independently qualified live context, exact reviewed terms and explicit confirmation.
     * Synthetic tests grant artificial authority, independently of production authorization. */
    Result execute(UUID sponsor, String termsDigest, CareerCommandGate.Owner owner, CareerCommandGate.Context context,
            FinanceRequest request, String activationToken, String journalToken, String contractToken, String witnessToken,
            CareerCommandDispatcher.Handler handler) throws Exception {
        return execute(sponsor, termsDigest, owner, context, request, activationToken, journalToken, contractToken, witnessToken, handler, "", "");
    }
    Result execute(UUID sponsor, String termsDigest, CareerCommandGate.Owner owner, CareerCommandGate.Context context,
            FinanceRequest request, String activationToken, String journalToken, String contractToken, String witnessToken,
            CareerCommandDispatcher.Handler handler, String stadiumBefore, String stadiumAfter) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Sponsor activation must start off EDT");
        String ownerId = gate.ownerId(owner);
        if (!SponsorActivationStore.OWNER.equals(ownerId) || !request.matches(context, ownerId)
                || !request.key().equals(SponsorActivationStore.key(sponsor)) || !gate.currentContext(context)
                || !journal.matches(context, ownerId) || !contracts.matches(context, ownerId) || !witnesses.matches(context, ownerId)
                || !handler.supportsExecutionWitness() || !handler.persistentRequest().equals(Optional.of(request))
                || !handler.accepts(request.intent()) || !handler.acceptsContext(context, ownerId))
            throw new IllegalArgumentException("Qualified sponsor credit context and matching trusted handler required");
        gate.retainCompound(context);
        try {
        // Historical journal state never becomes a new sponsor claim, even if a caller supplies a fresh ledger.
        var history = journal.read(context.save());
        if (!history.token().equals(journalToken) || history.receipts().stream().anyMatch(r -> r.intent().key().equals(request.key())))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Historical/changed financial journal; no new activation");
        var claim = activations.claim(sponsor, termsDigest, request, activationToken, stadiumBefore, stadiumAfter);
        CareerCommandDispatcher.Result finance = null;
        SponsorActivationStore.State terminal = SponsorActivationStore.State.UNKNOWN;
        Detail detail = Detail.NONE;
        try {
            finance = dispatcher.executeWitnessedFinance(owner, context, request, journalToken, contracts, contractToken,
                    witnesses, witnessToken, handler).dispatch();
            terminal = switch (finance.kind()) {
                case EXECUTED -> SponsorActivationStore.State.MEMORY_APPLIED;
                case NO_EFFECT -> SponsorActivationStore.State.NO_EFFECT;
                default -> SponsorActivationStore.State.UNKNOWN;
            };
        } catch (Exception failure) {
            // An exception is not a no-effect proof. Keep the original durable claim, never re-enqueue.
            gate.invalidate(); detail = Detail.FINANCE_FAILURE;
        }
        // Sidecar I/O must finish with interruption cleared; restore caller interruption afterwards.
        boolean interrupted = Thread.interrupted();
        try {
            var completed = activations.complete(claim.lease(), claim.snapshot().token(), terminal);
            var kind = terminal == SponsorActivationStore.State.MEMORY_APPLIED ? CareerCommandDispatcher.Kind.EXECUTED
                    : terminal == SponsorActivationStore.State.NO_EFFECT ? CareerCommandDispatcher.Kind.NO_EFFECT : CareerCommandDispatcher.Kind.UNCERTAIN;
            return new Result(kind, Optional.ofNullable(finance), completed, detail);
        } catch (IOException | RuntimeException uncertainCommit) {
            gate.invalidate();
            return new Result(CareerCommandDispatcher.Kind.UNCERTAIN, Optional.ofNullable(finance), claim.snapshot(), Detail.ACTIVATION_COMMIT_FAILURE);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        } finally { gate.releaseCompound(context); }
    }
}
