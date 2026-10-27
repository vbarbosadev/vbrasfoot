package br.vbrasfoot;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Presentation-only checks. No game, save writes or transfer authority. */
public final class NativeLoanDialogSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static List<JButton> buttons(Container container) {
        var result = new ArrayList<JButton>();
        for (var child : container.getComponents()) {
            if (child instanceof JButton button && button.getText() != null && !button.getText().isBlank()) result.add(button);
            else if (child instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }
    private static void layout(Container container) {
        container.doLayout(); for (var child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    public static void main(String[] args) throws Exception {
        Path preview = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loan-panel-preview-");
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger(); var accepted = new AtomicInteger();
            var confirm = NativeLoanDialog.panel("Pedido de emprestimo", "Gabriel Inocencio",
                    "Emprestimo aceito.\n\nClube de origem -> Flamengo\n\nTaxa a pagar: 256600\nPrazo: 366 dias.", true,
                    value -> { calls.incrementAndGet(); if (value) accepted.incrementAndGet(); });
            check(confirm.getBackground().equals(NativeLoanDialog.FRAME), "blue frame matches requested style");
            check(confirm.getPreferredSize().equals(new Dimension(466, 365)), "compact dimensions instead of oversized message box");
            var actions = buttons(confirm);
            check(actions.size() == 2 && actions.get(0).getText().equals("Cancelar") && actions.get(1).getText().equals("Confirmar"), "explicit cancel and confirmation");
            check(calls.get() == 0, "building or painting panel never authorizes a request");
            actions.get(0).doClick(); check(calls.get() == 1 && accepted.get() == 0, "cancel is not acceptance");
            actions.get(1).doClick(); check(calls.get() == 2 && accepted.get() == 1, "confirmation is explicit");
            var refused = NativeLoanDialog.panel("Resposta do emprestimo", "Ganso", "Recusado: historico insuficiente para avaliar.", false,
                    value -> { check(!value, "close cannot authorize a transfer"); });
            check(buttons(refused).size() == 1 && buttons(refused).get(0).getText().equals("Fechar"), "refusal only has close");
            buttons(refused).get(0).doClick();
            var failure = new CareerAssociationCoordinator.HistoricalLoanFailure(List.of("Macarrao", "Matheus Reis"));
            String message = NativeLoanPresentation.failure(failure);
            check(message.contains("Macarrao, Matheus Reis") && message.contains("Nenhum novo pedido"), "unconfirmed historical players identified without suggesting replay");
            for (var entry : Map.of("accepted", confirm, "refused", refused).entrySet()) {
                var panel = entry.getValue(); panel.setSize(panel.getPreferredSize()); layout(panel);
                var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                try { ImageIO.write(image, "png", preview.resolve(entry.getKey() + ".png").toFile()); }
                catch (java.io.IOException failed) { throw new IllegalStateException(failed); }
            }
        });
        if (args.length == 1 && args[0].equals("--smoke")) SwingUtilities.invokeAndWait(() -> {
            var parent = new JFrame("Owned loan panel smoke"); parent.setSize(700, 520); parent.setLocationRelativeTo(null); parent.setVisible(true);
            try {
                for (String action : List.of("Cancelar", "Confirmar", "dispose")) {
                    var timer = new javax.swing.Timer(250, event -> {
                        for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.isShowing() && dialog.getOwner() == parent) {
                            if (action.equals("dispose")) dialog.dispose();
                            else buttons(dialog).stream().filter(button -> button.getText().equals(action)).findFirst().orElseThrow().doClick();
                        }
                    }); timer.setRepeats(false); timer.start();
                    try {
                        check(NativeLoanDialog.show(parent, "Jogador teste", "Sem taxa. Nenhum efeito real.", true) == action.equals("Confirmar"),
                                "modal result only accepts explicit confirmation: " + action);
                    } finally { timer.stop(); }
                }
            } finally { parent.dispose(); }
        });
        System.out.println("PASS: " + checks + " compact loan panel/presentation checks; no game effects. PREVIEW=" + preview);
    }
}
