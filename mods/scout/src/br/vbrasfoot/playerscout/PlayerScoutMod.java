package br.vbrasfoot.playerscout;

import br.vbrasfoot.api.*;
import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** SDK-only embedded, manually refreshed, read-only scout. */
public final class PlayerScoutMod implements Mod {
    private AutoCloseable registration;
    @Override public void initialize(ModContext context) {
        registration = context.registerPanel("scout", "Scout de jogadores", theme -> new Panel(context.game()));
    }
    @Override public void shutdown() throws Exception {
        if (registration != null) { registration.close(); registration = null; }
    }
    private static final class Panel implements ModPanel {
        private final JPanel root = new JPanel(new BorderLayout(6, 6));
        private final ScoutModel model;
        private final JTextField filter = new JTextField(16);
        private final JComboBox<String> clubs = new JComboBox<>();
        private final JLabel status = new JLabel("Atualize clubes. Ordenacao e filtros valem apenas para a pagina atual.");
        private final DefaultTableModel rows = new DefaultTableModel(new String[] {
                "Nome", "Idade", "Posicao (codigo)", "Forca", "Valor (jogo)", "Goleiro", "Desarme", "Passe", "Armacao", "Finalizacao", "Velocidade", "Tecnica"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) { return column == 0 ? String.class : Integer.class; }
        };
        private final JButton clubPrevious = new JButton("Clubes <"), clubNext = new JButton("Clubes >");
        private final JButton read = new JButton("Consultar elenco"), playerPrevious = new JButton("Jogadores <"), playerNext = new JButton("Jogadores >");
        private int clubOffset, playerOffset; private boolean updating, closed;
        Panel(GameAccess game) {
            model = new ScoutModel(game);
            JPanel top = new JPanel(); JButton refresh = new JButton("Buscar clubes");
            top.add(filter); top.add(refresh); top.add(clubPrevious); top.add(clubNext); top.add(clubs);
            top.add(read); top.add(playerPrevious); top.add(playerNext); root.add(top, BorderLayout.NORTH);
            JTable table = new JTable(rows); table.setAutoCreateRowSorter(true); root.add(new JScrollPane(table));
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.add(new JLabel("Somente leitura. Atributos brutos; vazio = habilidade individual desativada. Sem salarios/contratos ou pontuacao de scout."), BorderLayout.NORTH);
            bottom.add(status); root.add(bottom, BorderLayout.SOUTH);
            refresh.addActionListener(e -> query(() -> loadClubs(0))); filter.addActionListener(e -> refresh.doClick());
            clubPrevious.addActionListener(e -> query(() -> loadClubs(Math.max(0, clubOffset - ScoutModel.PAGE_SIZE))));
            clubNext.addActionListener(e -> query(() -> loadClubs(clubOffset + ScoutModel.PAGE_SIZE)));
            read.addActionListener(e -> query(() -> loadPlayers(0)));
            playerPrevious.addActionListener(e -> query(() -> loadPlayers(Math.max(0, playerOffset - ScoutModel.PAGE_SIZE))));
            playerNext.addActionListener(e -> query(() -> loadPlayers(playerOffset + ScoutModel.PAGE_SIZE)));
            clubs.addActionListener(e -> { if (!updating) { clearRows(); read.setEnabled(clubs.getSelectedIndex() >= 0); } });
            clear();
        }
        private void clearRows() { rows.setRowCount(0); playerOffset = 0; playerPrevious.setEnabled(false); playerNext.setEnabled(false); }
        private void clear() {
            model.clear(); clearRows(); updating = true;
            try { clubs.removeAllItems(); } finally { updating = false; }
            read.setEnabled(false); clubPrevious.setEnabled(false); clubNext.setEnabled(false);
        }
        private void query(Runnable action) {
            if (closed) return;
            try { action.run(); }
            catch (RuntimeException failure) {
                clear(); status.setText((failure instanceof GameQueryException q ? q.reason().name() : failure.getClass().getSimpleName())
                        + " — dados limpos; busque clubes novamente. Sem fallback.");
            }
        }
        private void loadClubs(int offset) {
            clear(); var page = model.clubs(filter.getText(), offset); clubOffset = page.offset(); updating = true;
            try { for (var club : page.items()) clubs.addItem(club.club().name()); clubs.setSelectedIndex(-1); }
            finally { updating = false; }
            clubPrevious.setEnabled(page.loaded() && offset > 0);
            clubNext.setEnabled(page.loaded() && (long) offset + page.items().size() < page.total());
            status.setText(page.loaded() ? "Selecione clube; " + page.items().size() + " de " + page.total() + " clubes." : "NO_CAREER — carregue uma carreira e atualize.");
        }
        private void loadPlayers(int offset) {
            clearRows(); var page = model.players(clubs.getSelectedIndex(), offset); playerOffset = page.offset();
            for (var item : page.items()) {
                var p = item.player(); var s = item.skills().orElse(null);
                rows.addRow(new Object[] {p.name(), p.age(), p.position(), p.strength(), p.value(),
                        s == null ? null : s.goalkeeping(), s == null ? null : s.tackling(), s == null ? null : s.passing(),
                        s == null ? null : s.playmaking(), s == null ? null : s.finishing(), s == null ? null : s.speed(), s == null ? null : s.technique()});
            }
            playerPrevious.setEnabled(offset > 0); playerNext.setEnabled((long) offset + page.items().size() < page.total());
            status.setText("Elenco: " + page.items().size() + " de " + page.total() + "; offset " + offset + ". Clique nos titulos para ordenar esta pagina.");
        }
        @Override public JComponent component() { return root; }
        @Override public void close() { closed = true; clear(); root.removeAll(); }
    }
}
