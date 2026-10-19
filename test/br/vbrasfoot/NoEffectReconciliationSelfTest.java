package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerCommandJournal.*;

/** Owned histories only. Never infers saved application of an original gameplay effect. */
public final class NoEffectReconciliationSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static Intent intent(String key) { return new Intent(key, CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64)); }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "no-effect-reconciliation-").toRealPath();
        var store = new CareerSidecarStore(root); var before = new SaveRevision("a".repeat(64), 10); var after = new SaveRevision("b".repeat(64), 11);
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.no-effect"); var journal = new CareerCommandJournal(store, key);
        rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.reanchorNoEffects(before, after, EMPTY));
        check(!Files.exists(root.resolve(key.career().toString())), "empty migration never creates a fabricated history");
        var pending = journal.prepare(before, EMPTY, intent("once"));
        rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.reanchorNoEffects(before, after, pending.snapshot().token()));
        var noEffect = journal.complete(pending.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, pending.snapshot().token());
        var diagnostic = journal.inspectRecordedRecovery(after);
        check(diagnostic.recordedSave().equals(before) && diagnostic.observedSave().equals(after) && diagnostic.revisionChanged()
                && diagnostic.status() == RecoveryStatus.NO_EFFECT_ONLY, "administrative inspection exposes actual stored anchor without changing it");
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.read(after));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.inspectRecovery(after, before));
        var reconciled = journal.reanchorNoEffects(before, after, noEffect.token());
        check(reconciled.receipts().equals(noEffect.receipts()) && !reconciled.token().equals(noEffect.token()), "explicit no-effect migration preserves UUIDs/keys/digests/states and changes only anchor/envelope revision");
        check(journal.read(after).equals(reconciled) && journal.inspectRecordedRecovery(after).recordedSave().equals(after), "current journal anchor matches explicit new revision");
        check(journal.reanchorNoEffects(after, after, reconciled.token()).token().equals(reconciled.token()), "same-anchor reviewed no-effect migration is idempotent");
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.reanchorNoEffects(before, after, noEffect.token()));
        rejects(Reason.CONFLICT, () -> journal.reanchorNoEffects(after, before, noEffect.token()));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.read(before));
        check(journal.prepare(after, reconciled.token(), intent("once")).lease().isEmpty(), "reanchoring never reissues old command lease even when no effect was confirmed");
        check(store.readPrevious(key, before, reconciled.token()).save().equals(before), "previous committed anchor remains explicitly inspectable");
        for (State state : List.of(State.PREPARED, State.APPLIED, State.OUTCOME_UNKNOWN)) {
            var otherKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.blocked"); var other = new CareerCommandJournal(store, otherKey);
            var prepared = other.prepare(before, EMPTY, intent("blocked"));
            String token = prepared.snapshot().token();
            if (state != State.PREPARED) token = other.complete(prepared.lease().orElseThrow(), state, token).token();
            String expectedToken = token;
            rejects(Reason.RECONCILIATION_REQUIRED, () -> other.reanchorNoEffects(before, after, expectedToken));
            check(other.read(before).token().equals(expectedToken), "refusal preserves original committed history " + state);
        }
        for (Stage stage : Stage.values()) {
            var faultKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.fault"); var original = new CareerCommandJournal(store, faultKey);
            var prepared = original.prepare(before, EMPTY, intent("fault"));
            var completed = original.complete(prepared.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            var failing = new CareerCommandJournal(new CareerSidecarStore(root, point -> { if (point == stage) throw new IOException("owned interruption"); }), faultKey);
            boolean interrupted = false;
            try { failing.reanchorNoEffects(before, after, completed.token()); } catch (IOException expected) { interrupted = true; }
            check(interrupted, "migration interruption injected " + stage);
            var observed = original.inspectRecordedRecovery(after);
            check(observed.snapshot().receipts().equals(completed.receipts()) && observed.recordedSave().equals(stage == Stage.HEAD_COMMITTED ? after : before),
                    "failure preserves receipts and reveals only committed anchor " + stage);
        }
        var edtFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { journal.reanchorNoEffects(after, before, reconciled.token()); } catch (Throwable expected) { edtFailure.set(expected); } });
        check(edtFailure.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "journal migration never does disk I/O on EDT");
        System.out.println("PASS: " + checks + " no-effect reconciliation checks; owned fixture " + root + "; APPLIED/UNKNOWN remain unresolved.");
    }
}
