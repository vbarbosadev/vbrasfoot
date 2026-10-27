package br.vbrasfoot;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Owned Swing window only. Does not load the game's classes, start a career or grant write authority. */
public final class LoanManagerUiSmokeTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    private static void collect(Container parent, List<JButton> buttons) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button) buttons.add(button);
            if (component instanceof Container container) collect(container, buttons);
        }
    }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) { System.out.println("SKIP: loan Swing smoke requires display; not validated."); return; }
        Path root = Files.createTempDirectory(Files.createDirectories(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode")), "loan-ui-smoke-");
        var observer = new SaveFileObserver(); Object primary = new Object(), secondary = new Object();
        var coordinator = new CareerAssociationCoordinator(root.resolve("sav"), new CareerSaveAssociations(root.resolve("associations")),
                new CareerSidecarStore(root.resolve("journals")), new CareerSidecarStore(root.resolve("finance")),
                new CareerSidecarStore(root.resolve("executions")), new CareerSidecarStore(root.resolve("loans")), observer,
                new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2)), List.of(LoanManagerPanel.OWNER));
        var logger = Logger.getLogger("loan-ui-smoke"); logger.setUseParentHandlers(false);
        var runtime = new ClassLoader() { };
        var ui = new ModUi(runtime, logger);
        Set<Window> existing = new HashSet<>(Arrays.asList(Window.getWindows()));
        try {
            CareerBridge.onEdt(() -> {
                LoanManagerPanel.register(ui, coordinator, new CareerBridge(runtime), () -> { throw new AssertionError("No adapter should be created on open"); }, logger);
                ui.invoke("vbrasfoot:loans");
                var dialog = Arrays.stream(Window.getWindows()).filter(window -> !existing.contains(window) && window instanceof JDialog)
                        .map(window -> (JDialog) window).filter(window -> window.getTitle().startsWith("vbrasfoot - Pedidos")).findFirst().orElseThrow();
                check(dialog.isVisible(), "owned pending UI visible");
                var buttons = new ArrayList<JButton>(); collect(dialog, buttons);
                var transfer = buttons.stream().filter(button -> button.getText().startsWith("Transferir pedido")).findFirst().orElseThrow();
                check(!transfer.isEnabled(), "transfer disabled until exact approved request reviewed"); transfer.doClick(); // Disabled: no review, adapter or effect.
                var submit = buttons.stream().filter(button -> button.getText().startsWith("Registrar e avaliar")).findFirst().orElseThrow();
                submit.doClick(); // Missing selections: no adapter, metadata write, or game call permitted.
                var eligibility = buttons.stream().filter(button -> button.getText().startsWith("Consultar regra e taxa")).findFirst().orElseThrow();
                eligibility.doClick(); // No selections: must not construct adapter or invoke native code.
                check(buttons.stream().anyMatch(button -> button.getText().startsWith("Novo pedido")), "explicit new key action available without rewriting historical requests");
                check(!Files.exists(root.resolve("loans")) && !Files.exists(root.resolve("sav")), "opening/missing selection creates no metadata or saves");
                BufferedImage image = new BufferedImage(dialog.getWidth(), dialog.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); try { dialog.paintAll(graphics); } finally { graphics.dispose(); }
                ImageIO.write(image, "png", root.resolve("pending-manager.png").toFile());
                return null;
            });
        } finally { ui.close(); CareerBridge.onEdt(() -> null); }
        check(Arrays.stream(Window.getWindows()).filter(window -> !existing.contains(window)).noneMatch(Window::isDisplayable), "owned dialogs disposed");
        System.out.println("PASS: loan Swing open/missing identity/missing selection/no writes/cleanup; synthetic UI only, fixture " + root);
    }
}
