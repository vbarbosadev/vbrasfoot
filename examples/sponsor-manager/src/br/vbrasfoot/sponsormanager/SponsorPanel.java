package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.CareerDataAccess;
import br.vbrasfoot.api.SponsorAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.ModContext;
import br.vbrasfoot.api.ModPanel;
import br.vbrasfoot.api.UiTheme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import br.vbrasfoot.api.SponsorDialogs;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/** Career-scoped configuration and explicitly confirmed one-shot sponsor activation. */
final class SponsorPanel implements ModPanel {
    static record ClubLookup(GameAccess.ClubSummary club, String message, GameAccess.CareerScope scope) { }
    private record Initial(List<SponsorContract> contracts, java.util.Optional<UUID> featured,
                          java.util.Optional<CareerDataAccess.Scope> dataScope, int legacyCount, ClubLookup club) { }
    private record Stored(List<SponsorContract> contracts, java.util.Optional<UUID> featured,
                          java.util.Optional<CareerDataAccess.Scope> dataScope, int legacyCount, ClubLookup club) { }
    private static final int CLUB_PAGE_SIZE = 500, MAX_CLUBS_TO_SCAN = 10_000;
    private final ModContext context;
    private final SponsorBanner banner;
    private final CareerDataAccess careerData;
    private final SponsorAccess sponsorAccess;
    private volatile java.util.Map<UUID, SponsorAccess.State> activationStates = java.util.Map.of();
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final JTextField sponsor = new JTextField(18), baseAmount = new JTextField("0", 10),
            intervalDays = new JTextField("30", 5), bonusAmount = new JTextField("0", 10), stadiumName = new JTextField(18);
    private final JComboBox<SponsorContract.Cadence> cadence = new JComboBox<>(SponsorContract.Cadence.values());
    private final JCheckBox bonus = new JCheckBox("Bonus por vitoria oficial em casa (inclusive penaltis)"),
            namingRights = new JCheckBox("Alterar nome do estadio ao receber");
    private final javax.swing.JTextArea message = new javax.swing.JTextArea("A {patrocinador} oferece {valor} ao {clube}. Clique em Receber patrocinio para receber.", 3, 30);
    private final JLabel clubLabel = new JLabel("Identificando clube controlado..."),
            logoLabel = new JLabel("Nenhuma logo escolhida"), status = new JLabel("Carregando configuracoes...");
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[] {"Patrocinador", "Base (unid.)", "Periodicidade", "Bonus casa (unid.)", "Estadio proposto", "Estado"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JPanel logoStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    private final JButton refreshClub = new JButton("Atualizar meu clube"), chooseLogo = new JButton("Escolher logo PNG/JPEG..."),
            clearLogo = new JButton("Sem logo"),
            add = new JButton("Salvar configuracao nesta carreira"), end = new JButton("Arquivar selecionada"), terminate = new JButton("Encerrar patrocinio"),
            feature = new JButton("Exibir selecionada no topo"), importLegacy = new JButton("Importar configuracoes globais (sem ativar)"),
             activate = new JButton("Ativar e enviar mensagem (sem credito)"), bind = new JButton("Vincular esta carreira..."), reload = new JButton("Recarregar dados");
    private List<SponsorContract> all = List.of(), visible = List.of();
    private java.util.Optional<CareerDataAccess.Scope> dataScope = java.util.Optional.empty();
    private java.util.Optional<GameAccess.CareerScope> gameScope = java.util.Optional.empty();
    private int legacyCount;
    private GameAccess.ClubSummary fixedClub;
    private String clubMessage = "Identificando clube controlado...";
    private byte[] logoBytes;
    private boolean busy, closed, loaded;

    SponsorPanel(ModContext context, UiTheme theme, SponsorBanner banner) {
        this.context = context; this.banner = banner; this.careerData = context.careerData(); this.sponsorAccess = context.sponsors();
        theme.style(root, UiTheme.Role.BODY);
        var warning = theme.style(new JLabel("Ativar envia uma mensagem. O dinheiro e o estadio so mudam ao RECEBER na Central de Mensagens."), UiTheme.Role.STATUS);
        root.add(warning, BorderLayout.NORTH);

        JPanel clubRow = theme.style(new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)), UiTheme.Role.BODY);
        clubRow.add(theme.style(new JLabel("Clube fixo da carreira:"), UiTheme.Role.BODY));
        clubRow.add(theme.style(clubLabel, UiTheme.Role.BODY));
        clubRow.add(theme.style(refreshClub, UiTheme.Role.SECONDARY_BUTTON));
        bind.setName("sponsor.bind"); stadiumName.setName("sponsor.stadium"); chooseLogo.setName("sponsor.logo.choose");
        clubRow.add(theme.style(bind, UiTheme.Role.PRIMARY_BUTTON));

