package br.vbrasfoot;

/** User-facing text only. Technical reports and exact authorization stay internal. */
final class NativeLoanPresentation {
    private NativeLoanPresentation() { }
    static String refused() { return "Emprestimo recusado."; }
    static String failure(Throwable cause) {
        if (cause instanceof CareerAssociationCoordinator.HistoricalLoanFailure failure)
            return "Recusado: emprestimo anterior nao confirmado neste save.\nJogador(es): "
                    + String.join(", ", failure.players()) + ".\nNenhum novo pedido foi executado.";
        if (cause instanceof NativeLoanAssociation.Failure failure)
            return failure.reason() == NativeLoanAssociation.Reason.AMBIGUOUS
                    ? "Recusado: existem historicos conflitantes para este save. Nenhum registro foi alterado."
                    : "Recusado: nao foi possivel identificar a carreira com seguranca.";
        if (cause instanceof LoadedSaveVerifier.Failure)
            return "Recusado: recarregue seu save antes de outro pedido.";
        return refused();
    }
    static String confirmation(LoanRequest request) {
        return "Emprestimo aceito.\n" + request.player().name() + "\n" + request.origin().name() + " -> " + request.destination().name()
                + (request.fee() == 0 ? "\nSem taxa." : "\nTaxa a pagar: " + request.fee()) + "\nPrazo: 366 dias.\nConfirmar emprestimo?";
    }
    static String confirmationBody(LoanRequest request) {
        return "Emprestimo aceito.\n\n" + request.origin().name() + " -> " + request.destination().name()
                + (request.fee() == 0 ? "\n\nSem taxa." : "\n\nTaxa a pagar: " + request.fee())
                + "\nPrazo: 366 dias.\n\nConfirmar emprestimo?";
    }
    static String cancelled() { return "Emprestimo cancelado. Nenhuma transferencia ou taxa executada."; }
    static String uncertain() {
        return "Resultado incerto. Nao repita o pedido: pode haver transferencia ou cobranca parcial. Revise a carreira e os registros.";
    }
    static String result(CareerCommandDispatcher.Kind kind, int fee) {
        return switch (kind) {
            case EXECUTED -> "Emprestimo realizado. Taxa paga: " + fee + ".\nSalve pelo menu original.";
            case UNCERTAIN -> uncertain();
            case NO_EFFECT, RECORDED_ONLY, DENIED -> refused();
        };
    }
}
