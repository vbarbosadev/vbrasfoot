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
        GameAccess access = () -> {
            reads.incrementAndGet();
            return new GameAccess.Snapshot(true, List.of(new GameAccess.Club(10, "Clube de teste", true,
                    List.of(new GameAccess.Player(20, "Jogador de teste", 22, 4, 50, 100000)))));
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
            if (reads.get() != 1) throw new AssertionError("Inspetor nao consultou API");
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
                return null;
            });
            System.out.println("PASS: janela db() correta, botao interno junto a Opcoes, nenhum menu externo, acoes sem duplicacao, inspetor somente de leitura.");
        } finally {
            CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; });
        }
    }
}
