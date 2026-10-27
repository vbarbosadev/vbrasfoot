package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Dedicated bounded ledger. Its sentinel is NOT a save revision or proof of save inclusion.
 * Original contract bytes and source tokens are never reanchored, evicted or replayed. */
final class LoanContinuationStore {
    static final String OWNER = "br.vbrasfoot.loan-continuation";
    static final SaveRevision ANCHOR = new SaveRevision("0".repeat(64), 0);
    static final int LIMIT = 32;
    private static final int MAGIC = 0x56424c43, SCHEMA = 1;
    enum State { EXECUTING, APPLIED, NO_EFFECT, UNKNOWN }
    static final class Entry {
        private final byte[] raw;
        private final LoanRequest request;
        private final State state;
        private Entry(byte[] raw, State state) throws IOException {
            this.raw = raw.clone(); this.request = LoanRequest.decode(raw); this.state = Objects.requireNonNull(state);
        }
        LoanRequest request() { return request; }
        State state() { return state; }
        byte[] raw() { return raw.clone(); }
    }
    record Snapshot(String token, List<Entry> entries, Map<String, String> sources) {
        Snapshot { entries = List.copyOf(entries); sources = Map.copyOf(sources); }
        boolean uncertain() { return entries.stream().anyMatch(e -> e.state == State.UNKNOWN || e.state == State.EXECUTING); }
    }
    private final CareerSidecarStore store;
    private final Key key;
    LoanContinuationStore(CareerSidecarStore store, CareerSaveAssociations.Handle handle) {
        this.store = Objects.requireNonNull(store); key = new Key(handle.career(), handle.binding(), OWNER);
    }
    Snapshot read() throws IOException {
        var inventory = store.inventoryBinding(key.career(), key.binding());
        if (inventory.tokens().keySet().stream().anyMatch(owner -> !owner.equals(OWNER)))
            throw failure(Reason.RECONCILIATION_REQUIRED, "Unrelated continuation namespace blocks commands");
        var view = store.read(key, ANCHOR);
        if (view.value().isEmpty()) {
            if (!inventory.tokens().isEmpty()) throw failure(Reason.RECONCILIATION_REQUIRED, "Incomplete continuation ledger");
            return new Snapshot(EMPTY, List.of(), Map.of());
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(view.value().orElseThrow().payload()))) {
            if (view.value().orElseThrow().schema() != SCHEMA || in.readInt() != MAGIC || in.readInt() != SCHEMA)
                throw new IOException("Unknown continuation schema");
            int count = in.readInt(); if (count < 0 || count > 256) throw new IOException("Source quota");
            var sources = new TreeMap<String, String>();
            for (int i = 0; i < count; i++) {
                String source = in.readUTF(), token = in.readUTF(); validateSource(source, token);
                if (sources.put(source, token) != null) throw new IOException("Duplicate source");
            }
            count = in.readInt(); if (count < 1 || count > LIMIT) throw new IOException("Request quota");
            var entries = new ArrayList<Entry>(); var keys = new HashSet<String>();
            for (int i = 0; i < count; i++) {
                int length = in.readInt(); if (length < 1 || length > LoanRequest.MAX_BYTES) throw new IOException("Request size");
                var entry = new Entry(in.readNBytes(length), State.valueOf(in.readUTF())); validateScope(entry.request);
                if (!keys.add(entry.request.key())) throw new IOException("Duplicate request key");
                for (var previous : entries) if (samePlayer(previous.request, entry.request)) throw new IOException("Duplicate player tombstone");
                entries.add(entry);
            }
            if (in.available() != 0) throw new IOException("Trailing continuation bytes");
            return new Snapshot(view.token(), entries, sources);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new Failure(Reason.CORRUPT, "Invalid continuation ledger", invalid);
        }
    }
    /** This comparison never forgets a previously reviewed source, including deleted namespaces. */
    static void validateSources(Snapshot before, Map<String, String> reviewed) throws Failure {
        if (reviewed.size() > 256) throw failure(Reason.QUOTA, "Source quota exhausted");
        for (var source : reviewed.entrySet()) validateSource(source.getKey(), source.getValue());
        for (var source : before.sources.entrySet())
            if (!source.getValue().equals(reviewed.get(source.getKey())))
                throw failure(Reason.CONFLICT, "Historical source disappeared or changed: " + source.getKey());
    }
    Snapshot claim(LoanRequest request, String token, Map<String, String> explicitlyReviewedSources) throws IOException {
        validateScope(request); var before = current(token); validateSources(before, explicitlyReviewedSources);
        if (before.uncertain()) throw failure(Reason.RECONCILIATION_REQUIRED, "Uncertain continuation blocks every new loan");
        if (before.entries.size() >= LIMIT) throw failure(Reason.QUOTA, "Continuation quota exhausted; tombstones are never evicted");
        for (var entry : before.entries) if (entry.request.key().equals(request.key()) || samePlayer(entry.request, request))
            throw failure(Reason.CONFLICT, "Request key or historical player already recorded; never replay");
        var entries = new ArrayList<>(before.entries); entries.add(new Entry(request.encode(), State.EXECUTING));
        return commit(before.token, entries, explicitlyReviewedSources);
    }
    Snapshot complete(LoanRequest request, String token, State terminal) throws IOException {
        if (terminal == State.EXECUTING) throw new IllegalArgumentException("Terminal state required");
        var before = current(token); var entries = new ArrayList<Entry>(); boolean found = false;
        for (var entry : before.entries) {
            if (entry.request.key().equals(request.key())) {
                if (!Arrays.equals(entry.raw, request.encode()) || entry.state != State.EXECUTING)
                    throw failure(Reason.CONFLICT, "Continuation execution changed");
                entry = new Entry(entry.raw, terminal); found = true;
            }
            entries.add(entry);
        }
        if (!found) throw failure(Reason.NOT_FOUND, "Execution not recorded");
        return commit(before.token, entries, before.sources);
    }
    static boolean samePlayer(LoanRequest left, LoanRequest right) {
        return left.origin().id() == right.origin().id() && left.player().id() == right.player().id();
    }
    private Snapshot current(String token) throws IOException {
        var before = read(); if (!before.token.equals(token)) throw failure(Reason.CONFLICT, "Continuation ledger changed"); return before;
    }
    private void validateScope(LoanRequest request) throws Failure {
        if (!request.career().equals(key.career()) || !request.binding().equals(key.binding())
                || !request.owner().equals(LoanManagerPanel.OWNER) || request.assessment().decision() != LoanClubAssessment.Decision.APPROVE)
            throw failure(Reason.CONFLICT, "Invalid continuation scope/policy");
    }
    private Snapshot commit(String token, List<Entry> entries, Map<String, String> sources) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(SCHEMA); out.writeInt(sources.size());
            for (var source : new TreeMap<>(sources).entrySet()) { out.writeUTF(source.getKey()); out.writeUTF(source.getValue()); }
            out.writeInt(entries.size());
            for (var entry : entries) { out.writeInt(entry.raw.length); out.write(entry.raw); out.writeUTF(entry.state.name()); }
        }
        if (bytes.size() > MAX_PAYLOAD) throw failure(Reason.QUOTA, "Continuation byte quota exhausted");
        var view = store.write(key, ANCHOR, SCHEMA, token, bytes.toByteArray());
        return new Snapshot(view.token(), entries, sources);
    }
    private static void validateSource(String source, String token) throws Failure {
        if (source == null || !source.matches("(?:baseline|(journal|finance|execution|loan)/[a-z0-9][a-z0-9._-]{0,99}|alias/[a-f0-9-]{36}/[a-f0-9-]{36}/(?:(journal|finance|execution|loan)/[a-z0-9][a-z0-9._-]{0,99}|association|continuation))")
                || token == null || !token.matches("[a-f0-9]{64}")) throw failure(Reason.CORRUPT, "Invalid source reference");
    }
    private static Failure failure(Reason reason, String message) { return new Failure(reason, message); }
}
