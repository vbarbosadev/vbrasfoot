package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Synthetic original-call markers and owned streams: never evidence of an original initialized game path. */
public final class DecodedSaveProvenanceSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(LoadedSaveVerifier.Reason reason, Action action) throws Exception {
        try { action.run(); } catch (LoadedSaveVerifier.Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static void decoded(Path path, byte[] bytes, Object primary, Object secondary, boolean initialized) throws Exception {
        HookProbe.enterLoad(path.getFileName().toString(), false); HookProbe.loadSource(path.toString());
        var stream = HookProbe.traceLoadInput(new ByteArrayInputStream(bytes));
        stream.readAllBytes(); stream.close(); HookProbe.rootAssigned(); HookProbe.rootAssigned();
        HookProbe.decodedLoad(primary, secondary);
        if (initialized) HookProbe.loadInitializationPathFinished();
        HookProbe.exitLoad(true);
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "decoder-provenance-").toRealPath();
        byte[] bytes = {1, 2, 3, 4}; Path save = Files.write(root.resolve("owned.s22"), bytes);
        Path copy = Files.write(root.resolve("copy.s22"), bytes);
        Object primary = new Object(), secondary = new Object(); var observer = new SaveFileObserver();
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        CareerLifecycle state = new CareerLifecycle();
        try (var guard = HookProbe.attachLifecycle(state)) {
            rejects(LoadedSaveVerifier.Reason.NO_PROVENANCE, () -> verifier.verify(observer.observe(root, "owned.s22")));
            decoded(save, bytes, primary, secondary, false);
            var binding = HookProbe.decodedProvenance().binding().orElseThrow();
            check(binding.bytes().sha256().equals(Profile.digest(save)) && binding.bytes().size() == bytes.length, "stream bytes hash/count retained without reopening source");
            check(HookProbe.currentProvenance(binding, primary, secondary), "same accepted session/generation/weak roots current");
            check(!HookProbe.currentProvenance(binding, new Object(), secondary), "equal-looking root cannot replace actual reference");
            var verified = verifier.verify(observer.observe(root, "owned.s22"));
            check(verified.bytes().equals(binding.bytes()) && !verified.initializationPathObserved(), "verification preserves unqualified initialization rather than manufacturing readiness");
            rejects(LoadedSaveVerifier.Reason.FILE_MISMATCH, () -> verifier.verify(observer.observe(root, "copy.s22")));
            var oldObserved = observer.observe(root, "owned.s22");
            Files.write(save, new byte[] {9, 8, 7, 6});
            rejects(LoadedSaveVerifier.Reason.FILE_MISMATCH, () -> verifier.verify(observer.observe(root, "owned.s22")));
            boolean stale = false;
            try { verifier.verify(oldObserved); } catch (CareerSidecarStore.Failure expected) { stale = expected.reason() == CareerSidecarStore.Reason.CONFLICT; }
            check(stale, "old filesystem observation never accepted after changed source");
            Files.write(save, bytes);
            var staleRoots = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(new Object(), secondary), Duration.ofSeconds(2));
            rejects(LoadedSaveVerifier.Reason.STALE_ROOTS, () -> staleRoots.verify(observer.observe(root, "owned.s22")));
            HookProbe.rootAssigned();
            check(!HookProbe.currentProvenance(binding, primary, secondary), "root assignment revokes old evidence reference");
            rejects(LoadedSaveVerifier.Reason.NO_PROVENANCE, () -> verifier.verify(observer.observe(root, "owned.s22")));
            decoded(save, bytes, primary, secondary, true);
            check(verifier.verify(observer.observe(root, "owned.s22")).initializationPathObserved(), "synthetic initialization marker explicitly distinguished (not original acceptance)");
            HookProbe.enterSave("owned");
            check(HookProbe.decodedProvenance().reason() == DecodedSaveProvenance.Reason.SAVE_STARTED, "save begins by invalidating provenance before original mutation");
            HookProbe.exitSave();
            decoded(save, new byte[] {1, 2}, primary, secondary, false);
            rejects(LoadedSaveVerifier.Reason.FILE_MISMATCH, () -> verifier.verify(observer.observe(root, "owned.s22")));
            HookProbe.enterLoad("failed", false); HookProbe.loadSource(save.toString());
            var badClose = HookProbe.traceLoadInput(new ByteArrayInputStream(bytes) { @Override public void close() throws IOException { throw new IOException("owned close failure"); } });
            badClose.readAllBytes(); boolean closeFailed = false;
            try { badClose.close(); } catch (IOException expected) { closeFailed = true; }
            HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            check(closeFailed && HookProbe.decodedProvenance().reason() == DecodedSaveProvenance.Reason.UNRELIABLE_STREAM, "swallowed upstream close failure cannot manufacture stream proof");
            HookProbe.enterLoad("primary", false); HookProbe.loadSource(save.toString());
            HookProbe.enterLoad("backup", true); HookProbe.loadSource(root.resolve("owned.sbck").toString());
            var backupStream = HookProbe.traceLoadInput(new ByteArrayInputStream(bytes)); backupStream.readAllBytes(); backupStream.close();
            HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true); HookProbe.exitLoad(false);
            var fallback = HookProbe.decodedProvenance().binding().orElseThrow();
            check(fallback.backup() && fallback.source().getFileName().toString().equals("owned.sbck"), "nested fallback carries actual child stream/path despite false outer return");
            HookProbe.enterLoad("failed", false); HookProbe.abortLoad(new IOException("owned abort"));
            check(HookProbe.decodedProvenance().binding().isEmpty(), "failed replacement load never retains prior career evidence");
            decoded(save, bytes, primary, secondary, false);
            var edtFailure = new AtomicReference<Throwable>();
            SwingUtilities.invokeAndWait(() -> { try { verifier.verify(oldObserved); } catch (Throwable expected) { edtFailure.set(expected); } });
            check(edtFailure.get() instanceof LoadedSaveVerifier.Failure f && f.reason() == LoadedSaveVerifier.Reason.WRONG_THREAD, "filesystem verification refused on EDT");
            var parked = new CountDownLatch(1); var release = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> { parked.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } });
            check(parked.await(2, TimeUnit.SECONDS), "owned EDT parking established");
            try {
                var timed = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofMillis(30));
                rejects(LoadedSaveVerifier.Reason.ROOTS_TIMEOUT, () -> timed.verify(observer.observe(root, "owned.s22")));
            } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            HookProbe.enterReset(); HookProbe.rootsReset();
            check(HookProbe.decodedProvenance().binding().isEmpty(), "reset cannot reuse previous decoder evidence");
        } finally { state.close(); }
        check(HookProbe.decodedProvenance().binding().isEmpty(), "guard detachment drops weak evidence");
        var trace = new LoadInputTrace(new ByteArrayInputStream(bytes));
        check(trace.read() == 1 && trace.read(new byte[2], 0, 2) == 2 && trace.read() == 4 && trace.read() == -1, "single/bulk reads and EOF preserve original results");
        check(!trace.finish().reliable(), "no successful stream close means no proof");
        var skipped = new LoadInputTrace(new ByteArrayInputStream(bytes)); skipped.skip(1); skipped.readAllBytes(); skipped.close();
        check(!skipped.finish().reliable(), "skip is not reimplemented by reading to manufacture hash");
        var marked = new LoadInputTrace(new ByteArrayInputStream(bytes)); marked.mark(4); marked.readAllBytes(); marked.reset(); marked.close();
        check(!marked.finish().reliable(), "replay/mark cannot look like one linear file read");
        var reliable = new LoadInputTrace(new ByteArrayInputStream(bytes)); reliable.readAllBytes(); reliable.close(); var result = reliable.finish();
        check(result.reliable() && result.sha256().equals(Profile.digest(save)) && result.equals(reliable.finish()), "closed linear trace finalizes idempotently");
        reliable.close(); check(!reliable.finish().reliable(), "repeated activity after finalized proof cannot remain reliable");
        var readFailure = new LoadInputTrace(new InputStream() { public int read() throws IOException { throw new IOException("owned read failure"); } });
        boolean readFailed = false; try { readFailure.read(); } catch (IOException expected) { readFailed = true; } readFailure.close();
        check(readFailed && !readFailure.finish().reliable(), "read failure keeps original IOException and refuses evidence");
        var otherThread = new LoadInputTrace(new ByteArrayInputStream(bytes)); var reader = Executors.newSingleThreadExecutor();
        try { check(Arrays.equals(reader.submit(otherThread::readAllBytes).get(2, TimeUnit.SECONDS), bytes), "cross-thread reading still returns original stream bytes"); }
        finally { reader.shutdownNow(); }
        otherThread.close(); check(!otherThread.finish().reliable(), "cross-thread stream cannot issue a single-thread decoder proof");
        var bounded = new LoadInputTrace(new InputStream() {
            long left = SaveFileObserver.MAX_SAVE_BYTES + 1;
            public int read() { if (left == 0) return -1; left--; return 0; }
            public int read(byte[] buffer, int offset, int length) {
                if (left == 0) return -1; int count = (int) Math.min(left, length); left -= count; return count;
            }
        });
        byte[] buffer = new byte[1024 * 1024]; long generated = 0; int count;
        while ((count = bounded.read(buffer)) != -1) generated += count;
        bounded.close(); check(generated == SaveFileObserver.MAX_SAVE_BYTES + 1 && !bounded.finish().reliable(), "observation budget refuses proof without truncating original reads or allocating whole save");
        var tracker = new DecodedSaveProvenance();
        tracker.invalidate(DecodedSaveProvenance.Reason.LOAD_STARTED); long oldRevision = tracker.snapshot().revision();
        tracker.invalidate(DecodedSaveProvenance.Reason.ROOT_CHANGED); long newRevision = tracker.snapshot().revision();
        var ticket = new CareerLifecycle.Ticket("owned-session", 1, true);
        tracker.accept(oldRevision, ticket, save, result, false, false, primary, secondary, true);
        check(tracker.snapshot().revision() == newRevision && tracker.snapshot().status() == DecodedSaveProvenance.Status.INVALIDATED,
                "stale completion cannot replace a newer invalidation even with successful-looking stream metadata");
        tracker.accept(newRevision, ticket, save, result, false, false, primary, secondary, true);
        var latest = tracker.snapshot().binding().orElseThrow();
        tracker.accept(oldRevision, ticket, copy, result, false, false, primary, secondary, true);
        check(tracker.snapshot().binding().orElseThrow() == latest, "older completion cannot overwrite newer accepted provenance");
        check(!tracker.current(latest, new CareerLifecycle.Ticket("other-session", 1, true), primary, secondary)
                && !tracker.current(latest, new CareerLifecycle.Ticket("owned-session", 2, true), primary, secondary), "session and generation both required despite identical root objects");
        check(Arrays.equals(Files.readAllBytes(save), bytes) && Arrays.equals(Files.readAllBytes(copy), bytes), "all provenance checks leave owned source bytes untouched");
        System.out.println("PASS: " + checks + " decoder provenance checks; owned fixture " + root + "; synthetic markers are not production readiness.");
    }
}
