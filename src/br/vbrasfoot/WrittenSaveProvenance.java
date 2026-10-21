package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;

/** One bounded observation of original serialized roots and actual output bytes. NEVER an operation inclusion proof. */
final class WrittenSaveProvenance {
    enum Status { EMPTY, INVALIDATED, REJECTED, STREAM_BOUND }
    static final class Binding {
        private final UUID observationId = UUID.randomUUID();
        private final CareerLifecycle.Ticket ticket;
        private final long revision;
        private final Path destination;
        private final CareerSidecarStore.SaveRevision bytes;
        private final WeakReference<Object> primary, secondary;
        private final FinanceMutationTracker.Snapshot financeBefore, financeAfter;
        private final FinanceSerializationTracker.Capture operationCapture;
        private Binding(CareerLifecycle.Ticket ticket, long revision, Path destination, SaveOutputTrace.Result stream, Object primary, Object secondary,
                        FinanceMutationTracker.Snapshot financeBefore, FinanceMutationTracker.Snapshot financeAfter, FinanceSerializationTracker.Capture operationCapture) {
            this.ticket = ticket; this.revision = revision; this.destination = destination;
            bytes = new CareerSidecarStore.SaveRevision(stream.sha256(), stream.bytes());
            this.primary = new WeakReference<>(primary); this.secondary = new WeakReference<>(secondary);
            this.financeBefore = financeBefore; this.financeAfter = financeAfter;
            this.operationCapture = Objects.requireNonNull(operationCapture);
        }
        String session() { return ticket.session(); } long generation() { return ticket.generation(); }
        long revision() { return revision; } Path destination() { return destination; }
        CareerSidecarStore.SaveRevision bytes() { return bytes; }
        Optional<FinanceMutationTracker.Snapshot> financeBefore() { return Optional.ofNullable(financeBefore); }
        Optional<FinanceMutationTracker.Snapshot> financeAfter() { return Optional.ofNullable(financeAfter); }
        boolean noObservedFinanceWriteDuringSerialization() { return financeAfter != null && financeAfter.stableSince(financeBefore); }
        FinanceSerializationTracker.Capture operationCapture() { return operationCapture; }
        UUID observationId() { return observationId; }
        boolean matches(Object primary, Object secondary) { return primary != null && secondary != null && this.primary.get() == primary && this.secondary.get() == secondary; }
    }
    record Snapshot(Status status, long revision, Optional<Binding> binding) { }
    private long revision;
    private Status status = Status.EMPTY;
    private Binding binding;
    synchronized long invalidate() { revision++; status = Status.INVALIDATED; binding = null; return revision; }
    synchronized void complete(long expected, CareerLifecycle.Ticket started, CareerLifecycle.Ticket current, Path destination,
                                SaveOutputTrace.Result stream, boolean sequenceComplete, Object primary, Object secondary) {
        complete(expected, started, current, destination, stream, sequenceComplete, primary, secondary, null, null);
    }
    synchronized void complete(long expected, CareerLifecycle.Ticket started, CareerLifecycle.Ticket current, Path destination,
                               SaveOutputTrace.Result stream, boolean sequenceComplete, Object primary, Object secondary,
                               FinanceMutationTracker.Snapshot financeBefore, FinanceMutationTracker.Snapshot financeAfter) {
        complete(expected, started, current, destination, stream, sequenceComplete, primary, secondary, financeBefore, financeAfter, FinanceSerializationTracker.Capture.unavailable());
    }
    synchronized void complete(long expected, CareerLifecycle.Ticket started, CareerLifecycle.Ticket current, Path destination,
                               SaveOutputTrace.Result stream, boolean sequenceComplete, Object primary, Object secondary,
                               FinanceMutationTracker.Snapshot financeBefore, FinanceMutationTracker.Snapshot financeAfter, FinanceSerializationTracker.Capture operationCapture) {
        if (expected != revision) return; // Superseded attempt may never restore previous evidence.
        binding = null; status = Status.REJECTED;
        if (started == null || !started.active() || !started.equals(current) || !sequenceComplete || destination == null
                || !destination.isAbsolute() || !destination.equals(destination.normalize()) || !destination.getFileName().toString().endsWith(".s22")
                || stream == null || !stream.reliable() || primary == null || secondary == null || primary == secondary) return;
        binding = new Binding(started, revision, destination, stream, primary, secondary, financeBefore, financeAfter, operationCapture); status = Status.STREAM_BOUND;
    }
    synchronized Snapshot snapshot() { return new Snapshot(status, revision, Optional.ofNullable(binding)); }
    synchronized boolean current(Binding expected, CareerLifecycle.Ticket ticket, Object primary, Object secondary) {
        return expected != null && binding == expected && status == Status.STREAM_BOUND && expected.ticket.equals(ticket) && expected.matches(primary, secondary);
    }
}
