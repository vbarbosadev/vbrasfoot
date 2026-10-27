package br.vbrasfoot.api;

import java.io.IOException;
import java.util.UUID;

/** Explicit experimental sponsor operations for the supported game profile. No periodic payments or automatic retry. */
public interface SponsorAccess {
    enum State { NOT_ACTIVATED, APPLIED_IN_MEMORY, NO_EFFECT, REVIEW_REQUIRED, ENDED, PENDING_RECEIPT }
    record Quote(UUID token, UUID sponsor, String club, String sponsorName, int credit,
                 long balanceBefore, long balanceAfter, String stadiumBefore, String stadiumAfter) { }
    record Result(State state, String message) { }
    record EndQuote(UUID token, UUID sponsor, String club, long balance,
                    String stadiumCurrent, String stadiumOriginal, boolean changedSinceActivation) { }
    /** Shows an explicit loader-owned association confirmation on the EDT; call from a worker. */
    CareerDataAccess.Scope bindCareer() throws IOException;
    /** Read-only snapshot for a separately confirmed activation, valid only for this session. */
    Quote quote(CareerDataAccess.Scope career, UUID sponsor, int clubId, String clubName,
                String sponsorName, int credit, String stadiumName) throws IOException;
    /** Call only after showing and confirming the exact quote. Consumes the token even on failure. */
    Result activate(Quote quote) throws IOException;
    /** Reads durable history, never replays a payment or infers inclusion in a save from a balance. */
    State state(CareerDataAccess.Scope career, UUID sponsor) throws IOException;
    /** Publishes a durable message without crediting money or changing the stadium. Loader 0.14.0+. */
    default Result publish(CareerDataAccess.Scope career, UUID sponsor, int clubId, String clubName,
                           String sponsorName, int credit, String stadiumName, String message) throws IOException {
        throw new UnsupportedOperationException("Mensagens exigem loader 0.14.0");
    }
    /** Cancels only an unpaid offer with no financial attempt. */
    default Result cancelPending(CareerDataAccess.Scope career, UUID sponsor) throws IOException {
        throw new UnsupportedOperationException("Mensagens exigem loader 0.14.0");
    }
    /** Read-only review of the recorded original name. Does not debit/refund/recredit any money. Loader 0.13.0+. */
    default EndQuote quoteEnd(CareerDataAccess.Scope career, UUID sponsor) throws IOException {
        throw new UnsupportedOperationException("Encerramento exige loader 0.13.0");
    }
    /** Explicit confirmation required, including any manually changed stadium name. Token is one-shot. */
    default Result end(EndQuote quote) throws IOException {
        throw new UnsupportedOperationException("Encerramento exige loader 0.13.0");
    }
}
