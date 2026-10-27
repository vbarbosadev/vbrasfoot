package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.nio.charset.StandardCharsets;

/** Profile isolation with owned manifest-only JARs; no game/plugin initialization. */
public final class SponsorsOnlyProfileSelfTest {
    static int checks;
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    static void jar(Path path, String id) throws Exception {
        try (var zip = new JarOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            zip.write(("id=" + id + "\nname=Synthetic\nversion=1\napiVersion=1\ngameProfile=brasfoot22-23\nmainClass=fixture.NeverInitialize\npermissions=game.read\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsors-only-");
        check(!Main.sponsorsOnlyProfile(home), "normal installations unaffected without marker");
        Files.writeString(home.resolve("distribution-profile.txt"), "vbrasfoot-sponsors-only-v1\r\n");
        Files.writeString(home.resolve("loans-only.txt"), "vbrasfoot-loans-only-v1");
        check(Main.sponsorsOnlyProfile(home), "sponsor distribution remains identifiable over old loan profile");
        var options = Main.Options.parse(new String[]{"--sponsors-only", "--probe-career-hooks"});
        check(options.sponsorsOnly && !options.loansOnly && options.probe, "dedicated sponsor launcher flags");
        Files.createDirectory(home.resolve("mods"));
        var extra = Files.write(home.resolve("mods/broken-extra.jar"), new byte[]{1,2,3}); String before = Profile.digest(extra);
        Path sponsor = home.resolve("mods/br.vbrasfoot.sponsor-manager.jar"); jar(sponsor, SponsorActivationStore.OWNER);
        var selected = Main.selectedManifests(options, home);
        check(selected.size() == 1 && selected.get(0).id().equals(SponsorActivationStore.OWNER), "only sponsor selected without inspecting malformed extras");
        check(Profile.digest(extra).equals(before), "old plugin not removed or modified");
        jar(sponsor, "wrong.owner");
        boolean blocked = false; try { Main.selectedManifests(options, home); } catch (java.io.IOException expected) { blocked = true; }
        check(blocked, "wrong owner cannot replace sponsor in restricted profile");
        boolean conflict = false; try { Main.Options.parse(new String[]{"--sponsors-only", "--loans-only"}); } catch (IllegalArgumentException expected) { conflict = true; }
        check(conflict, "conflicting explicit profiles refused");
        Files.writeString(home.resolve("distribution-profile.txt"), "unknown");
        blocked = false; try { Main.sponsorsOnlyProfile(home); } catch (java.io.IOException expected) { blocked = true; }
        check(blocked, "invalid profile never falls back to loading other plugins");
        System.out.println("PASS: " + checks + " sponsor-only profile checks; no game started. " + home);
    }
}
