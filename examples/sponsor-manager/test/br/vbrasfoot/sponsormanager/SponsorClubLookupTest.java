package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.GameAccess;
import java.util.ArrayList;
import java.util.List;

public final class SponsorClubLookupTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }

    private static final GameAccess.CareerScope SCOPE = new GameAccess.CareerScope("club-lookup-test", 1);

    private static GameAccess.ClubView club(int index, boolean human) {
        var summary = new GameAccess.ClubSummary(index, index, "Clube " + index, human, 25);
        return new GameAccess.ClubView(new GameAccess.ClubRef(SCOPE, "club-" + index), summary);
    }

    private static GameAccess game(int total, List<Integer> humanIndexes, List<Integer> offsets) {
        return new GameAccess() {
            @Override public Snapshot snapshot() { return new Snapshot(true, List.of()); }
            @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                offsets.add(offset);
                int count = Math.min(limit, Math.max(0, total - offset));
                var items = new ArrayList<ClubView>();
                for (int i = offset; i < offset + count; i++) items.add(club(i, humanIndexes.contains(i)));
                return new ScopedClubPage(true, SCOPE, items, offset, total);
            }
        };
    }

    private static void findsHumanClubAfterFirstPage() {
        var offsets = new ArrayList<Integer>();
        var result = SponsorPanel.lookupClub(game(1_301, List.of(777), offsets));
        check(result.club() != null && result.club().id() == 777, "human club is found on a later page");
        check(offsets.equals(List.of(0, 500, 1_000)), "all bounded pages are queried in order");
    }

    private static void rejectsAmbiguousHumanClubsAcrossPages() {
        var offsets = new ArrayList<Integer>();
        var result = SponsorPanel.lookupClub(game(900, List.of(12, 850), offsets));
        check(result.club() == null && result.message().contains("2 clubes controlados"), "human clubs on separate pages are treated as ambiguous");
        check(offsets.equals(List.of(0, 500)), "ambiguity check scans all clubs");
    }

    private static void rejectsInconsistentPages() {
        var offsets = new ArrayList<Integer>();
        GameAccess changedScope = new GameAccess() {
            @Override public Snapshot snapshot() { return new Snapshot(true, List.of()); }
            @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                offsets.add(offset);
                var actualScope = offset == 0 ? SCOPE : new CareerScope("club-lookup-test", 2);
                var items = new ArrayList<ClubView>();
                for (int i = offset; i < Math.min(700, offset + limit); i++) {
                    var summary = new ClubSummary(i, i, "Clube " + i, i == 600, 25);
                    items.add(new ClubView(new ClubRef(actualScope, "club-" + i), summary));
                }
                return new ScopedClubPage(true, actualScope, items, offset, 700);
            }
        };
        var result = SponsorPanel.lookupClub(changedScope);
        check(result.club() == null && result.message().contains("mudou durante a consulta"), "scope change between pages is rejected");

        GameAccess changedTotal = new GameAccess() {
            @Override public Snapshot snapshot() { return new Snapshot(true, List.of()); }
            @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                int reportedTotal = offset == 0 ? 700 : 701;
                var items = new ArrayList<ClubView>();
                for (int i = offset; i < Math.min(700, offset + limit); i++) items.add(club(i, i == 600));
                return new ScopedClubPage(true, SCOPE, items, offset, reportedTotal);
            }
        };
        result = SponsorPanel.lookupClub(changedTotal);
        check(result.club() == null && result.message().contains("mudou durante a consulta"), "total change between pages is rejected");

        GameAccess shortPage = new GameAccess() {
            @Override public Snapshot snapshot() { return new Snapshot(true, List.of()); }
            @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                var items = new ArrayList<ClubView>();
                for (int i = offset; i < 499; i++) items.add(club(i, i == 10));
                return new ScopedClubPage(true, SCOPE, items, offset, 700);
            }
        };
        result = SponsorPanel.lookupClub(shortPage);
        check(result.club() == null && result.message().contains("incompleta"), "short first page is rejected");
    }

    private static void enforcesSafeScanLimit() {
        var offsets = new ArrayList<Integer>();
        var result = SponsorPanel.lookupClub(game(10_001, List.of(9_999), offsets));
        check(result.club() == null && result.message().contains("limite seguro"), "oversized career is refused with a clear message");
        check(offsets.equals(List.of(0)), "oversized career is rejected before scanning additional pages");
    }

    public static void main(String[] args) {
        findsHumanClubAfterFirstPage();
        rejectsAmbiguousHumanClubsAcrossPages();
        rejectsInconsistentPages();
        enforcesSafeScanLimit();
        System.out.println("PASS: " + checks + " sponsor fixed-club pagination checks");
    }
}
