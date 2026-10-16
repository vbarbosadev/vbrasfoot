package a;

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToolBar;

/** Synthetic fixture matching only the shipped panel's toolbar member names and types. */
public final class eg extends JPanel {
    private final JToolBar ve = new JToolBar();
    private final JButton HX = new JButton("Opcoes");
    public eg() {
        super(new BorderLayout());
        ve.add(new JButton("Agenda"));
        ve.add(HX);
        add(ve, BorderLayout.NORTH);
    }
    public JToolBar toolbar() { return ve; }
}
