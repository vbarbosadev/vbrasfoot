package br.vbrasfoot;

import java.util.*;

/** New mod rule, not native availability. Unknown participation never means zero games. */
final class LoanReleasePolicy {
    static final String LEGACY_POLICY = "origin-median-or-low-use/1";
    static final String POLICY = "origin-median-or-low-use/2";
    enum Decision { FREE, PAID, REFUSE, INSUFFICIENT_DATA }
    record Result(Decision decision, int fee, String reason) {
        boolean approved() { return decision == Decision.FREE || decision == Decision.PAID; }
    }
    private LoanReleasePolicy() { }
    static Result evaluate(int index, List<Integer> strengths, List<Integer> games, int value) {
        return evaluate(POLICY, index, strengths, games, value);
    }
    static Result evaluate(String policy, int index, List<Integer> strengths, List<Integer> games, int value) {
        if (!POLICY.equals(policy) && !LEGACY_POLICY.equals(policy)) throw new IllegalArgumentException("Unknown release policy");
        strengths = List.copyOf(strengths); games = List.copyOf(games);
        if (index < 0 || index >= strengths.size() || strengths.size() != games.size() || value < 0)
            throw new IllegalArgumentException("Invalid release inputs");
        var sorted = strengths.stream().sorted().toList(); int middle = sorted.size() / 2;
        long twiceMedian = sorted.size() % 2 == 0 ? (long) sorted.get(middle - 1) + sorted.get(middle) : 2L * sorted.get(middle);
        if (2L * strengths.get(index) <= twiceMedian) return new Result(Decision.FREE, 0, "Forca ate a mediana da origem: liberado sem taxa");
        if (LEGACY_POLICY.equals(policy) && games.stream().anyMatch(count -> count < 0)) return new Result(Decision.INSUFFICIENT_DATA, 0, "Participacao atual no clube incompleta; ausencia de registro nao significa zero jogos");
        long known = games.stream().filter(count -> count >= 0).count();
        String coverage = " [registros conhecidos: " + known + "/" + games.size() + "; desconhecidos nao contam como zero]";
        if (games.get(index) < 0) return new Result(Decision.INSUFFICIENT_DATA, 0, "Participacao do candidato desconhecida" + coverage);
        int maximum = games.stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maximum < 5) return new Result(Decision.INSUFFICIENT_DATA, 0, "Amostra curta: maior participacao conhecida abaixo de 5 jogos" + coverage);
        if (4L * games.get(index) <= maximum)
            return new Result(Decision.PAID, value / 10, "Acima da mediana, com ate25% da maior participacao conhecida (" + maximum + "): taxa10% do valor (arredondada para baixo)" + coverage);
        if (known < games.size()) return new Result(Decision.INSUFFICIENT_DATA, 0, "Registros conhecidos nao comprovam pouca participacao; faltam dados para concluir" + coverage);
        return new Result(Decision.REFUSE, 0, "Acima da mediana e participacao superior a25% da maior participacao do elenco");
    }
}
