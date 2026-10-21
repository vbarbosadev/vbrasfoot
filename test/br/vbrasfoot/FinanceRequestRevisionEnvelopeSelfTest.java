package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.FinanceRequestRevisionEnvelope.*;

/** Own canonical requests and UNVERIFIED transition references. Never applies a migration. */
public final class FinanceRequestRevisionEnvelopeSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static final Key SCOPE = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.envelope");
    private static final SaveRevision A = revision(1), B = revision(2), C = revision(3);
    private static SaveRevision revision(int value) { return new SaveRevision(String.format("%064x", value), value + 10); }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Action action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; } throw new AssertionError(label);
    }
    private static FinanceRequest request(SaveRevision save, String key) {
        var fields = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) fields.put(field, 0L);
        return new FinanceRequest(SCOPE.career(), SCOPE.binding(), save, SCOPE.mod(), key, new FinanceRequest.Club(0, 1, "Owned"),
                FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, fields));
    }
    private static Transition transition(SaveRevision source, SaveRevision target) { return new Transition(UUID.randomUUID(), source, target, "a".repeat(64)); }
    private static FinanceRequestRevisionEnvelope roundtrip(FinanceRequestRevisionEnvelope value) throws Exception {
        byte[] bytes = value.encode(); var restored = decode(bytes, value.scope(), value.anchor());
        check(restored.equals(value) && Arrays.equals(bytes, restored.encode()), "canonical exact envelope roundtrip");
        check(!restored.migrationAuthorized() && !restored.writeAuthority(), "transition manifest reference never authorizes migration or write");
        return restored;
    }
    public static void main(String[] args) throws Exception {
        var original = request(A, "old-key"); var legacy = new FinanceRequestRevisionEnvelope(1, SCOPE, A, A, List.of(), List.of(original));
        var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(1); out.writeInt(1); byte[] raw = original.encode(); out.writeInt(raw.length); out.write(raw);
        }
        check(Arrays.equals(legacy.encode(), bytes.toByteArray()), "format1 reproduces original request-store payload exactly, with no fabricated transition");
        check(roundtrip(legacy).format() == 1 && decode(bytes.toByteArray(), SCOPE, A).transitions().isEmpty(), "legacy decode preserves format/absence without migration");
        roundtrip(new FinanceRequestRevisionEnvelope(2, SCOPE, A, A, List.of(), List.of(original)));
        var ab = transition(A, B); var envelope = new FinanceRequestRevisionEnvelope(2, SCOPE, A, B, List.of(ab), List.of(original, request(B, "new-key")));
        var restored = roundtrip(envelope);
        check(restored.requests().get(0).intent().equals(original.intent()) && Arrays.equals(restored.requests().get(0).encode(), original.encode()), "new storage anchor preserves original request bytes/intent including source revision");
        var bc = transition(B, C); var repeated = roundtrip(new FinanceRequestRevisionEnvelope(2, SCOPE, A, C, List.of(ab, bc), List.of(original, request(B, "middle-key"), request(C, "latest-key"))));
        check(repeated.requests().stream().map(FinanceRequest::save).toList().equals(List.of(A, B, C)), "mixed request-origin revisions stay explicit across consecutive draft transitions");
        rejects(() -> new FinanceRequestRevisionEnvelope(3, SCOPE, A, A, List.of(), List.of(original)), "unknown format refused");
        rejects(() -> new FinanceRequestRevisionEnvelope(1, SCOPE, A, B, List.of(ab), List.of(original)), "legacy cannot acquire transition facts");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, B, List.of(), List.of(original)), "anchor change needs explicit chain, not inferred matching fields");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, C, List.of(ab), List.of(original)), "chain must end at supplied storage anchor");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, C, List.of(bc), List.of(original)), "chain must start at envelope origin");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, A, List.of(ab, transition(B, A)), List.of(original)), "cycle/restore to seen revision not silently treated as forward recovery");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, C, List.of(ab, new Transition(ab.id(), B, C, "b".repeat(64))), List.of(original)), "transition ID replay refused");
        rejects(() -> new Transition(UUID.randomUUID(), A, A, "a".repeat(64)), "identity transition refused");
        rejects(() -> new Transition(UUID.randomUUID(), A, B, "bad"), "malformed manifest reference refused");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, B, List.of(ab), List.of(original, request(B, "old-key"))), "same historical key cannot be inserted again at new origin revision");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, B, List.of(ab), List.of(request(C, "unrelated"))), "request outside explicit origin chain refused");
        rejects(() -> decode(envelope.encode(), new Key(UUID.randomUUID(), SCOPE.binding(), SCOPE.mod()), B), "foreign career scope refused");
        rejects(() -> decode(envelope.encode(), new Key(SCOPE.career(), UUID.randomUUID(), SCOPE.mod()), B), "foreign binding scope refused");
        rejects(() -> decode(envelope.encode(), new Key(SCOPE.career(), SCOPE.binding(), "br.owned.other"), B), "foreign owner scope refused");
        rejects(() -> decode(envelope.encode(), SCOPE, A), "wrong expected storage anchor refused");
        rejects(() -> decode(legacy.encode(), SCOPE, B), "legacy origin cannot borrow current anchor");
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, A, List.of(), List.of()), "empty envelope not substituted for absent namespace");
        var many = new ArrayList<FinanceRequest>(); for (int i = 0; i < FinanceRequestStore.MAX_REQUESTS + 1; i++) many.add(request(A, "k" + i));
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, A, List.of(), many), "request quota retained across all origin revisions");
        roundtrip(new FinanceRequestRevisionEnvelope(2, SCOPE, A, A, List.of(), many.subList(0, FinanceRequestStore.MAX_REQUESTS)));
        var steps = new ArrayList<Transition>(); SaveRevision cursor = A;
        for (int i = 0; i < MAX_TRANSITIONS + 1; i++) { SaveRevision next = revision(i + 10); steps.add(transition(cursor, next)); cursor = next; }
        SaveRevision last = cursor;
        rejects(() -> new FinanceRequestRevisionEnvelope(2, SCOPE, A, last, steps, List.of(original)), "transition history bounded, never evicted");
        roundtrip(new FinanceRequestRevisionEnvelope(2, SCOPE, A, steps.get(MAX_TRANSITIONS - 1).target(), steps.subList(0, MAX_TRANSITIONS), List.of(original)));
        for (int count : List.of(-1, 0, FinanceRequestStore.MAX_REQUESTS + 1, Integer.MAX_VALUE)) {
            var invalid = new ByteArrayOutputStream(); try (var out = new DataOutputStream(invalid)) { out.writeInt(MAGIC); out.writeInt(1); out.writeInt(count); }
            rejects(() -> decode(invalid.toByteArray(), SCOPE, A), "wire request count outside bounds refused");
        }
        for (int length : List.of(-1, 0, 4097, Integer.MAX_VALUE)) {
            var invalid = new ByteArrayOutputStream(); try (var out = new DataOutputStream(invalid)) { out.writeInt(MAGIC); out.writeInt(1); out.writeInt(1); out.writeInt(length); }
            rejects(() -> decode(invalid.toByteArray(), SCOPE, A), "wire request length outside bounds refused");
        }
        byte[] encoded = envelope.encode();
        for (int size : List.of(0, 4, 8, encoded.length - 1)) rejects(() -> decode(Arrays.copyOf(encoded, size), SCOPE, B), "truncated envelope refused");
        rejects(() -> decode(Arrays.copyOf(encoded, encoded.length + 1), SCOPE, B), "trailing envelope byte refused");
        byte[] future = encoded.clone(); future[7] = 3; rejects(() -> decode(future, SCOPE, B), "future wire version refused");
        byte[] badMagic = encoded.clone(); badMagic[0] ^= 1; rejects(() -> decode(badMagic, SCOPE, B), "unknown wire magic refused");
        rejects(() -> decode(new byte[FinanceRequestRevisionEnvelope.MAX_BYTES + 1], SCOPE, B), "byte budget enforced before parsing");
        boolean immutable = false; try { envelope.transitions().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "transition chain immutable");
        // Production store is deliberately NOT upgraded: even a structurally valid format2 is still rejected there.
        Path parent = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode")); Path root = Files.createTempDirectory(parent, "request-envelope-").toRealPath();
        var disk = new CareerSidecarStore(root); disk.write(SCOPE, B, 1, EMPTY, encoded);
        boolean refused = false; try { new FinanceRequestStore(disk, SCOPE).read(B); } catch (Failure failure) { refused = failure.reason() == Reason.CORRUPT; }
        check(refused, "production schema1 decoder refuses offline format2; no bypass or implicit integration");
        var futureDisk = new CareerSidecarStore(root.resolve("schema2-not-supported")); futureDisk.write(SCOPE, B, 2, EMPTY, encoded);
        refused = false; try { new FinanceRequestStore(futureDisk, SCOPE).read(B); } catch (Failure failure) { refused = failure.reason() == Reason.CORRUPT; }
        check(refused, "production reader also refuses new schema; changing outer schema does not install a migration path");
        var originalDisk = new CareerSidecarStore(root.resolve("original-schema1")); var originalStore = new FinanceRequestStore(originalDisk, SCOPE);
        var committed = originalStore.persist(original, EMPTY); var raw = originalDisk.read(SCOPE, A).value().orElseThrow();
        check(Arrays.equals(raw.payload(), legacy.encode()) && decode(raw.payload(), SCOPE, A).requests().equals(committed.requests()),
                "codec reads payload from real owned production schema1 writer without changing historical bytes");
        check(originalStore.read(A).equals(committed) && originalDisk.read(SCOPE, A).value().orElseThrow().schema() == 1,
                "offline inspection never upgrades original store HEAD or schema");
        var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("historical-keys")), SCOPE);
        var reserved = journal.prepare(A, EMPTY, original.intent());
        var noEffect = journal.complete(reserved.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, reserved.snapshot().token());
        check(journal.prepare(A, noEffect.token(), restored.requests().get(0).intent()).lease().isEmpty(),
                "request retrieved from transitioned offline envelope never gives an old key another execution lease");
        refused = false; try { journal.prepare(B, noEffect.token(), restored.requests().get(0).intent()); }
        catch (Failure failure) { refused = failure.reason() == Reason.SAVE_REVISION_MISMATCH; }
        check(refused, "envelope target anchor is not permission to use a journal still at origin revision");
        System.out.println("PASS: " + checks + " immutable request revision envelope checks; owned metadata " + root + "; transition references unverified, no migration authority.");
    }
}
