package br.vbrasfoot.api;

import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Sponsor notifications only, matching the native-loan dialog skin. No gameplay effects. */
public final class SponsorDialogs {
    private SponsorDialogs() { }
    public static JPanel panel(String title, String text, boolean confirmation, Consumer<Boolean> answer) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Notificacao exige EDT");
        var outer = new JPanel(new BorderLayout()); outer.setBackground(new Color(0, 73, 101));
        outer.setBorder(new EmptyBorder(14, 22, 14, 18));
        var body = new JPanel(new BorderLayout(0, 18)); body.setBackground(new Color(103, 120, 98));
        body.setBorder(new EmptyBorder(28, 32, 26, 32));
        var heading = new JLabel(title, SwingConstants.CENTER); heading.putClientProperty("html.disable", Boolean.TRUE);
        heading.setForeground(Color.YELLOW); heading.setFont(new Font(Font.SERIF, Font.BOLD, 17)); body.add(heading, BorderLayout.NORTH);
        var message = new JTextArea(text); message.setEditable(false); message.setFocusable(false);
        message.setLineWrap(true); message.setWrapStyleWord(true); message.setOpaque(false);
        message.setForeground(Color.WHITE); message.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        message.setBorder(new EmptyBorder(8, 0, 8, 0)); message.setName("sponsor.notice.text");
        var scroll = new JScrollPane(message); scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); body.add(scroll, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0)); buttons.setOpaque(false);
        if (confirmation) buttons.add(button("Cancelar", () -> answer.accept(false)));
        buttons.add(button(confirmation ? "Confirmar" : "Fechar", () -> answer.accept(confirmation)));
        body.add(buttons, BorderLayout.SOUTH); outer.add(body); outer.setPreferredSize(new Dimension(466, 365)); return outer;
    }
    private static JButton button(String text, Runnable action) {
        var button = new JButton(text); button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setName("sponsor.notice.action");
        button.setForeground(new Color(20, 40, 55)); button.setBackground(new Color(225, 237, 245));
        button.setPreferredSize(new Dimension(114, 32)); button.addActionListener(e -> action.run()); return button;
    }
    public static boolean confirm(Component parent, String title, String text) { return show(parent, title, text, true); }
    public static void message(Component parent, String title, String text) { show(parent, title, text, false); }
    public static int showConfirmDialog(Component parent, String text, String title, int options, int... type) {
        if (options != JOptionPane.OK_CANCEL_OPTION) throw new IllegalArgumentException("Somente confirmar/cancelar");
        return confirm(parent, title, text) ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
    }
    public static void showMessageDialog(Component parent, String text, String title, int type) { message(parent, title, text); }
    private static boolean show(Component parent, String title, String text, boolean confirmation) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Notificacao exige EDT");
        Window owner = parent instanceof Window window ? window : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setUndecorated(true); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        boolean[] accepted = {false};
        JPanel panel = panel(title, text, confirmation, value -> { accepted[0] = value; dialog.dispose(); });
        dialog.setContentPane(panel); dialog.pack();
        GraphicsConfiguration config = parent == null ? null : parent.getGraphicsConfiguration();
        Rectangle bounds = config == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() : config.getBounds();
        dialog.setSize(Math.min(dialog.getWidth(), Math.max(240, bounds.width - 40)), Math.min(dialog.getHeight(), Math.max(220, bounds.height - 60)));
        dialog.setLocationRelativeTo(parent);
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", new AbstractAction() { public void actionPerformed(ActionEvent e) { dialog.dispose(); } });
        // No default acceptance: Enter/closing the window must never authorize a payment.
        try { dialog.setVisible(true); return accepted[0]; } finally { dialog.dispose(); }
    }
}
