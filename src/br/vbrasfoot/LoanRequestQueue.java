package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import java.util.*;

/** Bounded session-only pending requests. No persistence, permission grant, scheduling or game writes. */
final class LoanRequestQueue implements AutoCloseable {
    enum State { PENDING, APPROVED, REFUSED, INSUFFICIENT_DATA, CANCELLED, INVALIDATED }
    record Proposal(String key, PlayerRef player, ClubRef origin, ClubRef destination,
                    int candidateStrength, List<Integer> destinationStrengths) {
        Proposal {
            if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid request key");
            Objects.requireNonNull(player); Objects.requireNonNull(origin); Objects.requireNonNull(destination);
            if (!player.scope().equals(origin.scope()) || !origin.scope().equals(destination.scope()))
                throw new IllegalArgumentException("Exact query scope required");
            // Distinct handles do NOT prove distinct club objects. A future adapter must revalidate that.
            if (origin.equals(destination)) throw new IllegalArgumentException("Same club handle");
            destinationStrengths = List.copyOf(destinationStrengths);
            if (destinationStrengths.size() > 500) throw new IllegalArgumentException("Bounded roster required");
        }
    }
    record Request(Proposal proposal, State state, Optional<LoanClubAssessment.Result> assessment) { }
    private final LinkedHashMap<String, Request> requests = new LinkedHashMap<>();
    private final CareerScope scope;
    private boolean closed;

    LoanRequestQueue(CareerScope scope) { this.scope = Objects.requireNonNull(scope); }

    synchronized Request submit(Proposal proposal) {
        requireOpen(); Objects.requireNonNull(proposal);
        if (!scope.equals(proposal.player().scope())) throw new IllegalArgumentException("Foreign query scope");
        Request previous = requests.get(proposal.key());
        if (previous != null) {
            if (!previous.proposal().equals(proposal)) throw new IllegalArgumentException("Request key conflict");
            return previous; // Never recreate/re-evaluate an existing request, even if cancelled or invalidated.
        }
        if (requests.size() >= 32) throw new IllegalStateException("Session request capacity exhausted");
        Request pending = new Request(proposal, State.PENDING, Optional.empty());
        requests.put(proposal.key(), pending);
        return pending;
    }

    /** Explicit pure assessment step. APPROVED is a recommendation, never an executed loan. */
    synchronized Request evaluate(String key) {
        requireOpen(); Request current = requireRequest(key);
        if (current.state() != State.PENDING) return current;
        var proposal = current.proposal();
        var assessment = LoanClubAssessment.evaluate(proposal.candidateStrength(), proposal.destinationStrengths());
        State state = switch (assessment.decision()) {
            case APPROVE -> State.APPROVED;
            case REFUSE -> State.REFUSED;
            case INSUFFICIENT_DATA -> State.INSUFFICIENT_DATA;
        };
        Request result = new Request(proposal, state, Optional.of(assessment));
        requests.put(key, result); return result;
    }

    synchronized Request cancel(String key) {
        requireOpen(); Request current = requireRequest(key);
        if (current.state() != State.PENDING && current.state() != State.APPROVED) return current;
        Request cancelled = new Request(current.proposal(), State.CANCELLED, current.assessment());
        requests.put(key, cancelled); return cancelled;
    }

    /** Host must call on invalidation; matching scope alone never proves that roster inputs are fresh. */
    synchronized void invalidate() {
        requests.replaceAll((key, request) -> request.state() == State.PENDING || request.state() == State.APPROVED
                ? new Request(request.proposal(), State.INVALIDATED, request.assessment()) : request);
    }
    synchronized List<Request> snapshot() { return List.copyOf(requests.values()); }
    private Request requireRequest(String key) {
        Request request = requests.get(key);
        if (request == null) throw new IllegalArgumentException("Unknown request key");
        return request;
    }
    private void requireOpen() { if (closed) throw new IllegalStateException("Request queue closed"); }
    @Override public synchronized void close() { invalidate(); closed = true; }
}
