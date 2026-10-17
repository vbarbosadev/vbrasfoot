package br.vbrasfoot.api;

import java.util.function.Consumer;

/** Experimental, session-only observations. Requires opt-in hooks; NOT a durable event log. */
public interface CareerObservations {
    enum Phase { EMPTY, UNKNOWN, ACTIVE, CLOSED }
    enum Kind { CURRENT_STATE, INVALIDATED, READ_COMPLETED, PRIMARY_WRITE_COMPLETED }
    enum Status { ACTIVE, CLOSED, OVERFLOW, CALLBACK_FAILED }

    /** No game objects or filenames. Generation is not a persistent identity or SDK query scope. */
    record State(String session, long generation, long revision, Phase phase,
                 long reads, long primaryWrites, boolean healthy) { }
    /** READ_COMPLETED backup identifies fallback/backup input; writes do not guarantee backup or fsync. */
    record Update(Kind kind, State state, boolean backupInput) { }
    interface Subscription extends AutoCloseable {
        Status status();
        /** Stops delivery; an already selected callback may still start/finish. Does not wait on the EDT. */
        @Override void close();
    }

    /** Current immutable state, independent of subscriber loss. CLOSED does not mean CareerUnloaded. */
    State state();
    /**
     * Replays CURRENT_STATE, then ordered revisions on an owned daemon worker, never the game thread.
     * Queue overflow or callback failure terminates only this subscription; poll status and state.
     * Close and resubscribe explicitly to recover. No retries, cancellation of game actions or durable delivery.
     */
    Subscription subscribe(Consumer<Update> callback);
}
