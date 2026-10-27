package br.vbrasfoot.api;

import java.util.function.Consumer;

/** Qualified, opt-in session events (loader 0.8.0+), NOT a durable or complete career lifecycle. */
public interface CareerIoEvents {
    enum Phase { EMPTY, UNKNOWN, ACTIVE, CLOSED }
    enum Kind { CURRENT_STATE, INVALIDATED, EXISTING_LOAD_COMPLETED, SAVE_SEQUENCE_COMPLETED }
    /** Counts are session aggregates. Session/generation are not persistent identities or query scopes. */
    record State(String session, long generation, long revision, Phase phase,
                 long existingLoads, long completedSaves, boolean healthy) { }
    /** Backup input denotes the accepted read, not a guarantee for a future save. No paths or legacy objects. */
    record Update(Kind kind, State state, boolean backupInput) { }
    /** Current state independent of callback loss. CLOSED is disposal, not CareerUnloaded. */
    State state();
    /**
     * Replay CURRENT_STATE then ordered revisions on a bounded worker. Shared limits with careerObservations.
     * EXISTING_LOAD_COMPLETED requires the observed initialization branch and accepted exclusive read.
     * SAVE_SEQUENCE_COMPLETED requires that qualified generation and all observed metadata/primary stream/backup stages.
     * Neither proves fsync, graph consistency, atomic files, creation readiness or persistent identity.
     * Overflow/failure closes only the subscription; recover explicitly by close/resubscribe.
     */
    CareerObservations.Subscription subscribe(Consumer<Update> callback);
}
