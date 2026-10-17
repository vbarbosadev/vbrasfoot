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
            return new ComparatorView(context.game(), theme);
        });
    }
    private void open(ModContext context) {
        if (stopped) return;
        if (window == null || !window.isDisplayable()) {
            ComparatorView view = new ComparatorView(context.game(), new br.vbrasfoot.api.UiTheme(
                    UIManager.getFont("Label.font"), java.awt.Color.WHITE, java.awt.Color.BLACK, new java.awt.Color(36, 91, 45), java.awt.Color.WHITE));
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
        private final br.vbrasfoot.api.UiTheme theme;
        private final ComparisonModel model;
        private final Browser[] browsers = new Browser[2];
        private final JLabel status = new JLabel("Carregue uma carreira e clique em Atualizar tudo.");
        private final DefaultTableModel comparison = table("Campo publico", "Jogador A", "Jogador B");
        ComparatorView(GameAccess game, br.vbrasfoot.api.UiTheme theme) {
            super(new BorderLayout());
            this.theme = theme;
            theme.style(this, br.vbrasfoot.api.UiTheme.Role.BODY);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
            model = new ComparisonModel(game);
            JPanel top = theme.style(new JPanel(new BorderLayout(0, 10)), br.vbrasfoot.api.UiTheme.Role.BODY);
            JButton refresh = theme.style(new JButton("ATUALIZAR CONSULTAS"), br.vbrasfoot.api.UiTheme.Role.PRIMARY_BUTTON);
            refresh.setName("player-comparator.refresh");
            refresh.addActionListener(e -> {
                model.clear(); resetViews();
                runQuery(() -> { browsers[0].loadClubs(0); browsers[1].loadClubs(0); });
            });
            JPanel commands = theme.style(new JPanel(new BorderLayout(12, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
            commands.add(new JLabel("ELENCO  /  COMPARACAO DE JOGADORES"), BorderLayout.CENTER);
            commands.add(refresh, BorderLayout.EAST); top.add(commands, BorderLayout.NORTH);
            JPanel pair = theme.style(new JPanel(new GridLayout(1, 2, 14, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
            for (int i = 0; i < 2; i++) { browsers[i] = new Browser(i); pair.add(browsers[i]); }
            top.add(pair, BorderLayout.CENTER);
            JTable results = theme.style(new JTable(comparison), br.vbrasfoot.api.UiTheme.Role.TABLE); results.setName("player-comparator.results");
            status.setName("player-comparator.status");
            JPanel resultArea = theme.style(new JPanel(new BorderLayout(0, 6)), br.vbrasfoot.api.UiTheme.Role.BODY);
            JLabel resultTitle = theme.style(new JLabel("COMPARACAO LADO A LADO"), br.vbrasfoot.api.UiTheme.Role.SECTION); resultTitle.setOpaque(true);
            resultArea.add(resultTitle, BorderLayout.NORTH); resultArea.add(new JScrollPane(results));
            top.setMinimumSize(new Dimension(0, 285)); resultArea.setMinimumSize(new Dimension(0, 200));
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, resultArea);
            split.setBorder(BorderFactory.createEmptyBorder()); split.setDividerSize(8); split.setContinuousLayout(true);
            split.setResizeWeight(0.60);
            add(split, BorderLayout.CENTER);
            JPanel bottom = theme.style(new JPanel(new GridLayout(2, 1)), br.vbrasfoot.api.UiTheme.Role.HEADER);
            JLabel hint = theme.style(new JLabel("Leitura SDK • sem ranking oficial, salario ou potencial inferidos"), br.vbrasfoot.api.UiTheme.Role.STATUS);
            bottom.add(hint); theme.style(status, br.vbrasfoot.api.UiTheme.Role.STATUS);
            bottom.add(status); add(bottom, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(1050, 650));
            theme.apply(this);
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
                theme.style(this, br.vbrasfoot.api.UiTheme.Role.BODY);
                String prefix = "player-comparator." + side + ".";
                clubs.setName(prefix + "clubs"); playerTable.setName(prefix + "players");
                setBorder(BorderFactory.createLineBorder(new java.awt.Color(215, 220, 216)));
                JPanel controls = theme.style(new JPanel(new BorderLayout(6, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
                JButton search = theme.style(new JButton("FILTRAR"), br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                theme.style(filter, br.vbrasfoot.api.UiTheme.Role.INPUT); theme.style(clubs, br.vbrasfoot.api.UiTheme.Role.INPUT);
                filter.setToolTipText("Nome do clube; Enter para filtrar");
                controls.add(filter, BorderLayout.CENTER); controls.add(search, BorderLayout.EAST);
                JPanel north = theme.style(new JPanel(new GridLayout(0, 1, 0, 5)), br.vbrasfoot.api.UiTheme.Role.BODY);
                JLabel section = theme.style(new JLabel(side == 0 ? "JOGADOR A" : "JOGADOR B"), br.vbrasfoot.api.UiTheme.Role.SECTION); section.setOpaque(true);
                north.add(section); north.add(controls);
                JPanel clubNav = theme.style(new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 5, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
                theme.style(clubPrev, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON); theme.style(clubNext, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                clubNav.add(clubPrev); clubNav.add(clubNext);
                JPanel clubRow = theme.style(new JPanel(new BorderLayout(5, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
                clubRow.add(clubs, BorderLayout.CENTER); clubRow.add(clubNav, BorderLayout.EAST); north.add(clubRow);
                add(north, BorderLayout.NORTH); add(new JScrollPane(playerTable));
                playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                playerTable.setAutoCreateRowSorter(true);
                theme.style(playerTable, br.vbrasfoot.api.UiTheme.Role.TABLE);
                int[] widths = {220, 70, 110, 80, 110};
                for (int column = 0; column < widths.length; column++) playerTable.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
                var numbers = new javax.swing.table.DefaultTableCellRenderer(); numbers.setHorizontalAlignment(SwingConstants.CENTER);
                for (int column = 1; column <= 3; column++) playerTable.getColumnModel().getColumn(column).setCellRenderer(numbers);
                var values = new javax.swing.table.DefaultTableCellRenderer(); values.setHorizontalAlignment(SwingConstants.RIGHT);
                playerTable.getColumnModel().getColumn(4).setCellRenderer(values);
                JPanel south = theme.style(new JPanel(), br.vbrasfoot.api.UiTheme.Role.BODY);
                south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
                JPanel actions = theme.style(new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 5, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
                actions.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); pageInfo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); chosen.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                theme.style(playerPrev, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON); theme.style(playerNext, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                JButton choose = theme.style(new JButton("ESCOLHER " + (side == 0 ? "A" : "B")), br.vbrasfoot.api.UiTheme.Role.PRIMARY_BUTTON);
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
