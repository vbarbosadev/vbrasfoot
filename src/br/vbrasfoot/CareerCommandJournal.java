package br.vbrasfoot;

import java.io.*;
import java.util.*;

/** Internal durable intents for an explicit binding/recorded revision, never a public mod write service. */
final class CareerCommandJournal {
    enum State { PREPARED, APPLIED, NO_EFFECT_CONFIRMED, OUTCOME_UNKNOWN }
    record Intent(String key, CareerCommandGate.Operation operation, String payloadDigest) {
        Intent {
            Objects.requireNonNull(operation);
            if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}") || payloadDigest == null || !payloadDigest.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid durable command intent");
        }
    }
    record Receipt(UUID operationId, Intent intent, State state) { }
    record NoEffectResolution(UUID operationId, Intent intent, State originalState, String witnessToken, UUID context, long generation) { }
    record Snapshot(String token, List<Receipt> receipts, List<NoEffectResolution> noEffectResolutions) {
        Snapshot(String token, List<Receipt> receipts) { this(token, receipts, List.of()); }
        Snapshot { receipts = List.copyOf(receipts); noEffectResolutions = List.copyOf(noEffectResolutions); }
        /** Derived view only. Raw UNKNOWN receipts and their original UUID/intent remain permanently visible. */
        List<Receipt> effectiveReceipts() {
            return receipts.stream().map(receipt -> noEffectResolutions.stream().anyMatch(value -> value.operationId().equals(receipt.operationId()) && value.intent().equals(receipt.intent()))
                    ? new Receipt(receipt.operationId(), receipt.intent(), State.NO_EFFECT_CONFIRMED) : receipt).toList();
        }
    }
    record Prepared(Snapshot snapshot, Optional<Lease> lease) { }
    enum RecoveryStatus { EMPTY, NO_EFFECT_ONLY, HISTORICAL_EFFECTS, UNCERTAIN }
    /** Diagnostic only: even matching bytes do not establish effects in the loaded graph. */
    record Recovery(Snapshot snapshot, CareerSidecarStore.SaveRevision recordedSave,
                    CareerSidecarStore.SaveRevision observedSave, RecoveryStatus status) {
        boolean revisionChanged() { return !recordedSave.equals(observedSave); }
    }
    static final class NoEffectReanchor {
        private final CareerSidecarStore.Key key;
        private final CareerSidecarStore.SaveRevision previous, next;
        private final String token;
        private final byte[] payload;
        private NoEffectReanchor(CareerSidecarStore.Key key, CareerSidecarStore.SaveRevision previous,
                                 CareerSidecarStore.SaveRevision next, String token, byte[] payload) {
            this.key = key; this.previous = previous; this.next = next; this.token = token; this.payload = payload.clone();
        }
        CareerSidecarStore.Key key() { return key; } CareerSidecarStore.SaveRevision previous() { return previous; }
        CareerSidecarStore.SaveRevision next() { return next; } String token() { return token; }
        int schema() { return SCHEMA; } byte[] payload() { return payload.clone(); }
    }
    static final class Lease {
        private final UUID journal, operation;
        private final Intent intent;
        private final CareerSidecarStore.SaveRevision save;
        private Lease(UUID journal, UUID operation, Intent intent, CareerSidecarStore.SaveRevision save) {
            this.journal = journal; this.operation = operation; this.intent = intent; this.save = save;
        }
    }
    /** Opaque fresh-PREPARED proof; cannot be constructed from a historical receipt or after reopening. */
    static final class ExecutionPermit {
        private final CareerSidecarStore.Key key;
        private final CareerSidecarStore.SaveRevision save;
        private final UUID operation;
        private final Intent intent;
        private ExecutionPermit(CareerSidecarStore.Key key, CareerSidecarStore.SaveRevision save, UUID operation, Intent intent) {
            this.key = key; this.save = save; this.operation = operation; this.intent = intent;
        }
        boolean matches(CareerCommandGate.Context context, String owner) {
            return context != null && context.career().equals(key.career()) && context.binding().equals(key.binding())
                    && context.save().equals(save) && key.mod().equals(owner);
        }
        UUID operationId() { return operation; } Intent intent() { return intent; }
        boolean matches(FinanceRequest request) {
            return request != null && key.career().equals(request.career()) && key.binding().equals(request.binding())
                    && key.mod().equals(request.owner()) && save.equals(request.save()) && intent.equals(request.intent());
        }
    }
    private static final int MAGIC = 0x5642434a, FORMAT = 2, SCHEMA = 2, MAX_INTENTS = 32;
    private static final class Entries extends LinkedHashMap<String, Receipt> {
        final List<NoEffectResolution> resolutions = new ArrayList<>();
    }
    private final UUID instance = UUID.randomUUID();
    private final CareerSidecarStore store;
    private final CareerSidecarStore.Key key;
    /** Store root must be dedicated to command journals, not mod contract payloads. */
    CareerCommandJournal(CareerSidecarStore store, CareerSidecarStore.Key key) { this.store = Objects.requireNonNull(store); this.key = Objects.requireNonNull(key); }
    boolean matches(CareerCommandGate.Context context, String owner) {
        return context != null && key.career().equals(context.career()) && key.binding().equals(context.binding()) && key.mod().equals(owner);
    }
    Snapshot read(CareerSidecarStore.SaveRevision save) throws IOException {
        var view = store.read(key, save); return snapshot(view.token(), decode(view));
    }
    /** New persisted-payload path cannot interpret an interrupted initial journal write as a clean history. */
    void reviewStoredRequest(CareerSidecarStore.SaveRevision save, String expectedToken, Intent intent) throws IOException {
        var view = store.read(key, save); requireToken(view, expectedToken); var values = decode(view);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Existing uncommitted journal requires review, not retry");
        var previous = values.get(intent.key());
        if (previous != null && !previous.intent().equals(intent))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Historical journal key has another payload");
        if (previous == null && values.values().stream().anyMatch(receipt -> uncertain(values, receipt)))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Uncertain journal blocks a new stored request");
    }
    /** Explicit known old anchor required. Never retargets the journal, searches history or grants a lease. */
    Recovery inspectRecovery(CareerSidecarStore.SaveRevision recordedSave, CareerSidecarStore.SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave);
        Snapshot snapshot = read(recordedSave);
        RecoveryStatus status = snapshot.receipts().isEmpty() ? RecoveryStatus.EMPTY
                : snapshot.effectiveReceipts().stream().anyMatch(r -> r.state() == State.OUTCOME_UNKNOWN) ? RecoveryStatus.UNCERTAIN
                : snapshot.effectiveReceipts().stream().anyMatch(r -> r.state() == State.APPLIED) ? RecoveryStatus.HISTORICAL_EFFECTS
                : RecoveryStatus.NO_EFFECT_ONLY;
        return new Recovery(snapshot, recordedSave, observedSave, status);
    }
    /** Diagnostic current committed anchor, not identity discovery or permission to replay/migrate. */
    Recovery inspectRecordedRecovery(CareerSidecarStore.SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave); var view = store.inspectJournalRecord(key);
        var snapshot = snapshot(view.token(), decode(view));
        var recorded = view.value().map(CareerSidecarStore.Value::save).orElse(observedSave);
        RecoveryStatus status = snapshot.receipts().isEmpty() ? RecoveryStatus.EMPTY
                : snapshot.effectiveReceipts().stream().anyMatch(r -> r.state() == State.OUTCOME_UNKNOWN) ? RecoveryStatus.UNCERTAIN
                : snapshot.effectiveReceipts().stream().anyMatch(r -> r.state() == State.APPLIED) ? RecoveryStatus.HISTORICAL_EFFECTS : RecoveryStatus.NO_EFFECT_ONLY;
        return new Recovery(snapshot, recorded, observedSave, status);
    }
    /** Explicit narrow reconciliation only: preserve all receipts/keys and move anchor iff every result proves NO EFFECT.
     * APPLIED/PREPARED/UNKNOWN are never resolved by balance comparison or generic human acknowledgment. */
    Snapshot reanchorNoEffects(CareerSidecarStore.SaveRevision previous, CareerSidecarStore.SaveRevision next, String expectedToken) throws IOException {
        Objects.requireNonNull(next); var view = store.read(key, previous); requireToken(view, expectedToken); var values = decode(view);
        if (view.value().isEmpty() || values.isEmpty()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "No committed no-effect history to reconcile");
        if (values.values().stream().anyMatch(receipt -> receipt.state() != State.NO_EFFECT_CONFIRMED && !resolved(values, receipt)))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Historical applied or uncertain effects cannot be reanchored by this operation");
        if (previous.equals(next)) return snapshot(view.token(), values);
        var committed = store.reanchorNoEffectJournal(new NoEffectReanchor(key, previous, next, expectedToken, encode(values)));
        return snapshot(committed.token(), values);
    }
    /** Monotonic recovery: persist PREPARED as UNKNOWN, preserving all keys/digests/UUIDs and terminal results.
     * May conservatively fence a still-live lease, but can never make an uncertain command executable again. */
    Snapshot quarantinePending(CareerSidecarStore.SaveRevision save, String expectedToken) throws IOException {
        var view = store.read(key, save); requireToken(view, expectedToken); var values = decode(view);
        boolean changed = false;
        for (var entry : values.entrySet()) {
            Receipt receipt = entry.getValue();
            if (receipt.state() == State.PREPARED && !resolved(values, receipt)) {
                entry.setValue(new Receipt(receipt.operationId(), receipt.intent(), State.OUTCOME_UNKNOWN)); changed = true;
            }
        }
        if (!changed) return snapshot(view.token(), values);
        var committed = store.write(key, save, SCHEMA, expectedToken, encode(values));
        return snapshot(committed.token(), values);
    }
    Prepared prepare(CareerSidecarStore.SaveRevision save, String expectedToken, Intent intent) throws IOException {
        Objects.requireNonNull(intent); var view = store.read(key, save); requireToken(view, expectedToken);
        var values = decode(view); Receipt previous = values.get(intent.key());
        if (previous != null) {
            if (!previous.intent().equals(intent)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Command key already has another payload or operation");
            return new Prepared(snapshot(view.token(), values), Optional.empty()); // Never grant an existing PREPARED intent again.
        }
        if (values.values().stream().anyMatch(r -> uncertain(values, r)))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Pending or uncertain intent blocks new operations until explicit reconciliation exists");
        if (values.size() >= MAX_INTENTS) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.QUOTA, "Durable intent capacity exhausted; no automatic eviction");
        UUID operation = UUID.randomUUID(); values.put(intent.key(), new Receipt(operation, intent, State.PREPARED));
        var committed = store.write(key, save, SCHEMA, expectedToken, encode(values));
        return new Prepared(snapshot(committed.token(), values), Optional.of(new Lease(instance, operation, intent, save)));
    }
    Snapshot complete(Lease lease, State terminal, String expectedToken) throws IOException {
        if (lease == null || !instance.equals(lease.journal)) throw new IllegalArgumentException("Foreign or previous-process command lease");
        if (terminal == null || terminal == State.PREPARED) throw new IllegalArgumentException("Terminal result required");
        var view = store.read(key, lease.save); requireToken(view, expectedToken); var values = decode(view);
        Receipt pending = values.get(lease.intent.key());
        if (pending == null || !pending.operationId().equals(lease.operation) || !pending.intent().equals(lease.intent))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Durable intent changed");
        if (resolved(values, pending)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Resolved operation cannot complete a prior execution lease");
        if (pending.state() != State.PREPARED) {
            if (pending.state() != terminal) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Terminal command result cannot be overwritten");
            return snapshot(view.token(), values);
        }
        values.put(lease.intent.key(), new Receipt(lease.operation, lease.intent, terminal));
        var committed = store.write(key, lease.save, SCHEMA, expectedToken, encode(values));
        return snapshot(committed.token(), values);
    }
    ExecutionPermit executionPermit(Lease lease, String expectedToken) throws IOException {
        if (lease == null || !instance.equals(lease.journal)) throw new IllegalArgumentException("Fresh current-process command lease required");
        var view = store.read(key, lease.save); requireToken(view, expectedToken); var values = decode(view); var receipt = values.get(lease.intent.key());
        if (receipt == null || receipt.state() != State.PREPARED || resolved(values, receipt) || !receipt.operationId().equals(lease.operation) || !receipt.intent().equals(lease.intent))
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "Only the original still-PREPARED lease may arm an execution witness");
        return new ExecutionPermit(key, lease.save, lease.operation, lease.intent);
    }
    /** Append-only adjudication: raw receipt never changes and no old key receives a new lease. */
    Snapshot reconcileNoEffect(FinanceExecutionWitnessStore.NoEffectProof proof, String expectedToken) throws IOException {
        return reconcileNoEffects(List.of(Objects.requireNonNull(proof)), expectedToken);
    }
    Snapshot reconcileNoEffects(List<FinanceExecutionWitnessStore.NoEffectProof> proofs, String expectedToken) throws IOException {
        proofs = List.copyOf(proofs);
        if (proofs.isEmpty() || proofs.size() > MAX_INTENTS) throw new IllegalArgumentException("Bounded nonempty no-effect proof list required");
        var save = proofs.get(0).save();
        for (var proof : proofs) {
            if (!key.equals(proof.key()) || !save.equals(proof.save())) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "No-effect proof belongs to another journal scope/revision");
            proof.revalidate();
        }
        var view = store.read(key, save); requireToken(view, expectedToken); var values = decode(view); boolean changed = false;
        var ids = new HashSet<UUID>();
        for (var proof : proofs) {
            var witness = proof.witness(); var receipt = values.get(witness.intent().key());
            if (!ids.add(witness.operationId()) || receipt == null || !receipt.operationId().equals(witness.operationId()) || !receipt.intent().equals(witness.intent()))
                throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "No-effect proof does not match unique exact journal operation");
            if (receipt.state() == State.APPLIED) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.RECONCILIATION_REQUIRED, "APPLIED contradicts no-effect proof; never overwrite");
            if (receipt.state() == State.NO_EFFECT_CONFIRMED || resolved(values, receipt)) continue;
            values.resolutions.add(new NoEffectResolution(receipt.operationId(), receipt.intent(), receipt.state(), proof.token(), witness.context(), witness.generation())); changed = true;
        }
        if (!changed) return snapshot(view.token(), values);
        for (var proof : proofs) proof.revalidate();
        var committed = store.write(key, save, SCHEMA, expectedToken, encode(values));
        return snapshot(committed.token(), values);
    }
    private static boolean resolved(Entries values, Receipt receipt) {
        return values.resolutions.stream().anyMatch(value -> value.operationId().equals(receipt.operationId()) && value.intent().equals(receipt.intent()));
    }
    private static boolean uncertain(Entries values, Receipt receipt) {
        return !resolved(values, receipt) && (receipt.state() == State.PREPARED || receipt.state() == State.OUTCOME_UNKNOWN);
    }
    private static void requireToken(CareerSidecarStore.View view, String expected) throws CareerSidecarStore.Failure {
        if (!view.token().equals(expected)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Command journal changed; reread before deciding");
    }
    private static Snapshot snapshot(String token, Entries values) {
        return new Snapshot(token, values.values().stream().map(r -> r.state() == State.PREPARED
                ? new Receipt(r.operationId(), r.intent(), State.OUTCOME_UNKNOWN) : r).toList(), values.resolutions);
    }
    private static Entries decode(CareerSidecarStore.View view) throws IOException {
        var values = new Entries(); if (view.value().isEmpty()) return values;
        var value = view.value().orElseThrow();
        if (value.schema() != SCHEMA && value.schema() != 1) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CORRUPT, "Unknown journal schema; no migration performed");
        try (var input = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            int magic = input.readInt(), format = input.readInt();
            if (magic != MAGIC || format != value.schema()) throw new IOException("Unknown intent journal format/schema pair");
            int count = input.readInt(); if (count < 0 || count > MAX_INTENTS) throw new IOException("Invalid intent count");
            var operationIds = new HashSet<UUID>();
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(input.readLong(), input.readLong()); String name = input.readUTF();
                var operation = CareerCommandGate.Operation.valueOf(input.readUTF());
                Intent intent = new Intent(name, operation, HexFormat.of().formatHex(input.readNBytes(32)));
                var state = State.valueOf(input.readUTF());
                if (!operationIds.add(id) || values.putIfAbsent(name, new Receipt(id, intent, state)) != null) throw new IOException("Duplicate intent key/UUID");
            }
            if (format == 2) {
                int resolutions = input.readInt(); if (resolutions < 0 || resolutions > count) throw new IOException("Invalid resolution count");
                var ids = new HashSet<UUID>();
                for (int i = 0; i < resolutions; i++) {
                    UUID id = new UUID(input.readLong(), input.readLong()); String name = input.readUTF();
                    Intent intent = new Intent(name, CareerCommandGate.Operation.valueOf(input.readUTF()), input.readUTF());
                    State originalState = State.valueOf(input.readUTF()); String token = input.readUTF();
                    UUID context = new UUID(input.readLong(), input.readLong()); long generation = input.readLong();
                    var receipt = values.get(name);
                    if (!ids.add(id) || receipt == null || !receipt.operationId().equals(id) || !receipt.intent().equals(intent)
                            || originalState != State.PREPARED && originalState != State.OUTCOME_UNKNOWN
                            || receipt.state() != originalState && !(originalState == State.PREPARED && receipt.state() == State.OUTCOME_UNKNOWN)
                            || !token.matches("[a-f0-9]{64}") || generation < 1) throw new IOException("Invalid no-effect resolution audit");
                    values.resolutions.add(new NoEffectResolution(id, intent, originalState, token, context, generation));
                }
            }
            if (input.available() != 0) throw new IOException("Trailing intent bytes");
            return values;
        } catch (IOException | IllegalArgumentException invalid) { throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CORRUPT, "Invalid durable command journal", invalid); }
    }
    private static byte[] encode(Entries values) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC); output.writeInt(FORMAT); output.writeInt(values.size());
            for (var receipt : values.values()) {
                output.writeLong(receipt.operationId().getMostSignificantBits()); output.writeLong(receipt.operationId().getLeastSignificantBits());
                output.writeUTF(receipt.intent().key()); output.writeUTF(receipt.intent().operation().name());
                output.write(HexFormat.of().parseHex(receipt.intent().payloadDigest())); output.writeUTF(receipt.state().name());
            }
            output.writeInt(values.resolutions.size());
            for (var resolution : values.resolutions) {
                output.writeLong(resolution.operationId().getMostSignificantBits()); output.writeLong(resolution.operationId().getLeastSignificantBits());
                output.writeUTF(resolution.intent().key()); output.writeUTF(resolution.intent().operation().name()); output.writeUTF(resolution.intent().payloadDigest());
                output.writeUTF(resolution.originalState().name()); output.writeUTF(resolution.witnessToken());
                output.writeLong(resolution.context().getMostSignificantBits()); output.writeLong(resolution.context().getLeastSignificantBits()); output.writeLong(resolution.generation());
            }
        }
        return buffer.toByteArray();
    }
}
