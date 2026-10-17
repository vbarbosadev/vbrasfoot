package br.exemplo.consulta;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;
import javax.swing.JOptionPane;

/** Only public SDK imports: no game classes, reflection, classloader or save access. */
public final class ReadOnlyMod implements Mod {
    private AutoCloseable registration;

    @Override public void initialize(ModContext context) {
        registration = context.registerScopedAction("clubs", "Exemplo: consultar clubes", () ->
                showReport(report(context.game())));
    }

    private static void showReport(String report) {
        var text = new javax.swing.JTextArea(report, 18, 52);
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setCaretPosition(0);
        var scroll = new javax.swing.JScrollPane(text);
        var usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        scroll.setPreferredSize(new java.awt.Dimension(Math.max(240, Math.min(600, usable.width - 100)),
                Math.max(150, Math.min(360, usable.height - 150))));
        JOptionPane.showMessageDialog(null, scroll, "Meu primeiro mod", JOptionPane.INFORMATION_MESSAGE);
    }

    static String report(GameAccess game) {
        try {
            var page = game.scopedClubs("", 0, 50);
            if (!page.loaded()) return "Carregue uma carreira no jogo antes de consultar.";
            StringBuilder text = new StringBuilder("Clubes encontrados: " + page.total() + "\nExibindo ate 50 nesta consulta.\n");
            for (var item : page.items()) text.append(item.club().name()).append(item.club().human() ? " (seu clube)" : "").append('\n');
            var selected = page.items().stream().filter(c -> c.club().human()).findFirst()
                    .or(() -> page.items().stream().findFirst());
            if (selected.isPresent()) {
                var view = selected.get();
                var players = game.scopedPlayers(view.reference(), 0, 20);
                text.append("\nJogadores de ").append(view.club().name()).append(" (ate 20 de ").append(players.total()).append("):\n");
                for (var player : players.items()) text.append(player.name()).append(" — ").append(player.age()).append(" anos\n");
            }
            return text.toString();
        } catch (GameQueryException stale) {
            return "Consulta recusada: " + stale.reason() + ". Abra a acao novamente para atualizar.";
        }
    }

    @Override public void shutdown() throws Exception {
        if (registration != null) { registration.close(); registration = null; }
    }
}
