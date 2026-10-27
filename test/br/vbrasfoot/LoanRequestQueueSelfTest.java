package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import java.util.*;
import static br.vbrasfoot.LoanRequestQueue.*;

public final class LoanRequestQueueSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    public static void main(String[] args) {
        var scope = new CareerScope("owned-session", 1);
        var player = new PlayerRef(scope, "player");
        var origin = new ClubRef(scope, "origin"); var destination = new ClubRef(scope, "destination");
        var strengths = new ArrayList<>(List.of(40, 60));
        var proposal = new Proposal("one", player, origin, destination, 50, strengths);
        strengths.clear(); check(proposal.destinationStrengths().size() == 2, "scalar inputs copied");
        try (var queue = new LoanRequestQueue(scope)) {
            check(queue.submit(proposal).state() == State.PENDING, "submit creates pending not transfer");
            check(queue.snapshot().size() == 1 && queue.submit(proposal).assessment().isEmpty(), "duplicate pending idempotent");
            var result = queue.evaluate("one");
            check(result.state() == State.APPROVED && result.assessment().isPresent(), "rule recommends approval");
            check(queue.evaluate("one").equals(result) && queue.submit(proposal).equals(result), "repeat cannot recreate approved request");
            rejects(() -> queue.submit(new Proposal("one", player, origin, destination, 51, List.of(40, 60))));
            queue.cancel("one"); check(queue.submit(proposal).state() == State.CANCELLED, "cancelled key never revived");
            queue.submit(new Proposal("two", player, origin, destination, 90, List.of(40, 60)));
            check(queue.evaluate("two").state() == State.REFUSED, "above median refusal");
            queue.submit(new Proposal("three", player, origin, destination, 1, List.of()));
            check(queue.evaluate("three").state() == State.INSUFFICIENT_DATA, "empty roster distinct from refusal");
            queue.submit(new Proposal("four", player, origin, destination, 1, List.of(40)));
            queue.evaluate("four");
            queue.submit(new Proposal("five", player, origin, destination, 1, List.of(40)));
            queue.invalidate();
            check(queue.evaluate("four").state() == State.INVALIDATED && queue.evaluate("five").state() == State.INVALIDATED, "invalidate approved and pending");
            rejects(() -> queue.evaluate("missing"));
            check(queue.snapshot().get(1).state() == State.REFUSED, "historical refusal preserved");
            try { queue.snapshot().clear(); throw new AssertionError("mutable snapshot"); }
            catch (UnsupportedOperationException expected) { checks++; }
        }
        var capped = new LoanRequestQueue(scope);
        for (int i = 0; i < 32; i++) capped.submit(new Proposal("q" + i, player, origin, destination, 1, List.of(40)));
        rejects(() -> capped.submit(new Proposal("overflow", player, origin, destination, 1, List.of(40))));
        capped.close(); capped.close();
        rejects(() -> capped.submit(proposal)); rejects(() -> capped.evaluate("q0"));
        check(capped.snapshot().stream().allMatch(r -> r.state() == State.INVALIDATED), "shutdown leaves no actionable request");
        var foreign = new CareerScope("owned-session", 2);
        rejects(() -> new Proposal("bad", new PlayerRef(foreign, "player"), origin, destination, 1, List.of(40)));
        rejects(() -> new Proposal("same", player, origin, origin, 1, List.of(40)));
        rejects(() -> new Proposal("bad key", player, origin, destination, 1, List.of(40)));
        rejects(() -> new LoanRequestQueue(foreign).submit(proposal));
        System.out.println("PASS: " + checks + " session-only loan-request checks; no persistent queue, execution or public write authority.");
    }
}
