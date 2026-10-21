package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Internal append-only contracts on a DEDICATED root. No effect receipts, automatic retry or migration. */
final class FinanceRequestStore {
    static final int MAX_REQUESTS = 32;
    private static final int MAGIC = 0x56424652, FORMAT = 1, SCHEMA = 1;
    record Snapshot(String token, List<FinanceRequest> requests) {
        Snapshot { requests = List.copyOf(requests); }
    }
    record Recovery(Snapshot snapshot, SaveRevision recordedSave, SaveRevision observedSave) {
        boolean revisionChanged() { return !recordedSave.equals(observedSave); }
    }
    private final CareerSidecarStore store;
    private final Key key;
    FinanceRequestStore(CareerSidecarStore store, Key key) { this.store = Objects.requireNonNull(store); this.key = Objects.requireNonNull(key); }
    boolean matchesKey(Key expected) { return key.equals(expected); }
    boolean matches(CareerCommandGate.Context context, String owner) {
        return context != null && key.career().equals(context.career()) && key.binding().equals(context.binding()) && key.mod().equals(owner);
    }
    Snapshot read(SaveRevision save) throws IOException {
        var view = store.read(key, save);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Existing uncommitted request namespace cannot be treated as empty");
        return new Snapshot(view.token(), decode(view, save));
    }
    /** Diagnostic anchor read only; no identity discovery, migration or dispatch permission. */
    Recovery inspectRecordedRecovery(SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave); var view = store.inspectFinanceRequests(key);
        if (view.value().isEmpty() && store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Existing uncommitted request namespace requires review");
        var recorded = view.value().map(Value::save).orElse(observedSave);
        return new Recovery(new Snapshot(view.token(), decode(view, recorded)), recorded, observedSave);
    }
    Snapshot persist(FinanceRequest request, String expectedToken) throws IOException {
        Objects.requireNonNull(request);
        if (!request.career().equals(key.career()) || !request.binding().equals(key.binding()) || !request.owner().equals(key.mod()))
            throw new Failure(Reason.CONFLICT, "Request store scope mismatch");
        var current = read(request.save());
        if (!current.token().equals(expectedToken)) throw new Failure(Reason.CONFLICT, "Request store changed; reread before deciding");
        for (var previous : current.requests()) {
            if (previous.key().equals(request.key())) {
                if (!previous.equals(request)) throw new Failure(Reason.CONFLICT, "Request key already has another immutable payload");
                return current; // Read-only confirmation, never authorizes dispatch or reissues a journal lease.
            }
        }
        if (current.requests().size() >= MAX_REQUESTS) throw new Failure(Reason.QUOTA, "Request capacity exhausted; no eviction");
        var values = new ArrayList<>(current.requests()); values.add(request);
        var committed = store.write(key, request.save(), SCHEMA, expectedToken, encode(values));
        return new Snapshot(committed.token(), values);
    }
    private List<FinanceRequest> decode(View view, SaveRevision save) throws IOException {
        if (view.value().isEmpty()) return List.of();
        var value = view.value().orElseThrow();
        if (value.schema() != SCHEMA) throw new Failure(Reason.CORRUPT, "Unknown request-store schema; no implicit migration");
        try (var in = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("Unknown request-store format");
            int count = in.readInt();
            if (count < 1 || count > MAX_REQUESTS) throw new IOException("Invalid request count");
            var requests = new ArrayList<FinanceRequest>(); var keys = new HashSet<String>();
            for (int i = 0; i < count; i++) {
                int length = in.readInt(); if (length < 1 || length > 4096) throw new IOException("Invalid request length");
                var request = FinanceRequest.decode(in.readNBytes(length));
                if (!request.career().equals(key.career()) || !request.binding().equals(key.binding())
                        || !request.owner().equals(key.mod()) || !request.save().equals(save) || !keys.add(request.key()))
                    throw new IOException("Request scope/anchor mismatch or duplicate key");
                requests.add(request);
            }
            if (in.available() != 0) throw new IOException("Trailing request-store data");
            return List.copyOf(requests);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid stored finance requests", invalid); }
    }
    private static byte[] encode(List<FinanceRequest> requests) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeInt(requests.size());
            for (var request : requests) { byte[] payload = request.encode(); out.writeInt(payload.length); out.write(payload); }
        }
        return bytes.toByteArray();
    }
}
