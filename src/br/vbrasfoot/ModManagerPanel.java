package br.vbrasfoot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Nonmodal loader-owned UI; config scans/writes happen off the EDT. No hot reload. */
final class ModManagerPanel {
    private ModManagerPanel() { }
    static void register(ModUi ui, Path home, Path logs, Supplier<Map<String, br.vbrasfoot.api.ModState>> states) {
        register(ui, home, logs, states, () -> new ModFailures.Snapshot(List.of(), 0));
    }
    static void register(ModUi ui, Path home, Path logs, Supplier<Map<String, br.vbrasfoot.api.ModState>> states, Supplier<ModFailures.Snapshot> failures) {
        ui.register("vbrasfoot:manage", "Gerenciar mods (proxima inicializacao)", () -> show(ui, home, logs, states, failures));
    }
    static JPanel content(List<ModSelection.Entry> entries, Map<String, br.vbrasfoot.api.ModState> states) {
        return content(entries, states, new ModFailures.Snapshot(List.of(), 0));
    }
    static JPanel content(List<ModSelection.Entry> entries, Map<String, br.vbrasfoot.api.ModState> states, ModFailures.Snapshot failures) {
        DefaultTableModel model = new DefaultTableModel(new String[] {"JAR", "ID", "Versao", "Estado agora", "Proximo inicio", "Permissoes", "Validacao", "Falhas recentes", "Ultima falha"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (var entry : entries) {
            var manifest = entry.manifest();
            String id = manifest == null ? "" : manifest.id();
            var recent = failures.entries().stream().filter(f -> f.file().equals(entry.file())).toList();
            var last = recent.isEmpty() ? null : recent.get(recent.size() - 1);
            model.addRow(new Object[] {entry.file(), id, manifest == null ? "" : manifest.version(),
                    states.containsKey(entry.file()) ? states.get(entry.file()).name() : "NAO CARREGADO", entry.enabled() ? "HABILITADO" : "DESABILITADO",
                    manifest == null ? "" : manifest.permissions().stream().sorted().toList().toString(), entry.error(), recent.size(),
                    last == null ? "" : last.stage() + ": " + last.type() + " " + last.message()});
        }
        JTable table = new JTable(model); table.setName("vbrasfoot.mod-manager.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 || i == 1 || i > 4 ? 220 : 130);
        JPanel panel = new JPanel(new BorderLayout()); panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
    static String failureText(ModFailures.Snapshot failures) {
        StringBuilder text = new StringBuilder("Falhas desta sessao (ate 128). Atualizar recarrega a lista.\nNao persistidas entre sessoes; mensagens podem conter dados privados.\nRegistros antigos descartados: " + failures.evicted() + "\n\n");
        if (failures.entries().isEmpty()) text.append("Nenhuma falha capturada nos limites instrumentados. Isso nao prova ausencia de erros no jogo/mods.\n");
        for (var entry : failures.entries()) text.append('#').append(entry.sequence()).append(' ').append(entry.time()).append(' ')
                .append(entry.file()).append(" [").append(entry.id()).append("] ").append(entry.stage()).append('\n')
                .append(entry.type()).append(": ").append(entry.message()).append("\n\n");
        return text.toString();
    }
    private static void show(ModUi ui, Path home, Path logs, Supplier<Map<String, br.vbrasfoot.api.ModState>> states, Supplier<ModFailures.Snapshot> failures) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "vbrasfoot — Gerenciar mods", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        ui.own(dialog);
        JPanel body = new JPanel(new BorderLayout(8, 8));
        JLabel status = new JLabel("Carregando selecao...");
        JTextArea info = new JTextArea("Mudancas valem no proximo inicio. Sem hot reload; estado atual nao muda.\nLogs locais: " + logs);
        info.setEditable(false); info.setLineWrap(true); info.setWrapStyleWord(true); body.add(info, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout()); body.add(center, BorderLayout.CENTER);
        JButton enable = new JButton("Habilitar selecionado"), disable = new JButton("Desabilitar selecionado"), refresh = new JButton("Atualizar");
        JPanel commands = new JPanel(); commands.add(enable); commands.add(disable); commands.add(refresh);
        JPanel footer = new JPanel(new BorderLayout()); footer.add(status, BorderLayout.NORTH); footer.add(commands, BorderLayout.SOUTH); body.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        var usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(new Dimension(Math.max(300, Math.min(1000, usable.width - 80)), Math.max(240, Math.min(480, usable.height - 80))));
        dialog.setLocationRelativeTo(null);
        final ModSelection[] selection = new ModSelection[1];
        final JTable[] table = new JTable[1];
        class Work {
            void run(String jar, boolean enabled) {
                enable.setEnabled(false); disable.setEnabled(false); refresh.setEnabled(false); status.setText("Validando...");
                new SwingWorker<List<ModSelection.Entry>, Void>() {
                    private ModSelection next;
                    @Override protected List<ModSelection.Entry> doInBackground() throws Exception {
                        next = jar == null ? ModSelection.read(home) : selection[0].change(jar, enabled);
                        return next.inventory();
                    }
                    @Override protected void done() {
                        if (!dialog.isDisplayable()) return;
                        try {
                            var entries = get(); selection[0] = next;
                            ModFailures.Snapshot snapshot = failures.get();
                            JPanel panel = content(entries, states.get(), snapshot);
                            table[0] = (JTable) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
                            JTextArea history = new JTextArea(failureText(snapshot));
                            history.setEditable(false); history.setLineWrap(true); history.setWrapStyleWord(true); history.setCaretPosition(0);
                            history.setName("vbrasfoot.mod-manager.failures");
                            JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Mods", panel); tabs.addTab("Falhas da sessao", new JScrollPane(history));
                            center.removeAll(); center.add(tabs); center.revalidate(); center.repaint();
                            status.setText(jar == null ? "Selecao carregada; alteracoes nao afetam esta sessao." : "Salvo para proximo inicio. Estado atual preservado.");
                        } catch (Exception failure) {
                            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                            status.setText("Falha; nenhuma mudanca de estado nesta sessao.");
                            JOptionPane.showMessageDialog(dialog, cause.getMessage(), "Selecao recusada", JOptionPane.ERROR_MESSAGE);
                        } finally { enable.setEnabled(true); disable.setEnabled(true); refresh.setEnabled(true); }
                    }
                }.execute();
            }
            void change(boolean enabled) {
                if (table[0] == null || table[0].getSelectedRow() < 0) return;
                String jar = table[0].getValueAt(table[0].getSelectedRow(), 0).toString();
                if (JOptionPane.showConfirmDialog(dialog, (enabled ? "Habilitar " : "Desabilitar ") + jar + " no proximo inicio?\nNao modifica JARs nem saves.",
                        "Confirmar selecao", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) run(jar, enabled);
            }
        }
        Work work = new Work(); refresh.addActionListener(event -> work.run(null, false));
        enable.addActionListener(event -> work.change(true)); disable.addActionListener(event -> work.change(false));
        dialog.setVisible(true); work.run(null, false);
    }
}
