package br.vbrasfoot;

import br.vbrasfoot.api.ClubBanner;
import br.vbrasfoot.api.ClubBannerProvider;
import br.vbrasfoot.api.GameAccess;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.JToolBar;
import javax.swing.Timer;

final class ModUi implements AutoCloseable {
    private record Action(String label, Runnable callback) { }
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final ClassLoader runtime;
    private final Logger logger;
    private final EmbeddedPanels panels;
    private final CareerBridge careerBridge;
    private final Map<String, ClubBannerProvider> clubBanners = new LinkedHashMap<>();
    private final JPanel bannerStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final Map<String, BannerIcon> bannerIcons = new HashMap<>();
    private final Set<String> invalidBannerLogged = new HashSet<>();
    private JToolBar bannerToolbar;
    private String bannerSignature = "";
    private record BannerIcon(String digest, ImageIcon icon) { }
    private Timer timer;
    private final java.util.Set<java.awt.Window> ownedWindows = new java.util.HashSet<>();
    private final java.util.List<AutoCloseable> ownedIntegrations = new java.util.ArrayList<>();
    void own(AutoCloseable integration) { ownedIntegrations.add(integration); }
    void own(java.awt.Window window) {
        ownedWindows.add(window);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { ownedWindows.remove(window); }
        });
    }
    private final java.util.Set<JButton> installed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    ModUi(ClassLoader runtime, Logger logger) {
        this.runtime = runtime; this.logger = logger; this.panels = new EmbeddedPanels(logger);
        this.careerBridge = new CareerBridge(runtime);
        bannerStrip.setOpaque(false); bannerStrip.setVisible(false);
    }
    CareerBridge bridge() { return careerBridge; }
    void closePanel(String id) { javax.swing.SwingUtilities.invokeLater(() -> panels.closeOwner(id)); }
    void openPanel(String id, String title, java.util.function.Function<br.vbrasfoot.api.UiTheme, br.vbrasfoot.api.ModPanel> factory) {
        CareerBridge.onEdt(() -> {
            Object window = Class.forName("best.n", false, runtime).getMethod("db").invoke(null);
            if (!(window instanceof JFrame frame)) throw new IllegalStateException("Janela da carreira indisponivel");
            JPanel original = careerPanel(frame.getContentPane());
            if (original == null) throw new IllegalStateException("Abra a tela de elenco antes do painel");
            var field = original.getClass().getDeclaredField("HX"); field.setAccessible(true);
            JButton options = (JButton) field.get(original);
            var theme = new br.vbrasfoot.api.UiTheme(options.getFont(), original.getBackground(), original.getForeground(), options.getBackground(), options.getForeground());
            panels.open(id, title, frame, theme, factory, () -> careerPanel(frame.getContentPane()) == original);
            return null;
        });
    }

    synchronized void register(String id, String label, Runnable callback) {
        registerOwned(id, label, callback);
    }
    synchronized AutoCloseable registerOwned(String id, String label, Runnable callback) {
        if (id == null || label == null || callback == null || actions.containsKey(id)) {
            throw new IllegalArgumentException("Acao invalida ou duplicada: " + id);
        }
        Action action = new Action(label, callback);
        actions.put(id, action);
        return () -> unregister(id, action);
    }
    AutoCloseable registerClubBanner(String id, ClubBannerProvider provider) {
        if (id == null || id.isBlank() || provider == null) throw new IllegalArgumentException("Banner invalido");
        synchronized (this) {
            if (clubBanners.containsKey(id)) throw new IllegalArgumentException("Banner duplicado: " + id);
            if (clubBanners.size() >= 8) throw new IllegalStateException("Limite de 8 banners por sessao");
            clubBanners.put(id, provider);
        }
        return () -> {
            synchronized (ModUi.this) {
                if (clubBanners.get(id) != provider) return;
                clubBanners.remove(id);
                bannerSignature = "";
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                bannerStrip.removeAll(); bannerStrip.setVisible(false); bannerStrip.revalidate(); bannerStrip.repaint();
            });
        };
    }
    void invoke(String id) {
        Action action;
        synchronized (this) { action = actions.get(id); }
        if (action != null) action.callback().run();
    }

    void start() {
        start(true);
    }
    void start(boolean windowRecoveryMenu) {
        CareerBridge.onEdt(() -> {
            if (windowRecoveryMenu) register("vbrasfoot:restore-window", "Restaurar janela na area do monitor", () -> {
                CareerBridge.onEdt(() -> {
                    Object window = Class.forName("best.n", false, runtime).getMethod("db").invoke(null);
                    if (!(window instanceof JFrame frame)) throw new IllegalStateException("Janela da carreira indisponivel");
                    WindowRecovery.restore(frame, logger);
                    return null;
                });
            });
            timer = new Timer(800, event -> install());
            timer.start();
            return null;
        });
    }

    void unregister(String id) {
        Action action;
        synchronized (this) { action = actions.get(id); }
        if (action != null) unregister(id, action);
    }
    private void unregister(String id, Action expected) {
        // Revoke dispatch immediately; Swing cleanup is asynchronous and never waits for the EDT.
        synchronized (this) {
            if (actions.get(id) != expected) return;
            actions.remove(id);
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (JButton button : installed) {
                JPopupMenu menu = (JPopupMenu) button.getClientProperty("vbrasfoot.popup");
                for (Component item : menu.getComponents()) {
                    if (item instanceof JMenuItem action && id.equals(action.getClientProperty("vbrasfoot.action.id"))) menu.remove(action);
                }
                menu.putClientProperty("vbrasfoot.action." + id, null);
            }
        });
    }

    private synchronized void install() {
        try {
            panels.check();
            Object window = Class.forName("best.n", false, runtime).getMethod("db").invoke(null);
            if (!(window instanceof JFrame frame) || !frame.isDisplayable()) return;
            // Remove only the old loader menu, never any original menu bar entries.
            JMenuBar bar = frame.getJMenuBar();
            if (bar != null) {
                for (int i = bar.getMenuCount() - 1; i >= 0; i--) {
                    var candidate = bar.getMenu(i);
                    if (candidate != null && Boolean.TRUE.equals(candidate.getClientProperty("vbrasfoot.menu"))) bar.remove(i);
                }
                if (bar.getMenuCount() == 0) frame.setJMenuBar(null);
            }
            JPanel panel = careerPanel(frame.getContentPane());
            if (panel == null) return; // Wait for the squad screen; do not alter initial setup or dialogs.
            var toolbarField = panel.getClass().getDeclaredField("ve");
            toolbarField.setAccessible(true);
            var optionsField = panel.getClass().getDeclaredField("HX");
            optionsField.setAccessible(true);
            JToolBar toolbar = (JToolBar) toolbarField.get(panel);
            JButton options = (JButton) optionsField.get(panel);
            if (toolbar == null || options == null || options.getParent() != toolbar) return;
            JButton button = null;
            for (Component component : toolbar.getComponents()) {
                if (component instanceof JButton candidate && Boolean.TRUE.equals(candidate.getClientProperty("vbrasfoot.button"))) {
                    button = candidate;
                    break;
                }
            }
            if (button == null) {
                button = new JButton("vbrasfoot", options.getIcon());
                button.putClientProperty("vbrasfoot.button", true);
                button.setFont(options.getFont());
                button.setForeground(options.getForeground());
                button.setBackground(options.getBackground());
                button.setBorder(options.getBorder());
                button.setMargin(options.getMargin());
                button.setOpaque(options.isOpaque());
                button.setContentAreaFilled(options.isContentAreaFilled());
                button.setFocusPainted(options.isFocusPainted());
                button.setHorizontalTextPosition(options.getHorizontalTextPosition());
                button.setVerticalTextPosition(options.getVerticalTextPosition());
                button.setToolTipText("vbrasfoot — mods da carreira");
                Dimension preferred = button.getPreferredSize();
                Dimension size = new Dimension(Math.max(options.getPreferredSize().width, preferred.width),
                        Math.max(options.getPreferredSize().height, preferred.height));
                button.setPreferredSize(size);
                button.setMinimumSize(size);
                button.setMaximumSize(size);
                JPopupMenu popup = new JPopupMenu();
                button.putClientProperty("vbrasfoot.popup", popup);
                JButton owner = button;
                button.addActionListener(event -> popup.show(owner, 0, owner.getHeight()));
                toolbar.add(button, toolbar.getComponentIndex(options));
                installed.add(button);
                logger.info("Menu vbrasfoot integrado na barra interna da carreira, ao lado de Opcoes.");
                logger.info("career-window bounds=" + frame.getBounds() + " state=" + frame.getExtendedState()
                        + " undecorated=" + frame.isUndecorated() + " screen=" + frame.getGraphicsConfiguration().getBounds()
                        + " scale=" + frame.getGraphicsConfiguration().getDefaultTransform());
            }
            JPopupMenu menu = (JPopupMenu) button.getClientProperty("vbrasfoot.popup");
            for (var entry : actions.entrySet()) {
                String key = "vbrasfoot.action." + entry.getKey();
                if (Boolean.TRUE.equals(menu.getClientProperty(key))) continue;
                Action action = entry.getValue();
                JMenuItem item = new JMenuItem(action.label());
                item.putClientProperty("vbrasfoot.action.id", entry.getKey());
                item.addActionListener(event -> {
                    try { invoke(entry.getKey()); }
                    catch (RuntimeException | Error failure) {
                        logger.log(Level.WARNING, "Falha na acao " + entry.getKey(), failure);
                        JOptionPane.showMessageDialog(frame, failure.getMessage(), "vbrasfoot", JOptionPane.ERROR_MESSAGE);
                    }
                });
                menu.add(item);
                menu.putClientProperty(key, true);
            }
            var dateField = panel.getClass().getDeclaredField("Eg");
            dateField.setAccessible(true);
            JLabel date = (JLabel) dateField.get(panel);
            updateClubBanners(toolbar, date, options);
            toolbar.revalidate();
            toolbar.repaint();
            frame.validate();
        } catch (ReflectiveOperationException failure) {
            logger.log(Level.SEVERE, "Menu incompativel com o runtime", failure);
            timer.stop();
        }
    }

    private void updateClubBanners(JToolBar toolbar, JLabel date, JButton options) {
        if (bannerToolbar != null && bannerToolbar != toolbar && bannerStrip.getParent() == bannerToolbar) {
            bannerToolbar.remove(bannerStrip);
        }
        bannerToolbar = toolbar;
        placeAfterDate(toolbar, date, bannerStrip);

        List<Map.Entry<String, ClubBannerProvider>> providers;
        synchronized (this) { providers = new ArrayList<>(clubBanners.entrySet()); }
        CareerBridge.HumanClub human = null;
        if (!providers.isEmpty()) {
            try { human = careerBridge.currentHumanClub(); }
            catch (Exception failure) {
                logger.log(Level.FINE, "Clube humano indisponivel para banner read-only", failure);
            }
        }

        var rendered = new ArrayList<Map.Entry<String, ClubBanner>>();
        StringBuilder signature = new StringBuilder();
        if (human != null) for (var entry : providers) {
            Optional<ClubBanner> candidate;
            try { candidate = entry.getValue().banner(human.summary(), human.scope()); }
            catch (RuntimeException | Error failure) {
                logger.log(Level.WARNING, "Falha no provedor de banner " + entry.getKey(), failure);
                continue;
            }
            if (candidate == null || candidate.isEmpty()) continue;
            ClubBanner banner = candidate.orElseThrow();
            if (!matchesHumanClub(banner, human.summary(), human.scope())) continue;
            byte[] png = banner.png();
            String digest = digest(png);
            signature.append(entry.getKey()).append('\0').append(banner.clubId()).append('\0')
                    .append(banner.clubName()).append('\0').append(banner.label()).append('\0').append(digest).append('\n');
            rendered.add(Map.entry(entry.getKey(), banner));
        }
        String nextSignature = signature.toString();
        if (nextSignature.equals(bannerSignature)) return;
        bannerSignature = nextSignature;
        bannerStrip.removeAll();
        for (var entry : rendered) {
            String id = entry.getKey(); ClubBanner banner = entry.getValue();
            byte[] png = banner.png(); String hash = digest(png);
            BannerIcon cached = bannerIcons.get(id);
            if (cached == null || !cached.digest().equals(hash)) {
                try {
                    cached = new BannerIcon(hash, decodeBanner(png));
                    bannerIcons.put(id, cached);
                    invalidBannerLogged.remove(id);
                } catch (IOException invalid) {
                    if (invalidBannerLogged.add(id)) logger.log(Level.WARNING, "Imagem de banner recusada: " + id, invalid);
                    continue;
                }
            }
            JLabel label = new JLabel(cached.icon(), SwingConstants.CENTER);
            Dimension fixed = new Dimension(140, 46);
            label.setPreferredSize(fixed); label.setMinimumSize(fixed); label.setMaximumSize(fixed);
            label.setFont(options.getFont()); label.setForeground(options.getForeground()); label.setIconTextGap(5);
            label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(options.getForeground()),
                    javax.swing.BorderFactory.createEmptyBorder(2, 5, 2, 5)));
            label.setToolTipText(banner.label() + " — " + banner.clubName());
            bannerStrip.add(label);
        }
        bannerIcons.keySet().retainAll(providers.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        invalidBannerLogged.retainAll(providers.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        bannerStrip.setVisible(bannerStrip.getComponentCount() > 0);
        bannerStrip.revalidate(); bannerStrip.repaint();
    }

    static void placeAfterDate(JToolBar toolbar, JLabel date, JPanel strip) {
        if (date == null || date.getParent() != toolbar) { strip.setVisible(false); return; }
        int dateIndex = toolbar.getComponentIndex(date);
        int stripIndex = toolbar.getComponentIndex(strip);
        if (stripIndex == dateIndex + 1) return;
        if (strip.getParent() != null) strip.getParent().remove(strip);
        toolbar.add(strip, toolbar.getComponentIndex(date) + 1);
    }

    static boolean matchesHumanClub(ClubBanner banner, GameAccess.ClubSummary current, GameAccess.CareerScope scope) {
        return banner != null && current != null && banner.clubId() == current.id()
                && banner.clubName().equals(current.name()) && banner.scope().equals(scope);
    }

    private static ImageIcon decodeBanner(byte[] png) throws IOException {
        if (png.length == 0 || png.length > ClubBanner.MAX_PNG_BYTES) throw new IOException("PNG acima do limite");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            if (input == null) throw new IOException("PNG ilegivel");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Imagem desconhecida");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName())) throw new IOException("Somente PNG permitido");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 512 || height > 256 || (long) width * height > 131_072)
                    throw new IOException("Dimensoes do banner fora do limite");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("PNG vazio");
                return new ImageIcon(fitBanner(image));
            } finally { reader.dispose(); }
        }
    }
    static BufferedImage fitBanner(BufferedImage image) {
        BufferedImage canvas = new BufferedImage(128, 40, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min(128.0 / image.getWidth(), 40.0 / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale)), h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, (128 - w) / 2, (40 - h) / 2, w, h, null);
        } finally { graphics.dispose(); }
        return canvas;
    }

    private static String digest(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static JPanel careerPanel(Container container) {
        if (container instanceof JPanel panel && panel.getClass().getName().equals("a.eg")) return panel;
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                JPanel found = careerPanel(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override public void close() {
        CareerBridge.onEdt(() -> {
            if (timer != null) timer.stop();
            for (var integration : java.util.List.copyOf(ownedIntegrations)) {
                try { integration.close(); }
                catch (Exception failure) { logger.log(Level.WARNING, "Falha ao encerrar integracao interna", failure); }
            }
            ownedIntegrations.clear();
            panels.close();
            for (var window : java.util.List.copyOf(ownedWindows)) window.dispose();
            ownedWindows.clear();
            for (JButton button : installed) {
                Container parent = button.getParent();
                if (parent != null) { parent.remove(button); parent.revalidate(); parent.repaint(); }
            }
            if (bannerStrip.getParent() != null) bannerStrip.getParent().remove(bannerStrip);
            bannerStrip.removeAll();
            synchronized (this) { clubBanners.clear(); bannerIcons.clear(); invalidBannerLogged.clear(); }
            bannerToolbar = null; bannerSignature = "";
            installed.clear();
            synchronized (this) { actions.clear(); }
            return null;
        });
    }
}
