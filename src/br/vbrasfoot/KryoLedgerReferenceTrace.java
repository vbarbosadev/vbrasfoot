package br.vbrasfoot;

import java.util.*;

/** One exact ledger's scalar reference history in a writer. No objects, paths, field values or persistent IDs. */
final class KryoLedgerReferenceTrace {
    static final int MAX_EVENTS = 128;
    enum Resolution { MISSING, NEW_CONTENT_RETURNED, PRIOR_CONTENT_REFERENCE, PRIOR_REFERENCE_UNPROVEN,
        UNTRACKED_CONTENT_RETURNED, AMBIGUOUS, UNAVAILABLE, CAPACITY_EXCEEDED }
    record Summary(Resolution fieldResolution, int newMarkers, int existingMarkers, int contentReturns,
                   int usesOutsideFieldCall, int resets, int fieldCalls, boolean supportedResolver) {
        static Summary missing() { return new Summary(Resolution.MISSING, 0, 0, 0, 0, 0, 0, false); }
    }
    private record Key(int epoch, int id) { }
    static final class Call {
        private final UUID owner;
        private final int sequence, markers, bodies, epoch;
        private Call(UUID owner, int sequence, int markers, int bodies, int epoch) { this.owner = owner; this.sequence = sequence; this.markers = markers; this.bodies = bodies; this.epoch = epoch; }
    }
    private final UUID owner = UUID.randomUUID();
    private final Map<Key, Boolean> definitions = new HashMap<>();
    private int epoch, events, news, existing, bodies, outside, calls, finished;
    private boolean failed, exceeded, supported = true, resolverObserved, resetting;
    private Call active;
    private Key pending;
    private int lastKind = -1;
    private boolean lastLinked;
    private Resolution resolution = Resolution.MISSING;
    private boolean event() { if (events >= MAX_EVENTS) { exceeded = true; return false; } events++; return true; }
    Call beginField() {
        if (!event()) return null;
        if (active != null) { failed = true; return null; }
        calls++; active = new Call(owner, calls, news + existing, bodies, epoch); return active;
    }
    void marker(int kind, int id, int encoded, boolean supportedResolver) {
        if (!event()) return;
        resolverObserved = true; supported &= supportedResolver;
        if (!supportedResolver || id < 0 || kind != 2 && kind != 3 || encoded != (kind == 2 ? id + 2 : 1) || kind == 2 && id > Integer.MAX_VALUE - 2) { failed = true; return; }
        if (active == null) outside++;
        var key = new Key(epoch, id); lastKind = kind; lastLinked = false;
        if (kind == 3) {
            news++;
            if (pending != null || definitions.putIfAbsent(key, false) != null) { failed = true; return; }
            pending = key;
        } else {
            existing++; lastLinked = Boolean.TRUE.equals(definitions.get(key));
        }
    }
    void contentReturned(boolean supportedSerializer) {
        if (!event()) return;
        if (active == null) outside++;
        bodies++;
        if (!supportedSerializer) failed = true;
        if (pending != null) { definitions.put(pending, true); pending = null; }
    }
    void endField(Call call) {
        if (call == null || !owner.equals(call.owner) || active != call || call.sequence != calls) { failed = true; return; }
        active = null; finished++;
        int markers = news + existing - call.markers, content = bodies - call.bodies;
        Resolution current = call.epoch != epoch ? Resolution.UNAVAILABLE
                : markers == 1 && lastKind == 3 && content == 1 && pending == null ? Resolution.NEW_CONTENT_RETURNED
                : markers == 1 && lastKind == 2 && content == 0 ? lastLinked ? Resolution.PRIOR_CONTENT_REFERENCE : Resolution.PRIOR_REFERENCE_UNPROVEN
                : markers == 0 && content == 1 ? Resolution.UNTRACKED_CONTENT_RETURNED : Resolution.AMBIGUOUS;
        resolution = finished == 1 ? current : Resolution.AMBIGUOUS;
    }
    void reset() { if (!event()) return; if (active != null || pending != null || resetting) failed = true; epoch++; pending = null; resetting = true; }
    void resetCompleted() { if (!resetting) failed = true; resetting = false; }
    void fail() { failed = true; }
    Summary snapshot() {
        var status = exceeded ? Resolution.CAPACITY_EXCEEDED : failed || active != null || pending != null || resetting || calls != finished ? Resolution.UNAVAILABLE : resolution;
        return new Summary(status, news, existing, bodies, outside, epoch, calls, resolverObserved && supported);
    }
}
