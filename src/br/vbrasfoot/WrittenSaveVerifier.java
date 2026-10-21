package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Diagnostic exact-file/output-stream/serialized-root comparison, never save inclusion of a financial operation. */
final class WrittenSaveVerifier {
    enum Reason { WRONG_THREAD, NO_PROVENANCE, FILE_MISMATCH, STALE_ROOTS, ROOTS_TIMEOUT, ROOTS_UNAVAILABLE }
    static final class Failure extends IOException {
        private final Reason reason;
        Failure(Reason reason, String message) { super(message); this.reason = reason; }
        Reason reason() { return reason; }
    }
    record Verified(String session, long generation, long evidenceRevision, Path destination, CareerSidecarStore.SaveRevision bytes) { }
    /** Not reconstructible from a path/hash/report or after restarting the process. Weak roots remain in Binding only. */
    static final class Captured {
        private final WrittenSaveVerifier owner;
        private final SaveFileObserver.Observation observed;
        private final WrittenSaveProvenance.Binding binding;
        private Captured(WrittenSaveVerifier owner, SaveFileObserver.Observation observed, WrittenSaveProvenance.Binding binding) {
            this.owner = owner; this.observed = observed; this.binding = binding;
        }
        WrittenSaveProvenance.Binding binding() { return binding; }
        void revalidate() throws IOException, InterruptedException {
            owner.verify(observed);
            if (HookProbe.writtenSaveProvenance().binding().orElse(null) != binding)
                throw new Failure(Reason.STALE_ROOTS, "Original writer observation no longer current");
        }
    }
    private final SaveFileObserver observer;
    private final CareerCommandDispatcher.RootsSource roots;
    private final long timeoutNanos;
    WrittenSaveVerifier(LegacyRuntime runtime) {
        this(new SaveFileObserver(), () -> {
            Class<?> state = Class.forName("c.a", false, runtime);
            return new CareerCommandDispatcher.Roots(state.getField("SR").get(null), state.getField("afQ").get(null));
        }, Duration.ofSeconds(3));
    }
    WrittenSaveVerifier(SaveFileObserver observer, CareerCommandDispatcher.RootsSource roots, Duration timeout) {
        this.observer = Objects.requireNonNull(observer); this.roots = Objects.requireNonNull(roots);
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Bounded output verification timeout required");
        timeoutNanos = timeout.toNanos();
    }
    Verified verify(SaveFileObserver.Observation observed) throws IOException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) throw new Failure(Reason.WRONG_THREAD, "Verify written output off EDT");
        var binding = HookProbe.writtenSaveProvenance().binding().orElseThrow(() -> new Failure(Reason.NO_PROVENANCE, "No complete original output stream evidence"));
        observer.verify(Objects.requireNonNull(observed)); CareerSidecarStore.plain(binding.destination());
        Path selected = observed.directory().resolve(observed.name());
        if (!binding.destination().toRealPath().equals(selected.toRealPath()) || !binding.bytes().equals(observed.revision()))
            throw new Failure(Reason.FILE_MISMATCH, "Selected file differs from actual written stream destination/bytes");
        var query = new FutureTask<Boolean>(() -> {
            var current = roots.current();
            return current != null && HookProbe.currentWrittenProvenance(binding, current.primary(), current.secondary());
        });
        SwingUtilities.invokeLater(query);
        try {
            if (!query.get(timeoutNanos, TimeUnit.NANOSECONDS)) throw new Failure(Reason.STALE_ROOTS, "Written root identity/generation no longer current");
        } catch (TimeoutException timeout) { query.cancel(false); throw new Failure(Reason.ROOTS_TIMEOUT, "Written roots lookup timed out; no authority granted"); }
        catch (ExecutionException failure) { throw new Failure(Reason.ROOTS_UNAVAILABLE, "Written roots could not be checked"); }
        catch (InterruptedException interrupted) { query.cancel(false); throw interrupted; }
        observer.verify(observed);
        if (HookProbe.writtenSaveProvenance().binding().orElse(null) != binding) throw new Failure(Reason.STALE_ROOTS, "Save evidence changed during verification");
        return new Verified(binding.session(), binding.generation(), binding.revision(), binding.destination(), binding.bytes());
    }
    Captured capture(SaveFileObserver.Observation observed) throws IOException, InterruptedException {
        var verified = verify(observed);
        var binding = HookProbe.writtenSaveProvenance().binding().orElseThrow(() -> new Failure(Reason.NO_PROVENANCE, "Writer capture disappeared"));
        if (!binding.session().equals(verified.session()) || binding.generation() != verified.generation() || binding.revision() != verified.evidenceRevision()
                || !binding.bytes().equals(verified.bytes()) || !binding.destination().equals(verified.destination()))
            throw new Failure(Reason.STALE_ROOTS, "Writer changed while capturing evidence");
        return new Captured(this, observed, binding);
    }
}
