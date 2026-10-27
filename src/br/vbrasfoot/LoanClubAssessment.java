package br.vbrasfoot;

import java.util.List;
import java.util.Objects;

/** Pure proposed mod policy, not the original game's eligibility rule or a transfer authorization. */
final class LoanClubAssessment {
    static final String POLICY = "destination-median/1";
    enum Decision { APPROVE, REFUSE, INSUFFICIENT_DATA }
    enum Reason { WITHIN_DESTINATION_MEDIAN, ABOVE_DESTINATION_MEDIAN, DESTINATION_FULL, EMPTY_DESTINATION, ORIGIN_NOT_RELEASED, INSUFFICIENT_FEE_BALANCE, NATIVE_LISTED }
    record Result(Decision decision, Reason reason) { }

    private LoanClubAssessment() { }

    /**
     * Rule v1: refuse a full destination; otherwise recommend acceptance only when candidate
     * strength is no greater than the destination roster median. All inputs are copied scalar
     * observations. No probability, legacy call, persistent request, or write permission exists here.
     * The original loan rule still has to validate availability/eligibility at execution time.
     */
    static Result evaluate(int candidateStrength, List<Integer> destinationStrengths) {
        var strengths = List.copyOf(Objects.requireNonNull(destinationStrengths));
        if (strengths.size() >= 35) return new Result(Decision.REFUSE, Reason.DESTINATION_FULL);
        if (strengths.isEmpty()) return new Result(Decision.INSUFFICIENT_DATA, Reason.EMPTY_DESTINATION);
        var sorted = strengths.stream().sorted().toList();
        int middle = sorted.size() / 2;
        // Twice the median, with long arithmetic to preserve half-integer and int boundary values.
        long twiceMedian = sorted.size() % 2 == 0
                ? (long) sorted.get(middle - 1) + sorted.get(middle) : 2L * sorted.get(middle);
        return 2L * candidateStrength <= twiceMedian
                ? new Result(Decision.APPROVE, Reason.WITHIN_DESTINATION_MEDIAN)
                : new Result(Decision.REFUSE, Reason.ABOVE_DESTINATION_MEDIAN);
    }
}
