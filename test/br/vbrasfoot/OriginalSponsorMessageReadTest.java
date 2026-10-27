package br.vbrasfoot;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Run in a separate JVM with ONLY original game/dependency JARs and this test class. No loader classes. */
public final class OriginalSponsorMessageReadTest {
    private static Object call(Object receiver, String name) throws Exception { return receiver.getClass().getMethod(name).invoke(receiver); }
    private static Object field(Object receiver, String name) throws Exception { var f = receiver.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(receiver); }
    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(Path.of(".sponsor-test-fixture"))) throw new IllegalStateException("Marked disposable fixture required");
        try { Class.forName("br.vbrasfoot.SponsorService"); throw new AssertionError("Loader must not be on standalone classpath"); }
        catch (ClassNotFoundException expected) { }
        Class.forName("best.M").getConstructor(boolean.class).newInstance(true);
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Unmodified original game classes — no mod loaded"); frame.pack();
            try {
                Class.forName("best.n").getMethod("a", JFrame.class).invoke(null, frame);
                if (!(boolean)Class.forName("c.a").getMethod("c", String.class, boolean.class).invoke(null, args[0], false)) throw new AssertionError("Original load failed");
                Object roots = Class.forName("c.a").getField("SR").get(null);
                Object human = ((java.util.List<?>)call(roots, "aN")).get(0), coach = call(human, "ka");
                var saved = (ArrayList<?>)call(coach, "lQ");
                Object found = null;
                for (Object row : saved) if (String.valueOf(call(row, "vp")).contains("Patrocinio: Lubrax")) {
                    if (found != null) throw new AssertionError("Duplicate sponsor native rows"); found = row;
                }
                if (found == null || (boolean)call(found, "vr")) throw new AssertionError("Saved native informational message missing or exposes fine action");
                String body = (String)call(found, "vq");
                if (!body.contains(args[1]) || !body.contains("&lt;b&gt;Texto literal&lt;/b&gt;")) throw new AssertionError("Status/custom text not saved safely: " + body);
                if ((long)call(human, "kb") != Long.parseLong(args[2])) throw new AssertionError("Standalone balance mismatch");
                JPanel panel = (JPanel)Class.forName("a.bn").getConstructor(JDialog.class, ArrayList.class, Class.forName("best.ah")).newInstance(null, saved, human);
                JTable table = (JTable)field(panel, "Ad");
                java.util.List<?> rows = (java.util.List<?>)field(panel, "oh"); int index = rows.indexOf(found);
                table.setRowSelectionInterval(index, index);
                String rendered = ((JLabel)field(panel, "Ac")).getText();
                if (!rendered.contains(args[1]) || ((JButton)field(panel, "Aa")).isVisible() || ((JButton)field(panel, "zZ")).isVisible())
                    throw new AssertionError("Original central must display text without fine/receive button");
                panel.setSize(900, 440); layout(panel);
                var image = new java.awt.image.BufferedImage(900, 440, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); panel.printAll(g); g.dispose();
                javax.imageio.ImageIO.write(image, "PNG", Path.of(args[3]).toFile());
                System.out.println("PASS: standalone original decoder + central display/status/balance, no loader/mod classes, fine actions hidden. " + args[0]);
            } catch (Throwable failure) { error.set(failure); }
            finally { frame.dispose(); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container next) layout(next); }
}
