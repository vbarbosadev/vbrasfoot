package br.vbrasfoot;

import java.awt.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.*;

/** Opt-in loader-owned metadata UI. All file/decoder verification runs off EDT. No gameplay actions. */
final class CareerAssociationPanel {
    private CareerAssociationPanel() { }
    static void register(ModUi ui, CareerAssociationCoordinator coordinator, Logger logger) {
        ui.register("vbrasfoot:career-association", "Carreira e diarios (experimental, sem escritas no jogo)", () -> show(ui, coordinator, logger));
    }
    static String report(CareerAssociationCoordinator.Review review) {
        var decoded = review.decoded();
        StringBuilder text = new StringBuilder("SEM AUTORIDADE DE ESCRITA NO JOGO\nNenhum pagamento, transferencia ou repeticao de comando habilitado.\n\n");
        text.append("Estado dos metadados: ").append(review.status()).append('\n');
        text.append("Origem decodificada: ").append(decoded.source()).append('\n');
        text.append("Geracao: ").append(decoded.generation()).append(" / evidencia: ").append(decoded.evidenceRevision()).append('\n');
        text.append("SHA256: ").append(decoded.bytes().sha256()).append(" / bytes: ").append(decoded.bytes().size()).append('\n');
        text.append("Backup: ").append(decoded.backup()).append(" / ramo de inicializacao observado: ").append(decoded.initializationPathObserved()).append('\n');
        text.append("Ler duas roots nao comprova consistencia global, EDT drenada ou prontidao da carreira.\n\n");
        review.association().ifPresentOrElse(association -> {
            text.append("Carreira UUID: ").append(association.handle().career()).append('\n');
            text.append("Binding UUID: ").append(association.handle().binding()).append('\n');
            text.append("Revisao metadata: ").append(association.revision()).append(" / correspondencia: ").append(review.match().orElseThrow()).append('\n');
            text.append("Ultima decisao explicita: ").append(association.decision()).append('\n');
        }, () -> text.append("Nenhuma associacao selecionada. Nao identificamos carreira automaticamente por nome/hash.\n"));
        text.append("\nDiarios: owners configurados + namespaces existentes no binding explicitamente escolhido.\nInclui mods desabilitados/ausentes nesse binding. Nao e inventario global de outras carreiras ou roots.\nSem snapshot atomico; mudancas exigem nova revisao.\n");
        for (var journal : review.journals()) {
            text.append(journal.owner()).append(journal.configured() ? " [configurado]: " : " [existente, nao selecionado]: ");
            journal.recovery().ifPresentOrElse(recovery -> {
                text.append(recovery.status()).append(" / mudanca de revisao: ").append(recovery.revisionChanged())
                        .append(" / receipts: ").append(recovery.snapshot().receipts().size());
                if (recovery.revisionChanged()) text.append("\n  Ancora registrada SHA256: ").append(recovery.recordedSave().sha256())
                        .append(" / bytes: ").append(recovery.recordedSave().size());
            }, () -> text.append("RECUSADO ").append(journal.failure().orElseThrow()));
            text.append('\n');
        }
        text.append("\nAPPLIED e historico, nao prova de inclusao no save. UNKNOWN bloqueia, nao permite tentar novamente.\n");
        text.append("\nContratos financeiros: owners configurados + diarios + contratos existentes no binding escolhido.\n");
        for (var contracts : review.requests()) {
            text.append(contracts.owner()).append(contracts.configured() ? " [configurado]: " : " [existente, nao selecionado]: ");
            contracts.recovery().ifPresentOrElse(recovery -> {
                text.append("contratos: ").append(recovery.snapshot().requests().size()).append(" / mudanca de revisao: ").append(recovery.revisionChanged());
                if (recovery.revisionChanged()) text.append("\n  Ancora dos contratos SHA256: ").append(recovery.recordedSave().sha256())
                        .append(" / bytes: ").append(recovery.recordedSave().size());
                for (var link : contracts.links()) text.append("\n  ").append(link.key()).append(": ").append(link.status());
            }, () -> text.append("RECUSADO ").append(contracts.failure().orElseThrow()));
            text.append('\n');
        }
        text.append("Contrato sem receipt exige reconciliacao; nao e seguro repetir nem descartar.\n");
        text.append("\nContratos de emprestimo: inclui namespaces de mods desabilitados/ausentes.\n");
        for (var contracts : review.loans()) {
            text.append(contracts.owner()).append(contracts.configured() ? " [configurado]: " : " [existente, nao selecionado]: ");
            contracts.recovery().ifPresentOrElse(recovery -> {
                text.append("contratos: ").append(recovery.snapshot().requests().size()).append(" / revisao alterada: ").append(recovery.revisionChanged());
                for (var request : recovery.snapshot().requests()) text.append("\n  Decisao ").append(request.key()).append(": ").append(recovery.snapshot().state(request.key()));
                for (var link : contracts.links()) text.append("\n  ").append(link.key()).append(": ").append(link.status());
            }, () -> text.append("RECUSADO ").append(contracts.failure().orElseThrow()));
            text.append('\n');
        }
        text.append("Pendentes nao sao reexecutados. APPLIED_SAVE_UNPROVEN nao prova inclusao; UNKNOWN continua bloqueado.\n");
        text.append("LEGACY_NO_EFFECT_WITHOUT_REQUEST preserva apenas recusa antiga sem efeito; nao inventa contrato ou permite replay.\n");
        text.append("\nEvidencias de execucao: inclui namespaces existentes mesmo de mods nao selecionados.\n");
        for (var execution : review.executions()) {
            text.append(execution.owner()).append(execution.configured() ? " [configurado]: " : " [existente, nao selecionado]: ");
            execution.recovery().ifPresentOrElse(recovery -> {
                text.append("registros: ").append(recovery.snapshot().witnesses().size()).append(" / mudanca de revisao: ").append(recovery.revisionChanged());
                text.append(" / observacoes de writer: ").append(recovery.snapshot().serializations().size()).append(" (sem prova de inclusao)");
                for (var gaps : FinanceSerializationReview.inspect(recovery.snapshot(), decoded.bytes())) {
                    text.append("\n  Writer: ").append(gaps.writerObservation()).append(" / operacao UUID: ").append(gaps.operation())
                            .append(" / lacunas: ").append(new TreeSet<>(gaps.gaps()));
                }
                if (recovery.revisionChanged()) text.append("\n  Ancora de execucao SHA256: ").append(recovery.recordedSave().sha256())
                        .append(" / bytes: ").append(recovery.recordedSave().size());
                for (var operation : execution.operations()) text.append("\n  ").append(operation.intent().key()).append(" / UUID: ")
                        .append(operation.operationId()).append(" / ").append(operation.status());
            }, () -> text.append("RECUSADO ").append(execution.failure().orElseThrow()));
            text.append('\n');
        }
        text.append("MEMORY_APPLIED_SAVE_UNPROVEN confirma execucao observada, NAO inclusao no save nem permissao de repetir.\n");
        text.append("UNKNOWN/ARMED, conflitos, registros sem diario ou ancoras antigas continuam bloqueados.\n");
        review.association().ifPresent(association -> {
            text.append("\nRecuperacao entre roots de metadados (somente leitura; sem snapshot atomico):\n");
            for (var row : FinanceMultiRootRecoveryReview.inspect(association.save(), decoded.bytes(), review.journals(), review.requests(), review.executions())) {
                text.append(row.owner()).append(" / roots com HEAD: ").append(row.anchors().size())
                        .append(" / bloqueios: ").append(new TreeSet<>(row.gaps())).append('\n');
            }
            text.append("Roots: diario, contratos e evidencias. Nao sao as duas roots Kryo do jogo. Nenhuma reancoragem coordenada ou replay autorizado.\n");
        });
        text.append("Quarentena conserva todos os comprovantes e converte PREPARED em UNKNOWN; nao resolve efeitos.\n");
        text.append("Reconciliacao NO_EFFECT exige prova duravel exata; conserva recibo bruto UNKNOWN e acrescenta auditoria, sem repetir a chave.\n");
        for (var row : review.journals()) row.recovery().ifPresent(value -> {
            for (var resolution : value.snapshot().noEffectResolutions()) text.append("Resolucao NO_EFFECT: ").append(row.owner()).append(" / ")
                    .append(resolution.intent().key()).append(" / UUID: ").append(resolution.operationId()).append(" / original: ").append(resolution.originalState())
                    .append(" / witness HEAD: ").append(resolution.witnessToken()).append('\n');
        });
        return text.toString();
    }
    private record Choice(String label, CareerAssociationCoordinator.Action action) {
        @Override public String toString() { return label; }
    }
    private static void show(ModUi ui, CareerAssociationCoordinator coordinator, Logger logger) {
        JDialog dialog = new JDialog((Frame) null, "vbrasfoot — Carreira e diarios (experimental)", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); ui.own(dialog);
        JPanel body = new JPanel(new BorderLayout(8, 8));
        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField career = new JTextField(), binding = new JTextField();
        fields.add(new JLabel("Carreira UUID (vazio para criar nova associacao):")); fields.add(career);
        fields.add(new JLabel("Binding UUID (selecao explicita; sem busca por hash):")); fields.add(binding);
        body.add(fields, BorderLayout.NORTH);
        JTextArea details = new JTextArea("Verificando origem carregada. Nao cria carreira nem altera saves.");
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setName("vbrasfoot.career-association.report");
        body.add(new JScrollPane(details), BorderLayout.CENTER);
        JComboBox<Choice> action = new JComboBox<>(new Choice[] {
                new Choice("Escolha uma acao explicita", null),
                new Choice("Criar associacao nova (nao cria carreira no jogo)", CareerAssociationCoordinator.Action.CREATE_NEW),
                new Choice("Copia independente com dados de mods vazios", CareerAssociationCoordinator.Action.FORK_COPY),
                new Choice("Renomeacao comprovada por identidade nativa", CareerAssociationCoordinator.Action.RENAME_PROVEN),
                new Choice("Eu afirmo a renomeacao SEM prova de identidade nativa", CareerAssociationCoordinator.Action.ASSERT_RENAME),
                new Choice("Aceitar revisao alterada (sem migrar dados/diarios)", CareerAssociationCoordinator.Action.ACCEPT_REVISION),
                new Choice("Registrar restore do previous conhecido (sem rollback de mods)", CareerAssociationCoordinator.Action.RESTORE_PREVIOUS),
                new Choice("Quarentena de pendentes (SEM resolver ou repetir efeitos)", CareerAssociationCoordinator.Action.QUARANTINE_PENDING),
                new Choice("Reancorar diario SOMENTE com ausencia de efeitos confirmada", CareerAssociationCoordinator.Action.REANCHOR_NO_EFFECT_JOURNAL),
                new Choice("Reconciliar UNKNOWN SOMENTE com prova duravel NO_EFFECT (preserva historico)", CareerAssociationCoordinator.Action.RECONCILE_PROVEN_NO_EFFECT),
                new Choice("Preparar contexto INTERNO (nenhuma escrita publica liberada)", CareerAssociationCoordinator.Action.PREPARE_INTERNAL_CONTEXT)
        });
        JComboBox<String> owner = new JComboBox<>(); owner.setToolTipText("Owner explicito para quarentena, reancoragem ou reconciliacao provada do diario revisado");
         JButton refresh = new JButton("Verificar (somente leitura)"), confirm = new JButton("Revisar e confirmar acao"); confirm.setEnabled(false);
         JButton selectCareerData = new JButton("Vincular dados de mods a esta associacao"), clearCareerData = new JButton("Limpar selecao de dados da sessao");
         selectCareerData.setEnabled(false); clearCareerData.setEnabled(false);
        JLabel status = new JLabel("Sem comandos de gameplay.");
        JPanel commands = new JPanel(new GridLayout(0, 1, 4, 4)); commands.add(action); commands.add(owner);
         JPanel buttons = new JPanel(); buttons.add(refresh); buttons.add(confirm); commands.add(buttons);
         JPanel dataButtons = new JPanel(); dataButtons.add(selectCareerData); dataButtons.add(clearCareerData); commands.add(dataButtons); commands.add(status);
        body.add(commands, BorderLayout.SOUTH); dialog.setContentPane(body);
        var usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.max(300, Math.min(1000, usable.width - 80)), Math.max(260, Math.min(700, usable.height - 80))); dialog.setLocationRelativeTo(null);
        CareerAssociationCoordinator.Review[] latest = new CareerAssociationCoordinator.Review[1];
        DocumentListener changed = new DocumentListener() {
            private void invalidate() { latest[0] = null; confirm.setEnabled(false); }
            public void insertUpdate(DocumentEvent event) { invalidate(); } public void removeUpdate(DocumentEvent event) { invalidate(); }
            public void changedUpdate(DocumentEvent event) { invalidate(); }
        };
        career.getDocument().addDocumentListener(changed); binding.getDocument().addDocumentListener(changed);
        class Work {
            void run(Callable<CareerSaveAssociations.Handle> mutation) {
                final Optional<CareerSaveAssociations.Handle> selected;
                try {
                    if (career.getText().isBlank() && binding.getText().isBlank()) selected = Optional.empty();
                    else selected = Optional.of(new CareerSaveAssociations.Handle(UUID.fromString(career.getText().trim()), UUID.fromString(binding.getText().trim())));
                } catch (IllegalArgumentException invalid) { status.setText("Informe ambos UUIDs validos ou deixe ambos vazios."); return; }
                latest[0] = null; confirm.setEnabled(false); refresh.setEnabled(false); career.setEnabled(false); binding.setEnabled(false); status.setText("Verificando fora da EDT...");
                new SwingWorker<CareerAssociationCoordinator.Review, Void>() {
                    CareerSaveAssociations.Handle committed;
                    @Override protected CareerAssociationCoordinator.Review doInBackground() throws Exception {
                        if (mutation != null) {
                            committed = mutation.call();
                            logger.info("career-metadata-confirmed career=" + committed.career() + " binding=" + committed.binding() + " gameplayWriteAuthority=false");
                        }
                        return coordinator.review(committed == null ? selected : Optional.of(committed));
                    }
                    @Override protected void done() {
                        // Always consume/log outcomes even if user disposed the window during explicit metadata commit.
                        try {
                            var review = get();
                            if (!dialog.isDisplayable()) return;
                            if (committed != null) { career.setText(committed.career().toString()); binding.setText(committed.binding().toString()); }
                            latest[0] = review; details.setText(report(review) + "\nContexto interno: " + coordinator.operationalSnapshot().status()
                                    + "\nNenhuma permissao de comando ou handler concedida a mods.\n"); details.setCaretPosition(0); owner.removeAllItems();
                            for (var row : review.journals()) owner.addItem(row.owner());
                             confirm.setEnabled(true);
                             boolean selectable = review.status() == CareerAssociationCoordinator.Status.READ_ONLY_LINKED && review.decoded().initializationPathObserved();
                             selectCareerData.setEnabled(selectable); clearCareerData.setEnabled(coordinator.hasCareerDataSelection());
                             status.setText("Snapshot de revisao. Nenhuma escrita no jogo habilitada.");
                        } catch (Exception failure) {
                            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                            if (cause instanceof CareerSaveAssociations.CreationFailure unknown) {
                                logger.warning("career-metadata-create-inspect-before-retry career=" + unknown.attempted().career() + " binding=" + unknown.attempted().binding());
                                if (dialog.isDisplayable()) { career.setText(unknown.attempted().career().toString()); binding.setText(unknown.attempted().binding().toString()); }
                            }
                            if (dialog.isDisplayable()) {
                                if (committed != null) { career.setText(committed.career().toString()); binding.setText(committed.binding().toString()); }
                                status.setText("Recusado ou resultado exige inspecao; nao repetir automaticamente.");
                                details.setText(cause.getClass().getSimpleName() + ": " + cause.getMessage()
                                        + "\nNenhum comando de gameplay autorizado. Se houve commit, UUIDs foram preservados aqui e no log local.");
                            }
                         } finally { if (dialog.isDisplayable()) { refresh.setEnabled(true); career.setEnabled(true); binding.setEnabled(true); } }
                    }
                }.execute();
            }
        }
        Work work = new Work(); refresh.addActionListener(event -> work.run(null));
        selectCareerData.addActionListener(event -> {
            var review = latest[0];
            if (review == null || review.status() != CareerAssociationCoordinator.Status.READ_ONLY_LINKED || !review.decoded().initializationPathObserved()) return;
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Vincular somente os dados externos dos mods a esta associacao?\n"
                    + "Career UUID: " + review.association().orElseThrow().handle().career() + "\n"
                    + "Binding UUID: " + review.association().orElseThrow().handle().binding() + "\n\n"
                    + "Isto nao autoriza pagamento nem grava no save. Uma nova sessao exige nova selecao explicita.",
                    "Selecionar carreira para dados dos mods", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) work.run(() -> coordinator.selectCareerData(review));
        });
        clearCareerData.addActionListener(event -> {
            coordinator.clearCareerDataSelection();
            latest[0] = null; confirm.setEnabled(false); selectCareerData.setEnabled(false); clearCareerData.setEnabled(false);
            status.setText("Selecao de dados por carreira removida para esta sessao.");
            work.run(null);
        });
        confirm.addActionListener(event -> {
            var review = latest[0]; var choice = (Choice) action.getSelectedItem();
            if (review == null || choice == null || choice.action() == null) return;
            Optional<String> selectedOwner = Optional.ofNullable((String) owner.getSelectedItem());
            String message = choice.label() + "?\nArquivo decodificado: " + review.decoded().source().getFileName()
                    + "\nGeracao: " + review.decoded().generation() + " / SHA256: " + review.decoded().bytes().sha256()
                    + "\nOwner (quarentena/reancoragem/reconciliacao de diario): " + selectedOwner.orElse("nenhum")
                    + "\nSomente metadados externos. Nao cria carreira/mod, nao altera save e nao resolve efeitos incertos.";
            int answer = JOptionPane.showOptionDialog(dialog, message, "Confirmacao explicita", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, new String[] {"Cancelar", "Confirmar esta acao"}, "Cancelar");
            if (answer == 1) work.run(() -> coordinator.confirm(review, choice.action(), selectedOwner));
        });
        dialog.setVisible(true); work.run(null);
    }
}
