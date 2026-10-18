package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerCommandJournal.*;

/** Owned fixtures only; journal results do not establish execution of any original game command. */
public final class CareerCommandJournalSelfTest {
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
        Path root = Files.createTempDirectory(parent, "command-journal-").toRealPath();
        var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.journal");
        var save = new SaveRevision("a".repeat(64), 10); var store = new CareerSidecarStore(root);
        var journal = new CareerCommandJournal(store, key);
        check(journal.read(save).token().equals(EMPTY), "empty journal without automatic association");
        var first = journal.prepare(save, EMPTY, intent("payment-1"));
        check(first.lease().isPresent() && first.snapshot().receipts().get(0).state() == State.OUTCOME_UNKNOWN, "new intent grants one lease, public snapshot remains conservative until completion");
        var resumed = new CareerCommandJournal(new CareerSidecarStore(root), key);
        var pending = resumed.read(save);
        check(pending.receipts().get(0).state() == State.OUTCOME_UNKNOWN, "reopen cannot infer whether prepared intent executed");
        check(resumed.prepare(save, pending.token(), intent("payment-1")).lease().isEmpty(), "reopen never reissues prepared intent lease");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> resumed.prepare(save, pending.token(), intent("another-payment")));
        boolean foreign = false;
        try { resumed.complete(first.lease().orElseThrow(), State.APPLIED, pending.token()); } catch (IllegalArgumentException expected) { foreign = true; }
        check(foreign, "lease from earlier journal instance refused");
        var complete = journal.complete(first.lease().orElseThrow(), State.APPLIED, pending.token());
        check(complete.receipts().get(0).state() == State.APPLIED, "same live authority records explicit completion");
        check(journal.complete(first.lease().orElseThrow(), State.APPLIED, complete.token()).token().equals(complete.token()), "duplicate identical completion makes no new generation");
        rejects(Reason.CONFLICT, () -> journal.complete(first.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, complete.token()));
        check(resumed.prepare(save, complete.token(), intent("payment-1")).lease().isEmpty(), "applied operation never replayed");
        rejects(Reason.CONFLICT, () -> journal.prepare(save, complete.token(), new Intent("payment-1", CareerCommandGate.Operation.FINANCE_DEBIT, "c".repeat(64))));
        rejects(Reason.CONFLICT, () -> journal.prepare(save, EMPTY, intent("stale-token")));
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.read(new SaveRevision("b".repeat(64), 10)));
        var second = journal.prepare(save, complete.token(), intent("payment-2"));
        var done = journal.complete(second.lease().orElseThrow(), State.APPLIED, second.snapshot().token());
        check(done.receipts().get(1).state() == State.APPLIED && journal.prepare(save, done.token(), intent("payment-2")).lease().isEmpty(), "second applied effect cannot retry");
        var third = journal.prepare(save, done.token(), intent("payment-3"));
        var noEffect = journal.complete(third.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, third.snapshot().token());
        check(noEffect.receipts().get(2).state() == State.NO_EFFECT_CONFIRMED, "trusted no-effect result distinguished");
        var captured = journal.read(save);
        for (int i = 4; i <= 32; i++) {
            var current = journal.read(save); var prepared = journal.prepare(save, current.token(), intent("payment-" + i));
            journal.complete(prepared.lease().orElseThrow(), State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
        }
        check(captured.receipts().size() == 3 && journal.read(save).receipts().size() == 32, "snapshots detached and bounded");
        String fullToken = journal.read(save).token();
        rejects(Reason.QUOTA, () -> journal.prepare(save, fullToken, intent("payment-33")));
        check(journal.prepare(save, fullToken, intent("payment-1")).lease().isEmpty(), "full journal retains duplicate suppression");
        var uncertainJournal = new CareerCommandJournal(store, new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.uncertain"));
        var uncertainIntent = uncertainJournal.prepare(save, EMPTY, intent("uncertain"));
        var uncertainResult = uncertainJournal.complete(uncertainIntent.lease().orElseThrow(), State.OUTCOME_UNKNOWN, uncertainIntent.snapshot().token());
        check(uncertainJournal.prepare(save, uncertainResult.token(), intent("uncertain")).lease().isEmpty(), "uncertain command never regranted");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> uncertainJournal.prepare(save, uncertainResult.token(), intent("next")));
        for (Stage interrupted : Stage.values()) {
            var interruptedKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.crash");
            var failing = new CareerCommandJournal(new CareerSidecarStore(root, stage -> { if (stage == interrupted) throw new IOException("owned injected crash"); }), interruptedKey);
            boolean failed = false;
            try { failing.prepare(save, EMPTY, intent("once")); } catch (IOException expected) { failed = true; }
            check(failed, "failed prepare never returns a lease " + interrupted);
            var reopened = new CareerCommandJournal(store, interruptedKey); var observed = reopened.read(save);
            if (interrupted == Stage.HEAD_COMMITTED) {
                check(observed.receipts().size() == 1 && reopened.prepare(save, observed.token(), intent("once")).lease().isEmpty(), "committed ambiguous prepare cannot mint another authority");
            } else check(observed.receipts().isEmpty(), "uncommitted intent never promoted from orphan " + interrupted);
        }
        var schemaKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.schema");
        store.write(schemaKey, save, 2, EMPTY, new byte[0]);
        rejects(Reason.CORRUPT, () -> new CareerCommandJournal(store, schemaKey).read(save));
        var malformedKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.malformed");
        store.write(malformedKey, save, 1, EMPTY, new byte[] {1, 2, 3});
        rejects(Reason.CORRUPT, () -> new CareerCommandJournal(store, malformedKey).read(save));
        System.out.println("PASS: " + checks + " durable command journal checks; owned fixture " + root + "; no game execution or save transaction guarantee.");
    }
}
