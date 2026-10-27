package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Proves complete metadata aggregation, not that synthetic saves contain any game effects. */
public final class GroupedLoanHistorySelfTest {
    static int checks;
    interface Action { void run() throws Exception; }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static void refuses(Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { checks++; return; }
        throw new AssertionError("Expected safe refusal");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "grouped-history-");
        Path saves = Files.createDirectory(root.resolve("sav")), source = Files.write(saves.resolve("owned.s22"), new byte[]{1,2,3});
        var observations = new java.util.concurrent.atomic.AtomicInteger();
        var observer = new SaveFileObserver(path -> observations.incrementAndGet()); var observed = observer.observe(saves, "owned.s22");
        var associations = new CareerSaveAssociations(root.resolve("associations"));
        var create = CareerSaveAssociations.class.getDeclaredMethod("createNew", SaveFileObserver.Observation.class, Optional.class, CareerSaveAssociations.Confirmation.class);
        create.setAccessible(true);
        var members = new ArrayList<CareerSaveAssociations.Association>();
        for (int i = 0; i < 4; i++) members.add((CareerSaveAssociations.Association)create.invoke(associations, observed, Optional.empty(), CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER));
        var journals = new CareerSidecarStore(root.resolve("journals")); var loans = new CareerSidecarStore(root.resolve("loan-requests"));
        var finance = new CareerSidecarStore(root.resolve("finance-requests")); var execution = new CareerSidecarStore(root.resolve("finance-execution-witnesses"));
        var continuation = new CareerSidecarStore(root.resolve("loan-continuation"));
        for (int i = 0; i < 4; i++) {
            var member = members.get(i); var request = LoanContinuationStoreSelfTest.request(member.handle(), "history" + i, i + 1, observed.revision());
            var ledger = new LoanContinuationStore(continuation, member.handle()); var claim = ledger.claim(request, EMPTY, Map.of());
            ledger.complete(request, claim.token(), i < 2 ? LoanContinuationStore.State.APPLIED : LoanContinuationStore.State.NO_EFFECT);
        }
        var group = associations.consolidate(observed); Object primary = new Object(), secondary = new Object();
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        var coordinator = new CareerAssociationCoordinator(saves, associations, journals, finance, execution, loans, continuation, observer, verifier, List.of(LoanManagerPanel.OWNER));
        try (var hook = HookProbe.attachLifecycle(new CareerLifecycle())) {
            HookProbe.enterLoad("owned.s22", false); HookProbe.loadSource(source.toString());
            var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(source))); trace.readAllBytes(); trace.close();
            HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            var review = coordinator.review(Optional.of(group.primary()));
            var ledger = new LoanContinuationStore(continuation, group.primary()).read();
            var method = CareerAssociationCoordinator.class.getDeclaredMethod("nativeHistory", CareerAssociationCoordinator.Review.class, LoanContinuationStore.Snapshot.class);
            method.setAccessible(true); int beforeObservations = observations.get(); Object history = method.invoke(coordinator, review, ledger);
            check(observations.get() - beforeObservations == 1, "three alias reviews share save bytes and one final independent verification instead of nine observations");
            var all = history.getClass().getDeclaredMethod("all"); all.setAccessible(true);
            var active = history.getClass().getDeclaredMethod("active"); active.setAccessible(true);
            var sources = history.getClass().getDeclaredMethod("sources"); sources.setAccessible(true);
            check(((List<?>)all.invoke(history)).size() == 4, "all four historical ledgers are mandatory");
            check(((List<?>)active.invoke(history)).size() == 2, "both old applied effects still require runtime tracker checks");
            check(((Map<?,?>)sources.invoke(history)).size() == 6, "three aliases retain identity and continuation tokens");
            @SuppressWarnings("unchecked") var applied = (List<LoanRequest>)active.invoke(history);
            @SuppressWarnings("unchecked") var reviewedSources = (Map<String,String>)sources.invoke(history);
            var baseline = new LoanBaselineStore(new CareerSidecarStore(root.resolve("loan-baselines")), group.primary());
            baseline.selectCurrentSave(observed.revision(), applied, reviewedSources);
            Object selected = method.invoke(coordinator, coordinator.review(Optional.of(group.primary())), ledger);
            check(((List<?>)all.invoke(selected)).size() == 4 && ((List<?>)active.invoke(selected)).isEmpty(), "explicit current-save selection keeps every tombstone while retiring selected active checks");
            var retired = selected.getClass().getDeclaredMethod("retired"); retired.setAccessible(true);
            check(((List<?>)retired.invoke(selected)).size() == 2, "retired players require separate exact-origin runtime checks, not unconditional bypass");
            check(((Map<?,?>)sources.invoke(selected)).size() == 7, "baseline decision is retained as a source token");
            var alias = group.members().stream().filter(a -> !a.handle().equals(group.primary())).findFirst().orElseThrow();
            refuses(() -> coordinator.review(Optional.of(alias.handle())));
            refuses(() -> coordinator.confirm(review, CareerAssociationCoordinator.Action.ACCEPT_REVISION, Optional.empty()));
            var aliasLedger = new LoanContinuationStore(continuation, alias.handle());
            var extra = LoanContinuationStoreSelfTest.request(alias.handle(), "unexpected", 88, observed.revision());
            aliasLedger.claim(extra, aliasLedger.read().token(), Map.of());
            refuses(() -> coordinator.review(Optional.of(group.primary())));
            check(Arrays.equals(Files.readAllBytes(source), new byte[]{1,2,3}), "no game/save effect executed by consolidation or metadata checks");
        }
        System.out.println("PASS: " + checks + " mandatory grouped loan history checks; synthetic metadata only");
    }
}
