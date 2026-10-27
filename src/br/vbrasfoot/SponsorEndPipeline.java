package br.vbrasfoot;

import java.time.Duration;
import java.util.*;
import javax.swing.SwingUtilities;

/** One durable ending claim followed by original stadium-name restoration; no financial invocation. */
final class SponsorEndPipeline {
    static final String OWNER = "br.vbrasfoot.sponsor-end";
    record Result(CareerCommandDispatcher.Kind kind, SponsorActivationStore.Snapshot activation) { }
    private final CareerCommandGate gate;
    private final CareerCommandJournal journal;
    private final SponsorActivationStore activations;
    private final CareerCommandDispatcher dispatcher;
    SponsorEndPipeline(CareerCommandGate gate, CareerCommandJournal journal, SponsorActivationStore activations,
                       CareerCommandDispatcher.RootsSource roots, Duration timeout) {
        this.gate = gate; this.journal = journal; this.activations = activations;
        dispatcher = new CareerCommandDispatcher(gate, journal, roots, timeout);
    }
    Result execute(CareerCommandGate.Owner owner, CareerCommandGate.Context context, SponsorActivationStore.Entry activation,
                   SponsorActivationStore.EndAttempt ending, String activationToken, String journalToken,
                   CareerCommandDispatcher.Handler handler) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("End sponsor off EDT");
        var request = activation.request(); var intent = SponsorActivationStore.endIntent(activation, ending);
        if (!OWNER.equals(gate.ownerId(owner)) || !request.career().equals(context.career()) || !request.binding().equals(context.binding())
                || !ending.save().equals(context.save()) || !gate.currentContext(context) || !journal.matches(context, OWNER)
                || !handler.accepts(intent) || !handler.acceptsContext(context, OWNER)
                || handler.persistentRequest().isPresent() || handler.persistentLoanRequest().isPresent())
            throw new IllegalArgumentException("Qualified ending context/handler required; no financial request allowed");
        gate.retainCompound(context);
        try {
            var current = activations.read();
            if (!current.token().equals(activationToken) || current.entries().stream().noneMatch(activation::equals))
                throw new IllegalStateException("Ativacao mudou antes do encerramento");
            var claim = activations.claimEnd(activation.sponsor(), ending, activationToken);
            SponsorActivationStore.EndState terminal = SponsorActivationStore.EndState.UNKNOWN;
            try {
                var result = dispatcher.execute(owner, context, intent, journalToken, handler);
                terminal = switch (result.kind()) {
                    case EXECUTED -> SponsorActivationStore.EndState.ENDED;
                    case NO_EFFECT -> SponsorActivationStore.EndState.NO_EFFECT;
                    default -> SponsorActivationStore.EndState.UNKNOWN;
                };
            } catch (Exception uncertain) { gate.invalidate(); }
            boolean interrupted = Thread.interrupted();
            try {
                var completed = activations.completeEnd(claim.lease(), claim.snapshot().token(), terminal);
                return new Result(terminal == SponsorActivationStore.EndState.ENDED ? CareerCommandDispatcher.Kind.EXECUTED
                        : terminal == SponsorActivationStore.EndState.NO_EFFECT ? CareerCommandDispatcher.Kind.NO_EFFECT
                        : CareerCommandDispatcher.Kind.UNCERTAIN, completed);
            } catch (java.io.IOException | RuntimeException failedCommit) {
                gate.invalidate(); return new Result(CareerCommandDispatcher.Kind.UNCERTAIN, claim.snapshot());
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        } finally { gate.releaseCompound(context); }
    }
}
