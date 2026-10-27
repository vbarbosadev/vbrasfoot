package br.vbrasfoot;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/** Copied historical observations for THIS club only. Peer maxima are observed lower bounds, not reconstructed old squads. */
record LoanClubHistory(int clubKey, int currentSeason, int rosterSize, List<Season> seasons) {
    static final String POLICY = "origin-club-career-weighted/1";
    record Observation(int clubKey, int season, int games) { }
    static LoanClubHistory capture(int clubKey, int currentSeason, List<List<Observation>> players) {
        players = List.copyOf(players);
        if (players.isEmpty() || players.size() > 500) throw new IllegalArgumentException("Historical roster bound exceeded");
        var perPlayer = new ArrayList<Map<Integer, Integer>>(); var years = new TreeSet<Integer>();
        for (var records : players) {
            records = List.copyOf(records); if (records.size() > 500) throw new IllegalArgumentException("Historical player bound exceeded");
            var counts = new HashMap<Integer, Integer>(); var seen = new HashSet<Integer>();
            for (var row : records) if (row.clubKey() == clubKey && row.season() > 0 && row.season() <= currentSeason) {
                if (!seen.add(row.season())) counts.put(row.season(), -1);
                else counts.put(row.season(), row.games() >= 0 ? row.games() : -1);
                years.add(row.season());
            }
            perPlayer.add(Map.copyOf(counts));
        }
        if (years.size() > 128 || (long) years.size() * players.size() > 16000) throw new IllegalArgumentException("Historical club bound exceeded");
        var seasons = new ArrayList<Season>();
        for (int year : years) seasons.add(new Season(year, perPlayer.stream().map(counts -> counts.getOrDefault(year, -1)).toList()));
        return new LoanClubHistory(clubKey, currentSeason, players.size(), seasons);
    }
    record Season(int season, List<Integer> games) {
        Season { games = List.copyOf(games); if (season < 1 || games.stream().anyMatch(count -> count < -1)) throw new IllegalArgumentException("Invalid historical participation"); }
    }
    LoanClubHistory {
        seasons = List.copyOf(seasons);
        if (currentSeason < 1 || rosterSize < 1 || rosterSize > 500 || seasons.size() > 128 || (long) seasons.size() * rosterSize > 16000)
            throw new IllegalArgumentException("Club career history bound exceeded");
        int previous = 0;
        for (var season : seasons) {
            if (season.season() <= previous || season.season() > currentSeason || season.games().size() != rosterSize)
                throw new IllegalArgumentException("Historical season scope/order mismatch");
            previous = season.season();
        }
    }
    List<Integer> currentGames() {
        return seasons.stream().filter(row -> row.season() == currentSeason).map(Season::games).findFirst()
                .orElseGet(() -> Collections.nCopies(rosterSize, -1));
    }
    LoanReleasePolicy.Result evaluate(int index, List<Integer> strengths, int value) {
        if (strengths.size() != rosterSize || index < 0 || index >= rosterSize || value < 0) throw new IllegalArgumentException("History/player mismatch");
        // The free strength branch does not require participation, preserving unknown != zero.
        var strength = LoanReleasePolicy.evaluate(index, strengths, Collections.nCopies(rosterSize, -1), value);
        if (strength.approved()) return strength;
        long currentGames = -1, currentMaximum = 0, priorGames = 0, priorMaximum = 0; int years = 0;
        for (var row : seasons) {
            int games = row.games().get(index);
            if (games < 0) continue; // Missing/ambiguous observations never become zero or another club's games.
            int maximum = row.games().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (row.season() == currentSeason) { currentGames = games; currentMaximum = maximum; }
            else { priorGames = Math.addExact(priorGames, games); priorMaximum = Math.addExact(priorMaximum, maximum); years++; }
        }
        boolean current = currentGames >= 0 && currentMaximum >= 5, prior = years > 0 && priorMaximum >= 5;
        String detail = "Clube " + clubKey + ": temporada atual=" + currentGames + "/" + currentMaximum
                + "; carreira anterior NESTE clube=" + priorGames + "/" + priorMaximum + " em " + years + " temporadas registradas."
                + " Maximos conhecidos sao limites inferiores; dados ausentes nao sao zero e outros clubes nao entram.";
        if (!current && !prior) return new LoanReleasePolicy.Result(LoanReleasePolicy.Decision.INSUFFICIENT_DATA, 0,
                "Historico insuficiente para avaliar pouca participacao. " + detail);
        boolean low;
        if (current && prior) {
            var numerator = big(currentGames).multiply(big(priorMaximum)).multiply(big(7))
                    .add(big(priorGames).multiply(big(currentMaximum)).multiply(big(3)));
            low = numerator.multiply(big(4)).compareTo(big(currentMaximum).multiply(big(priorMaximum)).multiply(big(10))) <= 0;
            detail += " Peso70% atual/30% historico anterior do clube.";
        } else {
            long games = current ? currentGames : priorGames, maximum = current ? currentMaximum : priorMaximum;
            low = big(games).multiply(big(4)).compareTo(big(maximum)) <= 0;
            detail += current ? " Primeira temporada registrada ou historico anterior insuficiente: somente dados atuais do clube."
                    : " Temporada atual insuficiente: somente historico conhecido deste clube.";
        }
        return low ? new LoanReleasePolicy.Result(LoanReleasePolicy.Decision.PAID, value / 10,
                "Pouca participacao comprovada (ate25%); taxa10% do valor, arredondada para baixo. " + detail)
                : new LoanReleasePolicy.Result(LoanReleasePolicy.Decision.REFUSE, 0, "Pouca participacao nao comprovada pelo historico do clube. " + detail);
    }
    private static BigInteger big(long value) { return BigInteger.valueOf(value); }
    void write(DataOutputStream out) throws IOException {
        out.writeInt(clubKey); out.writeInt(currentSeason); out.writeInt(rosterSize); out.writeInt(seasons.size());
        for (var row : seasons) { out.writeInt(row.season()); for (int games : row.games()) out.writeInt(games); }
    }
    static LoanClubHistory read(DataInputStream in) throws IOException {
        int club = in.readInt(), current = in.readInt(), size = in.readInt(), count = in.readInt();
        if (size < 1 || size > 500 || count < 0 || count > 128 || (long) count * size > 16000) throw new IOException("Historical input bound exceeded");
        var seasons = new ArrayList<Season>();
        for (int i = 0; i < count; i++) { int year = in.readInt(); var games = new ArrayList<Integer>(); for (int p = 0; p < size; p++) games.add(in.readInt()); seasons.add(new Season(year, games)); }
        return new LoanClubHistory(club, current, size, seasons);
    }
}
