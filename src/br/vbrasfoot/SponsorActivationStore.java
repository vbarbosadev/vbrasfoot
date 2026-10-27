package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Internal at-most-once intent ledger on a dedicated root. Never proves save inclusion or grants write authority. */
final class SponsorActivationStore {
    static final String OWNER = "br.vbrasfoot.sponsor-manager";
    static final SaveRevision ANCHOR = new SaveRevision("0".repeat(64), 0);
    static final int LIMIT = 32;
    private static final int MAGIC = 0x56425341, SCHEMA = 3, MAX_REQUEST = 4096;

    enum State { CLAIMED, MEMORY_APPLIED, NO_EFFECT, UNKNOWN }
    enum EndState { CLAIMED, ENDED, NO_EFFECT, UNKNOWN }
    record EndAttempt(UUID attempt, SaveRevision save, String stadiumCurrent, FinanceDelta.Snapshot finances, EndState state) {
        EndAttempt {
            Objects.requireNonNull(attempt); Objects.requireNonNull(save); Objects.requireNonNull(finances); Objects.requireNonNull(state);
            if (save.equals(ANCHOR) || stadiumCurrent == null || stadiumCurrent.isBlank() || stadiumCurrent.length() > 120)
                throw new IllegalArgumentException("Exact ending observation required");
        }
        EndAttempt terminal(EndState terminal) { return new EndAttempt(attempt, save, stadiumCurrent, finances, terminal); }
    }
    record Entry(UUID sponsor, UUID attempt, String termsDigest, FinanceRequest request, State state, String stadiumBefore, String stadiumAfter,
                 Optional<EndAttempt> ending) {
        Entry(UUID sponsor, UUID attempt, String termsDigest, FinanceRequest request, State state, String before, String after) {
            this(sponsor, attempt, termsDigest, request, state, before, after, Optional.empty());
        }
        Entry(UUID sponsor, UUID attempt, String termsDigest, FinanceRequest request, State state) {
            this(sponsor, attempt, termsDigest, request, state, "", "");
        }
        Entry {
            Objects.requireNonNull(sponsor); Objects.requireNonNull(attempt); Objects.requireNonNull(request); Objects.requireNonNull(state);
            if (termsDigest == null || !termsDigest.matches("[a-f0-9]{64}") || !request.owner().equals(OWNER)
                    || request.direction() != FinanceDelta.Direction.CREDIT_RAW_6 || !request.key().equals(key(sponsor)))
                throw new IllegalArgumentException("Exact sponsor credit payload required");
            if (stadiumBefore == null || stadiumAfter == null || stadiumBefore.length() > 120 || stadiumAfter.length() > 120)
                throw new IllegalArgumentException("Bounded stadium audit required");
            Objects.requireNonNull(ending);
            if (ending.isPresent() && (state != State.MEMORY_APPLIED || stadiumBefore.isBlank() || stadiumAfter.isBlank()))
                throw new IllegalArgumentException("Ending requires applied activation with original stadium audit");
        }
        boolean blocksNewActivation() { return state != State.NO_EFFECT; }
        /** No stored state here is proof of an active, saved financial contract. */
        boolean savedActivationProven() { return false; }
    }
    record Snapshot(String token, List<Entry> entries) {
        Snapshot { entries = List.copyOf(entries); }
        boolean blocksNewActivation() { return entries.stream().anyMatch(Entry::blocksNewActivation); }
        boolean permitsNextSponsor() { return entries.stream().allMatch(e -> e.state() == State.NO_EFFECT
                || e.state() == State.MEMORY_APPLIED && e.ending().filter(end -> end.state() == EndState.ENDED).isPresent()); }
    }
    /** Only the instance which committed this claim may complete it. Reopening never recreates a lease. */
    static final class Lease {
        private final SponsorActivationStore issuer;
        private final Entry entry;
        private boolean used;
        private Lease(SponsorActivationStore issuer, Entry entry) { this.issuer = issuer; this.entry = entry; }
    }
    record Claimed(Snapshot snapshot, Lease lease) { }
    static final class EndLease {
        private final SponsorActivationStore issuer; private final Entry entry; private boolean used;
        private EndLease(SponsorActivationStore issuer, Entry entry) { this.issuer = issuer; this.entry = entry; }
    }
    record EndClaimed(Snapshot snapshot, EndLease lease) { }

