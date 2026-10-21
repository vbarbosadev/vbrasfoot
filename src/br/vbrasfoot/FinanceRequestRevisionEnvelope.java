package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Offline versioned contract codec. Transition references are UNVERIFIED, never migration/write authority.
 * Not wired to FinanceRequestStore: its production reader/writer remains schema1. */
record FinanceRequestRevisionEnvelope(int format, Key scope, SaveRevision origin, SaveRevision anchor,
                                      List<Transition> transitions, List<FinanceRequest> requests) {
    static final int MAGIC = 0x56424652, MAX_TRANSITIONS = 8, MAX_BYTES = 160 * 1024;
    record Transition(UUID id, SaveRevision source, SaveRevision target, String manifestDigest) {
        Transition {
            Objects.requireNonNull(id); Objects.requireNonNull(source); Objects.requireNonNull(target);
            if (source.equals(target) || manifestDigest == null || !manifestDigest.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Nontrivial transition and manifest reference required");
        }
    }
    FinanceRequestRevisionEnvelope {
        Objects.requireNonNull(scope); Objects.requireNonNull(origin); Objects.requireNonNull(anchor);
        transitions = List.copyOf(transitions); requests = List.copyOf(requests);
        if (format != 1 && format != 2 || transitions.size() > MAX_TRANSITIONS || requests.isEmpty() || requests.size() > FinanceRequestStore.MAX_REQUESTS)
            throw new IllegalArgumentException("Unsupported or unbounded request envelope");
        if (format == 1 && (!transitions.isEmpty() || !origin.equals(anchor)))
            throw new IllegalArgumentException("Legacy envelope cannot contain invented transition facts");
        var revisions = new HashSet<SaveRevision>(); revisions.add(origin); var ids = new HashSet<UUID>(); SaveRevision current = origin;
        for (var step : transitions) {
            if (!step.source().equals(current) || !ids.add(step.id()) || !revisions.add(step.target()))
                throw new IllegalArgumentException("Disconnected, replayed or cyclic transition chain");
            current = step.target();
        }
        if (!current.equals(anchor)) throw new IllegalArgumentException("Transition chain does not end at storage anchor");
        var keys = new HashSet<String>();
        for (var request : requests) {
            if (!request.career().equals(scope.career()) || !request.binding().equals(scope.binding()) || !request.owner().equals(scope.mod())
                    || !revisions.contains(request.save()) || !keys.add(request.key()) || request.encode().length > 4096)
                throw new IllegalArgumentException("Request scope/origin/identity mismatch");
        }
    }
    boolean migrationAuthorized() { return false; }
    boolean writeAuthority() { return false; }
    byte[] encode() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(format);
            if (format == 2) {
                uuid(out, scope.career()); uuid(out, scope.binding()); out.writeUTF(scope.mod()); revision(out, origin); revision(out, anchor);
                out.writeInt(transitions.size());
                for (var step : transitions) { uuid(out, step.id()); revision(out, step.source()); revision(out, step.target()); out.writeUTF(step.manifestDigest()); }
            }
            out.writeInt(requests.size());
            for (var request : requests) { byte[] canonical = request.encode(); out.writeInt(canonical.length); out.write(canonical); }
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Request envelope byte budget exceeded");
        return bytes.toByteArray();
    }
    static FinanceRequestRevisionEnvelope decode(byte[] bytes, Key expectedScope, SaveRevision expectedAnchor) throws IOException {
        Objects.requireNonNull(bytes); Objects.requireNonNull(expectedScope); Objects.requireNonNull(expectedAnchor);
        if (bytes.length > MAX_BYTES) throw new IOException("Request envelope byte budget exceeded");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown request envelope magic");
            int format = in.readInt(); if (format != 1 && format != 2) throw new IOException("Unknown request envelope format");
            Key scope = expectedScope; SaveRevision origin = expectedAnchor, anchor = expectedAnchor; var transitions = new ArrayList<Transition>();
            if (format == 2) {
                scope = new Key(uuid(in), uuid(in), in.readUTF()); origin = revision(in); anchor = revision(in);
                if (!scope.equals(expectedScope) || !anchor.equals(expectedAnchor)) throw new IOException("Request envelope scope/anchor mismatch");
                int size = bounded(in.readInt(), 0, MAX_TRANSITIONS);
                for (int i = 0; i < size; i++) transitions.add(new Transition(uuid(in), revision(in), revision(in), in.readUTF()));
            }
            int size = bounded(in.readInt(), 1, FinanceRequestStore.MAX_REQUESTS); var requests = new ArrayList<FinanceRequest>();
            for (int i = 0; i < size; i++) requests.add(FinanceRequest.decode(in.readNBytes(bounded(in.readInt(), 1, 4096))));
            var result = new FinanceRequestRevisionEnvelope(format, scope, origin, anchor, transitions, requests);
            if (in.available() != 0 || !Arrays.equals(bytes, result.encode())) throw new IOException("Trailing or noncanonical request envelope");
            return result;
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid request envelope", invalid); }
    }
    private static int bounded(int value, int min, int max) throws IOException { if (value < min || value > max) throw new IOException("Request envelope count outside bounds"); return value; }
    private static void revision(DataOutputStream out, SaveRevision value) throws IOException { out.writeUTF(value.sha256()); out.writeLong(value.size()); }
    private static SaveRevision revision(DataInputStream in) throws IOException { return new SaveRevision(in.readUTF(), in.readLong()); }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
