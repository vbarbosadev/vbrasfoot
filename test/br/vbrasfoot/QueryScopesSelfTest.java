package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.GameQueryException.Reason;
import java.util.*;

public final class QueryScopesSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejects(Runnable action, Reason reason) {
        try { action.run(); } catch (GameQueryException failure) { check(failure.reason() == reason, "typed error " + reason); return; }
        throw new AssertionError("Expected " + reason);
    }
    public static void main(String[] args) {
        QueryScopes registry = new QueryScopes(2);
        Object primary = new Object(), auxiliary = new Object(), first = new Object(), second = new Object();
        List<Object> clubs = new ArrayList<>(List.of(first, second)), humans = new ArrayList<>(List.of(first));
        var scope = registry.observe(primary, auxiliary, clubs, humans);
        check(scope.equals(registry.observe(primary, auxiliary, clubs, humans)), "same graph stable scope");
        var a = registry.issue(scope, 0, first);
        var b = registry.issue(scope, 1, second);
        check(registry.resolve(a, clubs) == 0 && registry.resolve(b, clubs) == 1, "same-ID clubs selected by opaque handles");
        var c = registry.issue(scope, 0, first);
        check(registry.retained() == 2, "bounded registry");
        rejects(() -> registry.resolve(a, clubs), Reason.EXPIRED_REFERENCE);
        Collections.swap(clubs, 0, 1);
        rejects(() -> registry.resolve(c, clubs), Reason.CLUB_MOVED);
        Collections.swap(clubs, 0, 1);
        var old = registry.issue(scope, 0, first);
        var next = registry.observe(new Object(), auxiliary, clubs, humans);
        check(next.generation() > scope.generation(), "primary root replacement increments generation");
        rejects(() -> registry.resolve(old, clubs), Reason.STALE_SCOPE);
        var oldAux = registry.issue(next, 0, first);
        var third = registry.observe(primary, new Object(), clubs, humans);
        rejects(() -> registry.resolve(oldAux, clubs), Reason.STALE_SCOPE);
        var previousList = registry.issue(third, 0, first);
        registry.observe(primary, auxiliary, new ArrayList<>(clubs), humans);
        rejects(() -> registry.resolve(previousList, clubs), Reason.STALE_SCOPE);
        QueryScopes otherBridge = new QueryScopes(2);
        otherBridge.observe(primary, auxiliary, clubs, humans);
        rejects(() -> otherBridge.resolve(old, clubs), Reason.STALE_SCOPE);
        var now = registry.observe(primary, auxiliary, clubs, humans);
        rejects(() -> registry.resolve(new GameAccess.ClubRef(now, "forged-token"), clubs), Reason.EXPIRED_REFERENCE);
        Object playerA = new Object(), playerB = new Object();
        List<Object> roster = new ArrayList<>(List.of(playerA, playerB));
        var playerRef = registry.issuePlayer(now, 0, 0, first, playerA);
        check(registry.resolvePlayer(playerRef, 0, clubs, roster) == 0, "opaque player handle resolves exact object in queried club");
        Collections.swap(roster, 0, 1);
        rejects(() -> registry.resolvePlayer(playerRef, 0, clubs, roster), Reason.PLAYER_MOVED);
        Collections.swap(roster, 0, 1);
        var movedClub = registry.issuePlayer(now, 0, 0, first, playerA);
        rejects(() -> registry.resolvePlayer(movedClub, 1, clubs, roster), Reason.PLAYER_MOVED);
        var expiredPlayer = registry.issuePlayer(now, 0, 1, first, playerB);
        registry.issue(now, 1, second);
        registry.issuePlayer(now, 0, 0, first, playerA);
        check(registry.retained() == 2, "club and player handles share one bounded registry capacity");
        rejects(() -> registry.resolvePlayer(expiredPlayer, 0, clubs, roster), Reason.EXPIRED_REFERENCE);
        var beforeReset = registry.issue(now, 0, first);
        registry.observe(null, null, null, null);
        rejects(() -> registry.resolve(beforeReset, clubs), Reason.STALE_SCOPE);
        rejects(() -> CareerBridge.onEdt(() -> { throw new GameQueryException(Reason.STATE_CHANGED, "fixture"); }), Reason.STATE_CHANGED);
        rejects(() -> CareerBridge.scopedRead(() -> { throw new IndexOutOfBoundsException("fixture list shrink"); }), Reason.STATE_CHANGED);
        rejects(() -> CareerBridge.scopedRead(() -> { throw new ConcurrentModificationException("fixture mutation"); }), Reason.STATE_CHANGED);
        GameAccess oldApi = () -> new GameAccess.Snapshot(false, List.of());
        try { oldApi.scopedClubs("", 0, 10); throw new AssertionError("unsupported scope fabricated"); }
        catch (UnsupportedOperationException expected) { check(true, "old API does not invent scoped guarantees"); }
        try { oldApi.scopedPlayersWithReferences(new GameAccess.ClubRef(now, "fixture"), 0, 10); throw new AssertionError("unsupported player reference fabricated"); }
        catch (UnsupportedOperationException expected) { check(true, "old API does not fabricate player handles"); }
        System.out.println("PASS: " + checks + " query scope tests (synthetic; no persistent player identity).");
    }
}
