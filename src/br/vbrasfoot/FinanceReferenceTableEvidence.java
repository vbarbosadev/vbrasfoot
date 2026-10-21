package br.vbrasfoot;

import java.io.*;
import java.util.*;

/** Explicitly versioned scalar observations, never reference IDs or graph-wide/inclusion authority. */
record FinanceReferenceTableEvidence(int version, KryoLedgerReferenceTrace.Summary summary) {
    FinanceReferenceTableEvidence {
        Objects.requireNonNull(summary); Objects.requireNonNull(summary.fieldResolution());
        if (version != 1) throw new IllegalArgumentException("Unknown reference evidence version");
        int n = summary.newMarkers(), e = summary.existingMarkers(), b = summary.contentReturns(),
                outside = summary.usesOutsideFieldCall(), resets = summary.resets(), calls = summary.fieldCalls();
        if (n < 0 || e < 0 || b < 0 || outside < 0 || resets < 0 || calls < 0
                || (long) n + e + b + resets + calls > KryoLedgerReferenceTrace.MAX_EVENTS || outside > n + e + b)
            throw new IllegalArgumentException("Unbounded/inconsistent reference counts");
        var status = summary.fieldResolution();
        if (status == KryoLedgerReferenceTrace.Resolution.NEW_CONTENT_RETURNED && (calls != 1 || n < 1 || b < 1 || !summary.supportedResolver())
                || status == KryoLedgerReferenceTrace.Resolution.PRIOR_CONTENT_REFERENCE && (calls != 1 || n < 1 || e < 1 || b < 1 || outside < 2 || !summary.supportedResolver())
                || status == KryoLedgerReferenceTrace.Resolution.PRIOR_REFERENCE_UNPROVEN && (calls != 1 || e < 1 || !summary.supportedResolver())
                || status == KryoLedgerReferenceTrace.Resolution.UNTRACKED_CONTENT_RETURNED && (calls != 1 || b < 1)
                || status == KryoLedgerReferenceTrace.Resolution.MISSING && calls != 0)
            throw new IllegalArgumentException("Reference resolution contradicts scalar observations");
    }
    void write(DataOutputStream out) throws IOException {
        out.writeInt(version); out.writeUTF(summary.fieldResolution().name());
        out.writeInt(summary.newMarkers()); out.writeInt(summary.existingMarkers()); out.writeInt(summary.contentReturns());
        out.writeInt(summary.usesOutsideFieldCall()); out.writeInt(summary.resets()); out.writeInt(summary.fieldCalls()); out.writeBoolean(summary.supportedResolver());
    }
    static FinanceReferenceTableEvidence read(DataInputStream in) throws IOException {
        int version = in.readInt(); var status = KryoLedgerReferenceTrace.Resolution.valueOf(in.readUTF());
        int n = in.readInt(), e = in.readInt(), b = in.readInt(), outside = in.readInt(), resets = in.readInt(), calls = in.readInt();
        int supported = in.readUnsignedByte(); if (supported > 1) throw new IOException("Invalid resolver presence flag");
        return new FinanceReferenceTableEvidence(version, new KryoLedgerReferenceTrace.Summary(status, n, e, b, outside, resets, calls, supported == 1));
    }
}
