package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Read-only cross-check between the original decoder's input, an observed file and current weak roots.
 * Never installs a command context or infers logical career identity. */
final class LoadedSaveVerifier {
    enum Reason { WRONG_THREAD, NO_PROVENANCE, FILE_MISMATCH, STALE_ROOTS, ROOTS_TIMEOUT, ROOTS_UNAVAILABLE }
    static final class Failure extends IOException {
        private final Reason reason;
        Failure(Reason reason, String message) { super(message); this.reason = reason; }
        Reason reason() { return reason; }
    }
    record Verified(String session, long generation, long evidenceRevision, Path source,
                    CareerSidecarStore.SaveRevision bytes, boolean backup, boolean initializationPathObserved) { }
    private final SaveFileObserver observer;
    private final CareerCommandDispatcher.RootsSource roots;
    private final long timeoutNanos;
    LoadedSaveVerifier(LegacyRuntime runtime) {
        this(new SaveFileObserver(), () -> {
            Class<?> state = Class.forName("c.a", false, runtime);
            return new CareerCommandDispatcher.Roots(state.getField("SR").get(null), state.getField("afQ").get(null));
        }, Duration.ofSeconds(3));
    }
    LoadedSaveVerifier(SaveFileObserver observer, CareerCommandDispatcher.RootsSource roots, Duration timeout) {
        this.observer = Objects.requireNonNull(observer); this.roots = Objects.requireNonNull(roots);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Bounded root verification timeout required");
        timeoutNanos = timeout.toNanos();
    }
    Verified verify(SaveFileObserver.Observation observed) throws IOException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) throw new Failure(Reason.WRONG_THREAD, "Verify decoder/file provenance off EDT");
        var binding = HookProbe.decodedProvenance().binding().orElseThrow(() -> new Failure(Reason.NO_PROVENANCE, "No accepted decoder stream evidence"));
        observer.verify(Objects.requireNonNull(observed));
        Path selected = observed.directory().resolve(observed.name());
        // Exact stream digest/length is mandatory; observing an unrelated equal-byte file cannot select an origin.
        CareerSidecarStore.plain(binding.source());
        if (!binding.source().toRealPath().equals(selected.toRealPath()) || !binding.bytes().equals(observed.revision()))
            throw new Failure(Reason.FILE_MISMATCH, "Observed file/path differs from bytes fed to the accepted decoder");
        FutureTask<Boolean> query = new FutureTask<>(() -> {
            var current = roots.current();
            return current != null && HookProbe.currentProvenance(binding, current.primary(), current.secondary());
        });
        SwingUtilities.invokeLater(query);
        try {
            if (!query.get(timeoutNanos, TimeUnit.NANOSECONDS)) throw new Failure(Reason.STALE_ROOTS, "Decoder generation or root references no longer current");
        } catch (TimeoutException timeout) { query.cancel(false); throw new Failure(Reason.ROOTS_TIMEOUT, "EDT root check timed out; no authority granted"); }
        catch (ExecutionException failure) { throw new Failure(Reason.ROOTS_UNAVAILABLE, "Current decoder roots could not be checked"); }
        catch (InterruptedException interrupted) { query.cancel(false); throw interrupted; }
        // Captured values are diagnostic facts at verification time, not a durable or live authorization token.
        return new Verified(binding.session(), binding.generation(), binding.revision(), binding.source(), binding.bytes(),
                binding.backup(), binding.initializedPath());
    }
}
