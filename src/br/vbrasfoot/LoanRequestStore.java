package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Immutable contracts and versioned decision state on a dedicated loan root. No automatic replay/migration. */
final class LoanRequestStore {
    enum State { PENDING, APPROVED, REFUSED, INSUFFICIENT_DATA, CANCELLED, EXECUTING, APPLIED, NO_EFFECT, UNKNOWN }
    record Snapshot(String token, List<LoanRequest> requests, Map<String, State> states) {
        Snapshot(String token, List<LoanRequest> requests) { this(token, requests, pending(requests)); }
        Snapshot {
            requests = List.copyOf(requests); states = Map.copyOf(states);
            if (!states.keySet().equals(new HashSet<>(requests.stream().map(LoanRequest::key).toList()))) throw new IllegalArgumentException("Decision keys mismatch");
        }
        private static Map<String, State> pending(List<LoanRequest> requests) {
            var states = new HashMap<String, State>(); for (var request : requests) states.put(request.key(), State.PENDING); return states;
        }
        State state(String requestKey) { return Objects.requireNonNull(states.get(requestKey), "Unknown loan key"); }
    }
    static final class Lease {
        private final UUID store;
        private final String key, digest;
        private boolean consumed;
        private Lease(UUID store, LoanRequest request) { this.store = store; key = request.key(); digest = request.digest(); }
    }
    record Claim(Snapshot snapshot, Optional<Lease> lease) { }
    record Recovery(Snapshot snapshot, SaveRevision recordedSave, SaveRevision observedSave) {
        boolean revisionChanged() { return !recordedSave.equals(observedSave); }
        /** Recovery is a read-only projection; immutable historical decisions are never rewritten as fresh approvals. */
        String reviewedState(String key) {
            State state = snapshot.state(key);
            if (state == State.EXECUTING) return "OUTCOME_UNKNOWN";
            if (revisionChanged() && (state == State.PENDING || state == State.APPROVED)) return "STALE_REVIEW_REQUIRED";
            return state.name();
        }
        boolean executable(String key) { return !revisionChanged() && snapshot.state(key) == State.APPROVED; }
    }
    private static final int MAGIC = 0x56424c52, FORMAT = 2, SCHEMA = 2;
    private final UUID instance = UUID.randomUUID();
    private final CareerSidecarStore store;
    private final Key key;
    LoanRequestStore(CareerSidecarStore store, Key key) { this.store = Objects.requireNonNull(store); this.key = Objects.requireNonNull(key); }
    boolean matches(CareerCommandGate.Context context, String owner) {
        return context != null && key.career().equals(context.career()) && key.binding().equals(context.binding()) && key.mod().equals(owner);
    }
    Snapshot read(SaveRevision save) throws IOException {
        return decode(store.read(key, save), save);
    }
    Recovery inspectRecordedRecovery(SaveRevision observedSave) throws IOException {
        Objects.requireNonNull(observedSave);
        var view = store.inspectLoanRequests(key);
        var recordedSave = view.value().map(Value::save).orElse(observedSave);
        return new Recovery(decode(view, recordedSave), recordedSave, observedSave);
    }
    private Snapshot decode(View view, SaveRevision save) throws IOException {
        if (view.value().isEmpty()) {
            if (store.inventoryBinding(key.career(), key.binding()).tokens().containsKey(key.mod()))
                throw new Failure(Reason.RECONCILIATION_REQUIRED, "Uncommitted loan namespace requires review");
            return new Snapshot(view.token(), List.of());
        }
        var value = view.value().orElseThrow();
        if (value.schema() != SCHEMA && value.schema() != 1) throw new Failure(Reason.CORRUPT, "Unknown loan schema");
        try (var in = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            int magic = in.readInt(), format = in.readInt();
            if (magic != MAGIC || format != value.schema()) throw new IOException("Unknown loan store format/schema");
            int count = in.readInt(); if (count < 1 || count > 32) throw new IOException("Invalid loan count");
            var requests = new ArrayList<LoanRequest>(); var keys = new HashSet<String>();
            for (int i = 0; i < count; i++) {
                int length = in.readInt(); if (length < 1 || length > LoanRequest.MAX_BYTES) throw new IOException("Invalid payload length");
                var request = LoanRequest.decode(in.readNBytes(length));
                if (!request.career().equals(key.career()) || !request.binding().equals(key.binding()) || !request.owner().equals(key.mod())
                        || !request.save().equals(save) || !keys.add(request.key())) throw new IOException("Loan scope/revision/key mismatch");
                requests.add(request);
            }
            var states = new LinkedHashMap<String, State>();
            for (var request : requests) {
                State state = format == 1 ? State.PENDING : State.valueOf(in.readUTF());
                validateDecision(request, state); states.put(request.key(), state);
            }
            if (in.available() != 0) throw new IOException("Trailing loan store bytes");
            return new Snapshot(view.token(), requests, states);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid stored loans", invalid); }
    }
    Snapshot persist(LoanRequest request, String token) throws IOException {
        if (!request.career().equals(key.career()) || !request.binding().equals(key.binding()) || !request.owner().equals(key.mod()))
            throw new Failure(Reason.CONFLICT, "Loan namespace mismatch");
        var before = read(request.save());
        if (!before.token().equals(token)) throw new Failure(Reason.CONFLICT, "Loan store changed");
        for (var previous : before.requests()) if (previous.key().equals(request.key())) {
            if (!previous.equals(request)) throw new Failure(Reason.CONFLICT, "Immutable loan key conflict");
            return before;
        }
        if (before.requests().size() >= 32) throw new Failure(Reason.QUOTA, "Loan store capacity exhausted");
        var values = new ArrayList<>(before.requests()); values.add(request);
        var states = new HashMap<>(before.states()); states.put(request.key(), State.PENDING);
        return commit(request.save(), token, values, states);
    }
    Snapshot evaluate(SaveRevision save, String requestKey, String token) throws IOException {
        var before = current(save, token); var request = find(before, requestKey);
        if (before.state(requestKey) != State.PENDING) return before;
        State next = switch (request.assessment().decision()) {
            case APPROVE -> State.APPROVED;
            case REFUSE -> State.REFUSED;
            case INSUFFICIENT_DATA -> State.INSUFFICIENT_DATA;
        };
        return transition(save, before, requestKey, next);
    }
    Snapshot cancel(SaveRevision save, String requestKey, String token) throws IOException {
        var before = current(save, token); find(before, requestKey);
        if (before.state(requestKey) != State.PENDING && before.state(requestKey) != State.APPROVED)
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Cannot cancel an executing or terminal loan");
        return transition(save, before, requestKey, State.CANCELLED);
    }
    /** Persisted execution intent; never reissues a lease for EXECUTING or historical state after restart. */
    Claim claim(SaveRevision save, String requestKey, String token) throws IOException {
        var before = current(save, token); var request = find(before, requestKey);
        if (before.state(requestKey) != State.APPROVED) return new Claim(before, Optional.empty());
        if (request.assessment().decision() != LoanClubAssessment.Decision.APPROVE) throw new Failure(Reason.CORRUPT, "Invalid approved policy");
        var committed = transition(save, before, requestKey, State.EXECUTING);
        return new Claim(committed, Optional.of(new Lease(instance, request)));
    }
    synchronized Snapshot complete(SaveRevision save, Lease lease, State outcome, String token) throws IOException {
        if (lease == null || !instance.equals(lease.store) || lease.consumed) throw new IllegalArgumentException("Foreign or consumed loan lease");
        if (outcome != State.APPLIED && outcome != State.NO_EFFECT && outcome != State.UNKNOWN) throw new IllegalArgumentException("Invalid execution outcome");
        lease.consumed = true; // Failure is uncertain; no second completion attempt assumed safe.
        var before = current(save, token); var request = find(before, lease.key);
        if (!lease.digest.equals(request.digest()) || before.state(lease.key) != State.EXECUTING)
            throw new Failure(Reason.CONFLICT, "Execution contract/state changed");
        return transition(save, before, lease.key, outcome);
    }
    private Snapshot current(SaveRevision save, String token) throws IOException {
        var snapshot = read(save);
        if (!snapshot.token().equals(token)) throw new Failure(Reason.CONFLICT, "Loan decisions changed");
        return snapshot;
    }
    private static LoanRequest find(Snapshot snapshot, String key) throws Failure {
        return snapshot.requests().stream().filter(request -> request.key().equals(key)).findFirst()
                .orElseThrow(() -> new Failure(Reason.NOT_FOUND, "Unknown loan request"));
    }
    private Snapshot transition(SaveRevision save, Snapshot before, String requestKey, State next) throws IOException {
        var states = new HashMap<>(before.states()); states.put(requestKey, next);
        return commit(save, before.token(), before.requests(), states);
    }
    private Snapshot commit(SaveRevision save, String token, List<LoanRequest> values, Map<String, State> states) throws IOException {
        for (var request : values) validateDecision(request, states.get(request.key()));
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeInt(values.size());
            for (var item : values) { byte[] payload = item.encode(); out.writeInt(payload.length); out.write(payload); }
            for (var item : values) out.writeUTF(states.get(item.key()).name());
        }
        if (bytes.size() > MAX_PAYLOAD) throw new Failure(Reason.QUOTA, "Loan envelope exceeds quota");
        var committed = store.write(key, save, SCHEMA, token, bytes.toByteArray());
        return new Snapshot(committed.token(), values, states);
    }
    private static void validateDecision(LoanRequest request, State state) {
        Objects.requireNonNull(state);
        var decision = request.assessment().decision();
        boolean valid = switch (state) {
            case PENDING, CANCELLED -> true;
            case REFUSED -> decision == LoanClubAssessment.Decision.REFUSE;
            case INSUFFICIENT_DATA -> decision == LoanClubAssessment.Decision.INSUFFICIENT_DATA;
            default -> decision == LoanClubAssessment.Decision.APPROVE;
        };
        if (!valid) throw new IllegalArgumentException("Stored decision contradicts immutable policy inputs");
    }
}
