package br.vbrasfoot.playercomparator;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.ModContext;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/** No windows, game implementation, reflection or test framework needed. */
public final class ComparisonModelTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        FakeGame game = new FakeGame();
        ComparisonModel model = new ComparisonModel(game);
        var leftClubs = model.clubs(0, "Clube", 0);
        check(leftClubs.items().size() == 100 && leftClubs.total() == 201, "club page bounded");
        check(game.lastFilter.equals("Clube"), "filter passed to SDK");
        var rightClubs = model.clubs(1, "", 100);
        check(rightClubs.offset() == 100 && rightClubs.items().size() == 100, "club offset passed");
        var last = model.clubs(1, "", 200);
        check(last.items().size() == 1 && last.total() == 201, "last partial club page");
        var empty = model.clubs(1, "missing", 0);
        check(empty.items().isEmpty() && empty.total() == 0, "empty filtered clubs");
        model.clubs(1, "", 0);
        var club = leftClubs.items().get(0);
        var first = model.players(0, club, 0);
        check(first.items().size() == 100 && first.total() == 201, "player page bounded");
        model.select(0, 0);
        model.players(1, club, 100); model.select(1, 0);
        check(model.selection(0).player().name().equals("Player 0"), "first selection retained");
        check(model.selection(1).player().name().equals("Player 100"), "same club other player page");
        check(model.comparison().size() == 6, "only public fields and club context");
        check(model.comparison().get(2).label().equals("Idade (anos)"), "age unit");
        check(model.comparison().get(3).left().equals("99"), "unknown position left as SDK code");
        check(model.comparison().get(5).left().equals("1234"), "raw SDK value, no salary/currency inference");
        check(model.comparison().stream().noneMatch(r -> r.label().equals("ID")), "no persistent identity comparison");
        var other = model.clubs(1, "", 100).items().get(0);
        model.players(1, other, 200); model.select(1, 0);
        check(model.selection(1).club().equals("Clube 100"), "different club with duplicate public ID");
        check(model.selection(1).player().name().equals("Player 200"), "last player page");
        check(game.lastRef.equals(other.reference()), "opaque ClubRef, not club ID/index lookup");
        check(game.maxLimit == 100 && game.snapshotCalls == 0, "scoped bounded calls only");

        for (var reason : GameQueryException.Reason.values()) {
            model.clear();
            var view = model.clubs(0, "", 0).items().get(0);
            model.players(0, view, 0); model.select(0, 0);
            game.failure = reason;
            try { model.players(0, view, 0); throw new AssertionError("failure expected"); }
            catch (GameQueryException expected) { check(expected.reason() == reason, "failure reason preserved"); }
            check(model.selection(0) == null && model.comparison().isEmpty(), "failure invalidates selection");
            game.failure = null;
            try { model.players(0, view, 0); throw new AssertionError("old reference accepted"); }
            catch (GameQueryException expected) { check(expected.reason() == GameQueryException.Reason.EXPIRED_REFERENCE, "must refresh after failure"); }
            model.clubs(0, "", 0);
            game.failure = reason;
            try { model.clubs(0, "", 100); throw new AssertionError("club failure expected"); }
            catch (GameQueryException expected) { check(expected.reason() == reason, "club failure reason preserved"); }
            check(model.selection(0) == null && model.selection(1) == null, "club failure clears both sides");
            game.failure = null;
        }
        model.clear();
        var noLongerOnPage = model.clubs(0, "", 0).items().get(0);
        model.clubs(0, "", 100);
        try { model.players(0, noLongerOnPage, 0); throw new AssertionError("obsolete page handle accepted"); }
        catch (GameQueryException expected) { check(expected.reason() == GameQueryException.Reason.EXPIRED_REFERENCE, "handle must belong to current club page"); }
        model.clear();
        var old = model.clubs(0, "", 0).items().get(0);
        model.players(0, old, 0); model.select(0, 0);
        game.scope = new GameAccess.CareerScope("fake", 2);
        try { model.clubs(1, "", 0); throw new AssertionError("changed scope accepted"); }
        catch (GameQueryException expected) { check(expected.reason() == GameQueryException.Reason.STALE_SCOPE, "cross-page scope change"); }
        check(model.selection(0) == null, "scope change clears both sides");
        model.clubs(0, "", 0);
        game.playerScope = new GameAccess.CareerScope("fake", 3);
        try { model.players(0, model.clubs(0, "", 0).items().get(0), 0); throw new AssertionError("changed player scope accepted"); }
        catch (GameQueryException expected) { check(expected.reason() == GameQueryException.Reason.STALE_SCOPE, "player result scope validated"); }
        game.playerScope = null;
        game.loaded = false;
        check(!model.clubs(0, "", 0).loaded() && model.comparison().isEmpty(), "no career clears state");
        try { model.clubs(0, "", -1); throw new AssertionError("negative offset accepted"); }
        catch (IllegalArgumentException expected) { check(true, "negative paging rejected"); }

        FakeContext context = new FakeContext(game);
        PlayerComparatorMod mod = new PlayerComparatorMod();
        mod.initialize(context);
        check(context.registered && !context.closed, "scoped action registered without queries/windows");
        mod.shutdown(); mod.shutdown();
        check(context.closed && context.closeCount == 1, "action closed once, shutdown idempotent");
        SwingUtilities.invokeAndWait(context.action); // revoked/in-flight callback cannot open a headless window
        check(true, "late action after shutdown is inert");
        System.out.println("PASS: " + checks + " checks; headless model/lifecycle; only scoped queries");
    }
    private static final class FakeGame implements GameAccess {
        CareerScope scope = new CareerScope("fake", 1), playerScope;
        GameQueryException.Reason failure;
        boolean loaded = true;
        int maxLimit, snapshotCalls;
        String lastFilter;
        ClubRef lastRef;
        @Override public Snapshot snapshot() { snapshotCalls++; throw new AssertionError("global snapshot forbidden"); }
        @Override public ClubPage clubs(String f, int o, int l) { throw new AssertionError("unscoped query forbidden"); }
        @Override public PlayerPage players(int id, int o, int l) { throw new AssertionError("ID lookup forbidden"); }
        @Override public PlayerPage playersAt(int id, int o, int l) { throw new AssertionError("index lookup forbidden"); }
        private void query(int offset, int limit) {
            check(offset >= 0 && limit > 0 && limit <= 100, "query bounded");
            maxLimit = Math.max(maxLimit, limit);
            if (failure != null) throw new GameQueryException(failure, "fake failure");
        }
        @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
            query(offset, limit); lastFilter = filter;
            List<ClubView> clubs = new ArrayList<>();
            int total = !loaded || "missing".equals(filter) ? 0 : 201;
            for (int i = offset; i < Math.min(total, offset + limit); i++) {
                clubs.add(new ClubView(new ClubRef(scope, "token-" + i), new ClubSummary(i, 7, "Clube " + i, false, 201)));
            }
            return new ScopedClubPage(loaded, scope, clubs, offset, total);
        }
        @Override public ScopedPlayerPage scopedPlayers(ClubRef club, int offset, int limit) {
            query(offset, limit); lastRef = club;
            if (!club.scope().equals(scope)) throw new GameQueryException(GameQueryException.Reason.STALE_SCOPE, "fake stale");
            List<Player> players = new ArrayList<>();
            for (int i = offset; i < Math.min(201, offset + limit); i++) players.add(new Player(7, "Player " + i, 20 + i, 99, 80, 1234));
            return new ScopedPlayerPage(playerScope == null ? scope : playerScope, players, offset, 201);
        }
    }
    private static final class FakeContext implements ModContext {
        private final FakeGame game;
        boolean registered, closed;
        int closeCount;
        Runnable action;
        FakeContext(FakeGame game) { this.game = game; }
        @Override public GameAccess game() { return game; }
        @Override public Logger logger() { return Logger.getLogger("player-comparator-test"); }
        @Override public void registerAction(String id, String label, Runnable callback) { throw new AssertionError("unscoped registration"); }
        @Override public AutoCloseable registerScopedAction(String id, String label, Runnable callback) {
            registered = true; action = callback;
            return () -> { closed = true; closeCount++; };
        }
        @Override public AutoCloseable registerPanel(String id, String label, java.util.function.Function<br.vbrasfoot.api.UiTheme, br.vbrasfoot.api.ModPanel> factory) {
            return () -> { };
        }
        @Override public byte[] readData(String file) { throw new AssertionError("no persistence"); }
        @Override public void writeData(String file, byte[] data) { throw new AssertionError("no persistence"); }
    }
}
