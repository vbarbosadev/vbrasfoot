package br.vbrasfoot.api;

import java.util.List;

/** Immutable snapshots only. No legacy objects or save-writing operations are exposed. */
public interface GameAccess {
    Snapshot snapshot();

    /** Added in loader 0.3.0. Default deliberately refuses to fabricate scoped guarantees. */
    default ScopedClubPage scopedClubs(String filter, int offset, int limit) {
        throw new UnsupportedOperationException("Consulta com escopo exige loader 0.3.0");
    }
    default ScopedPlayerPage scopedPlayers(ClubRef club, int offset, int limit) {
        throw new UnsupportedOperationException("Consulta com escopo exige loader 0.3.0");
    }
    /** Read-only raw attributes. Requires query.player.details and game.read; no persistent player identity. */
    default ScopedPlayerDetailsPage scopedPlayerDetails(ClubRef club, int offset, int limit) {
        throw new UnsupportedOperationException("Detalhes de jogadores exigem query.player.details");
    }
    /** Original integer values, not normalized ratings or promised ranges. */
    record IndividualSkills(int goalkeeping, int tackling, int passing, int playmaking, int finishing, int speed, int technique) { }
    /** Skills are absent when the career disables individual abilities; never synthesized from strength. */
    record PlayerDetails(Player player, java.util.Optional<IndividualSkills> skills) {
        public PlayerDetails { java.util.Objects.requireNonNull(player); java.util.Objects.requireNonNull(skills); }
    }
    record ScopedPlayerDetailsPage(CareerScope scope, ClubRef club, List<PlayerDetails> items, int offset, int total) {
        public ScopedPlayerDetailsPage {
            java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(club);
            if (!scope.equals(club.scope())) throw new IllegalArgumentException("Scope mismatch");
            items = List.copyOf(items);
            if (offset < 0 || total < 0 || items.size() > Math.max(0, total - offset)) throw new IllegalArgumentException("Invalid page");
        }
    }
    /** Current calendar only, not a schedule/event stream. Requires loader 0.5.0 and game.read. */
    default ScopedCalendar scopedCalendar() { throw new UnsupportedOperationException("Calendario atual exige loader 0.5.0"); }
    /** Integer balance in unspecified game monetary units. No history or currency conversion. */
    default ScopedClubBalance scopedClubBalance(ClubRef club) { throw new UnsupportedOperationException("Saldo do clube exige loader 0.5.0"); }
    /** Copied Gregorian AD fields, month 1..12; displayedDate uses displayTimeZoneId. */
    record CalendarDate(int year, int month, int day, String sourceTimeZoneId, String displayTimeZoneId, String displayedDate) {
        public CalendarDate {
            if (year < 1) throw new IllegalArgumentException("Gregorian AD year required");
            java.time.LocalDate.of(year, month, day);
            if (sourceTimeZoneId == null || sourceTimeZoneId.isBlank() || displayTimeZoneId == null || displayTimeZoneId.isBlank()) throw new IllegalArgumentException("Timezone IDs required");
            java.util.Objects.requireNonNull(displayedDate);
        }
    }
    /** dayIndex is the observed schedule-list index, not an identity or mutation revision. */
    record ScopedCalendar(CareerScope scope, int dayIndex, CalendarDate date) {
        public ScopedCalendar { java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(date); if (dayIndex < 0) throw new IllegalArgumentException("Invalid calendar index"); }
    }
    record ScopedClubBalance(CareerScope scope, ClubRef club, long balance) {
        public ScopedClubBalance { java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(club); if (!scope.equals(club.scope())) throw new IllegalArgumentException("Scope mismatch"); }
    }
    /** Query-session identity only. Not a persistent career ID or a gameplay mutation revision. */
    record CareerScope(String session, long generation) {
        public CareerScope { if (session == null || session.isBlank() || generation < 1) throw new IllegalArgumentException("Invalid query scope"); }
    }
    /** Opaque, bounded-lifetime handle issued by a scoped club query. Never persist to a sidecar. */
    record ClubRef(CareerScope scope, String token) {
        public ClubRef { java.util.Objects.requireNonNull(scope); if (token == null || token.isBlank()) throw new IllegalArgumentException("Invalid club reference"); }
    }
    record ClubView(ClubRef reference, ClubSummary club) {
        public ClubView { java.util.Objects.requireNonNull(reference); java.util.Objects.requireNonNull(club); }
    }
    record ScopedClubPage(boolean loaded, CareerScope scope, List<ClubView> items, int offset, int total) {
        public ScopedClubPage { java.util.Objects.requireNonNull(scope); items = List.copyOf(items); }
    }
    record ScopedPlayerPage(CareerScope scope, List<Player> items, int offset, int total) {
        public ScopedPlayerPage { java.util.Objects.requireNonNull(scope); items = List.copyOf(items); }
    }

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
