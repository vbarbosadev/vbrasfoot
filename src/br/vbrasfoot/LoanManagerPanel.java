package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import javax.swing.*;

/** Loader-owned requests with explicit confirmed execution via the session coordinator. */
final class LoanManagerPanel {
    static final String OWNER = "br.vbrasfoot.loader.loans";
    private record ClubChoice(ClubView view) { @Override public String toString() { return view.club().name(); } }
    private record PlayerChoice(ScopedPlayerView view) { @Override public String toString() { return view.player().name() + " / forca " + view.player().strength(); } }
    private record Submission(String text, Optional<CareerAssociationCoordinator.Review> review) { }
    static boolean canTransfer(CareerAssociationCoordinator.Review review, String key) {
        if (review.status() != CareerAssociationCoordinator.Status.READ_ONLY_LINKED) return false;
        var own = review.loans().stream().filter(row -> row.owner().equals(OWNER)).findFirst().flatMap(row -> row.recovery());
        if (own.isEmpty() || !own.orElseThrow().snapshot().states().containsKey(key) || !own.orElseThrow().executable(key)) return false;
        for (var row : review.journals()) {
            if (row.recovery().isEmpty()) return false;
            var recovery = row.recovery().orElseThrow();
            if (recovery.revisionChanged() || recovery.status() == CareerCommandJournal.RecoveryStatus.UNCERTAIN
                    || recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS) return false;
        }
        for (var row : review.loans()) {
            if (row.recovery().isEmpty() || row.recovery().orElseThrow().revisionChanged()) return false;
            for (var link : row.links()) if (link.blocksContext()
                    && !(row.owner().equals(OWNER) && link.key().equals(key) && link.status() == LoanContractReview.Status.PENDING_WITHOUT_RECEIPT)) return false;
        }
        for (var row : review.requests()) if (row.recovery().isEmpty() || row.recovery().orElseThrow().revisionChanged()
                || row.links().stream().anyMatch(FinanceContractReview.Link::blocksContext)) return false;
        for (var row : review.executions()) if (row.recovery().isEmpty() || row.recovery().orElseThrow().revisionChanged()
                || !row.recovery().orElseThrow().snapshot().witnesses().isEmpty()) return false;
        return true; // UI hint only; full coordinator validation always runs again before execution.
    }
    private LoanManagerPanel() { }
    static void register(ModUi ui, CareerAssociationCoordinator coordinator, CareerBridge bridge,
                         Callable<LegacyLoanAdapter> adapter, Logger logger) {
        ui.register("vbrasfoot:loans", "Emprestimos (experimental)", () -> show(ui, coordinator, bridge, adapter, logger));
    }
    static String report(CareerAssociationCoordinator.Review review) {
        StringBuilder text = new StringBuilder("PEDIDOS: APROVAR NAO TRANSFERE. TRANSFERENCIA EXIGE CONFIRMACAO SEPARADA.\n")
                .append("Prazo planejado: 366 dias nativos. Aprovacao da regra nao significa emprestimo realizado.\n")
                .append("Regra NOVA: origem ate mediana libera gratis; acima, pouca participacao libera com taxa10%. Destino tambem avalia pela mediana.\n")
                .append("Estado da associacao: ").append(review.status()).append('\n');
        var row = review.loans().stream().filter(value -> value.owner().equals(OWNER)).findFirst();
        if (row.isEmpty()) return text.append("Nenhum pedido deste gerenciador no binding selecionado.\n").toString();
        row.orElseThrow().recovery().ifPresentOrElse(recovery -> {
            text.append("Revisao alterada: ").append(recovery.revisionChanged()).append('\n');
            for (var request : recovery.snapshot().requests()) text.append(request.key()).append(" / ")
                    .append(request.player().name()).append(" / ").append(request.origin().name()).append(" -> ")
                    .append(request.destination().name()).append(" / ").append(recovery.reviewedState(request.key()))
                    .append(" (decisao gravada: ").append(recovery.snapshot().state(request.key())).append(")\n")
                    .append(request.termsReport()).append('\n');
            for (var link : row.orElseThrow().links()) text.append("Recuperacao ").append(link.key()).append(": ").append(link.status()).append('\n');
            if (recovery.snapshot().states().containsValue(LoanRequestStore.State.NO_EFFECT)) text.append("NO_EFFECT = tentativa encerrada sem transferencia; Registrar novamente NAO reabre essa chave.\n")
                    .append(LoanDiagnostics.historical()).append('\n');
        }, () -> text.append("Leitura recusada: ").append(row.orElseThrow().failure().orElseThrow()).append('\n'));
        return text.toString();
    }
    private static void show(ModUi ui, CareerAssociationCoordinator coordinator, CareerBridge bridge,
                             Callable<LegacyLoanAdapter> adapter, Logger logger) {
        JDialog dialog = new JDialog((Frame) null, "vbrasfoot - Pedidos de emprestimo (experimental)", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); ui.own(dialog);
        JTextField career = new JTextField(), binding = new JTextField(), sourceFilter = new JTextField(), targetFilter = new JTextField();
        JTextField key = new JTextField("loan_" + UUID.randomUUID().toString().replace("-", ""));
        JComboBox<ClubChoice> origins = new JComboBox<>(), destinations = new JComboBox<>();
        JComboBox<PlayerChoice> players = new JComboBox<>();
        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Carreira UUID (associe em Carreira e diarios):")); fields.add(career);
        fields.add(new JLabel("Binding UUID (selecao explicita):")); fields.add(binding);
        fields.add(new JLabel("Filtro do clube de origem:")); fields.add(sourceFilter);
        fields.add(new JLabel("Filtro do clube humano de destino:")); fields.add(targetFilter);
        fields.add(new JLabel("Clube de origem:")); fields.add(origins);
        fields.add(new JLabel("Clube humano de destino:")); fields.add(destinations);
        fields.add(new JLabel("Jogador do elenco consultado:")); fields.add(players);
        fields.add(new JLabel("Chave do pedido (nao altere apos erro incerto):")); fields.add(key);
        JTextArea details = new JTextArea("Escolha explicitamente a associacao e consulte os elencos. Apenas o botao Transferir com confirmacao altera o jogo.");
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        JButton clubs = new JButton("Consultar clubes"), roster = new JButton("Consultar elenco"), submit = new JButton("Registrar e avaliar (sem transferir)");
        JButton refresh = new JButton("Revisar pedidos"), evaluate = new JButton("Avaliar pela regra"), cancel = new JButton("Cancelar pedido");
        JButton execute = new JButton("Transferir pedido aprovado...");
        execute.setEnabled(false);
        JButton eligibility = new JButton("Consultar regra e taxa (sem transferir)");
        JButton fresh = new JButton("Novo pedido (nova chave)...");
        execute.setToolTipText("Revalida carreira/elencos e pede confirmacao. Nunca repete pedidos historicos/incertos.");
        JLabel status = new JLabel("Somente leitura e metadados. Sem partidas, calendario ou save automatico.");
        JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 4));
        for (JButton button : List.of(clubs, roster, eligibility, fresh, submit, refresh, evaluate, cancel, execute)) buttons.add(button);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(buttons, BorderLayout.CENTER); bottom.add(status, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(8, 8)); body.add(fields, BorderLayout.NORTH); body.add(new JScrollPane(details), BorderLayout.CENTER); body.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        var usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.max(300, Math.min(1100, usable.width - 60)), Math.max(260, Math.min(740, usable.height - 60))); dialog.setLocationRelativeTo(null);
        var controls = List.of(clubs, roster, eligibility, fresh, submit, refresh, evaluate, cancel, execute);
        List<JComponent> inputs = List.of(career, binding, sourceFilter, targetFilter, key, origins, destinations, players);
        class Work {
            boolean approved;
            void invalidate() { approved = false; execute.setEnabled(false); }
            void reviewed(CareerAssociationCoordinator.Review review) {
                details.setText(report(review)); approved = canTransfer(review, key.getText().trim());
            }
            <T> void run(Callable<T> action, java.util.function.Consumer<T> success) {
                invalidate();
                controls.forEach(button -> button.setEnabled(false)); status.setText("Verificando fora da EDT...");
                inputs.forEach(input -> input.setEnabled(false));
                new SwingWorker<T, Void>() {
                    @Override protected T doInBackground() throws Exception { return action.call(); }
                    @Override protected void done() {
                        try {
                            T value = get(); logger.info("loan-manager operation completed; inspect result before retry");
                            if (dialog.isDisplayable()) { success.accept(value); status.setText("Concluido. Confira o resultado; nao repita operacoes historicas/incertas."); }
                        } catch (Exception failure) {
                            invalidate();
                            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                            logger.warning("loan-manager review required: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                            if (dialog.isDisplayable()) { details.setText(cause.getClass().getSimpleName() + ": " + cause.getMessage()
                                    + "\nPode haver metadados ou efeito em memoria. Chave preservada; revise antes de qualquer outra acao."); status.setText("Recusado ou requer revisao; sem repeticao automatica."); }
                        } finally { if (dialog.isDisplayable()) { controls.forEach(button -> button.setEnabled(true)); inputs.forEach(input -> input.setEnabled(true)); execute.setEnabled(approved); } }
                    }
                }.execute();
            }
            CareerSaveAssociations.Handle selected() { return new CareerSaveAssociations.Handle(UUID.fromString(career.getText().trim()), UUID.fromString(binding.getText().trim())); }
        }
        Work work = new Work();
        var invalidate = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { work.invalidate(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { work.invalidate(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { work.invalidate(); }
        };
        for (var field : List.of(career, binding, key, sourceFilter, targetFilter)) field.getDocument().addDocumentListener(invalidate);
        for (var combo : List.of(origins, destinations, players)) combo.addActionListener(event -> work.invalidate());
        eligibility.addActionListener(event -> {
            var source = (ClubChoice) origins.getSelectedItem(); var target = (ClubChoice) destinations.getSelectedItem(); var player = (PlayerChoice) players.getSelectedItem();
            if (source == null || target == null || player == null) { status.setText("Consulte e selecione origem, destino e jogador."); return; }
            work.run(() -> {
                var selectedAdapter = adapter.call();
                return CareerBridge.onEdt(() -> selectedAdapter.eligibility(bridge, player.view().reference(), source.view().reference(), target.view().reference()));
            }, diagnostic -> details.setText(player.view().player().name() + " / " + source + " -> " + target + "\n" + diagnostic.report()
                    + "\nNao e o motivo historico de uma tentativa anterior. Nao grava pedido, nao transfere e nao avanca a data."));
        });
        fresh.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Criar uma chave para uma NOVA solicitacao?\nO pedido anterior e seu historico serao preservados."
                            + "\nIsto nao reabre pedidos encerrados e nao remove bloqueios de APPLIED/UNKNOWN/revisao antiga."
                            + "\nConsulte o elenco, regra e taxa antes de registrar outro pedido.",
                    "Novo pedido", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            key.setText("loan_" + UUID.randomUUID().toString().replace("-", "")); players.removeAllItems();
            details.setText("Nova chave preparada SOMENTE na tela. Nenhum contrato foi gravado. Consulte o elenco, regra e taxa.");
        });
        execute.addActionListener(event -> {
            final CareerSaveAssociations.Handle selected;
            try { selected = work.selected(); } catch (IllegalArgumentException invalid) { status.setText("Informe ambos UUIDs validos."); return; }
            String requestKey = key.getText().trim();
            work.run(() -> coordinator.review(Optional.of(selected)), review -> {
                var row = review.loans().stream().filter(value -> value.owner().equals(OWNER)).findFirst();
                var request = row.flatMap(value -> value.recovery()).flatMap(value -> value.snapshot().requests().stream()
                        .filter(item -> item.key().equals(requestKey)).findFirst());
                if (request.isEmpty()) { details.setText("Pedido nao encontrado; registre e avalie antes."); return; }
                var value = request.orElseThrow();
                var recovery = row.orElseThrow().recovery().orElseThrow();
                if (!canTransfer(review, requestKey)) {
                    details.setText(report(review) + "\nTransferencia bloqueada: pedido historico, incerto, obsoleto ou associacao nao confirmada."
                            + "\nNao crie outra associacao/chave para contornar o bloqueio; nao ha repeticao ou recuperacao automatica.");
                    return;
                }
                int answer = JOptionPane.showOptionDialog(dialog,
                        "Transferir " + value.player().name() + " de " + value.origin().name() + " para " + value.destination().name()
                                + "?\nPrazo nativo: 366 dias. Pedido: " + requestKey
                                + "\n" + value.termsReport()
                                + "\nTaxa debitada do destino e creditada na origem: " + value.fee()
                                + "\nAltera o jogo em memoria; salve pela UI original. Nao avanca calendario."
                                + "\nExperimental: apos efeito/save, historico exige revisao e nao pode ser repetido.",
                        "Confirmar emprestimo", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, new String[] {"Cancelar", "Confirmar transferencia"}, "Cancelar");
                if (answer != 1) { work.reviewed(review); return; }
                // Schedule after the outer worker's finally re-enables controls.
                SwingUtilities.invokeLater(() -> work.run(() -> coordinator.executeLoan(review, requestKey, adapter.call()), result -> {
                    logger.info("loan execution key=" + requestKey + " result=" + result.dispatch().kind() + " diagnostic=" + result.diagnostic());
                    details.setText("Resultado: " + result.dispatch().kind() + " / " + result.dispatch().detail()
                            + "\nEstado duravel: " + result.requests().state(requestKey)
                            + "\nDiagnostico: " + result.diagnostic()
                            + "\nDiagnostico desta tentativa tambem registrado no log da sessao; nao faz parte do contrato antigo."
                            + "\nNO_EFFECT = tentativa encerrada sem transferencia; nao registrar novamente com a mesma chave."
                            + "\nEXECUTED = efeito em memoria; salve pelo menu original. UNKNOWN = nao repetir."
                            + "\nApos salvar/reabrir, revisoes antigas permanecem apenas como historico, sem replay.");
                    players.removeAllItems();
                }));
            });
        });
        clubs.addActionListener(event -> {
            String source = sourceFilter.getText(), target = targetFilter.getText(); players.removeAllItems();
            work.run(() -> List.of(bridge.scopedClubs(source, 0, 100), bridge.scopedClubs(target, 0, 100)), pages -> {
                origins.removeAllItems(); destinations.removeAllItems();
                for (var club : pages.get(0).items()) origins.addItem(new ClubChoice(club));
                for (var club : pages.get(1).items()) if (club.club().human()) destinations.addItem(new ClubChoice(club));
                details.setText("Ate100clubes por filtro. Refine se o clube nao apareceu. Escolha origem/destino e consulte o elenco; referencias expiram apos reload/reordenacao.");
            });
        });
        origins.addActionListener(event -> players.removeAllItems());
        roster.addActionListener(event -> {
            var choice = (ClubChoice) origins.getSelectedItem(); if (choice == null) return;
            work.run(() -> bridge.scopedPlayersWithReferences(choice.view().reference(), 0, 500), page -> {
                players.removeAllItems(); for (var player : page.items()) players.addItem(new PlayerChoice(player));
            });
        });
        submit.addActionListener(event -> {
            var source = (ClubChoice) origins.getSelectedItem(); var target = (ClubChoice) destinations.getSelectedItem(); var player = (PlayerChoice) players.getSelectedItem();
            if (source == null || target == null || player == null) { status.setText("Consulte e selecione origem, destino e jogador."); return; }
            final CareerSaveAssociations.Handle selected;
            try { selected = work.selected(); } catch (IllegalArgumentException invalid) { status.setText("Informe ambos UUIDs validos."); return; }
            String requestKey = key.getText().trim();
            work.run(() -> {
                var review = coordinator.review(Optional.of(selected));
                boolean exists = review.loans().stream().filter(value -> value.owner().equals(OWNER))
                        .flatMap(value -> value.recovery().stream()).flatMap(value -> value.snapshot().requests().stream())
                        .anyMatch(value -> value.key().equals(requestKey));
                if (exists) return new Submission("Chave ja registrada. Nenhum pedido novo criado e nenhuma avaliacao repetida.\n" + report(review), Optional.of(review));
                var selectedAdapter = adapter.call();
                var nativeEligibility = CareerBridge.onEdt(() -> selectedAdapter.eligibility(bridge, player.view().reference(), source.view().reference(), target.view().reference()));
                if (!nativeEligibility.eligible()) return new Submission(nativeEligibility.report()
                        + "\nPedido NAO registrado. Transferencia desativada; escolha outro jogador/destino elegivel. Nao e preciso trocar a chave ainda nao gravada.", Optional.empty());
                coordinator.submitLoan(review, OWNER, requestKey, bridge, selectedAdapter, player.view().reference(), source.view().reference(), target.view().reference());
                var after = coordinator.review(Optional.of(selected));
                return new Submission(report(after), Optional.of(after));
            }, result -> { result.review().ifPresent(work::reviewed); details.setText(result.text()); });
        });
        refresh.addActionListener(event -> {
            final CareerSaveAssociations.Handle selected;
            try { selected = work.selected(); } catch (IllegalArgumentException invalid) { status.setText("Informe ambos UUIDs validos."); return; }
            work.run(() -> coordinator.review(Optional.of(selected)), work::reviewed);
        });
        for (JButton button : List.of(evaluate, cancel)) button.addActionListener(event -> {
            final CareerSaveAssociations.Handle selected;
            try { selected = work.selected(); } catch (IllegalArgumentException invalid) { status.setText("Informe ambos UUIDs validos."); return; }
            String requestKey = key.getText().trim(); boolean cancelling = button == cancel;
            work.run(() -> {
                coordinator.decideLoan(coordinator.review(Optional.of(selected)), OWNER, requestKey, cancelling);
                return coordinator.review(Optional.of(selected));
            }, work::reviewed);
        });
        dialog.setVisible(true);
    }
}
