package br.vbrasfoot;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** UI routing only. This is never a permit: the coordinator/adapter must still revalidate and journal the effect. */
final class NativeLoanFlow {
    private NativeLoanFlow() { }
    static boolean proceed(LoanRequest request, BooleanSupplier currentSelection, BooleanSupplier confirmProposal) {
        Objects.requireNonNull(request); Objects.requireNonNull(currentSelection); Objects.requireNonNull(confirmProposal);
        if (!currentSelection.getAsBoolean()) throw new IllegalStateException("Selecao mudou durante avaliacao; nenhum emprestimo solicitado");
        if (request.assessment().decision() != LoanClubAssessment.Decision.APPROVE) return false;
        // The already-listed flag is part of the exact immutable quote and is checked again immediately before the effect.
        return request.nativeListed() || confirmProposal.getAsBoolean();
    }
}
