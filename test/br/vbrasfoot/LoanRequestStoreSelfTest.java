package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.Player;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static br.vbrasfoot.CareerSidecarStore.*;

public final class LoanRequestStoreSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    private static LoanRequest request(UUID career, UUID binding, SaveRevision save, String key, int strength) {
        return new LoanRequest(career, binding, save, "loan.fixture", key,
                new FinanceRequest.Club(0, 1, "Origin"), new FinanceRequest.Club(1, 2, "Destination"), 0,
                List.of(new Player(10, "Player", 20, 1, strength, 100)), List.of(new Player(20, "Other", 22, 1, 50, 100)), 1000);
    }
    private static CareerCommandDispatcher.Handler handler(LoanRequest request, AtomicInteger effects) {
        return new CareerCommandDispatcher.Handler() {
            public Optional<LoanRequest> persistentLoanRequest() { return Optional.of(request); }
            public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
            public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return request.matches(context, owner); }
            public CareerCommandGate.Status apply() { effects.incrementAndGet(); return CareerCommandGate.Status.APPLIED; }
        };
    }
    public static void main(String[] args) throws Exception {
        var parent = Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"));
        var root = Files.createTempDirectory(parent, "loan-request-store-");
        UUID career = UUID.randomUUID(), binding = UUID.randomUUID(); var save = new SaveRevision("a".repeat(64), 10);
        var key = new Key(career, binding, "loan.fixture"); var request = request(career, binding, save, "one", 40);
        check(LoanRequest.decode(request.encode()).equals(request), "canonical codec roundtrip");
        check(request.digest().equals(LoanRequest.decode(request.encode()).digest()), "digest stable after decode");
        check(request.assessment().decision() == LoanClubAssessment.Decision.APPROVE, "policy evaluated from copied roster");
        rejects(() -> LoanRequest.decode(Arrays.copyOf(request.encode(), request.encode().length - 1)));
        rejects(() -> LoanRequest.decode(Arrays.copyOf(request.encode(), request.encode().length + 1)));
        rejects(() -> LoanRequest.decode(new byte[LoanRequest.MAX_BYTES + 1]));
        var store = new LoanRequestStore(new CareerSidecarStore(root.resolve("requests")), key);
        check(store.read(save).requests().isEmpty() && !Files.exists(root.resolve("requests")), "read absent store creates nothing");
        var first = store.persist(request, EMPTY);
        check(new LoanRequestStore(new CareerSidecarStore(root.resolve("requests")), key).read(save).requests().equals(List.of(request)), "reopen immutable request");
        check(store.persist(request, first.token()).token().equals(first.token()), "duplicate payload read-only confirmation");
        rejects(() -> store.persist(request(career, binding, save, "one", 41), first.token()));
        rejects(() -> store.persist(request(career, binding, save, "two", 40), EMPTY));
        rejects(() -> store.read(new SaveRevision("b".repeat(64), 10)));
        var administrative = store.inspectRecordedRecovery(new SaveRevision("b".repeat(64), 10));
        check(administrative.revisionChanged() && administrative.recordedSave().equals(save), "administrative loan read preserves recorded anchor");
        rejects(() -> store.persist(request(UUID.randomUUID(), binding, save, "two", 40), first.token()));
        Object primary = new Object(), secondary = new Object(); var effects = new AtomicInteger();
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
            var context = gate.installContext(career, binding, 1, save, primary, secondary);
            var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journal")), key);
            var dispatcher = new CareerCommandDispatcher(gate, journal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(3));
            var refused = request(career, binding, save, "policy-refused", 60);
            rejects(() -> dispatcher.executeStoredLoan(owner, context, refused, EMPTY, store, first.token(), handler(refused, effects)));
            check(effects.get() == 0 && store.read(save).requests().size() == 1 && journal.read(save).receipts().isEmpty(),
                    "policy refusal cannot persist execution intent or invoke handler");
            var foreignContext = new CareerCommandGate.Context(context.token(), UUID.randomUUID(), binding, 1, save);
            rejects(() -> dispatcher.executeStoredLoan(owner, foreignContext, request, EMPTY, store, first.token(), handler(request, effects)));
            check(effects.get() == 0, "foreign binding context cannot invoke handler");
            check(dispatcher.execute(owner, context, request.intent(), EMPTY, handler(request, effects)).detail() == CareerCommandDispatcher.Detail.REQUEST_REQUIRED,
                    "direct dispatch cannot bypass payload store");
            check(effects.get() == 0, "bypassed dispatch has no effect");
            var result = dispatcher.executeStoredLoan(owner, context, request, EMPTY, store, first.token(), handler(request, effects));
            check(result.dispatch().kind() == CareerCommandDispatcher.Kind.EXECUTED && effects.get() == 1, "payload+journal then owned synthetic effect");
            check(result.dispatch().journal().orElseThrow().receipts().get(0).intent().operation() == CareerCommandGate.Operation.LOAN_ACCEPT, "loan operation persisted separately");
            var duplicate = dispatcher.executeStoredLoan(owner, context, request, result.dispatch().journal().orElseThrow().token(), store, result.requests().token(), handler(request, effects));
            check(duplicate.dispatch().kind() == CareerCommandDispatcher.Kind.RECORDED_ONLY && effects.get() == 1, "same session historical result no replay");
        }
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
            Object p = new Object(), s = new Object(); var context = gate.installContext(career, binding, 2, save, p, s);
            var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journal")), key);
            var dispatcher = new CareerCommandDispatcher(gate, journal, () -> new CareerCommandDispatcher.Roots(p, s), Duration.ofSeconds(3));
            var result = dispatcher.executeStoredLoan(owner, context, request, journal.read(save).token(), store, store.read(save).token(), handler(request, effects));
            check(result.dispatch().kind() == CareerCommandDispatcher.Kind.RECORDED_ONLY && effects.get() == 1, "restart historical effect never replayed on new roots");
        }
        String token = store.read(save).token();
        for (int i = 2; i <= 32; i++) token = store.persist(request(career, binding, save, "item" + i, 40), token).token();
        String finalToken = token;
        rejects(() -> store.persist(request(career, binding, save, "overflow", 40), finalToken));
        check(store.read(save).requests().size() == 32, "capacity never evicts old requests");
        var contractSnapshot = new LoanRequestStore.Snapshot("owned", List.of(request));
        for (var state : CareerCommandJournal.State.values()) {
            var receipt = new CareerCommandJournal.Receipt(UUID.randomUUID(), request.intent(), state);
            var history = new CareerCommandJournal.Snapshot("owned", List.of(receipt));
            var link = LoanContractReview.compare(contractSnapshot, history).get(0);
            check(link.blocksContext() == (state != CareerCommandJournal.State.NO_EFFECT_CONFIRMED), "loan receipt state reviewed conservatively " + state);
        }
        check(LoanContractReview.compare(contractSnapshot, new CareerCommandJournal.Snapshot("owned", List.of())).get(0).status()
                == LoanContractReview.Status.PENDING_WITHOUT_RECEIPT, "pending payload is not permission to execute");
        var conflictReceipt = new CareerCommandJournal.Receipt(UUID.randomUUID(), request(career, binding, save, "one", 41).intent(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED);
        check(LoanContractReview.compare(contractSnapshot, new CareerCommandJournal.Snapshot("owned", List.of(conflictReceipt))).get(0).status()
                == LoanContractReview.Status.INTENT_CONFLICT, "same key different payload never treated no-effect");
        check(LoanContractReview.compare(new LoanRequestStore.Snapshot("owned", List.of()), new CareerCommandJournal.Snapshot("owned", List.of(conflictReceipt))).get(0).status()
                == LoanContractReview.Status.RECEIPT_WITHOUT_REQUEST, "orphan loan receipt blocks");
        for (Stage stage : Stage.values()) {
            Path faultRoot = root.resolve("fault-" + stage);
            var failing = new LoanRequestStore(new CareerSidecarStore(faultRoot, observed -> {
                if (observed == stage) throw new IOException("Owned loan persistence fault");
            }), key);
            rejects(() -> failing.persist(request, EMPTY));
            var reopened = new LoanRequestStore(new CareerSidecarStore(faultRoot), key);
            if (stage == Stage.HEAD_COMMITTED) check(reopened.read(save).requests().equals(List.of(request)), "postcommit failure payload retained, no automatic effect");
            else rejects(() -> reopened.read(save));
        }
        var damagedStore = new CareerSidecarStore(root.resolve("damaged"));
        damagedStore.write(key, save, 1, EMPTY, new byte[] {0, 1, 2});
        rejects(() -> new LoanRequestStore(damagedStore, key).read(save));
        // Handler failure after entry is uncertain and must remain non-replayable.
        try (var gate = new CareerCommandGate()) {
            Object p = new Object(), s = new Object();
            var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
            var context = gate.installContext(career, binding, 1, save, p, s);
            var unknownJournal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("unknown-journal")), key);
            var unknownRequests = new LoanRequestStore(new CareerSidecarStore(root.resolve("unknown-requests")), key);
            var dispatcher = new CareerCommandDispatcher(gate, unknownJournal, () -> new CareerCommandDispatcher.Roots(p, s), Duration.ofSeconds(3));
            var uncertainHandler = new CareerCommandDispatcher.Handler() {
                public Optional<LoanRequest> persistentLoanRequest() { return Optional.of(request); }
                public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
                public boolean acceptsContext(CareerCommandGate.Context expected, String ownerId) { return request.matches(expected, ownerId); }
                public CareerCommandGate.Status apply() { throw new IllegalStateException("Owned uncertain effect"); }
            };
            var unknown = dispatcher.executeStoredLoan(owner, context, request, EMPTY, unknownRequests, EMPTY, uncertainHandler);
            check(unknown.dispatch().kind() == CareerCommandDispatcher.Kind.UNCERTAIN, "handler failure never presumed rollback");
            check(unknownJournal.read(save).receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN, "uncertainty durable");
            var replay = dispatcher.executeStoredLoan(owner, context, request, unknownJournal.read(save).token(), unknownRequests,
                    unknownRequests.read(save).token(), handler(request, effects));
            check(replay.dispatch().kind() != CareerCommandDispatcher.Kind.EXECUTED && effects.get() == 1, "unknown cannot replay effect");
        }
        System.out.println("PASS: " + checks + " durable loan payload/store/dispatch checks; synthetic effects only, fixture " + root);
    }
}
