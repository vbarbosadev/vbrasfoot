package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.Player;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.LoanRequestStore.State;

public final class LoanDecisionsSelfTest {
    private static int checks;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    private static LoanRequest request(Key key, SaveRevision save, String name, int strength) {
        return new LoanRequest(key.career(), key.binding(), save, key.mod(), name,
                new FinanceRequest.Club(0, 1, "Origin"), new FinanceRequest.Club(1, 2, "Destination"), 0,
                List.of(new Player(1, "Player", 20, 1, strength, 100)), List.of(new Player(2, "Other", 20, 1, 40, 100)), 1000);
    }
    public static void main(String[] args) throws Exception {
        for (int code = 0; code <= 6; code++) {
            var eligibility = new LoanDiagnostics.Eligibility(code);
            check(eligibility.eligible() == (code == 1), "only native preflight success permits registration: " + code);
            check(!eligibility.report().isBlank(), "all preflight results have a diagnostic");
        }
        check(LoanDiagnostics.nativeResult(5).contains("nao esta disponivel"), "native unavailability reason");
        check(LoanDiagnostics.nativeResult(3).contains("quatro"), "native loan cap reason");
        check(LoanDiagnostics.nativeResult(4).contains("elegibilidade"), "native club eligibility reason");
        check(LoanDiagnostics.nativeResult(6).contains("35"), "native roster cap not legacy UI limit30");
        check(LoanDiagnostics.nativeResult(-1).contains("desconhecido"), "unknown code never claims success");
        check(LoanDiagnostics.historical().contains("nao gravado"), "historical diagnosis never invented");
        Path root = Files.createTempDirectory(Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode")), "loan-decisions-");
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "loan.decisions"); var save = new SaveRevision("a".repeat(64), 3);
        var store = new LoanRequestStore(new CareerSidecarStore(root.resolve("normal")), key);
        var approvedRequest = request(key, save, "approve", 20);
        var pending = store.persist(approvedRequest, EMPTY);
        check(pending.state("approve") == State.PENDING, "durable pending state");
        var approved = store.evaluate(save, "approve", pending.token());
        var reopened = new LoanRequestStore(new CareerSidecarStore(root.resolve("normal")), key);
        check(reopened.read(save).state("approve") == State.APPROVED, "approval survives reopen without execution");
        var changedSave = new SaveRevision("b".repeat(64), 4);
        var stale = reopened.inspectRecordedRecovery(changedSave);
        check(stale.reviewedState("approve").equals("STALE_REVIEW_REQUIRED") && !stale.executable("approve"),
                "old approval is visibly stale and not actionable after save revision change");
        check(reopened.inspectRecordedRecovery(save).executable("approve"), "same revision approval remains eligible for full coordinator validation");
        rejects(() -> reopened.claim(changedSave, "approve", approved.token()));
        check(reopened.read(save).token().equals(approved.token()), "stale claim refusal does not mutate historical approval");
        rejects(() -> store.cancel(save, "approve", pending.token()));
        var claim = store.claim(save, "approve", approved.token());
        check(claim.lease().isPresent() && reopened.read(save).state("approve") == State.EXECUTING, "execution intent committed before lease");
        check(reopened.claim(save, "approve", claim.snapshot().token()).lease().isEmpty(), "restart never reissues execution lease");
        check(reopened.inspectRecordedRecovery(save).reviewedState("approve").equals("OUTCOME_UNKNOWN"),
                "interrupted execution is displayed as uncertain, never approved");
        rejects(() -> reopened.cancel(save, "approve", claim.snapshot().token()));
        rejects(() -> reopened.complete(save, claim.lease().orElseThrow(), State.APPLIED, claim.snapshot().token()));
        var applied = store.complete(save, claim.lease().orElseThrow(), State.APPLIED, claim.snapshot().token());
        check(applied.state("approve") == State.APPLIED && applied.requests().get(0).equals(approvedRequest), "terminal result preserves immutable payload");
        rejects(() -> store.complete(save, claim.lease().orElseThrow(), State.APPLIED, applied.token()));
        check(store.claim(save, "approve", applied.token()).lease().isEmpty(), "applied cannot execute again");
        var historical = reopened.inspectRecordedRecovery(changedSave);
        check(historical.reviewedState("approve").equals("APPLIED") && !historical.executable("approve"), "applied survives revision changes only as non-executable history");
        var cancelPending = store.persist(request(key, save, "cancel", 20), applied.token());
        var cancelled = store.cancel(save, "cancel", cancelPending.token());
        check(reopened.read(save).state("cancel") == State.CANCELLED && store.evaluate(save, "cancel", cancelled.token()).state("cancel") == State.CANCELLED,
                "cancelled request cannot be reevaluated into approval");
        var refusedPending = store.persist(request(key, save, "refuse", 90), cancelled.token());
        var refused = store.evaluate(save, "refuse", refusedPending.token());
        check(refused.state("refuse") == State.REFUSED && store.claim(save, "refuse", refused.token()).lease().isEmpty(), "policy refusal durable and non-executable");

        Path legacyRoot = root.resolve("legacy"); var raw = new CareerSidecarStore(legacyRoot);
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        byte[] payload = approvedRequest.encode(); out.writeInt(0x56424c52); out.writeInt(1); out.writeInt(1); out.writeInt(payload.length); out.write(payload); out.flush();
        var legacyHead = raw.write(key, save, 1, EMPTY, bytes.toByteArray());
        var legacy = new LoanRequestStore(raw, key); var legacyRead = legacy.read(save);
        check(legacyRead.token().equals(legacyHead.token()) && legacyRead.state("approve") == State.PENDING, "schema1 read has pending state, no silent write migration");
        var upgraded = legacy.evaluate(save, "approve", legacyRead.token());
        check(raw.read(key, save).value().orElseThrow().schema() == 2 && upgraded.requests().get(0).digest().equals(approvedRequest.digest()),
                "explicit decision writes schema2 preserving contract digest");

        Path faultRoot = root.resolve("claim-fault"); var armed = new AtomicBoolean();
        var faulty = new LoanRequestStore(new CareerSidecarStore(faultRoot, stage -> {
            if (armed.get() && stage == Stage.HEAD_COMMITTED) throw new IOException("Owned postcommit execution-intent fault");
        }), key);
        var faultPending = faulty.persist(approvedRequest, EMPTY); var faultApproved = faulty.evaluate(save, "approve", faultPending.token());
        armed.set(true); rejects(() -> faulty.claim(save, "approve", faultApproved.token()));
        var recovered = new LoanRequestStore(new CareerSidecarStore(faultRoot), key); var uncertain = recovered.read(save);
        check(uncertain.state("approve") == State.EXECUTING && recovered.claim(save, "approve", uncertain.token()).lease().isEmpty(),
                "postcommit fault cannot cause retry, even before original effect");
        var links = LoanContractReview.compare(uncertain, new CareerCommandJournal.Snapshot("owned", List.of()));
        check(links.get(0).status() == LoanContractReview.Status.OUTCOME_UNKNOWN && links.get(0).blocksContext(), "interrupted execution blocks reviewed context");
        System.out.println("PASS: " + checks + " durable loan-decision checks; metadata only, fixture " + root);
    }
}
