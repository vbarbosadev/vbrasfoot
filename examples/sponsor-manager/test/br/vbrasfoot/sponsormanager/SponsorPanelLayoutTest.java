package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.CareerDataAccess;
import br.vbrasfoot.api.ModContext;
import br.vbrasfoot.api.UiTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

public final class SponsorPanelLayoutTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Context implements ModContext {
        public GameAccess game() { return () -> new GameAccess.Snapshot(false, List.of()); }
        public Logger logger() { return Logger.getLogger("sponsor-panel-test"); }
        public br.vbrasfoot.api.SponsorAccess sponsors() {
            return new br.vbrasfoot.api.SponsorAccess() {
                public CareerDataAccess.Scope bindCareer() { throw new AssertionError("No real binding in layout test"); }
                public Quote quote(CareerDataAccess.Scope scope, java.util.UUID id, int clubId, String club, String name, int credit, String stadium) {
                    throw new AssertionError("No real quote in layout test");
                }
                public Result activate(Quote quote) { throw new AssertionError("No real effects in layout test"); }
                public State state(CareerDataAccess.Scope scope, java.util.UUID id) { return State.NOT_ACTIVATED; }
            };
        }
        public CareerDataAccess careerData() {
            return new CareerDataAccess() {
                public java.util.Optional<Scope> current() { return java.util.Optional.empty(); }
                public byte[] read(Scope scope, String file) { return new byte[0]; }
                public void write(Scope scope, String file, byte[] bytes) { throw new AssertionError("Panel smoke test must not persist"); }
            };
        }
        public void registerAction(String id, String label, Runnable action) { }
        public byte[] readData(String file) { return new byte[0]; }
        public void writeData(String file, byte[] bytes) { throw new AssertionError("Panel smoke test must not persist"); }
    }
    private static JComponent find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent widget && name.equals(widget.getName())) return widget;
            if (child instanceof Container nested) { var match = find(nested, name); if (match != null) return match; }
        }
        return null;
    }
    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var theme = new UiTheme(new Font(Font.DIALOG, Font.PLAIN, 12), Color.RED, Color.WHITE, Color.RED, Color.WHITE);
            var context = new Context();
            var panel = new SponsorPanel(context, theme, new SponsorBanner(context.careerData()));
            try {
                var root = (JPanel) panel.component(); root.setSize(900, 650); layout(root);
                var tabs = (JTabbedPane) find(root, "sponsor.tabs");
                var form = (JPanel) find(root, "sponsor.form"); var fields = (JPanel) find(root, "sponsor.form.fields");
                var add = (JButton) find(root, "sponsor.form.add");
                var feature = (JButton) find(root, "sponsor.feature");
                var legacy = (JButton) find(root, "sponsor.legacy.import");
                var activate = (JButton) find(root, "sponsor.activate");
                check(find(root, "sponsor.end") instanceof JButton, "explicit ending action available");
                check(find(root, "sponsor.bind") != null && find(root, "sponsor.stadium") != null
                        && find(root, "sponsor.logo.choose") != null, "inline career binding, stadium name and local logo picker are available");
                check(tabs != null && tabs.getTabCount() == 2 && tabs.getTitleAt(0).equals("Novo patrocinador"), "creation form is the first visible tab");
                check(find(root, "sponsor.message") instanceof javax.swing.JTextArea text && text.getHeight() > 30,
                        "editable native-central message field is present and not clipped");
                check(form != null && form.getWidth() > 700 && form.getHeight() > 300, "form receives usable tab area");
                check(fields != null && fields.getHeight() > 200 && add != null && add.getHeight() > 0, "input fields and save button are not clipped");
                check(root.getBackground().getRed() < 100, "game's red surface color does not leak into the mod panel");
                tabs.setSelectedIndex(1); layout(root);
                var table = (JTable) find(root, "sponsor.configurations.table");
                var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                check(table != null && scroll != null && scroll.getViewport().getHeight() > 200, "configuration table remains usable in its own tab");
                check(feature != null && feature.getText().contains("topo"), "explicit read-only logo feature action is present");
                check(legacy != null && legacy.getText().contains("Importar"), "legacy configuration import is explicit and non-activating");
                check(activate != null && !activate.isEnabled(), "financial activation needs selected contract and career");
                for (Component child : fields.getComponents()) if (child instanceof javax.swing.JTextField input)
                    check(input.getHeight() <= 36, "form text fields remain compact rather than stretching vertically");
            } finally { panel.close(); }
        });
        System.out.println("PASS: " + checks + " sponsor panel layout/theme checks; data writes disabled");
    }
}
