package br.vbrasfoot;

import java.time.LocalDate;
import java.util.*;

public final class LoanReturnEvidenceSelfTest {
    static int checks;
    static final LocalDate DAY = LocalDate.of(2022,1,30);
    static LoanReturnEvidence.Transfer row(int player, int from, int to, int amount, LocalDate date) {
        return new LoanReturnEvidence.Transfer(player,from,to,amount,date);
    }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static boolean returned(List<LoanReturnEvidence.Transfer> history) { return LoanReturnEvidence.returned(1,10,20,DAY,DAY.plusDays(1),history); }
    public static void main(String[] args) {
        var loan = row(1,10,20,0,DAY); var back = row(1,20,10,0,DAY);
        check(returned(List.of(loan,back)), "same-day unsaved return accepted by row ordering");
        check(returned(List.of(loan,row(1,20,10,0,DAY.plusDays(1)))), "next-day returned history accepted");
        check(!returned(List.of()), "absence never means returned");
        check(!returned(List.of(loan)), "loan alone does not imply return");
        check(!returned(List.of(back)), "return without matching loan refused");
        check(!returned(List.of(back,loan)), "reversed event order refused");
        check(!returned(List.of(loan,row(1,20,11,0,DAY))), "wrong origin refused");
        check(!returned(List.of(loan,row(1,20,10,1,DAY))), "sale is not qualified as return");
        check(!returned(List.of(row(1,10,20,1,DAY),back)), "original history must match zero-amount native loan entry");
        check(!returned(List.of(loan,loan,back)), "duplicate loan history refused");
        check(!returned(List.of(loan,back,back)), "duplicate return refused");
        check(!returned(List.of(loan,back,row(1,10,30,0,DAY))), "later move not silently ignored");
        check(returned(List.of(row(1,30,10,20,DAY.minusDays(1)),loan,row(2,20,10,0,DAY),back)), "unrelated rows and earlier player career do not invalidate exact pair");
        check(!returned(List.of(loan,row(1,20,10,0,DAY.minusDays(1)))), "return before loan refused");
        check(!returned(List.of(loan,row(1,20,10,0,DAY.plusDays(2)))), "future return refused");
        check(!returned(List.of(loan,row(1,20,10,0,null))), "missing date refused");
        System.out.println("PASS: " + checks + " native return evidence/order/ambiguity checks; no effects");
    }
}
