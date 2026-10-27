package br.vbrasfoot;

import java.util.*;
import br.vbrasfoot.api.GameAccess.Player;

/** Policy/codec checks only; native runtime still decides original eligibility. */
public final class NativeListedLoanSelfTest {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static FinanceDelta.Snapshot finance() {
        var fields = new HashMap<String,Long>(); for (String key : FinanceDelta.FIELDS) fields.put(key, 0L);
        return new FinanceDelta.Snapshot(0, fields);
    }
    public static void main(String[] args) throws Exception {
        for (int count = 0; count <= 5; count++) check(LoanDestinationLimit.reached(count) == (count >= 3), "three is hard cap, including preexisting four: " + count);
        var terms = new LoanTerms(1, List.of(-1), finance(), finance(), false, LoanTerms.NATIVE_POLICY);
        var request = new LoanRequest(UUID.randomUUID(), UUID.randomUUID(), new CareerSidecarStore.SaveRevision("a".repeat(64), 1),
                LoanManagerPanel.OWNER, "listed", new FinanceRequest.Club(0,1,"Origin"), new FinanceRequest.Club(1,2,"Target"), 0,
                List.of(new Player(1,"High strength",23,1,99,9000000)), List.of(new Player(2,"Low strength",23,1,1,100)), 0, Optional.of(terms));
        check(request.nativeListed() && request.fee() == 0, "listed player fee always zero");
        check(request.assessment().decision() == LoanClubAssessment.Decision.APPROVE, "listed player bypasses custom destination median");
        check(request.release().approved() && request.release().fee() == 0, "missing participation does not refuse listed player");
        check(LoanRequest.decode(request.encode()).equals(request), "native policy survives canonical persistence");
        check(LoanRequest.decode(request.encode()).digest().equals(request.digest()), "native path bound into digest");
        var full = new LoanRequest(request.career(), request.binding(), request.save(), request.owner(), "full", request.origin(), request.destination(), 0,
                request.originRoster(), Collections.nCopies(35, request.destinationRoster().get(0)), 0, Optional.of(terms));
        check(full.assessment().decision() == LoanClubAssessment.Decision.REFUSE, "listed still respects roster35");
        var customTerms = new LoanTerms(1, List.of(-1), finance(), finance(), false, LoanReleasePolicy.POLICY);
        var custom = new LoanRequest(request.career(), request.binding(), request.save(), request.owner(), request.key(), request.origin(), request.destination(), 0,
                request.originRoster(), request.destinationRoster(), 0, Optional.of(customTerms));
        check(!custom.nativeListed() && !custom.digest().equals(request.digest()), "custom policy not silently migrated to native");
        check(new LoanDiagnostics.Eligibility(3).userMessage().contains("tres emprestados"), "limit refusal identifies three, not native four");
        check(terms.predicted(true,0).equals(terms.originFinance()) && terms.predicted(false,0).equals(terms.destinationFinance()), "native no fee preserves balance snapshots");
        var prompts = new java.util.concurrent.atomic.AtomicInteger();
        check(NativeLoanFlow.proceed(request, () -> true, () -> { prompts.incrementAndGet(); throw new AssertionError("No proposal for listed player"); }), "listed quote proceeds without proposal dialog");
        check(!NativeLoanFlow.proceed(full, () -> true, () -> { throw new AssertionError("No dialog or direct transfer when refused"); }), "full destination still blocks direct route");
        boolean staleDenied = false;
        try { NativeLoanFlow.proceed(request, () -> false, () -> { throw new AssertionError("Stale selection must not open proposal"); }); }
        catch (IllegalStateException expected) { staleDenied = true; }
        check(staleDenied, "listed direct route refuses selection change");
        var unlisted = new LoanRequest(request.career(), request.binding(), request.save(), request.owner(), "unlisted", request.origin(), request.destination(), 0,
                List.of(new Player(1,"Unlisted",23,1,1,100)), List.of(new Player(2,"Target",23,1,50,100)), 0);
        check(NativeLoanFlow.proceed(unlisted, () -> true, () -> { prompts.incrementAndGet(); return true; }) && prompts.get() == 1,
                "unlisted/legacy quote still requires an explicit proposal confirmation");
        check(!NativeLoanFlow.proceed(unlisted, () -> true, () -> false), "unlisted cancellation still prevents execution");
        System.out.println("PASS: " + checks + " listed-native/no-fee/three-limit policy checks; no game effects");
    }
}
