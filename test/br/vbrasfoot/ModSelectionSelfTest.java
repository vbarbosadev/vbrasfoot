package br.vbrasfoot;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Selection/config graph tests, no original artifacts or mod initialization. */
public final class ModSelectionSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action, String label) throws Exception {
        try { action.run(); } catch (java.io.IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void jar(Path mods, String file, String id, String dependencies, String conflicts) throws Exception {
        try (var output = new JarOutputStream(Files.newOutputStream(mods.resolve(file)))) {
            output.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            output.write(("id=" + id + "\nname=Test\nversion=1\napiVersion=1\ngameProfile=brasfoot22-23\nmainClass=Never.Initialize\ndependencies=" + dependencies + "\nconflicts=" + conflicts + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory(Path.of("build"), "mod-selection-").toAbsolutePath();
        Path mods = Files.createDirectory(home.resolve("mods"));
        check(ModSelection.read(home).inventory().isEmpty(), "empty inventory");
        jar(mods, "base.jar", "base", "", ""); jar(mods, "child.jar", "child", "base", "");
        byte[] base = Files.readAllBytes(mods.resolve("base.jar"));
        var initial = ModSelection.read(home);
        check(initial.selected().stream().map(ModManifest::id).toList().equals(List.of("base", "child")), "default all enabled and ordered");
        rejects(() -> initial.change("base.jar", false), "required dependency not silently disabled");
        check(!Files.exists(home.resolve("mods-selection.properties")), "failed candidate does not create config");
        var childOff = initial.change("child.jar", false);
        check(childOff.selected().size() == 1 && Files.exists(mods.resolve("child.jar")), "disable config only, JAR preserved");
        rejects(() -> initial.change("child.jar", false), "stale snapshot refused");
        var allOff = childOff.change("base.jar", false);
        check(allOff.selected().isEmpty() && ModSelection.read(home).selected().isEmpty(), "next startup applies selection");
        rejects(() -> allOff.change("child.jar", true), "enable without required base refused");
        var baseOn = allOff.change("base.jar", true);
        var allOn = baseOn.change("child.jar", true);
        check(allOn.selected().size() == 2 && Arrays.equals(base, Files.readAllBytes(mods.resolve("base.jar"))), "enable restores graph without modifying JAR");
        Files.write(mods.resolve("broken.jar"), new byte[] {1, 2, 3});
        check(allOn.inventory().stream().anyMatch(e -> e.file().equals("broken.jar") && e.manifest() == null && !e.error().isEmpty()), "inventory diagnoses malformed enabled JAR");
        rejects(allOn::selected, "malformed enabled JAR refuses launch");
        var fixed = allOn.change("broken.jar", false);
        check(fixed.selected().size() == 2 && !fixed.inventory().stream().filter(e -> e.file().equals("broken.jar")).findFirst().orElseThrow().enabled(), "disabled broken JAR skipped before manifest load");
        rejects(() -> fixed.change("broken.jar", true), "malformed JAR cannot be enabled");
        rejects(() -> fixed.change("../base.jar", false), "path traversal refused");
        rejects(() -> fixed.change("C:\\base.jar", false), "absolute path refused");
        rejects(() -> fixed.change("missing.jar", false), "missing target refused");
        rejects(() -> fixed.change("BASE.jar", false), "case alias must not silently produce ineffective disabled entry");
        jar(mods, "conflito.jar", "conflict", "", "base");
        var conflictOff = fixed.change("conflito.jar", false);
        rejects(() -> conflictOff.change("conflito.jar", true), "enable conflict refused");
        jar(mods, "Espaço ç.jar", "accent", "", "");
        var unicodeOff = conflictOff.change("Espaço ç.jar", false);
        check(ModSelection.read(home).inventory().stream().anyMatch(e -> e.file().equals("Espaço ç.jar") && !e.enabled()), "unicode filename roundtrip");
        jar(mods, "duplicate.jar", "base", "", "");
        var duplicateOff = unicodeOff.change("duplicate.jar", false);
        rejects(() -> duplicateOff.change("duplicate.jar", true), "duplicate id enable refused");
        byte[] config = Files.readAllBytes(home.resolve("mods-selection.properties"));
        Files.writeString(home.resolve("mods-selection.properties"), "format=99\n");
        rejects(() -> ModSelection.read(home), "unknown config version refuses");
        rejects(() -> duplicateOff.change("base.jar", true), "external config modification not overwritten");
        check(Files.readString(home.resolve("mods-selection.properties")).contains("99"), "corrupt config preserved");
        Files.writeString(home.resolve("mods-selection.properties"), "format=1\ndisabled.../x.jar=true\n");
        rejects(() -> ModSelection.read(home), "unsafe stored filename refused");
        Files.writeString(home.resolve("mods-selection.properties"), "format=1\nunknown=true\n");
        rejects(() -> ModSelection.read(home), "unknown fields refused");
        Files.write(home.resolve("mods-selection.properties"), new byte[0]);
        rejects(() -> ModSelection.read(home), "empty existing config fails closed instead of enabling all mods");
        Files.write(home.resolve("mods-selection.properties"), new byte[65537]);
        rejects(() -> ModSelection.read(home), "bounded config read");
        Files.write(home.resolve("mods-selection.properties"), config);
        try (var files = Files.list(home)) { check(files.noneMatch(p -> p.toString().endsWith(".tmp")), "no staging debris"); }
        CareerBridge.onEdt(() -> {
            var panel = ModManagerPanel.content(ModSelection.read(home).inventory(), Map.of("base.jar", br.vbrasfoot.api.ModState.ACTIVE));
            var table = (javax.swing.JTable) ((javax.swing.JScrollPane) panel.getComponent(0)).getViewport().getView();
            check(table.getRowCount() == 6 && !table.isCellEditable(0, 4), "bounded noneditable management inventory");
            boolean active = false;
            for (int i = 0; i < table.getRowCount(); i++) if (table.getValueAt(i, 0).equals("base.jar")) active = table.getValueAt(i, 3).equals("ACTIVE");
            check(active, "current state separate from next startup selection");
            for (int i = 0; i < table.getRowCount(); i++) if (table.getValueAt(i, 0).equals("duplicate.jar")) check(table.getValueAt(i, 3).equals("NAO CARREGADO"), "disabled duplicate ID not mistaken for loaded file");
            return null;
        });
        check(Main.Options.parse(new String[] {"--disable-mod", "base.jar"}).manageMod.equals("base.jar"), "disable option");
        check(Main.Options.parse(new String[] {"--enable-mod", "base.jar"}).enableMod, "enable option");
        rejects(() -> Main.Options.parse(new String[] {"--disable-mod", "base.jar", "--validate-only"}), "exclusive modes");
        rejects(() -> Main.Options.parse(new String[] {"--disable-mod", "base.jar", "--safe-mode"}), "no silent disable in safe mode");
        System.out.println("PASS: " + checks + " next-start mod selection checks (synthetic, no game).");
    }
}
