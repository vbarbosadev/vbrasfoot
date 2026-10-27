package br.vbrasfoot;

import java.util.*;
import static br.vbrasfoot.LoanClubAssessment.*;

/** Target-free policy checks; approval never invokes a transfer or grants write authority. */
public final class LoanClubAssessmentSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        check(evaluate(50, List.of(30, 50, 70)).decision() == Decision.APPROVE, "equal odd median accepted");
        check(evaluate(51, List.of(70, 30, 50)).reason() == Reason.ABOVE_DESTINATION_MEDIAN, "above median refused irrespective of order");
        check(evaluate(50, List.of(40, 61)).decision() == Decision.APPROVE, "even half median preserved");
        check(evaluate(51, List.of(40, 61)).decision() == Decision.REFUSE, "above half median refused");
        check(evaluate(50, Collections.nCopies(34, 50)).decision() == Decision.APPROVE, "34 leaves one vacancy");
        check(evaluate(0, Collections.nCopies(35, 50)).reason() == Reason.DESTINATION_FULL, "35 blocks even weakest candidate");
        check(evaluate(0, Collections.nCopies(39, 50)).reason() == Reason.DESTINATION_FULL, "overfull roster refused");
        check(evaluate(0, List.of()).decision() == Decision.INSUFFICIENT_DATA, "empty roster does not invent median");
        check(evaluate(Integer.MAX_VALUE, List.of(Integer.MAX_VALUE, Integer.MAX_VALUE)).decision() == Decision.APPROVE, "upper integer boundary no overflow");
        check(evaluate(Integer.MIN_VALUE, List.of(Integer.MIN_VALUE, Integer.MIN_VALUE)).decision() == Decision.APPROVE, "lower integer boundary no overflow");
        check(evaluate(0, List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)).decision() == Decision.REFUSE, "negative half median not rounded to zero");
        var source = new ArrayList<>(List.of(70, 30, 50));
        evaluate(50, source);
        check(source.equals(List.of(70, 30, 50)), "input roster not sorted in place");
        try { evaluate(0, null); throw new AssertionError("null input accepted"); }
        catch (NullPointerException expected) { checks++; }
        try { evaluate(0, Arrays.asList(20, null)); throw new AssertionError("missing strength accepted"); }
        catch (NullPointerException expected) { checks++; }
        System.out.println("PASS: " + checks + " pure mod loan-policy checks; no game, request queue or transfer authority.");
    }
}
