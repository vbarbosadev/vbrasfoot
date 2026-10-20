package br.vbrasfoot;

import java.util.*;

/** Direct-write checkpoint comparison only. No claim about uninstrumented writes, financial causality or save inclusion. */
final class FinanceMutationAssessment {
    enum Status { UNAVAILABLE, OBSERVED_INTERFERENCE, NO_OBSERVED_DIRECT_WRITE }
    record Report(Status executionToSerializer, Status serializerWindow) { }
    private FinanceMutationAssessment() { }
    static Report assess(Optional<FinanceMutationTracker.Snapshot> executionEnd, WrittenSaveProvenance.Binding written) {
        Objects.requireNonNull(executionEnd); Objects.requireNonNull(written);
        return new Report(compare(executionEnd, written.financeBefore()), compare(written.financeBefore(), written.financeAfter()));
    }
    private static Status compare(Optional<FinanceMutationTracker.Snapshot> before, Optional<FinanceMutationTracker.Snapshot> after) {
        if (before.isEmpty() || after.isEmpty()) return Status.UNAVAILABLE;
        var first = before.orElseThrow(); var last = after.orElseThrow();
        if (!first.tracker().equals(last.tracker()) || !first.definingClassesObserved() || !last.definingClassesObserved()
                || !first.healthy() || !last.healthy()) return Status.UNAVAILABLE;
        return last.stableSince(first) ? Status.NO_OBSERVED_DIRECT_WRITE : Status.OBSERVED_INTERFERENCE;
    }
}
