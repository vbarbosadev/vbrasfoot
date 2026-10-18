package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerSaveAssociations.*;

/** Association metadata over owned arbitrary byte fixtures, not a claim those bytes encode careers. */
public final class CareerSaveAssociationsSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action, String label) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, label + ": " + expected.reason()); return; }
        throw new AssertionError(label);
    }
    private static void invalid(Action action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(label);
    }
    private static Map<String, String> contents(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), Profile.digest(file));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path parent = System.getenv("LOCALAPPDATA") == null ? Files.createDirectories(Path.of("build"))
                : Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"));
        Path fixture = Files.createTempDirectory(parent, "save-association-").toRealPath();
        Path saves = Files.createDirectory(fixture.resolve("sav")), metadata = fixture.resolve("association-metadata");
        byte[] firstBytes = {1, 2, 3, 4}; Path first = saves.resolve("owned test.s22"); Files.write(first, firstBytes);
        var observer = new SaveFileObserver(); var service = new CareerSaveAssociations(metadata);
        var initialFiles = contents(saves); var observed = observer.observe(saves, first.getFileName().toString());
        check(observed.revision().size() == 4 && observed.revision().sha256().equals(Profile.digest(first)), "bounded hash matches owned bytes");
        check(contents(saves).equals(initialFiles) && !Files.exists(metadata), "observe never writes save or metadata");
        invalid(() -> service.create(observed, null), "missing confirmation refused");
        invalid(() -> service.create(observed, Confirmation.ACCEPT_CHANGED_REVISION), "wrong action cannot create career ID");
        check(!Files.exists(metadata), "unconfirmed create has no disk effects");
        var created = service.create(observed, Confirmation.CREATE_NEW_CAREER);
        var loaded = new CareerSaveAssociations(metadata).inspect(created.handle());
        check(loaded.handle().equals(created.handle()) && loaded.token().equals(created.token()) && loaded.revision() == 1 && loaded.parent().isEmpty(), "explicit UUID binding persists across service instances");
        check(service.assess(loaded, observed) == (observed.fileKey().isEmpty() ? Match.FILE_IDENTITY_UNAVAILABLE : Match.EXACT_OBSERVATION), "exact observation is not guessed from hash alone");
        check(contents(saves).equals(initialFiles), "association creation never modifies original save bytes");
        rejects(Reason.CONFLICT, () -> service.acceptRevision(created.handle(), created.token(), observed, Confirmation.ACCEPT_CHANGED_REVISION), "repeated confirmation cannot manufacture file identity or metadata change");
        var modStore = new CareerSidecarStore(fixture.resolve("mod-data"));
        var modKey = new Key(created.handle().career(), created.handle().binding(), "br.owned.contracts");
        var modData = modStore.write(modKey, observed.revision(), 1, EMPTY, new byte[] {99});
        Path copyPath = saves.resolve("copied.s22"); Files.copy(first, copyPath);
        var copied = observer.observe(saves, copyPath.getFileName().toString());
        check(service.assess(created, copied) != Match.EXACT_OBSERVATION, "byte-identical copy never automatically matches binding");
        rejects(Reason.RECONCILIATION_REQUIRED, () -> service.rename(created.handle(), created.token(), copied, Confirmation.RENAME_SAME_FILE), "copy cannot be accepted as same-file rename");
        var fork = service.fork(created.handle(), created.token(), copied, Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA);
        check(!fork.handle().career().equals(created.handle().career()) && !fork.handle().binding().equals(created.handle().binding()), "explicit copy fork gets new logical IDs");
        check(fork.parent().orElseThrow().handle().equals(created.handle()) && fork.parent().orElseThrow().token().equals(created.token())
                && fork.parent().orElseThrow().save().equals(created.save()), "fork lineage records exact source observation, not merely parent UUID");
        check(modStore.read(new Key(fork.handle().career(), fork.handle().binding(), "br.owned.contracts"), copied.revision()).value().isEmpty(), "fork never clones mod contracts or command receipts implicitly");
        Path renamedPath = saves.resolve("renamed.s22"); Files.move(first, renamedPath);
        var renamed = observer.observe(saves, renamedPath.getFileName().toString());
        Association moved = created;
        boolean nativeIdentity = !observed.fileKey().isEmpty() && observed.fileKey().equals(renamed.fileKey());
        if (nativeIdentity || observed.fileKey().isEmpty() || renamed.fileKey().isEmpty()) {
            check(service.assess(created, renamed) == (nativeIdentity ? Match.NAME_CHANGED : Match.NAME_CHANGED_IDENTITY_UNAVAILABLE), "rename evidence strength explicit");
            Confirmation renameChoice = nativeIdentity ? Confirmation.RENAME_SAME_FILE : Confirmation.ASSERT_RENAME_WITHOUT_FILE_IDENTITY;
            if (!nativeIdentity) System.out.println("SKIP strong native file-identity rename: fileKey unavailable; explicit assertion branch tested instead.");
            if (!nativeIdentity) rejects(Reason.RECONCILIATION_REQUIRED, () -> service.rename(created.handle(), created.token(), renamed, Confirmation.RENAME_SAME_FILE), "no strong identity invented when fileKey is unavailable");
            moved = service.rename(created.handle(), created.token(), renamed, renameChoice);
            check(moved.handle().equals(created.handle()) && moved.revision() == 2 && moved.name().equals("renamed.s22"), "explicit rename preserves IDs and advances metadata revision");
            check(!Files.exists(first) && Arrays.equals(Files.readAllBytes(renamedPath), firstBytes), "metadata rename does not recreate/delete/mutate save files");
            rejects(Reason.CONFLICT, () -> service.rename(created.handle(), created.token(), renamed, renameChoice), "stale metadata token refused");
        } else System.out.println("SKIP same-file rename identity unavailable; not accepted as passed.");
        var current = moved;
        if (current.name().equals("renamed.s22")) {
            Files.write(renamedPath, new byte[] {5, 6, 7}); var updated = observer.observe(saves, "renamed.s22");
            check(service.assess(current, updated) == Match.REVISION_CHANGED, "changed revision requires decision");
            check(service.inspect(current.handle()).save().equals(current.save()), "assess does not adopt new bytes");
            var adopted = service.acceptRevision(current.handle(), current.token(), updated, Confirmation.ACCEPT_CHANGED_REVISION);
            check(adopted.handle().equals(current.handle()) && adopted.save().equals(updated.revision()), "explicit confirmed revision updates metadata only");
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> modStore.read(modKey, updated.revision()), "association revision does not silently migrate mod payload");
            check(modStore.read(modKey, observed.revision()).token().equals(modData.token()), "old mod record preserved for separate reconciliation");
            Files.write(renamedPath, firstBytes); var restored = observer.observe(saves, "renamed.s22");
            var reverted = service.restorePrevious(adopted.handle(), adopted.token(), restored, Confirmation.RESTORE_RECORDED_PREVIOUS);
            check(reverted.handle().equals(adopted.handle()) && reverted.revision() == adopted.revision() + 1 && reverted.save().equals(observed.revision()), "known previous save accepted as new monotonic metadata revision only");
            Files.write(renamedPath, new byte[] {8, 8}); var unknown = observer.observe(saves, "renamed.s22");
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> service.restorePrevious(reverted.handle(), reverted.token(), unknown, Confirmation.RESTORE_RECORDED_PREVIOUS), "unknown restore bytes never selected from orphans/history by guessing");
            Files.write(renamedPath, firstBytes); var fresh = observer.observe(saves, "renamed.s22");
            Files.setLastModifiedTime(renamedPath, FileTime.fromMillis(Files.getLastModifiedTime(renamedPath).toMillis() + 5000));
            rejects(Reason.CONFLICT, () -> service.acceptRevision(reverted.handle(), reverted.token(), fresh, Confirmation.ACCEPT_CHANGED_REVISION), "stale observation refused before metadata write");
        }
        Path otherDirectory = Files.createDirectory(fixture.resolve("other-sav")); Files.write(otherDirectory.resolve("copied.s22"), firstBytes);
        check(service.assess(fork, observer.observe(otherDirectory, "copied.s22")) == Match.DIRECTORY_CHANGED, "moving or copying entire save directory not silently same binding");
        for (String name : List.of("../owned.s22", "C:\\owned.s22", "nested/a.s22", "nested\\a.s22", "owned.info", "con.s22", "aux.sbck", "x\u0000.s22", ".s22", "owned.s22:stream"))
            invalid(() -> observer.observe(saves, name), "unsafe save name refused");
        invalid(() -> observer.observe(saves.resolve("child/.."), "copied.s22"), "nonnormalized root not reinterpreted");
        var changing = new SaveFileObserver(path -> Files.write(path, new byte[] {8, 7, 6, 5}));
        rejects(Reason.CONFLICT, () -> changing.observe(saves, "copied.s22"), "file mutation between hash passes refused");
        Path timeFile = saves.resolve("timestamp.s22"); Files.write(timeFile, new byte[] {1});
        var timeObservation = observer.observe(saves, "timestamp.s22");
        try {
            var createdTime = Files.readAttributes(timeFile, java.nio.file.attribute.BasicFileAttributes.class).creationTime();
            Files.setAttribute(timeFile, "basic:creationTime", FileTime.fromMillis(createdTime.toMillis() + 5000));
            if (!Files.readAttributes(timeFile, java.nio.file.attribute.BasicFileAttributes.class).creationTime().equals(createdTime))
                rejects(Reason.CONFLICT, () -> observer.verify(timeObservation), "creation-time change cannot hide behind equal hash and modified time");
            else System.out.println("SKIP creation-time mutation unavailable; not accepted as passed.");
        } catch (UnsupportedOperationException | FileSystemException denied) { System.out.println("SKIP creation-time mutation unavailable; not accepted as passed."); }
        var onEdt = new AtomicReference<Throwable>();
        javax.swing.SwingUtilities.invokeAndWait(() -> { try { observer.observe(saves, "copied.s22"); } catch (Throwable expected) { onEdt.set(expected); } });
        check(onEdt.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "save hashing refused on EDT");
        var otherMod = new Key(UUID.randomUUID(), UUID.randomUUID(), "br.owned.other");
        invalid(() -> modStore.inspectAssociation(otherMod), "admin read cannot bypass ordinary mod revision guard");
        invalid(() -> modStore.changeAssociationRevision(otherMod, observed.revision(), copied.revision(), 1, EMPTY, new byte[0]), "mod namespace cannot use association transition privilege");
        var authority = new CareerSidecarStore(metadata);
        var associationKey = new Key(fork.handle().career(), fork.handle().binding(), ASSOCIATION_OWNER);
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> authority.changeAssociationRevision(associationKey, new SaveRevision("0".repeat(64), 0), fork.save(), 1, fork.token(), new byte[] {1}), "old association revision checked under writer lock");
        Path hardlink = saves.resolve("hardlinked.s22");
        try {
            Files.createLink(hardlink, copyPath);
            var copyNow = observer.observe(saves, "copied.s22");
            var refreshedFork = service.acceptRevision(fork.handle(), fork.token(), copyNow, Confirmation.ACCEPT_CHANGED_REVISION);
            var alias = observer.observe(saves, "hardlinked.s22");
            Confirmation strength = alias.fileKey().isEmpty() ? Confirmation.ASSERT_RENAME_WITHOUT_FILE_IDENTITY : Confirmation.RENAME_SAME_FILE;
            rejects(Reason.CONFLICT, () -> service.rename(refreshedFork.handle(), refreshedFork.token(), alias, strength), "hardlink with existing old path is not rename");
            rejects(Reason.RECONCILIATION_REQUIRED, () -> service.fork(refreshedFork.handle(), refreshedFork.token(), alias, Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA), "isSameFile rejects hardlink even when fileKey is unavailable");
        } catch (UnsupportedOperationException | FileSystemException unsupported) { System.out.println("SKIP owned hardlink creation unavailable; not accepted as passed."); }
        Path raceFile = saves.resolve("race.s22"); Files.write(raceFile, new byte[] {1});
        var raceBinding = service.create(observer.observe(saves, "race.s22"), Confirmation.CREATE_NEW_CAREER);
        Files.write(raceFile, new byte[] {2}); var raceObservation = observer.observe(saves, "race.s22");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1); var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) results.add(executor.submit(() -> {
                start.await();
                try { service.acceptRevision(raceBinding.handle(), raceBinding.token(), raceObservation, Confirmation.ACCEPT_CHANGED_REVISION); return true; }
                catch (Failure expected) { if (expected.reason() != Reason.CONFLICT && expected.reason() != Reason.BUSY) throw expected; return false; }
            }));
            start.countDown(); int winners = 0; for (var result : results) if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) winners++;
            check(winners == 1 && service.inspect(raceBinding.handle()).revision() == 2, "concurrent confirmations have one metadata CAS winner");
        } finally { executor.shutdownNow(); }
        var schemaHandle = new Handle(UUID.randomUUID(), UUID.randomUUID());
        authority.write(new Key(schemaHandle.career(), schemaHandle.binding(), ASSOCIATION_OWNER), observed.revision(), 2, EMPTY, new byte[0]);
        rejects(Reason.CORRUPT, () -> service.inspect(schemaHandle), "association unknown schema never migrated implicitly");
        var malformedHandle = new Handle(UUID.randomUUID(), UUID.randomUUID());
        authority.write(new Key(malformedHandle.career(), malformedHandle.binding(), ASSOCIATION_OWNER), observed.revision(), 1, EMPTY, new byte[] {1, 2});
        rejects(Reason.CORRUPT, () -> service.inspect(malformedHandle), "malformed association metadata not accepted merely because envelope checksum is valid");
        if (System.getProperty("os.name").startsWith("Windows") && fixture.toString().matches("[A-Za-z]:\\\\[A-Za-z0-9 _\\\\.-]+")) {
            Path junction = fixture.resolve("junction-sav");
            var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), saves.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("owned junction creation timeout"); }
            process.getInputStream().readAllBytes();
            if (process.exitValue() == 0) {
                var beforeJunction = contents(saves);
                rejects(Reason.UNSAFE_PATH, () -> observer.observe(junction, "race.s22"), "save observation refuses Windows junction");
                check(contents(saves).equals(beforeJunction), "junction target saves unchanged");
            } else System.out.println("SKIP owned junction unavailable; not accepted as passed.");
        }
        for (Stage stage : Stage.values()) {
            Path faultRoot = fixture.resolve("fault-" + stage); var faults = new CareerSaveAssociations(new CareerSidecarStore(faultRoot, point -> { if (point == stage) throw new IOException("owned interruption"); }), observer);
            var exact = observer.observe(saves, "copied.s22"); boolean interrupted = false;
            try { faults.create(exact, Confirmation.CREATE_NEW_CAREER); }
            catch (CreationFailure expected) {
                interrupted = true;
                if (stage == Stage.HEAD_COMMITTED) check(faults.inspect(expected.attempted()).save().equals(exact.revision()), "post-commit create retains attempted handle for explicit inspection");
                else rejects(Reason.NOT_FOUND, () -> faults.inspect(expected.attempted()), "uncommitted metadata not promoted from orphan");
            }
            check(interrupted && Arrays.equals(Files.readAllBytes(copyPath), new byte[] {8, 7, 6, 5}), "association failure never alters save " + stage);
        }
        System.out.println("PASS: " + checks + " explicit association checks; owned byte fixtures " + fixture + "; not decoded-career identity or public API.");
    }
}
