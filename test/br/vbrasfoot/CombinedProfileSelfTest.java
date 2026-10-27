package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Owned manifest/UI fixtures only; never initializes the original game. */
public final class CombinedProfileSelfTest {
    private static int checks;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "combined-profile-");
        Files.writeString(home.resolve("loans-only.txt"), "vbrasfoot-loans-only-v1");
        Files.writeString(home.resolve("distribution-profile.txt"), "vbrasfoot-combined-v1");
        var options = Main.Options.parse(new String[]{});
        Main.applyDistributionProfile(options, home);
        check(options.combined && !options.loansOnly && !options.sponsorsOnly, "combined marker overrides leftover loan profile");
        check(!Main.sponsorsOnlyProfile(home), "combined is not sponsors-only");
        Files.createDirectory(home.resolve("mods"));
        var plugin = Files.write(home.resolve("mods/ignored.jar"), new byte[]{1,2,3});
        String before = Profile.digest(plugin);
        SponsorsOnlyProfileSelfTest.jar(home.resolve("mods/br.vbrasfoot.sponsor-manager.jar"), SponsorActivationStore.OWNER);
        var selected = Main.selectedManifests(options, home);
        check(selected.size() == 1 && selected.get(0).id().equals(SponsorActivationStore.OWNER), "combined selects only sponsor; loans and screen recovery are built in");
        check(Profile.digest(plugin).equals(before), "extra plugin preserved without scanning/loading");
        var explicit = Main.Options.parse(new String[]{"--combined", "--probe-career-hooks"});
        Files.writeString(home.resolve("distribution-profile.txt"), "vbrasfoot-sponsors-only-v1");
        Main.applyDistributionProfile(explicit, home);
        check(explicit.combined && explicit.probe && !explicit.sponsorsOnly, "dedicated launcher overrides old sponsor profile");
        for (String flag : List.of("--loans-only", "--sponsors-only")) {
            boolean blocked = false;
            try { Main.Options.parse(new String[]{"--combined", flag}); } catch (IllegalArgumentException expected) { blocked = true; }
            check(blocked, "conflicting explicit profiles refused: " + flag);
        }
        var safe = Main.Options.parse(new String[]{"--combined", "--safe-mode"});
        check(Main.selectedManifests(safe, home).isEmpty(), "safe mode still ignores sponsor");
        try (var ui = new ModUi(new ClassLoader(){}, Logger.getLogger("combined-test"))) {
            ui.register("vbrasfoot:loans", "Emprestimos", () -> {});
            ui.start(true);
            var field = ModUi.class.getDeclaredField("actions"); field.setAccessible(true);
            check(((Map<?,?>) field.get(ui)).keySet().equals(Set.of("vbrasfoot:loans", "vbrasfoot:restore-window")),
                    "combined includes explicit screen recovery beside loan action");
        }
        SponsorsOnlyProfileSelfTest.jar(home.resolve("mods/br.vbrasfoot.sponsor-manager.jar"), "wrong.owner");
        boolean blocked = false;
        try { Main.selectedManifests(explicit, home); } catch (java.io.IOException expected) { blocked = true; }
        check(blocked, "restricted combined profile rejects wrong plugin identity");
        System.out.println("PASS: " + checks + " combined profile checks; synthetic, no game started. " + home);
    }
}
