package br.vbrasfoot;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Read-only metadata review and fail-closed regression tests. Never fabricates a native quote or production load authority. */
public final class LoanContinuationCoordinatorSelfTest {
    private static int checks;
    interface Call { void run() throws Exception; }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejects(Call call) throws Exception {
        try { call.run(); } catch (Failure expected) { checks++; return; }
        throw new AssertionError("Expected blocked review");
    }
    private static void load(Path path, Object primary, Object secondary) throws Exception {
        HookProbe.enterLoad(path.getFileName().toString(), false); HookProbe.loadSource(path.toString());
        var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(path))); trace.readAllBytes(); trace.close();
        HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
    }
    private static Object qualify(CareerAssociationCoordinator.Review review, LoanContinuationStore.Snapshot ledger) throws Exception {
        // Exercise only the pure metadata filter, not verifier/gate installation or gameplay execution.
        var method = CareerAssociationCoordinator.class.getDeclaredMethod("memberHistory", CareerAssociationCoordinator.Review.class, LoanContinuationStore.Snapshot.class);
        method.setAccessible(true);
        try { return method.invoke(null, review, ledger); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loan-coordinator-test-");
        Path saves = Files.createDirectory(root.resolve("saves")); Path source = Files.write(saves.resolve("owned.s22"), new byte[]{1, 2, 3});
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(root.resolve("associations"));
        var journals = new CareerSidecarStore(root.resolve("journals")); var finance = new CareerSidecarStore(root.resolve("finance"));
        var witnesses = new CareerSidecarStore(root.resolve("witnesses")); var loans = new CareerSidecarStore(root.resolve("loans"));
        var continuation = new CareerSidecarStore(root.resolve("continuation"));
        Object primary = new Object(), secondary = new Object();
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        var coordinator = new CareerAssociationCoordinator(saves, associations, journals, finance, witnesses, loans, continuation, observer, verifier, List.of(LoanManagerPanel.OWNER));
        try (var hook = HookProbe.attachLifecycle(new CareerLifecycle()); var gate = new CareerCommandGate()) {
            load(source, primary, secondary);
            var handle = coordinator.confirm(coordinator.review(Optional.empty()), CareerAssociationCoordinator.Action.CREATE_NEW, Optional.empty());
            var save = associations.inspect(handle).save(); var request = LoanContinuationStoreSelfTest.request(handle, "legacy", 1, save);
            var legacy = new LoanRequestStore(loans, new Key(handle.career(), handle.binding(), LoanManagerPanel.OWNER));
            var journal = new CareerCommandJournal(journals, new Key(handle.career(), handle.binding(), LoanManagerPanel.OWNER));
            var persisted = legacy.persist(request, EMPTY); var approved = legacy.evaluate(save, request.key(), persisted.token());
            var claimed = legacy.claim(save, request.key(), approved.token()); var intent = journal.prepare(save, EMPTY, request.intent());
            journal.complete(intent.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, intent.snapshot().token());
            legacy.complete(save, claimed.lease().orElseThrow(), LoanRequestStore.State.APPLIED, claimed.snapshot().token());
            var noEffect = LoanContinuationStoreSelfTest.request(handle, "old-no-effect", 2, save);
            var noEffectPayload = legacy.persist(noEffect, legacy.read(save).token());
            var noEffectApproval = legacy.evaluate(save, noEffect.key(), noEffectPayload.token());
            var noEffectClaim = legacy.claim(save, noEffect.key(), noEffectApproval.token());
            var noEffectIntent = journal.prepare(save, journal.read(save).token(), noEffect.intent());
            journal.complete(noEffectIntent.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, noEffectIntent.snapshot().token());
            legacy.complete(save, noEffectClaim.lease().orElseThrow(), LoanRequestStore.State.NO_EFFECT, noEffectClaim.snapshot().token());
            var emptyLedger = new LoanContinuationStore.Snapshot(EMPTY, List.of(), Map.of());
            check(qualify(coordinator.review(Optional.of(handle)), emptyLedger) != null, "exact legacy APPLIED request/receipt qualifies for adapter inspection only");
            String oldLoanToken = legacy.read(save).token(), oldJournalToken = journal.read(save).token();
            Files.write(source, new byte[]{4, 5, 6, 7}); load(source, primary, secondary);
            var changed = coordinator.review(Optional.of(handle));
            check(changed.status() == CareerAssociationCoordinator.Status.REVISION_REVIEW_REQUIRED, "changed save requires explicit association review");
            check(qualify(changed, emptyLedger) != null, "loan-only old anchors may be inspected, not rewritten");
            check(legacy.inspectRecordedRecovery(changed.decoded().bytes()).snapshot().state(noEffect.key()) == LoanRequestStore.State.NO_EFFECT,
                    "historical confirmed no-effect survives revision change as terminal history, not actionable or unresolved");
            coordinator.confirm(changed, CareerAssociationCoordinator.Action.ACCEPT_REVISION, Optional.empty());
            var current = coordinator.review(Optional.of(handle));
            check(qualify(current, emptyLedger) != null && legacy.inspectRecordedRecovery(current.decoded().bytes()).revisionChanged(), "new baseline keeps old loan revision intact");
            check(legacy.read(save).token().equals(oldLoanToken) && journal.read(save).token().equals(oldJournalToken), "review and association acceptance do not modify old request/journal anchors");
            var ledger = new LoanContinuationStore(continuation, handle);
            var duplicate = LoanContinuationStoreSelfTest.request(handle, "new-key-same-player", 1, current.decoded().bytes());
            var duplicateClaim = ledger.claim(duplicate, EMPTY, Map.of());
            var duplicateDone = ledger.complete(duplicate, duplicateClaim.token(), LoanContinuationStore.State.NO_EFFECT);
            rejects(() -> qualify(current, duplicateDone));
            coordinator.operational(new CareerOperationalContext(gate));
            // A fresh gate has no session receipts: the persisted continuation history must still disable the old panel.
            check(gate.receipts().isEmpty(), "fresh session gate has no imported receipts");
            rejects(() -> coordinator.decideLoan(coordinator.review(Optional.of(handle)), LoanManagerPanel.OWNER, "legacy", false));
            rejects(() -> coordinator.submitLoan(coordinator.review(Optional.of(handle)), LoanManagerPanel.OWNER, "bypass", null, null, null, null, null));
            rejects(() -> coordinator.executeLoan(coordinator.review(Optional.of(handle)), "legacy", null));
            // A separate binding with only a persisted UNKNOWN record blocks generic context before qualification.
            check(coordinator.confirm(coordinator.review(Optional.empty()), CareerAssociationCoordinator.Action.CREATE_NEW, Optional.empty()).equals(handle), "repeated create reuses identity rather than bypassing its history");
            Path otherSource = Files.copy(source, saves.resolve("independent.s22")); load(otherSource, primary, secondary);
            var second = coordinator.confirm(coordinator.review(Optional.empty()), CareerAssociationCoordinator.Action.CREATE_NEW, Optional.empty());
            var secondStore = new LoanContinuationStore(continuation, second);
            var partial = LoanContinuationStoreSelfTest.request(second, "partial", 2, current.decoded().bytes());
            var partialClaim = secondStore.claim(partial, EMPTY, Map.of());
            var unknown = secondStore.complete(partial, partialClaim.token(), LoanContinuationStore.State.UNKNOWN);
            rejects(() -> coordinator.confirm(coordinator.review(Optional.of(second)), CareerAssociationCoordinator.Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            rejects(() -> qualify(coordinator.review(Optional.of(second)), unknown));
            load(source, primary, secondary);
            // An unrelated financial receipt blocks ALL new-loan review, even for a different owner/key/player.
            var unrelated = new CareerCommandJournal(journals, new Key(handle.career(), handle.binding(), "br.unrelated.finance"));
            var unrelatedIntent = new CareerCommandJournal.Intent("finance", CareerCommandGate.Operation.FINANCE_CREDIT, "e".repeat(64));
            var pending = unrelated.prepare(current.decoded().bytes(), EMPTY, unrelatedIntent);
            unrelated.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, pending.snapshot().token());
            rejects(() -> qualify(coordinator.review(Optional.of(handle)), emptyLedger));
            check(Arrays.equals(Files.readAllBytes(source), new byte[]{4, 5, 6, 7}), "all coordinator tests leave original save bytes unchanged");
        }
        System.out.println("LoanContinuationCoordinatorSelfTest: " + checks + " checks passed");
    }
}
