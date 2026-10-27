package br.vbrasfoot.playerscout;

import br.vbrasfoot.api.*;
import java.util.*;

public final class ScoutModelTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    private static final class Game implements GameAccess {
        final CareerScope scope = new CareerScope("owned", 1); final ClubRef ref = new ClubRef(scope, "club");
        int clubCalls, detailCalls; boolean fail, wrong;
        public Snapshot snapshot() { throw new AssertionError("unbounded snapshot forbidden"); }
        public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
            check(limit == 50 && offset == 0); clubCalls++;
            return new ScopedClubPage(true, scope, List.of(new ClubView(ref, new ClubSummary(0, 1, "Owned", true, 1))), offset, 1);
        }
        public ScopedPlayerDetailsPage scopedPlayerDetails(ClubRef club, int offset, int limit) {
            check(club.equals(ref) && limit == 50); detailCalls++;
            if (fail) throw new GameQueryException(GameQueryException.Reason.STALE_SCOPE, "owned stale");
            return new ScopedPlayerDetailsPage(scope, wrong ? new ClubRef(scope, "other") : club,
                    offset == 0 ? List.of(new PlayerDetails(new Player(1, "Owned", 20, 2, 40, 10), Optional.empty())) : List.of(), offset, 1);
        }
    }
    private static final class Context implements ModContext {
        final Game game = new Game(); ModPanel panel; int closes;
        public GameAccess game() { return game; }
        public java.util.logging.Logger logger() { return java.util.logging.Logger.getAnonymousLogger(); }
        public void registerAction(String id, String label, Runnable action) { throw new AssertionError("panel only"); }
        public AutoCloseable registerPanel(String id, String label, java.util.function.Function<UiTheme, ModPanel> factory) {
            panel = factory.apply(null); return () -> { closes++; panel.close(); };
        }
        public byte[] readData(String file) { throw new AssertionError("no storage"); }
        public void writeData(String file, byte[] bytes) { throw new AssertionError("no storage"); }
    }
    private static javax.swing.JButton button(java.awt.Container parent, String text) {
        for (var item : parent.getComponents()) {
            if (item instanceof javax.swing.JButton b && b.getText().equals(text)) return b;
            if (item instanceof java.awt.Container nested) { var found = button(nested, text); if (found != null) return found; }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        Game game = new Game(); ScoutModel model = new ScoutModel(game);
        model.clubs("Owned", 0); check(game.clubCalls == 1 && game.detailCalls == 0);
        check(model.players(0, 0).items().get(0).skills().isEmpty());
        check(model.players(0, 50).items().isEmpty());
        game.fail = true; boolean refused = false;
        try { model.players(0, 0); } catch (GameQueryException expected) { refused = true; } check(refused);
        game.fail = false; int calls = game.detailCalls; refused = false;
        try { model.players(0, 0); } catch (IllegalStateException expected) { refused = true; } check(refused && game.detailCalls == calls);
        model.clubs("", 0); game.wrong = true; refused = false;
        try { model.players(0, 0); } catch (IllegalStateException expected) { refused = true; } check(refused);
        model.clear();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                Context context = new Context(); PlayerScoutMod mod = new PlayerScoutMod(); mod.initialize(context);
                var root = context.panel.component();
                check(context.game.clubCalls == 0 && context.game.detailCalls == 0);
                var refresh = button(root, "Buscar clubes"); refresh.doClick(); check(context.game.clubCalls == 1);
                mod.shutdown(); mod.shutdown(); check(context.closes == 1 && root.getComponentCount() == 0);
                refresh.doClick(); check(context.game.clubCalls == 1);
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        System.out.println("PASS: " + checks + " SDK-only scout checks; no game or persistence.");
    }
}
