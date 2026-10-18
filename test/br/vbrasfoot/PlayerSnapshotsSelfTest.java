package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.util.*;

public final class PlayerSnapshotsSelfTest {
    public static final class Player {
        int skillReads, value = 7;
        public int gD() { return 1; } public String getNome() { return "Owned"; }
        public int getIdade() { return 20; } public int getPosicao() { return 2; }
        public int fi() { return 40; } public int fk() { return 100; }
        public int gK() { skillReads++; return value; } public int gN() { skillReads++; return 8; }
        public int gM() { skillReads++; return 9; } public int gO() { skillReads++; return 10; }
        public int gP() { skillReads++; return 11; } public int gJ() { skillReads++; return 12; }
        public int gL() { skillReads++; return 13; }
    }
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        Player original = new Player(); var copy = PlayerSnapshots.copy(original, true);
        check(copy.skills().orElseThrow().equals(new GameAccess.IndividualSkills(7, 8, 9, 10, 11, 12, 13)));
        check(original.skillReads == 7 && copy.player().name().equals("Owned"));
        original.value = Integer.MIN_VALUE;
        check(copy.skills().orElseThrow().goalkeeping() == 7);
        check(PlayerSnapshots.copy(original, true).skills().orElseThrow().goalkeeping() == Integer.MIN_VALUE);
        int reads = original.skillReads;
        check(PlayerSnapshots.copy(original, false).skills().isEmpty() && original.skillReads == reads);
        var scope = new GameAccess.CareerScope("owned", 1); var club = new GameAccess.ClubRef(scope, "token");
        var mutable = new ArrayList<>(List.of(copy));
        var page = new GameAccess.ScopedPlayerDetailsPage(scope, club, mutable, 0, 1); mutable.clear();
        check(page.items().size() == 1);
        boolean refused = false; try { page.items().clear(); } catch (UnsupportedOperationException expected) { refused = true; } check(refused);
        refused = false;
        try { new GameAccess.ScopedPlayerDetailsPage(new GameAccess.CareerScope("other", 1), club, List.of(), 0, 0); }
        catch (IllegalArgumentException expected) { refused = true; } check(refused);
        GameAccess legacy = () -> new GameAccess.Snapshot(false, List.of());
        refused = false; try { legacy.scopedPlayerDetails(club, 0, 1); } catch (UnsupportedOperationException expected) { refused = true; } check(refused);
        check(new GameAccess.ScopedPlayerDetailsPage(scope, club, List.of(), 10, 1).items().isEmpty());
        System.out.println("PASS: " + checks + " player detail snapshot checks; synthetic only.");
    }
}
