package br.vbrasfoot;

import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;

/** Own target samplers only. No INCLUDED state, persistent witness mutation, public command or original graph readiness. */
public final class FinanceSerializationTrackerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static CareerCommandJournal.Intent intent(String key) { return new CareerCommandJournal.Intent(key, CareerCommandGate.Operation.FINANCE_CREDIT, "a".repeat(64)); }
    private static <T> T edt(java.util.concurrent.Callable<T> action) throws Exception { return CareerBridge.onEdt(action); }
    public static void main(String[] args) throws Exception {
        Object primary = new Object(); var loader = new ClassLoader() { };
        var mutations = new FinanceMutationTracker(); mutations.instrumented("best.ah", loader); mutations.instrumented("best.C", loader);
        var checkpoint = mutations.snapshot(loader); var sampler = new AtomicReference<>(FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE);
        FinanceSerializationTracker.Target target = root -> root == primary ? sampler.get() : FinanceSerializationTracker.Sample.ROOT_MISMATCH;
        var tracker = new FinanceSerializationTracker(); UUID operation = UUID.randomUUID();
        tracker.observe(operation, intent("once"), target, checkpoint);
        var window = edt(() -> tracker.begin(primary, checkpoint));
        var capture = edt(() -> tracker.finish(window, primary));
        check(capture.validWindow() && !capture.capacityExceeded() && capture.operations().size() == 1, "bounded exact operation is captured around serialization window");
        var captured = capture.operations().get(0);
        check(captured.operationId().equals(operation) && captured.intent().equals(intent("once")), "fresh operation UUID/intent preserved, not inferred from balance");
        check(captured.before() == FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE && captured.after() == captured.before()
                && captured.executionToSerializer() == FinanceSerializationTracker.Continuity.NO_OBSERVED_DIRECT_WRITE, "matched samples are only bounded diagnostic facts");
        check(!edt(() -> tracker.finish(window, primary)).validWindow(), "window is one-shot");
        check(!new FinanceSerializationTracker().finish(window, primary).validWindow(), "foreign tracker cannot consume window");
        for (var sample : FinanceSerializationTracker.Sample.values()) {
            if (sample == FinanceSerializationTracker.Sample.WRONG_THREAD) continue;
            sampler.set(sample);
            var next = edt(() -> tracker.begin(primary, checkpoint));
            var result = edt(() -> tracker.finish(next, primary));
            check(result.operations().get(0).before() == sample && result.operations().get(0).after() == sample, "sampler refusal/status retained " + sample);
        }
        sampler.set(FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE);
        var changeBetween = edt(() -> tracker.begin(primary, checkpoint)); sampler.set(FinanceSerializationTracker.Sample.LEDGER_MISMATCH);
        check(edt(() -> tracker.finish(changeBetween, primary)).operations().get(0).after() == FinanceSerializationTracker.Sample.LEDGER_MISMATCH,
                "ledger replacement during serializer interval is not hidden by initial sample");
        var wrongThread = tracker.begin(primary, checkpoint);
        check(tracker.finish(wrongThread, primary).operations().get(0).before() == FinanceSerializationTracker.Sample.WRONG_THREAD, "off-EDT boundaries never read live game target");
        var beforeReset = edt(() -> tracker.begin(primary, checkpoint)); tracker.invalidate();
        check(!edt(() -> tracker.finish(beforeReset, primary)).validWindow(), "load/root/detach invalidation rejects in-flight window");
        check(edt(() -> tracker.finish(tracker.begin(primary, checkpoint), primary)).operations().isEmpty(), "invalidation clears old operation identities, never rediscover by value");
        tracker.observe(operation, intent("once"), target, checkpoint);
        var beforeNew = edt(() -> tracker.begin(primary, checkpoint));
        tracker.observe(UUID.randomUUID(), intent("another"), target, checkpoint);
        check(!edt(() -> tracker.finish(beforeNew, primary)).validWindow(), "new registration changes window revision");
        var wrongRoot = edt(() -> tracker.begin(primary, checkpoint));
        check(!edt(() -> tracker.finish(wrongRoot, new Object())).validWindow(), "different root identity does not complete existing window");
        check(!edt(() -> tracker.finish(wrongRoot, primary)).validWindow(), "root mismatch consumes window and cannot be retried with another identity");
        mutations.begin(); mutations.end();
        var interference = edt(() -> tracker.finish(tracker.begin(primary, mutations.snapshot(loader)), primary));
        check(interference.operations().stream().allMatch(value -> value.executionToSerializer() == FinanceSerializationTracker.Continuity.OBSERVED_INTERFERENCE),
                "equal-value/any selected direct write invalidates execution checkpoint");
        var missingCoverage = edt(() -> tracker.finish(tracker.begin(primary, new FinanceMutationTracker().snapshot(loader)), primary));
        check(missingCoverage.operations().stream().allMatch(value -> value.executionToSerializer() == FinanceSerializationTracker.Continuity.UNAVAILABLE), "missing/mismatched mutation tracker is unavailable, not clean");
        tracker.close(); check(tracker.begin(primary, checkpoint) == null, "closed tracker never captures targets");
        var throwsTracker = new FinanceSerializationTracker(); FinanceSerializationTracker.Target throwsTarget = root -> { throw new IllegalStateException("Owned getter failure"); };
        throwsTracker.observe(UUID.randomUUID(), intent("throws"), throwsTarget, checkpoint);
        var failed = edt(() -> throwsTracker.finish(throwsTracker.begin(primary, checkpoint), primary));
        check(failed.operations().get(0).before() == FinanceSerializationTracker.Sample.READ_FAILED && failed.operations().get(0).after() == FinanceSerializationTracker.Sample.READ_FAILED,
                "target read failure never interrupts original serialization or becomes positive sample");
        var overflow = new FinanceSerializationTracker();
        for (int i = 0; i < FinanceSerializationTracker.MAX_OPERATIONS + 1; i++) overflow.observe(UUID.randomUUID(), intent("key_" + i), target, checkpoint);
        var bounded = edt(() -> overflow.finish(overflow.begin(primary, checkpoint), primary));
        check(bounded.capacityExceeded() && bounded.operations().size() == FinanceSerializationTracker.MAX_OPERATIONS, "capacity exhaustion is explicit, never clean partial coverage");
        var duplicate = new FinanceSerializationTracker(); duplicate.observe(operation, intent("once"), target, checkpoint); duplicate.observe(operation, intent("changed"), target, checkpoint);
        var duplicated = edt(() -> duplicate.finish(duplicate.begin(primary, checkpoint), primary));
        check(duplicated.capacityExceeded() && duplicated.operations().get(0).intent().equals(intent("once")), "ambiguous duplicate cannot overwrite original operation observation");
        var samples = new AtomicInteger();
        var membership = new AtomicReference<>(FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST);
        FinanceSerializationTracker.Target qualifiedTarget = new FinanceSerializationTracker.Target() {
            public FinanceSerializationTracker.Sample sample(Object root) { throw new AssertionError("Legacy callback must not trigger a second membership scan"); }
            public FinanceSerializationTracker.SampleFact sampleFact(Object root) {
                samples.incrementAndGet();
                return new FinanceSerializationTracker.SampleFact(membership.get() == FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST
                        ? FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE : FinanceSerializationTracker.Sample.LEDGER_MISMATCH, Optional.of(membership.get()));
            }
        };
        var qualified = new FinanceSerializationTracker(); qualified.observe(UUID.randomUUID(), intent("qualified"), qualifiedTarget, checkpoint);
        var qualifiedWindow = edt(() -> qualified.begin(primary, checkpoint)); membership.set(FinanceLedgerMembership.Status.SHARED_LEDGER);
        var qualifiedCapture = edt(() -> qualified.finish(qualifiedWindow, primary)).operations().get(0);
        check(samples.get() == 2, "membership factual samples captured once before and once after, no duplicate scan");
        check(qualifiedCapture.qualifications().version() == 1
                && qualifiedCapture.qualifications().membershipBefore().orElseThrow() == FinanceLedgerMembership.Status.UNIQUE_IN_CLUB_LIST
                && qualifiedCapture.qualifications().membershipAfter().orElseThrow() == FinanceLedgerMembership.Status.SHARED_LEDGER,
                "versioned manifest preserves independent before/after membership facts including negative after");
        check(qualifiedCapture.after() == FinanceSerializationTracker.Sample.LEDGER_MISMATCH && qualifiedCapture.qualifications().serializerAfterReference().isEmpty(),
                "root sample and absent profile are not upgraded by before membership success");
        var invalidatedQualification = edt(() -> qualified.begin(primary, checkpoint)); qualified.invalidate();
        check(!edt(() -> qualified.finish(invalidatedQualification, primary)).validWindow(), "qualification facts cannot resurrect invalidated window");
        java.lang.ref.Reference.reachabilityFence(qualifiedTarget);
        java.lang.ref.Reference.reachabilityFence(target); java.lang.ref.Reference.reachabilityFence(throwsTarget);
        System.out.println("PASS: " + checks + " operation serializer capture checks; own samplers only, no field-level serialization or inclusion proof.");
    }
}
