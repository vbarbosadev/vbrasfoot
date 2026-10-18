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
    private final CareerSubscriberBudget subscriberBudget = new CareerSubscriberBudget(32);
    private final CareerObservationHub observations = new CareerObservationHub(subscriberBudget);
    private final CareerIoEventHub ioEvents = new CareerIoEventHub(subscriberBudget);
    private final CareerLifecycle lifecycle = new CareerLifecycle(observations, ioEvents);
    private final Thread shutdown;
    private final Logger logger;
    private final Path report;
    private boolean closed;
    br.vbrasfoot.api.CareerObservations observations() { return observations; }
    br.vbrasfoot.api.CareerIoEvents ioEvents() { return ioEvents; }

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
        var provenance = HookProbe.decodedProvenance();
        try { subscription.close(); } catch (Exception failure) { logger.warning("Could not detach probe subscription"); }
        try { lifecycleSubscription.close(); } catch (Exception failure) { logger.warning("Could not detach lifecycle guard"); }
        Properties decoded = new Properties();
        decoded.setProperty("format", "1");
        decoded.setProperty("coverage", "Original decoder stream bytes and weak root generation only; not graph consistency, persistent identity or write readiness");
        decoded.setProperty("revision", Long.toString(provenance.revision()));
        decoded.setProperty("status", provenance.status().name()); decoded.setProperty("reason", provenance.reason().name());
        provenance.binding().ifPresent(value -> {
            decoded.setProperty("generation", Long.toString(value.generation()));
            decoded.setProperty("bytes.sha256", value.bytes().sha256()); decoded.setProperty("bytes.size", Long.toString(value.bytes().size()));
            decoded.setProperty("backup", Boolean.toString(value.backup()));
            decoded.setProperty("initializationPathObserved", Boolean.toString(value.initializedPath()));
        });
        decoded.setProperty("writeAuthority", "false");
        try (var writer = Files.newBufferedWriter(report.resolveSibling("decoded-save-provenance.properties"))) { decoded.store(writer, "Decoder provenance before probe detachment; no save paths or root objects exported"); }
        catch (Exception failure) { System.err.println("Falha ao registrar provenance do decoder: " + failure); }
        Properties async = new Properties();
        var asyncSnapshot = HookProbe.asyncCreationSnapshot();
        async.setProperty("coverage", "Causal candidate tasks/progression only; not exclusive new-career origin, readiness, unload or durable lifecycle");
        async.setProperty("rejected", Long.toString(asyncSnapshot.rejected()));
        async.setProperty("unbound", Long.toString(asyncSnapshot.unbound()));
        var asyncReports = asyncSnapshot.tasks();
        async.setProperty("count", Integer.toString(asyncReports.size()));
        var progression = asyncSnapshot.progress();
        async.setProperty("progress.count", Integer.toString(progression.size()));
        async.setProperty("progress.dropped", Long.toString(asyncSnapshot.dropped()));
        async.setProperty("progress.outside", Long.toString(asyncSnapshot.outside()));
        int progressIndex = 0;
        for (var value : progression) {
            String prefix = "progress." + progressIndex++ + ".";
            async.setProperty(prefix + "sequence", Long.toString(value.sequence()));
            async.setProperty(prefix + "origin", Long.toString(value.origin())); async.setProperty(prefix + "parent", Long.toString(value.parent()));
            async.setProperty(prefix + "originLabel", value.originLabel()); async.setProperty(prefix + "method", value.method());
            async.setProperty(prefix + "stage", value.stage()); async.setProperty(prefix + "panel", Integer.toString(value.panel()));
            async.setProperty(prefix + "generation", Long.toString(value.generation())); async.setProperty(prefix + "thread", Long.toString(value.thread()));
            async.setProperty(prefix + "matched", Boolean.toString(value.matched()));
            async.setProperty(prefix + "rootsMatched", Boolean.toString(value.rootsMatched()));
            async.setProperty(prefix + "receiverKnown", Boolean.toString(value.receiverKnown()));
        }
        int asyncIndex = 0;
        for (var value : asyncReports) {
            String prefix = "task." + asyncIndex++ + ".";
            async.setProperty(prefix + "id", Long.toString(value.id())); async.setProperty(prefix + "origin", Long.toString(value.origin()));
            async.setProperty(prefix + "parent", Long.toString(value.parent())); async.setProperty(prefix + "label", value.label());
            async.setProperty(prefix + "originLabel", value.originLabel());
            async.setProperty(prefix + "generation", Long.toString(value.generation()));
            async.setProperty(prefix + "scheduleReturned", Boolean.toString(value.scheduleReturned()));
            async.setProperty(prefix + "scheduleFailed", Boolean.toString(value.scheduleFailed()));
            async.setProperty(prefix + "started", Boolean.toString(value.started()));
            async.setProperty(prefix + "returned", Boolean.toString(value.returned()));
            async.setProperty(prefix + "failed", Boolean.toString(value.failed())); async.setProperty(prefix + "stale", Boolean.toString(value.stale()));
        }
        try (var writer = Files.newBufferedWriter(report.resolveSibling("creation-async.properties"))) { async.store(writer, "Bounded weak-identity async candidate report"); }
        catch (Exception failure) { System.err.println("Falha ao registrar async candidates: " + failure); }
        var dialogSnapshot = HookProbe.creationDialogs();
        Properties dialogs = new Properties();
        dialogs.setProperty("coverage", "Same-thread oq/pl/dialog/AB diagnostics only; raw result is not career readiness or cancellation proof");
        dialogs.setProperty("count", Integer.toString(dialogSnapshot.events().size()));
        dialogs.setProperty("dropped", Long.toString(dialogSnapshot.dropped()));
        dialogs.setProperty("rejected", Long.toString(dialogSnapshot.rejected()));
        dialogs.setProperty("outside", Long.toString(dialogSnapshot.outside()));
        int dialogIndex = 0;
        for (var value : dialogSnapshot.events()) {
            String prefix = "event." + dialogIndex++ + ".";
            dialogs.setProperty(prefix + "sequence", Long.toString(value.sequence()));
            dialogs.setProperty(prefix + "frame", Long.toString(value.frame()));
            dialogs.setProperty(prefix + "parent", Long.toString(value.parent()));
            dialogs.setProperty(prefix + "method", value.method()); dialogs.setProperty(prefix + "stage", value.stage());
            dialogs.setProperty(prefix + "result", Integer.toString(value.result()));
            dialogs.setProperty(prefix + "resultKnown", Boolean.toString(value.resultKnown()));
            dialogs.setProperty(prefix + "generation", Long.toString(value.generation()));
            dialogs.setProperty(prefix + "thread", Long.toString(value.thread()));
            dialogs.setProperty(prefix + "rootsMatched", Boolean.toString(value.rootsMatched()));
        }
        try (var writer = Files.newBufferedWriter(report.resolveSibling("creation-dialog.properties"))) { dialogs.store(writer, "Bounded creation dialog diagnostics"); }
        catch (Exception failure) { System.err.println("Falha ao registrar creation dialog: " + failure); }
        var entrySnapshot = HookProbe.creationEntries();
        Properties entries = new Properties();
        entries.setProperty("format", "1");
        entries.setProperty("coverage", "Weak panel binding from hU settings preparation to bV callback only; not career readiness, graph consistency or persistent identity");
        entries.setProperty("count", Integer.toString(entrySnapshot.events().size()));
        entries.setProperty("dropped", Long.toString(entrySnapshot.dropped()));
        entries.setProperty("rejected", Long.toString(entrySnapshot.rejected()));
        entries.setProperty("outside", Long.toString(entrySnapshot.outside()));
        entries.setProperty("attempts.bound", Long.toString(entrySnapshot.boundAttempts()));
        entries.setProperty("attempts.unbound", Long.toString(entrySnapshot.unboundAttempts()));
        int entryIndex = 0;
        for (var value : entrySnapshot.events()) {
            String prefix = "event." + entryIndex++ + ".";
            entries.setProperty(prefix + "sequence", Long.toString(value.sequence()));
            entries.setProperty(prefix + "entry", Long.toString(value.entry()));
            entries.setProperty(prefix + "attempt", Long.toString(value.attempt()));
            entries.setProperty(prefix + "stage", value.stage());
            entries.setProperty(prefix + "generation", Long.toString(value.generation()));
            entries.setProperty(prefix + "thread", Long.toString(value.thread()));
            entries.setProperty(prefix + "bound", Boolean.toString(value.bound()));
            entries.setProperty(prefix + "rootsMatched", Boolean.toString(value.rootsMatched()));
            entries.setProperty(prefix + "resultKnown", Boolean.toString(value.resultKnown()));
            entries.setProperty(prefix + "result", Integer.toString(value.result()));
        }
        try (var writer = Files.newBufferedWriter(report.resolveSibling("creation-entry.properties"))) { entries.store(writer, "Bounded weak creation entry binding"); }
        catch (Exception failure) { System.err.println("Falha ao registrar creation entry: " + failure); }
        var qualificationSnapshot = HookProbe.creationQualifications();
        Properties qualification = new Properties();
        qualification.setProperty("format", "1");
        qualification.setProperty("coverage", "Strict online no-friendly/no-save synchronous path recognition only; not career readiness, global quiescence, durable save, lifecycle activation or persistent identity");
        qualification.setProperty("lifecycleActivated", "false");
        qualification.setProperty("count", Integer.toString(qualificationSnapshot.attempts().size()));
        qualification.setProperty("dropped", Long.toString(qualificationSnapshot.dropped()));
        qualification.setProperty("active", Integer.toString(qualificationSnapshot.active()));
        qualification.setProperty("capacityRejected", Long.toString(qualificationSnapshot.capacityRejected()));
        int qualificationIndex = 0;
        for (var value : qualificationSnapshot.attempts()) {
            String prefix = "attempt." + qualificationIndex++ + ".";
            qualification.setProperty(prefix + "entry", Long.toString(value.entry()));
            qualification.setProperty(prefix + "attempt", Long.toString(value.attempt()));
            qualification.setProperty(prefix + "generation", Long.toString(value.generation()));
            qualification.setProperty(prefix + "status", value.path().status().name());
            qualification.setProperty(prefix + "reason", value.path().reason().name());
            qualification.setProperty(prefix + "signals", Integer.toString(value.path().signals()));
            qualification.setProperty(prefix + "methodEntries", Integer.toString(value.path().methodEntries()));
            qualification.setProperty(prefix + "maxDepth", Integer.toString(value.path().maxDepth()));
            qualification.setProperty(prefix + "panelReturns", Integer.toString(value.path().panelReturns()));
        }
        try (var writer = Files.newBufferedWriter(report.resolveSibling("creation-qualification.properties"))) { qualification.store(writer, "Strict observed path qualification, not career readiness"); }
        catch (Exception failure) { System.err.println("Falha ao registrar creation qualification: " + failure); }
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
        ioEvents.close();
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
        state.setProperty("save.streamCloseFailures", Long.toString(finalState.streamCloseFailures()));
        state.setProperty("io.existingLoads", Long.toString(finalState.ioState().existingLoads()));
        state.setProperty("io.completedSaves", Long.toString(finalState.ioState().completedSaves()));
        state.setProperty("io.phase.beforeClose", finalState.ioState().phase().name());
        state.setProperty("save.inFlight.beforeClose", Integer.toString(finalState.inFlightSaves()));
        var outcome = finalState.lastSaveOutcome();
        if (outcome != null) {
            state.setProperty("save.last.metadataClosed", Boolean.toString(outcome.metadataClosed()));
            state.setProperty("save.last.primaryClosed", Boolean.toString(outcome.primaryClosed()));
            state.setProperty("save.last.backupCopied", Boolean.toString(outcome.backupCopied()));
            state.setProperty("save.last.returned", Boolean.toString(outcome.returned()));
            state.setProperty("save.last.bound", Boolean.toString(outcome.bound()));
            state.setProperty("save.last.completeSequence", Boolean.toString(outcome.completeSequence()));
            state.setProperty("save.last.streamClosed", Boolean.toString(outcome.streamClosed()));
            state.setProperty("save.last.streamCloseFailed", Boolean.toString(outcome.streamCloseFailed()));
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
