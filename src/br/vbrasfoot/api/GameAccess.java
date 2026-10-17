package br.vbrasfoot.api;

import java.util.List;

/** Immutable snapshots only. No legacy objects or save-writing operations are exposed. */
public interface GameAccess {
    Snapshot snapshot();

    /** Bounded club query; override to avoid constructing a complete snapshot. */
    default ClubPage clubs(String filter, int offset, int limit) {
        validatePage(offset, limit);
        String query = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT);
        Snapshot source = snapshot();
        List<Integer> matches = java.util.stream.IntStream.range(0, source.clubs().size()).boxed()
                .filter(i -> source.clubs().get(i).name().toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        return new ClubPage(source.loaded(), matches.stream().skip(offset).limit(limit).map(i -> {
            Club c = source.clubs().get(i);
            return new ClubSummary(i, c.id(), c.name(), c.human(), c.players().size());
        }).toList(), offset, matches.size());
    }

    /** IDs are scoped to the observed career, not global or cross-save identities. */
    default PlayerPage players(int clubId, int offset, int limit) {
        validatePage(offset, limit);
        Snapshot source = snapshot();
        List<Club> matches = source.clubs().stream().filter(c -> c.id() == clubId).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("ID de clube ambiguo; use playersAt com index da pagina atual");
        List<Player> players = matches.isEmpty() ? List.of() : matches.get(0).players();
        return new PlayerPage(source.loaded(), players.stream().skip(offset).limit(limit).toList(), offset, players.size());
    }

    /** Position in the current club list, not a durable ID. Requery after career changes. */
    default PlayerPage playersAt(int clubIndex, int offset, int limit) {
        validatePage(offset, limit);
        Snapshot source = snapshot();
        if (clubIndex < 0 || clubIndex >= source.clubs().size()) throw new IllegalArgumentException("Indice de clube fora da lista atual");
        List<Player> players = source.clubs().get(clubIndex).players();
        return new PlayerPage(source.loaded(), players.stream().skip(offset).limit(limit).toList(), offset, players.size());
    }

    static void validatePage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 500) throw new IllegalArgumentException("offset >= 0; limit entre 1 e 500");
    }

    record ClubSummary(int index, int id, String name, boolean human, int playerCount) { }
    record ClubPage(boolean loaded, List<ClubSummary> items, int offset, int total) {
        public ClubPage { items = List.copyOf(items); }
    }
    record PlayerPage(boolean loaded, List<Player> items, int offset, int total) {
        public PlayerPage { items = List.copyOf(items); }
    }

    record Player(int id, String name, int age, int position, int strength, int value) { }
    record Club(int id, String name, boolean human, List<Player> players) {
        public Club { players = List.copyOf(players); }
    }
    record Snapshot(boolean loaded, List<Club> clubs) {
        public Snapshot { clubs = List.copyOf(clubs); }
    }
}
