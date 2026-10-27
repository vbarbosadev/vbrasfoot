package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Synthetic coordinator review regression: ended legacy finance must not poison the next sponsor's loan view. */
public final class SponsorContinuationReviewSelfTest {
    static int checks;
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsor-continuation-review-");
        Path saves = Files.createDirectory(root.resolve("sav")); Path save = Files.write(saves.resolve("owned.s22"), new byte[]{1,2,3});
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(root.resolve("associations"));
        var observed = observer.observe(saves, "owned.s22");
        var association = associations.create(observed, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
        var handle = association.handle(); Object primary = new Object(), secondary = new Object();
        var journalDisk = new CareerSidecarStore(root.resolve("journals")); var requestDisk = new CareerSidecarStore(root.resolve("finance-requests"));
        var executionDisk = new CareerSidecarStore(root.resolve("finance-execution-witnesses"));
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        var coordinator = new CareerAssociationCoordinator(saves, associations, journalDisk, requestDisk, executionDisk,
                new CareerSidecarStore(root.resolve("loan-requests")), new CareerSidecarStore(root.resolve("loan-continuation")),
                observer, verifier, List.of(SponsorActivationStore.OWNER));
        var ledger = new SponsorActivationStore(new CareerSidecarStore(root.resolve("sponsor-activations")), handle);
        UUID sponsor = UUID.randomUUID(); var request = SponsorActivationStoreSelfTest.request(handle, sponsor, observed.revision());
        try (var lifecycle = HookProbe.attachLifecycle(new CareerLifecycle()); var gate = new CareerCommandGate()) {
            HookProbe.enterLoad("owned", false); HookProbe.loadSource(save.toString());
            var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(save))); trace.readAllBytes(); trace.close();
            HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary);
            HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            var context = gate.installContext(handle.career(), handle.binding(), 1, observed.revision(), primary, secondary);
            var owner = gate.owner(SponsorActivationStore.OWNER, Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var key = new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER);
            var pipeline = new SponsorActivationPipeline(gate, new CareerCommandJournal(journalDisk, key), new FinanceRequestStore(requestDisk, key),
                    new FinanceExecutionWitnessStore(executionDisk, key), ledger, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            var handler = new CareerCommandDispatcher.Handler() {
                public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
                public Optional<FinanceRequest> persistentRequest() { return Optional.of(request); }
                public boolean supportsExecutionWitness() { return true; }
                public CareerCommandGate.Status apply() { return CareerCommandGate.Status.APPLIED; }
            };
            pipeline.execute(sponsor, "a".repeat(64), owner, context, request, EMPTY, EMPTY, EMPTY, EMPTY, handler, "Original", "Sponsored");
            var method = CareerAssociationCoordinator.class.getDeclaredMethod("nativeHistory", CareerAssociationCoordinator.Review.class,
                    LoanContinuationStore.Snapshot.class, boolean.class); method.setAccessible(true);
            var continuation = new LoanContinuationStore(new CareerSidecarStore(root.resolve("loan-continuation")), handle).read();
            try { method.invoke(coordinator, coordinator.review(Optional.of(handle)), continuation, true); throw new AssertionError("Active sponsor ignored"); }
            catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "active sponsor blocks continuation"); }
            var ending = new SponsorActivationStore.EndAttempt(UUID.randomUUID(), observed.revision(), "Sponsored", request.before(), SponsorActivationStore.EndState.CLAIMED);
            var endClaim = ledger.claimEnd(sponsor, ending, ledger.read().token());
            ledger.completeEnd(endClaim.lease(), endClaim.snapshot().token(), SponsorActivationStore.EndState.ENDED);
            Object history = method.invoke(coordinator, coordinator.review(Optional.of(handle)), continuation, true);
            check(history != null, "coordinator accepts exact ended legacy finance for distinct sponsor without reanchoring");
            check(new FinanceRequestStore(requestDisk, key).read(observed.revision()).requests().equals(List.of(request)), "old financial payload preserved");
            try { method.invoke(coordinator, coordinator.review(Optional.of(handle)), continuation, false); throw new AssertionError("Generic continuation bypass"); }
            catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "generic loan path remains conservative"); }
            // Add an unaccounted pending credit to the legacy finance journal; filtering must never hide it.
            var journal = new CareerCommandJournal(journalDisk, key); var current = journal.read(observed.revision());
            journal.prepare(observed.revision(), current.token(), new CareerCommandJournal.Intent("foreign_pending", CareerCommandGate.Operation.FINANCE_CREDIT, "e".repeat(64)));
            try { method.invoke(coordinator, coordinator.review(Optional.of(handle)), continuation, true); throw new AssertionError("Pending unrelated finance ignored"); }
            catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "unaccounted pending finance still blocks new sponsor"); }
        }
        System.out.println("PASS: " + checks + " coordinator sponsor continuation review checks; synthetic only. " + root);
    }
}
