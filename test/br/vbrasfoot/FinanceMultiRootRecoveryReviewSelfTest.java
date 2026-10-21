package br.vbrasfoot;

import java.util.*;
import static br.vbrasfoot.FinanceMultiRootRecoveryReview.*;

/** Own immutable histories only. No files, decoder, UI window, migration or financial effects. */
public final class FinanceMultiRootRecoveryReviewSelfTest {
    private static int checks;
    private static final String OWNER = "br.owned.multiroot", TOKEN = "a".repeat(64);
    private static final CareerSidecarStore.SaveRevision OLD = new CareerSidecarStore.SaveRevision("b".repeat(64), 17), NOW = new CareerSidecarStore.SaveRevision("c".repeat(64), 19);
    private static final UUID ID = UUID.randomUUID();
    private static final CareerCommandJournal.Intent INTENT = new CareerCommandJournal.Intent("once", CareerCommandGate.Operation.FINANCE_CREDIT, "d".repeat(64));
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Runnable action, String label) { try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(label); }
    private static CareerAssociationCoordinator.JournalReview journal(String owner, String token, CareerSidecarStore.SaveRevision anchor,
            CareerSidecarStore.SaveRevision observed, CareerCommandJournal.State state) {
        var values = state == null ? List.<CareerCommandJournal.Receipt>of() : List.of(new CareerCommandJournal.Receipt(ID, INTENT, state));
        var status = state == null ? CareerCommandJournal.RecoveryStatus.EMPTY : state == CareerCommandJournal.State.NO_EFFECT_CONFIRMED
                ? CareerCommandJournal.RecoveryStatus.NO_EFFECT_ONLY : state == CareerCommandJournal.State.APPLIED
                ? CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS : CareerCommandJournal.RecoveryStatus.UNCERTAIN;
        return new CareerAssociationCoordinator.JournalReview(owner, true, Optional.of(new CareerCommandJournal.Recovery(new CareerCommandJournal.Snapshot(token, values), anchor, observed, status)), Optional.empty());
    }
    private static CareerAssociationCoordinator.RequestReview contracts(String owner, String token, CareerSidecarStore.SaveRevision anchor) {
        return new CareerAssociationCoordinator.RequestReview(owner, true, Optional.of(new FinanceRequestStore.Recovery(new FinanceRequestStore.Snapshot(token, List.of()), anchor, NOW)), Optional.empty(), List.of());
    }
    private static CareerAssociationCoordinator.ExecutionReview execution(String owner, String token, CareerSidecarStore.SaveRevision anchor, FinanceExecutionWitnessStore.State state) {
        var values = state == null ? List.<FinanceExecutionWitnessStore.Witness>of() : List.of(new FinanceExecutionWitnessStore.Witness(ID, INTENT, UUID.randomUUID(), 1, state));
        return new CareerAssociationCoordinator.ExecutionReview(owner, true, Optional.of(new FinanceExecutionWitnessStore.Recovery(new FinanceExecutionWitnessStore.Snapshot(token, values), anchor, NOW)), Optional.empty(), List.of());
    }
    private static Owner inspect(CareerSidecarStore.SaveRevision association, CareerAssociationCoordinator.JournalReview j,
            CareerAssociationCoordinator.RequestReview c, CareerAssociationCoordinator.ExecutionReview e) {
        var value = FinanceMultiRootRecoveryReview.inspect(association, NOW, List.of(j), List.of(c), List.of(e)).get(0);
        check(!value.automaticReanchorAllowed() && !value.operationInclusionProven() && !value.writeAuthority()
                && value.gaps().contains(Gap.COORDINATED_COMMIT_UNPROVEN), "every report is non-authoritative, including clean/no-effect/empty");
        return value;
    }
    public static void main(String[] args) {
        var j = journal(OWNER, TOKEN, NOW, NOW, null); var c = contracts(OWNER, TOKEN, NOW); var e = execution(OWNER, TOKEN, NOW, null);
        var aligned = inspect(NOW, j, c, e);
        check(aligned.gaps().equals(Set.of(Gap.COORDINATED_COMMIT_UNPROVEN)) && aligned.anchors().size() == 3 && aligned.tokens().size() == 3,
                "matching anchors and empty committed histories do not invent transaction readiness");
        var split = inspect(NOW, j, contracts(OWNER, TOKEN, OLD), execution(OWNER, TOKEN, OLD, null));
        check(split.gaps().containsAll(Set.of(Gap.ROOT_ANCHORS_DIFFER, Gap.CONTRACT_ANCHOR_DIFFERS, Gap.EXECUTION_ANCHOR_DIFFERS))
                && !split.gaps().contains(Gap.JOURNAL_ANCHOR_DIFFERS), "single journal reanchor does not hide contracts/execution at older revision");
        var old = inspect(OLD, journal(OWNER, TOKEN, OLD, NOW, null), contracts(OWNER, TOKEN, OLD), execution(OWNER, TOKEN, OLD, null));
        check(old.gaps().containsAll(Set.of(Gap.ASSOCIATION_REVISION_DIFFERS, Gap.JOURNAL_ANCHOR_DIFFERS, Gap.CONTRACT_ANCHOR_DIFFERS, Gap.EXECUTION_ANCHOR_DIFFERS))
                && !old.gaps().contains(Gap.ROOT_ANCHORS_DIFFER), "same old anchor is distinct from cross-root divergence and still not observed revision");
        var absent = inspect(NOW, journal(OWNER, CareerSidecarStore.EMPTY, NOW, NOW, null), contracts(OWNER, CareerSidecarStore.EMPTY, NOW), execution(OWNER, CareerSidecarStore.EMPTY, NOW, null));
        check(absent.anchors().isEmpty() && absent.tokens().size() == 3, "absent namespace's fallback observed revision is not a committed anchor");
        var failed = new CareerAssociationCoordinator.ExecutionReview(OWNER, true, Optional.empty(), Optional.of(CareerSidecarStore.Reason.CORRUPT), List.of());
        check(inspect(NOW, j, c, failed).gaps().contains(Gap.ROOT_REVIEW_UNAVAILABLE), "corrupt execution root cannot be treated as empty or clean");
        var stale = inspect(NOW, journal(OWNER, TOKEN, NOW, OLD, null), c, e);
        check(stale.gaps().contains(Gap.REVIEW_REVISION_DIFFERS), "reviews of different observed revisions cannot combine silently");
        var noEffect = inspect(NOW, journal(OWNER, TOKEN, NOW, NOW, CareerCommandJournal.State.NO_EFFECT_CONFIRMED), c, execution(OWNER, TOKEN, NOW, FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED));
        check(!noEffect.gaps().contains(Gap.HISTORICAL_OR_UNCERTAIN_RECEIPTS) && !noEffect.gaps().contains(Gap.EXECUTION_NOT_NO_EFFECT), "exact no-effect histories remain distinguished, not enabled for migration");
        var legacy = inspect(NOW, journal(OWNER, TOKEN, NOW, NOW, CareerCommandJournal.State.NO_EFFECT_CONFIRMED), c, e);
        check(!legacy.gaps().contains(Gap.EXECUTION_NOT_NO_EFFECT), "legacy no-effect receipt without witness retains existing conservative exception");
        var applied = inspect(NOW, journal(OWNER, TOKEN, NOW, NOW, CareerCommandJournal.State.APPLIED), c, execution(OWNER, TOKEN, NOW, FinanceExecutionWitnessStore.State.MEMORY_APPLIED));
        check(applied.gaps().containsAll(Set.of(Gap.HISTORICAL_OR_UNCERTAIN_RECEIPTS, Gap.CONTRACT_LINKS_UNRESOLVED, Gap.EXECUTION_NOT_NO_EFFECT)), "APPLIED/missing contract remains blocked despite equal anchors");
        var unknown = inspect(NOW, journal(OWNER, TOKEN, NOW, NOW, CareerCommandJournal.State.OUTCOME_UNKNOWN), c, execution(OWNER, TOKEN, NOW, FinanceExecutionWitnessStore.State.OUTCOME_UNKNOWN));
        check(unknown.gaps().containsAll(Set.of(Gap.HISTORICAL_OR_UNCERTAIN_RECEIPTS, Gap.EXECUTION_NOT_NO_EFFECT)), "unknown never resolved by comparing revisions");
        var orphan = inspect(NOW, j, c, execution(OWNER, TOKEN, NOW, FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED));
        check(orphan.gaps().contains(Gap.EXECUTION_NOT_NO_EFFECT), "orphan no-effect witness still conflicts without matching journal identity");
        var capture = new FinanceSerializationTracker.Operation(ID, INTENT, FinanceSerializationTracker.Sample.STATE_MISMATCH,
                FinanceSerializationTracker.Sample.STATE_MISMATCH, FinanceSerializationTracker.Continuity.UNAVAILABLE,
                new FinanceSerializationTracker.Fields(FinanceSerializationTracker.Emission.MISSING, 0, Set.of()), FinanceSerializationTracker.Edge.missing());
        var observation = new FinanceSerializationEvidence.Observation(UUID.randomUUID(), UUID.randomUUID().toString(), 1, 1, NOW, TOKEN, TOKEN, TOKEN, false,
                FinanceMutationAssessment.Status.UNAVAILABLE, List.of(new FinanceSerializationEvidence.Operation(capture, FinanceExecutionWitnessStore.State.MEMORY_APPLIED, CareerCommandJournal.State.APPLIED)));
        var observedExecution = new CareerAssociationCoordinator.ExecutionReview(OWNER, true, Optional.of(new FinanceExecutionWitnessStore.Recovery(
                new FinanceExecutionWitnessStore.Snapshot(TOKEN, execution(OWNER, TOKEN, NOW, FinanceExecutionWitnessStore.State.MEMORY_APPLIED).recovery().orElseThrow().snapshot().witnesses(), List.of(observation)), NOW, NOW)), Optional.empty(), List.of());
        var observedWriter = inspect(NOW, journal(OWNER, TOKEN, NOW, NOW, CareerCommandJournal.State.APPLIED), c, observedExecution);
        check(observedWriter.gaps().containsAll(Set.of(Gap.SERIALIZATION_OBSERVATIONS_PRESENT, Gap.EXECUTION_NOT_NO_EFFECT)), "matching writer output hash does not authorize moving historical observations or applied execution");
        var partial = FinanceMultiRootRecoveryReview.inspect(NOW, NOW, List.of(j), List.of(), List.of());
        check(partial.size() == 1 && partial.get(0).gaps().contains(Gap.ROOT_REVIEW_UNAVAILABLE), "partial root inventory not completed by empty assumptions");
        var multi = FinanceMultiRootRecoveryReview.inspect(NOW, NOW, List.of(j), List.of(contracts("br.owned.absent", TOKEN, NOW)), List.of(e));
        check(multi.size() == 2 && multi.stream().allMatch(value -> value.gaps().contains(Gap.ROOT_REVIEW_UNAVAILABLE)), "owners from any reviewed root retained even when other roots omit them");
        rejects(() -> FinanceMultiRootRecoveryReview.inspect(NOW, NOW, List.of(j, j), List.of(c), List.of(e)), "duplicate owner rows refused");
        rejects(() -> inspect(NOW, journal(OWNER, "bad", NOW, NOW, null), c, e), "malformed reviewed HEAD token refused");
        var many = new ArrayList<CareerAssociationCoordinator.JournalReview>(); for (int i = 0; i < CareerSidecarStore.MAX_BINDING_NAMESPACES + 1; i++) many.add(journal("br.owned.owner" + i, TOKEN, NOW, NOW, null));
        rejects(() -> FinanceMultiRootRecoveryReview.inspect(NOW, NOW, many, List.of(c), List.of(e)), "oversized inventories refused instead of truncated");
        boolean immutable = false; try { aligned.anchors().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "anchor vector immutable");
        immutable = false; try { aligned.gaps().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "gap set immutable");
        check(FinanceMultiRootRecoveryReview.inspect(NOW, NOW, List.of(), List.of(), List.of()).isEmpty(), "empty supplied inventory does not discover other careers/namespaces");
        System.out.println("PASS: " + checks + " multi-root recovery review checks; own histories, no migration/transaction/authority.");
    }
}
