package br.vbrasfoot.sponsormanager;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

public final class SponsorStoreTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static byte[] png() throws IOException {
        var image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB); image.setRGB(3, 4, 0xffcc2200);
        try (var out = new ByteArrayOutputStream()) { ImageIO.write(image, "png", out); return out.toByteArray(); }
    }
    private static SponsorContract sponsor(String profile, String name, boolean naming, boolean ended) throws IOException {
        return sponsor(10, profile, name, naming, ended);
    }
    private static SponsorContract sponsor(int clubId, String profile, String name, boolean naming, boolean ended) throws IOException {
        return new SponsorContract(UUID.randomUUID(), profile, clubId, "Flamengo", name, 1200,
                SponsorContract.Cadence.MONTHLY, 0, true, 100, naming, naming ? "Arena Marca" : "", png(), ended);
    }
    private static void rejects(Throwing action, String label) throws Exception {
        try { action.run(); throw new AssertionError("Accepted invalid case: " + label); }
        catch (IOException | IllegalArgumentException expected) { checks++; }
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        var first = sponsor("Flamengo teste", "Marca A", true, false);
        var bytes = SponsorStore.encode(List.of(first)); var decoded = SponsorStore.decode(bytes);
        check(decoded.size() == 1 && decoded.get(0).sponsorName().equals("Marca A") && decoded.get(0).baseAmount() == 1200, "codec roundtrip");
        var featureBytes = SponsorFeatureStore.encode(java.util.Optional.of(first.id()));
        check(SponsorFeatureStore.decode(featureBytes).equals(java.util.Optional.of(first.id())), "explicit featured-logo selection roundtrips");
        check(SponsorFeatureStore.decode(new byte[0]).isEmpty(), "legacy settings do not select a logo automatically");
        var gameScope = new br.vbrasfoot.api.GameAccess.CareerScope("session", 1);
        var dataScope = new br.vbrasfoot.api.CareerDataAccess.Scope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), 10);
        var careerData = new br.vbrasfoot.api.CareerDataAccess() {
            public java.util.Optional<Scope> current() { return java.util.Optional.of(dataScope); }
            public boolean isSelected(Scope scope) { return dataScope.equals(scope); }
            public byte[] read(Scope scope, String file) { return new byte[0]; }
            public void write(Scope scope, String file, byte[] bytes) { }
        };
        var banner = new SponsorBanner(careerData);
        banner.replace(decoded, java.util.Optional.of(first.id()), java.util.Optional.of(gameScope), java.util.Optional.of(dataScope));
        var human = new br.vbrasfoot.api.GameAccess.ClubSummary(0, 10, "Flamengo", true, 22);
        check(banner.banner(human, gameScope).orElseThrow().label().equals("Prévia: Marca A"), "selected logo is a visual preview for the exact club");
        check(banner.banner(new br.vbrasfoot.api.GameAccess.ClubSummary(0, 10, "Outro Flamengo", true, 22), gameScope).isEmpty(),
                "featured logo does not cross exact club-name match");
        banner.replace(decoded, java.util.Optional.of(first.id()), java.util.Optional.of(gameScope), java.util.Optional.of(dataScope));
        check(banner.banner(human, new br.vbrasfoot.api.GameAccess.CareerScope("other", 1)).isEmpty(), "same club in another session cannot display stale logo");
        banner.replace(List.of(first.withEnded(true)), java.util.Optional.of(first.id()), java.util.Optional.of(gameScope), java.util.Optional.of(dataScope));
        check(banner.banner(human, gameScope).isEmpty(), "archived logo is not rendered");
        check(decoded.get(0).cadence() == SponsorContract.Cadence.MONTHLY && decoded.get(0).bonusEnabled()
                && decoded.get(0).homeWinBonus() == 100 && decoded.get(0).namingRights(), "proposal fields survive");
        var unknownId = sponsor(-1, "legacy-id", "Marca ID desconhecido", false, false);
        var unknownIdDecoded = SponsorStore.decode(SponsorStore.encode(List.of(unknownId)));
        check(unknownIdDecoded.get(0).clubId() == -1 && SponsorModel.forClub(unknownIdDecoded, -1, "Flamengo").size() == 1,
                "unknown native club ID sentinel persists and filters with exact club name");
        byte[] mutable = first.logoPng(); mutable[0] ^= 1;
        check(SponsorLogo.decode(first.logoPng()).getWidth() == 16, "logo bytes are defensively copied");
        check(SponsorStore.decode(new byte[0]).isEmpty(), "empty user-data store begins empty");

        var two = sponsor("Flamengo teste", "Marca B", false, false);
        var withTwo = SponsorModel.add(List.of(first), two);
        check(withTwo.size() == 2, "multiple sponsor configurations per club allowed");
        check(SponsorModel.forProfile(withTwo, "Outro save").isEmpty(), "manual profile labels separate views");
        check(SponsorModel.forClub(withTwo, 10, "Flamengo").size() == 2
                && SponsorModel.forClub(withTwo, 11, "Outro").isEmpty(), "fixed club view filters by exact current team");
        rejects(() -> SponsorModel.add(withTwo, sponsor("Flamengo teste", "Marca C", true, false)), "two concurrent naming rights in one club/profile label");
        var otherLabelNaming = sponsor("other-label", "Marca D", true, false);
        rejects(() -> SponsorModel.add(withTwo, otherLabelNaming), "naming rights uniqueness follows fixed club despite legacy labels");
        var archived = SponsorModel.end(withTwo, first.id(), "Flamengo teste");
        check(archived.get(0).ended() && !archived.get(1).ended(), "archive changes only selected metadata record");
        check(SponsorModel.add(archived, sponsor("Flamengo teste", "Marca C", true, false)).size() == 3, "archived naming rights no longer blocks a new proposal");
        rejects(() -> SponsorModel.end(archived, first.id(), "Outro save"), "archive cannot cross manual profile");
        rejects(() -> SponsorStore.decode(java.util.Arrays.copyOf(bytes, bytes.length + 1)), "trailing bytes refused");
        rejects(() -> new SponsorContract(UUID.randomUUID(), "p", 1, "c", "s", 1,
                SponsorContract.Cadence.EVERY_N_DAYS, 3661, false, 0, false, "", png(), false), "interval over maximum refused");
        rejects(() -> new SponsorContract(UUID.randomUUID(), "p", 1, "c", "s", 1,
                SponsorContract.Cadence.ON_ACTIVATION, 0, false, 3, false, "", png(), false), "disabled bonus cannot carry amount");
        rejects(() -> sponsor(-2, "p", "invalid club ID", false, false), "club IDs below the unknown sentinel are rejected");

        var temp = Files.createTempFile("sponsor-logo", ".png");
        try {
            var large = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
            try (var out = Files.newOutputStream(temp)) { ImageIO.write(large, "png", out); }
            var normalized = SponsorLogo.decode(SponsorLogo.importPng(temp));
            check(normalized.getWidth() == 512 && normalized.getHeight() == 256, "local image becomes a bounded normalized PNG");
            var preview = SponsorLogo.fixedPreview(SponsorLogo.importPng(temp));
            var canvas = (BufferedImage) preview.getImage();
            check(canvas.getWidth() == 128 && canvas.getHeight() == 40, "preview canvas has fixed dimensions");
            check((canvas.getRGB(0, 20) >>> 24) == 0 && (canvas.getRGB(24, 20) >>> 24) == 255
                    && (canvas.getRGB(103, 20) >>> 24) == 255 && (canvas.getRGB(104, 20) >>> 24) == 0,
                    "2:1 logo remains 80x40 centered, rather than stretched to 128x40");
            try (var out = Files.newOutputStream(temp)) { ImageIO.write(large, "jpeg", out); }
            var jpeg = SponsorLogo.importPng(temp);
            check((jpeg[0] & 255) == 137 && SponsorLogo.decode(jpeg).getWidth() == 512,
                    "JPEG input is accepted and normalized to PNG independent of file extension");
            Files.writeString(temp, "not an image");
            rejects(() -> SponsorLogo.importPng(temp), "invalid local image refused");
        } finally { Files.deleteIfExists(temp); }
        System.out.println("PASS: " + checks + " sponsor prototype model/codec/logo checks; no game APIs or saves touched");
    }
}
