package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned metadata only: positive proof of NO EFFECT, never a balance-based resolution or replay. */
public final class ProvenNoEffectReconciliationSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static CareerCommandJournal.Intent intent(String key) { return new CareerCommandJournal.Intent(key, CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64)); }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA"); Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "proven-no-effect-"); var save = new SaveRevision("a".repeat(64), 12);
        for (boolean quarantine : List.of(false, true)) {
            Path fixture = root.resolve("resolve-" + quarantine); var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.proven");
            var rawStore = new CareerSidecarStore(fixture.resolve("journals")); var journal = new CareerCommandJournal(rawStore, key);
            var witnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses")), key);
            var context = new CareerCommandGate.Context(UUID.randomUUID(), key.career(), key.binding(), 1, save);
            var prepared = journal.prepare(save, EMPTY, intent("once")); var originalId = prepared.snapshot().receipts().get(0).operationId();
            var armed = witnesses.arm(context, journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), EMPTY);
            var proven = witnesses.complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            var pending = quarantine ? journal.quarantinePending(save, prepared.snapshot().token()) : journal.read(save);
            var proof = witnesses.proveNoEffect(save, proven.token(), originalId, intent("once"));
            var resolved = journal.reconcileNoEffect(proof, pending.token());
            check(resolved.receipts().equals(pending.receipts()) && resolved.receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN, "raw UNKNOWN receipt/UUID/payload are preserved " + quarantine);
            check(resolved.noEffectResolutions().size() == 1 && resolved.noEffectResolutions().get(0).originalState()
                    == (quarantine ? CareerCommandJournal.State.OUTCOME_UNKNOWN : CareerCommandJournal.State.PREPARED), "audit preserves actual recorded original state " + quarantine);
            check(resolved.noEffectResolutions().get(0).witnessToken().equals(proven.token()) && resolved.noEffectResolutions().get(0).context().equals(context.token()), "audit links committed witness HEAD and context " + quarantine);
            check(resolved.effectiveReceipts().get(0).state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED && journal.inspectRecordedRecovery(save).status() == CareerCommandJournal.RecoveryStatus.NO_EFFECT_ONLY,
                    "derived no-effect status can clear only proven execution uncertainty " + quarantine);
            check(new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals")), key).read(save).equals(resolved), "reopen keeps raw receipt and resolution " + quarantine);
            check(journal.reconcileNoEffect(proof, resolved.token()).equals(resolved), "same proof is idempotent without new commit " + quarantine);
            rejects(Reason.CONFLICT, () -> journal.reconcileNoEffect(proof, pending.token()));
            check(journal.prepare(save, resolved.token(), intent("once")).lease().isEmpty(), "resolved key never receives another lease " + quarantine);
            rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.executionPermit(prepared.lease().orElseThrow(), resolved.token()));
            rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, resolved.token()));
            check(journal.quarantinePending(save, resolved.token()).equals(resolved), "post-resolution quarantine cannot rewrite original receipt " + quarantine);
            var next = journal.prepare(save, resolved.token(), intent("new-operation"));
            check(next.lease().isPresent() && next.snapshot().noEffectResolutions().equals(resolved.noEffectResolutions()), "new key permitted while immutable old receipt/audit retained " + quarantine);
            rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.prepare(save, next.snapshot().token(), intent("blocked-by-new-unknown")));
            var newDone = journal.complete(next.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, next.snapshot().token());
            var revision = new SaveRevision("b".repeat(64), 13);
            var reanchored = journal.reanchorNoEffects(save, revision, newDone.token());
            check(reanchored.receipts().equals(newDone.receipts()) && reanchored.noEffectResolutions().equals(resolved.noEffectResolutions()), "explicit no-effect reanchor preserves raw receipt and audit " + quarantine);
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.reconcileNoEffect(proof, reanchored.token()));
        }
        for (var state : FinanceExecutionWitnessStore.State.values()) {
            var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.state");
            var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("states-journal")), key);
            var store = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("states-witness")), key);
            var prepared = journal.prepare(save, EMPTY, intent("once")); var context = new CareerCommandGate.Context(UUID.randomUUID(), key.career(), key.binding(), 1, save);
            var armed = store.arm(context, journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), EMPTY);
            var witness = state == FinanceExecutionWitnessStore.State.ARMED ? armed.snapshot() : store.complete(armed.lease(), state, armed.snapshot().token());
            if (state == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED) {
                var proof = store.proveNoEffect(save, witness.token(), prepared.snapshot().receipts().get(0).operationId(), intent("once"));
                var applied = journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, prepared.snapshot().token());
                rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.reconcileNoEffect(proof, applied.token()));
                check(journal.read(save).equals(applied), "APPLIED contradiction is not rewritten or resolved");
                var other = new CareerCommandJournal(new CareerSidecarStore(root.resolve("wrong-scope")), new Key(key.career(), UUID.randomUUID(), key.mod()));
                rejects(Reason.CONFLICT, () -> other.reconcileNoEffect(proof, EMPTY));
            } else rejects(Reason.RECONCILIATION_REQUIRED, () -> store.proveNoEffect(save, witness.token(), prepared.snapshot().receipts().get(0).operationId(), intent("once")));
            rejects(Reason.RECONCILIATION_REQUIRED, () -> store.proveNoEffect(save, witness.token(), UUID.randomUUID(), intent("once")));
        }
        for (Stage stage : Stage.values()) {
            Path fixture = root.resolve("fault-" + stage); var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.fault");
            var journal = new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals")), key);
            var store = new FinanceExecutionWitnessStore(new CareerSidecarStore(fixture.resolve("witnesses")), key);
            var prepared = journal.prepare(save, EMPTY, intent("once"));
            var armed = store.arm(new CareerCommandGate.Context(UUID.randomUUID(), key.career(), key.binding(), 1, save), journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), EMPTY);
            var completed = store.complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            var proof = store.proveNoEffect(save, completed.token(), prepared.snapshot().receipts().get(0).operationId(), intent("once"));
            var failing = new CareerCommandJournal(new CareerSidecarStore(fixture.resolve("journals"), at -> { if (at == stage) throw new IOException("Owned resolution fault"); }), key);
            boolean failed = false; try { failing.reconcileNoEffect(proof, prepared.snapshot().token()); } catch (IOException expected) { failed = true; }
            var inspected = journal.read(save);
            check(failed && inspected.receipts().equals(prepared.snapshot().receipts()), "fault preserves raw UNKNOWN without pretending rollback " + stage);
            check(inspected.noEffectResolutions().size() == (stage == Stage.HEAD_COMMITTED ? 1 : 0), "only committed audit resolution is used after reopen " + stage);
            check(journal.prepare(save, inspected.token(), intent("once")).lease().isEmpty(), "fault never makes historical key executable " + stage);
        }
        {
            var key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.batch"); var context = new CareerCommandGate.Context(UUID.randomUUID(), key.career(), key.binding(), 1, save);
            var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("batch-journal")), key);
            var witnessStore = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("batch-witness")), key);
            var first = journal.prepare(save, EMPTY, intent("first")); var armed = witnessStore.arm(context, journal.executionPermit(first.lease().orElseThrow(), first.snapshot().token()), EMPTY);
            var committedWitness = witnessStore.complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            var oldProof = witnessStore.proveNoEffect(save, committedWitness.token(), first.snapshot().receipts().get(0).operationId(), intent("first"));
            var firstDone = journal.complete(first.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, first.snapshot().token());
            var second = journal.prepare(save, firstDone.token(), intent("second")); var armedSecond = witnessStore.arm(context, journal.executionPermit(second.lease().orElseThrow(), second.snapshot().token()), committedWitness.token());
            var evidenceSecond = witnessStore.complete(armedSecond.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armedSecond.snapshot().token());
            rejects(Reason.CONFLICT, () -> journal.reconcileNoEffect(oldProof, second.snapshot().token()));
            var proofFirst = witnessStore.proveNoEffect(save, evidenceSecond.token(), firstDone.receipts().get(0).operationId(), intent("first"));
            var proofSecond = witnessStore.proveNoEffect(save, evidenceSecond.token(), second.snapshot().receipts().get(1).operationId(), intent("second"));
            rejects(Reason.CONFLICT, () -> journal.reconcileNoEffects(List.of(proofSecond, proofSecond), second.snapshot().token()));
            check(journal.read(save).equals(second.snapshot()), "duplicate proof list cannot commit partial resolutions");
            var resolved = journal.reconcileNoEffects(List.of(proofFirst, proofSecond), second.snapshot().token());
            check(resolved.noEffectResolutions().size() == 1 && resolved.effectiveReceipts().stream().allMatch(receipt -> receipt.state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED),
                    "bounded batch ignores already-known no-effect while adjudicating exact unknown operation");
            var third = journal.prepare(save, resolved.token(), intent("third"));
            var armedThird = witnessStore.arm(context, journal.executionPermit(third.lease().orElseThrow(), third.snapshot().token()), evidenceSecond.token());
            var evidenceThird = witnessStore.complete(armedThird.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armedThird.snapshot().token());
            var proofThird = witnessStore.proveNoEffect(save, evidenceThird.token(), third.snapshot().receipts().get(2).operationId(), intent("third"));
            var wrongProofIntent = new CareerCommandJournal.Intent("third", CareerCommandGate.Operation.FINANCE_CREDIT, "b".repeat(64));
            rejects(Reason.RECONCILIATION_REQUIRED, () -> witnessStore.proveNoEffect(save, evidenceThird.token(), third.snapshot().receipts().get(2).operationId(), wrongProofIntent));
            var otherJournal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("other-history")), key);
            rejects(Reason.CONFLICT, () -> otherJournal.reconcileNoEffect(proofThird, EMPTY));
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            javax.swing.SwingUtilities.invokeAndWait(() -> { try { journal.reconcileNoEffect(proofThird, third.snapshot().token()); } catch (Throwable expected) { failure.set(expected); } });
            check(failure.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "adjudication never does file I/O on EDT");
        }
        // Existing format1 journal remains readable; its raw receipt is not migrated by inspection.
        var legacyKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.legacy"); var raw = new CareerSidecarStore(root.resolve("legacy"));
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes); UUID id = UUID.randomUUID();
        out.writeInt(0x5642434a); out.writeInt(1); out.writeInt(1); out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
        out.writeUTF("once"); out.writeUTF("FINANCE_CREDIT"); out.write(HexFormat.of().parseHex("c".repeat(64))); out.writeUTF("OUTCOME_UNKNOWN"); out.flush();
        var committed = raw.write(legacyKey, save, 1, EMPTY, bytes.toByteArray()); var legacy = new CareerCommandJournal(raw, legacyKey).read(save);
        check(legacy.noEffectResolutions().isEmpty() && legacy.receipts().get(0).operationId().equals(id), "schema1 legacy UNKNOWN remains raw and unresolved");
        check(raw.read(legacyKey, save).token().equals(committed.token()) && raw.read(legacyKey, save).value().orElseThrow().schema() == 1, "read-only legacy inspection never auto-upgrades bytes");
        for (int mode = 0; mode < 5; mode++) {
            var badKey = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.bad-audit");
            var payload = new ByteArrayOutputStream(); var output = new DataOutputStream(payload);
            output.writeInt(0x5642434a); output.writeInt(2); output.writeInt(1);
            output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); output.writeUTF("once"); output.writeUTF("FINANCE_CREDIT");
            output.write(HexFormat.of().parseHex("c".repeat(64))); output.writeUTF(mode == 0 ? "APPLIED" : "OUTCOME_UNKNOWN");
            output.writeInt(mode == 1 ? 2 : 1); UUID auditId = mode == 2 ? UUID.randomUUID() : id;
            output.writeLong(auditId.getMostSignificantBits()); output.writeLong(auditId.getLeastSignificantBits()); output.writeUTF("once"); output.writeUTF("FINANCE_CREDIT");
            output.writeUTF("c".repeat(64)); output.writeUTF("OUTCOME_UNKNOWN"); output.writeUTF(mode == 3 ? "EMPTY" : "d".repeat(64));
            output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); output.writeLong(mode == 4 ? 0 : 1); output.flush();
            raw.write(badKey, save, 2, EMPTY, payload.toByteArray());
            rejects(Reason.CORRUPT, () -> new CareerCommandJournal(raw, badKey).read(save));
        }
        System.out.println("PASS: " + checks + " proven no-effect reconciliation checks; owned fixture " + root + "; no APPLIED/save inclusion inferred.");
    }
}
