package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned metadata-only experiment: why a journal-only reanchor is NOT coordinated financial recovery. */
public final class FinanceNoEffectRevisionRecoverySelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    public static void main(String[] args) throws Exception {
        Path parent = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode"));
        Path root = Files.createTempDirectory(parent, "no-effect-revision-").toRealPath();
        Path saves = Files.createDirectory(root.resolve("saves")), file = saves.resolve("owned.s22"); Files.write(file, new byte[]{1, 2, 3});
        var observer = new SaveFileObserver(); var source = observer.observe(saves, "owned.s22");
        var associations = new CareerSaveAssociations(root.resolve("associations"));
        var association = associations.create(source, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
        var key = new Key(association.handle().career(), association.handle().binding(), "br.owned.noeffect-revision");
        var contractDisk = new CareerSidecarStore(root.resolve("contracts")); var requests = new FinanceRequestStore(contractDisk, key);
        var journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journal")), key);
        var witnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(root.resolve("execution")), key);
        var fields = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) fields.put(field, 0L);
        var request = new FinanceRequest(key.career(), key.binding(), source.revision(), key.mod(), "once", new FinanceRequest.Club(0, 1, "Owned"),
                FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, fields));
        var contracts = requests.persist(request, EMPTY); var prepared = journal.prepare(source.revision(), EMPTY, request.intent());
        try (var gate = new CareerCommandGate()) {
            // Artificial no-effect execution facts; no handler or game object is invoked.
            var context = gate.installContext(key.career(), key.binding(), 1, source.revision(), new Object(), new Object());
            var armed = witnesses.arm(context, journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), EMPTY);
            var evidence = witnesses.complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            var history = journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            check(history.receipts().get(0).state() == CareerCommandJournal.State.NO_EFFECT_CONFIRMED
                    && evidence.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, "fixture has exact durable no-effect pair without executing an effect");
            Files.write(file, new byte[]{4, 5, 6, 7}); var target = observer.observe(saves, "owned.s22");
            var changed = associations.acceptRevision(association.handle(), association.token(), target, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION);
            check(changed.save().equals(target.revision()) && requests.read(source.revision()).equals(contracts)
                    && journal.read(source.revision()).equals(history) && witnesses.read(source.revision()).equals(evidence),
                    "association-only revision acceptance leaves all three financial roots untouched");
            var moved = journal.reanchorNoEffects(source.revision(), target.revision(), history.token());
            check(moved.receipts().equals(history.receipts()) && moved.noEffectResolutions().equals(history.noEffectResolutions()), "existing narrow journal reanchor preserves raw no-effect history");
            check(!journal.inspectRecordedRecovery(target.revision()).revisionChanged() && requests.inspectRecordedRecovery(target.revision()).revisionChanged()
                    && witnesses.inspectRecordedRecovery(target.revision()).revisionChanged(), "successful journal-only reanchor leaves contracts/execution at old anchor");
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> requests.read(target.revision()));
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> witnesses.read(target.revision()));
            check(journal.prepare(target.revision(), moved.token(), request.intent()).lease().isEmpty(), "old key never receives another execution lease even after no-effect reanchor");
            var rewritten = new FinanceRequest(key.career(), key.binding(), target.revision(), key.mod(), request.key(), request.club(), request.direction(), request.amount(), request.before());
            check(!rewritten.intent().equals(request.intent()) && !Arrays.equals(rewritten.encode(), request.encode()), "rewriting request.save changes canonical payload/digest, not just metadata anchor");
            rejects(Reason.CONFLICT, () -> journal.prepare(target.revision(), moved.token(), rewritten.intent()));
            check(FinanceContractReview.compare(new FinanceRequestStore.Snapshot(contracts.token(), List.of(rewritten)), moved).stream().allMatch(FinanceContractReview.Link::blocksContext),
                    "a rewritten contract cannot masquerade as its exact preserved receipt");
            var raw = contractDisk.read(key, source.revision()).value().orElseThrow();
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> contractDisk.write(key, target.revision(), raw.schema(), contracts.token(), raw.payload()));
            // Deliberately invalid copied envelope, only in another owned root. Production never offers this migration.
            var badCopy = new CareerSidecarStore(root.resolve("invalid-outer-anchor-copy")); badCopy.write(key, target.revision(), raw.schema(), EMPTY, raw.payload());
            rejects(Reason.CORRUPT, () -> new FinanceRequestStore(badCopy, key).read(target.revision()));
            check(requests.read(source.revision()).equals(contracts) && witnesses.read(source.revision()).equals(evidence)
                    && journal.read(target.revision()).equals(moved), "all failed shortcuts preserve original contracts/witnesses and reanchored raw receipts");
            check(Arrays.equals(Files.readAllBytes(file), new byte[]{4, 5, 6, 7}), "metadata experiment never rewrites the selected target file");
        }
        System.out.println("PASS: " + checks + " no-effect revision recovery constraints; owned metadata " + root + "; no coordinated migration or gameplay authority.");
    }
}
