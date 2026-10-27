package br.vbrasfoot;

import java.awt.event.ActionListener;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;

/** Replaces rather than stacks an effect callback; owns and restores only its exact listener. */
final class NativeLoanButtonBinding implements AutoCloseable {
    static final String LABEL = "Empréstimos";
    private final JButton button;
    private final ActionListener[] original;
    private final ActionListener replacement;
    private final String text, tooltip;
    private final boolean enabled;
    private final BooleanSupplier available;
    private boolean closed;
    NativeLoanButtonBinding(JButton button, BooleanSupplier available, Runnable action) {
        this.button = button; this.available = available;
        original = button.getActionListeners(); text = button.getText(); tooltip = button.getToolTipText(); enabled = button.isEnabled();
        for (var listener : original) button.removeActionListener(listener);
        replacement = event -> { if (!closed && available.getAsBoolean()) action.run(); };
        button.addActionListener(replacement); button.setText(LABEL);
        button.setToolTipText("Ja listado: emprestimo direto ao seu clube, sem taxa. Fora da lista: avaliar proposta e confirmar."); update();
    }
    void update() {
        if (closed) return;
        var listeners = button.getActionListeners();
        // Never allow a second native/mod effect listener beside this one.
        if (listeners.length != 1 || listeners[0] != replacement) { button.setEnabled(false); return; }
        // The original view can rewrite the caption when the player selection changes.
        if (!"Avaliando...".equals(button.getText()) && !"Executando...".equals(button.getText())) button.setText(LABEL);
        button.setEnabled(available.getAsBoolean());
    }
    @Override public void close() {
        if (closed) return; closed = true; button.removeActionListener(replacement);
        for (int i = original.length - 1; i >= 0; i--) button.addActionListener(original[i]);
        button.setText(text); button.setToolTipText(tooltip); button.setEnabled(enabled);
    }
}
