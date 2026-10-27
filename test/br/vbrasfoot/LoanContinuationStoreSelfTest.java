package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned metadata only; no runtime, game installation or gameplay authority. */
public final class LoanContinuationStoreSelfTest {
    private static int checks;
    interface Call { void run() throws Exception; }
    static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    static void rejects(Reason reason, Call call) throws Exception {
        try { call.run(); } catch (Failure failure) { check(failure.reason() == reason, failure.toString()); return; }
        throw new AssertionError("Expected " + reason);
    }
    static LoanRequest request(CareerSaveAssociations.Handle handle, String key, int player, SaveRevision revision) {
        return new LoanRequest(handle.career(), handle.binding(), revision, LoanManagerPanel.OWNER, key,
                new FinanceRequest.Club(0, 10, "Origin"), new FinanceRequest.Club(1, 20, "Destination"), 0,
                List.of(new br.vbrasfoot.api.GameAccess.Player(player, "Player " + player, 20, 1, 10, 100)),
                List.of(new br.vbrasfoot.api.GameAccess.Player(1000, "Other", 20, 1, 20, 100)), 1000);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loan-ledger-test-");
        var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        var disk = new CareerSidecarStore(root.resolve("ledger")); var store = new LoanContinuationStore(disk, handle);
        var firstSave = new SaveRevision("a".repeat(64), 12); var nextSave = new SaveRevision("b".repeat(64), 14);
        var sources = Map.of("loan/" + LoanManagerPanel.OWNER, "c".repeat(64), "journal/" + LoanManagerPanel.OWNER, "d".repeat(64));
        var first = request(handle, "first", 1, firstSave); byte[] raw = first.encode();
        var claimed = store.claim(first, EMPTY, sources);
        check(claimed.uncertain() && claimed.entries().get(0).state() == LoanContinuationStore.State.EXECUTING, "durable pre-effect tombstone");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> new LoanContinuationStore(disk, handle).claim(request(handle, "other", 2, firstSave), claimed.token(), sources));
        var applied = store.complete(first, claimed.token(), LoanContinuationStore.State.APPLIED);
        rejects(Reason.CONFLICT, () -> store.claim(request(handle, "different-key", 1, nextSave), applied.token(), sources));
        rejects(Reason.CONFLICT, () -> store.claim(request(handle, "first", 2, nextSave), applied.token(), sources));
        rejects(Reason.CONFLICT, () -> store.claim(request(handle, "stale", 2, nextSave), claimed.token(), sources));
        rejects(Reason.CONFLICT, () -> LoanContinuationStore.validateSources(applied, Map.of()));
        rejects(Reason.CONFLICT, () -> LoanContinuationStore.validateSources(applied, Map.of("loan/" + LoanManagerPanel.OWNER, "e".repeat(64))));
        var expanded = new TreeMap<>(sources); expanded.put("loan/br.new.reviewed", "f".repeat(64));
        String alias = "alias/" + UUID.randomUUID() + "/" + UUID.randomUUID() + "/";
        expanded.put(alias + "association", "1".repeat(64));
        expanded.put(alias + "continuation", "0".repeat(64));
        expanded.put(alias + "loan/" + LoanManagerPanel.OWNER, "2".repeat(64));
        var second = request(handle, "second", 2, nextSave);
        var next = new LoanContinuationStore(disk, handle).claim(second, applied.token(), expanded);
        next = store.complete(second, next.token(), LoanContinuationStore.State.NO_EFFECT);
        var reopened = new LoanContinuationStore(disk, handle).read();
        check(reopened.entries().get(0).request().save().equals(firstSave) && reopened.entries().get(1).request().save().equals(nextSave), "each contract retains its own original save revision");
        check(Arrays.equals(raw, reopened.entries().get(0).raw()), "old immutable raw bytes preserved after save/reopen and new request");
        byte[] detached = reopened.entries().get(0).raw(); detached[0] ^= 1;
        check(Arrays.equals(raw, reopened.entries().get(0).raw()), "raw bytes defensively detached");
        check(reopened.sources().equals(expanded), "all reviewed source tokens retained");
        var missingAlias = new TreeMap<>(expanded); missingAlias.remove(alias + "continuation");
        rejects(Reason.CONFLICT, () -> LoanContinuationStore.validateSources(reopened, missingAlias));
        var changedAlias = new TreeMap<>(expanded); changedAlias.put(alias + "loan/" + LoanManagerPanel.OWNER, "3".repeat(64));
        rejects(Reason.CONFLICT, () -> LoanContinuationStore.validateSources(reopened, changedAlias));
        var key = new Key(handle.career(), handle.binding(), LoanContinuationStore.OWNER);
        check(disk.read(key, LoanContinuationStore.ANCHOR).value().orElseThrow().save().equals(LoanContinuationStore.ANCHOR), "dedicated stable sentinel anchor");
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> disk.read(key, nextSave));
        final String token = reopened.token();
        rejects(Reason.CONFLICT, () -> store.claim(request(handle, "same-no-effect-player", 2, nextSave), token, expanded));
        // Exhaustion never evicts an older key/player tombstone.
        for (int i = 3; i <= LoanContinuationStore.LIMIT; i++) {
            var request = request(handle, "q" + i, i, nextSave); var before = store.read();
            var execution = store.claim(request, before.token(), expanded);
            store.complete(request, execution.token(), LoanContinuationStore.State.NO_EFFECT);
        }
        var full = store.read();
        rejects(Reason.QUOTA, () -> store.claim(request(handle, "overflow", 100, nextSave), full.token(), expanded));
        check(Arrays.equals(raw, full.entries().get(0).raw()), "quota preserves oldest tombstone");
        // A failed terminal write leaves EXECUTING durable and blocks all future requests after reopen.
        var fail = new AtomicBoolean();
        var faultyDisk = new CareerSidecarStore(root.resolve("faulty"), stage -> {
            if (fail.get() && stage == Stage.HEAD_STAGED) throw new java.io.IOException("owned terminal fault");
        });
        var faulty = new LoanContinuationStore(faultyDisk, handle); var execution = faulty.claim(first, EMPTY, sources); fail.set(true);
        try { faulty.complete(first, execution.token(), LoanContinuationStore.State.APPLIED); throw new AssertionError("Expected failure"); }
        catch (java.io.IOException expected) { checks++; }
        var uncertain = new LoanContinuationStore(faultyDisk, handle).read();
        check(uncertain.uncertain(), "terminal commit failure remains globally blocking");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> faulty.claim(second, uncertain.token(), sources));
        System.out.println("LoanContinuationStoreSelfTest: " + checks + " checks passed");
    }
}
