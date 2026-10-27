package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.Player;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static br.vbrasfoot.LoanReleasePolicy.Decision.*;

public final class LoanReleasePolicySelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static FinanceDelta.Snapshot finance(long balance) {
        var fields = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) fields.put(field, 0L);
        return new FinanceDelta.Snapshot(balance, fields);
    }
    public static void main(String[] args) throws Exception {
        check(LoanReleasePolicy.evaluate(0, List.of(8, 20, 30), List.of(-1, -1, -1), 101).decision() == FREE, "low strength does not need invented games");
        check(LoanReleasePolicy.evaluate(1, List.of(8, 20, 30), List.of(-1, -1, -1), 101).decision() == FREE, "exact median free");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(2, 8, 8), 109).fee() == 10, "quarter usage boundary and rounded10percent fee");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(2, 8, 8), 109).decision() == PAID, "high strength low usage paid");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(3, 8, 8), 109).decision() == REFUSE, "regular high-strength player retained");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(0, 4, 4), 109).decision() == INSUFFICIENT_DATA, "season just started not interpreted as unused star");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(-1, 8, 8), 109).decision() == INSUFFICIENT_DATA, "unknown candidate games never zero");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(0, -1, 8), 109).decision() == PAID, "known maximum proves low use despite unknown teammate");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(1, -1, 5), 109).decision() == PAID, "one game with known five-game teammate is sufficient proof");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(2, -1, 5), 109).decision() == INSUFFICIENT_DATA, "partial maximum cannot prove rejection or approval above threshold");
        check(LoanReleasePolicy.evaluate(LoanReleasePolicy.LEGACY_POLICY, 0, List.of(30, 8, 20), List.of(0, -1, 8), 109).decision() == INSUFFICIENT_DATA, "legacy rule preserved for stored contracts");
        check(LoanReleasePolicy.evaluate(0, List.of(30, 8, 20), List.of(0, -1, 8), 109).reason().contains("2/3"), "diagnostic reports participation coverage");
        check(LoanReleasePolicy.evaluate(0, List.of(20, 19), List.of(0, 8), 100).decision() == PAID, "half-integer origin median");
        check(LoanReleasePolicy.evaluate(0, List.of(Integer.MAX_VALUE, Integer.MIN_VALUE), List.of(0, 8), Integer.MAX_VALUE).fee() == 214748364, "strength and fee int extremes safe");
        var save = new CareerSidecarStore.SaveRevision("a".repeat(64), 3); var career = UUID.randomUUID(); var binding = UUID.randomUUID();
        var origin = new FinanceRequest.Club(0, 1, "Origin"); var destination = new FinanceRequest.Club(1, 2, "Destination");
        var players = List.of(new Player(1, "Candidate", 20, 1, 30, 109), new Player(2, "Other", 20, 1, 8, 10), new Player(3, "Third", 20, 1, 20, 10));
        var targets = List.of(new Player(4, "Target", 20, 1, 40, 100));
        var legacy = new LoanRequest(career, binding, save, "loan.policy", "legacy", origin, destination, 0, players, targets, 1000);
        byte[] oldBytes = legacy.encode(); String oldDigest = legacy.digest();
        var terms = new LoanTerms(2022, List.of(2, 8, 8), finance(1000), finance(100));
        var paid = new LoanRequest(career, binding, save, "loan.policy", "paid", origin, destination, 0, players, targets, 1000, Optional.of(terms));
        check(paid.fee() == 10 && paid.assessment().decision() == LoanClubAssessment.Decision.APPROVE, "paid contract approval includes destination and funds");
        check(LoanRequest.decode(paid.encode()).equals(paid) && LoanRequest.decode(paid.encode()).digest().equals(paid.digest()), "v2 canonical codec and exact fee inputs");
        var oldPolicyTerms = new LoanTerms(terms.season(), terms.games(), terms.originFinance(), terms.destinationFinance(), true, LoanReleasePolicy.LEGACY_POLICY);
        var oldPolicyRequest = new LoanRequest(career, binding, save, "loan.policy", "old-policy", origin, destination, 0, players, targets, 1000, Optional.of(oldPolicyTerms));
        check(LoanRequest.decode(oldPolicyRequest.encode()).equals(oldPolicyRequest) && Arrays.equals(oldPolicyRequest.encode(), LoanRequest.decode(oldPolicyRequest.encode()).encode()), "old release policy bytes retained without silent upgrade");
        check(LoanRequest.decode(oldBytes).equals(legacy) && legacy.digest().equals(oldDigest) && legacy.fee() == 0, "v1 unchanged without retroactive fee");
        var poorTerms = new LoanTerms(2022, terms.games(), terms.originFinance(), finance(9));
        var poor = new LoanRequest(career, binding, save, "loan.policy", "poor", origin, destination, 0, players, targets, 1000, Optional.of(poorTerms));
        check(poor.assessment().reason() == LoanClubAssessment.Reason.INSUFFICIENT_FEE_BALANCE, "insufficient funds refuses before command");
        check(!poor.digest().equals(paid.digest()), "fee/balance evidence bound into immutable digest");
        var credited = FinanceDelta.predict(terms.originFinance(), FinanceDelta.Direction.CREDIT_RAW_6, paid.fee());
        var debited = FinanceDelta.predict(terms.destinationFinance(), FinanceDelta.Direction.DEBIT_RAW_7, paid.fee());
        check(credited.balance() == 1010 && debited.balance() == 90 && credited.balance() + debited.balance() == 1100, "paired fee conserves balances");
        var aiTerms = new LoanTerms(2022, terms.games(), terms.originFinance(), terms.destinationFinance(), false);
        check(aiTerms.predicted(true, 10).balance() == 1010 && aiTerms.predicted(true, 10).ledger().equals(terms.originFinance().ledger()), "native AI source credit changes balance but not human-only ledger");
        check(terms.predicted(true, 10).equals(credited) && terms.predicted(false, 10).equals(debited), "human paired fee predicts full ledgers");
        Path root = Files.createTempDirectory(Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode")), "loan-release-policy-");
        var key = new CareerSidecarStore.Key(career, binding, "loan.policy");
        var store = new LoanRequestStore(new CareerSidecarStore(root), key);
        var snapshot = store.persist(legacy, CareerSidecarStore.EMPTY); snapshot = store.persist(paid, snapshot.token());
        snapshot = store.evaluate(save, paid.key(), snapshot.token());
        var claim = store.claim(save, paid.key(), snapshot.token());
        var reopened = new LoanRequestStore(new CareerSidecarStore(root), key);
        check(reopened.read(save).requests().equals(List.of(legacy, paid)), "mixed v1/v2 durable store no rewrite");
        check(reopened.claim(save, paid.key(), claim.snapshot().token()).lease().isEmpty(), "interrupted compound effect never reissues lease");
        var completed = store.complete(save, claim.lease().orElseThrow(), LoanRequestStore.State.APPLIED, claim.snapshot().token());
        check(reopened.claim(save, paid.key(), completed.token()).lease().isEmpty(), "paid result cannot be charged twice on reopen");
        for (int failAfter = 0; failAfter <= 3; failAfter++) {
            int failureStage = failAfter; int[] operations = {0, 0, 0}; long[] balances = {1000, 100};
            var requests = new LoanRequestStore(new CareerSidecarStore(root.resolve("compound-" + failAfter)), key);
            var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journal-" + failAfter)), key);
            try (var gate = new CareerCommandGate()) {
                Object primary = new Object(), secondary = new Object();
                var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.LOAN_ACCEPT));
                var context = gate.installContext(career, binding, 1, save, primary, secondary);
                var dispatcher = new CareerCommandDispatcher(gate, journal, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(3));
                var handler = new CareerCommandDispatcher.Handler() {
                    public Optional<LoanRequest> persistentLoanRequest() { return Optional.of(paid); }
                    public boolean accepts(CareerCommandJournal.Intent intent) { return paid.intent().equals(intent); }
                    public boolean acceptsContext(CareerCommandGate.Context value, String id) { return paid.matches(value, id); }
                    public CareerCommandGate.Status apply() {
                        operations[0]++;
                        if (failureStage == 0) throw new IllegalStateException("Owned fault after loan before fee");
                        operations[1]++; balances[1] -= paid.fee();
                        if (failureStage == 1) throw new IllegalStateException("Owned fault after debit before credit");
                        operations[2]++; balances[0] += paid.fee();
                        if (failureStage == 2) throw new IllegalStateException("Owned fault after credit before completion");
                        return CareerCommandGate.Status.APPLIED;
                    }
                };
                var result = dispatcher.executeStoredLoan(owner, context, paid, CareerSidecarStore.EMPTY, requests, CareerSidecarStore.EMPTY, handler);
                check(result.dispatch().kind() == (failAfter == 3 ? CareerCommandDispatcher.Kind.EXECUTED : CareerCommandDispatcher.Kind.UNCERTAIN), "compound failure never means rollback: " + failAfter);
                int[] before = operations.clone(); long[] beforeBalances = balances.clone();
                var replay = dispatcher.executeStoredLoan(owner, context, paid, journal.read(save).token(), requests, requests.read(save).token(), handler);
                check(replay.dispatch().kind() != CareerCommandDispatcher.Kind.EXECUTED && Arrays.equals(before, operations) && Arrays.equals(beforeBalances, balances), "no duplicate loan/debit/credit after partial failure: " + failAfter);
                var recovery = new LoanRequestStore(new CareerSidecarStore(root.resolve("compound-" + failAfter)), key);
                check(!recovery.inspectRecordedRecovery(save).executable(paid.key()), "compound outcome non-executable on reopen: " + failAfter);
            }
        }
        System.out.println("PASS: " + checks + " relative release/fee/codec/recovery checks; synthetic fixtures only: " + root);
    }
}
