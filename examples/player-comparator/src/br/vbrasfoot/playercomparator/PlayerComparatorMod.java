package br.vbrasfoot.playercomparator;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class PlayerComparatorMod implements Mod {
    private AutoCloseable registration;
    private AutoCloseable panelRegistration;
    private volatile boolean stopped;
    private JDialog window; // EDT only

    @Override public void initialize(ModContext context) {
        registration = context.registerScopedAction("compare", "Comparar dois jogadores", () -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(() -> open(context));
            } else open(context);
        });
        panelRegistration = context.registerPanel("compare-integrated", "Comparador dentro do jogo", theme -> {
            if (stopped) throw new IllegalStateException("Mod encerrado");
            return new ComparatorView(context.game());
        });
    }
    private void open(ModContext context) {
        if (stopped) return;
        if (window == null || !window.isDisplayable()) {
            ComparatorView view = new ComparatorView(context.game());
            window = new JDialog((Window) null, "Comparador de jogadores — leitura SDK");
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setContentPane(view);
            window.addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent event) { view.close(); } });
            window.pack(); window.setLocationRelativeTo(null);
        }
        window.setVisible(true); window.toFront();
    }
    @Override public void shutdown() throws Exception {
        stopped = true;
        try { if (registration != null) registration.close(); }
        finally {
            if (panelRegistration != null) { panelRegistration.close(); panelRegistration = null; }
            registration = null;
            Runnable dispose = () -> { if (window != null) { window.dispose(); window = null; } };
            if (SwingUtilities.isEventDispatchThread()) dispose.run();
            else SwingUtilities.invokeLater(dispose); // Never wait for EDT during disposal.
        }
    }

    private static DefaultTableModel table(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    private static final class ComparatorView extends JPanel implements br.vbrasfoot.api.ModPanel {
        private final ComparisonModel model;
        private final Browser[] browsers = new Browser[2];
        private final JLabel status = new JLabel("Carregue uma carreira e clique em Atualizar tudo.");
        private final DefaultTableModel comparison = table("Campo publico", "Jogador A", "Jogador B");
        ComparatorView(GameAccess game) {
            super(new BorderLayout());
            model = new ComparisonModel(game);
            JPanel top = new JPanel(new BorderLayout());
            JButton refresh = new JButton("Atualizar tudo (limpa selecoes)");
            refresh.setName("player-comparator.refresh");
            refresh.addActionListener(e -> {
                model.clear(); resetViews();
                runQuery(() -> { browsers[0].loadClubs(0); browsers[1].loadClubs(0); });
            });
            top.add(refresh, BorderLayout.NORTH);
            JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
            for (int i = 0; i < 2; i++) { browsers[i] = new Browser(i); pair.add(browsers[i]); }
            top.add(pair, BorderLayout.CENTER);
            JTable results = new JTable(comparison); results.setName("player-comparator.results");
            status.setName("player-comparator.status");
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(results));
            split.setResizeWeight(0.65);
            add(split, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new GridLayout(2, 1));
            bottom.add(new JLabel("Dados consultados, nao ranking oficial. Paginas nao sao snapshot atomico; sem salario/potencial."));
            bottom.add(status); add(bottom, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(1050, 650));
        }
        public JComponent component() { return this; }
        public void close() { model.clear(); resetViews(); }
        private void resetViews() {
            for (Browser browser : browsers) browser.clear();
            comparison.setRowCount(0);
        }
        private void runQuery(Runnable query) {
            try { query.run(); render(); }
            catch (GameQueryException failure) {
                model.clear(); resetViews();
                status.setText(failure.reason() + " — consulta invalidada. Clique em Atualizar tudo.");
            } catch (RuntimeException failure) {
                model.clear(); resetViews();
                status.setText("Consulta indisponivel (" + failure.getClass().getSimpleName() + "). Clique em Atualizar tudo.");
            }
        }
        private void render() {
            comparison.setRowCount(0);
            for (var row : model.comparison()) comparison.addRow(new Object[] {row.label(), row.left(), row.right()});
        }
        private final class Browser extends JPanel {
            private final int side;
            private final JTextField filter = new JTextField(12);
            private final JComboBox<String> clubs = new JComboBox<>();
            private final DefaultTableModel players = table("Nome", "Idade (anos)", "Posicao (codigo)", "Forca", "Value");
            private final JTable playerTable = new JTable(players);
            private final JButton clubPrev = new JButton("Clubes <"), clubNext = new JButton("Clubes >");
            private final JButton playerPrev = new JButton("Jogadores <"), playerNext = new JButton("Jogadores >");
            private final JLabel pageInfo = new JLabel("Sem consulta");
            private final JLabel chosen = new JLabel("Nenhum jogador escolhido");
            private GameAccess.ScopedClubPage clubPage;
            private GameAccess.ClubView club;
            private int playerOffset;
            private boolean updating;
            Browser(int side) {
                super(new BorderLayout(4, 4)); this.side = side;
                String prefix = "player-comparator." + side + ".";
                clubs.setName(prefix + "clubs"); playerTable.setName(prefix + "players");
                setBorder(BorderFactory.createTitledBorder(side == 0 ? "Jogador A" : "Jogador B"));
                JPanel controls = new JPanel();
                JButton search = new JButton("Filtrar");
                controls.add(filter); controls.add(search); controls.add(clubPrev); controls.add(clubNext);
                JPanel north = new JPanel(new BorderLayout()); north.add(controls, BorderLayout.NORTH); north.add(clubs);
                add(north, BorderLayout.NORTH); add(new JScrollPane(playerTable));
                playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                playerTable.setAutoCreateRowSorter(true);
                JPanel south = new JPanel(new GridLayout(3, 1)); JPanel actions = new JPanel();
                JButton choose = new JButton("Escolher " + (side == 0 ? "A" : "B"));
                choose.setName(prefix + "choose");
                actions.add(playerPrev); actions.add(playerNext); actions.add(choose);
                south.add(actions); south.add(pageInfo); south.add(chosen); add(south, BorderLayout.SOUTH);
                search.addActionListener(e -> runQuery(() -> loadClubs(0)));
                filter.addActionListener(e -> search.doClick());
                clubPrev.addActionListener(e -> runQuery(() -> loadClubs(Math.max(0, clubPage.offset() - 100))));
                clubNext.addActionListener(e -> runQuery(() -> loadClubs(clubPage.offset() + 100)));
                clubs.addActionListener(e -> {
                    if (!updating && clubs.getSelectedIndex() >= 0) runQuery(() -> {
                        club = clubPage.items().get(clubs.getSelectedIndex()); loadPlayers(0);
                    });
                });
                playerPrev.addActionListener(e -> runQuery(() -> loadPlayers(Math.max(0, playerOffset - 100))));
                playerNext.addActionListener(e -> runQuery(() -> loadPlayers(playerOffset + 100)));
                choose.addActionListener(e -> {
                    int row = playerTable.getSelectedRow();
                    if (row < 0) { status.setText("Selecione uma linha de jogador."); return; }
                    model.select(side, playerTable.convertRowIndexToModel(row));
                    chosen.setText(model.selection(side).player().name() + " — " + model.selection(side).club());
                    status.setText("Selecoes mantidas apenas em memoria; atualize para reler os dados."); render();
                });
                clear();
            }
            void clear() {
                updating = true; clubs.removeAllItems(); updating = false;
                clubPage = null; club = null; playerOffset = 0; players.setRowCount(0);
                clubPrev.setEnabled(false); clubNext.setEnabled(false);
                playerPrev.setEnabled(false); playerNext.setEnabled(false);
                pageInfo.setText("Sem consulta"); chosen.setText("Nenhum jogador escolhido");
            }
            void loadClubs(int offset) {
                var page = model.clubs(side, filter.getText(), offset);
                updating = true;
                try {
                    clubs.removeAllItems(); clubPage = page; club = null; players.setRowCount(0);
                    playerPrev.setEnabled(false); playerNext.setEnabled(false);
                    for (var view : page.items()) clubs.addItem(view.club().name());
                    clubs.setSelectedIndex(-1);
                } finally { updating = false; }
                clubPrev.setEnabled(page.loaded() && page.offset() > 0);
                clubNext.setEnabled(page.loaded() && page.offset() + page.items().size() < page.total());
                pageInfo.setText("Clubes: " + page.items().size() + " de " + page.total() + "; offset " + page.offset());
                status.setText(page.loaded() ? "Escolha clube, linha e botao Escolher em cada lado." : "Nenhuma carreira ativa. Carregue e clique em Atualizar tudo.");
                if (!page.loaded()) { resetViews(); }
            }
            void loadPlayers(int offset) {
                var page = model.players(side, club, offset); playerOffset = page.offset(); players.setRowCount(0);
                for (var p : page.items()) players.addRow(new Object[] {p.name(), p.age(), p.position(), p.strength(), p.value()});
                playerPrev.setEnabled(page.offset() > 0);
                playerNext.setEnabled(page.offset() + page.items().size() < page.total());
                pageInfo.setText("Jogadores: " + page.items().size() + " de " + page.total() + "; offset " + page.offset());
            }
        }
    }
}
