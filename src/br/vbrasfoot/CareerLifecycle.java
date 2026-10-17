package br.vbrasfoot;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Internal synchronous guard, no user callbacks/I/O/EDT operations under its lock. */
final class CareerLifecycle {
    enum Phase { EMPTY, UNKNOWN, ACTIVE, CLOSED }
    record Ticket(String session, long generation, boolean active) { }
    record Attempt(String session, long id, long interference, boolean exclusive) { }
    record Snapshot(String session, long generation, Phase phase, String loadedFile, boolean loadedBackup,
                    String lastSavedFile, long loads, long saves, long invalidations, long staleCompletions,
                    long unboundSaves, int inFlight, boolean healthy) { }
    private final String session = UUID.randomUUID().toString();
    private long generation, loads, saves, invalidations, staleCompletions, unboundSaves;
    private Phase phase = Phase.EMPTY;
    private String loadedFile, lastSavedFile;
    private boolean loadedBackup;
    private long nextAttempt, interference;
    private final Set<Long> inFlight = new HashSet<>();
    private boolean healthy = true;

    synchronized Ticket ticket() { return new Ticket(session, generation, phase == Phase.ACTIVE); }
    synchronized Attempt beginMutation() {
        if (phase == Phase.CLOSED || !healthy) return null;
        if (inFlight.size() >= 64) { healthy = false; rootsChanged(); return null; }
        boolean exclusive = inFlight.isEmpty();
        if (!exclusive) interference++;
        Attempt attempt = new Attempt(session, ++nextAttempt, interference, exclusive);
        inFlight.add(attempt.id());
        rootsChanged(); // Invalidate early; a failing attempt can leave partially replaced roots.
        return attempt;
    }
    private boolean finishMutation(Attempt attempt) {
        if (attempt == null || !session.equals(attempt.session()) || !inFlight.remove(attempt.id())) return false;
        return healthy && attempt.exclusive() && attempt.interference() == interference && inFlight.isEmpty();
    }
    synchronized Ticket rootsChanged() {
        if (phase == Phase.CLOSED) return ticket();
        generation++; invalidations++;
        phase = Phase.UNKNOWN; loadedFile = null; lastSavedFile = null; loadedBackup = false;
        return ticket();
    }
    private boolean current(Ticket ticket) { return ticket != null && session.equals(ticket.session()) && ticket.generation() == generation; }
    synchronized boolean loaded(Attempt attempt, Ticket decoded, String file, boolean backup, boolean completed) {
        boolean exclusive = finishMutation(attempt);
        if (!completed) return false;
        if (phase == Phase.CLOSED || !exclusive || file == null || !current(decoded)) { staleCompletions++; return false; }
        phase = Phase.ACTIVE; loadedFile = file; loadedBackup = backup; lastSavedFile = null; loads++;
        return true;
    }
    synchronized void resetFinished(Attempt attempt) {
        finishMutation(attempt);
        rootsChanged(); // New roots / setter are not a proven loaded career.
    }
    synchronized boolean saved(Ticket started, String file) {
        if (phase == Phase.CLOSED || !healthy || !inFlight.isEmpty() || !current(started)) { staleCompletions++; return false; }
        if (!started.active() || phase != Phase.ACTIVE || file == null) { unboundSaves++; return false; }
        lastSavedFile = file; saves++;
        return true;
    }
    synchronized Snapshot snapshot() {
        return new Snapshot(session, generation, phase, loadedFile, loadedBackup, lastSavedFile,
                loads, saves, invalidations, staleCompletions, unboundSaves, inFlight.size(), healthy);
    }
    synchronized void close() {
        if (phase == Phase.CLOSED) return;
        generation++; phase = Phase.CLOSED; loadedFile = null; lastSavedFile = null; loadedBackup = false;
        inFlight.clear();
    }
    synchronized Snapshot closeAndSnapshot() {
        Snapshot before = snapshot(); close(); return before;
    }
}
