package br.vbrasfoot;

/** Scalar synthetic histories. No original bytes or object graph represented by these cases. */
public final class KryoLedgerReferenceTraceSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        var trace = new KryoLedgerReferenceTrace();
        check(trace.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.MISSING, "no events never infer content/reference");
        var call = trace.beginField(); trace.marker(3, 17, 1, true); trace.contentReturned(true); trace.endField(call);
        var value = trace.snapshot();
        check(value.fieldResolution() == KryoLedgerReferenceTrace.Resolution.NEW_CONTENT_RETURNED && value.newMarkers() == 1 && value.contentReturns() == 1 && value.supportedResolver(), "new marker linked to returned body in exact field call");
        trace.marker(2, 17, 19, true);
        check(trace.snapshot().existingMarkers() == 1 && trace.snapshot().usesOutsideFieldCall() == 1, "alias beyond nx/club list observed as additional original reference use");
        var prior = new KryoLedgerReferenceTrace(); prior.marker(3, 9, 1, true); prior.contentReturned(true);
        var priorCall = prior.beginField(); prior.marker(2, 9, 11, true); prior.endField(priorCall);
        check(prior.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.PRIOR_CONTENT_REFERENCE && prior.snapshot().usesOutsideFieldCall() == 2, "field reference links to prior content observed outside field call");
        var missing = new KryoLedgerReferenceTrace(); var missingCall = missing.beginField(); missing.marker(2, 9, 11, true); missing.endField(missingCall);
        check(missing.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.PRIOR_REFERENCE_UNPROVEN, "preexisting table entry without body observation not accepted");
        var reset = new KryoLedgerReferenceTrace(); reset.marker(3, 1, 1, true); reset.contentReturned(true); reset.reset(); reset.resetCompleted();
        var resetCall = reset.beginField(); reset.marker(2, 1, 3, true); reset.endField(resetCall);
        check(reset.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.PRIOR_REFERENCE_UNPROVEN, "same numeric ID in another reset epoch never borrows prior body");
        var fresh = new KryoLedgerReferenceTrace(); fresh.marker(3, 1, 1, true); fresh.contentReturned(true); fresh.reset(); fresh.resetCompleted();
        var freshCall = fresh.beginField(); fresh.marker(3, 1, 1, true); fresh.contentReturned(true); fresh.endField(freshCall);
        check(fresh.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.NEW_CONTENT_RETURNED && fresh.snapshot().resets() == 1, "fresh marker may reuse ID after completed reset but must observe new content");
        var disabled = new KryoLedgerReferenceTrace(); var disabledCall = disabled.beginField(); disabled.contentReturned(true); disabled.endField(disabledCall);
        check(disabled.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNTRACKED_CONTENT_RETURNED && !disabled.snapshot().supportedResolver(), "body without reference marker is not invented table coverage");
        var duplicate = new KryoLedgerReferenceTrace();
        for (int i = 0; i < 2; i++) { var twice = duplicate.beginField(); if (i == 0) { duplicate.marker(3, 2, 1, true); duplicate.contentReturned(true); } else duplicate.marker(2, 2, 4, true); duplicate.endField(twice); }
        check(duplicate.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.AMBIGUOUS, "two original field invocations never collapse into unique encoding");
        var nested = new KryoLedgerReferenceTrace(); var nestedCall = nested.beginField(); nested.marker(3, 2, 1, true); nested.marker(2, 2, 4, true); nested.contentReturned(true); nested.endField(nestedCall);
        check(nested.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.AMBIGUOUS, "self/nested alias while body running does not become simple field encoding");
        var failure = new KryoLedgerReferenceTrace(); failure.beginField(); failure.marker(3, 1, 1, true);
        check(failure.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "marker without body return/field return remains incomplete");
        var resetFailure = new KryoLedgerReferenceTrace(); resetFailure.reset();
        check(resetFailure.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "reset without original completion stays unavailable");
        var during = new KryoLedgerReferenceTrace(); during.beginField(); during.reset(); during.resetCompleted();
        check(during.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "reset in active field call prevents causal matching");
        var custom = new KryoLedgerReferenceTrace(); custom.marker(3, 1, 1, false);
        check(custom.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE && !custom.snapshot().supportedResolver(), "custom resolver not accepted by positive ID/marker values");
        var bad = new KryoLedgerReferenceTrace(); bad.marker(2, 8, 999, true);
        check(bad.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "actual marker must equal original ID+2 for existing reference");
        var foreign = new KryoLedgerReferenceTrace(); foreign.endField(call);
        check(foreign.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "opaque call cannot cross trackers");
        var replay = new KryoLedgerReferenceTrace(); var replayCall = replay.beginField(); replay.marker(3, 3, 1, true); replay.contentReturned(true); replay.endField(replayCall); replay.endField(replayCall);
        check(replay.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.UNAVAILABLE, "one field call completion cannot replay");
        var capacity = new KryoLedgerReferenceTrace(); for (int i = 0; i < KryoLedgerReferenceTrace.MAX_EVENTS + 1; i++) capacity.marker(2, 1, 3, true);
        check(capacity.snapshot().fieldResolution() == KryoLedgerReferenceTrace.Resolution.CAPACITY_EXCEEDED && capacity.snapshot().existingMarkers() <= KryoLedgerReferenceTrace.MAX_EVENTS, "overflow is explicit and bounded, never partial clean result");
        System.out.println("PASS: " + checks + " ledger reference trace checks; own scalar histories, no operation inclusion or exclusive graph ownership.");
    }
}
