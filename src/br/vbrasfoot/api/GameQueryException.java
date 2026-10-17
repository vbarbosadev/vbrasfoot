package br.vbrasfoot.api;

/** Actionable read failures; refresh your scoped club page before retrying stale references. */
public final class GameQueryException extends IllegalStateException {
    public enum Reason { NO_CAREER, STALE_SCOPE, EXPIRED_REFERENCE, CLUB_MOVED, STATE_CHANGED }
    private final Reason reason;
    public GameQueryException(Reason reason, String message) { super(message); this.reason = java.util.Objects.requireNonNull(reason); }
    public Reason reason() { return reason; }
}
