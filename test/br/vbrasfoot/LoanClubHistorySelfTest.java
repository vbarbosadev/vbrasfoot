package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.Player;
import java.io.*;
import java.util.*;
import static br.vbrasfoot.LoanReleasePolicy.Decision.*;

/** Synthetic history only; future-season preservation is not inferred from these fixtures. */
public final class LoanClubHistorySelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IOException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    private static LoanClubHistory.Observation row(int club, int season, int games) { return new LoanClubHistory.Observation(club, season, games); }
    private static LoanClubHistory history(List<Integer> prior, List<Integer> current) {
        return new LoanClubHistory(81, 2, 3, List.of(new LoanClubHistory.Season(1, prior), new LoanClubHistory.Season(2, current)));
    }
    private static LoanReleasePolicy.Result evaluate(LoanClubHistory value) { return value.evaluate(0, List.of(60, 20, 30), 8427000); }
    private static FinanceDelta.Snapshot finance(long balance) {
        var fields = new HashMap<String, Long>(); for (String key : FinanceDelta.FIELDS) fields.put(key, 0L);
        return new FinanceDelta.Snapshot(balance, fields);
    }
    public static void main(String[] args) throws Exception {
        var first = LoanClubHistory.capture(81, 2, List.of(List.of(row(9, 1, 100), row(81, 2, 1)),
                List.of(row(9, 1, 200), row(81, 2, 5)), List.of(row(81, 3, 99))));
        check(first.seasons().size() == 1 && first.seasons().get(0).season() == 2, "other clubs and future seasons never enter current club career");
        check(evaluate(first).decision() == PAID && evaluate(first).fee() == 842700, "first club season uses own current club participation only");
        check(evaluate(first).reason().contains("Primeira temporada"), "first-season fallback explicitly explained internally");
        var shortCurrent = history(List.of(1, 20, -1), List.of(0, 2, -1));
        check(evaluate(shortCurrent).decision() == PAID, "short current season falls back to actual earlier club career");
        check(evaluate(shortCurrent).reason().contains("somente historico"), "history fallback disclosed, no invented current games");
        check(evaluate(history(List.of(20, 20, -1), List.of(0, 20, -1))).decision() == REFUSE,
                "old full-time regular needs enough recent evidence;70/30 score30percent exceeds25");
        check(evaluate(history(List.of(20, 20, -1), List.of(0, 40, -1))).decision() == REFUSE, "normalization is seasonal, not diluted by larger recent schedule");
        check(evaluate(history(List.of(10, 20, -1), List.of(0, 20, -1))).decision() == PAID, "currently unused player can leave despite previously moderate usage");
        check(evaluate(history(List.of(0, 20, -1), List.of(10, 20, -1))).decision() == REFUSE, "current regular cannot hide behind old unused seasons");
        check(evaluate(history(List.of(5, 20, -1), List.of(5, 20, -1))).decision() == PAID, "exact weighted25percent boundary accepted");
        check(evaluate(history(List.of(5, 20, -1), List.of(6, 20, -1))).decision() == REFUSE, "above weighted boundary refused");
        check(evaluate(history(List.of(-1, 20, 20), List.of(-1, 20, 20))).decision() == INSUFFICIENT_DATA, "candidate unknown in all years never assumed zero");
        check(evaluate(history(List.of(0, 2, -1), List.of(0, 2, -1))).decision() == INSUFFICIENT_DATA, "short observations across both periods remain insufficient");
        check(evaluate(history(List.of(-1, -1, -1), List.of(0, 20, -1))).decision() == PAID, "current sufficient does not need fabricated historical tenure");
        check(evaluate(history(List.of(0, 20, -1), List.of(-1, 2, -1))).decision() == PAID, "valid prior career may support fallback without unknown current treated as0");
        check(history(List.of(-1, -1, -1), List.of(-1, -1, -1)).evaluate(1, List.of(60, 20, 30), 8427000).decision() == FREE, "low strength remains free without history");
        var duplicate = LoanClubHistory.capture(81, 2, List.of(List.of(row(81, 1, 1), row(81, 1, 2), row(81, 1, 0)), List.of(row(81, 1, 20)), List.of()));
        check(duplicate.seasons().get(0).games().get(0) == -1 && evaluate(duplicate).decision() == INSUFFICIENT_DATA, "duplicate club/year records stay ambiguous, not summed or last-wins");
        var many = new LoanClubHistory(81, 128, 3, java.util.stream.IntStream.rangeClosed(1, 128)
                .mapToObj(year -> new LoanClubHistory.Season(year, List.of(Integer.MAX_VALUE / 4, Integer.MAX_VALUE, -1))).toList());
        check(evaluate(many).decision() == PAID, "128season integer extremes use exact rational arithmetic without overflow");
        rejects(() -> new LoanClubHistory(81, 2, 3, List.of(new LoanClubHistory.Season(2, List.of(0, 5, 8)), new LoanClubHistory.Season(1, List.of(0, 5, 8)))));
        rejects(() -> new LoanClubHistory(81, 1, 3, List.of(new LoanClubHistory.Season(2, List.of(0, 5, 8)))));
        rejects(() -> new LoanClubHistory(81, 1, 2, List.of(new LoanClubHistory.Season(1, List.of(0, 5, 8)))));
        rejects(() -> LoanClubHistory.capture(81, 1, List.of()));
        var inputs = new LoanTerms(2, shortCurrent.currentGames(), finance(1000), finance(900000), false, LoanClubHistory.POLICY, Optional.of(shortCurrent));
        var players = List.of(new Player(1, "Candidate", 25, 1, 60, 8427000), new Player(2, "Other", 25, 1, 20, 1000), new Player(3, "Third", 25, 1, 30, 1000));
        var career = UUID.randomUUID(); var binding = UUID.randomUUID(); var save = new CareerSidecarStore.SaveRevision("a".repeat(64), 1);
        var request = new LoanRequest(career, binding, save, LoanManagerPanel.OWNER, "history", new FinanceRequest.Club(0, 10, "Origin"),
                new FinanceRequest.Club(1, 20, "Destination"), 0, players, List.of(new Player(8, "Target", 25, 1, 70, 1000)), 1000, Optional.of(inputs));
        check(request.assessment().decision() == LoanClubAssessment.Decision.APPROVE && request.fee() == 842700, "historical fee bound into approved contract");
        check(LoanRequest.decode(request.encode()).equals(request) && LoanRequest.decode(request.encode()).digest().equals(request.digest()), "historical policy inputs canonical after decode");
        var changed = history(List.of(20, 20, -1), List.of(0, 2, -1));
        var changedTerms = new LoanTerms(2, inputs.games(), inputs.originFinance(), inputs.destinationFinance(), false, LoanClubHistory.POLICY, Optional.of(changed));
        var stale = new LoanRequest(career, binding, save, request.owner(), request.key(), request.origin(), request.destination(), 0, players, request.destinationRoster(), 1000, Optional.of(changedTerms));
        check(!stale.digest().equals(request.digest()) && stale.assessment().decision() == LoanClubAssessment.Decision.REFUSE, "changing prior club career changes digest and decision");
        rejects(() -> new LoanTerms(2, List.of(1, 2, 3), inputs.originFinance(), inputs.destinationFinance(), false, LoanClubHistory.POLICY, Optional.of(shortCurrent)));
        for (String policy : List.of(LoanReleasePolicy.LEGACY_POLICY, LoanReleasePolicy.POLICY)) {
            var oldTerms = new LoanTerms(2, inputs.games(), inputs.originFinance(), inputs.destinationFinance(), false, policy);
            var bytes = new ByteArrayOutputStream(); oldTerms.write(new DataOutputStream(bytes)); byte[] raw = bytes.toByteArray();
            var restored = LoanTerms.read(new DataInputStream(new ByteArrayInputStream(raw))); var repeated = new ByteArrayOutputStream(); restored.write(new DataOutputStream(repeated));
            check(restored.history().isEmpty() && Arrays.equals(raw, repeated.toByteArray()) && restored.policy().equals(policy), "old policy bytes and missing-history semantics preserved: " + policy);
        }
        var insufficient = new LoanDiagnostics.Eligibility(1, "Technical SHA HEAD coverage -1", false, "historico insuficiente para avaliar.");
        check(insufficient.userMessage().equals("Recusado: historico insuficiente para avaliar.") && !insufficient.userMessage().contains("HEAD"), "simple user refusal hides technical data");
        System.out.println("PASS: " + checks + " club-career history/policy/codec checks; synthetic only, no native progression");
    }
}
