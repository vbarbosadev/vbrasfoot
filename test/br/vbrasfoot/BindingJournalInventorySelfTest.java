package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Administrative inventory of explicitly chosen owned bindings, not automatic career discovery. */
public final class BindingJournalInventorySelfTest {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, Action action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static Map<String, String> contents(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) { for (Path file : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), Profile.digest(file)); }
        return result;
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path root = Files.createTempDirectory(parent, "binding-journal-inventory-").toRealPath();
        Path absent = root.resolve("absent"); var store = new CareerSidecarStore(absent);
        UUID career = UUID.randomUUID(), binding = UUID.randomUUID(); var save = new SaveRevision("a".repeat(64), 1);
        check(store.inventoryBinding(career, binding).tokens().isEmpty() && !Files.exists(absent), "inventory of absent explicit binding creates nothing");
        var key = new Key(career, binding, "br.owned.disabled"); var journal = new CareerCommandJournal(store, key);
        var intent = new CareerCommandJournal.Intent("once", CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64));
        var prepared = journal.prepare(save, EMPTY, intent);
        check(store.inventoryBinding(career, binding).tokens().equals(Map.of(key.mod(), prepared.snapshot().token())), "disabled/unconfigured journal discovered by explicit binding, not manifest selection");
        var captured = store.inventoryBinding(career, binding);
        journal.quarantinePending(save, prepared.snapshot().token());
        check(!store.inventoryBinding(career, binding).equals(captured), "committed HEAD change invalidates inventory token set");
        boolean immutable = false; try { captured.tokens().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && captured.tokens().size() == 1, "inventory snapshots immutable");
        Path selected = absent.resolve(career.toString()).resolve(binding.toString());
        Path orphan = Files.createDirectory(selected.resolve("br.owned.orphan"));
        Files.writeString(orphan.resolve("r-" + UUID.randomUUID() + ".bin"), "owned uncommitted bytes");
        check(store.inventoryBinding(career, binding).tokens().get("br.owned.orphan").equals(EMPTY), "uncommitted orphan namespace remains explicit, never promoted to committed HEAD");
        Path empty = Files.createDirectory(selected.resolve("br.owned.empty-directory"));
        check(store.inventoryBinding(career, binding).tokens().containsKey(empty.getFileName().toString()), "even empty preexisting directory is distinguished from absent namespace");
        Path unknownBinding = Files.createDirectories(absent.resolve(UUID.randomUUID().toString()).resolve(UUID.randomUUID().toString()));
        Files.writeString(unknownBinding.resolve("unfamiliar-private-file"), "owned unrelated binding");
        check(store.inventoryBinding(career, binding).tokens().size() == 3, "inventory never scans another career/binding or requires its contents to be valid");
        var before = contents(root); store.inventoryBinding(career, binding);
        check(contents(root).equals(before), "inventory preserves every owned file and creates no locks or records");
        var edtFailure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { store.inventoryBinding(career, binding); } catch (Throwable expected) { edtFailure.set(expected); } });
        check(edtFailure.get() instanceof Failure f && f.reason() == Reason.WRONG_THREAD, "namespace inventory refused on EDT");
        UUID invalidBinding = UUID.randomUUID(); Path invalidRoot = Files.createDirectories(absent.resolve(career.toString()).resolve(invalidBinding.toString()));
        Files.writeString(invalidRoot.resolve("br.owned.not-a-directory"), "preserve");
        rejects(Reason.UNSAFE_PATH, () -> store.inventoryBinding(career, invalidBinding));
        UUID unknownName = UUID.randomUUID(); Path unknownRoot = Files.createDirectories(absent.resolve(career.toString()).resolve(unknownName.toString()));
        Files.createDirectory(unknownRoot.resolve(".unknown"));
        rejects(Reason.UNSAFE_PATH, () -> store.inventoryBinding(career, unknownName));
        UUID unknownContent = UUID.randomUUID(); Path contentRoot = Files.createDirectories(absent.resolve(career.toString()).resolve(unknownContent.toString()).resolve("br.owned.unknown"));
        Files.writeString(contentRoot.resolve("private-unknown.txt"), "preserve this unknown file");
        rejects(Reason.UNSAFE_PATH, () -> store.inventoryBinding(career, unknownContent));
        UUID corrupt = UUID.randomUUID(); var corruptKey = new Key(career, corrupt, "br.owned.corrupt");
        store.write(corruptKey, save, 1, EMPTY, new byte[] {1});
        Path head = absent.resolve(career.toString()).resolve(corrupt.toString()).resolve(corruptKey.mod()).resolve("HEAD");
        Files.write(head, new byte[] {1}); rejects(Reason.CORRUPT, () -> store.inventoryBinding(career, corrupt));
        UUID tooMany = UUID.randomUUID(); Path many = Files.createDirectories(absent.resolve(career.toString()).resolve(tooMany.toString()));
        for (int i = 0; i <= MAX_BINDING_NAMESPACES; i++) Files.createDirectory(many.resolve("br.owned.n" + i));
        rejects(Reason.QUOTA, () -> store.inventoryBinding(career, tooMany));
        UUID tooManyFiles = UUID.randomUUID(); Path crowded = Files.createDirectories(absent.resolve(career.toString()).resolve(tooManyFiles.toString()).resolve("br.owned.crowded"));
        for (int i = 0; i <= MAX_FILES; i++) Files.write(crowded.resolve("r-" + UUID.randomUUID() + ".bin"), new byte[0]);
        rejects(Reason.QUOTA, () -> store.inventoryBinding(career, tooManyFiles));
        UUID tooLarge = UUID.randomUUID(); Path large = Files.createDirectories(absent.resolve(career.toString()).resolve(tooLarge.toString()).resolve("br.owned.large"));
        try (var file = new RandomAccessFile(large.resolve("r-" + UUID.randomUUID() + ".bin").toFile(), "rw")) { file.setLength(MAX_BYTES + 1); }
        rejects(Reason.QUOTA, () -> store.inventoryBinding(career, tooLarge));
        UUID nested = UUID.randomUUID(); Files.createDirectories(absent.resolve(career.toString()).resolve(nested.toString()).resolve("br.owned.nested/HEAD"));
        rejects(Reason.UNSAFE_PATH, () -> store.inventoryBinding(career, nested));
        if (System.getProperty("os.name").startsWith("Windows") && root.toString().matches("[A-Za-z]:\\\\[A-Za-z0-9 _\\\\.-]+")) {
            UUID redirect = UUID.randomUUID(); Path redirected = Files.createDirectories(absent.resolve(career.toString()).resolve(redirect.toString()));
            Path junction = redirected.resolve("br.owned.redirect");
            var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), orphan.toString()).redirectErrorStream(true).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("owned junction creation timeout"); }
            process.getInputStream().readAllBytes();
            if (process.exitValue() == 0) rejects(Reason.UNSAFE_PATH, () -> store.inventoryBinding(career, redirect));
            else System.out.println("SKIP owned junction unavailable; not accepted as passed.");
        }
        System.out.println("PASS: " + checks + " binding journal inventory checks; owned fixture " + root + "; not global identity discovery or readiness.");
    }
}
