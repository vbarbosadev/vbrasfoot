package br.exemplo.loanwindow;

import br.vbrasfoot.api.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** SDK-only author example. No reflection, original classes, clock edits or automatic acceptance. */
public final class LoanWindowMod implements Mod {
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean closed;
    private ModContext context;
    public void initialize(ModContext context) {
        this.context = context;
        context.registerScopedAction("loan", "Exemplo: solicitar emprestimo", () -> start(false));
        context.registerScopedAction("windows", "Exemplo: janela de emprestimos", () -> start(true));
    }
    private void start(boolean windows) {
        if (closed || !busy.compareAndSet(false, true)) return;
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                var loans = context.loans(); var scope = loans.bindCareer();
                return windows ? configure(scope) : request(loans, scope);
            }
            protected void done() {
                try { String message = get(); if (!closed && message != null) JOptionPane.showMessageDialog(null, message); }
                catch (Exception e) { if (!closed) JOptionPane.showMessageDialog(null, "Nao repetir automaticamente.\n" + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())); }
                finally { busy.set(false); }
            }
        }.execute();
    }
    private String configure(CareerDataAccess.Scope scope) throws Exception {
        var service = context.transferWindows(); var before = service.current(scope);
        int year = context.game().scopedCalendar().date().year();
        var rules = ui(() -> {
            String start = JOptionPane.showInputDialog(null, "Inicio (AAAA-MM-DD). Somente emprestimos pelo loader.", year + "-01-01");
            if (start == null) return null;
            String end = JOptionPane.showInputDialog(null, "Fim inclusivo (AAAA-MM-DD). O calendario NAO muda.", year + "-01-31");
            if (end == null) return null;
            var value = new TransferWindows.Rules(true, List.of(new TransferWindows.Period(LocalDate.parse(start), LocalDate.parse(end))));
            return JOptionPane.showConfirmDialog(null, "Aplicar janela de EMPRESTIMOS de " + start + " a " + end
                    + "?\nCompras, agentes livres e IA NAO sao controlados.\nRegra persistida nos dados do loader, independentemente do save.",
                    "Confirmar politica", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION ? value : null;
        });
        if (rules == null) return null;
        if (closed) return null;
        service.configure(scope, before.revision(), rules); return "Janela de emprestimos configurada. Data do jogo intacta.";
    }
    private String request(LoanAccess loans, CareerDataAccess.Scope scope) throws Exception {
        String filter = ui(() -> JOptionPane.showInputDialog("Nome do clube de origem (use filtro especifico):"));
        if (filter == null || filter.isBlank()) return null;
        var origins = context.game().scopedClubs(filter, 0, 500).items();
        if (origins.isEmpty()) return "Nenhum clube encontrado.";
        int originIndex = choose("Clube de origem (ate 500 resultados)", origins.stream().map(v -> v.club().name()).toArray(String[]::new));
        if (originIndex < 0) return null;
        var origin = origins.get(originIndex);
        var humans = new ArrayList<GameAccess.ClubView>();
        var first = context.game().scopedClubs("", 0, 500);
        if (first.total() > 10000) return "Inventario grande demais para este exemplo.";
        for (int offset = 0; offset < first.total(); offset += 500) {
            var page = offset == 0 ? first : context.game().scopedClubs("", offset, 500);
            if (!page.scope().equals(first.scope())) throw new IllegalStateException("Carreira mudou");
            page.items().stream().filter(v -> v.club().human()).forEach(humans::add);
        }
        if (humans.size() != 1) return "Este exemplo exige exatamente um clube humano.";
        var destination = humans.get(0);
        var players = context.game().scopedPlayersWithReferences(origin.reference(), 0, 500).items();
        int playerIndex = choose("Jogador", players.stream().map(v -> v.player().name()).toArray(String[]::new));
        if (playerIndex < 0) return null;
        var quote = loans.quote(scope, players.get(playerIndex).reference(), origin.reference(), destination.reference());
        boolean confirmed = ui(() -> JOptionPane.showConfirmDialog(null, quote.player() + "\n" + quote.origin() + " -> " + quote.destination()
                + "\nTaxa: " + quote.fee() + " / Dias: " + quote.days() + "\nConfirmar uma unica tentativa? Salve pelo jogo depois.",
                "Emprestimo", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
        if (!confirmed || closed) return null;
        return loans.accept(quote).message();
    }
    private int choose(String title, String[] values) throws Exception {
        if (values.length == 0) return -1;
        return ui(() -> {
            var choices = new JComboBox<>(values);
            return JOptionPane.showConfirmDialog(null, choices, title, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION ? choices.getSelectedIndex() : -1;
        });
    }
    private static <T> T ui(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        var future = new FutureTask<>(task); SwingUtilities.invokeLater(future); return future.get();
    }
    public void shutdown() { closed = true; }
}
