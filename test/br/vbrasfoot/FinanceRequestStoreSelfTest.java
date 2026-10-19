package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned payload/journal failures only, never a power-loss or original save-inclusion claim. */
public final class FinanceRequestStoreSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + ", got " + expected.reason()); return; }
        throw new AssertionError("Expected refusal " + reason);
    }
    private static FinanceRequest request(Key key, SaveRevision save, String operation, int amount) {
        var ledger = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) ledger.put(field, 0L);
        return new FinanceRequest(key.career(), key.binding(), save, key.mod(), operation,
                new FinanceRequest.Club(0, 4, "Owned club"), FinanceDelta.Direction.CREDIT_RAW_6, amount, new FinanceDelta.Snapshot(1000, ledger));
    }
    private static final class Handler implements CareerCommandDispatcher.Handler {
        final FinanceRequest request; final AtomicInteger effects; final List<String> events;
        Handler(FinanceRequest request, AtomicInteger effects, List<String> events) { this.request = request; this.effects = effects; this.events = events; }
        public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
        public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return request.matches(context, owner); }
        public Optional<FinanceRequest> persistentRequest() { return Optional.of(request); }
        public CareerCommandGate.Status apply() {
            if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("Effect requires EDT");
            effects.incrementAndGet(); events.add("effect"); return CareerCommandGate.Status.APPLIED;
        }
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "finance-request-store-");
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.requests");
        var save = new SaveRevision("a".repeat(64), 12); var otherSave = new SaveRevision("b".repeat(64), 13);
        var store = new FinanceRequestStore(new CareerSidecarStore(root.resolve("contracts")), key);
        var r = request(key, save, "first", 10);
        check(store.read(save).requests().isEmpty() && !Files.exists(root.resolve("contracts")), "empty read creates no namespace");
        var first = store.persist(r, EMPTY);
        check(first.requests().equals(List.of(r)) && store.read(save).equals(first), "whole canonical contract persists with checksummed CAS");
        check(store.persist(r, first.token()).equals(first), "same key/payload is read-only confirmation");
        rejects(Reason.CONFLICT, () -> store.persist(r, EMPTY));
        rejects(Reason.CONFLICT, () -> store.persist(request(key, save, "first", 11), first.token()));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> store.read(otherSave));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> store.persist(request(key, otherSave, "new", 10), first.token()));
        rejects(Reason.CONFLICT, () -> store.persist(request(new Key(key.career(), key.binding(), "br.other.owner"), save, "other", 10), first.token()));
        var reopened = new FinanceRequestStore(new CareerSidecarStore(root.resolve("contracts")), key);
        check(reopened.read(save).equals(first), "reopened store reconstructs exact immutable requests");
        var second = store.persist(request(key, save, "second", 12), first.token());
        rejects(Reason.CONFLICT, () -> reopened.persist(request(key, save, "stale-writer", 13), first.token()));
        check(reopened.read(save).equals(second), "stale store writer cannot lose another committed payload");
        var current = second;
        for (int i = 2; i < FinanceRequestStore.MAX_REQUESTS; i++) current = store.persist(request(key, save, "key_" + i, i), current.token());
        String full = current.token();
        rejects(Reason.QUOTA, () -> store.persist(request(key, save, "overflow", 1), full));
        check(store.read(save).requests().size() == FinanceRequestStore.MAX_REQUESTS && store.persist(r, full).token().equals(full), "quota never evicts old keys, existing payload still confirms");
        var onEdt = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { store.persist(r, full); } catch (Throwable expected) { onEdt.set(expected); } });
        check(onEdt.get() instanceof Failure failure && failure.reason() == Reason.WRONG_THREAD, "request persistence refuses EDT");

        // Bad committed payloads must fail closed, not become empty valid request sets.
        for (int mode = 0; mode < 5; mode++) {
            Path bad = root.resolve("invalid-" + mode); var raw = new CareerSidecarStore(bad);
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeInt(0x56424652); out.writeInt(1); out.writeInt(mode == 0 ? 0 : mode == 1 ? 33 : mode == 4 ? 2 : 1);
            byte[] payload = mode == 2 ? request(new Key(UUID.randomUUID(), key.binding(), key.mod()), save, "wrong", 1).encode() : r.encode();
            out.writeInt(payload.length); out.write(payload);
            if (mode == 4) { out.writeInt(payload.length); out.write(payload); }
            if (mode == 3) out.writeByte(1); out.flush();
            raw.write(key, save, 1, EMPTY, bytes.toByteArray());
            rejects(Reason.CORRUPT, () -> new FinanceRequestStore(raw, key).read(save));
        }
        var schema = new CareerSidecarStore(root.resolve("schema")); schema.write(key, save, 2, EMPTY, new byte[0]);
        rejects(Reason.CORRUPT, () -> new FinanceRequestStore(schema, key).read(save));

        for (Stage stage : Stage.values()) {
            Path fault = root.resolve("payload-fault-" + stage);
            var journal = new CareerCommandJournal(new CareerSidecarStore(fault.resolve("journals")), key);
            var requests = new FinanceRequestStore(new CareerSidecarStore(fault.resolve("requests"), at -> { if (at == stage) throw new IOException("Owned payload interruption"); }), key);
            var effects = new AtomicInteger();
            try (var gate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object();
                var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
                var dispatcher = new CareerCommandDispatcher(gate, journal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
                boolean interrupted = false;
                try { dispatcher.executeStoredFinance(owner, context, r, EMPTY, requests, EMPTY, new Handler(r, effects, new ArrayList<>())); }
                catch (IOException expected) { interrupted = true; }
                check(interrupted && effects.get() == 0 && journal.read(save).receipts().isEmpty(), "payload interruption never journals or effects " + stage);
                var inspect = new FinanceRequestStore(new CareerSidecarStore(fault.resolve("requests")), key);
                if (stage == Stage.HEAD_COMMITTED) check(inspect.read(save).requests().equals(List.of(r)), "committed-but-unacknowledged payload remains inspectable, not executable");
                else rejects(Reason.RECONCILIATION_REQUIRED, () -> inspect.read(save));
            }
        }
        // Exact ordering and historical replay protection survive reopens.
        Path ordered = root.resolve("ordered"); var events = Collections.synchronizedList(new ArrayList<String>()); var effects = new AtomicInteger();
        var requests = new FinanceRequestStore(new CareerSidecarStore(ordered.resolve("requests"), at -> { if (at == Stage.HEAD_COMMITTED) events.add("payload"); }), key);
        var journal = new CareerCommandJournal(new CareerSidecarStore(ordered.resolve("journals"), at -> { if (at == Stage.HEAD_COMMITTED) events.add("journal"); }), key);
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var dispatcher = new CareerCommandDispatcher(gate, journal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            var handler = new Handler(r, effects, events);
            check(dispatcher.execute(owner, context, r.intent(), EMPTY, handler).detail() == CareerCommandDispatcher.Detail.REQUEST_REQUIRED
                    && effects.get() == 0 && journal.read(save).receipts().isEmpty(), "ordinary dispatcher cannot bypass persistent payload prerequisite");
            var result = dispatcher.executeStoredFinance(owner, context, r, EMPTY, requests, EMPTY, handler);
            check(events.equals(List.of("payload", "journal", "effect", "journal")) && effects.get() == 1, "payload then durable PREPARED then effect then receipt");
            check(result.dispatch().kind() == CareerCommandDispatcher.Kind.EXECUTED && result.requests().requests().equals(List.of(r)), "result keeps committed contract token separate from effect receipt");
        }
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 2, save, primary, secondary);
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var reopenedJournal = new CareerCommandJournal(new CareerSidecarStore(ordered.resolve("journals")), key);
            var reopenedRequests = new FinanceRequestStore(new CareerSidecarStore(ordered.resolve("requests")), key);
            var dispatcher = new CareerCommandDispatcher(gate, reopenedJournal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            var retry = dispatcher.executeStoredFinance(owner, context, r, reopenedJournal.read(save).token(), reopenedRequests,
                    reopenedRequests.read(save).token(), new Handler(r, effects, events));
            check(retry.dispatch().kind() == CareerCommandDispatcher.Kind.RECORDED_ONLY && effects.get() == 1, "persisted payload and fresh gate cannot replay historical receipt");
        }
        // Journal interruption after payload commit leaves inspectable request but never grants a repeat lease.
        for (Stage stage : Stage.values()) {
            Path fault = root.resolve("journal-fault-" + stage);
            var contracts = new FinanceRequestStore(new CareerSidecarStore(fault.resolve("requests")), key);
            var failingJournal = new CareerCommandJournal(new CareerSidecarStore(fault.resolve("journals"), at -> { if (at == stage) throw new IOException("Owned journal interruption"); }), key);
            try (var gate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
                var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT)); var count = new AtomicInteger();
                var result = new CareerCommandDispatcher(gate, failingJournal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2))
                        .executeStoredFinance(owner, context, r, EMPTY, contracts, EMPTY, new Handler(r, count, new ArrayList<>()));
                check(count.get() == 0 && result.dispatch().kind() == CareerCommandDispatcher.Kind.UNCERTAIN && contracts.read(save).requests().equals(List.of(r)),
                        "journal failure retains full request and conservatively blocks with no handler " + stage);
                if (stage == Stage.HEAD_COMMITTED) check(failingJournal.read(save).receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN, "unacknowledged PREPARED is uncertain after reopen");
            }
            try (var reopenedGate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object(); var context = reopenedGate.installContext(key.career(), key.binding(), 2, save, primary, secondary);
                var owner = reopenedGate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT)); var count = new AtomicInteger();
                var reopenedJournal = new CareerCommandJournal(new CareerSidecarStore(fault.resolve("journals")), key);
                var dispatcher = new CareerCommandDispatcher(reopenedGate, reopenedJournal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
                if (stage == Stage.HEAD_COMMITTED) {
                    var result = dispatcher.executeStoredFinance(owner, context, r, reopenedJournal.read(save).token(), contracts, contracts.read(save).token(), new Handler(r, count, new ArrayList<>()));
                    check(result.dispatch().kind() == CareerCommandDispatcher.Kind.RECORDED_ONLY && count.get() == 0, "reopened uncertain journal never repeats stored request");
                    var another = request(key, save, "blocked-new", 1);
                    rejects(Reason.RECONCILIATION_REQUIRED, () -> dispatcher.executeStoredFinance(owner, context, another, reopenedJournal.read(save).token(), contracts,
                            contracts.read(save).token(), new Handler(another, count, new ArrayList<>())));
                    check(contracts.read(save).requests().equals(List.of(r)), "uncertainty blocks new payload before storage");
                } else rejects(Reason.RECONCILIATION_REQUIRED, () -> dispatcher.executeStoredFinance(owner, context, r, EMPTY, contracts,
                        contracts.read(save).token(), new Handler(r, count, new ArrayList<>())));
                check(count.get() == 0, "uncommitted or uncertain journal remains non-executable after fresh gate " + stage);
            }
        }
        Path conflict = root.resolve("historical-conflict");
        var historical = new CareerCommandJournal(new CareerSidecarStore(conflict.resolve("journals")), key);
        var pending = historical.prepare(save, EMPTY, r.intent());
        var applied = historical.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, pending.snapshot().token());
        var contracts = new FinanceRequestStore(new CareerSidecarStore(conflict.resolve("requests")), key);
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT)); var count = new AtomicInteger();
            var dispatcher = new CareerCommandDispatcher(gate, historical, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            var changed = request(key, save, r.key(), 99);
            rejects(Reason.CONFLICT, () -> dispatcher.executeStoredFinance(owner, context, changed, applied.token(), contracts, EMPTY, new Handler(changed, count, new ArrayList<>())));
            check(contracts.read(save).requests().isEmpty() && count.get() == 0, "historical digest conflict is rejected before payload storage");
            rejects(Reason.CONFLICT, () -> dispatcher.executeStoredFinance(owner, context, r, EMPTY, contracts, EMPTY, new Handler(r, count, new ArrayList<>())));
            check(contracts.read(save).requests().isEmpty(), "stale journal token cannot consume payload capacity");
        }
        System.out.println("PASS: " + checks + " finance request-store checks; owned fixture " + root + "; no original save-inclusion claim.");
    }
}
