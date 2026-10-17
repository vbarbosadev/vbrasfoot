package br.vbrasfoot.inspector;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public final class InspectorMod implements Mod {
    private JDialog dialog;

    @Override public void initialize(ModContext context) {
        context.registerAction("inspect", "Consultar clubes e jogadores", () -> open(context.game()));
        context.registerAction("about", "Sobre o vbrasfoot", () -> JOptionPane.showMessageDialog(null,
                "vbrasfoot 0.2.0 — Brasfoot 22-23\nConsulta somente de leitura.\nNenhum formato de save foi alterado."));
    }

    private void open(GameAccess game) {
        if (dialog != null && dialog.isDisplayable()) { dialog.toFront(); return; }
        dialog = new JDialog((Window) null, "vbrasfoot — Inspetor da carreira");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JComboBox<String> clubs = new JComboBox<>();
        DefaultTableModel model = new DefaultTableModel(new String[] {"ID", "Nome", "Idade", "Posicao", "Forca", "Valor"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        JLabel status = new JLabel("Carregue uma carreira no jogo e clique em Atualizar.");
        JButton refresh = new JButton("Atualizar");
        JTextField filter = new JTextField(16);
        JButton previous = new JButton("Anterior"), next = new JButton("Proxima");
        JPanel controls = new JPanel();
        controls.add(new JLabel("Clube:"));
        controls.add(filter);
        controls.add(previous);
        controls.add(next);
        controls.add(refresh);
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(clubs, BorderLayout.CENTER);
        top.add(controls, BorderLayout.NORTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(800, 450));
        final GameAccess.ClubPage[] current = {new GameAccess.ClubPage(false, java.util.List.of(), 0, 0)};
        final int[] offset = {0};
        clubs.addActionListener(event -> {
            model.setRowCount(0);
            int index = clubs.getSelectedIndex();
            if (index < 0 || index >= current[0].items().size()) return;
            String[] positions = {"Goleiro", "Lateral", "Zagueiro", "Meia", "Atacante"};
            // Bound each read and never materialize all players from the career.
            for (GameAccess.Player player : game.playersAt(current[0].items().get(index).index(), 0, 500).items()) {
                String position = player.position() >= 0 && player.position() < positions.length
                        ? positions[player.position()] : Integer.toString(player.position());
                model.addRow(new Object[] {player.id(), player.name(), player.age(), position, player.strength(),
                        NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(player.value())});
            }
        });
        Runnable reload = () -> {
            clubs.removeAllItems();
            current[0] = game.clubs(filter.getText(), offset[0], 100);
            for (GameAccess.ClubSummary club : current[0].items()) clubs.addItem(club.name() + (club.human() ? " (seu clube)" : ""));
            previous.setEnabled(offset[0] > 0);
            next.setEnabled(offset[0] + current[0].items().size() < current[0].total());
            status.setText(current[0].loaded() ? current[0].total() + " clubes; pagina " + (offset[0] / 100 + 1) + " — ate 500 jogadores do clube selecionado"
                    : "Nenhuma carreira ativa. Carregue seu save existente no jogo e atualize.");
        };
        refresh.addActionListener(event -> { offset[0] = 0; reload.run(); });
        filter.addActionListener(event -> refresh.doClick());
        previous.addActionListener(event -> { offset[0] = Math.max(0, offset[0] - 100); reload.run(); });
        next.addActionListener(event -> { offset[0] += 100; reload.run(); });
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        refresh.doClick();
    }

    @Override public void shutdown() { SwingUtilities.invokeLater(() -> { if (dialog != null) dialog.dispose(); }); }
}
