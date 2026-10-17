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
        check(br.vbrasfoot.api.Sdk.GAME_PROFILE.equals(Profile.ID), "public SDK and loader share exact game profile identity");
        check(Profile.digest(write(temp.resolve("empty"), new byte[0])).equals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), "SHA256");
        write(temp.resolve("bf22-23.exe"), new byte[] {1, 2, 3});
        rejects(() -> Profile.verify(temp), "wrong executable version");
        check(Main.Options.parse(new String[] {"--safe-mode", "--validate-only"}).safe, "safe validation");
        check(Main.Options.parse(new String[] {"--probe-career-hooks", "--validate-only"}).probe, "probe validation opt-in");
        rejects(() -> Main.Options.parse(new String[] {"--probe-career-hooks", "--safe-mode"}), "probe prohibited in safe mode");
        rejects(() -> Main.Options.parse(new String[] {"--probe-career-hooks", "--check-save", "x.s22"}), "offline verifier does not probe methods");
        check(Main.Options.parse(new String[] {"--", "--game-option"}).gameArguments.equals(List.of("--game-option")), "pass through");
        rejects(() -> Main.Options.parse(new String[] {"--wat"}), "unknown option");
        rejects(() -> Main.Options.parse(new String[] {"--home"}), "missing option value");
        rejects(() -> Main.Options.parse(new String[] {"--check-save", "--safe-mode"}), "missing save path");
        rejects(() -> Main.Options.parse(new String[] {"--list-mods", "--validate-only"}), "conflicting modes");
        check(Main.Options.parse(new String[] {"--validate-mod", "mod.jar"}).validateMod.equals(Path.of("mod.jar")), "individual mod validation");
        rejects(() -> Main.Options.parse(new String[] {"--validate-mod", "mod.jar", "--safe-mode"}), "individual validation refuses safe mode");
        rejects(() -> Main.Options.parse(new String[] {"--validate-mod", "mod.jar", "--probe-career-hooks"}), "individual validation refuses hooks");
        Path modFixture = temp.resolve("uninitialized.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(modFixture))) {
            jar.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            jar.write("id=fixture\nname=Fixture\nversion=1\napiVersion=1\ngameProfile=brasfoot22-23\nmainClass=br.vbrasfoot.UninitializedModFixture\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("br/vbrasfoot/UninitializedModFixture.class"));
            try (var bytes = SelfTest.class.getResourceAsStream("/br/vbrasfoot/UninitializedModFixture.class")) { bytes.transferTo(jar); }
            jar.closeEntry();
        }
        var validator = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-jar", Path.of("dist/vbrasfoot.jar").toAbsolutePath().toString(), "--validate-mod", modFixture.toAbsolutePath().toString())
                .directory(temp.toFile()).redirectErrorStream(true).redirectOutput(temp.resolve("mod-validation.log").toFile()).start();
        if (!validator.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) { validator.destroyForcibly(); throw new AssertionError("validator fixture timed out"); }
        check(validator.exitValue() == 0, "individual validator requires no game and never initializes mod");
        check(!Files.exists(temp.resolve("vbrasfoot")), "individual validator writes no home/cache/session");
        var sdkValidator = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-jar", Path.of("dist/vbrasfoot-validator.jar").toAbsolutePath().toString(), modFixture.toAbsolutePath().toString())
                .directory(temp.toFile()).redirectErrorStream(true).redirectOutput(temp.resolve("sdk-validation.log").toFile()).start();
        if (!sdkValidator.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) { sdkValidator.destroyForcibly(); throw new AssertionError("SDK validator fixture timed out"); }
        check(sdkValidator.exitValue() == 0 && !Files.exists(temp.resolve("vbrasfoot")), "SDK-only tool never initializes fixture or creates loader home");
        try (var jar = new java.util.jar.JarFile("dist/vbrasfoot-validator.jar")) {
            check(jar.getJarEntry("br/vbrasfoot/Main.class") == null && jar.getJarEntry("br/vbrasfoot/LegacyRuntime.class") == null
                    && jar.getJarEntry("br/vbrasfoot/Profile.class") == null, "SDK validator contains no launcher/game-runtime/profile implementation");
        }
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
        var console = new java.io.ByteArrayOutputStream();
        var capture = new java.io.ByteArrayOutputStream();
        try (var tee = new SessionDiagnostics.Capture(console, capture, 4)) {
            tee.write("abcdefgh".getBytes(StandardCharsets.UTF_8));
            tee.write("ijk".getBytes(StandardCharsets.UTF_8));
        }
        check(console.toString(StandardCharsets.UTF_8).equals("abcdefghijk"), "capture preserves console");
        String captured = capture.toString(StandardCharsets.UTF_8);
        check(captured.startsWith("abcd\n[vbrasfoot:"), "bounded capture");
        check(captured.indexOf("capture limit") == captured.lastIndexOf("capture limit"), "single truncation marker");
        var originalOut = System.out;
        var originalErr = System.err;
        Path session;
        try (var diagnostic = SessionDiagnostics.open(temp, temp, new String[] {"--validate-only"})) {
            session = diagnostic.directory();
            System.out.println("early-output");
            System.err.println("legacy-handled-error");
            diagnostic.logger().info("test-stage");
        }
        check(System.out == originalOut && System.err == originalErr, "restore console streams");
        check(Files.readString(session.resolve("stdout.log")).contains("early-output"), "stdout capture");
        check(Files.readString(session.resolve("stderr.log")).contains("legacy-handled-error"), "stderr capture");
        check(Files.readString(session.resolve("loader-0.log")).contains("test-stage"), "session logger");
        check(Files.readString(session.resolve("session.properties")).contains("java.version="), "runtime metadata");
        Path invalidGame = Files.createDirectory(temp.resolve("invalid-game"));
        write(invalidGame.resolve("bf22-23.exe"), new byte[] {1});
        Path failureHome = temp.resolve("startup-failure");
        rejects(() -> Main.run(new String[] {"--game-dir", invalidGame.toString(), "--home", failureHome.toString(), "--validate-only"}), "startup failure");
        try (var sessions = Files.list(failureHome.resolve("logs"))) {
            Path failedSession = sessions.findFirst().orElseThrow();
            check(Files.readString(failedSession.resolve("loader-0.log")).contains("stage=failed"), "early failure in session logger");
            check(!Files.readString(failedSession.resolve("stderr.log")).isBlank(), "early failure stderr captured");
        }
        Path rejectedHome = invalidGame.resolve("must-not-create");
        rejects(() -> Main.run(new String[] {"--game-dir", invalidGame.toString(), "--home", rejectedHome.toString()}), "home inside original rejected");
        check(!Files.exists(rejectedHome), "reject home before writing original");
        Path interruptedHome = Files.createDirectory(temp.resolve("interrupted-install"));
        Files.createDirectory(interruptedHome.resolve(".update-pending"));
        rejects(() -> Main.run(new String[] {"--game-dir", invalidGame.toString(), "--home", interruptedHome.toString(), "--validate-only"}), "incomplete installation rejected");
        check(!Files.exists(interruptedHome.resolve("logs")), "incomplete install blocked before session writes");
        check(InstallPaths.resolve(invalidGame, invalidGame.resolve("vbrasfoot")).installRoot().endsWith("vbrasfoot"), "installed root accepted");
        rejects(() -> InstallPaths.resolve(invalidGame, invalidGame.resolve("sav")), "save root rejected");
        rejects(() -> InstallPaths.resolve(invalidGame, invalidGame), "game root rejected");
        rejects(() -> InstallPaths.resolve(invalidGame, invalidGame.resolve("vbrasfoot/nested")), "nested install root rejected");
        GameAccess paged = () -> new GameAccess.Snapshot(true, List.of(club));
        check(paged.clubs("clu", 0, 10).items().size() == 1, "filtered club page");
        check(paged.clubs("missing", 0, 10).total() == 0, "empty filtered page");
        check(paged.clubs("", 9, 10).items().isEmpty(), "page beyond end");
        rejects(() -> paged.clubs("", 0, 501), "bounded page size");
        rejects(() -> paged.players(1, -1, 10), "negative offset rejected");
        GameAccess duplicateIds = () -> new GameAccess.Snapshot(true, List.of(club, new GameAccess.Club(1, "Outro", false, List.of())));
        rejects(() -> duplicateIds.players(1, 0, 10), "ambiguous club id rejected");
        check(duplicateIds.playersAt(1, 0, 10).items().isEmpty(), "club list position selection");
        Path evolved = temp.resolve("evolved.jar");
        String evolvedText = "id=evolved\nname=Evolved\nversion=1\napiVersion=[1,2)\ngameProfile=brasfoot22-23\nmainClass=test.Main\nminLoaderVersion=0.2.0\noptionalDependencies=base\nrequiredCapabilities=query.paged\nconflicts=other\n";
        manifestText(evolved, evolvedText);
        check(ModManifest.read(evolved).optionalDependencies().equals(List.of("base")), "evolved manifest");
        ModManifest evolvedManifest = ModManifest.read(evolved);
        check(evolvedManifest.requiredCapabilities().equals(Set.of("query.paged")), "required capabilities retained for validation report");
        check(ModLoader.ordered(Map.of("evolved", evolvedManifest)).size() == 1, "absent optional dependency accepted");
        check(ModLoader.ordered(Map.of("evolved", evolvedManifest, "base", entry("base", List.of()))).get(0).id().equals("base"), "optional dependency order");
        rejects(() -> ModLoader.ordered(Map.of("evolved", evolvedManifest, "other", entry("other", List.of()))), "explicit conflict");
        manifestText(evolved, evolvedText + "typo=invalid\n");
        rejects(() -> ModManifest.read(evolved), "unknown manifest field");
        manifestText(evolved, evolvedText.replace("0.2.0", "99.0.0"));
        rejects(() -> ModManifest.read(evolved), "minimum loader version");
        manifestText(evolved, evolvedText.replace("query.paged", "game.write"));
        rejects(() -> ModManifest.read(evolved), "unknown required capability");
        for (String mode : List.of("exit", "exit-edt", "return")) {
            Path childHome = temp.resolve("shutdown-" + mode);
            var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-cp", System.getProperty("java.class.path"), DiagnosticExitFixture.class.getName(), childHome.toString(), mode)
                    .redirectErrorStream(true).redirectOutput(temp.resolve(mode + ".log").toFile()).start();
            if (!child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                child.destroyForcibly();
                throw new AssertionError("Diagnostic shutdown timed out: " + mode);
            }
            check(child.exitValue() == 0, "diagnostic child " + mode);
            try (var sessions = Files.list(childHome.resolve("logs"))) {
                Path childSession = sessions.findFirst().orElseThrow();
                String reason = Files.readString(childSession.resolve("termination.txt")).trim();
                check(reason.equals(mode.startsWith("exit") ? "jvm-shutdown" : "launcher-returned"), "shutdown reason " + mode);
                check(Files.readString(childSession.resolve("stderr.log")).contains("child-error"), "shutdown flush " + mode);
            }
        }
        System.out.println("PASS: " + checks + " testes.");
    }
    private static Path write(Path path, byte[] bytes) throws IOException { return Files.write(path, bytes); }
    private static void manifestText(Path path, String text) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            jar.write(text.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }
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
