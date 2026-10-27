package br.vbrasfoot.api;

import java.io.IOException;
import java.util.UUID;

/** Experimental human-club loan commands. Call from a worker; explicitly confirm the exact quote before accept. */
public interface LoanAccess {
    record Quote(UUID token, String player, String origin, String destination, int fee, int days, boolean listed) { }
    enum State { APPLIED_IN_MEMORY, NO_EFFECT, REVIEW_REQUIRED }
    record Result(State state, String message) { }
    /** Identifies the loaded save; unknown careers require human identity confirmation. No gameplay effect. */
    CareerDataAccess.Scope bindCareer() throws IOException;
    /** Read-only, one outstanding quote per mod service. Temporary scoped references only. */
    Quote quote(CareerDataAccess.Scope career, GameAccess.PlayerRef player, GameAccess.ClubRef origin,
                GameAccess.ClubRef destination) throws IOException;
    /** One attempt, including exceptions. Never retry automatically or reconstruct a token after restart. */
    Result accept(Quote quote) throws IOException;
}
