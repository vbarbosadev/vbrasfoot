package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Own operations only. Evidence of observed execution is not evidence that a save includes it. */
public final class FinanceExecutionWitnessSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static FinanceRequest request(Key key, SaveRevision save, String operation) {
        var ledger = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) ledger.put(field, 0L);
        return new FinanceRequest(key.career(), key.binding(), save, key.mod(), operation, new FinanceRequest.Club(0, 1, "Owned"),
                FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, ledger));
    }
    private static class Handler implements CareerCommandDispatcher.Handler {
        final FinanceRequest request;
        final Callable<CareerCommandGate.Status> effect;
        Handler(FinanceRequest request, Callable<CareerCommandGate.Status> effect) { this.request = request; this.effect = effect; }
        public Optional<FinanceRequest> persistentRequest() { return Optional.of(request); }
        public boolean supportsExecutionWitness() { return true; }
        public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
        public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return request.matches(context, owner); }
        public CareerCommandGate.Status apply() throws Exception {
            if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("EDT required"); return effect.call();
        }
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "finance-execution-witness-");
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.witness"); var save = new SaveRevision("a".repeat(64), 12);
        var request = request(key, save, "once");
        var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("direct-journal")), key);
        var witness = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("direct-witness")), key);
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object();
            var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
            check(witness.read(save).witnesses().isEmpty() && !Files.exists(root.resolve("direct-witness")), "read of absent witness root is side-effect-free");
            var prepared = journal.prepare(save, EMPTY, request.intent());
            var proof = journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token());
            var foreignStore = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("foreign-scope")), new Key(UUID.randomUUID(), key.binding(), key.mod()));
            boolean foreignProof = false;
            try { foreignStore.arm(context, proof, EMPTY); } catch (IllegalArgumentException expected) { foreignProof = true; }
            check(foreignProof && !Files.exists(root.resolve("foreign-scope")), "fresh execution proof cannot be transplanted into another career");
            var armed = witness.arm(context, proof, EMPTY);
            var value = armed.snapshot().witnesses().get(0);
            check(value.operationId().equals(prepared.snapshot().receipts().get(0).operationId()) && value.intent().equals(request.intent())
                    && value.context().equals(context.token()) && value.generation() == context.generation(), "witness causally binds exact freshly prepared journal UUID, payload and session context");
            check(value.state() == FinanceExecutionWitnessStore.State.ARMED, "arming is not a claim that handler ran");
            rejects(Reason.CONFLICT, () -> witness.arm(context, proof, armed.snapshot().token()));
            var done = witness.complete(armed.lease(), FinanceExecutionWitnessStore.State.MEMORY_APPLIED, armed.snapshot().token());
            check(done.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED, "observed applied execution persists independently of journal completion");
            rejects(Reason.CONFLICT, () -> witness.complete(armed.lease(), FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN, done.token()));
            var reopened = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("direct-witness")), key);
            check(reopened.read(save).equals(done), "restart reconstructs the exact immutable execution fact");
            boolean foreignLease = false;
            try { reopened.complete(armed.lease(), FinanceExecutionWitnessStore.State.MEMORY_APPLIED, done.token()); } catch (IllegalArgumentException expected) { foreignLease = true; }
            check(foreignLease, "previous-process witness lease cannot be reconstructed");
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> reopened.read(new SaveRevision("b".repeat(64), 13)));
            var completed = journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, prepared.snapshot().token());
            rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.executionPermit(prepared.lease().orElseThrow(), completed.token()));
            check(journal.prepare(save, completed.token(), request.intent()).lease().isEmpty(), "terminal historical journal never arms another execution");
        }
        // Observe ordering and prove journal terminal-commit failure does not erase execution evidence.
        for (Stage stage : Stage.values()) {
            Path fixture = root.resolve("terminal-journal-" + stage); var events = Collections.synchronizedList(new ArrayList<String>());
            var effects = new AtomicInteger(); var commits = new AtomicInteger();
            var contracts = new FinanceRequestStore(new CareerSidecarStore(fixture.resolve("contracts"), point -> { if (point == Stage.HEAD_COMMITTED) events.add("payload"); }), key);
            var journalStore = new CareerSidecarStore(fixture.resolve("journals"), point -> {
                if (point == Stage.RECORD_WRITTEN) commits.incrementAndGet();
                if (commits.get() == 2 && point == stage) throw new IOException("Owned terminal journal failure");
                if (point == Stage.HEAD_COMMITTED) events.add("journal");
            });
            var history = new CareerCommandJournal(journalStore, key);
            var witnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses"), point -> { if (point == Stage.HEAD_COMMITTED) events.add("witness"); }), key);
            try (var gate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
                var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                var result = new CareerCommandDispatcher(gate, history, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2))
                        .executeWitnessedFinance(owner, context, request, EMPTY, contracts, EMPTY, witnesses, EMPTY,
                                new Handler(request, () -> { effects.incrementAndGet(); events.add("effect"); return CareerCommandGate.Status.APPLIED; }));
                check(result.dispatch().kind() == CareerCommandDispatcher.Kind.UNCERTAIN && result.dispatch().detail() == CareerCommandDispatcher.Detail.JOURNAL_FAILURE
                        && effects.get() == 1 && !gate.contextInstalled(), "journal terminal failure preserves uncertainty and invalidates authority " + stage);
                check(events.subList(0, 5).equals(List.of("payload", "journal", "witness", "effect", "witness")), "contract -> PREPARED -> ARMED -> effect -> MEMORY_APPLIED order " + stage);
                var durable = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses")), key).read(save);
                check(durable.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED, "execution evidence survives missing/uncertain terminal journal commit " + stage);
                var observed = new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals")), key).read(save);
                check(observed.receipts().get(0).operationId().equals(durable.witnesses().get(0).operationId()), "journal/witness exact operation IDs remain correlated " + stage);
                check(observed.receipts().get(0).state() == (stage == Stage.HEAD_COMMITTED ? CareerCommandJournal.State.APPLIED : CareerCommandJournal.State.OUTCOME_UNKNOWN), "only committed journal terminal state is exposed " + stage);
                check(FinanceExecutionRecovery.inspect(observed, durable).get(0).status() == FinanceExecutionRecovery.Status.MEMORY_APPLIED_SAVE_UNPROVEN,
                        "lost terminal acknowledgement can be distinguished from unobserved execution, without rewriting UNKNOWN " + stage);
                check(observed.receipts().get(0).state() == (stage == Stage.HEAD_COMMITTED ? CareerCommandJournal.State.APPLIED : CareerCommandJournal.State.OUTCOME_UNKNOWN), "recovery inspection does not mutate original receipt");
                check(new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals")), key).prepare(save, observed.token(), request.intent()).lease().isEmpty(), "known key never replays despite independent execution evidence " + stage);
            }
        }
        for (boolean terminal : List.of(false, true)) for (Stage stage : Stage.values()) {
            Path fixture = root.resolve("witness-fault-" + terminal + "-" + stage); var writes = new AtomicInteger(); var effects = new AtomicInteger();
            var witnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses"), point -> {
                if (point == Stage.RECORD_WRITTEN) writes.incrementAndGet();
                if (writes.get() == (terminal ? 2 : 1) && point == stage) throw new IOException("Owned witness interruption");
            }), key);
            var history = new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals")), key);
            var contracts = new FinanceRequestStore(new CareerSidecarStore(fixture.resolve("contracts")), key);
            try (var gate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
                var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                var result = new CareerCommandDispatcher(gate, history, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2))
                        .executeWitnessedFinance(owner, context, request, EMPTY, contracts, EMPTY, witnesses, EMPTY,
                                new Handler(request, () -> { effects.incrementAndGet(); return CareerCommandGate.Status.APPLIED; }));
                check(result.dispatch().kind() == CareerCommandDispatcher.Kind.UNCERTAIN && result.dispatch().detail() == CareerCommandDispatcher.Detail.WITNESS_FAILURE
                        && effects.get() == (terminal ? 1 : 0), "witness fault never retries effect and preserves phase " + terminal + "/" + stage);
                check(history.read(save).receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN && !gate.contextInstalled(), "witness fault leaves journal uncertain and no authority " + terminal + "/" + stage);
                var reopened = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses")), key);
                if (!terminal && stage != Stage.HEAD_COMMITTED) rejects(Reason.RECONCILIATION_REQUIRED, () -> reopened.read(save));
                else check(reopened.read(save).witnesses().get(0).state() == (terminal && stage == Stage.HEAD_COMMITTED
                        ? FinanceExecutionWitnessStore.State.MEMORY_APPLIED : FinanceExecutionWitnessStore.State.ARMED), "only committed witness state is exposed, never inferred from balance");
            }
        }
        // Runtime timeout stays UNKNOWN even if the trusted handler later returns APPLIED.
        Path timeout = root.resolve("timeout"); var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var count = new AtomicInteger();
        try (var gate = new CareerCommandGate()) {
            Object primary = new Object(), secondary = new Object(); var context = gate.installContext(key.career(), key.binding(), 1, save, primary, secondary);
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var history = new CareerCommandJournal(new CareerSidecarStore(timeout.resolve("journals")), key);
            var contracts = new FinanceRequestStore(new CareerSidecarStore(timeout.resolve("contracts")), key);
            var witnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(timeout.resolve("witnesses")), key);
            try {
                var result = new CareerCommandDispatcher(gate, history, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofMillis(100))
                        .executeWitnessedFinance(owner, context, request, EMPTY, contracts, EMPTY, witnesses, EMPTY, new Handler(request, () -> {
                            entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Owned wait timeout"); count.incrementAndGet(); return CareerCommandGate.Status.APPLIED;
                        }));
                check(entered.getCount() == 0 && result.dispatch().detail() == CareerCommandDispatcher.Detail.EXECUTION_TIMEOUT, "running timeout observed");
                check(witnesses.read(save).witnesses().get(0).state() == FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN, "runtime timeout is durably uncertain, not a late positive");
                boolean saveBlocked = false; try { gate.beginSave(); } catch (IllegalStateException expected) { saveBlocked = true; }
                check(saveBlocked, "in-flight original handler remains fenced after witness/journal completion");
            } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            check(count.get() == 1 && witnesses.read(save).witnesses().get(0).state() == FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN, "late return never rewrites immutable uncertainty");
            check(FinanceExecutionRecovery.inspect(history.read(save), witnesses.read(save)).get(0).status() == FinanceExecutionRecovery.Status.EXECUTION_UNCERTAIN, "timeout cannot be upgraded by delayed return or a later equal balance");
        }
        var intent = request.intent(); UUID operation = UUID.randomUUID(); UUID context = UUID.randomUUID();
        for (var state : FinanceExecutionWitnessStore.State.values()) {
            var prepared = new CareerCommandJournal.Snapshot("j", List.of(new CareerCommandJournal.Receipt(operation, intent, CareerCommandJournal.State.OUTCOME_UNKNOWN)));
            var evidence = new FinanceExecutionWitnessStore.Snapshot("w", List.of(new FinanceExecutionWitnessStore.Witness(operation, intent, context, 1, state)));
            var expected = switch (state) {
                case ARMED -> FinanceExecutionRecovery.Status.ARMED_UNCERTAIN;
                case MEMORY_APPLIED -> FinanceExecutionRecovery.Status.MEMORY_APPLIED_SAVE_UNPROVEN;
                case NO_EFFECT_CONFIRMED -> FinanceExecutionRecovery.Status.NO_EFFECT_CONFIRMED;
                default -> FinanceExecutionRecovery.Status.EXECUTION_UNCERTAIN;
            };
            check(FinanceExecutionRecovery.inspect(prepared, evidence).get(0).status() == expected, "evidence classification does not infer facts missing in phase " + state);
        }
        var historical = new CareerCommandJournal.Snapshot("j", List.of(new CareerCommandJournal.Receipt(operation, intent, CareerCommandJournal.State.APPLIED)));
        check(FinanceExecutionRecovery.inspect(historical, new FinanceExecutionWitnessStore.Snapshot("w", List.of())).get(0).status() == FinanceExecutionRecovery.Status.NO_EXECUTION_WITNESS, "old histories do not acquire fabricated evidence");
        var mismatched = new FinanceExecutionWitnessStore.Snapshot("w", List.of(new FinanceExecutionWitnessStore.Witness(UUID.randomUUID(), intent, context, 1, FinanceExecutionWitnessStore.State.MEMORY_APPLIED)));
        check(FinanceExecutionRecovery.inspect(historical, mismatched).stream().allMatch(value -> value.status() == FinanceExecutionRecovery.Status.CONFLICT), "same key with different operation UUID is never reconciled");
        var contradictory = new FinanceExecutionWitnessStore.Snapshot("w", List.of(new FinanceExecutionWitnessStore.Witness(operation, intent, context, 1, FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED)));
        check(FinanceExecutionRecovery.inspect(historical, contradictory).get(0).status() == FinanceExecutionRecovery.Status.CONFLICT, "NO_EFFECT witness cannot rewrite APPLIED receipt");
        var raw = new CareerSidecarStore(root.resolve("bad-schema")); raw.write(key, save, 2, EMPTY, new byte[0]);
        rejects(Reason.CORRUPT, () -> new FinanceExecutionWitnessStore(raw, key).read(save));
        var malformed = new CareerSidecarStore(root.resolve("bad-payload")); malformed.write(key, save, 1, EMPTY, new byte[] {1});
        rejects(Reason.CORRUPT, () -> new FinanceExecutionWitnessStore(malformed, key).read(save));
        var wrongThread = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { new FinanceExecutionWitnessStore(raw, key).read(save); } catch (Throwable expected) { wrongThread.set(expected); } });
        check(wrongThread.get() instanceof Failure failure && failure.reason() == Reason.WRONG_THREAD, "execution witness file I/O is forbidden on EDT");
        System.out.println("PASS: " + checks + " durable execution-witness checks; owned fixture " + root + "; MEMORY_APPLIED is not save inclusion.");
    }
}
