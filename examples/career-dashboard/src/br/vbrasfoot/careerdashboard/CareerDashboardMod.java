package br.vbrasfoot.careerdashboard;

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

public final class CareerDashboardMod implements Mod {
    private AutoCloseable registration;
    private volatile boolean stopped;
    private DashboardWindow window; // EDT only

    @Override public void initialize(ModContext context) {
        registration = context.registerScopedAction("dashboard", "Painel de carreira (data e saldo)", () -> {
            if (stopped) return;
            if (SwingUtilities.isEventDispatchThread()) open(context);
            else SwingUtilities.invokeLater(() -> open(context));
        });
    }
    private void open(ModContext context) {
        if (stopped) return;
        if (window == null || !window.isDisplayable()) window = new DashboardWindow(context.game());
        window.setVisible(true); window.toFront();
    }
    @Override public void shutdown() throws Exception {
        stopped = true;
        try { if (registration != null) registration.close(); }
        finally {
            registration = null;
            Runnable dispose = () -> {
                if (window != null) { window.clear(); window.dispose(); window = null; }
            };
            if (SwingUtilities.isEventDispatchThread()) dispose.run();
            else SwingUtilities.invokeLater(dispose); // Never wait for EDT.
        }
    }

    private static final class DashboardWindow extends JDialog {
        private final DashboardModel model;
        private final JTextField filter = new JTextField(20);
        private final JComboBox<String> clubs = new JComboBox<>();
        private final JTextArea output = new JTextArea();
        private final JLabel status = new JLabel("Carregue uma carreira e clique em Atualizar clubes.");
        private final JButton previous = new JButton("Clubes <"), next = new JButton("Clubes >");
        private final JButton read = new JButton("Consultar data e saldo");
        private boolean updating;

        DashboardWindow(GameAccess game) {
            super((Window) null, "Painel de carreira — leitura manual SDK 0.5.0");
            model = new DashboardModel(game);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { clear(); }
            });
            JPanel top = new JPanel(new GridLayout(3, 1));
            top.add(new JLabel("Somente leitura: clubes com escopo, data atual e saldo. Sem agenda completa ou historico."));
            top.add(new JLabel("Capacidades: query.scoped | query.calendar.current | query.club.balance; game.read + ui.register"));
            JPanel buttons = new JPanel();
            JButton refresh = new JButton("Atualizar clubes (limpa selecao)"), search = new JButton("Filtrar");
            refresh.setName("career-dashboard.refresh"); filter.setName("career-dashboard.filter");
            clubs.setName("career-dashboard.clubs"); read.setName("career-dashboard.read");
            output.setName("career-dashboard.output"); status.setName("career-dashboard.status");
            buttons.add(refresh); buttons.add(filter); buttons.add(search); buttons.add(previous); buttons.add(next);
            top.add(buttons); add(top, BorderLayout.NORTH);
            JPanel center = new JPanel(new BorderLayout());
            JPanel choice = new JPanel(new BorderLayout()); choice.add(clubs); choice.add(read, BorderLayout.EAST);
            center.add(choice, BorderLayout.NORTH);
            output.setEditable(false); center.add(new JScrollPane(output)); add(center);
            JPanel bottom = new JPanel(new GridLayout(2, 1));
            bottom.add(new JLabel("Unidades monetarias do jogo, nao R$/centavos. Escopo nao e identidade nem revisao atomica."));
            bottom.add(status); add(bottom, BorderLayout.SOUTH);
            refresh.addActionListener(e -> { clear(); query(() -> load(0)); });
            search.addActionListener(e -> query(() -> load(0)));
            filter.addActionListener(e -> search.doClick());
            previous.addActionListener(e -> query(() -> load(Math.max(0, model.page().offset() - DashboardModel.PAGE_SIZE))));
            next.addActionListener(e -> query(() -> load(model.page().offset() + DashboardModel.PAGE_SIZE)));
            clubs.addActionListener(e -> {
                if (!updating) {
                    model.clearReading(); output.setText(""); read.setEnabled(clubs.getSelectedIndex() >= 0);
                    status.setText("Escolha o clube e consulte manualmente; trocar selecao nao consulta saldo.");
                }
            });
            read.addActionListener(e -> query(() -> {
                var value = model.read(clubs.getSelectedIndex());
                output.setText("Clube observado: " + value.clubName()
                        + "\nData exibida pelo SDK: " + value.displayedDate()
                        + "\nCampos gregorianos AD: ano=" + value.year() + "; mes=" + value.month() + "; dia=" + value.day()
                        + "\nIndice observado do dia: " + value.dayIndex()
                        + "\nTimezone de origem: " + value.sourceTimeZoneId()
                        + "\nTimezone de exibicao: " + value.displayTimeZoneId()
                        + "\nSaldo (unidades monetarias do jogo; long): " + value.balance());
                status.setText("Leitura manual concluida. Mesmo escopo, nao snapshot atomico; consulte novamente para reler.");
            }));
            clear(); setPreferredSize(new Dimension(1000, 480)); pack(); setLocationRelativeTo(null);
        }
        void clear() {
            model.clear(); updating = true;
            try { clubs.removeAllItems(); } finally { updating = false; }
            output.setText(""); previous.setEnabled(false); next.setEnabled(false); read.setEnabled(false);
        }
        private void query(Runnable action) {
            try { action.run(); }
            catch (RuntimeException failure) {
                clear();
                String reason = failure instanceof GameQueryException gameFailure ? gameFailure.reason().name()
                        : failure instanceof UnsupportedOperationException ? "UNSUPPORTED_DATA (SDK/loader antigo ou dado indisponivel)"
                        : failure.getClass().getSimpleName();
                status.setText(reason + " — dados/handles limpos. Clique em Atualizar clubes; sem fallback.");
            }
        }
        private void load(int offset) {
            var page = model.clubs(filter.getText(), offset);
            updating = true;
            try {
                clubs.removeAllItems();
                if (page.loaded()) for (var club : page.items()) clubs.addItem(club.club().name());
                clubs.setSelectedIndex(-1);
            } finally { updating = false; }
            output.setText(""); read.setEnabled(false);
            previous.setEnabled(page.loaded() && page.offset() > 0);
            next.setEnabled(page.loaded() && (long) page.offset() + page.items().size() < page.total());
            status.setText(page.loaded() ? "Clubes: " + page.items().size() + " de " + page.total()
                    + "; offset " + page.offset() + ". Escolha clube e consulte."
                    : "NO_CAREER — nenhuma carreira ativa; carregue e atualize manualmente.");
        }
    }
}
