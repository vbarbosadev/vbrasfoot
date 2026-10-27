package br.vbrasfoot;

import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Owned, modal presentation only. No game objects, metadata writes or effect callbacks. */
final class NativeLoanDialog {
    static final Color FRAME = new Color(0, 73, 101), BACKGROUND = new Color(103, 120, 98);
    private NativeLoanDialog() { }

    static JPanel panel(String title, String player, String text, boolean confirmation, Consumer<Boolean> answer) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Loan panel requires EDT");
        var outer = new JPanel(new BorderLayout()); outer.setBackground(FRAME);
        outer.setBorder(new EmptyBorder(14, 22, 14, 18));
        var body = new JPanel(new BorderLayout(0, 18)); body.setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(28, 32, 26, 32));
        var headings = new JPanel(); headings.setOpaque(false); headings.setLayout(new BoxLayout(headings, BoxLayout.Y_AXIS));
        var heading = new JLabel(title); heading.setForeground(Color.YELLOW);
        heading.setFont(new Font(Font.SERIF, Font.BOLD, 17)); heading.setAlignmentX(.5f); headings.add(heading);
        if (player != null && !player.isBlank()) {
            headings.add(Box.createVerticalStrut(10));
            var name = new JLabel(player); name.setForeground(Color.WHITE); name.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            name.setAlignmentX(.5f); headings.add(name);
        }
        body.add(headings, BorderLayout.NORTH);
        var message = new JTextArea(text); message.setEditable(false); message.setFocusable(false);
        message.setLineWrap(true); message.setWrapStyleWord(true); message.setOpaque(false);
        message.setForeground(Color.WHITE); message.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        message.setBorder(new EmptyBorder(8, 0, 8, 0));
        var scroll = new JScrollPane(message); scroll.setBorder(null); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        body.add(scroll, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0)); buttons.setOpaque(false);
        if (confirmation) buttons.add(button("Cancelar", () -> answer.accept(false)));
        buttons.add(button(confirmation ? "Confirmar" : "Fechar", () -> answer.accept(confirmation)));
        body.add(buttons, BorderLayout.SOUTH); outer.add(body);
        outer.setPreferredSize(new Dimension(466, 365));
        return outer;
    }
    private static JButton button(String text, Runnable action) {
        var button = new JButton(text); button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setForeground(new Color(20, 40, 55)); button.setBackground(new Color(225, 237, 245));
        button.setPreferredSize(new Dimension(114, 32)); button.addActionListener(event -> action.run()); return button;
    }
    static boolean show(Component parent, String player, String text, boolean confirmation) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Loan dialog requires EDT");
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        var dialog = new JDialog(owner, "Emprestimo", Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setUndecorated(true); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        boolean[] accepted = {false};
        var panel = panel(confirmation ? "Pedido de emprestimo" : "Resposta do emprestimo", player, text, confirmation,
                value -> { accepted[0] = value; dialog.dispose(); });
        dialog.setContentPane(panel); dialog.pack();
        Rectangle bounds = parent.getGraphicsConfiguration() == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                : parent.getGraphicsConfiguration().getBounds();
        dialog.setSize(Math.min(dialog.getWidth(), Math.max(240, bounds.width - 40)), Math.min(dialog.getHeight(), Math.max(220, bounds.height - 60)));
        dialog.setLocationRelativeTo(parent);
        var close = new AbstractAction() { public void actionPerformed(ActionEvent event) { dialog.dispose(); } };
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", close);
        // No default acceptance button: Enter must not accidentally authorize a transfer.
        try { dialog.setVisible(true); return accepted[0]; }
        finally { dialog.dispose(); }
    }
}
