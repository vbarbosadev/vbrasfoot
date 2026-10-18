package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;

/** One bounded weak observation of decoder provenance, NOT career readiness or a write authority. */
final class DecodedSaveProvenance {
    enum Status { EMPTY, INVALIDATED, REJECTED, STREAM_BOUND }
    enum Reason { NONE, LOAD_STARTED, ROOT_CHANGED, RESET_STARTED, SAVE_STARTED, DETACHED, UNACCEPTED_LOAD,
        UNRELIABLE_STREAM, INVALID_SOURCE, MISSING_ROOTS }
    static final class Binding {
        private final String session;
        private final long generation, revision;
        private final Path source;
        private final CareerSidecarStore.SaveRevision bytes;
        private final boolean backup, initializedPath;
        private final WeakReference<Object> primary, secondary;
        private Binding(CareerLifecycle.Ticket ticket, long revision, Path source, CareerSidecarStore.SaveRevision bytes,
                        boolean backup, boolean initializedPath, Object primary, Object secondary) {
            session = ticket.session(); generation = ticket.generation(); this.revision = revision; this.source = source; this.bytes = bytes;
            this.backup = backup; this.initializedPath = initializedPath;
            this.primary = new WeakReference<>(primary); this.secondary = new WeakReference<>(secondary);
        }
        String session() { return session; } long generation() { return generation; } long revision() { return revision; }
        Path source() { return source; } CareerSidecarStore.SaveRevision bytes() { return bytes; }
        boolean backup() { return backup; } boolean initializedPath() { return initializedPath; }
        private boolean matches(CareerLifecycle.Ticket ticket, Object primary, Object secondary) {
            return ticket != null && ticket.active() && session.equals(ticket.session()) && generation == ticket.generation()
                    && primary != null && secondary != null && this.primary.get() == primary && this.secondary.get() == secondary;
        }
    }
    record Snapshot(long revision, Status status, Reason reason, Optional<Binding> binding) { }
    private long revision;
    private Status status = Status.EMPTY;
    private Reason reason = Reason.NONE;
    private Binding current;
    synchronized void invalidate(Reason why) {
        revision++; current = null; status = Status.INVALIDATED; reason = Objects.requireNonNull(why);
    }
    synchronized void accept(long expectedRevision, CareerLifecycle.Ticket ticket, Path source, LoadInputTrace.Result stream, boolean backup,
                             boolean initializedPath, Object primary, Object secondary, boolean accepted) {
        if (revision != expectedRevision) return; // An older completion must never replace newer invalidation/evidence.
        revision++; current = null; status = Status.REJECTED;
        if (!accepted || ticket == null || !ticket.active()) { reason = Reason.UNACCEPTED_LOAD; return; }
        if (source == null || !source.isAbsolute() || source.getFileName() == null || !source.equals(source.normalize())) { reason = Reason.INVALID_SOURCE; return; }
        try { SaveFileObserver.validateName(source.getFileName().toString()); }
        catch (IllegalArgumentException invalid) { reason = Reason.INVALID_SOURCE; return; }
        if (stream == null || !stream.reliable()) { reason = Reason.UNRELIABLE_STREAM; return; }
        if (primary == null || secondary == null) { reason = Reason.MISSING_ROOTS; return; }
        current = new Binding(ticket, revision, source, new CareerSidecarStore.SaveRevision(stream.sha256(), stream.bytes()),
                backup, initializedPath, primary, secondary);
        status = Status.STREAM_BOUND; reason = Reason.NONE;
    }
    synchronized Snapshot snapshot() { return new Snapshot(revision, status, reason, Optional.ofNullable(current)); }
    synchronized boolean current(Binding binding, CareerLifecycle.Ticket ticket, Object primary, Object secondary) {
        return binding != null && current == binding && binding.matches(ticket, primary, secondary);
    }
}
