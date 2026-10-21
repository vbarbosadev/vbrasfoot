package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Durable causal execution evidence on a DEDICATED root. MEMORY_APPLIED never means included in a save. */
final class FinanceExecutionWitnessStore {
    enum State { ARMED, MEMORY_APPLIED, NO_EFFECT_CONFIRMED, OUTCOME_UNKNOWN }
    record Witness(UUID operationId, CareerCommandJournal.Intent intent, UUID context, long generation, State state) { }
    record Snapshot(String token, List<Witness> witnesses, List<FinanceSerializationEvidence.Observation> serializations) {
        Snapshot { witnesses = List.copyOf(witnesses); serializations = List.copyOf(serializations); }
        Snapshot(String token, List<Witness> witnesses) { this(token, witnesses, List.of()); }
    }
    record Recovery(Snapshot snapshot, SaveRevision recordedSave, SaveRevision observedSave) {
        boolean revisionChanged() { return !recordedSave.equals(observedSave); }
    }
    record Armed(Snapshot snapshot, Lease lease) { }
    /** Constructible only from a verified committed NO_EFFECT witness, never a caller-supplied receipt. */
    static final class NoEffectProof {
        private final FinanceExecutionWitnessStore owner;
        private final SaveRevision save;
        private final String token;
        private final Witness witness;
        private NoEffectProof(FinanceExecutionWitnessStore owner, SaveRevision save, String token, Witness witness) {
            this.owner = owner; this.save = save; this.token = token; this.witness = witness;
        }
        Key key() { return owner.key; } SaveRevision save() { return save; } String token() { return token; }
        Witness witness() { return witness; }
        void revalidate() throws IOException {
            var current = owner.read(save); requireToken(current, token);
            if (!current.witnesses().contains(witness) || witness.state() != State.NO_EFFECT_CONFIRMED)
                throw new Failure(Reason.RECONCILIATION_REQUIRED, "Committed no-effect witness is no longer current");
        }
    }
    static final class Lease {
        private final UUID store, operation;
        private final SaveRevision save;
        private Lease(UUID store, UUID operation, SaveRevision save) { this.store = store; this.operation = operation; this.save = save; }
    }
    private static final int MAGIC = 0x56424645, MAX_WITNESSES = 32;
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
        return decode(view);
    }
    /** Current committed anchor for explicit review only, never migration, invocation or save-inclusion proof. */
    Recovery inspectRecordedRecovery(SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave); var view = store.inspectFinanceExecution(key);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Uncommitted execution namespace requires review");
        return new Recovery(decode(view), view.value().map(Value::save).orElse(observedSave), observedSave);
    }
    NoEffectProof proveNoEffect(SaveRevision save, String expectedToken, UUID operationId, CareerCommandJournal.Intent intent) throws IOException {
        Objects.requireNonNull(operationId); Objects.requireNonNull(intent);
        var snapshot = read(save); requireToken(snapshot, expectedToken);
        var witness = snapshot.witnesses().stream().filter(value -> value.operationId().equals(operationId) && value.intent().equals(intent)).findFirst()
                .orElseThrow(() -> new Failure(Reason.RECONCILIATION_REQUIRED, "Exact operation UUID and payload witness required"));
        if (witness.state() != State.NO_EFFECT_CONFIRMED) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Only durable NO_EFFECT can prove absence of execution effects");
        return new NoEffectProof(this, save, snapshot.token(), witness);
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
        var committed = store.write(key, context.save(), schema(current.serializations()), expectedToken, encode(values, current.serializations()));
        return new Armed(new Snapshot(committed.token(), values, current.serializations()), new Lease(instance, prepared.operationId(), context.save()));
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
        if (terminal == State.NO_EFFECT_CONFIRMED && current.serializations().stream().flatMap(value -> value.operations().stream())
                .anyMatch(value -> value.capture().operationId().equals(old.operationId())))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Serialization observations contradict no-effect completion");
        values.set(index, new Witness(old.operationId(), old.intent(), old.context(), old.generation(), terminal));
        var committed = store.write(key, lease.save, schema(current.serializations()), expectedToken, encode(values, current.serializations()));
        return new Snapshot(committed.token(), values, current.serializations());
    }
    /** Explicit off-EDT internal collection; not an automatic production writer, effect retry or inclusion adjudicator. */
    Snapshot observeSerialization(SaveRevision source, String expectedToken, WrittenSaveVerifier.Captured captured,
                                  FinanceRequestStore contracts, CareerCommandJournal journal) throws IOException, InterruptedException {
        Objects.requireNonNull(captured); Objects.requireNonNull(contracts); Objects.requireNonNull(journal);
        if (!contracts.matchesKey(key) || !journal.matchesKey(key)) throw new Failure(Reason.CONFLICT, "Exact owner/career/binding stores required");
        var current = read(source); requireToken(current, expectedToken);
        captured.revalidate(); var binding = captured.binding(); var capture = binding.operationCapture();
        if (!capture.validWindow() || capture.operations().isEmpty()) throw new Failure(Reason.RECONCILIATION_REQUIRED, "No valid operation-specific writer window");
        var requests = contracts.read(source); var history = journal.read(source);
        var selected = new ArrayList<FinanceSerializationEvidence.Operation>();
        for (var operation : capture.operations()) {
            var witness = current.witnesses().stream().filter(value -> value.operationId().equals(operation.operationId())).findFirst();
            if (witness.isEmpty()) continue; // Another explicitly scoped owner's UUID, never adopt it by matching values/key.
            var execution = witness.orElseThrow();
            var receipt = history.receipts().stream().filter(value -> value.operationId().equals(operation.operationId()) && value.intent().equals(operation.intent())).findFirst();
            boolean contract = requests.requests().stream().anyMatch(value -> value.intent().equals(operation.intent()));
            if (!execution.intent().equals(operation.intent()) || !contract || receipt.isEmpty() || execution.state() == State.NO_EFFECT_CONFIRMED
                    || receipt.orElseThrow().state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED)
                throw new Failure(Reason.RECONCILIATION_REQUIRED, "Writer UUID/intent must match exact contract, raw receipt and execution witness");
            var table = capture.referenceTraces().stream().filter(value -> value.operationId().equals(operation.operationId())).findFirst()
                    .map(value -> new FinanceReferenceTableEvidence(1, value.summary()));
            selected.add(new FinanceSerializationEvidence.Operation(operation, execution.state(), receipt.orElseThrow().state(), table));
        }
        if (selected.isEmpty()) throw new Failure(Reason.RECONCILIATION_REQUIRED, "No witnessed operation for this exact namespace");
        var prior = current.serializations().stream().filter(value -> value.id().equals(binding.observationId())).findFirst();
        if (prior.isPresent()) {
            var value = prior.orElseThrow();
            if (!value.output().equals(binding.bytes()) || !value.operations().equals(selected) || !value.requestToken().equals(requests.token()) || !value.journalToken().equals(history.token()))
                throw new Failure(Reason.CONFLICT, "Existing writer observation cannot be changed");
            requireToken(read(source), expectedToken); captured.revalidate();
            return current; // Read-only confirmation, never an effect lease or another append.
        }
        if (current.serializations().size() >= FinanceSerializationEvidence.MAX_OBSERVATIONS) throw new Failure(Reason.QUOTA, "Serialization observation capacity exhausted; no eviction");
        var observation = new FinanceSerializationEvidence.Observation(binding.observationId(), binding.session(), binding.generation(), binding.revision(), binding.bytes(),
                current.token(), requests.token(), history.token(), capture.capacityExceeded(), FinanceMutationAssessment.assess(Optional.empty(), binding).serializerWindow(), selected);
        var values = new ArrayList<>(current.serializations()); values.add(observation);
        captured.revalidate();
        if (!requests.equals(contracts.read(source)) || !history.equals(journal.read(source))) throw new Failure(Reason.CONFLICT, "Source histories changed before observation commit");
        var committed = store.write(key, source, schema(values), expectedToken, encode(current.witnesses(), values));
        try {
            captured.revalidate();
            if (!requests.equals(contracts.read(source)) || !history.equals(journal.read(source)) || !read(source).token().equals(committed.token()))
                throw new Failure(Reason.CONFLICT, "History changed after observation commit");
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new Failure(Reason.OUTCOME_UNKNOWN, "Observation may be committed; inspect without retrying the effect", failure);
        }
        return new Snapshot(committed.token(), current.witnesses(), values);
    }
    private static void requireToken(Snapshot current, String expected) throws Failure {
        if (!current.token().equals(expected)) throw new Failure(Reason.CONFLICT, "Execution witness changed; inspect before deciding");
    }
    private static Snapshot decode(View view) throws IOException {
        if (view.value().isEmpty()) return new Snapshot(view.token(), List.of()); var value = view.value().orElseThrow();
        if (value.schema() != 1 && value.schema() != 2 && value.schema() != 3 && value.schema() != 4) throw new Failure(Reason.CORRUPT, "Unknown execution witness schema");
        try (var in = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            if (in.readInt() != MAGIC || in.readInt() != value.schema()) throw new IOException("Unknown execution witness format");
            int count = in.readInt(); if (count < 1 || count > MAX_WITNESSES) throw new IOException("Invalid witness count");
            var ids = new HashSet<UUID>(); var keys = new HashSet<String>(); var values = new ArrayList<Witness>();
            for (int i = 0; i < count; i++) {
                UUID id = uuid(in); var intent = new CareerCommandJournal.Intent(in.readUTF(), CareerCommandGate.Operation.valueOf(in.readUTF()), in.readUTF());
                UUID context = uuid(in); long generation = in.readLong(); var state = State.valueOf(in.readUTF());
                if (generation < 1 || !ids.add(id) || !keys.add(intent.key()) || intent.operation() != CareerCommandGate.Operation.FINANCE_CREDIT
                        && intent.operation() != CareerCommandGate.Operation.FINANCE_DEBIT) throw new IOException("Invalid/duplicate witness identity or operation");
                values.add(new Witness(id, intent, context, generation, state));
            }
            var observations = value.schema() == 1 ? List.<FinanceSerializationEvidence.Observation>of() : FinanceSerializationEvidence.read(in, value.schema());
            if (value.schema() >= 3 && schema(observations) != value.schema()) throw new IOException("Schema lacks its declared versioned observations");
            for (var observation : observations) for (var item : observation.operations()) {
                var witness = values.stream().filter(entry -> entry.operationId().equals(item.capture().operationId()) && entry.intent().equals(item.capture().intent())).findFirst();
                if (witness.isEmpty() || witness.orElseThrow().state() == State.NO_EFFECT_CONFIRMED
                        || item.execution() != witness.orElseThrow().state() && item.execution() != State.ARMED)
                    throw new IOException("Observation lacks matching non-no-effect witness state");
            }
            if (in.available() != 0) throw new IOException("Trailing witness bytes"); return new Snapshot(view.token(), values, observations);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid execution witness", invalid); }
    }
    private static int schema(List<FinanceSerializationEvidence.Observation> observations) {
        if (observations.isEmpty()) return 1;
        if (observations.stream().flatMap(value -> value.operations().stream()).anyMatch(value -> value.referenceTable().isPresent())) return 4;
        return observations.stream().flatMap(value -> value.operations().stream()).anyMatch(value -> value.capture().qualifications().version() > 0) ? 3 : 2;
    }
    private static byte[] encode(List<Witness> values, List<FinanceSerializationEvidence.Observation> observations) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(schema(observations)); out.writeInt(values.size());
            for (var value : values) {
                uuid(out, value.operationId()); out.writeUTF(value.intent().key()); out.writeUTF(value.intent().operation().name()); out.writeUTF(value.intent().payloadDigest());
                uuid(out, value.context()); out.writeLong(value.generation()); out.writeUTF(value.state().name());
            }
            if (!observations.isEmpty()) FinanceSerializationEvidence.write(out, observations, schema(observations));
        } return bytes.toByteArray();
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
}
