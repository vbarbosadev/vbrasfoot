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
            var ownColor = new Color(31, 41, 51); var ownFont = new Font("Dialog", Font.BOLD, 17);
            label.setForeground(ownColor); label.setFont(ownFont); theme.apply(panel);
            if (!label.getForeground().equals(ownColor) || !label.getFont().equals(ownFont)) throw new AssertionError("explicit mod styling overwritten");
            JTable table = theme.style(new JTable(1, 2), UiTheme.Role.TABLE);
            JPanel body = theme.style(new JPanel(), UiTheme.Role.BODY); body.add(table);
            JLabel section = theme.style(new JLabel("SECTION"), UiTheme.Role.SECTION); body.add(section);
            theme.apply(body); theme.apply(body);
            if (!table.getBackground().equals(Color.WHITE) || table.getSelectionBackground().equals(table.getBackground())
                    || !table.getSelectionForeground().equals(new Color(0, 35, 65)) || table.getRowHeight() < 20
                    || !section.getFont().isBold() || section.getForeground().equals(section.getBackground())) throw new AssertionError("semantic roles/selection lost on theme application");
            return null;
        });
        System.out.println("PASS: 5 embedded UI palette/roles/explicit-style/thread checks (synthetic, no original widgets/assets).");
    }
}
