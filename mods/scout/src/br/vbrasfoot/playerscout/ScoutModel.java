package br.vbrasfoot.playerscout;

import br.vbrasfoot.api.GameAccess;
import java.util.*;

/** Manual bounded queries; no persistence, cross-page ranking or interpretation of raw skill scales. */
public final class ScoutModel {
    public static final int PAGE_SIZE = 50;
    private final GameAccess game;
    private GameAccess.ScopedClubPage clubs;
    private GameAccess.ScopedPlayerDetailsPage players;
    public ScoutModel(GameAccess game) { this.game = Objects.requireNonNull(game); }
    public GameAccess.ScopedClubPage clubs(String filter, int offset) {
        clear();
        clubs = game.scopedClubs(filter, offset, PAGE_SIZE); return clubs;
    }
    public GameAccess.ScopedPlayerDetailsPage players(int selected, int offset) {
        players = null;
        try {
            if (clubs == null || !clubs.loaded() || selected < 0 || selected >= clubs.items().size())
                throw new IllegalStateException("Atualize clubes e selecione um clube");
            var reference = clubs.items().get(selected).reference();
            var result = game.scopedPlayerDetails(reference, offset, PAGE_SIZE);
            if (!result.scope().equals(clubs.scope()) || !result.club().equals(reference))
                throw new IllegalStateException("Resposta de outro escopo/clube");
            players = result; return result;
        } catch (RuntimeException failure) { clear(); throw failure; }
    }
    public void clear() { clubs = null; players = null; }
}
