package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Owned streams and artificial execution facts only. Durable observations never resolve inclusion or retry effects. */
public final class FinanceSerializationEvidenceSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure failure) { check(failure.reason() == reason, "expected " + reason + " got " + failure.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static final class Fixture implements AutoCloseable {
        final Path root, file; final Key key = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.serialization");
        final SaveRevision source = new SaveRevision("a".repeat(64), 17);
        final CareerCommandGate gate = new CareerCommandGate(); final Object primary = new Object(), secondary = new Object();
        final FinanceRequestStore requests; final CareerCommandJournal journal; final CareerSidecarStore storage;
        final FinanceExecutionWitnessStore witnesses; final FinanceSerializationTracker tracker = new FinanceSerializationTracker();
        final FinanceSerializationTracker.Target target = value -> FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE;
        final WrittenSaveVerifier verifier; final AutoCloseable lifecycleGuard, captureGuard;
        final CareerCommandJournal.ExecutionPermit proof;
        Fixture(Path root, FaultProbe faults) throws Exception { this(root, faults, FinanceExecutionWitnessStore.State.MEMORY_APPLIED); }
        Fixture(Path root, FaultProbe faults, FinanceExecutionWitnessStore.State terminal) throws Exception {
            this.root = Files.createDirectories(root); file = root.resolve("owned.s22");
            requests = new FinanceRequestStore(new CareerSidecarStore(root.resolve("contracts")), key);
            journal = new CareerCommandJournal(new CareerSidecarStore(root.resolve("journal")), key);
            storage = new CareerSidecarStore(root.resolve("execution"), faults); witnesses = new FinanceExecutionWitnessStore(storage, key);
            var ledger = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) ledger.put(field, 0L);
            var request = new FinanceRequest(key.career(), key.binding(), source, key.mod(), "once", new FinanceRequest.Club(0, 1, "Owned"),
                    FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, ledger));
            requests.persist(request, EMPTY);
            var context = gate.installContext(key.career(), key.binding(), 1, source, primary, secondary);
            var prepared = journal.prepare(source, EMPTY, request.intent()); proof = journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token());
            var armed = witnesses.arm(context, proof, EMPTY);
            witnesses.complete(armed.lease(), terminal, armed.snapshot().token());
            journal.complete(prepared.lease().orElseThrow(), terminal == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED
                    ? CareerCommandJournal.State.NO_EFFECT_CONFIRMED : CareerCommandJournal.State.APPLIED, prepared.snapshot().token());
            lifecycleGuard = HookProbe.attachLifecycle(new CareerLifecycle());
            captureGuard = HookProbe.attachFinancialSerialization(tracker);
            HookProbe.enterLoad("owned", false); HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
            tracker.observe(proof.operationId(), proof.intent(), target, null);
            verifier = new WrittenSaveVerifier(new SaveFileObserver(), () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        }
        WrittenSaveVerifier.Captured save() throws Exception {
            HookProbe.enterSave("owned"); HookProbe.saveDestination(root.resolve("owned.info").toString()); HookProbe.saveDestination(file.toString());
            var output = HookProbe.traceSaveOutput(Files.newOutputStream(file));
            HookProbe.saveRootSerializing(primary); HookProbe.saveRootSerialized(); HookProbe.saveRootSerializing(secondary); HookProbe.saveRootSerialized();
            output.write(new byte[]{1, 2, 3, 4}); output.close(); Object marker = new Object();
            HookProbe.metadataClosed(); HookProbe.primaryOutputClosing(marker); HookProbe.primaryStreamClosed(marker); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            return verifier.capture(new SaveFileObserver().observe(root, file.getFileName().toString()));
        }
        FinanceExecutionWitnessStore.Snapshot record(WrittenSaveVerifier.Captured capture) throws Exception {
            return witnesses.observeSerialization(source, witnesses.read(source).token(), capture, requests, journal);
        }
        FinanceExecutionWitnessStore.Snapshot seedLegacyObservation() throws Exception {
            // Own fixture encoder for previous format, never production downgrade or migration.
            var before = witnesses.read(source); var witness = before.witnesses().get(0);
            var operation = new FinanceSerializationTracker.Operation(witness.operationId(), witness.intent(), FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE,
                    FinanceSerializationTracker.Sample.MATCHED_EXPECTED_STATE, FinanceSerializationTracker.Continuity.NO_OBSERVED_DIRECT_WRITE,
                    new FinanceSerializationTracker.Fields(FinanceSerializationTracker.Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, 15, Set.of("UnsafeLongField", "UnsafeIntField")),
                    new FinanceSerializationTracker.Edge(FinanceSerializationTracker.Reference.EXPECTED_LEDGER_ARGUMENT_OBSERVED, 1, Set.of("writeObject")));
            var observation = new FinanceSerializationEvidence.Observation(UUID.randomUUID(), UUID.randomUUID().toString(), 1, 1, source, before.token(), requests.read(source).token(),
                    journal.read(source).token(), false, FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE,
                    List.of(new FinanceSerializationEvidence.Operation(operation, witness.state(), CareerCommandJournal.State.APPLIED)));
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(0x56424645); out.writeInt(2); out.writeInt(before.witnesses().size());
                for (var value : before.witnesses()) {
                    out.writeLong(value.operationId().getMostSignificantBits()); out.writeLong(value.operationId().getLeastSignificantBits());
                    out.writeUTF(value.intent().key()); out.writeUTF(value.intent().operation().name()); out.writeUTF(value.intent().payloadDigest());
                    out.writeLong(value.context().getMostSignificantBits()); out.writeLong(value.context().getLeastSignificantBits()); out.writeLong(value.generation()); out.writeUTF(value.state().name());
                }
                FinanceSerializationEvidence.write(out, List.of(observation), 2);
            }
            storage.write(key, source, 2, before.token(), bytes.toByteArray()); return witnesses.read(source);
        }
        public void close() throws Exception { captureGuard.close(); lifecycleGuard.close(); gate.close(); java.lang.ref.Reference.reachabilityFence(target); }
    }
    public static void main(String[] args) throws Exception {
        Path parent = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode"));
        Path root = Files.createTempDirectory(parent, "serialization-evidence-").toRealPath();
        try (var f = new Fixture(root.resolve("basic"), stage -> { })) {
            check(f.storage.read(f.key, f.source).value().orElseThrow().schema() == 1, "legacy witness stays schema1 before any capture");
            var legacy = f.witnesses.read(f.source); check(legacy.serializations().isEmpty(), "schema1 read has empty observations without migration");
            var capture = f.save(); var history = f.journal.read(f.source); var requests = f.requests.read(f.source);
            var committed = f.record(capture); var observation = committed.serializations().get(0);
            var onEdt = new AtomicReference<Throwable>();
            CareerBridge.onEdt(() -> { try { f.record(capture); } catch (Throwable failure) { onEdt.set(failure); } return null; });
            check(onEdt.get() instanceof Failure failure && failure.reason() == Reason.WRONG_THREAD, "collector refuses all metadata I/O on EDT");
            check(committed.witnesses().equals(legacy.witnesses()), "append never rewrites execution states/UUIDs");
            check(f.storage.read(f.key, f.source).value().orElseThrow().schema() == 3, "new live observation writes explicit schema3 even when qualified facts are unavailable");
            check(f.storage.readPrevious(f.key, f.source, committed.token()).schema() == 1, "legacy committed record preserved as previous, no in-place rewrite");
            check(observation.id().equals(capture.binding().observationId()) && observation.output().equals(capture.binding().bytes()), "capture nonce bound to exact output hash/size");
            check(observation.executionToken().equals(legacy.token()) && observation.requestToken().equals(requests.token()) && observation.journalToken().equals(history.token()), "all three source history tokens persisted");
            check(observation.operations().get(0).capture().operationId().equals(f.proof.operationId()), "exact prepared operation UUID retained");
            check(observation.operations().get(0).capture().fields().status() == FinanceSerializationTracker.Emission.MISSING
                    && observation.operations().get(0).capture().before() == FinanceSerializationTracker.Sample.WRONG_THREAD, "negative/missing synthetic coverage preserved, never promoted");
            check(!observation.operationInclusionProven() && !observation.writeAuthority(), "durable observation never becomes inclusion/authority");
            check(f.record(capture).equals(committed), "same live writer read-only confirmation is idempotent");
            rejects(Reason.CONFLICT, () -> f.witnesses.observeSerialization(f.source, legacy.token(), capture, f.requests, f.journal));
            check(new FinanceExecutionWitnessStore(new CareerSidecarStore(f.root.resolve("execution")), f.key).read(f.source).equals(committed), "restart decodes exact immutable schema3 without objects");
            check(f.journal.read(f.source).equals(history) && f.requests.read(f.source).equals(requests), "contracts/raw receipts never changed");
            check(f.storage.inventoryBinding(f.key.career(), f.key.binding()).tokens().get(f.key.mod()).equals(committed.token()), "existing execution inventory observes new HEAD, no unreviewed parallel root");
            byte[] payload = f.storage.read(f.key, f.source).value().orElseThrow().payload();
            for (String mode : List.of("schema", "format", "trailing", "truncated")) {
                var bad = new CareerSidecarStore(f.root.resolve("bad-" + mode)); byte[] bytes = payload.clone();
                if (mode.equals("format")) bytes[7] = 4;
                if (mode.equals("trailing")) bytes = Arrays.copyOf(bytes, bytes.length + 1);
                if (mode.equals("truncated")) bytes = Arrays.copyOf(bytes, bytes.length - 1);
                bad.write(f.key, f.source, mode.equals("schema") ? 4 : 3, EMPTY, bytes);
                rejects(Reason.CORRUPT, () -> new FinanceExecutionWitnessStore(bad, f.key).read(f.source));
            }
            var duplicateBytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(duplicateBytes)) { FinanceSerializationEvidence.write(out, List.of(observation, observation)); }
            boolean duplicateRejected = false;
            try (var in = new DataInputStream(new ByteArrayInputStream(duplicateBytes.toByteArray()))) { FinanceSerializationEvidence.read(in); }
            catch (IOException expected) { duplicateRejected = true; }
            check(duplicateRejected, "duplicate writer UUIDs rejected during decode");
            boolean noEffectRejected = false;
            try { new FinanceSerializationEvidence.Operation(observation.operations().get(0).capture(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, CareerCommandJournal.State.APPLIED); }
            catch (IllegalArgumentException expected) { noEffectRejected = true; }
            check(noEffectRejected, "observation codec refuses contradictory no-effect witness");
            var originalOp = observation.operations().get(0).capture(); boolean inconsistentRejected = false;
            try { new FinanceSerializationEvidence.Operation(new FinanceSerializationTracker.Operation(originalOp.operationId(), originalOp.intent(), originalOp.before(), originalOp.after(), originalOp.executionToSerializer(),
                    new FinanceSerializationTracker.Fields(FinanceSerializationTracker.Emission.EXPECTED_NUMERIC_ARGUMENTS_OBSERVED, 0, Set.of()), originalOp.edge()),
                    FinanceExecutionWitnessStore.State.MEMORY_APPLIED, CareerCommandJournal.State.APPLIED); }
            catch (IllegalArgumentException expected) { inconsistentRejected = true; }
            check(inconsistentRejected, "numeric matched status cannot be decoded with missing fields/routes");
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> f.witnesses.read(observation.output()));
            check(f.witnesses.inspectRecordedRecovery(observation.output()).revisionChanged(), "output/restore/save-as remains explicit old-anchor recovery");
            check(FinanceExecutionRecovery.inspect(history, committed).get(0).status() == FinanceExecutionRecovery.Status.MEMORY_APPLIED_SAVE_UNPROVEN, "existing operational review still blocks applied with serialization observations");
            check(f.journal.prepare(f.source, history.token(), f.proof.intent()).lease().isEmpty(), "no historical key gets another lease");
            var foreign = new FinanceRequestStore(new CareerSidecarStore(f.root.resolve("foreign")), new Key(UUID.randomUUID(), f.key.binding(), f.key.mod()));
            rejects(Reason.CONFLICT, () -> f.witnesses.observeSerialization(f.source, committed.token(), capture, foreign, f.journal));
            var oldRequest = requests.requests().get(0);
            var later = new FinanceRequest(f.key.career(), f.key.binding(), f.source, f.key.mod(), "later", oldRequest.club(), oldRequest.direction(), oldRequest.amount(), oldRequest.before());
            f.requests.persist(later, requests.token());
            var preparedLater = f.journal.prepare(f.source, history.token(), later.intent());
            var contextLater = f.gate.installContext(f.key.career(), f.key.binding(), 2, f.source, f.primary, f.secondary);
            var armLater = f.witnesses.arm(contextLater, f.journal.executionPermit(preparedLater.lease().orElseThrow(), preparedLater.snapshot().token()), committed.token());
            var doneLater = f.witnesses.complete(armLater.lease(), FinanceExecutionWitnessStore.State.MEMORY_APPLIED, armLater.snapshot().token());
            f.journal.complete(preparedLater.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, preparedLater.snapshot().token());
            check(doneLater.serializations().equals(committed.serializations()) && f.storage.read(f.key, f.source).value().orElseThrow().schema() == 3,
                    "later arm/complete preserves all observations and cannot downgrade schema3");
            check(doneLater.witnesses().size() == 2, "subsequent artificial witness keeps original execution history");
            for (int i = 1; i < FinanceSerializationEvidence.MAX_OBSERVATIONS; i++) f.record(f.save());
            var full = f.witnesses.read(f.source); var extra = f.save();
            rejects(Reason.QUOTA, () -> f.record(extra));
            check(f.witnesses.read(f.source).equals(full), "quota never evicts committed writer observations");
            HookProbe.rootAssigned();
            boolean stale = false; try { f.record(extra); } catch (WrittenSaveVerifier.Failure expected) { stale = true; }
            check(stale && f.witnesses.read(f.source).equals(full), "root/reload invalidation refuses stale opaque capture without changing disk");
        }
        try (var f = new Fixture(root.resolve("legacy-mixed"), stage -> { })) {
            var old = f.seedLegacyObservation();
            check(f.storage.read(f.key, f.source).value().orElseThrow().schema() == 2 && old.serializations().get(0).operations().get(0).capture().qualifications().version() == 0,
                    "previous schema2 reads without qualification facts or silent migration");
            var repeated = f.witnesses.read(f.source);
            check(repeated.equals(old) && f.storage.read(f.key, f.source).value().orElseThrow().schema() == 2, "repeated legacy read preserves original HEAD/schema");
            var mixed = f.record(f.save());
            check(f.storage.read(f.key, f.source).value().orElseThrow().schema() == 3 && mixed.serializations().size() == 2 && mixed.serializations().get(0).equals(old.serializations().get(0)),
                    "new live append upgrades container to3 while old observation payload/facts remain identical");
            check(mixed.serializations().get(1).operations().get(0).capture().qualifications().version() == 1, "new observation has explicit current manifest even with absent metadata");
            check(f.storage.readPrevious(f.key, f.source, mixed.token()).schema() == 2, "old schema2 remains previous committed record, never rewritten in place");
            check(new FinanceExecutionWitnessStore(new CareerSidecarStore(f.root.resolve("execution")), f.key).read(f.source).equals(mixed), "mixed qualification versions reopen unchanged after restart");
            check(FinanceSerializationReview.inspect(mixed, f.source).stream().allMatch(value -> value.blocksInclusion() && value.gaps().contains(FinanceSerializationReview.Gap.LIVE_SERIALIZER_PROFILE_NOT_RECORDED)),
                    "neither old ideal facts nor new missing facts receive borrowed live qualifications");
        }
        try (var f = new Fixture(root.resolve("precommit-file"), stage -> { })) {
            var capture = f.save(); var before = f.witnesses.read(f.source); Files.write(f.file, new byte[]{9, 8, 7, 6});
            boolean changed = false; try { f.record(capture); } catch (IOException expected) { changed = true; }
            check(changed && f.witnesses.read(f.source).equals(before), "changed output bytes before commit refuse capture without disk-history mutation");
        }
        try (var f = new Fixture(root.resolve("no-effect"), stage -> { }, FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED)) {
            var capture = f.save(); var before = f.witnesses.read(f.source);
            rejects(Reason.RECONCILIATION_REQUIRED, () -> f.record(capture));
            check(f.witnesses.read(f.source).equals(before), "contradictory no-effect history stays unchanged");
        }
        for (Stage point : Stage.values()) {
            var enabled = new AtomicBoolean();
            try (var f = new Fixture(root.resolve("fault-" + point), stage -> { if (enabled.get() && stage == point) throw new IOException("Owned observation interruption"); })) {
                var before = f.seedLegacyObservation(); var capture = f.save(); enabled.set(true);
                boolean failed = false; try { f.record(capture); } catch (IOException expected) { failed = true; }
                check(failed, "controlled observation interruption surfaced " + point);
                var reopened = new FinanceExecutionWitnessStore(new CareerSidecarStore(f.root.resolve("execution")), f.key).read(f.source);
                check(reopened.serializations().size() == (point == Stage.HEAD_COMMITTED ? 2 : 1), "only committed HEAD exposes new manifest, no orphan promotion " + point);
                check(reopened.serializations().get(0).equals(before.serializations().get(0)), "schema2 history preserved during interrupted schema3 append " + point);
                check(new CareerSidecarStore(f.root.resolve("execution")).read(f.key, f.source).value().orElseThrow().schema() == (point == Stage.HEAD_COMMITTED ? 3 : 2),
                        "only committed HEAD changes legacy container format during interruption " + point);
                check(reopened.witnesses().equals(before.witnesses()), "interruption never erases/changes execution evidence " + point);
                check(f.journal.prepare(f.source, f.journal.read(f.source).token(), f.proof.intent()).lease().isEmpty(), "interruption never replays effect " + point);
            }
        }
        var mutate = new AtomicBoolean(); var file = new AtomicReference<Path>();
        try (var f = new Fixture(root.resolve("postcommit"), stage -> { if (mutate.get() && stage == Stage.HEAD_COMMITTED) Files.write(file.get(), new byte[]{9, 8, 7, 6}); })) {
            var capture = f.save(); file.set(f.file); mutate.set(true);
            rejects(Reason.OUTCOME_UNKNOWN, () -> f.record(capture));
            var snapshot = f.witnesses.read(f.source);
            check(snapshot.serializations().size() == 1 && snapshot.serializations().get(0).output().equals(capture.binding().bytes()), "postcommit file race preserves historical stream observation, not current-file authority");
        }
        System.out.println("PASS: " + checks + " durable serializer observation checks; owned fixture " + root + "; exceptions are not power-loss tests, no inclusion/authority.");
    }
}
