package br.vbrasfoot;

import java.time.Duration;
import java.util.concurrent.*;

/** Internal guard tests only. No public events, original game execution or sidecars. */
public final class CareerLifecycleSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("fixture latch timeout");
    }
    private static void load(String file) {
        HookProbe.enterLoad(file, false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
    }
    public static void main(String[] args) throws Exception {
        CareerLifecycle state = new CareerLifecycle();
        check(state.snapshot().phase() == CareerLifecycle.Phase.EMPTY, "initial guard empty");
        try (var attached = HookProbe.attachLifecycle(state);
             var brokenLogger = HookProbe.attach(event -> { throw new AssertionError("broken diagnostic callback"); })) {
            load("A");
            check(state.snapshot().phase() == CareerLifecycle.Phase.ACTIVE && state.snapshot().loads() == 1, "guard updated before diagnostic failure");
            HookProbe.enterSave("A"); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(state.snapshot().saves() == 1 && state.snapshot().lastSavedFile().equals("A"), "save bound to proven current graph");
            CountDownLatch began = new CountDownLatch(1), finish = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
            Thread lateSave = new Thread(() -> {
                try {
                    HookProbe.enterSave("A-old"); began.countDown(); await(finish);
                    HookProbe.primaryClosed(); HookProbe.exitSave();
                } catch (Throwable failure) { error.set(failure); }
            }, "owned-late-save-fixture");
            lateSave.start(); await(began);
            load("B"); finish.countDown(); lateSave.join(3000);
            check(!lateSave.isAlive() && error.get() == null, "owned save fixture completed");
            check(state.snapshot().loadedFile().equals("B") && state.snapshot().lastSavedFile() == null && state.snapshot().saves() == 1
                    && state.snapshot().staleCompletions() == 1, "late save never binds previous graph to current career");
            HookProbe.enterReset(); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.rootsReset();
            check(state.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && state.snapshot().inFlight() == 0, "new roots not mistaken for ready career");
            HookProbe.enterSave("unproven"); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(state.snapshot().unboundSaves() == 1 && state.snapshot().saves() == 1, "unproven save not bound");
            HookProbe.enterLoad("fallback", false);
            HookProbe.enterLoad("fallback", true); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
            check(state.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN, "inner fallback not committed separately");
            HookProbe.exitLoad(false);
            check(state.snapshot().phase() == CareerLifecycle.Phase.ACTIVE && state.snapshot().loadedBackup() && state.snapshot().inFlight() == 0, "fallback outer completion commits once");
            HookProbe.enterLoad("bad", false); HookProbe.rootAssigned(); HookProbe.abortLoad(new IllegalStateException());
            check(state.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN && state.snapshot().inFlight() == 0, "partial failed read not left active");
            HookProbe.enterReset(); HookProbe.rootAssigned(); HookProbe.abortReset(new IllegalStateException());
            check(state.snapshot().inFlight() == 0 && state.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN, "reset failure releases attempt");
        }

        CareerLifecycle overlapping = new CareerLifecycle();
        var first = overlapping.beginMutation(); overlapping.rootsChanged(); var firstTicket = overlapping.ticket();
        var second = overlapping.beginMutation(); overlapping.rootsChanged(); var secondTicket = overlapping.ticket();
        check(!overlapping.loaded(second, secondTicket, "B", false, true), "overlapping load not accepted");
        check(!overlapping.loaded(first, firstTicket, "A", false, true) && overlapping.snapshot().inFlight() == 0, "both overlapping attempts refused");
        var clean = overlapping.beginMutation(); overlapping.rootsChanged(); var cleanTicket = overlapping.ticket();
        check(overlapping.loaded(clean, cleanTicket, "clean", false, true), "later exclusive load restores known state");
        check(!overlapping.loaded(clean, cleanTicket, "duplicate", false, true), "duplicate completion refused");
        var foreign = new CareerLifecycle();
        check(!foreign.saved(overlapping.ticket(), "foreign"), "ticket from different session refused");
        overlapping.close();
        check(!overlapping.saved(cleanTicket, "closed") && overlapping.snapshot().phase() == CareerLifecycle.Phase.CLOSED, "closed guard cannot reactivate");

        CareerLifecycle capped = new CareerLifecycle();
        for (int i = 0; i < 65; i++) capped.beginMutation();
        check(!capped.snapshot().healthy() && capped.snapshot().inFlight() == 64 && capped.beginMutation() == null, "bounded attempt tracking fails closed on overflow");
        capped.close(); check(capped.snapshot().inFlight() == 0, "close releases guard metadata");

        CareerLifecycle stages = new CareerLifecycle();
        try (var attached = HookProbe.attachLifecycle(stages)) {
            HookProbe.enterLoad("stages", false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad();
            HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            check(stages.snapshot().initializedReadPaths() == 1, "initialization path recorded only with accepted read");
            HookProbe.enterSave("complete"); HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(stages.snapshot().completeSaveSequences() == 1 && stages.snapshot().lastSaveOutcome().completeSequence(), "all observed save stages required for full sequence");
            HookProbe.enterSave("metadata-missing"); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(stages.snapshot().partialSaveSequences() == 1 && !stages.snapshot().lastSaveOutcome().metadataClosed()
                    && stages.snapshot().saves() == 2, "primary write remains distinct from metadata failure");
            HookProbe.enterSave("backup-missing"); HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(stages.snapshot().partialSaveSequences() == 2 && !stages.snapshot().lastSaveOutcome().backupCopied(), "missing backup not a full sequence");
            HookProbe.enterSave("failed-primary"); HookProbe.metadataClosed(); HookProbe.exitSave();
            check(stages.snapshot().failedSaveSequences() == 1 && !stages.snapshot().lastSaveOutcome().bound(), "metadata alone never implies saved career");
            HookProbe.enterSave("abort"); HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.abortSave(new IllegalStateException());
            check(stages.snapshot().partialSaveSequences() == 3 && !stages.snapshot().lastSaveOutcome().returned()
                    && stages.snapshot().inFlightSaves() == 0 && stages.snapshot().saves() == 3, "aborted save releases tracking but cannot commit");
            HookProbe.enterLoad("failed-init", false); HookProbe.rootAssigned(); HookProbe.decodedLoad();
            HookProbe.loadInitializationPathFinished(); HookProbe.abortLoad(new IllegalStateException());
            check(stages.snapshot().initializedReadPaths() == 1 && stages.snapshot().phase() == CareerLifecycle.Phase.UNKNOWN, "path marker without normal read completion not counted");
            load("after-failure");
            CountDownLatch saving = new CountDownLatch(1), releaseSave = new CountDownLatch(1);
            Thread otherSave = new Thread(() -> {
                HookProbe.enterSave("concurrent"); saving.countDown();
                try { await(releaseSave); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                finally { HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave(); }
            }, "owned-overlapping-save-fixture");
            otherSave.start();
            try {
                await(saving);
                HookProbe.enterSave("overlap"); HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
                check(!stages.snapshot().lastSaveOutcome().bound(), "second concurrent save refused");
            } finally { releaseSave.countDown(); otherSave.join(3000); }
            check(!otherSave.isAlive() && stages.snapshot().saves() == 3 && stages.snapshot().inFlightSaves() == 0
                    && !stages.snapshot().lastSaveOutcome().completeSequence(), "first overlapping save also refused, both released");
            var cleanSave = stages.beginSave();
            check(stages.finishSave(cleanSave, "clean", true, true, true, true).completeSequence(), "later exclusive save recovers tracking");
            var last = stages.snapshot();
            check(stages.finishSave(cleanSave, "duplicate", true, true, true, true) == null && stages.snapshot().equals(last), "duplicate completion cannot overwrite outcome or count");
            var foreignSave = new CareerLifecycle().beginSave();
            check(stages.finishSave(foreignSave, "foreign", true, true, true, true) == null, "foreign save attempt refused");
        }
        CareerLifecycle saveCapped = new CareerLifecycle();
        for (int i = 0; i < 65; i++) saveCapped.beginSave();
        check(!saveCapped.snapshot().healthy() && saveCapped.snapshot().inFlightSaves() == 64 && saveCapped.beginSave() == null, "save attempts bounded and overflow fails closed");
        saveCapped.close(); check(saveCapped.snapshot().inFlightSaves() == 0, "close releases pending save attempts");
        CareerLifecycle pendingSave = new CareerLifecycle();
        var pending = pendingSave.beginSave(); var beforeClose = pendingSave.closeAndSnapshot();
        check(beforeClose.inFlightSaves() == 1 && beforeClose.completeSaveSequences() == 0, "shutdown records unfinished save, never synthesizes completion");
        check(pendingSave.finishSave(pending, "late", true, true, true, true) == null && pendingSave.snapshot().lastSaveOutcome() == null, "save return after disposal refused");

        CareerLifecycle independent = new CareerLifecycle();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var dispatcher = new ProbeDispatcher(1, Duration.ofMillis(500), event -> {
                 started.countDown(); try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
             }); var lifecycle = HookProbe.attachLifecycle(independent); var logger = HookProbe.attach(dispatcher::offer)) {
            load("queue-fixture"); await(started);
            HookProbe.enterSave("one"); HookProbe.primaryClosed(); HookProbe.exitSave();
            HookProbe.enterSave("two"); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(dispatcher.stats().dropped() > 0 && independent.snapshot().saves() == 2, "diagnostic saturation does not lose synchronous state");
            release.countDown();
        }
        var reportDirectory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "lifecycle-report-");
        var quiet = java.util.logging.Logger.getLogger("lifecycle-fixture"); quiet.setUseParentHandlers(false);
        var probe = new ProbeSession(quiet, reportDirectory.resolve("probe-delivery.properties"));
        load("report-fixture");
        HookProbe.enterSave("report-fixture"); HookProbe.metadataClosed(); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
        probe.close(); probe.close();
        var report = new java.util.Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(reportDirectory.resolve("career-lifecycle.properties"))) { report.load(reader); }
        check(report.getProperty("phase.beforeClose").equals("ACTIVE") && report.getProperty("phase.afterClose").equals("CLOSED")
                && report.getProperty("loads").equals("1") && report.getProperty("inFlight.beforeClose").equals("0"), "session writes internal report without EDT or synthetic unload");
        check(report.getProperty("save.completeSequences").equals("1") && report.getProperty("save.last.metadataClosed").equals("true")
                && report.getProperty("save.inFlight.beforeClose").equals("0"), "save-stage report survives callback delivery independently");
        try (var next = HookProbe.attachLifecycle(new CareerLifecycle())) { check(true, "close releases synchronous subscription"); }
        System.out.println("PASS: " + checks + " lifecycle guard tests (internal, no public events).");
    }
}