    private final CareerSidecarStore disk;
    private final Key scope;
    SponsorActivationStore(CareerSidecarStore disk, CareerSaveAssociations.Handle handle) {
        this.disk = Objects.requireNonNull(disk);
        scope = new Key(handle.career(), handle.binding(), OWNER);
    }
    static String key(UUID sponsor) { return "sponsor_" + Objects.requireNonNull(sponsor).toString().replace("-", ""); }

    Snapshot read() throws IOException {
        var inventory = disk.inventoryBinding(scope.career(), scope.binding());
        if (inventory.tokens().keySet().stream().anyMatch(owner -> !owner.equals(OWNER)))
            throw failure(Reason.RECONCILIATION_REQUIRED, "Unrelated sponsor activation namespace; preserve history");
        var view = disk.read(scope, ANCHOR);
        if (view.value().isEmpty()) {
            if (!inventory.tokens().isEmpty()) throw failure(Reason.RECONCILIATION_REQUIRED, "Incomplete sponsor activation ledger");
            return new Snapshot(EMPTY, List.of());
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(view.value().orElseThrow().payload()))) {
            int schema = view.value().orElseThrow().schema();
            if ((schema < 1 || schema > SCHEMA) || in.readInt() != MAGIC || in.readInt() != schema)
                throw new IOException("Unknown sponsor activation schema");
            int count = in.readInt();
            if (count < 1 || count > LIMIT) throw new IOException("Sponsor activation quota");
            var entries = new ArrayList<Entry>(); var sponsors = new HashSet<UUID>(); var attempts = new HashSet<UUID>();
            for (int i = 0; i < count; i++) {
                UUID sponsor = uuid(in), attempt = uuid(in); String digest = in.readUTF();
                int length = in.readInt();
                if (length < 1 || length > MAX_REQUEST || length > in.available()) throw new IOException("Invalid finance request length");
                var request = FinanceRequest.decode(in.readNBytes(length));
                State state = State.valueOf(in.readUTF());
                String before = schema == 1 ? "" : in.readUTF(), after = schema == 1 ? "" : in.readUTF();
                Optional<EndAttempt> ending = schema >= 3 && in.readBoolean() ? Optional.of(readEnd(in)) : Optional.empty();
                var entry = new Entry(sponsor, attempt, digest, request, state, before, after, ending);
                validateScope(request);
                if (!sponsors.add(sponsor) || !attempts.add(attempt)) throw new IOException("Duplicate sponsor/attempt tombstone");
                entries.add(entry);
            }
            if (in.available() != 0) throw new IOException("Trailing sponsor activation data");
            return new Snapshot(view.token(), entries);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new Failure(Reason.CORRUPT, "Invalid sponsor activation history; no automatic recovery/replay", invalid);
        }
    }

    /** Must run only after independent career/contract/finance qualification. This metadata commit is not authorization. */
    Claimed claim(UUID sponsor, String termsDigest, FinanceRequest request, String expectedToken) throws IOException {
        return claim(sponsor, termsDigest, request, expectedToken, "", "");
    }
    Claimed claim(UUID sponsor, String termsDigest, FinanceRequest request, String expectedToken, String stadiumBefore, String stadiumAfter) throws IOException {
        validateScope(request);
        var entry = new Entry(sponsor, UUID.randomUUID(), termsDigest, request, State.CLAIMED, stadiumBefore, stadiumAfter);
        var before = current(expectedToken);
        if (before.entries().stream().anyMatch(old -> old.sponsor().equals(sponsor)))
            throw failure(Reason.CONFLICT, "Sponsor activation already attempted; never replay, including NO_EFFECT history");
        if (!before.permitsNextSponsor())
            throw failure(Reason.RECONCILIATION_REQUIRED, "Previous activation applied/uncertain; saved inclusion remains unproven");
        if (before.entries().size() >= LIMIT) throw failure(Reason.QUOTA, "Sponsor activation history full; no eviction");
        var entries = new ArrayList<>(before.entries()); entries.add(entry);
        var committed = commit(before.token(), entries);
        return new Claimed(committed, new Lease(this, entry));
    }

    Snapshot complete(Lease lease, String expectedToken, State terminal) throws IOException {
        if (lease == null || lease.issuer != this) throw new IllegalArgumentException("Original sponsor claim lease required");
        if (terminal == null || terminal == State.CLAIMED) throw new IllegalArgumentException("Terminal observation required");
        synchronized (lease) {
            if (lease.used) throw failure(Reason.CONFLICT, "Sponsor completion lease consumed; review without retrying");
            lease.used = true; // Even a failed commit cannot be retried with an uncertain result.
        }
        var before = current(expectedToken); var entries = new ArrayList<Entry>(); boolean found = false;
        for (var entry : before.entries()) {
            if (entry.attempt().equals(lease.entry.attempt())) {
                if (!entry.equals(lease.entry)) throw failure(Reason.CONFLICT, "Sponsor execution payload/state changed");
                entry = new Entry(entry.sponsor(), entry.attempt(), entry.termsDigest(), entry.request(), terminal, entry.stadiumBefore(), entry.stadiumAfter(), entry.ending()); found = true;
            }
            entries.add(entry);
        }
        if (!found) throw failure(Reason.NOT_FOUND, "Sponsor claim missing; never create a retrospective receipt");
        return commit(before.token(), entries);
    }
    EndClaimed claimEnd(UUID sponsor, EndAttempt proposed, String token) throws IOException {
        if (proposed.state() != EndState.CLAIMED) throw new IllegalArgumentException("New ending claim required");
        var before = current(token); var entries = new ArrayList<Entry>(); Entry selected = null;
        for (var entry : before.entries()) {
            if (entry.sponsor().equals(sponsor)) {
                if (entry.ending().isPresent()) throw failure(Reason.CONFLICT, "Encerramento ja tentado; nao repetir automaticamente");
                if (entry.state() != State.MEMORY_APPLIED || entry.stadiumBefore().isBlank() || entry.stadiumAfter().isBlank())
                    throw failure(Reason.RECONCILIATION_REQUIRED, "Ativacao aplicada e nome original registrado obrigatorios");
                selected = new Entry(entry.sponsor(), entry.attempt(), entry.termsDigest(), entry.request(), entry.state(),
                        entry.stadiumBefore(), entry.stadiumAfter(), Optional.of(proposed));
                entry = selected;
            }
            entries.add(entry);
        }
        if (selected == null) throw failure(Reason.NOT_FOUND, "Ativacao nao encontrada para encerrar");
        var committed = commit(token, entries); return new EndClaimed(committed, new EndLease(this, selected));
    }
    Snapshot completeEnd(EndLease lease, String token, EndState terminal) throws IOException {
        if (lease == null || lease.issuer != this || terminal == null || terminal == EndState.CLAIMED)
            throw new IllegalArgumentException("Original ending lease and terminal observation required");
        synchronized (lease) {
            if (lease.used) throw failure(Reason.CONFLICT, "Ending completion consumed; no replay");
            lease.used = true;
        }
        var before = current(token); var entries = new ArrayList<Entry>(); boolean found = false;
        for (var entry : before.entries()) {
            if (entry.sponsor().equals(lease.entry.sponsor())) {
                if (!entry.equals(lease.entry)) throw failure(Reason.CONFLICT, "Ending claim changed");
                entry = new Entry(entry.sponsor(), entry.attempt(), entry.termsDigest(), entry.request(), entry.state(),
                        entry.stadiumBefore(), entry.stadiumAfter(), Optional.of(entry.ending().orElseThrow().terminal(terminal)));
                found = true;
            }
            entries.add(entry);
        }
        if (!found) throw failure(Reason.NOT_FOUND, "Ending claim missing; no retrospective completion");
        return commit(token, entries);
    }
    private Snapshot current(String expectedToken) throws IOException {
        var value = read();
        if (!value.token().equals(expectedToken)) throw failure(Reason.CONFLICT, "Sponsor ledger changed; explicit rereview required");
        return value;
    }
    private void validateScope(FinanceRequest request) throws Failure {
        if (!request.career().equals(scope.career()) || !request.binding().equals(scope.binding()) || !request.owner().equals(OWNER)
                || request.direction() != FinanceDelta.Direction.CREDIT_RAW_6 || request.save().equals(ANCHOR))
            throw failure(Reason.CONFLICT, "Sponsor credit must retain exact career/binding/original save revision");
    }
    private Snapshot commit(String token, List<Entry> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(SCHEMA); out.writeInt(entries.size());
            for (var entry : entries) {
                uuid(out, entry.sponsor()); uuid(out, entry.attempt()); out.writeUTF(entry.termsDigest());
                byte[] raw = entry.request().encode(); out.writeInt(raw.length); out.write(raw); out.writeUTF(entry.state().name());
                out.writeUTF(entry.stadiumBefore()); out.writeUTF(entry.stadiumAfter());
                out.writeBoolean(entry.ending().isPresent());
                if (entry.ending().isPresent()) writeEnd(out, entry.ending().orElseThrow());
            }
        }
        if (bytes.size() > MAX_PAYLOAD) throw failure(Reason.QUOTA, "Sponsor activation byte quota exceeded");
        var result = disk.write(scope, ANCHOR, SCHEMA, token, bytes.toByteArray());
        return new Snapshot(result.token(), entries);
    }
    private static void writeEnd(DataOutputStream out, EndAttempt ending) throws IOException {
        uuid(out, ending.attempt()); out.writeUTF(ending.save().sha256()); out.writeLong(ending.save().size());
        out.writeUTF(ending.stadiumCurrent()); out.writeLong(ending.finances().balance());
        for (String field : new TreeSet<>(FinanceDelta.FIELDS)) out.writeLong(ending.finances().ledger().get(field));
        out.writeUTF(ending.state().name());
    }
    private static EndAttempt readEnd(DataInputStream in) throws IOException {
        UUID attempt = uuid(in); var save = new SaveRevision(in.readUTF(), in.readLong());
        String stadium = in.readUTF(); long balance = in.readLong(); var ledger = new TreeMap<String,Long>();
        for (String field : new TreeSet<>(FinanceDelta.FIELDS)) ledger.put(field, in.readLong());
        return new EndAttempt(attempt, save, stadium, new FinanceDelta.Snapshot(balance, ledger), EndState.valueOf(in.readUTF()));
    }
    static CareerCommandJournal.Intent endIntent(Entry entry, EndAttempt ending) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                uuid(out, entry.sponsor()); uuid(out, entry.attempt()); out.writeUTF(entry.request().digest());
                out.writeUTF(entry.stadiumBefore()); out.writeUTF(entry.stadiumAfter());
                writeEnd(out, ending.terminal(EndState.CLAIMED));
            }
            String digest = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            return new CareerCommandJournal.Intent("sponsor_end_" + entry.sponsor().toString().replace("-", ""),
                    CareerCommandGate.Operation.SPONSOR_END, digest);
        } catch (IOException | java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static Failure failure(Reason reason, String message) { return new Failure(reason, message); }
}
