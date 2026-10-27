package br.vbrasfoot;

import br.vbrasfoot.api.ClubBanner;
import br.vbrasfoot.api.ClubBannerProvider;
import br.vbrasfoot.api.GameAccess;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public final class ClubBannerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static byte[] png() throws Exception {
        var image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        try (var out = new ByteArrayOutputStream()) { ImageIO.write(image, "png", out); return out.toByteArray(); }
    }
    public static void main(String[] args) throws Exception {
        byte[] source = png();
        var fixed = ModUi.fitBanner(new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB));
        check(fixed.getWidth() == 128 && fixed.getHeight() == 40, "logo uses a fixed 128x40 letterboxed canvas");
        var currentScope = new GameAccess.CareerScope("session", 1);
        var banner = new ClubBanner(-1, "Clube", "Prévia", source, currentScope);
        source[0] ^= 1;
        byte[] copy = banner.png(); copy[0] ^= 1;
        check(ImageIO.read(new java.io.ByteArrayInputStream(banner.png())).getWidth() == 8, "banner retains defensive PNG copy");
        check(banner.clubId() == -1 && banner.clubName().equals("Clube"), "unknown native ID remains valid with exact club name");
        check(ModUi.matchesHumanClub(banner, new GameAccess.ClubSummary(4, -1, "Clube", true, 0), currentScope), "banner accepts exact name with unavailable native ID");
        check(!ModUi.matchesHumanClub(banner, new GameAccess.ClubSummary(4, 12, "Clube", true, 0), currentScope), "banner refuses mismatched native ID");
        check(!ModUi.matchesHumanClub(banner, new GameAccess.ClubSummary(4, -1, "Outro", true, 0), currentScope), "banner refuses inexact club name");
        check(!ModUi.matchesHumanClub(banner, new GameAccess.ClubSummary(4, -1, "Clube", true, 0), new GameAccess.CareerScope("other", 1)),
                "banner refuses the same club in another current career session");
        rejects(() -> new ClubBanner(-2, "Clube", "Prévia", png(), currentScope), "invalid club ID");
        rejects(() -> new ClubBanner(1, " ", "Prévia", png(), currentScope), "blank club name");
        rejects(() -> new ClubBanner(1, "Clube", "x".repeat(121), png(), currentScope), "oversized label");
        rejects(() -> new ClubBanner(1, "Clube", "Prévia", new byte[ClubBanner.MAX_PNG_BYTES + 1], currentScope), "oversized PNG payload");

        Logger logger = Logger.getLogger("club-banner-test"); logger.setUseParentHandlers(false);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var toolbar = new javax.swing.JToolBar();
            var menu = new javax.swing.JButton("vbrasfoot");
            var options = new javax.swing.JButton("Opcoes");
            var date = new javax.swing.JLabel("23/10/2026");
            var strip = new javax.swing.JPanel();
            toolbar.add(menu); toolbar.add(strip); toolbar.add(options); toolbar.add(date);
            ModUi.placeAfterDate(toolbar, date, strip);
            check(toolbar.getComponentIndex(strip) == toolbar.getComponentIndex(date) + 1, "banner moves after original date");
            check(toolbar.getComponentIndex(menu) < toolbar.getComponentIndex(options)
                    && toolbar.getComponentIndex(options) < toolbar.getComponentIndex(date), "original controls keep their order");
            ModUi.placeAfterDate(toolbar, date, strip);
            check(toolbar.getComponentCount() == 4, "repeated placement does not duplicate banner");
        });
        try (var ui = new ModUi(new ClassLoader() { }, logger)) {
            var provider = (ClubBannerProvider) (ignored, scope) -> Optional.of(banner);
            AutoCloseable first = ui.registerClubBanner("fixture", provider);
            boolean duplicate = false;
            try { ui.registerClubBanner("fixture", provider); } catch (IllegalArgumentException expected) { duplicate = true; }
            check(duplicate, "duplicate live banner ID refused");
            first.close(); first.close();
            AutoCloseable replacement = ui.registerClubBanner("fixture", provider);
            replacement.close();
        }
        System.out.println("PASS: " + checks + " bounded read-only club banner checks; no game started.");
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
    private static void rejects(Throwing operation, String label) throws Exception {
        try { operation.run(); throw new AssertionError("Accepted " + label); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
