package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned sidecars only. No original runtime, personal saves, mod data, or gameplay permission. */
public final class SponsorActivationStoreSelfTest {
    private static int checks;
    private static final String TERMS = "c".repeat(64);
    private static final SaveRevision FIRST = new SaveRevision("a".repeat(64), 20), NEXT = new SaveRevision("b".repeat(64), 24);
    interface Action { void run() throws Exception; }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch (Failure failure) { check(failure.reason() == reason, reason + " / " + failure); }
    }
    static FinanceRequest request(CareerSaveAssociations.Handle handle, UUID sponsor, SaveRevision save) {
        var ledger = new TreeMap<String, Long>(); FinanceDelta.FIELDS.forEach(field -> ledger.put(field, 0L));
        return new FinanceRequest(handle.career(), handle.binding(), save, SponsorActivationStore.OWNER,
                SponsorActivationStore.key(sponsor), new FinanceRequest.Club(0, -1, "Owned Club"), FinanceDelta.Direction.CREDIT_RAW_6,
                500, new FinanceDelta.Snapshot(1000, ledger));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsor-activation-store-");
        var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID()); UUID sponsor = UUID.randomUUID();
        var disk = new CareerSidecarStore(root.resolve("ledger")); var store = new SponsorActivationStore(disk, handle);
        check(store.read().entries().isEmpty() && !Files.exists(disk.root()), "read of absent history creates no directory or activation");
        var payload = request(handle, sponsor, FIRST);
        var claimed = store.claim(sponsor, TERMS, payload, EMPTY, "Estadio antigo", "Arena nova");
        check(claimed.snapshot().blocksNewActivation() && claimed.snapshot().entries().get(0).state() == SponsorActivationStore.State.CLAIMED,
                "durable claim blocks another activation before any effect");
        check(!claimed.snapshot().entries().get(0).savedActivationProven(), "claim is never proof of saved activation");
        var reopened = new SponsorActivationStore(disk, handle);
        check(reopened.read().equals(claimed.snapshot()), "reopen retains exact claim and original revision");
        rejects(Reason.CONFLICT, () -> reopened.claim(sponsor, TERMS, request(handle, sponsor, NEXT), claimed.snapshot().token()));
        UUID other = UUID.randomUUID();
        rejects(Reason.RECONCILIATION_REQUIRED, () -> reopened.claim(other, TERMS, request(handle, other, NEXT), claimed.snapshot().token()));
        try { reopened.complete(claimed.lease(), claimed.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
            throw new AssertionError("Foreign instance accepted original completion lease");
        } catch (IllegalArgumentException expected) { checks++; }
        var applied = store.complete(claimed.lease(), claimed.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
        check(applied.entries().get(0).stadiumBefore().equals("Estadio antigo")
                && applied.entries().get(0).stadiumAfter().equals("Arena nova"), "schema 2 preserves stadium audit through terminal commit");
        check(applied.blocksNewActivation() && !applied.entries().get(0).savedActivationProven(), "memory application remains blocked, never ACTIVE or INCLUDED");
        rejects(Reason.CONFLICT, () -> store.complete(claimed.lease(), applied.token(), SponsorActivationStore.State.NO_EFFECT));
        rejects(Reason.CONFLICT, () -> store.claim(sponsor, "d".repeat(64), request(handle, sponsor, NEXT), applied.token()));
        rejects(Reason.CONFLICT, () -> store.claim(other, TERMS, request(handle, other, NEXT), claimed.snapshot().token()));
        // The binding is selected explicitly. No club-name match shares activation history across careers.
        var separate = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        check(new SponsorActivationStore(disk, separate).read().entries().isEmpty(), "another career/binding remains isolated despite the same club");
        rejects(Reason.CONFLICT, () -> new SponsorActivationStore(disk, separate).claim(sponsor, TERMS, payload, EMPTY));
        var independent = new SponsorActivationStore(new CareerSidecarStore(root.resolve("no-effect")), handle);
        var noEffectClaim = independent.claim(sponsor, TERMS, payload, EMPTY);
        var noEffect = independent.complete(noEffectClaim.lease(), noEffectClaim.snapshot().token(), SponsorActivationStore.State.NO_EFFECT);
        check(!noEffect.blocksNewActivation(), "proven no-effect permits only a different contract");
        rejects(Reason.CONFLICT, () -> independent.claim(sponsor, TERMS, payload, noEffect.token()));
        var distinct = independent.claim(other, TERMS, request(handle, other, NEXT), noEffect.token());
        var unknown = independent.complete(distinct.lease(), distinct.snapshot().token(), SponsorActivationStore.State.UNKNOWN);
        check(new SponsorActivationStore(new CareerSidecarStore(root.resolve("no-effect")), handle).read().equals(unknown), "UNKNOWN retained across store reopening");
        check(unknown.entries().get(0).request().save().equals(FIRST) && unknown.entries().get(1).request().save().equals(NEXT),
                "revision-independent ledger preserves each request's immutable original revision");
        UUID third = UUID.randomUUID();
        rejects(Reason.RECONCILIATION_REQUIRED, () -> independent.claim(third, TERMS, request(handle, third, FIRST), unknown.token()));

        // All three persistence failure points are tested both before dispatch and after observed execution.
        for (boolean terminal : List.of(false, true)) for (Stage stage : Stage.values()) {
            var fail = new AtomicBoolean(!terminal);
            var faultRoot = root.resolve("fault-" + terminal + "-" + stage);
            var faultyDisk = new CareerSidecarStore(faultRoot, point -> { if (fail.get() && point == stage) throw new IOException("owned injected fault"); });
            var faulty = new SponsorActivationStore(faultyDisk, handle);
            SponsorActivationStore.Claimed execution = terminal ? faulty.claim(sponsor, TERMS, payload, EMPTY) : null;
            fail.set(true);
            try {
                if (terminal) faulty.complete(execution.lease(), execution.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
                else faulty.claim(sponsor, TERMS, payload, EMPTY);
                throw new AssertionError("Fault not injected");
            } catch (IOException expected) { checks++; }
            var after = new SponsorActivationStore(new CareerSidecarStore(faultRoot), handle);
            if (!terminal && stage != Stage.HEAD_COMMITTED) {
                rejects(Reason.RECONCILIATION_REQUIRED, after::read);
            } else {
                var retained = after.read();
                check(retained.blocksNewActivation(), "failed commit never re-enables activation " + terminal + "/" + stage);
                rejects(Reason.CONFLICT, () -> after.claim(sponsor, TERMS, request(handle, sponsor, NEXT), retained.token()));
                if (terminal) rejects(Reason.CONFLICT, () -> faulty.complete(execution.lease(), retained.token(), SponsorActivationStore.State.NO_EFFECT));
            }
        }
        // A copied metadata tree preserves the tombstone, not a new execution lease.
        Path copyRoot = root.resolve("copy");
        try (var files = Files.walk(disk.root())) {
            for (Path path : files.toList()) {
                Path target = copyRoot.resolve(disk.root().relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
            }
        }
        var copied = new SponsorActivationStore(new CareerSidecarStore(copyRoot), handle);
        check(copied.read().equals(applied), "copy with intact sidecars retains immutable applied tombstone");
        rejects(Reason.CONFLICT, () -> copied.claim(sponsor, TERMS, payload, applied.token()));
        var legacyDisk = new CareerSidecarStore(root.resolve("schema-1"));
        var legacyBytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(legacyBytes)) {
            out.writeInt(0x56425341); out.writeInt(1); out.writeInt(1);
            var attempt = UUID.randomUUID();
            out.writeLong(sponsor.getMostSignificantBits()); out.writeLong(sponsor.getLeastSignificantBits());
            out.writeLong(attempt.getMostSignificantBits()); out.writeLong(attempt.getLeastSignificantBits());
            out.writeUTF(TERMS); byte[] encoded = payload.encode(); out.writeInt(encoded.length); out.write(encoded);
            out.writeUTF("MEMORY_APPLIED");
        }
        legacyDisk.write(new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER), SponsorActivationStore.ANCHOR,
                1, EMPTY, legacyBytes.toByteArray());
        var legacyStore = new SponsorActivationStore(legacyDisk, handle); var legacy = legacyStore.read();
        check(legacy.blocksNewActivation() && legacy.entries().get(0).stadiumBefore().isEmpty()
                && legacy.entries().get(0).stadiumAfter().isEmpty(), "schema 1 remains readable without inventing stadium evidence");
        rejects(Reason.CONFLICT, () -> legacyStore.claim(sponsor, TERMS, payload, legacy.token()));
        var bounded = new SponsorActivationStore(new CareerSidecarStore(root.resolve("quota")), handle);
        String boundedToken = EMPTY;
        for (int i = 0; i < SponsorActivationStore.LIMIT; i++) {
            UUID id = UUID.randomUUID();
            var claim = bounded.claim(id, TERMS, request(handle, id, FIRST), boundedToken);
            boundedToken = bounded.complete(claim.lease(), claim.snapshot().token(), SponsorActivationStore.State.NO_EFFECT).token();
        }
        var full = bounded.read(); UUID overflow = UUID.randomUUID();
        rejects(Reason.QUOTA, () -> bounded.claim(overflow, TERMS, request(handle, overflow, NEXT), full.token()));
        check(bounded.read().equals(full) && full.entries().size() == SponsorActivationStore.LIMIT, "quota never evicts historical contracts");
        var malformedDisk = new CareerSidecarStore(root.resolve("corrupt"));
        malformedDisk.write(new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER), SponsorActivationStore.ANCHOR,
                99, EMPTY, new byte[] {1, 2, 3});
        rejects(Reason.CORRUPT, () -> new SponsorActivationStore(malformedDisk, handle).read());
        var unrelated = new CareerSidecarStore(root.resolve("unrelated"));
        unrelated.write(new Key(handle.career(), handle.binding(), "br.owned.unrelated"), SponsorActivationStore.ANCHOR, 1, EMPTY, new byte[] {1});
        rejects(Reason.RECONCILIATION_REQUIRED, () -> new SponsorActivationStore(unrelated, handle).read());
        var edtFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        javax.swing.SwingUtilities.invokeAndWait(() -> { try { store.read(); } catch (Throwable failure) { edtFailure.set(failure); } });
        check(edtFailure.get() instanceof Failure failure && failure.reason() == Reason.WRONG_THREAD, "activation disk I/O refused on EDT");
        System.out.println("PASS: " + checks + " sponsor activation metadata checks; save inclusion and gameplay release NOT qualified. Fixture: " + root);
    }
}
