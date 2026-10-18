package br.vbrasfoot;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Internal synchronous guard, no user callbacks/I/O/EDT operations under its lock. */
final class CareerLifecycle {
    private final CareerObservationHub observations;
    private long revision;
    enum Phase { EMPTY, UNKNOWN, ACTIVE, CLOSED }
    record Ticket(String session, long generation, boolean active) { }
    record Attempt(String session, long id, long interference, boolean exclusive) { }
    record SaveAttempt(Ticket ticket, long id, long interference, boolean exclusive) { }
    /** Observed original stages, never a durability or graph-consistency guarantee. */
    record SaveOutcome(boolean metadataClosed, boolean primaryClosed, boolean backupCopied,
                       boolean returned, boolean bound, boolean completeSequence, boolean streamClosed, boolean streamCloseFailed) { }
    record Snapshot(String session, long generation, Phase phase, String loadedFile, boolean loadedBackup,
                    String lastSavedFile, long loads, long saves, long invalidations, long staleCompletions,
                    long unboundSaves, int inFlight, boolean healthy, long initializedReadPaths,
                    long completeSaveSequences, long partialSaveSequences, long failedSaveSequences,
                    int inFlightSaves, SaveOutcome lastSaveOutcome, long streamCloseFailures) { }
    private final String session = UUID.randomUUID().toString();
    private long generation, loads, saves, invalidations, staleCompletions, unboundSaves;
    private Phase phase = Phase.EMPTY;
    private String loadedFile, lastSavedFile;
    private boolean loadedBackup;
    private long nextAttempt, interference;
    private final Set<Long> inFlight = new HashSet<>();
    private final Set<Long> inFlightSaves = new HashSet<>();
    private long saveInterference, initializedReadPaths, completeSaveSequences, partialSaveSequences, failedSaveSequences;
    private SaveOutcome lastSaveOutcome;
    private long streamCloseFailures;
    private boolean healthy = true;

    CareerLifecycle() { this(null); }
    CareerLifecycle(CareerObservationHub observations) {
        this.observations = observations;
        if (observations != null) observations.initialize(publicState());
    }
    private br.vbrasfoot.api.CareerObservations.State publicState() {
        return new br.vbrasfoot.api.CareerObservations.State(session, generation, revision,
                br.vbrasfoot.api.CareerObservations.Phase.valueOf(phase.name()), loads, saves, healthy);
    }
    private void publish(br.vbrasfoot.api.CareerObservations.Kind kind, boolean backup) {
        revision++;
        if (observations != null) observations.publish(new br.vbrasfoot.api.CareerObservations.Update(kind, publicState(), backup));
    }

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
        publish(br.vbrasfoot.api.CareerObservations.Kind.INVALIDATED, false);
        return ticket();
    }
    private boolean current(Ticket ticket) { return ticket != null && session.equals(ticket.session()) && ticket.generation() == generation; }
    synchronized boolean loaded(Attempt attempt, Ticket decoded, String file, boolean backup, boolean completed) {
        return loaded(attempt, decoded, file, backup, completed, false);
    }
    synchronized boolean loaded(Attempt attempt, Ticket decoded, String file, boolean backup, boolean completed, boolean initializedPath) {
        boolean exclusive = finishMutation(attempt);
        if (!completed) return false;
        if (phase == Phase.CLOSED || !exclusive || file == null || !current(decoded)) { staleCompletions++; return false; }
        phase = Phase.ACTIVE; loadedFile = file; loadedBackup = backup; lastSavedFile = null; loads++;
        if (initializedPath) initializedReadPaths++;
        publish(br.vbrasfoot.api.CareerObservations.Kind.READ_COMPLETED, backup);
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
        publish(br.vbrasfoot.api.CareerObservations.Kind.PRIMARY_WRITE_COMPLETED, false);
        return true;
    }
    synchronized SaveAttempt beginSave() {
        if (phase == Phase.CLOSED || !healthy) return null;
        if (inFlightSaves.size() >= 64) { healthy = false; rootsChanged(); return null; }
        boolean exclusive = inFlightSaves.isEmpty();
        if (!exclusive) saveInterference++;
        SaveAttempt attempt = new SaveAttempt(ticket(), ++nextAttempt, saveInterference, exclusive);
        inFlightSaves.add(attempt.id());
        return attempt;
    }
    synchronized SaveOutcome finishSave(SaveAttempt attempt, String file, boolean metadata, boolean primary, boolean backup, boolean returned,
                                        boolean streamClosed, boolean streamFailed) {
        // No duplicate/foreign completion may replace the last valid diagnostic outcome.
        if (attempt == null || !session.equals(attempt.ticket().session()) || !inFlightSaves.remove(attempt.id())) return null;
        boolean exclusive = attempt.exclusive() && attempt.interference() == saveInterference && inFlightSaves.isEmpty();
        boolean bound = false;
        if (primary && returned && !streamFailed) {
            if (!exclusive) staleCompletions++;
            else bound = saved(attempt.ticket(), file);
        }
        boolean complete = metadata && primary && backup && returned && bound && streamClosed && !streamFailed;
        lastSaveOutcome = new SaveOutcome(metadata, primary, backup, returned, bound, complete, streamClosed, streamFailed);
        if (streamFailed) streamCloseFailures++;
        if (complete) completeSaveSequences++;
        else if (primary) partialSaveSequences++;
        else failedSaveSequences++;
        return lastSaveOutcome;
    }
    synchronized Snapshot snapshot() {
        return new Snapshot(session, generation, phase, loadedFile, loadedBackup, lastSavedFile,
                loads, saves, invalidations, staleCompletions, unboundSaves, inFlight.size(), healthy,
                initializedReadPaths, completeSaveSequences, partialSaveSequences, failedSaveSequences,
                inFlightSaves.size(), lastSaveOutcome, streamCloseFailures);
    }
    synchronized void close() {
        if (phase == Phase.CLOSED) return;
        generation++; phase = Phase.CLOSED; loadedFile = null; lastSavedFile = null; loadedBackup = false;
        inFlight.clear();
        inFlightSaves.clear();
        // Exposes disposal as state only; no invented unload callback during System.exit.
        revision++;
        if (observations != null) observations.finishState(publicState());
    }
    synchronized Snapshot closeAndSnapshot() {
        Snapshot before = snapshot(); close(); return before;
    }
}
