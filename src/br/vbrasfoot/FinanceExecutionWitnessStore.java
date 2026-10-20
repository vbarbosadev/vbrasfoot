package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Durable causal execution evidence on a DEDICATED root. MEMORY_APPLIED never means included in a save. */
final class FinanceExecutionWitnessStore {
    enum State { ARMED, MEMORY_APPLIED, NO_EFFECT_CONFIRMED, OUTCOME_UNKNOWN }
    record Witness(UUID operationId, CareerCommandJournal.Intent intent, UUID context, long generation, State state) { }
    record Snapshot(String token, List<Witness> witnesses) { Snapshot { witnesses = List.copyOf(witnesses); } }
    record Recovery(Snapshot snapshot, SaveRevision recordedSave, SaveRevision observedSave) {
        boolean revisionChanged() { return !recordedSave.equals(observedSave); }
    }
    record Armed(Snapshot snapshot, Lease lease) { }
    static final class Lease {
        private final UUID store, operation;
        private final SaveRevision save;
        private Lease(UUID store, UUID operation, SaveRevision save) { this.store = store; this.operation = operation; this.save = save; }
    }
    private static final int MAGIC = 0x56424645, FORMAT = 1, SCHEMA = 1, MAX_WITNESSES = 32;
    private final UUID instance = UUID.randomUUID();
    private final CareerSidecarStore store;
    private final Key key;
    FinanceExecutionWitnessStore(CareerSidecarStore store, Key key) { this.store = Objects.requireNonNull(store); this.key = Objects.requireNonNull(key); }
    boolean matches(CareerCommandGate.Context context, String owner) {
        return context != null && key.career().equals(context.career()) && key.binding().equals(context.binding()) && key.mod().equals(owner);
    }
    Snapshot read(SaveRevision save) throws IOException {
        var view = store.read(key, save);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Uncommitted witness namespace must not become a clean history");
        return new Snapshot(view.token(), decode(view));
    }
    /** Current committed anchor for explicit review only, never migration, invocation or save-inclusion proof. */
    Recovery inspectRecordedRecovery(SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave); var view = store.inspectFinanceExecution(key);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Uncommitted execution namespace requires review");
        return new Recovery(new Snapshot(view.token(), decode(view)), view.value().map(Value::save).orElse(observedSave), observedSave);
    }
    Armed arm(CareerCommandGate.Context context, CareerCommandJournal.ExecutionPermit prepared, String expectedToken) throws IOException {
        Objects.requireNonNull(prepared);
        if (!matches(context, key.mod()) || !prepared.matches(context, key.mod()) || context.generation() < 1
                || prepared.intent().operation() != CareerCommandGate.Operation.FINANCE_CREDIT && prepared.intent().operation() != CareerCommandGate.Operation.FINANCE_DEBIT)
            throw new IllegalArgumentException("Exact financial witness context required");
        var current = read(context.save()); requireToken(current, expectedToken);
        if (current.witnesses().stream().anyMatch(value -> value.operationId().equals(prepared.operationId()) || value.intent().key().equals(prepared.intent().key())))
            throw new Failure(Reason.CONFLICT, "Witness already exists; no old execution lease is ever reissued");
        if (current.witnesses().stream().anyMatch(value -> value.state() == State.ARMED || value.state() == State.OUTCOME_UNKNOWN))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Uncertain execution blocks new witnesses");
        if (current.witnesses().size() >= MAX_WITNESSES) throw new Failure(Reason.QUOTA, "Witness capacity exhausted; no eviction");
        var values = new ArrayList<>(current.witnesses());
        values.add(new Witness(prepared.operationId(), prepared.intent(), context.token(), context.generation(), State.ARMED));
        var committed = store.write(key, context.save(), SCHEMA, expectedToken, encode(values));
        return new Armed(new Snapshot(committed.token(), values), new Lease(instance, prepared.operationId(), context.save()));
    }
    /** Only trusted dispatcher after observed invocation/preflight outcome, never a balance comparison during recovery. */
    Snapshot complete(Lease lease, State terminal, String expectedToken) throws IOException {
        if (lease == null || !instance.equals(lease.store)) throw new IllegalArgumentException("Foreign or previous-process witness lease");
        if (terminal == null || terminal == State.ARMED) throw new IllegalArgumentException("Terminal execution evidence required");
        var current = read(lease.save); requireToken(current, expectedToken); var values = new ArrayList<>(current.witnesses());
        int index = -1;
        for (int i = 0; i < values.size(); i++) if (values.get(i).operationId().equals(lease.operation)) index = i;
        if (index < 0) throw new Failure(Reason.CONFLICT, "Witness intent disappeared");
        var old = values.get(index);
        if (old.state() != State.ARMED) {
            if (old.state() != terminal) throw new Failure(Reason.CONFLICT, "Terminal witness is immutable, including UNKNOWN");
            return current;
        }
        values.set(index, new Witness(old.operationId(), old.intent(), old.context(), old.generation(), terminal));
        var committed = store.write(key, lease.save, SCHEMA, expectedToken, encode(values));
        return new Snapshot(committed.token(), values);
    }
    private static void requireToken(Snapshot current, String expected) throws Failure {
        if (!current.token().equals(expected)) throw new Failure(Reason.CONFLICT, "Execution witness changed; inspect before deciding");
    }
    private static List<Witness> decode(View view) throws IOException {
        if (view.value().isEmpty()) return List.of(); var value = view.value().orElseThrow();
        if (value.schema() != SCHEMA) throw new Failure(Reason.CORRUPT, "Unknown execution witness schema");
        try (var in = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("Unknown execution witness format");
            int count = in.readInt(); if (count < 1 || count > MAX_WITNESSES) throw new IOException("Invalid witness count");
            var ids = new HashSet<UUID>(); var keys = new HashSet<String>(); var values = new ArrayList<Witness>();
            for (int i = 0; i < count; i++) {
                UUID id = uuid(in); var intent = new CareerCommandJournal.Intent(in.readUTF(), CareerCommandGate.Operation.valueOf(in.readUTF()), in.readUTF());
                UUID context = uuid(in); long generation = in.readLong(); var state = State.valueOf(in.readUTF());
                if (generation < 1 || !ids.add(id) || !keys.add(intent.key()) || intent.operation() != CareerCommandGate.Operation.FINANCE_CREDIT
                        && intent.operation() != CareerCommandGate.Operation.FINANCE_DEBIT) throw new IOException("Invalid/duplicate witness identity or operation");
                values.add(new Witness(id, intent, context, generation, state));
            }
            if (in.available() != 0) throw new IOException("Trailing witness bytes"); return List.copyOf(values);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid execution witness", invalid); }
    }
    private static byte[] encode(List<Witness> values) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeInt(values.size());
            for (var value : values) {
                uuid(out, value.operationId()); out.writeUTF(value.intent().key()); out.writeUTF(value.intent().operation().name()); out.writeUTF(value.intent().payloadDigest());
                uuid(out, value.context()); out.writeLong(value.generation()); out.writeUTF(value.state().name());
            }
        } return bytes.toByteArray();
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
}
