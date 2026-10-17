package br.vbrasfoot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Privacy allowlist and CREATE_NEW local export; no game, raw log collection or network. */
public final class DiagnosticExportSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build"), "diagnostic-export-").toAbsolutePath();
        Path sav = Files.createDirectory(root.resolve("sav")), logs = Files.createDirectory(root.resolve("logs"));
        Path save = Files.writeString(sav.resolve("PRIVATE_CLUB.s22"), "PRIVATE_SAVE_CONTENT");
        Path backup = Files.writeString(sav.resolve("PRIVATE_CLUB.sbck"), "PRIVATE_BACKUP_CONTENT");
        Path rawLog = Files.writeString(logs.resolve("stdout.log"), "RAW_LOG_SECRET token=SECRET\nC:\\PRIVATE_USER\\LICENSE\n");
        Path metadata = Files.writeString(logs.resolve("session.properties"), "jvm.arguments=SECRET_ARGS\njava.home=C:\\PRIVATE_USER\n");
        var manifest = new ModManifest(root.resolve("PRIVATE_FILE.jar"), "private.mod.id", "PRIVATE_DISPLAY_NAME", "PRIVATE_VERSION", "PRIVATE_MAIN_CLASS", List.of("PRIVATE_DEPENDENCY"), Set.of("game.read"));
        List<ModSelection.Entry> inventory = new ArrayList<>();
        inventory.add(new ModSelection.Entry("PRIVATE_FILE.jar", manifest, true, ""));
        inventory.add(new ModSelection.Entry("INVALID_PRIVATE.jar", null, false, "VALIDATION_SECRET C:\\PRIVATE_USER\\secret"));
        var failures = new ModFailures();
        failures.record("PRIVATE_FILE.jar", "private.mod.id", ModFailures.Stage.UI_ACTION, "PRIVATE_EXCEPTION_TYPE", "MESSAGE_SECRET\nforged.key=value\u202e");
        failures.record("REMOVED_PRIVATE.jar", "removed.private.id", ModFailures.Stage.SHUTDOWN, "OTHER_PRIVATE_TYPE", "REMOVED_MESSAGE_SECRET");
        var states = new HashMap<String, br.vbrasfoot.api.ModState>(); states.put("PRIVATE_FILE.jar", br.vbrasfoot.api.ModState.ACTIVE);
        var environment = DiagnosticExport.Environment.current(false, true);
        var snapshot = DiagnosticExport.capture(inventory, states, failures.snapshot(), environment);
        inventory.clear(); states.clear();
        check(snapshot.mods().size() == 2 && snapshot.mods().get(1).current().equals("ACTIVE"), "detached inventory/state snapshot");
        boolean immutable = false; try { snapshot.mods().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "immutable export data");
        String minimal = DiagnosticExport.preview(snapshot, new DiagnosticExport.Options(false, false));
        for (String secret : List.of("PRIVATE_", "SECRET", "private.mod.id", "game.read", "C:\\", "REMOVED_", "forged.key", "session.properties", "stdout.log")) check(!minimal.contains(secret), "default excludes " + secret);
        check(minimal.contains("mods.count=2") && minimal.contains("mode.probe=true") && minimal.contains("failures.UI_ACTION=1") && minimal.contains("failures.SHUTDOWN=1"), "allowlisted mode/counts/stages retained");
        check(minimal.contains("mod-1.manifestValid=false") && minimal.contains("mod-1.nextEnabled=false"), "invalid disabled state without raw error");
        check(minimal.contains("profile.expectedSha256=" + Profile.SHA256) && !minimal.contains("java.home="), "expected profile identity without paths or raw metadata");
        check(!Files.exists(root.resolve("diagnostics")), "preparing preview performs no writes");
        String identities = DiagnosticExport.preview(snapshot, new DiagnosticExport.Options(true, false));
        check(identities.contains("PRIVATE_FILE.jar") && identities.contains("private.mod.id") && identities.contains("PRIVATE_VERSION") && identities.contains("game.read"), "identities explicit opt-in");
        check(!identities.contains("MESSAGE_SECRET") && !identities.contains("VALIDATION_SECRET"), "identity consent does not imply message consent");
        String details = DiagnosticExport.preview(snapshot, new DiagnosticExport.Options(false, true));
        check(details.contains("MESSAGE_SECRET") && details.contains("VALIDATION_SECRET") && details.contains("PRIVATE_EXCEPTION_TYPE"), "failure details explicit opt-in");
        check(!details.contains("PRIVATE_FILE.jar") && !details.contains("private.mod.id") && details.contains("unlisted-mod-1"), "unlisted failure attributed without exporting filename/id");
        check(details.contains("\\u202e") && !details.contains("\u202e") && !details.contains("\nforged.key"), "controls/bidi cannot forge report lines");
        String all = DiagnosticExport.preview(snapshot, new DiagnosticExport.Options(true, true));
        check(all.contains("REMOVED_PRIVATE.jar") && all.contains("removed.private.id"), "combined options apply to removed mod failures");
        String edited = minimal + "\nuser.review=Removi detalhes; acentuaçao permitida.\n";
        UUID fixed = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Path output = DiagnosticExport.saveReviewed(root, edited, fixed);
        check(output.getParent().equals(root.resolve("diagnostics")) && output.getFileName().toString().equals("diagnostic-" + fixed + ".txt"), "fixed export directory/unique leaf only");
        check(Files.readString(output).equals(edited) && Arrays.equals(Files.readAllBytes(output), edited.getBytes(StandardCharsets.UTF_8)), "save exact reviewed UTF-8 bytes, not regenerated snapshot");
        String before = Profile.digest(output);
        rejects(() -> DiagnosticExport.saveReviewed(root, "DO_NOT_OVERWRITE", fixed), "existing report refused");
        check(Profile.digest(output).equals(before), "existing report bytes unchanged");
        Path another = DiagnosticExport.saveReviewed(root, edited);
        check(!another.equals(output) && Files.readString(another).equals(edited), "each explicit save creates a new report");
        check(Files.readString(save).equals("PRIVATE_SAVE_CONTENT") && Files.readString(backup).equals("PRIVATE_BACKUP_CONTENT")
                && Files.readString(rawLog).contains("RAW_LOG_SECRET") && Files.readString(metadata).contains("SECRET_ARGS"), "source sentinel saves/logs/metadata untouched");
        check(!Files.readString(output).contains("SECRET_ARGS") && !Files.readString(output).contains("PRIVATE_SAVE_CONTENT"), "sentinel raw inputs not silently bundled");
        Path invalid = Files.createDirectory(root.resolve("invalid-output-tests"));
        rejects(() -> DiagnosticExport.saveReviewed(invalid, "x".repeat(DiagnosticExport.MAX_BYTES + 1)), "char limit before I/O");
        rejects(() -> DiagnosticExport.saveReviewed(invalid, "é".repeat(DiagnosticExport.MAX_BYTES / 2 + 1)), "UTF-8 byte limit before I/O");
        rejects(() -> DiagnosticExport.saveReviewed(invalid, "invalid\ud800"), "invalid Unicode refused without silently changing reviewed text");
        check(!Files.exists(invalid.resolve("diagnostics")), "rejected content creates no directory/report");
        Path blocked = Files.createDirectory(root.resolve("blocked")); Files.writeString(blocked.resolve("diagnostics"), "USER_FILE");
        rejects(() -> DiagnosticExport.saveReviewed(blocked, minimal), "existing non-directory destination refused");
        check(Files.readString(blocked.resolve("diagnostics")).equals("USER_FILE"), "user destination file preserved");
        rejects(() -> DiagnosticExport.saveReviewed(root.resolve("absent"), minimal), "missing install root not created");
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            // Keep reparse fixtures outside the development installRoot, whose safety scan is recursive.
            Path privateRoot = Files.createTempDirectory(Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "opencode")), "vbrasfoot-diagnostic-junction-");
            Path linkedHome = Files.createDirectory(privateRoot.resolve("linked-home"));
            Path protectedFiles = Files.createDirectory(privateRoot.resolve("protected-fixture"));
            Files.writeString(protectedFiles.resolve("keep.s22"), "PROTECTED_FIXTURE");
            var junction = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", linkedHome.resolve("diagnostics").toString(), protectedFiles.toString())
                    .redirectErrorStream(true).redirectOutput(root.resolve("junction-fixture.log").toFile()).start();
            if (!junction.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) { junction.destroyForcibly(); throw new AssertionError("owned junction fixture timeout"); }
            check(junction.exitValue() == 0, "private NTFS junction fixture created");
            rejects(() -> DiagnosticExport.saveReviewed(linkedHome, minimal), "export refuses redirected destination");
            try (var files = Files.list(protectedFiles)) { check(files.count() == 1 && Files.readString(protectedFiles.resolve("keep.s22")).equals("PROTECTED_FIXTURE"), "junction target untouched"); }
        }
        var hugeMods = Collections.nCopies(513, snapshot.mods().get(0));
        rejects(() -> new DiagnosticExport.Snapshot(environment, hugeMods, List.of(), 0), "bounded snapshot mod count");
        rejects(() -> new DiagnosticExport.Snapshot(environment, List.of(), Collections.nCopies(129, snapshot.failures().get(0)), 0), "bounded failure count");
        String vendor = System.getProperty("java.vendor"), home = System.getProperty("java.home"), name = System.getProperty("os.name"), arch = System.getProperty("os.arch");
        try {
            System.setProperty("java.vendor", "VENDOR_SECRET"); System.setProperty("java.home", "PATH_SECRET");
            System.setProperty("os.name", "OS_NAME_SECRET"); System.setProperty("os.arch", "ARCH_SECRET");
            var safeEnvironment = DiagnosticExport.Environment.current(true, false);
            String safeReport = DiagnosticExport.preview(new DiagnosticExport.Snapshot(safeEnvironment, List.of(), List.of(), 0), new DiagnosticExport.Options(false, false));
            check(safeReport.contains("os.family=OTHER") && safeReport.contains("os.arch=OTHER") && !safeReport.contains("SECRET"), "environment uses enum/numeric allowlist, not raw system properties");
        } finally {
            System.setProperty("java.vendor", vendor); System.setProperty("java.home", home); System.setProperty("os.name", name); System.setProperty("os.arch", arch);
        }
        System.out.println("PASS: " + checks + " reviewable diagnostic export checks (synthetic; no raw log collection).");
    }
}