        JPanel fields = theme.style(new JPanel(new java.awt.GridBagLayout()), UiTheme.Role.BODY);
        fields.setName("sponsor.form.fields");
        fields.setBorder(BorderFactory.createTitledBorder("Novo patrocinador — somente nesta carreira"));
        formRow(fields, 0, theme.style(new JLabel("Nome do patrocinador:"), UiTheme.Role.BODY), theme.style(sponsor, UiTheme.Role.INPUT));
        formRow(fields, 1, theme.style(new JLabel("Credito unico (unidades do jogo):"), UiTheme.Role.BODY), theme.style(baseAmount, UiTheme.Role.INPUT));
        formRow(fields, 2, theme.style(namingRights, UiTheme.Role.INPUT), theme.style(stadiumName, UiTheme.Role.INPUT));
        var logoButtons = theme.style(new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)), UiTheme.Role.BODY);
        logoButtons.add(theme.style(chooseLogo, UiTheme.Role.SECONDARY_BUTTON)); logoButtons.add(theme.style(clearLogo, UiTheme.Role.SECONDARY_BUTTON));
        formRow(fields, 3, theme.style(new JLabel("Logo opcional (PNG/JPEG):"), UiTheme.Role.BODY), logoButtons);
        formRow(fields, 4, theme.style(new JLabel("Previa (128 x 40):"), UiTheme.Role.BODY), theme.style(logoLabel, UiTheme.Role.BODY));
        message.setName("sponsor.message"); message.setLineWrap(true); message.setWrapStyleWord(true);
        message.setToolTipText("Marcadores: {patrocinador}, {valor}, {clube}. Texto simples, ate 2000 caracteres.");
        formRow(fields, 5, theme.style(new JLabel("Mensagem na central:"), UiTheme.Role.BODY), new JScrollPane(message));

        JPanel form = theme.style(new JPanel(new BorderLayout(4, 5)), UiTheme.Role.BODY);
        form.setName("sponsor.form");
        form.add(clubRow, BorderLayout.NORTH);
        var formBody = theme.style(new JPanel(new BorderLayout()), UiTheme.Role.BODY);
        formBody.add(fields, BorderLayout.NORTH); form.add(formBody, BorderLayout.CENTER);
        add.setName("sponsor.form.add");
        form.add(theme.style(add, UiTheme.Role.PRIMARY_BUTTON), BorderLayout.SOUTH);

        table.setAutoCreateRowSorter(false);
        theme.style(table, UiTheme.Role.TABLE);
        table.setName("sponsor.configurations.table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelectionButtons();
        });
        logoStrip.setLayout(new javax.swing.BoxLayout(logoStrip, javax.swing.BoxLayout.X_AXIS));
        logoStrip.setBorder(BorderFactory.createTitledBorder("Logos deste clube (prévia dentro do painel)"));
        theme.style(logoStrip, UiTheme.Role.BODY);
        JPanel management = theme.style(new JPanel(new BorderLayout(4, 5)), UiTheme.Role.BODY);
        var logoScroll = new JScrollPane(logoStrip, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        logoScroll.setPreferredSize(new Dimension(850, 112));
        management.add(logoScroll, BorderLayout.NORTH);
        management.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel managementButtons = theme.style(new JPanel(new GridLayout(0, 2, 6, 4)), UiTheme.Role.BODY);
        managementButtons.add(theme.style(end, UiTheme.Role.SECONDARY_BUTTON));
        activate.setName("sponsor.activate"); activate.setEnabled(false);
        activate.setToolTipText("Uma tentativa por contrato; resultados incertos nunca sao repetidos automaticamente.");
        managementButtons.add(theme.style(activate, UiTheme.Role.SECONDARY_BUTTON));
        feature.setName("sponsor.feature");
        managementButtons.add(theme.style(feature, UiTheme.Role.SECONDARY_BUTTON));
        importLegacy.setName("sponsor.legacy.import");
        managementButtons.add(theme.style(importLegacy, UiTheme.Role.SECONDARY_BUTTON));
        managementButtons.add(theme.style(reload, UiTheme.Role.SECONDARY_BUTTON));
        terminate.setName("sponsor.end"); managementButtons.add(theme.style(terminate, UiTheme.Role.SECONDARY_BUTTON));
        management.add(managementButtons, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("sponsor.tabs");
        tabs.addTab("Novo patrocinador", form);
        tabs.addTab("Configuracoes e logos", management);
        root.add(tabs, BorderLayout.CENTER);
        root.add(theme.style(status, UiTheme.Role.STATUS), BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(900, 650));
        cadence.setSelectedItem(SponsorContract.Cadence.ON_ACTIVATION);
        theme.apply(root);
        intervalDays.setEnabled(false); bonusAmount.setEnabled(false); stadiumName.setEnabled(false);
        cadence.setEnabled(false); bonus.setEnabled(false);
        namingRights.addActionListener(event -> stadiumName.setEnabled(!busy && namingRights.isSelected()));
        chooseLogo.addActionListener(event -> importLogo());
        clearLogo.addActionListener(event -> { logoBytes = null; logoLabel.setText("Sem logo (opcional)"); logoLabel.setIcon(null); });
        refreshClub.addActionListener(event -> refreshCurrentClub());
        add.addActionListener(event -> create()); end.addActionListener(event -> archive());
        feature.addActionListener(event -> featureSelected()); importLegacy.addActionListener(event -> importLegacy());
        bind.addActionListener(event -> bindCareer()); activate.addActionListener(event -> activateSelected());
        terminate.addActionListener(event -> endSelected());
        reload.addActionListener(event -> reloadData());
        reloadInitial();
    }
    private static void formRow(JPanel panel, int row, JComponent label, JComponent input) {
        var c = new java.awt.GridBagConstraints(); c.gridy = row; c.gridx = 0;
        c.anchor = java.awt.GridBagConstraints.WEST; c.insets = new java.awt.Insets(7, 8, 7, 8);
        panel.add(label, c); c.gridx = 1; c.weightx = 1; c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        input.setPreferredSize(new Dimension(340, row == 5 ? 76 : row == 4 ? 48 : 30)); panel.add(input, c);
    }
    private void bindCareer() {
        run("Identificando save carregado...", () -> {
            sponsorAccess.bindCareer(); return readCurrent();
        }, this::installContracts);
    }

    private void reloadInitial() {
        run("Carregando configuracoes e clube...", () -> {
            var selected = careerData.current();
            var lookup = lookupClub(context.game());
            if (selected.isEmpty() && lookup.club() != null) selected = java.util.Optional.of(sponsorAccess.bindCareer());
            int legacy = legacyCount();
            if (selected.isEmpty()) return new Initial(List.of(), java.util.Optional.empty(), selected, legacy, lookup);
            var binding = selected.orElseThrow();
            var contracts = SponsorStore.decode(careerData.read(binding, "sponsor-config.bin"));
            var featured = SponsorFeatureStore.decode(careerData.read(binding, "sponsor-feature.bin"));
            refreshStates(binding, contracts);
            return new Initial(contracts, featured, selected, legacy, lookup);
        }, initial -> {
            all = projectEnded(initial.contracts()); dataScope = initial.dataScope(); legacyCount = initial.legacyCount();
            gameScope = java.util.Optional.ofNullable(initial.club().scope());
            banner.replace(all, initial.featured(), gameScope, dataScope); loaded = true;
            fixedClub = initial.club().club(); clubMessage = initial.club().message();
            clubLabel.setText(fixedClub == null ? clubMessage : clubText(fixedClub));
        });
    }

    static ClubLookup lookupClub(GameAccess game) {
        try {
            var first = game.scopedClubs("", 0, CLUB_PAGE_SIZE);
            if (!first.loaded()) return new ClubLookup(null, "Carregue uma carreira primeiro.", null);
            int total = first.total();
            if (total < 0 || total > MAX_CLUBS_TO_SCAN)
                return new ClubLookup(null, "Consulta acima do limite seguro de " + MAX_CLUBS_TO_SCAN + " clubes; selecao recusada.", null);
            if (!validPage(first, 0, total)) return new ClubLookup(null, "Pagina de clubes incompleta; atualize a consulta.", null);
            var human = new java.util.ArrayList<GameAccess.ClubSummary>();
            first.items().stream().map(GameAccess.ClubView::club).filter(GameAccess.ClubSummary::human).forEach(human::add);
            for (int offset = CLUB_PAGE_SIZE; offset < total; offset += CLUB_PAGE_SIZE) {
                var page = game.scopedClubs("", offset, CLUB_PAGE_SIZE);
                if (!page.loaded() || !page.scope().equals(first.scope()) || page.total() != total || !validPage(page, offset, total))
                    return new ClubLookup(null, "A lista de clubes mudou durante a consulta; atualize e tente novamente.", null);
                page.items().stream().map(GameAccess.ClubView::club).filter(GameAccess.ClubSummary::human).forEach(human::add);
            }
            if (human.isEmpty()) return new ClubLookup(null, "Nenhum clube controlado encontrado.", first.scope());
            if (human.size() != 1) return new ClubLookup(null, "Foram encontrados " + human.size() + " clubes controlados; esperado exatamente um.", first.scope());
            return new ClubLookup(human.get(0), "Clube fixo identificado.", first.scope());
        } catch (GameQueryException failure) {
            return new ClubLookup(null, failure.reason() + " — " + failure.getMessage(), null);
        } catch (RuntimeException failure) {
            return new ClubLookup(null, failure.getClass().getSimpleName() + " — " + failure.getMessage(), null);
        }
    }

    private static boolean validPage(GameAccess.ScopedClubPage page, int offset, int total) {
        int expected = Math.min(CLUB_PAGE_SIZE, Math.max(0, total - offset));
        return page.offset() == offset && page.items().size() == expected;
    }

    private void refreshCurrentClub() {
        reloadData();
    }

    private static String clubText(GameAccess.ClubSummary club) {
        return club.id() == -1 ? club.name() + " (ID indisponivel; nome tambem conferido)" : club.name() + " (ID " + club.id() + ")";
    }

    private void importLogo() {
        var chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("Imagens PNG ou JPEG", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            logoBytes = SponsorLogo.importPng(chooser.getSelectedFile().toPath());
            var image = SponsorLogo.decode(logoBytes);
            logoLabel.setText(image.getWidth() + " x " + image.getHeight() + " px / " + logoBytes.length + " bytes; copia incorporada");
            logoLabel.setIcon(SponsorLogo.fixedPreview(logoBytes));
        } catch (IOException failure) { showError("Logo recusada", failure); }
    }

    private void create() {
        try {
            GameAccess.ClubSummary club = fixedClub;
            if (!loaded) throw new IllegalStateException("Aguarde a leitura inicial do painel.");
            if (club == null) throw new IllegalStateException(clubMessage + " O cadastro esta bloqueado.");
            CareerDataAccess.Scope scope = requireCareerScope();
            long base = amount(baseAmount.getText(), "valor base");
            if (base <= 0) throw new IllegalArgumentException("O valor base precisa ser positivo para uma proposta de ativacao unica.");
            var item = new SponsorContract(UUID.randomUUID(), careerProfile(scope), club.id(), club.name(), sponsor.getText(), base,
                    SponsorContract.Cadence.ON_ACTIVATION, 0, false, 0, namingRights.isSelected(),
                    namingRights.isSelected() ? stadiumName.getText() : "", logoBytes, false, message.getText());
            String confirm = "Salvar somente uma configuracao para esta carreira?\n\n" + item.sponsorName()
                    + "\nCredito base proposto: " + item.baseAmount() + " (uma vez, se a ativacao for liberada)"
                    + "\nClube: " + club.name() + " / ID " + club.id() + "\n\n"
                     + "Estadio ao receber: " + (item.namingRights() ? item.stadiumName() : "manter nome atual")
                    + "\nSalvar configuracao NAO paga. Ativar envia a mensagem; receber na central aplica o credito e o estadio.";
            if (SponsorDialogs.showConfirmDialog(root, confirm, "Salvar configuracao por carreira", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            run("Revalidando carreira/clube e salvando sidecar do mod...", () -> {
                requireSameCareerScope(scope);
                requireSameFixedClub(context.game(), club);
                var current = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
                var next = SponsorModel.add(current, item);
                careerData.write(scope, "sponsor-config.bin", SponsorStore.encode(next));
                refreshStates(scope, next);
                return new Stored(next, banner.featured(), java.util.Optional.of(scope), legacyCount, lookupClub(context.game()));
            }, this::installContracts);
        } catch (Exception failure) { showError("Configuracao nao criada", failure); }
    }

    private static String careerProfile(CareerDataAccess.Scope scope) { return "career-" + scope.career() + "-" + scope.binding(); }
    private CareerDataAccess.Scope requireCareerScope() throws IOException {
        CareerDataAccess.Scope expected = dataScope.orElseThrow(() -> new IOException(
                "Selecione explicitamente uma associacao no painel Carreira e diarios antes de gravar dados por carreira."));
        return expected;
    }
    private void requireSameCareerScope(CareerDataAccess.Scope expected) throws IOException {
        var current = careerData.current();
        if (current.isEmpty() || !current.orElseThrow().equals(expected))
            throw new IOException("A carreira/revisao ou selecao mudou. Verifique Carreira e diarios e recarregue os dados.");
    }
    private static void requireSameFixedClub(GameAccess game, GameAccess.ClubSummary expected) throws IOException {
        var current = lookupClub(game).club();
        if (current == null || current.id() != expected.id() || !current.name().equals(expected.name()))
            throw new IOException("O clube controlado mudou ou esta ambiguo; atualize o clube fixo e tente novamente.");
    }

    private void archive() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visible.size()) { status.setText("Selecione uma configuracao deste clube."); return; }
        var item = visible.get(row);
        if (item.ended()) { status.setText("Configuracao ja arquivada."); return; }
        if (SponsorDialogs.showConfirmDialog(root, "Arquivar apenas esta configuracao?\n" + item.sponsorName()
                + "\nNenhum pagamento/estadio esta ativo ou sera alterado.", "Arquivar configuracao", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        run("Arquivando configuracao...", () -> {
            var scope = requireCareerScope();
            if (sponsorAccess.state(scope, item.id()) != SponsorAccess.State.NOT_ACTIVATED)
                throw new IOException("Contrato com tentativa financeira retida; nao arquivar nem repetir credito. O estadio nao sera restaurado automaticamente.");
            var current = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
            var next = SponsorModel.end(current, item.id(), item.profile());
            careerData.write(scope, "sponsor-config.bin", SponsorStore.encode(next));
            return new Stored(next, banner.featured(), java.util.Optional.of(scope), legacyCount, lookupClub(context.game()));
        }, this::installContracts);
    }

    private void featureSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visible.size()) { status.setText("Selecione uma configuracao deste clube."); return; }
        var item = visible.get(row);
        if (item.ended()) { status.setText("Configuracao arquivada nao pode ser exibida."); return; }
        if (SponsorDialogs.showConfirmDialog(root, "Exibir a logo de " + item.sponsorName() + " no topo da tela do clube?\n\n"
                + "Isto altera somente a exibicao visual do mod. NAO ativa um contrato, nao paga o valor base e nao altera o save.",
                "Confirmar prévia visual", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE) != JOptionPane.OK_OPTION) return;
        run("Salvando selecao de logo...", () -> {
            var scope = requireCareerScope();
            var current = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
            boolean stillPresent = current.stream().anyMatch(value -> value.id().equals(item.id()) && !value.ended()
                    && value.clubId() == item.clubId() && value.clubName().equals(item.clubName()));
            if (!stillPresent) throw new IOException("A configuracao mudou; recarregue antes de selecionar a logo.");
            careerData.write(scope, "sponsor-feature.bin", SponsorFeatureStore.encode(java.util.Optional.of(item.id())));
            return new Stored(all, java.util.Optional.of(item.id()), java.util.Optional.of(scope), legacyCount, lookupClub(context.game()));
        }, this::installContracts);
    }

    private void importLegacy() {
        if (legacyCount < 0) { showError("Configuracao global antiga invalida", new IOException("Dados legados preservados; corrija ou arquive manualmente antes de importar.")); return; }
        if (legacyCount == 0) { status.setText("Nenhuma configuracao global antiga encontrada."); return; }
        CareerDataAccess.Scope scope;
        try { scope = requireCareerScope(); }
        catch (IOException blocked) { showError("Carreira nao selecionada", blocked); return; }
        if (SponsorDialogs.showConfirmDialog(root, "Importar " + legacyCount + " configuracao(oes) antigas para somente esta carreira?\n\n"
                + "A importacao copia metadados/logos. NAO ativa contrato nem credita valores. Os dados globais originais serao preservados.",
                "Importacao explicita sem pagamentos", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        run("Importando configuracoes antigas para a carreira selecionada...", () -> {
            requireSameCareerScope(scope);
            var legacy = SponsorStore.decode(context.readData("sponsor-config.bin"));
            var current = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
            var next = new java.util.ArrayList<>(current);
            for (var old : legacy) {
                var scoped = new SponsorContract(old.id(), careerProfile(scope), old.clubId(), old.clubName(), old.sponsorName(),
                        old.baseAmount(), old.cadence(), old.intervalDays(), old.bonusEnabled(), old.homeWinBonus(),
                        old.namingRights(), old.stadiumName(), old.logoPng(), old.ended(), old.message());
                next = new java.util.ArrayList<>(SponsorModel.add(next, scoped));
            }
            careerData.write(scope, "sponsor-config.bin", SponsorStore.encode(next));
            return new Stored(next, SponsorFeatureStore.decode(careerData.read(scope, "sponsor-feature.bin")),
                    java.util.Optional.of(scope), legacy.size(), lookupClub(context.game()));
        }, this::installContracts);
    }

    private void reloadData() {
        run("Lendo dados desta carreira...", this::readCurrent, this::installContracts);
    }
    private Stored readCurrent() throws IOException {
        var selected = careerData.current(); int legacy = legacyCount(); var lookup = lookupClub(context.game());
        if (selected.isEmpty()) { activationStates = java.util.Map.of(); return new Stored(List.of(), java.util.Optional.empty(), selected, legacy, lookup); }
        var scope = selected.orElseThrow(); var contracts = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
        refreshStates(scope, contracts);
        return new Stored(contracts, SponsorFeatureStore.decode(careerData.read(scope, "sponsor-feature.bin")), selected, legacy, lookup);
    }
    private void refreshStates(CareerDataAccess.Scope scope, List<SponsorContract> contracts) throws IOException {
        var states = new java.util.HashMap<UUID, SponsorAccess.State>();
        for (var contract : contracts) states.put(contract.id(), sponsorAccess.state(scope, contract.id()));
        activationStates = java.util.Map.copyOf(states);
    }
    private void endSelected() {
        int row = table.getSelectedRow(); if (row < 0 || row >= visible.size()) return;
        var item = visible.get(row);
        if (activationStates.get(item.id()) == SponsorAccess.State.PENDING_RECEIPT) {
            if (SponsorDialogs.showConfirmDialog(root, "Cancelar a mensagem pendente de " + item.sponsorName()
                    + "? Nenhum dinheiro sera recebido.", "Cancelar patrocinio pendente", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            run("Cancelando mensagem sem pagamento...", () -> {
                sponsorAccess.cancelPending(requireCareerScope(), item.id()); return readCurrent();
            }, this::installContracts);
            return;
        }
        run("Revisando encerramento sem efeitos...", () -> sponsorAccess.quoteEnd(requireCareerScope(), item.id()), quote ->
                javax.swing.SwingUtilities.invokeLater(() -> confirmEnding(item, quote)));
    }
    private void confirmEnding(SponsorContract item, SponsorAccess.EndQuote quote) {
        if (closed) return;
        String message = "ENCERRAR PATROCINIO?\n" + quote.club() + " — " + item.sponsorName()
                + "\nEstadio atual: " + quote.stadiumCurrent() + "\nRestaurar nome original: " + quote.stadiumOriginal()
                + "\nSaldo mantido: " + quote.balance() + "\nA logo sera retirada do topo. Nenhum credito ou desconto."
                + (quote.changedSinceActivation() ? "\nATENCAO: o nome atual difere do nome aplicado pelo patrocinio; confirmar sobrescreve essa alteracao." : "")
                + "\nO contrato ficara ENCERRADO e nunca podera receber novamente. Salve pelo jogo depois.";
        if (SponsorDialogs.showConfirmDialog(root, message, "Confirmar encerramento", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        var states = new java.util.HashMap<>(activationStates); states.put(item.id(), SponsorAccess.State.REVIEW_REQUIRED);
        activationStates = java.util.Map.copyOf(states);
        run("Encerrando sem alterar dinheiro...", () -> {
            var result = sponsorAccess.end(quote);
            if (result.state() == SponsorAccess.State.ENDED) {
                try {
                    var scope = requireCareerScope();
                    var contracts = SponsorStore.decode(careerData.read(scope, "sponsor-config.bin"));
                    var next = SponsorModel.end(contracts, item.id(), item.profile());
                    careerData.write(scope, "sponsor-config.bin", SponsorStore.encode(next));
                    var featured = SponsorFeatureStore.decode(careerData.read(scope, "sponsor-feature.bin"));
                    if (featured.filter(item.id()::equals).isPresent())
                        careerData.write(scope, "sponsor-feature.bin", SponsorFeatureStore.encode(java.util.Optional.empty()));
                } catch (IOException metadataFailure) {
                    context.logger().warning("Encerramento registrado; projecao visual sera corrigida ao recarregar: " + metadataFailure.getMessage());
                }
            }
            return result;
        }, result -> {
            var next = new java.util.HashMap<>(activationStates); next.put(item.id(), result.state()); activationStates = java.util.Map.copyOf(next);
            if (result.state() == SponsorAccess.State.ENDED) {
                all = all.stream().map(c -> c.id().equals(item.id()) ? c.withEnded(true) : c).toList();
                banner.updateContracts(all);
                if (banner.featured().filter(item.id()::equals).isPresent()) banner.featured(java.util.Optional.empty());
            }
            SponsorDialogs.showMessageDialog(root, result.message(), "Resultado do encerramento", result.state() == SponsorAccess.State.ENDED
                    ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        });
    }
    private void activateSelected() {
        int row = table.getSelectedRow(); if (row < 0 || row >= visible.size()) return;
        var item = visible.get(row);
        String confirmation = "Ativar " + item.sponsorName() + " SEM receber agora?\nValor pendente: " + item.baseAmount()
                + "\n\nA mensagem aparecera na Central de Mensagens do jogo."
                + "\nO credito e o nome do estadio so serao aplicados ao clicar em Receber patrocinio."
                + "\n\nTexto:\n" + item.message();
        if (SponsorDialogs.showConfirmDialog(root, confirmation, "Enviar mensagem de patrocinio", JOptionPane.OK_CANCEL_OPTION,
                 JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        var pendingStates = new java.util.HashMap<>(activationStates);
        pendingStates.put(item.id(), SponsorAccess.State.REVIEW_REQUIRED); activationStates = java.util.Map.copyOf(pendingStates);
        run("Publicando mensagem sem credito...", () -> {
            var result = sponsorAccess.publish(requireCareerScope(), item.id(), item.clubId(), item.clubName(), item.sponsorName(),
                    Math.toIntExact(item.baseAmount()), item.namingRights() ? item.stadiumName() : "", item.message());
            if (result.state() == SponsorAccess.State.PENDING_RECEIPT && item.logoPng().length > 0) {
                 try { careerData.write(requireCareerScope(), "sponsor-feature.bin", SponsorFeatureStore.encode(java.util.Optional.of(item.id()))); }
                catch (IOException logoFailure) { context.logger().warning("Mensagem publicada; logo nao foi selecionada: " + logoFailure.getMessage()); }
            }
            return result;
        }, result -> {
            var next = new java.util.HashMap<>(activationStates); next.put(item.id(), result.state()); activationStates = java.util.Map.copyOf(next);
            if (result.state() == SponsorAccess.State.PENDING_RECEIPT && item.logoPng().length > 0) banner.featured(java.util.Optional.of(item.id()));
            SponsorDialogs.showMessageDialog(root, result.message(), "Mensagem publicada", result.state() == SponsorAccess.State.PENDING_RECEIPT
                    ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        });
    }

    private int legacyCount() {
        try { return SponsorStore.decode(context.readData("sponsor-config.bin")).size(); }
        catch (IOException invalidLegacy) {
            context.logger().warning("Legacy sponsor settings preserved but not importable: " + invalidLegacy.getMessage());
            return -1;
        }
    }

    private <T> void run(String message, Callable<T> operation, Consumer<T> success) {
        if (busy || closed) return;
        busy = true; setControls(false); status.setText(message);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                try {
                    T result = get();
                    if (!closed) success.accept(result);
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    if (!closed) { status.setText("Falha: se uma ativacao foi confirmada, nao repetir; revise o historico."); showError("Operacao exige revisao", cause); }
                } finally { busy = false; if (!closed) { setControls(true); render(); } }
            }
        }.execute();
    }

    private void installContracts(Stored stored) {
        all = projectEnded(stored.contracts()); dataScope = stored.dataScope(); legacyCount = stored.legacyCount();
        fixedClub = stored.club().club(); clubMessage = stored.club().message(); gameScope = java.util.Optional.ofNullable(stored.club().scope());
        banner.replace(all, stored.featured(), gameScope, dataScope); loaded = true;
        clubLabel.setText(fixedClub == null ? clubMessage : clubText(fixedClub));
    }
    private List<SponsorContract> projectEnded(List<SponsorContract> contracts) {
        return contracts.stream().map(c -> activationStates.get(c.id()) == SponsorAccess.State.ENDED ? c.withEnded(true) : c).toList();
    }
    private void render() {
        tableModel.setRowCount(0); logoStrip.removeAll();
        visible = fixedClub == null ? List.of() : SponsorModel.forClub(all, fixedClub.id(), fixedClub.name());
        for (var item : visible) {
            tableModel.addRow(new Object[] {item.sponsorName(), item.baseAmount(), cadenceText(item),
                    item.bonusEnabled() ? item.homeWinBonus() : "—", item.namingRights() ? item.stadiumName() : "—",
                      activationStates.get(item.id()) == SponsorAccess.State.ENDED ? "ENCERRADO / CANCELADO" : item.ended() ? "ARQUIVADO" : stateLabel(activationStates.getOrDefault(item.id(), SponsorAccess.State.NOT_ACTIVATED))});
            if (!item.ended() && item.logoPng().length > 0 && logoStrip.getComponentCount() < 12) {
                try {
                    var image = SponsorLogo.decode(item.logoPng());
                    var preview = new JLabel(item.sponsorName(), new javax.swing.ImageIcon(image.getScaledInstance(120, 60, Image.SCALE_SMOOTH)), JLabel.CENTER);
                    preview.setVerticalTextPosition(JLabel.BOTTOM); preview.setHorizontalTextPosition(JLabel.CENTER); logoStrip.add(preview);
                } catch (IOException invalid) { logoStrip.add(new JLabel(item.sponsorName() + " (logo invalida)")); }
            }
        }
        if (logoStrip.getComponentCount() == 0) logoStrip.add(themeLabel("Nenhuma logo configurada para este clube"));
        if (dataScope.isEmpty()) status.setText("Dados por carreira: inicie com --probe-career-hooks e selecione uma associacao em Carreira e diarios. Nada global e importado automaticamente.");
        else if (fixedClub == null) status.setText(clubMessage + " Cadastro desabilitado ate haver exatamente um clube controlado.");
        else status.setText(visible.size() + " patrocinador(es) para " + fixedClub.name() + ". Receba na Central de Mensagens; salve pelo jogo depois do recebimento.");
        if (legacyCount < 0) status.setText(status.getText() + " Configuracao global antiga invalida/preservada; importacao desabilitada.");
        setControls(!busy); root.revalidate(); root.repaint();
    }

    private JLabel themeLabel(String text) { return new JLabel(text); }
    private void setControls(boolean enabled) {
        boolean hasClub = enabled && loaded && fixedClub != null;
        boolean hasCareer = dataScope.isPresent();
        refreshClub.setEnabled(enabled); bind.setEnabled(enabled); chooseLogo.setEnabled(hasClub); clearLogo.setEnabled(hasClub); message.setEnabled(hasClub); add.setEnabled(hasClub && hasCareer); reload.setEnabled(enabled);
        importLegacy.setEnabled(enabled && hasCareer && legacyCount > 0 && all.isEmpty());
        updateSelectionButtons(); sponsor.setEnabled(hasClub); baseAmount.setEnabled(hasClub);
        cadence.setEnabled(hasClub); intervalDays.setEnabled(hasClub && cadence.getSelectedItem() == SponsorContract.Cadence.EVERY_N_DAYS);
        bonus.setEnabled(hasClub); bonusAmount.setEnabled(hasClub && bonus.isSelected());
        namingRights.setEnabled(hasClub); stadiumName.setEnabled(hasClub && namingRights.isSelected());
    }

    private void updateSelectionButtons() {
        int row = table.getSelectedRow();
        boolean hasSelection = !busy && row >= 0 && row < visible.size();
        end.setEnabled(hasSelection && dataScope.isPresent()
                && activationStates.getOrDefault(visible.get(row).id(), SponsorAccess.State.REVIEW_REQUIRED) == SponsorAccess.State.NOT_ACTIVATED);
        terminate.setEnabled(hasSelection && dataScope.isPresent() && !visible.get(row).ended()
                && (activationStates.getOrDefault(visible.get(row).id(), SponsorAccess.State.REVIEW_REQUIRED) == SponsorAccess.State.APPLIED_IN_MEMORY
                    || activationStates.get(visible.get(row).id()) == SponsorAccess.State.PENDING_RECEIPT));
        feature.setEnabled(hasSelection && dataScope.isPresent() && !visible.get(row).ended() && visible.get(row).logoPng().length > 0);
        activate.setEnabled(hasSelection && dataScope.isPresent() && !visible.get(row).ended()
                && activationStates.getOrDefault(visible.get(row).id(), SponsorAccess.State.REVIEW_REQUIRED) == SponsorAccess.State.NOT_ACTIVATED);
    }
    private static String stateLabel(SponsorAccess.State state) {
        return switch (state) {
            case NOT_ACTIVATED -> "CONFIGURADO — nao pago";
            case PENDING_RECEIPT -> "ATIVO — receber na Central de Mensagens";
            case APPLIED_IN_MEMORY -> "CREDITO REGISTRADO — nao repetir";
            case NO_EFFECT -> "SEM EFEITO — tentativa retida";
            case REVIEW_REQUIRED -> "INCERTO — nao repetir";
            case ENDED -> "ENCERRADO — credito preservado";
        };
    }

    private static long amount(String text, String label) {
        long value = Long.parseLong(text.trim());
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException(label + " deve estar entre zero e " + Integer.MAX_VALUE + " unidades do jogo");
        return value;
    }
    private static String cadenceText(SponsorContract item) {
        return item.cadence() == SponsorContract.Cadence.EVERY_N_DAYS
                ? "A cada " + item.intervalDays() + " dias (proposta)" : item.cadence().toString();
    }
    private static void showError(String title, Throwable failure) {
        SponsorDialogs.showMessageDialog(null, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), title, JOptionPane.WARNING_MESSAGE);
    }
    @Override public JComponent component() { return root; }
    @Override public void close() { closed = true; all = List.of(); visible = List.of(); root.removeAll(); }
}
