package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerAssociationCoordinator.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Synthetic decoder markers/roots and owned metadata. UI init/production career qualification not claimed. */
public final class CareerAssociationCoordinatorSelfTest {
    private static int checks;
    private interface ActionCall { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, ActionCall action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static void invalid(ActionCall action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError("Expected invalid authority or selection");
    }
    private static void load(Path path, Object primary, Object secondary) throws Exception {
        HookProbe.enterLoad(path.getFileName().toString(), false); HookProbe.loadSource(path.toString());
        var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(path))); trace.readAllBytes(); trace.close();
        HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.exitLoad(true);
    }
    private static long heads(Path root) throws IOException {
        if (!Files.exists(root)) return 0;
        try (var files = Files.walk(root)) { return files.filter(path -> path.getFileName().toString().equals("HEAD")).count(); }
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "career-association-coordinator-").toRealPath();
        Path saves = Files.createDirectory(root.resolve("sav")), metadata = root.resolve("metadata"), journals = root.resolve("journals");
        byte[] first = {1, 2, 3}; Path source = Files.write(saves.resolve("owned.s22"), first);
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(metadata); var journalStore = new CareerSidecarStore(journals);
        Object primary = new Object(), secondary = new Object();
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        var owners = List.of("br.owned.second", "br.owned.first");
        var coordinator = new CareerAssociationCoordinator(saves, associations, journalStore, observer, verifier, owners);
        var foreign = new CareerAssociationCoordinator(saves, associations, journalStore, observer, verifier, owners);
        check(!Files.exists(metadata) && !Files.exists(journals), "constructing coordinator does not create a career registry or journal");
        invalid(() -> new CareerAssociationCoordinator(saves, associations, journalStore, observer, verifier, List.of("a", "a")));
        var state = new CareerLifecycle();
        try (var guard = HookProbe.attachLifecycle(state)) {
            boolean missing = false;
            try { coordinator.review(Optional.empty()); } catch (LoadedSaveVerifier.Failure expected) { missing = expected.reason() == LoadedSaveVerifier.Reason.NO_PROVENANCE; }
            check(missing && !Files.exists(metadata), "startup without decoder proof cannot create/select identity");
            load(source, primary, secondary);
            var fresh = coordinator.review(Optional.empty());
            check(fresh.status() == Status.ASSOCIATION_REQUIRED && fresh.association().isEmpty() && fresh.journals().isEmpty(), "no handle means no identity inference or invented empty journal inventory");
            check(!fresh.decoded().initializationPathObserved(), "synthetic unqualified initialization preserved in report");
            check(!Files.exists(metadata) && !Files.exists(journals), "read-only review leaves storage absent");
            String text = CareerAssociationPanel.report(fresh);
            check(text.contains("SEM AUTORIDADE DE ESCRITA") && text.contains("Nao e inventario global") && text.contains("false"), "UI report discloses no write authority, binding-only inventory and unqualified initialization");
            invalid(() -> foreign.confirm(fresh, Action.CREATE_NEW, Optional.empty()));
            var handle = coordinator.confirm(fresh, Action.CREATE_NEW, Optional.empty());
            check(heads(metadata) == 1 && !Files.exists(journals) && Arrays.equals(Files.readAllBytes(source), first), "explicit metadata confirmation creates one handle, never journal/game save");
            rejects(Reason.CONFLICT, () -> coordinator.confirm(fresh, Action.CREATE_NEW, Optional.empty()));
            var selected = coordinator.review(Optional.of(handle));
            check(selected.status() == Status.READ_ONLY_LINKED && selected.journals().size() == 2, "explicit selected handle links read-only and reviews configured owners");
            check(selected.journals().stream().allMatch(row -> row.recovery().orElseThrow().status() == CareerCommandJournal.RecoveryStatus.EMPTY), "absent configured journals remain absent and classified empty");
            check(!Files.exists(journals), "registry assessment never creates empty journal files");
            rejects(Reason.CONFLICT, () -> coordinator.confirm(selected, Action.CREATE_NEW, Optional.empty()));
            var known = associations.inspect(handle); var key = new Key(handle.career(), handle.binding(), owners.get(0));
            var journal = new CareerCommandJournal(journalStore, key);
            var intent = new CareerCommandJournal.Intent("pending", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
            var beforePending = coordinator.review(Optional.of(handle));
            var prepared = journal.prepare(known.save(), EMPTY, intent);
            rejects(Reason.CONFLICT, () -> coordinator.confirm(beforePending, Action.QUARANTINE_PENDING, Optional.of(owners.get(0))));
            var pendingReview = coordinator.review(Optional.of(handle));
            check(pendingReview.journals().stream().filter(row -> row.owner().equals(owners.get(0))).findFirst().orElseThrow().recovery().orElseThrow().status()
                    == CareerCommandJournal.RecoveryStatus.UNCERTAIN, "startup inspection includes uncertain registered command");
            var sameHandle = coordinator.confirm(pendingReview, Action.QUARANTINE_PENDING, Optional.of(owners.get(0)));
            var recovered = journal.read(known.save());
            check(sameHandle.equals(handle) && recovered.receipts().equals(prepared.snapshot().receipts()), "explicit quarantine preserves operation IDs/digests/unknown semantics and association");
            rejects(Reason.CONFLICT, () -> journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, recovered.token()));
            rejects(Reason.RECONCILIATION_REQUIRED, () -> journal.prepare(known.save(), recovered.token(), new CareerCommandJournal.Intent("next", CareerCommandGate.Operation.FINANCE_CREDIT, "d".repeat(64))));
            var wrongOwnerReview = coordinator.review(Optional.of(handle));
            invalid(() -> coordinator.confirm(wrongOwnerReview, Action.QUARANTINE_PENDING, Optional.of("not.registered")));
            check(journal.read(known.save()).token().equals(recovered.token()), "unknown owner cannot alter a journal");
            var beforeDisabled = coordinator.review(Optional.of(handle));
            var disabledKey = new Key(handle.career(), handle.binding(), "br.owned.disabled");
            var disabledJournal = new CareerCommandJournal(journalStore, disabledKey);
            var disabledPending = disabledJournal.prepare(known.save(), EMPTY, new CareerCommandJournal.Intent("old-mod", CareerCommandGate.Operation.FINANCE_CREDIT, "e".repeat(64)));
            rejects(Reason.CONFLICT, () -> coordinator.confirm(beforeDisabled, Action.QUARANTINE_PENDING, Optional.of(owners.get(0))));
            var disabledReview = coordinator.review(Optional.of(handle));
            var disabledRow = disabledReview.journals().stream().filter(row -> row.owner().equals(disabledKey.mod())).findFirst().orElseThrow();
            check(!disabledRow.configured() && disabledRow.recovery().orElseThrow().status() == CareerCommandJournal.RecoveryStatus.UNCERTAIN,
                    "existing unselected journal included in explicit binding review with uncertainty intact");
            check(CareerAssociationPanel.report(disabledReview).contains("[existente, nao selecionado]"), "UI distinguishes existing disabled owner from configured selection");
            coordinator.confirm(disabledReview, Action.QUARANTINE_PENDING, Optional.of(disabledKey.mod()));
            var disabledDone = disabledJournal.read(known.save());
            check(!disabledDone.token().equals(disabledPending.snapshot().token()) && disabledDone.receipts().equals(disabledPending.snapshot().receipts()),
                    "explicit reviewed quarantine supports disabled journal without executing or loading its mod");
            Files.write(source, new byte[] {4, 5}); load(source, primary, secondary);
            var revised = coordinator.review(Optional.of(handle));
            check(revised.status() == Status.REVISION_REVIEW_REQUIRED && revised.journals().stream().anyMatch(row -> row.recovery().orElseThrow().revisionChanged()), "changed source keeps committed old anchors separate from actual decoder bytes");
            coordinator.confirm(revised, Action.ACCEPT_REVISION, Optional.empty());
            var afterRevision = coordinator.review(Optional.of(handle));
            check(afterRevision.journals().stream().anyMatch(row -> row.recovery().orElseThrow().revisionChanged()), "administrative review exposes actual committed old anchor without migrating journal");
            coordinator.confirm(afterRevision, Action.QUARANTINE_PENDING, Optional.of(owners.get(0)));
            check(journal.read(known.save()).token().equals(recovered.token()), "quarantine across anchor mismatch preserves existing unknown and does not reanchor");
            Files.write(source, first); load(source, primary, secondary);
            coordinator.confirm(coordinator.review(Optional.of(handle)), Action.RESTORE_PREVIOUS, Optional.empty());
            check(associations.inspect(handle).save().equals(known.save()) && journal.read(known.save()).token().equals(recovered.token()), "explicit metadata restore never rolls back unknown command receipts");
            Path copy = Files.copy(source, saves.resolve("copy.s22")); load(copy, primary, secondary);
            var copied = coordinator.review(Optional.of(handle));
            check(copied.status() == Status.ASSOCIATION_CONFLICT, "equal-byte copied path not silently the selected binding");
            var fork = coordinator.confirm(copied, Action.FORK_COPY, Optional.empty());
            check(!fork.equals(handle) && associations.inspect(fork).parent().orElseThrow().handle().equals(handle), "fork gets explicit independent IDs and precise lineage");
            check(coordinator.review(Optional.of(fork)).journals().stream().allMatch(row -> row.recovery().orElseThrow().snapshot().receipts().isEmpty()), "fork does not clone old journal or unresolved effects");
            Path renamed = Files.move(copy, saves.resolve("renamed.s22")); load(renamed, primary, secondary);
            var renameReview = coordinator.review(Optional.of(fork));
            Action rename = renameReview.match().orElseThrow() == CareerSaveAssociations.Match.NAME_CHANGED ? Action.RENAME_PROVEN : Action.ASSERT_RENAME;
            check(coordinator.confirm(renameReview, rename, Optional.empty()).equals(fork) && associations.inspect(fork).name().equals("renamed.s22"), "explicit strength-aware rename preserves logical metadata IDs");
            var staleReview = coordinator.review(Optional.empty()); long beforeHeads = heads(metadata);
            load(renamed, primary, secondary);
            rejects(Reason.CONFLICT, () -> coordinator.confirm(staleReview, Action.CREATE_NEW, Optional.empty()));
            check(heads(metadata) == beforeHeads, "same-byte same-object synthetic reload still revokes an older review without metadata write");
            var saveInvalidated = coordinator.review(Optional.empty()); HookProbe.enterSave("owned"); HookProbe.exitSave();
            boolean revoked = false;
            try { coordinator.confirm(saveInvalidated, Action.CREATE_NEW, Optional.empty()); } catch (LoadedSaveVerifier.Failure expected) { revoked = expected.reason() == LoadedSaveVerifier.Reason.NO_PROVENANCE; }
            check(revoked && heads(metadata) == beforeHeads, "save entry revokes review before any new metadata identity can be generated");
            load(renamed, primary, secondary);
            var raceReview = coordinator.review(Optional.empty()); var executor = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1); var results = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 2; i++) results.add(executor.submit(() -> {
                    start.await();
                    try { coordinator.confirm(raceReview, Action.CREATE_NEW, Optional.empty()); return true; }
                    catch (Failure expected) { if (expected.reason() != Reason.BUSY && expected.reason() != Reason.CONFLICT) throw expected; return false; }
                }));
                start.countDown(); int winners = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) winners++;
                check(winners == 1 && heads(metadata) == beforeHeads + 1, "one reviewed confirmation cannot allocate twice under concurrent clicks");
            } finally { executor.shutdownNow(); }
            var edtFailure = new AtomicReference<Throwable>();
            SwingUtilities.invokeAndWait(() -> { try { coordinator.review(Optional.of(fork)); } catch (Throwable expected) { edtFailure.set(expected); } });
            check(edtFailure.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "coordinator filesystem review refused on EDT");
            var schemaKey = new Key(fork.career(), fork.binding(), owners.get(1));
            journalStore.write(schemaKey, associations.inspect(fork).save(), 2, EMPTY, new byte[0]);
            check(coordinator.review(Optional.of(fork)).journals().stream().anyMatch(row -> row.failure().orElse(null) == Reason.CORRUPT), "corrupt/unknown-schema configured journal exposed, never treated as empty");
            Path orphanDirectory = Files.createDirectories(journals.resolve(fork.career().toString()).resolve(fork.binding().toString()).resolve("br.owned.orphan"));
            Files.writeString(orphanDirectory.resolve("r-" + UUID.randomUUID() + ".bin"), "owned uncommitted record");
            var orphanReview = coordinator.review(Optional.of(fork));
            var orphanRow = orphanReview.journals().stream().filter(row -> row.owner().equals("br.owned.orphan")).findFirst().orElseThrow();
            check(orphanRow.failure().orElseThrow() == Reason.RECONCILIATION_REQUIRED && orphanRow.recovery().isEmpty(), "existing uncommitted journal cannot masquerade as absent clean history");
            rejects(Reason.CONFLICT, () -> coordinator.confirm(orphanReview, Action.QUARANTINE_PENDING, Optional.of("br.owned.orphan")));
            var atCapacity = new ArrayList<String>();
            for (int i = 0; i < MAX_BINDING_NAMESPACES; i++) atCapacity.add("br.owned.configured" + i);
            var unionOverflow = new CareerAssociationCoordinator(saves, associations, journalStore, observer, verifier, atCapacity);
            rejects(Reason.QUOTA, () -> unionOverflow.review(Optional.of(fork)));
            check(Arrays.equals(Files.readAllBytes(source), first) && Arrays.equals(Files.readAllBytes(renamed), first), "all coordinated actions preserve owned save bytes");
        } finally { state.close(); }
        System.out.println("PASS: " + checks + " association coordinator checks; owned fixture " + root + "; no command context installed or original initialized UI qualified.");
    }
}
