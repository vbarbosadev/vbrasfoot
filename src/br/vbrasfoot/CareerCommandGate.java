package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.SwingUtilities;

/** Internal, session-only command reservation. No game handler, persistent ledger or public capability. */
final class CareerCommandGate implements AutoCloseable {
    enum Operation { FINANCE_CREDIT, FINANCE_DEBIT, TRANSFER_ACCEPT, TRANSFER_COUNTEROFFER }
    enum Status { RESERVED, RUNNING, APPLIED, REJECTED, OUTCOME_UNKNOWN }
    enum Reason { NONE, PERMISSION, OWNER_REVOKED, CLOSED, NO_CONTEXT, STALE_CONTEXT, REQUEST_CONFLICT, BUSY, CAPACITY,
        WRONG_THREAD, INVALIDATED, NO_EFFECT_CONFIRMED, EXECUTION_UNCERTAIN, SAVE_IN_PROGRESS }
    record Context(UUID token, UUID career, UUID binding, long generation, CareerSidecarStore.SaveRevision save) { }
    record Request(UUID context, String key, Operation operation, String payloadDigest) {
        Request {
            Objects.requireNonNull(context); Objects.requireNonNull(operation);
            if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}") || payloadDigest == null || !payloadDigest.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid command key/digest");
        }
    }
    record Receipt(String owner, Request request, Status status, Reason reason) { }
    record Reservation(Receipt receipt, Optional<Permit> permit) { }
    record Start(Receipt receipt, boolean invocationGranted) { }
    static final class Owner {
        private final UUID gate;
        private final String id;
        private final Set<Operation> operations;
        private boolean active = true;
        private Owner(UUID gate, String id, Set<Operation> operations) { this.gate = gate; this.id = id; this.operations = Set.copyOf(operations); }
    }
    static final class Permit {
        private final UUID gate;
        private final Slot slot;
        private Permit(UUID gate, Slot slot) { this.gate = gate; this.slot = slot; }
    }
    static final class SaveFence {
        private final UUID gate;
        private SaveFence(UUID gate) { this.gate = gate; }
    }
    private static final class Slot {
        final Owner owner; final Request request;
        Status status = Status.RESERVED; Reason reason = Reason.NONE;
        Slot(Owner owner, Request request) { this.owner = owner; this.request = request; }
        Receipt receipt() { return new Receipt(owner.id, request, status, reason); }
    }
    private record Identity(String owner, String key) { }
    private final UUID session = UUID.randomUUID();
    private final Map<String, Owner> owners = new HashMap<>();
    private final Map<Identity, Slot> commands = new LinkedHashMap<>();
    private final Set<Slot> executing = new HashSet<>();
    private final Set<Slot> dispatching = new HashSet<>();
    private Context context;
    private WeakReference<Object> primary = new WeakReference<>(null), secondary = new WeakReference<>(null);
    private Slot active;
    private SaveFence saveFence;
    private boolean closed;
    synchronized Owner owner(String id, Set<Operation> operations) {
        if (closed) throw new IllegalStateException("Command gate closed");
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,99}")) throw new IllegalArgumentException("Invalid owner ID");
        if (owners.size() >= 64 || owners.containsKey(id)) throw new IllegalStateException("Owner exists or owner capacity exhausted");
        Owner owner = new Owner(session, id, operations); owners.put(id, owner); return owner;
    }
    /** Trusted caller must supply an independently verified association; this method does not discover one. */
    synchronized Context installContext(UUID career, UUID binding, long generation, CareerSidecarStore.SaveRevision save, Object primary, Object secondary) {
        if (closed) throw new IllegalStateException("Command gate closed");
        if (!executing.isEmpty()) throw new IllegalStateException("Previous original handler has not returned");
        if (!dispatching.isEmpty()) throw new IllegalStateException("Previous command persistence has not returned");
        if (saveFence != null) throw new IllegalStateException("Original save window is still open");
        Objects.requireNonNull(career); Objects.requireNonNull(binding); Objects.requireNonNull(save);
        Objects.requireNonNull(primary); Objects.requireNonNull(secondary);
        if (generation < 1) throw new IllegalArgumentException("Invalid generation");
        invalidateActive(Reason.INVALIDATED);
        this.primary = new WeakReference<>(primary); this.secondary = new WeakReference<>(secondary);
        return context = new Context(UUID.randomUUID(), career, binding, generation, save);
    }
    synchronized void invalidate() {
        invalidateActive(Reason.INVALIDATED); context = null; primary.clear(); secondary.clear();
    }
    /** Trusted coordinator must acquire BEFORE invoking the original writer, never from a post-save event.
     * Refuses queued/running work. No waiting on the EDT, no writer invocation and no save-success claim. */
    synchronized SaveFence beginSave() {
        if (closed) throw new IllegalStateException("Command gate closed");
        if (saveFence != null || active != null || !executing.isEmpty() || !dispatching.isEmpty())
            throw new IllegalStateException("Commands or original save window still active");
        invalidate();
        return saveFence = new SaveFence(session);
    }
    /** Closing a window does not revalidate roots, retarget journals or restore the previous context. */
    synchronized void endSave(SaveFence fence) {
        if (fence == null || !session.equals(fence.gate) || saveFence != fence)
            throw new IllegalArgumentException("Foreign or already closed save fence");
        saveFence = null;
    }
    synchronized void revoke(Owner owner) {
        requireOwner(owner); owner.active = false;
        if (active != null && active.owner == owner) invalidateActive(Reason.OWNER_REVOKED);
    }
    synchronized Reservation reserve(Owner owner, Request request, Object primary, Object secondary) {
        requireOwner(owner); Objects.requireNonNull(request);
        Reason rejection = closed ? Reason.CLOSED : !owner.active ? Reason.OWNER_REVOKED
                : !owner.operations.contains(request.operation()) ? Reason.PERMISSION : saveFence != null ? Reason.SAVE_IN_PROGRESS : Reason.NONE;
        if (rejection != Reason.NONE) return denied(owner, request, rejection);
        Identity identity = new Identity(owner.id, request.key()); Slot previous = commands.get(identity);
        if (previous != null) {
            if (!previous.request.equals(request)) return denied(owner, request, Reason.REQUEST_CONFLICT);
            return new Reservation(previous.receipt(), Optional.empty()); // Never reissue a permit, including uncertain results.
        }
        if (context == null) return denied(owner, request, Reason.NO_CONTEXT);
        if (!current(request, primary, secondary)) return denied(owner, request, Reason.STALE_CONTEXT);
        if (active != null) return denied(owner, request, Reason.BUSY);
        if (commands.size() >= 256) return denied(owner, request, Reason.CAPACITY); // Never evict receipts to allow a duplicate effect.
        Slot slot = new Slot(owner, request); commands.put(identity, slot); active = slot;
        return new Reservation(slot.receipt(), Optional.of(new Permit(session, slot)));
    }
    Start begin(Permit permit, Object primary, Object secondary) {
        boolean edt = SwingUtilities.isEventDispatchThread();
        synchronized (this) {
            Slot slot = requirePermit(permit);
            if (slot.status != Status.RESERVED) return new Start(slot.receipt(), false);
            Reason rejection = closed ? Reason.CLOSED : !slot.owner.active ? Reason.OWNER_REVOKED
                    : !edt ? Reason.WRONG_THREAD : !current(slot.request, primary, secondary) ? Reason.STALE_CONTEXT : Reason.NONE;
            if (rejection != Reason.NONE) { reject(slot, rejection); return new Start(slot.receipt(), false); }
            slot.status = Status.RUNNING; executing.add(slot); return new Start(slot.receipt(), true);
        }
    }
    synchronized Receipt cancel(Permit permit) {
        Slot slot = requirePermit(permit);
        if (slot.status == Status.RESERVED) reject(slot, Reason.NO_EFFECT_CONFIRMED);
        return slot.receipt(); // Running commands cannot be declared unapplied by cancellation.
    }
    /** Only an original handler that can prove no effect may report NO_EFFECT_CONFIRMED. Throwable alone cannot. */
    synchronized Receipt finish(Permit permit, Status status, Object primary, Object secondary) {
        Slot slot = requirePermit(permit);
        if (status != Status.APPLIED && status != Status.REJECTED && status != Status.OUTCOME_UNKNOWN) throw new IllegalArgumentException("Invalid completion status");
        executing.remove(slot);
        if (slot.status != Status.RUNNING) { if (active == slot) active = null; return slot.receipt(); }
        if (closed || !slot.owner.active || !current(slot.request, primary, secondary)) {
            slot.status = Status.OUTCOME_UNKNOWN; slot.reason = Reason.INVALIDATED;
        } else { slot.status = status; slot.reason = status == Status.APPLIED ? Reason.NONE : status == Status.REJECTED ? Reason.NO_EFFECT_CONFIRMED : Reason.EXECUTION_UNCERTAIN; }
        if (active == slot) active = null;
        if (slot.status == Status.OUTCOME_UNKNOWN) { context = null; this.primary.clear(); this.secondary.clear(); }
        return slot.receipt();
    }
    /** Storage failure/timeout after start cannot be described as a rollback or safely replayed. */
    synchronized Receipt uncertain(Permit permit) {
        Slot slot = requirePermit(permit);
        if (slot.status == Status.RESERVED) { reject(slot, Reason.NO_EFFECT_CONFIRMED); return slot.receipt(); }
        if (slot.status == Status.RUNNING || slot.status == Status.APPLIED) {
            slot.status = Status.OUTCOME_UNKNOWN; slot.reason = Reason.EXECUTION_UNCERTAIN;
            context = null; primary.clear(); secondary.clear();
        }
        return slot.receipt();
    }
    synchronized String ownerId(Owner owner) { requireOwner(owner); return owner.id; }
    /** Dispatcher holds this through journal completion, not just the original handler's return. */
    synchronized void retainDispatch(Permit permit) {
        Slot slot = requirePermit(permit);
        if (slot.status != Status.RESERVED || !dispatching.add(slot)) throw new IllegalStateException("Dispatch reservation is no longer retainable");
    }
    synchronized void releaseDispatch(Permit permit) { dispatching.remove(requirePermit(permit)); }
    private Reservation denied(Owner owner, Request request, Reason reason) {
        return new Reservation(new Receipt(owner.id, request, Status.REJECTED, reason), Optional.empty());
    }
    private boolean current(Request request, Object primary, Object secondary) {
        return context != null && context.token().equals(request.context()) && primary != null && secondary != null
                && this.primary.get() == primary && this.secondary.get() == secondary;
    }
    private void requireOwner(Owner owner) {
        if (owner == null || !session.equals(owner.gate) || owners.get(owner.id) != owner) throw new IllegalArgumentException("Foreign owner authority");
    }
    private Slot requirePermit(Permit permit) {
        if (permit == null || !session.equals(permit.gate) || commands.get(new Identity(permit.slot.owner.id, permit.slot.request.key())) != permit.slot)
            throw new IllegalArgumentException("Foreign command permit");
        return permit.slot;
    }
    private void reject(Slot slot, Reason reason) {
        slot.status = Status.REJECTED; slot.reason = reason; if (active == slot) active = null;
    }
    private void invalidateActive(Reason reason) {
        if (active == null) return;
        active.status = active.status == Status.RUNNING ? Status.OUTCOME_UNKNOWN : Status.REJECTED;
        active.reason = reason;
        if (active.status == Status.OUTCOME_UNKNOWN) { context = null; primary.clear(); secondary.clear(); }
        active = null;
    }
    synchronized List<Receipt> receipts() { return commands.values().stream().map(Slot::receipt).toList(); }
    @Override public synchronized void close() {
        if (closed) return; invalidateActive(Reason.CLOSED); context = null; primary.clear(); secondary.clear();
        owners.values().forEach(owner -> owner.active = false); closed = true;
    }
}
