package br.vbrasfoot;

import br.vbrasfoot.api.SponsorDialogs;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Presentation-only test: owned Swing panels, no game, save, sponsor service or payment. */
public final class SponsorDialogsSelfTest {
    private static int checks;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void collect(Container root, List<Component> all) {
        for (Component c : root.getComponents()) { all.add(c); if (c instanceof Container child) collect(child, all); }
    }
    private static void layout(Container root) { root.doLayout(); for (Component c : root.getComponents()) if (c instanceof Container child) layout(child); }
    public static void main(String[] args) throws Exception {
        boolean denied = false;
        try { SponsorDialogs.panel("Titulo", "Texto", true, value -> {}); } catch (IllegalStateException expected) { denied = true; }
        check(denied, "dialogs require EDT");
        SwingUtilities.invokeAndWait(() -> {
            var answer = new AtomicReference<Boolean>();
            JPanel confirm = SponsorDialogs.panel("Confirmar recebimento", "Receber R$ 1.000.000,00 de Lubrax?\n\nSaldo: 15.000.000 -> 16.000.000\n\nSalve pelo jogo depois.", true, answer::set);
            check(confirm.getBackground().equals(NativeLoanDialog.FRAME), "same blue frame as loan dialogs");
            check(confirm.getComponent(0).getBackground().equals(NativeLoanDialog.BACKGROUND), "same body color as loan dialogs");
            var widgets = new ArrayList<Component>(); collect(confirm, widgets);
            var buttons = widgets.stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b -> "sponsor.notice.action".equals(b.getName())).toList();
            check(buttons.size() == 2 && buttons.get(0).getText().equals("Cancelar") && buttons.get(1).getText().equals("Confirmar"), "explicit cancel/confirm choices");
            check(answer.get() == null, "construction never authorizes payment");
            buttons.get(0).doClick(); check(Boolean.FALSE.equals(answer.get()), "cancel never accepts");
            buttons.get(1).doClick(); check(Boolean.TRUE.equals(answer.get()), "explicit confirm accepts");
            check(widgets.stream().filter(JLabel.class::isInstance).map(JLabel.class::cast).anyMatch(label -> label.getForeground().equals(Color.YELLOW)), "yellow Brasfoot heading");
            var text = widgets.stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow();
            check(!text.isEditable() && text.getLineWrap() && text.getForeground().equals(Color.WHITE), "read-only wrapped white text");
            var notice = SponsorDialogs.panel("Patrocinio recebido", "Credito confirmado. Salve pelo jogo.", false, answer::set);
            widgets.clear(); collect(notice, widgets);
            check(widgets.stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b -> "sponsor.notice.action".equals(b.getName())).map(JButton::getText).toList().equals(List.of("Fechar")), "result notice only has close, no payment action");
            confirm.setSize(confirm.getPreferredSize()); layout(confirm);
            check(text.getHeight() > 80 && buttons.stream().allMatch(b -> b.getHeight() > 20), "text/button layout fits compact dialog");
            if (args.length > 0) try {
                var image = new java.awt.image.BufferedImage(confirm.getWidth(), confirm.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); confirm.printAll(graphics); graphics.dispose();
                javax.imageio.ImageIO.write(image, "PNG", Path.of(args[0]).toFile());
            } catch (Exception error) { throw new AssertionError(error); }
        });
        System.out.println("PASS: " + checks + " sponsor dialog style/confirmation checks; no game effects.");
    }
}
