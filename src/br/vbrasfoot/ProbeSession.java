package br.vbrasfoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;

/** Owns diagnostic worker/subscription. Shutdown never queries legacy objects or Swing. */
final class ProbeSession implements AutoCloseable {
    private final ProbeDispatcher dispatcher;
    private final AutoCloseable subscription;
    private final AutoCloseable lifecycleSubscription;
    private final CareerObservationHub observations = new CareerObservationHub();
    private final CareerLifecycle lifecycle = new CareerLifecycle(observations);
    private final Thread shutdown;
    private final Logger logger;
    private final Path report;
    private boolean closed;
    br.vbrasfoot.api.CareerObservations observations() { return observations; }

    ProbeSession(Logger logger, Path report) {
        this.logger = logger; this.report = report;
        dispatcher = new ProbeDispatcher(128, Duration.ofMillis(500), observation -> logger.info("career-hook-probe=" + observation));
        AutoCloseable attached;
        AutoCloseable lifecycleAttached;
        try {
            lifecycleAttached = HookProbe.attachLifecycle(lifecycle);
            try { attached = HookProbe.attach(dispatcher::offer); }
            catch (RuntimeException failure) { lifecycleAttached.close(); throw failure; }
        }
        catch (Exception failure) { dispatcher.close(); throw new IllegalStateException("Falha ao instalar guard da sonda", failure); }
        subscription = attached;
        lifecycleSubscription = lifecycleAttached;
        shutdown = new Thread(this::finish, "vbrasfoot-probe-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
    }
    @Override public void close() {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException closingJvm) { }
        finish();
    }
    private synchronized void finish() {
        if (closed) return;
        closed = true;
        try { subscription.close(); } catch (Exception failure) { logger.warning("Could not detach probe subscription"); }
        try { lifecycleSubscription.close(); } catch (Exception failure) { logger.warning("Could not detach lifecycle guard"); }
        Properties candidates = new Properties();
        candidates.setProperty("format", "1");
        candidates.setProperty("coverage", "Candidate path/content observations only; not CareerLoaded, commit, persistent identity or proof of initialization");
        for (var entry : HookProbe.creationContent()) {
            String prefix = entry.label() + ".";
            candidates.setProperty(prefix + "starts", Long.toString(entry.starts()));
            candidates.setProperty(prefix + "returns", Long.toString(entry.returns()));
            candidates.setProperty(prefix + "failures", Long.toString(entry.failures()));
            candidates.setProperty(prefix + "rejected", Long.toString(entry.rejected()));
            candidates.setProperty(prefix + "rootsStable", Boolean.toString(entry.rootsStable()));
            candidates.setProperty(prefix + "listsStable", Boolean.toString(entry.listsStable()));
            candidates.setProperty(prefix + "generationStable", Boolean.toString(entry.generationStable()));
            candidates.setProperty(prefix + "clubsBefore", Integer.toString(entry.clubsBefore()));
            candidates.setProperty(prefix + "clubsAfter", Integer.toString(entry.clubsAfter()));
            candidates.setProperty(prefix + "humansBefore", Integer.toString(entry.humansBefore()));
            candidates.setProperty(prefix + "humansAfter", Integer.toString(entry.humansAfter()));
            candidates.setProperty(prefix + "readableAfter", Boolean.toString(entry.readableAfter()));
            candidates.setProperty(prefix + "outcome", entry.outcome());
        }
        int stepIndex = 0;
        for (var step : HookProbe.creationSteps()) {
            String prefix = "step." + stepIndex++ + ".";
            candidates.setProperty(prefix + "label", step.label());
            candidates.setProperty(prefix + "stage", step.stage());
            candidates.setProperty(prefix + "generation", Long.toString(step.generation()));
            candidates.setProperty(prefix + "clubs", Integer.toString(step.clubs()));
            candidates.setProperty(prefix + "humans", Integer.toString(step.humans()));
            candidates.setProperty(prefix + "dayIndex", Integer.toString(step.dayIndex()));
            candidates.setProperty(prefix + "year", Integer.toString(step.year()));
            candidates.setProperty(prefix + "month", Integer.toString(step.month()));
            candidates.setProperty(prefix + "day", Integer.toString(step.day()));
            candidates.setProperty(prefix + "calendarAvailable", Boolean.toString(step.calendarAvailable()));
        }
        candidates.setProperty("step.count", Integer.toString(stepIndex));
        try (var writer = Files.newBufferedWriter(report.resolveSibling("creation-candidates.properties"))) { candidates.store(writer, "Bounded creation content metadata, no game objects or filenames"); }
        catch (Exception failure) { System.err.println("Falha ao registrar candidatos de criacao: " + failure); }
        // Internal session disposal, NOT CareerUnloaded delivered to a mod.
        CareerLifecycle.Snapshot finalState = lifecycle.closeAndSnapshot();
        observations.close();
        dispatcher.close();
        ProbeDispatcher.Stats stats = dispatcher.stats();
        logger.info("career-hook-probe-delivery=" + stats);
        Properties values = new Properties();
        values.setProperty("capacity", "128");
        values.setProperty("close.graceMillis", "500");
        values.setProperty("accepted", Long.toString(stats.accepted()));
        values.setProperty("dropped", Long.toString(stats.dropped()));
        values.setProperty("delivered", Long.toString(stats.delivered()));
        values.setProperty("failed", Long.toString(stats.failed()));
        values.setProperty("discarded", Long.toString(stats.discarded()));
        values.setProperty("workerAlive", Boolean.toString(stats.workerAlive()));
        values.setProperty("coverage", "Diagnostic best-effort delivery, not durable events or sidecar transaction");
        try (var writer = Files.newBufferedWriter(report)) { values.store(writer, "Bounded probe delivery"); }
        catch (Exception failure) { System.err.println("Falha ao registrar estatisticas da sonda: " + failure); }
        Properties state = new Properties();
        state.setProperty("session", finalState.session());
        state.setProperty("generation", Long.toString(finalState.generation()));
        state.setProperty("phase.beforeClose", finalState.phase().name());
        state.setProperty("phase.afterClose", lifecycle.snapshot().phase().name());
        state.setProperty("loadedFile", finalState.loadedFile() == null ? "" : finalState.loadedFile());
        state.setProperty("lastSavedFile", finalState.lastSavedFile() == null ? "" : finalState.lastSavedFile());
        state.setProperty("loadedBackup", Boolean.toString(finalState.loadedBackup()));
        state.setProperty("loads", Long.toString(finalState.loads()));
        state.setProperty("saves", Long.toString(finalState.saves()));
        state.setProperty("read.initializedPaths", Long.toString(finalState.initializedReadPaths()));
        state.setProperty("save.completeSequences", Long.toString(finalState.completeSaveSequences()));
        state.setProperty("save.partialSequences", Long.toString(finalState.partialSaveSequences()));
        state.setProperty("save.failedSequences", Long.toString(finalState.failedSaveSequences()));
        state.setProperty("save.inFlight.beforeClose", Integer.toString(finalState.inFlightSaves()));
        var outcome = finalState.lastSaveOutcome();
        if (outcome != null) {
            state.setProperty("save.last.metadataClosed", Boolean.toString(outcome.metadataClosed()));
            state.setProperty("save.last.primaryClosed", Boolean.toString(outcome.primaryClosed()));
            state.setProperty("save.last.backupCopied", Boolean.toString(outcome.backupCopied()));
            state.setProperty("save.last.returned", Boolean.toString(outcome.returned()));
            state.setProperty("save.last.bound", Boolean.toString(outcome.bound()));
            state.setProperty("save.last.completeSequence", Boolean.toString(outcome.completeSequence()));
        }
        state.setProperty("invalidations", Long.toString(finalState.invalidations()));
        state.setProperty("staleCompletions", Long.toString(finalState.staleCompletions()));
        state.setProperty("unboundSaves", Long.toString(finalState.unboundSaves()));
        state.setProperty("inFlight.beforeClose", Integer.toString(finalState.inFlight()));
        state.setProperty("healthy", Boolean.toString(finalState.healthy()));
        state.setProperty("coverage", "Opt-in internal generation guard; no public lifecycle events or persistent career identity");
        try (var writer = Files.newBufferedWriter(report.resolveSibling("career-lifecycle.properties"))) { state.store(writer, "Internal lifecycle guard"); }
        catch (Exception failure) { System.err.println("Falha ao registrar lifecycle interno: " + failure); }
    }
}
