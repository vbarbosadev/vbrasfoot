package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JPopupMenu;

/** Tests the real example JAR and menu bridge with a synthetic window, not gameplay. */
public final class UiSmokeTest {
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toRealPath();
        AtomicInteger reads = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean stale = new java.util.concurrent.atomic.AtomicBoolean();
        GameAccess legacyFixture = () -> {
            reads.incrementAndGet();
            return new GameAccess.Snapshot(true, List.of(new GameAccess.Club(10, "Clube de teste", true,
                    List.of(new GameAccess.Player(20, "Jogador de teste", 22, 4, 50, 100000)))));
        };
        var scope = new GameAccess.CareerScope("ui-fixture", 1);
        var reference = new GameAccess.ClubRef(scope, "club-10");
        GameAccess access = new GameAccess() {
            public Snapshot snapshot() { return legacyFixture.snapshot(); }
            public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                var page = legacyFixture.clubs(filter, offset, limit);
                return new ScopedClubPage(page.loaded(), scope, page.items().stream().map(c -> new ClubView(reference, c)).toList(), offset, page.total());
            }
            public ScopedPlayerPage scopedPlayers(ClubRef club, int offset, int limit) {
                if (stale.get()) throw new br.vbrasfoot.api.GameQueryException(br.vbrasfoot.api.GameQueryException.Reason.STALE_SCOPE, "fixture stale scope");
                if (!reference.equals(club)) throw new AssertionError("Wrong club scope");
                var page = legacyFixture.playersAt(0, offset, limit);
                return new ScopedPlayerPage(scope, page.items(), offset, page.total());
            }
        };
        Logger logger = Logger.getLogger("vbrasfoot.test");
        try (LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home);
             ModUi ui = new ModUi(runtime, logger); ModLoader mods = new ModLoader(logger)) {
            JFrame frame = CareerBridge.onEdt(() -> {
                JFrame result = new JFrame("vbrasfoot — teste de integracao Swing");
                JMenuBar bar = new JMenuBar();
                bar.add(new JMenu("Menu original"));
                result.setJMenuBar(bar);
                result.setContentPane(new a.eg());
                result.pack(); // Allocate native peer without displaying the synthetic main window.
                return result;
            });
            JFrame other = CareerBridge.onEdt(() -> {
                JFrame result = new JFrame("Outra janela — nao e a carreira");
                result.pack();
                Class.forName("best.n", true, runtime).getMethod("b", JFrame.class).invoke(null, result);
                return result;
            });
            mods.load(ModLoader.discover(home.resolve("mods")), access, ui, home.resolve("build/test-data"));
            ui.start();
            Thread.sleep(1200);
            CareerBridge.onEdt(() -> {
                if (other.getJMenuBar() != null) throw new AssertionError("Menu instalado em dc(), a janela errada");
                if (frame.getJMenuBar().getMenuCount() != 1) throw new AssertionError("Menu instalado antes de registrar a carreira");
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, frame);
                return null;
            });
            long deadline = System.nanoTime() + 8_000_000_000L;
            while (!CareerBridge.onEdt(() -> ((a.eg) frame.getContentPane()).toolbar().getComponentCount() == 3)) {
                if (System.nanoTime() > deadline) throw new AssertionError("Menu nao instalado");
                Thread.sleep(100);
            }
            CareerBridge.onEdt(() -> {
                if (!frame.getJMenuBar().getMenu(0).getText().equals("Menu original")) throw new AssertionError("Menu original substituido");
                if (frame.getJMenuBar().getMenuCount() != 1) throw new AssertionError("Menu externo criado");
                var toolbar = ((a.eg) frame.getContentPane()).toolbar();
                JButton button = (JButton) toolbar.getComponent(1);
                if (!button.getText().equals("vbrasfoot") || !((JButton) toolbar.getComponent(2)).getText().equals("Opcoes")) {
                    throw new AssertionError("Botao nao esta junto a Opcoes");
                }
                JPopupMenu menu = (JPopupMenu) button.getClientProperty("vbrasfoot.popup");
                if (menu.getComponentCount() != 2) throw new AssertionError("Acoes do mod ausentes");
                ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();
                return null;
            });
            if (reads.get() != 2) throw new AssertionError("Inspetor deve consultar pagina de clubes e jogadores");
            ui.register("test:scoped", "Temporary action", () -> { });
            Thread.sleep(1000);
            CareerBridge.onEdt(() -> {
                var toolbar = ((a.eg) frame.getContentPane()).toolbar();
                var popup = (JPopupMenu) ((JButton) toolbar.getComponent(1)).getClientProperty("vbrasfoot.popup");
                if (popup.getComponentCount() != 3) throw new AssertionError("Scoped action missing");
                return null;
            });
            ui.unregister("test:scoped");
            Thread.sleep(1000); // Exercise a second installation pass to catch duplicate menus/actions.
            CareerBridge.onEdt(() -> {
                var toolbar = ((a.eg) frame.getContentPane()).toolbar();
                var menu = (JPopupMenu) ((JButton) toolbar.getComponent(1)).getClientProperty("vbrasfoot.popup");
                if (frame.getJMenuBar().getMenuCount() != 1 || toolbar.getComponentCount() != 3 || menu.getComponentCount() != 2) {
                    throw new AssertionError("Menu ou acao duplicados");
                }
                boolean found = false;
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog dialog && dialog.getTitle().contains("Inspetor")) {
                        var panel = (javax.swing.JPanel) dialog.getContentPane();
                        for (var component : panel.getComponents()) {
                            if (component instanceof javax.swing.JScrollPane scroll && scroll.getViewport().getView() instanceof JTable table) {
                                if (table.getRowCount() != 1 || !table.getValueAt(0, 1).equals("Jogador de teste")) {
                                    throw new AssertionError("Tabela do inspetor incorreta");
                                }
                                if (table.isCellEditable(0, 1)) throw new AssertionError("Tabela editavel");
                                found = true;
                            }
                        }
                    }
                }
                if (!found) throw new AssertionError("Inspetor nao abriu tabela");
                for (Window window : Window.getWindows()) {
                    if (!(window instanceof JDialog dialog) || !dialog.getTitle().contains("Inspetor")) continue;
                    var combo = find(dialog.getContentPane(), javax.swing.JComboBox.class);
                    var table = find(dialog.getContentPane(), JTable.class);
                    stale.set(true);
                    combo.setSelectedIndex(-1);
                    combo.setSelectedIndex(0);
                    if (table.getRowCount() != 0) throw new AssertionError("Inspetor manteve dados apos referencia obsoleta");
                    boolean message = false;
                    for (var component : dialog.getContentPane().getComponents()) {
                        if (component instanceof javax.swing.JLabel label && label.getText().contains("STALE_SCOPE")) message = true;
                    }
                    if (!message) throw new AssertionError("Inspetor nao pediu Atualizar apos scope obsoleto");
                    stale.set(false);
                    combo.setSelectedIndex(-1);
                    combo.setSelectedIndex(0);
                    if (table.getRowCount() != 1) throw new AssertionError("Consulta nao retomou apos referencia valida");
                }
                return null;
            });
            Path managerHome = java.nio.file.Files.createTempDirectory(Path.of("build"), "manager-ui-").toAbsolutePath();
            java.nio.file.Files.createDirectories(managerHome.resolve("mods"));
            java.nio.file.Files.copy(home.resolve("mods/inspector.jar"), managerHome.resolve("mods/inspector.jar"));
            var fixtureFailures = new ModFailures();
            fixtureFailures.record("inspector.jar", "br.vbrasfoot.inspector", ModFailures.Stage.UI_ACTION, new IllegalStateException("fixture-history-only"));
            ModManagerPanel.register(ui, managerHome, managerHome.resolve("logs"), mods::fileStates, fixtureFailures::snapshot);
            Thread.sleep(1000);
            CareerBridge.onEdt(() -> {
                var popup = (JPopupMenu) ((JButton) ((a.eg) frame.getContentPane()).toolbar().getComponent(1)).getClientProperty("vbrasfoot.popup");
                if (popup.getComponentCount() != 3) throw new AssertionError("Gerenciador nao registrado");
                ((javax.swing.JMenuItem) popup.getComponent(2)).doClick(); return null;
            });
            long managerDeadline = System.nanoTime() + 8_000_000_000L;
            boolean managerReady = false;
            while (!managerReady) {
                managerReady = CareerBridge.onEdt(() -> {
                    for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.getTitle().contains("Gerenciar mods")) {
                        var table = find(dialog.getContentPane(), JTable.class);
                        if (table == null || table.getRowCount() == 0) return false;
                        if (!table.getValueAt(0, 3).equals("ACTIVE") || !table.getValueAt(0, 4).equals("HABILITADO")) throw new AssertionError("Estado/seleção gerenciador incorretos");
                        if (!table.getValueAt(0, 7).equals(1) || !table.getValueAt(0, 8).toString().contains("fixture-history-only")) throw new AssertionError("Resumo de falha ausente");
                        var tabs = find(dialog.getContentPane(), javax.swing.JTabbedPane.class);
                        if (tabs == null || tabs.getTabCount() != 2 || !tabs.getTitleAt(1).equals("Falhas da sessao")) throw new AssertionError("Aba de falhas ausente");
                        var history = (javax.swing.JTextArea) ((javax.swing.JScrollPane) tabs.getComponentAt(1)).getViewport().getView();
                        if (history.isEditable() || !history.getText().contains("fixture-history-only")) throw new AssertionError("Historico incorreto/editavel");
                        return true;
                    }
                    return false;
                });
                if (System.nanoTime() > managerDeadline) throw new AssertionError("Painel de mods nao carregou");
                if (!managerReady) Thread.sleep(50);
            }
            if (java.nio.file.Files.exists(managerHome.resolve("mods-selection.properties"))) throw new AssertionError("Abrir painel alterou selecao");
            ui.close();
            CareerBridge.onEdt(() -> {
                for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.getTitle().contains("Gerenciar mods") && dialog.isDisplayable()) throw new AssertionError("Painel vazou apos fechar UI");
                return null;
            });
            System.out.println("PASS: menu/inspetor scoped, gerenciador, resumo/aba de falhas read-only; abrir nao grava, cleanup sem janela vazada (UI sintetica).");
        } finally {
            CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; });
        }
    }
    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (var component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof java.awt.Container child) {
                T value = find(child, type); if (value != null) return value;
            }
        }
        return null;
    }
}
