package br.vbrasfoot;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Synthetic owned directories only; never opens or writes game saves. */
public final class CareerSidecarStoreSelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action, String label) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, label + ": " + expected.reason()); return; }
        throw new AssertionError(label);
    }
    private static Key key(String mod) { return new Key(UUID.randomUUID(), UUID.randomUUID(), mod); }
    private static Path directory(Path root, Key key) { return root.resolve(key.career().toString()).resolve(key.binding().toString()).resolve(key.mod()); }
    private static Map<String, String> files(Path root) throws Exception {
        var result = new TreeMap<String, String>(); if (!Files.exists(root)) return result;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) result.put(root.relativize(path).toString(), Profile.digest(path));
        }
        return result;
    }
    private static Path currentRecord(Path directory) throws Exception {
        try (var input = new DataInputStream(Files.newInputStream(directory.resolve("HEAD")))) {
            input.readInt(); input.readInt(); UUID id = new UUID(input.readLong(), input.readLong()); return directory.resolve("r-" + id + ".bin");
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("owned-lock-child")) {
            Path lock = Path.of(args[1]);
            if (!Files.isRegularFile(lock.getParent().resolve("owned-child.marker"))) throw new IllegalArgumentException("Owned fixture marker required");
            try (var channel = FileChannel.open(lock, StandardOpenOption.WRITE); var held = channel.lock()) {
                System.out.println("OWNED_LOCK_READY"); System.out.flush(); if (System.in.read() != 42) throw new AssertionError("owned release missing");
            }
            return;
        }
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Path.of(local, "Temp", "opencode");
        Files.createDirectories(parent); Path fixture = Files.createTempDirectory(parent, "sidecar-core-").toRealPath();
        Path root = fixture.resolve("store"); var store = new CareerSidecarStore(root); var key = key("br.owned.test");
        var save = new SaveRevision("a".repeat(64), 123); var changedSave = new SaveRevision("b".repeat(64), 123);
        check(store.read(key, save).token().equals(EMPTY) && !Files.exists(root), "empty read creates no files");
        byte[] payload = {1, 2, 3}; var first = store.write(key, save, 1, EMPTY, payload); payload[0] = 9;
        check(first.value().orElseThrow().payload()[0] == 1 && first.value().orElseThrow().revision() == 1, "write detaches caller payload");
        byte[] exposed = first.value().orElseThrow().payload(); exposed[0] = 8;
        check(first.value().orElseThrow().payload()[0] == 1, "returned payload detached too");
        var reloaded = new CareerSidecarStore(root).read(key, save);
        check(Arrays.equals(reloaded.value().orElseThrow().payload(), new byte[] {1, 2, 3}) && reloaded.token().equals(first.token()), "reopen persists exact bytes/token");
        rejects(Reason.CONFLICT, () -> store.write(key, save, 1, EMPTY, new byte[] {99}), "stale expected token refused");
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> store.read(key, changedSave), "changed save never silently adopts sidecar");
        rejects(Reason.SAVE_REVISION_MISMATCH, () -> store.write(key, changedSave, 1, first.token(), new byte[] {99}), "write cannot silently rebind to other save bytes");
        var second = store.write(key, save, 2, first.token(), new byte[] {4, 5});
        check(second.value().orElseThrow().revision() == 2 && second.previousRecorded(), "second write records previous generation");
        check(Arrays.equals(store.readPrevious(key, save, second.token()).payload(), new byte[] {1, 2, 3}), "previous payload retained");
        rejects(Reason.CONFLICT, () -> store.readPrevious(key, save, first.token()), "old previous-view token refused");
        Path dir = directory(root, key), current = currentRecord(dir);
        byte[] currentBefore = Files.readAllBytes(current); Files.write(current, new byte[] {7});
        rejects(Reason.CORRUPT, () -> store.read(key, save), "corrupt current not silently replaced by backup");
        check(store.readPrevious(key, save, second.token()).schema() == 1, "explicit previous inspect still available");
        var recovered = store.recoverPrevious(key, save, second.token());
        check(recovered.value().orElseThrow().revision() == 3 && Arrays.equals(recovered.value().orElseThrow().payload(), new byte[] {1, 2, 3}), "explicit recovery is new monotonic revision");
        check(Arrays.equals(Files.readAllBytes(current), new byte[] {7}) && currentBefore.length > 3, "corrupt original preserved for investigation");
        rejects(Reason.CONFLICT, () -> store.recoverPrevious(key, save, second.token()), "same recovery intent cannot apply twice with old token");
        var foreign = new Key(key.career(), key.binding(), "br.owned.other");
        check(store.read(foreign, save).value().isEmpty(), "mod namespace isolated");
        Path foreignDir = directory(root, foreign); Files.createDirectories(foreignDir);
        Files.copy(dir.resolve("HEAD"), foreignDir.resolve("HEAD"));
        try (var all = Files.newDirectoryStream(dir, "r-*.bin")) { for (Path record : all) Files.copy(record, foreignDir.resolve(record.getFileName())); }
        rejects(Reason.CORRUPT, () -> store.read(foreign, save), "copied envelope ownership mismatch refused");
        for (String name : List.of("../other", "..", "a/b", "a\\b", "CON", "con.txt", "aux", "x.", "x:", "a\u0000b")) {
            boolean refused = false; try { key(name); } catch (IllegalArgumentException expected) { refused = true; } check(refused, "unsafe mod name refused");
        }
        var before = files(root);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { rejects(Reason.WRONG_THREAD, () -> store.read(key, save), "read refuses EDT");
                rejects(Reason.WRONG_THREAD, () -> store.write(key, save, 1, recovered.token(), new byte[0]), "write refuses EDT"); }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        check(files(root).equals(before), "refused EDT operations create/change nothing");
        for (Stage stage : Stage.values()) {
            var crashKey = key("br.owned.crash"); var baseline = store.write(crashKey, save, 1, EMPTY, new byte[] {10});
            var failing = new CareerSidecarStore(root, observed -> { if (observed == stage) throw new IOException("owned injected interruption"); });
            boolean failed = false;
            try { failing.write(crashKey, save, 1, baseline.token(), new byte[] {20}); }
            catch (IOException expected) { failed = true; if (stage == Stage.HEAD_COMMITTED) check(expected instanceof Failure f && f.reason() == Reason.OUTCOME_UNKNOWN, "post-commit error is uncertain, not falsely unapplied"); }
            check(failed, "injected stage failure observed " + stage);
            var observed = store.read(crashKey, save);
            check(observed.value().orElseThrow().payload()[0] == (stage == Stage.HEAD_COMMITTED ? 20 : 10), "HEAD alone chooses committed generation " + stage);
            if (stage != Stage.HEAD_COMMITTED) {
                var retry = store.write(crashKey, save, 1, baseline.token(), new byte[] {30});
                check(retry.value().orElseThrow().revision() == 2 && retry.value().orElseThrow().payload()[0] == 30, "orphan never promoted, fresh explicit CAS retry works");
            }
        }
        var corruptKey = key("br.owned.corrupt"); var healthy = store.write(corruptKey, save, 1, EMPTY, new byte[] {1});
        Path corruptDir = directory(root, corruptKey); Files.write(corruptDir.resolve("HEAD"), new byte[] {0, 1});
        var corruptBefore = files(corruptDir);
        rejects(Reason.CORRUPT, () -> store.read(corruptKey, save), "HEAD truncation refused");
        rejects(Reason.CORRUPT, () -> store.recoverPrevious(corruptKey, save, healthy.token()), "corrupt HEAD never scans/promotes latest record");
        check(files(corruptDir).equals(corruptBefore), "corrupt HEAD preserved, no recovery overwrite");
        var unknownKey = key("br.owned.unknown"); var initial = store.write(unknownKey, save, 1, EMPTY, new byte[] {1});
        Files.writeString(directory(root, unknownKey).resolve("user-work.txt"), "preserve");
        rejects(Reason.UNSAFE_PATH, () -> store.write(unknownKey, save, 1, initial.token(), new byte[] {2}), "unfamiliar directory entry preserved, not deleted");
        var quotaKey = key("br.owned.quota"); var quotaView = store.write(quotaKey, save, 1, EMPTY, new byte[] {1});
        Path quotaDir = directory(root, quotaKey);
        for (int i = 0; i < MAX_FILES; i++) Files.createFile(quotaDir.resolve("r-" + UUID.randomUUID() + ".bin"));
        rejects(Reason.QUOTA, () -> store.write(quotaKey, save, 1, quotaView.token(), new byte[] {2}), "orphan/history count bounded");
        check(store.read(quotaKey, save).value().orElseThrow().payload()[0] == 1, "quota failure keeps current data readable");
        var bytesKey = key("br.owned.bytes"); var bytesView = store.write(bytesKey, save, 1, EMPTY, new byte[] {1});
        Path huge = directory(root, bytesKey).resolve("r-" + UUID.randomUUID() + ".bin");
        try (var sparse = FileChannel.open(huge, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            sparse.position(MAX_BYTES); sparse.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
        }
        rejects(Reason.QUOTA, () -> store.write(bytesKey, save, 1, bytesView.token(), new byte[] {2}), "byte quota refuses orphan growth");
        check(store.read(bytesKey, save).value().orElseThrow().payload()[0] == 1, "byte quota cannot select orphan or damage current record");
        var maxKey = key("br.owned.max"); byte[] maximum = new byte[MAX_PAYLOAD]; maximum[MAX_PAYLOAD - 1] = 42;
        store.write(maxKey, save, 1, EMPTY, maximum);
        check(store.read(maxKey, save).value().orElseThrow().payload()[MAX_PAYLOAD - 1] == 42, "exact maximum payload round-trips");
        var missingKey = key("br.owned.missing"); store.write(missingKey, save, 1, EMPTY, new byte[] {1});
        Files.delete(currentRecord(directory(root, missingKey))); // Exact owned corruption fixture only.
        rejects(Reason.CORRUPT, () -> store.read(missingKey, save), "missing committed record refused, never empty");
        var typeKey = key("br.owned.type"); Path typeDir = directory(root, typeKey); Files.createDirectories(typeDir.resolve("HEAD"));
        rejects(Reason.UNSAFE_PATH, () -> store.read(typeKey, save), "HEAD directory refused");
        boolean unnormalized = false;
        try { new CareerSidecarStore(root.resolve("child/../other")); } catch (IllegalArgumentException expected) { unnormalized = true; }
        check(unnormalized, "non-normalized root not silently reinterpreted");
        boolean tooLarge = false; try { store.write(key, save, 1, recovered.token(), new byte[MAX_PAYLOAD + 1]); } catch (IllegalArgumentException expected) { tooLarge = true; }
        check(tooLarge, "payload bound checked before writing");
        var lockKey = key("br.owned.lock"); var lockView = store.write(lockKey, save, 1, EMPTY, new byte[] {1});
        Path lockDir = directory(root, lockKey), lockPath = lockDir.resolve(".lock");
        try (var lockChannel = FileChannel.open(lockPath, StandardOpenOption.WRITE); var held = lockChannel.lock()) {
            rejects(Reason.BUSY, () -> store.write(lockKey, save, 1, lockView.token(), new byte[] {2}), "same JVM lock conflict refused");
        }
        Files.writeString(lockDir.resolve("owned-child.marker"), "owned fixture only");
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), CareerSidecarStoreSelfTest.class.getName(), "owned-lock-child", lockPath.toString()).redirectErrorStream(true).start();
        try {
            var ready = CompletableFuture.supplyAsync(() -> { try { return new BufferedReader(new InputStreamReader(child.getInputStream())).readLine(); } catch (IOException failure) { throw new UncheckedIOException(failure); } });
            check("OWNED_LOCK_READY".equals(ready.get(10, TimeUnit.SECONDS)), "owned second process holds lock");
            rejects(Reason.BUSY, () -> store.write(lockKey, save, 1, lockView.token(), new byte[] {2}), "second process writer excluded");
            child.getOutputStream().write(42); child.getOutputStream().flush();
            check(child.waitFor(10, TimeUnit.SECONDS) && child.exitValue() == 0, "owned lock child exits normally");
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); } }
        var raceKey = key("br.owned.race"); var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1); var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                final byte value = (byte) i;
                results.add(executor.submit(() -> { start.await(); try { store.write(raceKey, save, 1, EMPTY, new byte[] {value}); return true; }
                    catch (Failure expected) { if (expected.reason() != Reason.BUSY && expected.reason() != Reason.CONFLICT) throw expected; return false; } }));
            }
            start.countDown(); int applied = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) applied++;
            check(applied == 1 && store.read(raceKey, save).value().orElseThrow().revision() == 1, "two concurrent initial CAS writes have one winner");
        } finally { executor.shutdownNow(); }
        Path link = fixture.resolve("linked-root");
        try {
            Files.createSymbolicLink(link, root);
            rejects(Reason.UNSAFE_PATH, () -> new CareerSidecarStore(link).read(key, save), "linked root refused before traversal");
        } catch (UnsupportedOperationException | FileSystemException denied) { System.out.println("SKIP symlink creation unavailable; not accepted as passed."); }
        if (System.getProperty("os.name").startsWith("Windows") && fixture.toString().matches("[A-Za-z]:\\\\[A-Za-z0-9 _\\\\.-]+")) {
            Path junction = fixture.resolve("junction-root");
            var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), root.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("owned junction fixture timeout"); }
            process.getInputStream().readAllBytes();
            if (process.exitValue() == 0) {
                var beforeJunction = files(root); var linked = new CareerSidecarStore(junction);
                rejects(Reason.UNSAFE_PATH, () -> linked.read(key, save), "Windows junction root refused");
                rejects(Reason.UNSAFE_PATH, () -> linked.write(key, save, 1, recovered.token(), new byte[] {99}), "Windows junction cannot write through to target");
                check(files(root).equals(beforeJunction), "junction target files unchanged");
            } else System.out.println("SKIP junction creation unavailable; not accepted as passed.");
        } else System.out.println("SKIP junction platform/path unavailable; not accepted as passed.");
        System.out.println("PASS: " + checks + " sidecar storage core checks; synthetic fixture " + fixture + "; no game association or public API.");
    }
}
