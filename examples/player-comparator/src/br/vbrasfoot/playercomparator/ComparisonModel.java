package br.vbrasfoot.playercomparator;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import java.util.List;
import java.util.Objects;

/** Session-only public DTOs. No IDs are used to find or persist players. */
public final class ComparisonModel {
    public static final int PAGE_SIZE = 100;
    public record Selection(String club, GameAccess.Player player) { }
    public record Row(String label, String left, String right) { }
    private final GameAccess game;
    private GameAccess.CareerScope scope;
    private final Side[] sides = {new Side(), new Side()};

    private static final class Side {
        GameAccess.ScopedClubPage clubs;
        GameAccess.ScopedPlayerPage players;
        GameAccess.ClubView club;
        Selection selection;
    }

    public ComparisonModel(GameAccess game) { this.game = Objects.requireNonNull(game); }
    public void clear() {
        scope = null;
        for (Side side : sides) {
            side.clubs = null; side.players = null; side.club = null; side.selection = null;
        }
    }
    private void accept(GameAccess.CareerScope next) {
        if (scope != null && !scope.equals(next)) {
            throw new GameQueryException(GameQueryException.Reason.STALE_SCOPE, "Escopo mudou");
        }
        scope = next;
    }
    public GameAccess.ScopedClubPage clubs(int sideIndex, String filter, int offset) {
        GameAccess.validatePage(offset, PAGE_SIZE);
        Side side = sides[sideIndex];
        try {
            var page = game.scopedClubs(filter, offset, PAGE_SIZE);
            accept(page.scope());
            side.clubs = page; side.players = null; side.club = null;
            if (!page.loaded()) clear();
            return page;
        } catch (GameQueryException failure) { clear(); throw failure; }
    }
    public GameAccess.ScopedPlayerPage players(int sideIndex, GameAccess.ClubView club, int offset) {
        GameAccess.validatePage(offset, PAGE_SIZE);
        Side side = sides[sideIndex];
        try {
            if (side.clubs == null || !side.clubs.loaded() || !side.clubs.items().contains(club)) {
                throw new GameQueryException(GameQueryException.Reason.EXPIRED_REFERENCE, "Atualize a pagina de clubes");
            }
            accept(club.reference().scope());
            var page = game.scopedPlayers(club.reference(), offset, PAGE_SIZE);
            accept(page.scope());
            side.players = page; side.club = club;
            return page;
        } catch (GameQueryException failure) { clear(); throw failure; }
    }
    public void select(int sideIndex, int row) {
        Side side = sides[sideIndex];
        if (side.players == null || side.club == null) throw new IllegalStateException("Consulte jogadores primeiro");
        side.selection = new Selection(side.club.club().name(), side.players.items().get(row));
    }
    public Selection selection(int sideIndex) { return sides[sideIndex].selection; }
    public List<Row> comparison() {
        Selection a = selection(0), b = selection(1);
        if (a == null || b == null) return List.of();
        return List.of(new Row("Clube na consulta", a.club(), b.club()),
                new Row("Nome", a.player().name(), b.player().name()),
                new Row("Idade (anos)", "" + a.player().age(), "" + b.player().age()),
                new Row("Posicao (codigo SDK)", "" + a.player().position(), "" + b.player().position()),
                new Row("Forca (valor SDK)", "" + a.player().strength(), "" + b.player().strength()),
                new Row("Value (valor SDK; unidade nao documentada)", "" + a.player().value(), "" + b.player().value()));
    }
}
