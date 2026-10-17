package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.awt.*;
import java.util.logging.Logger;
import javax.swing.*;

public final class EmbeddedPanelsSelfTest {
    public static void main(String[] args) {
        UiTheme theme = new UiTheme(new Font("Dialog", Font.PLAIN, 12), Color.BLACK, Color.WHITE, Color.DARK_GRAY, Color.WHITE);
        boolean refused = false; try { theme.apply(new JPanel()); } catch (IllegalStateException expected) { refused = true; }
        if (!refused) throw new AssertionError("theme requires EDT");
        refused = false; try { new EmbeddedPanels(Logger.getAnonymousLogger()).close(); } catch (IllegalStateException expected) { refused = true; }
        if (!refused) throw new AssertionError("host requires EDT");
        CareerBridge.onEdt(() -> {
            JPanel panel = new JPanel(); JButton button = new JButton("owned"); JLabel label = new JLabel("owned"); panel.add(button); panel.add(label);
            theme.apply(panel);
            if (!panel.getBackground().equals(Color.BLACK) || !button.getBackground().equals(Color.DARK_GRAY)
                    || !label.getForeground().equals(Color.WHITE) || !label.getFont().equals(theme.font())) throw new AssertionError("copied palette applied to mod-owned components");
            return null;
        });
        System.out.println("PASS: 3 embedded UI palette/thread checks (synthetic, no original widgets/assets).");
    }
}
