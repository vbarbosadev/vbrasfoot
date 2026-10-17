package br.vbrasfoot;

import java.awt.*;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Explicit user recovery only; never changes game data or automatically fights legacy layout. */
final class WindowRecovery {
    static Rectangle usable(Rectangle screen, Insets insets) {
        long width = (long) screen.width - insets.left - insets.right;
        long height = (long) screen.height - insets.top - insets.bottom;
        if (screen.width < 1 || screen.height < 1 || insets.left < 0 || insets.right < 0
                || insets.top < 0 || insets.bottom < 0 || width < 1 || height < 1) {
            throw new IllegalArgumentException("Area util de monitor invalida");
        }
        return new Rectangle(Math.addExact(screen.x, insets.left), Math.addExact(screen.y, insets.top),
                Math.toIntExact(width), Math.toIntExact(height));
    }
    static void restore(JFrame frame, Logger logger) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Recuperacao exige EDT");
        if (!frame.isDisplayable()) throw new IllegalStateException("Janela da carreira indisponivel");
        GraphicsConfiguration config = frame.getGraphicsConfiguration();
        Rectangle area = usable(config.getBounds(), Toolkit.getDefaultToolkit().getScreenInsets(config));
        logger.info("window-recovery requested bounds=" + frame.getBounds() + " state=" + frame.getExtendedState() + " target=" + area);
        frame.setExtendedState(Frame.NORMAL);
        frame.setBounds(area);
        frame.validate();
        frame.repaint();
        logger.info("window-recovery applied bounds=" + frame.getBounds() + " state=" + frame.getExtendedState());
    }
}
