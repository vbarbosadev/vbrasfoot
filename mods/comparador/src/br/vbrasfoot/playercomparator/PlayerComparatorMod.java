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
        private final JLabel status = new JLabel("Carregue uma carreira e clique em Atualizar.");
        private final DefaultTableModel comparison = table("Campo publico", "Jogador A", "Jogador B");
        ComparatorView(GameAccess game, br.vbrasfoot.api.UiTheme theme) {
            super(new BorderLayout());
            this.theme = theme;
            theme.style(this, br.vbrasfoot.api.UiTheme.Role.BODY);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            model = new ComparisonModel(game);
            JPanel top = theme.style(new JPanel(new BorderLayout(0, 8)), br.vbrasfoot.api.UiTheme.Role.BODY);
            JButton refresh = theme.style(new JButton("Atualizar"), br.vbrasfoot.api.UiTheme.Role.PRIMARY_BUTTON);
            refresh.setToolTipText("Reler clubes e limpar as selecoes dos dois jogadores");
            refresh.setName("player-comparator.refresh");
            refresh.addActionListener(e -> {
                model.clear(); resetViews();
                runQuery(() -> { browsers[0].loadClubs(0); browsers[1].loadClubs(0); });
            });
            JPanel commands = theme.style(new JPanel(new BorderLayout(12, 0)), br.vbrasfoot.api.UiTheme.Role.BODY);
            commands.add(refresh, BorderLayout.WEST);
            JPanel pair = theme.style(new JPanel(new GridLayout(1, 2, 18, 0)), br.vbrasfoot.api.UiTheme.Role.HEADER);
            for (int i = 0; i < 2; i++) { browsers[i] = new Browser(i); pair.add(browsers[i]); }
            top.add(pair, BorderLayout.CENTER); top.add(commands, BorderLayout.SOUTH);
            JTable results = theme.style(new JTable(comparison), br.vbrasfoot.api.UiTheme.Role.TABLE); results.setName("player-comparator.results");
            status.setName("player-comparator.status");
            JPanel resultArea = theme.style(new JPanel(new BorderLayout(0, 6)), br.vbrasfoot.api.UiTheme.Role.BODY);
            resultArea.add(new JScrollPane(results));
            top.setPreferredSize(new Dimension(0, 310));
            JPanel sections = theme.style(new JPanel(new BorderLayout(0, 10)), br.vbrasfoot.api.UiTheme.Role.BODY);
            sections.add(top, BorderLayout.NORTH); sections.add(resultArea, BorderLayout.CENTER); add(sections, BorderLayout.CENTER);
            JPanel bottom = theme.style(new JPanel(new GridLayout(2, 1)), br.vbrasfoot.api.UiTheme.Role.HEADER);
            JLabel hint = theme.style(new JLabel("Leitura SDK • sem ranking oficial, salario ou potencial inferidos"), br.vbrasfoot.api.UiTheme.Role.STATUS);
            bottom.add(hint); theme.style(status, br.vbrasfoot.api.UiTheme.Role.STATUS);
            bottom.add(status); add(bottom, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(900, 590));
            theme.apply(this);
        }
        public JComponent component() { return this; }
        private JPanel darkPanel(java.awt.LayoutManager layout) {
            JPanel panel = new JPanel(layout);
            panel.setBackground(new java.awt.Color(0, 48, 65)); panel.setForeground(java.awt.Color.WHITE);
            return panel;
        }
        public void close() { model.clear(); resetViews(); }
        private void resetViews() {
            for (Browser browser : browsers) browser.clear();
            comparison.setRowCount(0);
        }
        private void runQuery(Runnable query) {
            try { query.run(); render(); }
            catch (GameQueryException failure) {
                model.clear(); resetViews();
                status.setText(failure.reason() + " — consulta invalidada. Clique em Atualizar.");
            } catch (RuntimeException failure) {
                model.clear(); resetViews();
                status.setText("Consulta indisponivel (" + failure.getClass().getSimpleName() + "). Clique em Atualizar.");
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
            private final DefaultTableModel players = table("Nome", "Idade", "Pos.", "Forca", "Valor SDK");
            private final JTable playerTable = new JTable(players);
            private final JButton clubPrev = new JButton("<"), clubNext = new JButton(">");
            private final JButton playerPrev = new JButton("<"), playerNext = new JButton(">");
            private final JLabel pageInfo = new JLabel("Sem consulta");
            private final JLabel chosen = new JLabel("Nenhum jogador escolhido");
            private GameAccess.ScopedClubPage clubPage;
            private GameAccess.ClubView club;
            private int playerOffset;
            private boolean updating;
            Browser(int side) {
                super(new BorderLayout(4, 4)); this.side = side;
                setBackground(new java.awt.Color(0, 48, 65)); setForeground(java.awt.Color.WHITE);
                String prefix = "player-comparator." + side + ".";
                clubs.setName(prefix + "clubs"); playerTable.setName(prefix + "players");
                setBorder(BorderFactory.createEmptyBorder());
                JPanel controls = darkPanel(new BorderLayout(6, 0));
                controls.setBorder(BorderFactory.createEmptyBorder());
                JButton search = theme.style(new JButton("Filtrar"), br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                theme.style(filter, br.vbrasfoot.api.UiTheme.Role.INPUT); theme.style(clubs, br.vbrasfoot.api.UiTheme.Role.INPUT);
                filter.setToolTipText("Nome do clube; Enter para filtrar");
                controls.add(filter, BorderLayout.CENTER); controls.add(search, BorderLayout.EAST);
                JPanel north = darkPanel(new GridLayout(0, 1, 0, 4));
                north.setBorder(BorderFactory.createEmptyBorder());
                JLabel section = new JLabel(side == 0 ? "Jogador A — nome do clube:" : "Jogador B — nome do clube:");
                north.add(section); north.add(controls);
                JPanel clubNav = darkPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 3, 0));
                clubNav.setBorder(BorderFactory.createEmptyBorder());
                theme.style(clubPrev, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON); theme.style(clubNext, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                clubNav.add(clubPrev); clubNav.add(clubNext);
                JPanel clubRow = darkPanel(new BorderLayout(5, 0));
                clubRow.setBorder(BorderFactory.createEmptyBorder());
                clubRow.add(clubs, BorderLayout.CENTER); clubRow.add(clubNav, BorderLayout.EAST); north.add(clubRow);
                add(north, BorderLayout.NORTH); add(new JScrollPane(playerTable));
                playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                playerTable.setAutoCreateRowSorter(true);
                theme.style(playerTable, br.vbrasfoot.api.UiTheme.Role.TABLE);
                int[] widths = {150, 45, 40, 45, 95};
                for (int column = 0; column < widths.length; column++) playerTable.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
                int[] minimums = {85, 35, 35, 40, 70};
                for (int column = 0; column < minimums.length; column++) playerTable.getColumnModel().getColumn(column).setMinWidth(minimums[column]);
                var numbers = new javax.swing.table.DefaultTableCellRenderer(); numbers.setHorizontalAlignment(SwingConstants.CENTER);
                for (int column = 1; column <= 3; column++) playerTable.getColumnModel().getColumn(column).setCellRenderer(numbers);
                var values = new javax.swing.table.DefaultTableCellRenderer(); values.setHorizontalAlignment(SwingConstants.RIGHT);
                playerTable.getColumnModel().getColumn(4).setCellRenderer(values);
                playerTable.getTableHeader().setToolTipText("Idade em anos; Pos. e codigo SDK; Valor SDK tem unidade nao documentada");
                clubPrev.setToolTipText("Pagina anterior de clubes"); clubNext.setToolTipText("Proxima pagina de clubes");
                playerPrev.setToolTipText("Pagina anterior de jogadores"); playerNext.setToolTipText("Proxima pagina de jogadores");
                JPanel south = darkPanel(new java.awt.FlowLayout());
                south.setBorder(BorderFactory.createEmptyBorder());
                south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
                JPanel actions = darkPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 3, 0));
                actions.setBorder(BorderFactory.createEmptyBorder());
                actions.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); pageInfo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); chosen.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                theme.style(playerPrev, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON); theme.style(playerNext, br.vbrasfoot.api.UiTheme.Role.SECONDARY_BUTTON);
                JButton choose = theme.style(new JButton("Escolher " + (side == 0 ? "A" : "B")), br.vbrasfoot.api.UiTheme.Role.PRIMARY_BUTTON);
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
                status.setText(page.loaded() ? "Escolha clube, linha e botao Escolher em cada lado." : "Nenhuma carreira ativa. Carregue e clique em Atualizar.");
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
