package br.vbrasfoot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;

/** Synthetic fixture only; never accesses an installed game or starts mods. */
public final class StorageInventorySelfTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action, String label) throws Exception {
        try { action.run(); }
        catch (IOException | IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static StorageInventory.Category category(StorageInventory.Report report, String name) {
        return report.categories().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }
    private static Map<String, String> snapshot(Path root) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                result.put(root.relativize(path).toString(), attributes.isDirectory() ? "directory" :
                        attributes.size() + ":" + attributes.lastModifiedTime() + ":" + java.util.HexFormat.of().formatHex(Files.readAllBytes(path)));
            }
        }
        return result;
    }
    private static void linkCheck(Path game, Path home, Path link, Checked create, String capability) throws Exception {
        try { create.run(); }
        catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            System.out.println("SKIP " + capability + ": criacao indisponivel neste ambiente (nao validado).");
            return;
        }
        check(Files.exists(link, LinkOption.NOFOLLOW_LINKS), capability + " fixture exists");
        var report = StorageInventory.inspect(game, home);
        check(!report.complete() && category(report, "mods").status().equals("RECUSADO"), capability + " refused before traversal");
        check(category(report, "mods").bytes() == 0, capability + " target bytes never counted");
        rejects(() -> StorageInventory.inspect(game, link), capability + " home refused");
    }

    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = args.length == 0 && local != null
                ? Files.createDirectories(Path.of(local, "Temp", "opencode"))
                : args.length == 0 ? Files.createDirectories(Path.of("build")) : Path.of(args[0]);
        Path fixture = Files.createTempDirectory(parent, "storage-inventory-").toRealPath();
        Path game = Files.createDirectory(fixture.resolve("game"));
        Path home = Files.createDirectory(game.resolve("vbrasfoot"));
        Path original = Files.createDirectories(game.resolve("sav"));
        Files.write(original.resolve("private-original.sav"), new byte[97]);
        Files.createDirectories(home.resolve("logs/nested"));
        Files.write(home.resolve("logs/private-person.log"), new byte[3]);
        Files.write(home.resolve("logs/nested/secret.log"), new byte[5]);
        Files.createDirectory(home.resolve("mods"));
        Files.writeString(home.resolve("mods/private-invalid.jar"), "not a jar");
        Files.createDirectory(home.resolve("backups"));
        Files.write(home.resolve("backups/private-save.sav"), new byte[11]);
        Files.createDirectory(home.resolve("unknown-category"));
        Files.write(home.resolve("unknown-category/ignored"), new byte[101]);
        Files.writeString(home.resolve(".update-pending"), "synthetic marker");
        var before = snapshot(fixture);
        var report = StorageInventory.inspect(game, home);
        check(report.complete(), "known categories complete");
        check(report.categories().size() == 7, "only seven known categories");
        check(category(report, "logs").bytes() == 8 && category(report, "logs").files() == 2
                && category(report, "logs").directories() == 2, "logical sizes and recursive counts");
        check(category(report, "mods").bytes() == 9, "invalid jar metadata only; no mod load");
        check(category(report, "backups").bytes() == 11, "backup metadata only");
        check(category(report, "cache").status().equals("AUSENTE") && category(report, "data").bytes() == 0, "absent categories explicit zero");
        check(report.entries() == 8, "unknown category, original saves and marker excluded");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        report.print(new PrintStream(output, true, StandardCharsets.UTF_8));
        String printed = output.toString(StandardCharsets.UTF_8);
        check(!printed.contains("private-") && !printed.contains("secret") && !printed.contains(fixture.toString()), "no private names or paths in output");
        var bounded = StorageInventory.inspect(game, home, 1, 10_000);
        check(!bounded.complete() && bounded.entries() == 1, "global entry budget enforced");
        check(category(bounded, "logs").status().equals("INCOMPLETO") && category(bounded, "logs").bytes() == 0, "partial is not total");
        rejects(() -> StorageInventory.inspect(game, home, 0, 10), "invalid budget refused");
        check(StorageInventory.add(Long.MAX_VALUE - 1, 1) == Long.MAX_VALUE, "long maximum supported");
        try { StorageInventory.add(Long.MAX_VALUE, 1); throw new AssertionError("overflow not rejected"); }
        catch (ArithmeticException expected) { checks++; }
        try { StorageInventory.add(0, -1); throw new AssertionError("negative size not rejected"); }
        catch (ArithmeticException expected) { checks++; }
        rejects(() -> StorageInventory.inspect(game, game), "original root refused");
        rejects(() -> StorageInventory.inspect(game, original), "original saves refused");
        rejects(() -> StorageInventory.inspect(game, fixture), "ancestor of game refused");
        rejects(() -> StorageInventory.inspect(game, fixture.resolve("missing")), "missing home not created");
        rejects(() -> StorageInventory.inspect(game, home.resolve(".update-pending")), "file home refused");
        check(Main.Options.parse(new String[] {"--storage-status"}).storageStatus, "CLI parsed");
        for (String incompatible : new String[] {"--validate-only", "--list-mods", "--safe-mode", "--probe-career-hooks"}) {
            rejects(() -> Main.Options.parse(new String[] {"--storage-status", incompatible}), "exclusive CLI " + incompatible);
        }
        rejects(() -> Main.Options.parse(new String[] {"--storage-status", "--", "game-arg"}), "no game args accepted");
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Main.run(new String[] {"--storage-status", "--game-dir", game.toString(), "--home", home.toString()});
        } finally { System.setOut(previous); }
        check(before.equals(snapshot(fixture)), "CLI and inspections do not write files, logs, cache, lock or backup; saves intact");

        // Unknown redirect must not even be validated/traversed.
        try {
            Files.createSymbolicLink(home.resolve("unknown-link"), original);
            check(StorageInventory.inspect(game, home).complete(), "unknown symlink ignored");
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            System.out.println("SKIP unknown symlink: criacao indisponivel (nao validado).");
        }
        Path linkHome = Files.createDirectory(fixture.resolve("link-home"));
        Path link = linkHome.resolve("mods");
        linkCheck(game, linkHome, link, () -> Files.createSymbolicLink(link, original), "symlink de diretorio");
        Path nestedHome = Files.createDirectory(fixture.resolve("nested-link-home"));
        Files.createDirectory(nestedHome.resolve("mods"));
        linkCheck(game, nestedHome, nestedHome.resolve("mods/link"),
                () -> Files.createSymbolicLink(nestedHome.resolve("mods/link"), original), "symlink aninhado");
        Path brokenHome = Files.createDirectory(fixture.resolve("broken-link-home"));
        linkCheck(game, brokenHome, brokenHome.resolve("mods"),
                () -> Files.createSymbolicLink(brokenHome.resolve("mods"), fixture.resolve("nonexistent")), "symlink quebrado");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Path junctionHome = Files.createDirectory(fixture.resolve("junction-home"));
            Path junction = junctionHome.resolve("mods");
            linkCheck(game, junctionHome, junction, () -> {
                Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", junction.toString(), original.toString())
                        .redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                if (process.waitFor() != 0) throw new IOException("junction creation unavailable");
            }, "junction Windows");
        } else System.out.println("SKIP junction Windows: plataforma nao Windows (nao validado).");
        Path wrongType = Files.createDirectory(fixture.resolve("wrong-type-home"));
        Files.writeString(wrongType.resolve("mods"), "not a directory");
        var refused = StorageInventory.inspect(game, wrongType);
        check(!refused.complete() && category(refused, "mods").status().equals("RECUSADO"), "non-directory category refused");
        var wrongBefore = snapshot(wrongType);
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            rejects(() -> Main.run(new String[] {"--storage-status", "--game-dir", game.toString(), "--home", wrongType.toString()}), "CLI refuses incomplete report");
        } finally { System.setOut(previous); }
        check(wrongBefore.equals(snapshot(wrongType)), "refused CLI also writes nothing");
        Path emptyExternal = Files.createDirectory(fixture.resolve("empty-external-home"));
        var emptyReport = StorageInventory.inspect(game, emptyExternal);
        check(emptyReport.complete() && emptyReport.entries() == 0 && emptyReport.categories().stream().allMatch(c -> c.status().equals("AUSENTE")), "external existing empty home supported");
        System.out.println("PASS: " + checks + " storage inventory checks (synthetic only; fixture retained: " + fixture + ").");
    }
}
