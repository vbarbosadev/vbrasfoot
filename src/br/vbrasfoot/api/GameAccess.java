package br.vbrasfoot.api;

import java.util.List;

/** Immutable snapshots only. No legacy objects or save-writing operations are exposed. */
public interface GameAccess {
    Snapshot snapshot();

    record Player(int id, String name, int age, int position, int strength, int value) { }
    record Club(int id, String name, boolean human, List<Player> players) {
        public Club { players = List.copyOf(players); }
    }
    record Snapshot(boolean loaded, List<Club> clubs) {
        public Snapshot { clubs = List.copyOf(clubs); }
    }
}
