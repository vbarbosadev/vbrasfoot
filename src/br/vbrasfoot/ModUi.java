package br.vbrasfoot;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.Timer;

final class ModUi implements AutoCloseable {
    private record Action(String label, Runnable callback) { }
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final ClassLoader runtime;
    private final Logger logger;
    private final EmbeddedPanels panels;
    private Timer timer;
    private final java.util.Set<java.awt.Window> ownedWindows = new java.util.HashSet<>();
    void own(java.awt.Window window) {
        ownedWindows.add(window);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { ownedWindows.remove(window); }
        });
    }
    private final java.util.Set<JButton> installed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    ModUi(ClassLoader runtime, Logger logger) { this.runtime = runtime; this.logger = logger; this.panels = new EmbeddedPanels(logger); }
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
    void invoke(String id) {
        Action action;
        synchronized (this) { action = actions.get(id); }
        if (action != null) action.callback().run();
    }

    void start() {
        CareerBridge.onEdt(() -> {
            register("vbrasfoot:restore-window", "Restaurar janela na area do monitor", () -> {
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
            toolbar.revalidate();
            toolbar.repaint();
            frame.validate();
        } catch (ReflectiveOperationException failure) {
            logger.log(Level.SEVERE, "Menu incompativel com o runtime", failure);
            timer.stop();
        }
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
            panels.close();
            for (var window : java.util.List.copyOf(ownedWindows)) window.dispose();
            ownedWindows.clear();
            for (JButton button : installed) {
                Container parent = button.getParent();
                if (parent != null) { parent.remove(button); parent.revalidate(); parent.repaint(); }
            }
            installed.clear();
            synchronized (this) { actions.clear(); }
            return null;
        });
    }
}
