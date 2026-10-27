package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Owned profile/plugin byte fixtures, no original game execution. */
public final class LoansOnlyProfileSelfTest {
    private static int checks;
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); checks++; }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loans-only-");
        check(!Main.loansOnlyProfile(home), "normal distribution unchanged without profile");
        Files.writeString(home.resolve("loans-only.txt"), "vbrasfoot-loans-only-v1\r\n");
        check(Main.loansOnlyProfile(home), "portable profile selects loans-only");
        Files.createDirectory(home.resolve("mods"));
        Path plugin = Files.write(home.resolve("mods/old-sponsor.jar"), new byte[]{1,2,3});
        String before = Profile.digest(plugin);
        var options = Main.Options.parse(new String[]{"--loans-only", "--probe-career-hooks"});
        check(options.loansOnly && options.probe, "launcher flags parsed without forwarding to original game");
        check(Main.selectedManifests(options, home).isEmpty(), "even malformed old plugins are never scanned/selected in loans-only");
        check(Profile.digest(plugin).equals(before), "existing plugin preserved without deleting or modifying");
        var profileOptions = Main.Options.parse(new String[]{}); profileOptions.loansOnly = Main.loansOnlyProfile(home);
        check(Main.selectedManifests(profileOptions, home).isEmpty(), "normal launcher respects installation profile too");
        var field = ModUi.class.getDeclaredField("actions"); field.setAccessible(true);
        try (var ui = new ModUi(new ClassLoader(){}, Logger.getLogger("loans-only-test"))) {
            ui.register("vbrasfoot:loans", "Emprestimos", () -> {}); ui.start(false);
            check(((Map<?,?>)field.get(ui)).keySet().equals(Set.of("vbrasfoot:loans")), "only loan menu action, no window recovery or diagnostic menu added");
        }
        Files.writeString(home.resolve("loans-only.txt"), "unknown");
        boolean rejected = false; try { Main.loansOnlyProfile(home); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "corrupt profile fails closed instead of enabling old plugins");
        check(Profile.digest(plugin).equals(before), "validation never touches old plugin");
        System.out.println("PASS: " + checks + " loans-only distribution checks; no game started. " + home);
    }
}
