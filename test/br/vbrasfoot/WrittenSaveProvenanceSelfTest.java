package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;

/** Own streams/files/roots only. Successful byte correlation never proves a gameplay operation was saved. */
public final class WrittenSaveProvenanceSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(WrittenSaveVerifier.Reason reason, Action action) throws Exception {
        try { action.run(); } catch (WrittenSaveVerifier.Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected refusal " + reason);
    }
    private static void save(Path path, Object primary, Object secondary, boolean metadata, boolean backup, boolean abort, int roots) throws Exception {
        HookProbe.enterSave(path.getFileName().toString());
        HookProbe.saveDestination(path.resolveSibling("owned.info").toString()); HookProbe.saveDestination(path.toString());
        var output = HookProbe.traceSaveOutput(Files.newOutputStream(path));
        Object marker = new Object();
        for (int i = 0; i < roots; i++) { HookProbe.saveRootSerializing(i == 0 ? primary : secondary); HookProbe.saveRootSerialized(); }
        output.write(new byte[] {1, 2, 3, 4}); output.close();
        if (metadata) HookProbe.metadataClosed(); HookProbe.primaryOutputClosing(marker); HookProbe.primaryStreamClosed(marker); HookProbe.primaryClosed();
        if (backup) HookProbe.backupCopied();
        if (abort) HookProbe.abortSave(new IOException("Owned abort")); else HookProbe.exitSave();
    }
    public static void main(String[] args) throws Exception {
        var sink = new ByteArrayOutputStream(); var trace = new SaveOutputTrace(sink);
        byte[] bytes = {1, 2, 3, 4}; trace.write(1); trace.write(bytes, 1, 2); trace.write(new byte[] {4}); trace.flush(); trace.close();
        var result = trace.finish();
        check(Arrays.equals(sink.toByteArray(), bytes) && result.bytes() == bytes.length, "write overloads never duplicate bytes");
        check(result.sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))) && result.reliable(), "actual delegated bytes hash exactly");
        trace.write(5); check(!trace.finish().reliable(), "late post-finalization write invalidates trace result");
        var unclosed = new SaveOutputTrace(new ByteArrayOutputStream()); unclosed.write(1); check(!unclosed.finish().reliable(), "unclosed stream never qualifies");
        var empty = new SaveOutputTrace(new ByteArrayOutputStream()); empty.close(); check(!empty.finish().reliable(), "empty stream never qualifies");
        var cross = new SaveOutputTrace(new ByteArrayOutputStream());
        Thread thread = new Thread(() -> { try { cross.write(1); } catch (IOException failure) { throw new AssertionError(failure); } }); thread.start(); thread.join(); cross.close();
        check(!cross.finish().reliable(), "cross-thread stream access is not reliable evidence");
        for (String stage : List.of("write", "flush", "close")) {
            var failure = new IOException("Owned " + stage); var count = new AtomicInteger();
            var failing = new SaveOutputTrace(new OutputStream() {
                public void write(int value) throws IOException { count.incrementAndGet(); if (stage.equals("write")) throw failure; }
                public void flush() throws IOException { if (stage.equals("flush")) throw failure; }
                public void close() throws IOException { if (stage.equals("close")) throw failure; }
            });
            boolean propagated = false;
            try { failing.write(1); failing.flush(); failing.close(); } catch (IOException expected) { propagated = expected == failure; }
            check(propagated && count.get() == 1 && !failing.finish().reliable(), "no retry/suppression of original " + stage + " failure");
        }
        var flushCount = new AtomicInteger(); var closeCount = new AtomicInteger();
        var closing = new SaveOutputTrace(new ByteArrayOutputStream() {
            @Override public void flush() { flushCount.incrementAndGet(); }
            @Override public void close() { closeCount.incrementAndGet(); }
        }); closing.write(1); closing.close();
        check(flushCount.get() == 0 && closeCount.get() == 1, "wrapper close does not introduce a flush or extra close");
        var provenance = new WrittenSaveProvenance(); Object primary = new Object(), secondary = new Object();
        var ticket = new CareerLifecycle.Ticket("owned", 1, true); long revision = provenance.invalidate();
        var ok = new SaveOutputTrace.Result(4, "a".repeat(64), true, true); Path absolute = Path.of("build", "owned.s22").toAbsolutePath();
        provenance.complete(revision, ticket, ticket, absolute, ok, true, primary, secondary);
        var bound = provenance.snapshot().binding().orElseThrow();
        check(provenance.current(bound, ticket, primary, secondary), "exact serialized weak root pair and ticket bind diagnostic evidence");
        check(!provenance.current(bound, ticket, secondary, primary), "swapped roots never match");
        provenance.invalidate(); provenance.complete(revision, ticket, ticket, absolute, ok, true, primary, secondary);
        check(provenance.snapshot().binding().isEmpty(), "late older attempt cannot republish after invalidation");
        for (int mode = 0; mode < 5; mode++) {
            long next = provenance.invalidate();
            provenance.complete(next, ticket, mode == 0 ? new CareerLifecycle.Ticket("other", 1, true) : ticket,
                    mode == 1 ? Path.of("relative.s22") : absolute, mode == 2 ? new SaveOutputTrace.Result(4, "a".repeat(64), true, false) : ok,
                    mode != 3, primary, mode == 4 ? primary : secondary);
            check(provenance.snapshot().binding().isEmpty(), "invalid output evidence rejected mode " + mode);
        }
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "written-save-").toRealPath(); Path file = root.resolve("owned.s22");
        var lifecycle = new CareerLifecycle();
        try (var attached = HookProbe.attachLifecycle(lifecycle)) {
            HookProbe.enterLoad("owned", false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            var observer = new SaveFileObserver();
            var verifier = new WrittenSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            save(file, primary, secondary, true, true, false, 2);
            var observed = observer.observe(root, "owned.s22"); var verified = verifier.verify(observed);
            check(verified.bytes().equals(observed.revision()) && verified.destination().equals(file), "original-style hook stream/path/full sequence correlate without file mutation");
            Files.copy(file, root.resolve("copy.s22"));
            rejects(WrittenSaveVerifier.Reason.FILE_MISMATCH, () -> verifier.verify(observer.observe(root, "copy.s22")));
            var swapped = new WrittenSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(secondary, primary), Duration.ofSeconds(2));
            rejects(WrittenSaveVerifier.Reason.STALE_ROOTS, () -> swapped.verify(observed));
            Files.write(file, new byte[] {4, 3, 2, 1});
            rejects(WrittenSaveVerifier.Reason.FILE_MISMATCH, () -> verifier.verify(observer.observe(root, "owned.s22")));
            save(file, primary, secondary, true, true, false, 2); HookProbe.rootAssigned();
            rejects(WrittenSaveVerifier.Reason.NO_PROVENANCE, () -> verifier.verify(observer.observe(root, "owned.s22")));
            HookProbe.enterLoad("owned", false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            for (int mode = 0; mode < 4; mode++) {
                save(file, primary, secondary, mode != 0, mode != 1, mode == 2, mode == 3 ? 1 : 2);
                check(HookProbe.writtenSaveProvenance().binding().isEmpty(), "incomplete writer sequence cannot qualify mode " + mode);
            }
            save(file, primary, secondary, true, true, false, 2);
            var onEdt = new AtomicReference<Throwable>();
            SwingUtilities.invokeAndWait(() -> { try { verifier.verify(observed); } catch (Throwable expected) { onEdt.set(expected); } });
            check(onEdt.get() instanceof WrittenSaveVerifier.Failure failure && failure.reason() == WrittenSaveVerifier.Reason.WRONG_THREAD, "file verification stays off EDT");
            var parked = new CountDownLatch(1); var release = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> { parked.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } });
            check(parked.await(2, TimeUnit.SECONDS), "owned EDT parking established");
            try {
                var timed = new WrittenSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofMillis(30));
                rejects(WrittenSaveVerifier.Reason.ROOTS_TIMEOUT, () -> timed.verify(observer.observe(root, "owned.s22")));
            } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            HookProbe.enterLoad("owned", false); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            check(HookProbe.writtenSaveProvenance().binding().isEmpty(), "reload never restores old save evidence as current authority");
        }
        check(HookProbe.writtenSaveProvenance().binding().isEmpty(), "detachment invalidates writer evidence");
        Path report = Files.createDirectory(root.resolve("reports")).resolve("probe-delivery.properties");
        var logger = java.util.logging.Logger.getLogger("owned-written-save-report"); logger.setUseParentHandlers(false);
        UUID reportOperation = UUID.randomUUID();
        FinanceSerializationTracker.Target reportTarget = serialized -> serialized == primary ? FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE : FinanceSerializationTracker.Sample.ROOT_MISMATCH;
        try (var probe = new ProbeSession(logger, report)) {
            HookProbe.enterLoad("owned", false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            HookProbe.observeFinancialSerialization(reportOperation, new CareerCommandJournal.Intent("private-request-key", CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64)), reportTarget, null);
            CareerBridge.onEdt(() -> { save(file, primary, secondary, true, true, false, 2); return null; });
        }
        java.lang.ref.Reference.reachabilityFence(reportTarget);
        var properties = new Properties();
        try (var in = Files.newInputStream(report.resolveSibling("written-save-provenance.properties"))) { properties.load(in); }
        check(properties.getProperty("status").equals("STREAM_BOUND"), "report captures writer evidence before detach invalidates it");
        check(properties.getProperty("operationInclusionProven").equals("false") && properties.getProperty("writeAuthority").equals("false"), "report never invents effect inclusion or write permission");
        check(!properties.containsKey("destination") && !Files.readString(report.resolveSibling("written-save-provenance.properties")).contains(root.toString()), "diagnostic report does not export save paths or root objects");
        check(HookProbe.writtenSaveProvenance().binding().isEmpty(), "probe disposal never restores reported evidence as live authority");
        check(properties.getProperty("finance.operations.count").equals("1") && properties.getProperty("finance.operation.0.uuid").equals(reportOperation.toString()), "report preserves exact operation UUID captured at serializer boundaries");
        check(properties.getProperty("finance.operation.0.before").equals("MATCHED_EXPECTED_STATE") && properties.getProperty("finance.operation.0.after").equals("MATCHED_EXPECTED_STATE"), "report retains bounded target samples, not invented inclusion status");
        check(properties.getProperty("finance.operation.0.numericEmission").equals("MISSING")
                && properties.getProperty("finance.operation.0.numericFieldsObserved").equals("0"), "report never promotes root-only matched sample to observed numeric arguments");
        check(properties.getProperty("finance.operation.0.ledgerReference").equals("MISSING")
                && properties.getProperty("finance.operation.0.ledgerReferenceObserved").equals("0"), "report never infers ledger argument from matched root samples");
        check(properties.getProperty("finance.operation.0.qualification.version").equals("1")
                && properties.getProperty("finance.operation.0.qualification.membershipBefore").equals("NOT_RECORDED")
                && properties.getProperty("finance.operation.0.qualification.membershipAfter").equals("NOT_RECORDED")
                && properties.getProperty("finance.operation.0.qualification.serializerAfterReference").equals("NOT_RECORDED"),
                "new report identifies manifest1 but never invents unavailable sampler/profile facts");
        check(properties.getProperty("finance.referenceTable.operations").equals("1")
                && properties.getProperty("finance.referenceTable.0.resolution").equals("MISSING")
                && properties.getProperty("finance.referenceTable.0.uuid").equals(reportOperation.toString())
                && properties.getProperty("finance.referenceTable.0.supportedResolver").equals("false"),
                "root-only report exports missing bounded table summary for UUID, not invented reference IDs or content coverage");
        check(!Files.readString(report.resolveSibling("written-save-provenance.properties")).contains("private-request-key"), "exported report does not disclose private operation key/payload or target values");
        System.out.println("PASS: " + checks + " written-save stream checks; owned fixture " + root + "; no financial operation inclusion inferred.");
    }
}
