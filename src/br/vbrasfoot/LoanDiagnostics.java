package br.vbrasfoot;

/** Internal presentation only. Native return codes are scoped to the fingerprinted loan routine. */
final class LoanDiagnostics {
    private LoanDiagnostics() { }
    record Eligibility(int code, String policyReport, boolean policyApproved, String shortReason) {
        Eligibility(int code) { this(code, "", true, "nao atende aos criterios de emprestimo."); }
        Eligibility(int code, String policyReport, boolean policyApproved) { this(code, policyReport, policyApproved, "nao atende aos criterios de emprestimo."); }
        Eligibility { if (code < 0 || code > 6) throw new IllegalArgumentException("Unknown native preflight code"); }
        boolean eligible() { return code == 1 && policyApproved; }
        String userMessage() {
            if (eligible()) return "Aceito para emprestimo.";
            String reason = code == 1 ? shortReason : switch (code) {
                case 2 -> "jogador ja pertence ao seu clube.";
                case 3 -> "seu clube ja atingiu o limite de tres emprestados.";
                case 4 -> "o jogador nao aceita o clube de destino.";
                case 5 -> "jogador indisponivel para emprestimo.";
                case 6 -> "seu elenco esta cheio (35 jogadores).";
                default -> "jogador ou destino nao esta disponivel.";
            };
            return "Recusado: " + reason;
        }
        String report() { return policyReport.isEmpty() ? preflight(code) : "Consulta da regra NOVA do mod (sem transferencia): "
                + (eligible() ? "APROVADO" : "RECUSADO/DADOS INSUFICIENTES") + "\n" + policyReport
                + (code == 1 ? "" : "\n" + nativeResult(code)); }
    }
    static String nativeResult(int code) {
        return "Retorno nativo " + code + ": " + switch (code) {
            case 0 -> "jogador ou destino ausente";
            case 1 -> "rotina nativa informou sucesso; conferir pos-validacao e resultado duravel";
            case 2 -> "jogador ja pertence ao clube de destino";
            case 3 -> "destino ja tem quatro jogadores emprestados (limite nativo)";
            case 4 -> "regra nativa de elegibilidade entre jogador e clube recusou o destino";
            case 5 -> "jogador nao esta disponivel para emprestimo no jogo";
            case 6 -> "elenco de destino tem 35 ou mais jogadores (limite nativo)";
            default -> "codigo desconhecido; nao repetir a tentativa";
        };
    }
    static String historical() {
        return "Motivo detalhado nao gravado nos pedidos antigos. Nao e possivel reconstruir a recusa passada; "
                + "Verificar elegibilidade nativa consulta somente o estado ATUAL, sem transferir.";
    }
    static String preflight(int code) {
        return code == 1 ? "Pre-verificacao nativa ATUAL: elegivel. Consulta sem transferencia; nao garante execucao futura nem substitui a regra do mod."
                : "Pre-verificacao nativa ATUAL: recusado. " + nativeResult(code) + ". Nenhuma transferencia executada.";
    }
}
