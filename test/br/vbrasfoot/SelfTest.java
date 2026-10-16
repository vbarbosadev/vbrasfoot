package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class SelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory(Path.of("build"), "tests-");
        check(Profile.digest(write(temp.resolve("empty"), new byte[0])).equals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), "SHA256");
        write(temp.resolve("bf22-23.exe"), new byte[] {1, 2, 3});
        rejects(() -> Profile.verify(temp), "wrong executable version");
        check(Main.Options.parse(new String[] {"--safe-mode", "--validate-only"}).safe, "safe validation");
        check(Main.Options.parse(new String[] {"--", "--game-option"}).gameArguments.equals(List.of("--game-option")), "pass through");
        rejects(() -> Main.Options.parse(new String[] {"--wat"}), "unknown option");
        rejects(() -> Main.Options.parse(new String[] {"--home"}), "missing option value");
        rejects(() -> Main.Options.parse(new String[] {"--check-save", "--safe-mode"}), "missing save path");
        rejects(() -> Main.Options.parse(new String[] {"--list-mods", "--validate-only"}), "conflicting modes");
        Path good = manifest(temp.resolve("valid.jar"), "base", "", "game.read,ui.register", "brasfoot22-23");
        check(ModManifest.read(good).id().equals("base"), "manifest read");
        rejects(() -> ModManifest.read(manifest(temp.resolve("bad-profile.jar"), "bad", "", "game.read", "realbrasfoot26")), "profile mismatch");
        rejects(() -> ModManifest.read(manifest(temp.resolve("bad-permission.jar"), "bad", "", "game.write", "brasfoot22-23")), "save writing permission");
        rejects(() -> ModManifest.read(manifest(temp.resolve("bad-id.jar"), "../escape", "", "game.read", "brasfoot22-23")), "manifest path traversal");
        Map<String, ModManifest> mods = new LinkedHashMap<>();
        mods.put("child", entry("child", List.of("base")));
        mods.put("base", entry("base", List.of()));
        check(ModLoader.ordered(mods).stream().map(ModManifest::id).toList().equals(List.of("base", "child")), "dependency ordering");
        rejects(() -> ModLoader.ordered(Map.of("child", entry("child", List.of("missing")))), "missing dependency");
        rejects(() -> ModLoader.ordered(Map.of("a", entry("a", List.of("b")), "b", entry("b", List.of("a")))), "dependency cycle");
        Path duplicate = temp.resolve("duplicate");
        Files.createDirectories(duplicate);
        manifest(duplicate.resolve("a.jar"), "same", "", "", "brasfoot22-23");
        manifest(duplicate.resolve("b.jar"), "same", "", "", "brasfoot22-23");
        rejects(() -> ModLoader.discover(duplicate), "duplicate mod ID");
        check(ModLoader.discover(temp.resolve("absent")).isEmpty(), "no mods directory");
        Path storage = temp.resolve("data");
        check(ModLoader.dataPath(storage, "br.test", "settings.json").startsWith(storage.toAbsolutePath()), "data containment");
        rejects(() -> ModLoader.dataPath(storage, "br.test", "../options.bcf"), "data traversal");
        rejects(() -> ModLoader.dataPath(storage, "..", "x"), "data id traversal");
        rejects(() -> ModLoader.dataPath(storage, "br.test", "C:\\outside"), "absolute data path");
        List<GameAccess.Player> mutable = new ArrayList<>();
        GameAccess.Club club = new GameAccess.Club(1, "Clube", true, mutable);
        mutable.add(new GameAccess.Player(1, "Jogador", 21, 1, 40, 1000));
        check(club.players().isEmpty(), "defensive player copy");
        rejects(() -> club.players().add(mutable.get(0)), "immutable snapshot");
        check(CareerBridge.onEdt(() -> javax.swing.SwingUtilities.isEventDispatchThread()), "EDT dispatch");
        System.out.println("PASS: " + checks + " testes.");
    }
    private static Path write(Path path, byte[] bytes) throws IOException { return Files.write(path, bytes); }
    private static Path manifest(Path path, String id, String dependencies, String permissions, String profile) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            String text = "id=" + id + "\nname=Teste\nversion=1\napiVersion=1\nmainClass=test.Main\ngameProfile="
                    + profile + "\ndependencies=" + dependencies + "\npermissions=" + permissions + "\n";
            jar.write(text.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return path;
    }
    private static ModManifest entry(String id, List<String> deps) {
        return new ModManifest(Path.of(id + ".jar"), id, id, "1", "test.Main", deps, Set.of());
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private static void rejects(Throwing action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError("Should reject: " + label);
    }
    private interface Throwing { void run() throws Exception; }
}
