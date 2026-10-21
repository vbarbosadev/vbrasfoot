package br.vbrasfoot;

import java.util.*;
import java.util.function.Function;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Pure explanation of separately reviewed metadata roots. Not a transaction, migration plan or authority. */
final class FinanceMultiRootRecoveryReview {
    enum Root { JOURNAL, CONTRACTS, EXECUTION }
    enum Gap { ASSOCIATION_REVISION_DIFFERS, ROOT_REVIEW_UNAVAILABLE, REVIEW_REVISION_DIFFERS,
        ROOT_ANCHORS_DIFFER, JOURNAL_ANCHOR_DIFFERS, CONTRACT_ANCHOR_DIFFERS, EXECUTION_ANCHOR_DIFFERS,
        HISTORICAL_OR_UNCERTAIN_RECEIPTS, CONTRACT_LINKS_UNRESOLVED, EXECUTION_NOT_NO_EFFECT,
        SERIALIZATION_OBSERVATIONS_PRESENT, COORDINATED_COMMIT_UNPROVEN }
    record Owner(String owner, Map<Root, String> tokens, Map<Root, SaveRevision> anchors, Set<Gap> gaps) {
        Owner { tokens = Map.copyOf(tokens); anchors = Map.copyOf(anchors); gaps = Set.copyOf(gaps); }
        boolean automaticReanchorAllowed() { return false; }
        boolean operationInclusionProven() { return false; }
        boolean writeAuthority() { return false; }
    }
    private FinanceMultiRootRecoveryReview() { }
    static List<Owner> inspect(SaveRevision association, SaveRevision observed,
            List<CareerAssociationCoordinator.JournalReview> journals, List<CareerAssociationCoordinator.RequestReview> contracts,
            List<CareerAssociationCoordinator.ExecutionReview> executions) {
        Objects.requireNonNull(association); Objects.requireNonNull(observed);
        var js = index(journals, CareerAssociationCoordinator.JournalReview::owner);
        var cs = index(contracts, CareerAssociationCoordinator.RequestReview::owner);
        var es = index(executions, CareerAssociationCoordinator.ExecutionReview::owner);
        var owners = new TreeSet<String>(); owners.addAll(js.keySet()); owners.addAll(cs.keySet()); owners.addAll(es.keySet());
        if (owners.size() > MAX_BINDING_NAMESPACES) throw new IllegalArgumentException("Bounded complete owner inventory required");
        var result = new ArrayList<Owner>();
        for (String owner : owners) {
            var gaps = EnumSet.of(Gap.COORDINATED_COMMIT_UNPROVEN);
            if (!association.equals(observed)) gaps.add(Gap.ASSOCIATION_REVISION_DIFFERS);
            var tokens = new EnumMap<Root, String>(Root.class); var anchors = new EnumMap<Root, SaveRevision>(Root.class);
            var j = js.get(owner); var c = cs.get(owner); var e = es.get(owner);
            var journal = j == null || j.failure().isPresent() ? Optional.<CareerCommandJournal.Recovery>empty() : j.recovery();
            var contract = c == null || c.failure().isPresent() ? Optional.<FinanceRequestStore.Recovery>empty() : c.recovery();
            var execution = e == null || e.failure().isPresent() ? Optional.<FinanceExecutionWitnessStore.Recovery>empty() : e.recovery();
            if (journal.isEmpty() || contract.isEmpty() || execution.isEmpty()) gaps.add(Gap.ROOT_REVIEW_UNAVAILABLE);
            journal.ifPresent(value -> {
                root(tokens, anchors, Root.JOURNAL, value.snapshot().token(), value.recordedSave(), value.observedSave(), observed, gaps, Gap.JOURNAL_ANCHOR_DIFFERS);
                if (value.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS || value.status() == CareerCommandJournal.RecoveryStatus.UNCERTAIN)
                    gaps.add(Gap.HISTORICAL_OR_UNCERTAIN_RECEIPTS);
            });
            contract.ifPresent(value -> root(tokens, anchors, Root.CONTRACTS, value.snapshot().token(), value.recordedSave(), value.observedSave(), observed, gaps, Gap.CONTRACT_ANCHOR_DIFFERS));
            execution.ifPresent(value -> {
                root(tokens, anchors, Root.EXECUTION, value.snapshot().token(), value.recordedSave(), value.observedSave(), observed, gaps, Gap.EXECUTION_ANCHOR_DIFFERS);
                if (!value.snapshot().serializations().isEmpty()) gaps.add(Gap.SERIALIZATION_OBSERVATIONS_PRESENT);
            });
            if (new HashSet<>(anchors.values()).size() > 1) gaps.add(Gap.ROOT_ANCHORS_DIFFER);
            if (journal.isPresent() && contract.isPresent()
                    && FinanceContractReview.compare(contract.orElseThrow().snapshot(), journal.orElseThrow().snapshot()).stream().anyMatch(FinanceContractReview.Link::blocksContext))
                gaps.add(Gap.CONTRACT_LINKS_UNRESOLVED);
            if (journal.isPresent() && execution.isPresent()) {
                var history = journal.orElseThrow().snapshot();
                for (var operation : FinanceExecutionRecovery.inspect(history, execution.orElseThrow().snapshot())) {
                    boolean legacyNoEffect = operation.status() == FinanceExecutionRecovery.Status.NO_EXECUTION_WITNESS
                            && history.receipts().stream().anyMatch(value -> value.operationId().equals(operation.operationId()) && value.intent().equals(operation.intent())
                            && value.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED);
                    if (operation.status() != FinanceExecutionRecovery.Status.NO_EFFECT_CONFIRMED && !legacyNoEffect) gaps.add(Gap.EXECUTION_NOT_NO_EFFECT);
                }
            }
            result.add(new Owner(owner, tokens, anchors, gaps));
        }
        return List.copyOf(result);
    }
    private static void root(Map<Root, String> tokens, Map<Root, SaveRevision> anchors, Root root, String token,
            SaveRevision recorded, SaveRevision reviewed, SaveRevision observed, Set<Gap> gaps, Gap changed) {
        if (!EMPTY.equals(token) && (token == null || !token.matches("[a-f0-9]{64}"))) throw new IllegalArgumentException("Invalid reviewed HEAD token");
        tokens.put(root, token);
        if (!reviewed.equals(observed)) gaps.add(Gap.REVIEW_REVISION_DIFFERS);
        // An absent namespace's observedSave fallback is not a committed anchor or durable empty-root proof.
        if (!EMPTY.equals(token)) { anchors.put(root, recorded); if (!recorded.equals(observed)) gaps.add(changed); }
    }
    private static <T> Map<String, T> index(List<T> values, Function<T, String> owner) {
        if (values.size() > MAX_BINDING_NAMESPACES) throw new IllegalArgumentException("Bounded root inventory required");
        var result = new HashMap<String, T>();
        for (var value : values) {
            String name = owner.apply(value); new Key(new UUID(0, 0), new UUID(0, 0), name);
            if (result.put(name, value) != null) throw new IllegalArgumentException("Duplicate root owner");
        }
        return result;
    }
}
