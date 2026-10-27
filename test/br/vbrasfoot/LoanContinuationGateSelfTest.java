package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerCommandGate.*;

/** Synthetic gate/storage interlock tests; does not qualify a production context. */
public final class LoanContinuationGateSelfTest {
    private static int checks;
    interface Call { void run() throws Exception; }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void blocked(Call call) throws Exception {
        try { call.run(); } catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected gate refusal");
    }
    public static void main(String[] args) throws Exception {
        Object primary = new Object(), secondary = new Object();
        var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        var save = new CareerSidecarStore.SaveRevision("a".repeat(64), 12);
        var request = LoanContinuationStoreSelfTest.request(handle, "native", 1, save);
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loan-gate-test-");
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner(LoanManagerPanel.OWNER, Set.of(Operation.LOAN_ACCEPT));
            var context = gate.installContext(handle.career(), handle.binding(), 1, save, primary, secondary);
            var permit = gate.reserve(owner, new Request(context.token(), request.key(), Operation.LOAN_ACCEPT, request.digest()), primary, secondary).permit().orElseThrow();
            gate.retainDispatch(permit);
            var disk = new CareerSidecarStore(root, stage -> {
                // Check the REAL original I/O exclusion at both intent and terminal commits.
                if (stage == CareerSidecarStore.Stage.HEAD_COMMITTED) for (var kind : IoKind.values()) {
                    try { gate.beginOriginalIo(kind); throw new AssertionError("I/O escaped terminal hold"); }
                    catch (IllegalStateException expected) { checks++; }
                }
            });
            var store = new LoanContinuationStore(disk, handle);
            var execution = store.claim(request, CareerSidecarStore.EMPTY, Map.of());
            CareerBridge.onEdt(() -> { check(gate.begin(permit, primary, secondary).invocationGranted(), "owned effect begins once"); return null; });
            gate.finish(permit, Status.APPLIED, primary, secondary);
            store.complete(request, execution.token(), LoanContinuationStore.State.APPLIED);
            gate.releaseDispatch(permit);
            blocked(gate::prepareContext);
            blocked(() -> gate.prepareLoanContinuation(Set.of()));
            blocked(() -> gate.prepareLoanContinuation(Set.of(new AppliedLoan(LoanManagerPanel.OWNER, request.key(), "b".repeat(64)))));
            var exact = Set.of(new AppliedLoan(request.owner(), request.key(), request.digest()));
            var preparation = gate.prepareLoanContinuation(exact);
            check(gate.receipts().size() == 1 && gate.receipts().get(0).status() == Status.APPLIED, "narrow prepare never clears applied receipt");
            var fence = gate.beginOriginalIo(IoKind.SAVE); gate.endSave(fence);
            blocked(() -> gate.installPrepared(preparation, handle.career(), handle.binding(), 2, save, primary, secondary));
            var nextPreparation = gate.prepareLoanContinuation(exact);
            var nextContext = gate.installPrepared(nextPreparation, handle.career(), handle.binding(), 2, save, primary, secondary);
            check(gate.reserve(owner, new Request(nextContext.token(), request.key(), Operation.LOAN_ACCEPT, request.digest()), primary, secondary).permit().isEmpty(), "old key cannot replay after narrow preparation");
            var uncertain = gate.reserve(owner, new Request(nextContext.token(), "partial", Operation.LOAN_ACCEPT, "c".repeat(64)), primary, secondary).permit().orElseThrow();
            CareerBridge.onEdt(() -> { gate.begin(uncertain, primary, secondary); return null; });
            gate.finish(uncertain, Status.OUTCOME_UNKNOWN, primary, secondary);
            blocked(() -> gate.prepareLoanContinuation(Set.of(new AppliedLoan(request.owner(), "partial", "c".repeat(64)), exact.iterator().next())));
        }
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner("br.finance", Set.of(Operation.FINANCE_CREDIT));
            var context = gate.installContext(handle.career(), handle.binding(), 1, save, primary, secondary);
            var permit = gate.reserve(owner, new Request(context.token(), "financial", Operation.FINANCE_CREDIT, "d".repeat(64)), primary, secondary).permit().orElseThrow();
            CareerBridge.onEdt(() -> { gate.begin(permit, primary, secondary); return null; }); gate.finish(permit, Status.APPLIED, primary, secondary);
            blocked(() -> gate.prepareLoanContinuation(Set.of(new AppliedLoan("br.finance", "financial", "d".repeat(64)))));
        }
        System.out.println("LoanContinuationGateSelfTest: " + checks + " checks passed");
    }
}
