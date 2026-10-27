package br.vbrasfoot;

import java.time.LocalDate;
import java.util.*;

/** Native transfer-history round trip, not a claim about prior fee inclusion or refund. */
final class LoanReturnEvidence {
    record Transfer(int playerId, int origin, int destination, int amount, LocalDate date) { }
    static boolean returned(int playerId, int origin, int destination, LocalDate loanDate, LocalDate currentDate, List<Transfer> history) {
        if (history.size() > 64000 || currentDate.isBefore(loanDate)) return false;
        boolean loan = false, returned = false;
        for (var row : history) {
            if (row.playerId() != playerId) continue;
            if (row.date() == null) return false;
            if (!loan) {
                if (row.origin() == origin && row.destination() == destination && row.amount() == 0 && row.date().equals(loanDate)) loan = true;
                continue;
            }
            // No intervening sale, second loan, ambiguous duplicate or later move may be ignored.
            if (returned || row.origin() != destination || row.destination() != origin || row.amount() != 0
                    || row.date().isBefore(loanDate) || row.date().isAfter(currentDate)) return false;
            returned = true;
        }
        return loan && returned;
    }
}
