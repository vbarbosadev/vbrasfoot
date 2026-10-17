package br.vbrasfoot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import javax.swing.*;

/** Review is editable; only an explicit confirmation starts a local CREATE_NEW write. */
final class DiagnosticReview {
    private DiagnosticReview() { }
    static void show(ModUi ui, Path home, DiagnosticExport.Snapshot snapshot, String initial) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "vbrasfoot — Revisar diagnostico", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); ui.own(dialog);
        JPanel body = new JPanel(new BorderLayout(6, 6));
        JTextArea warning = new JTextArea("Revise/edite o texto antes de salvar ou compartilhar. Nada e enviado automaticamente.\nNao inclui saves, JARs ou logs brutos. Mensagens opcionais podem conter caminhos, dados e segredos.");
        warning.setEditable(false); warning.setLineWrap(true); warning.setWrapStyleWord(true); warning.setRows(3);
        JCheckBox identities = new JCheckBox("Incluir nomes/IDs/versoes/permissoes dos mods");
        JCheckBox details = new JCheckBox("Incluir tipos/mensagens de falhas e validacao (dados privados)");
        identities.setName("vbrasfoot.diagnostic.identity"); details.setName("vbrasfoot.diagnostic.details");
        JPanel top = new JPanel(); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS)); top.add(warning); top.add(identities); top.add(details);
        body.add(top, BorderLayout.NORTH);
        JTextArea preview = new JTextArea(initial); preview.setName("vbrasfoot.diagnostic.preview"); preview.setCaretPosition(0);
        preview.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        body.add(new JScrollPane(preview), BorderLayout.CENTER);
        JButton generate = new JButton("Gerar previa com opcoes"), save = new JButton("Salvar texto revisado"), cancel = new JButton("Fechar sem salvar");
        generate.setName("vbrasfoot.diagnostic.generate"); save.setName("vbrasfoot.diagnostic.save"); cancel.setName("vbrasfoot.diagnostic.close");
        JPanel controls = new JPanel(); controls.add(generate); controls.add(save); controls.add(cancel);
        JTextArea status = new JTextArea("Nenhum arquivo criado. Opcoes so se aplicam ao Gerar previa; salvar usa exatamente o texto exibido.");
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setRows(2);
        status.setName("vbrasfoot.diagnostic.status");
        JPanel footer = new JPanel(new BorderLayout()); footer.add(status, BorderLayout.NORTH); footer.add(controls, BorderLayout.SOUTH); body.add(footer, BorderLayout.SOUTH);
        String[] generated = {initial};
        generate.addActionListener(event -> {
            if (!preview.getText().equals(generated[0]) && JOptionPane.showConfirmDialog(dialog, "Gerar novamente descarta suas edicoes/redacoes. Continuar?", "Regerar previa", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try {
                generated[0] = DiagnosticExport.preview(snapshot, new DiagnosticExport.Options(identities.isSelected(), details.isSelected()));
                preview.setText(generated[0]); preview.setCaretPosition(0);
                status.setText("Previa atualizada a partir do mesmo snapshot. Revise antes de salvar.");
            } catch (Exception failure) { JOptionPane.showMessageDialog(dialog, failure.getMessage(), "Previa recusada", JOptionPane.ERROR_MESSAGE); }
        });
        save.addActionListener(event -> {
            String reviewed = preview.getText();
            if (JOptionPane.showConfirmDialog(dialog, "Salvar localmente o texto exibido em vbrasfoot/diagnostics/?\nRevise dados privados antes de compartilhar. Nenhum envio automatico.", "Confirmar exportacao local", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            save.setEnabled(false); generate.setEnabled(false); identities.setEnabled(false); details.setEnabled(false); preview.setEditable(false);
            cancel.setText("Fechar (gravacao iniciada)");
            new SwingWorker<Path, Void>() {
                @Override protected Path doInBackground() throws Exception { return DiagnosticExport.saveReviewed(home, reviewed); }
                @Override protected void done() {
                    if (!dialog.isDisplayable()) return;
                    try { status.setText("Salvo localmente: " + get() + "\nNada foi enviado. Revise o arquivo antes de compartilhar."); cancel.setText("Fechar"); }
                    catch (Exception failure) { Throwable cause = failure.getCause() == null ? failure : failure.getCause(); status.setText("Exportacao recusada: " + cause.getMessage()); }
                    finally { save.setEnabled(true); generate.setEnabled(true); identities.setEnabled(true); details.setEnabled(true); preview.setEditable(true); cancel.setText("Fechar"); }
                }
            }.execute();
        });
        cancel.addActionListener(event -> dialog.dispose());
        dialog.setContentPane(body);
        var usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(new Dimension(Math.max(320, Math.min(1000, usable.width - 80)), Math.max(300, Math.min(650, usable.height - 80))));
        dialog.setLocationRelativeTo(null); dialog.setVisible(true);
    }
}
