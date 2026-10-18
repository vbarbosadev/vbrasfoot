package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.awt.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.*;
import javax.swing.*;

/** One overlay per loader session. Never replaces or reparents the original content pane. */
final class EmbeddedPanels {
    private final Logger logger;
    private JFrame frame;
    private Component previous;
    private JPanel overlay;
    private ModPanel panel;
    private String owner;
    private BooleanSupplier stillCurrent;
    EmbeddedPanels(Logger logger) { this.logger = logger; }
    void open(String id, String title, JFrame window, UiTheme theme, Function<UiTheme, ModPanel> factory, BooleanSupplier current) {
        requireEdt();
        close();
        if (!window.isDisplayable() || window.getGlassPane().isVisible()) throw new IllegalStateException("Outra camada da UI esta ativa; feche-a antes de abrir o painel");
        ModPanel created = java.util.Objects.requireNonNull(factory.apply(theme), "panel");
        try {
            JComponent content = java.util.Objects.requireNonNull(created.component(), "component");
            if (content.getParent() != null) throw new IllegalArgumentException("Componente do mod ja pertence a outra interface");
            JPanel card = theme.style(new JPanel(new BorderLayout(0, 8)), UiTheme.Role.BODY);
            card.setName("vbrasfoot.embedded-card");
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0, 20, 30), 2), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            JPanel layer = new JPanel(null) {
                @Override public void doLayout() {
                    Dimension preferred = card.getPreferredSize();
                    int width = Math.max(1, Math.min(Math.min(preferred.width, 1200), getWidth() - 32));
                    int height = Math.max(1, Math.min(Math.min(preferred.height, 740), getHeight() - 32));
                    card.setBounds((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
                }
            };
            layer.setOpaque(false);
            layer.setName("vbrasfoot.embedded-panel");
            JPanel header = theme.style(new JPanel(new BorderLayout(12, 0)), UiTheme.Role.HEADER);
            header.add(theme.style(new JLabel(title), UiTheme.Role.TITLE), BorderLayout.CENTER);
            JButton back = theme.style(new JButton("X"), UiTheme.Role.SECONDARY_BUTTON); back.setName("vbrasfoot.embedded-back");
            back.setToolTipText("Fechar e voltar ao jogo (Escape)");
            back.getAccessibleContext().setAccessibleName("Fechar comparador e voltar ao jogo");
            back.setPreferredSize(new Dimension(50, 26));
            back.addActionListener(event -> close());
            header.add(back, BorderLayout.EAST);
            card.add(header, BorderLayout.NORTH); card.add(content, BorderLayout.CENTER); layer.add(card);
            theme.apply(layer);
            layer.addMouseListener(new java.awt.event.MouseAdapter() { });
            layer.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
            layer.setFocusCycleRoot(true);
            layer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
            layer.getActionMap().put("back", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent event) { close(); } });
            previous = window.getGlassPane(); frame = window; overlay = layer; panel = created; owner = id; stillCurrent = current;
            window.setGlassPane(layer); layer.setVisible(true); back.requestFocusInWindow();
            logger.info("embedded-panel opened id=" + id);
        } catch (RuntimeException | Error failure) {
            if (panel == created) close();
            else try { created.close(); } catch (RuntimeException | Error cleanup) { logger.log(Level.WARNING, "Falha cleanup painel nao aberto", cleanup); }
            throw failure;
        }
    }
    void check() { requireEdt(); if (panel != null && (!frame.isDisplayable() || frame.getGlassPane() != overlay || !stillCurrent.getAsBoolean())) close(); }
    void closeOwner(String id) { requireEdt(); if (id.equals(owner)) close(); }
    void close() {
        requireEdt(); if (panel == null) return;
        ModPanel closed = panel; panel = null;
        JFrame oldFrame = frame; Component oldPrevious = previous; JPanel oldOverlay = overlay;
        owner = null; frame = null; previous = null; overlay = null; stillCurrent = null;
        oldOverlay.setVisible(false); oldOverlay.removeAll();
        if (oldFrame.getGlassPane() == oldOverlay) { oldFrame.setGlassPane(oldPrevious); oldPrevious.setVisible(false); }
        try { closed.close(); } catch (RuntimeException | Error failure) { logger.log(Level.WARNING, "Falha ao descartar painel integrado", failure); }
        logger.info("embedded-panel closed; original content preserved");
    }
    private static void requireEdt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Painel integrado exige EDT"); }
}
