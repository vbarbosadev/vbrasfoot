package br.vbrasfoot;

import java.io.*;
import java.util.*;

/** Immutable policy/finance inputs bound into a v2 loan digest; no retry or migration authority. */
record LoanTerms(int season, List<Integer> games, FinanceDelta.Snapshot originFinance, FinanceDelta.Snapshot destinationFinance, boolean originHuman, String policy, Optional<LoanClubHistory> history) {
    static final String NATIVE_POLICY = "native-listed-no-fee/1";
    boolean nativeListed() { return NATIVE_POLICY.equals(policy); }
    LoanTerms(int season, List<Integer> games, FinanceDelta.Snapshot originFinance, FinanceDelta.Snapshot destinationFinance, boolean originHuman, String policy) {
        this(season, games, originFinance, destinationFinance, originHuman, policy, Optional.empty());
    }
    LoanTerms(int season, List<Integer> games, FinanceDelta.Snapshot originFinance, FinanceDelta.Snapshot destinationFinance, boolean originHuman) {
        this(season, games, originFinance, destinationFinance, originHuman, LoanReleasePolicy.POLICY);
    }
    LoanTerms(int season, List<Integer> games, FinanceDelta.Snapshot originFinance, FinanceDelta.Snapshot destinationFinance) {
        this(season, games, originFinance, destinationFinance, true);
    }
    LoanTerms {
        games = List.copyOf(games);
        if (games.size() > 500 || games.stream().anyMatch(count -> count < -1)) throw new IllegalArgumentException("Invalid participation");
        Objects.requireNonNull(originFinance); Objects.requireNonNull(destinationFinance);
        if (!LoanReleasePolicy.POLICY.equals(policy) && !LoanReleasePolicy.LEGACY_POLICY.equals(policy) && !LoanClubHistory.POLICY.equals(policy) && !NATIVE_POLICY.equals(policy)) throw new IllegalArgumentException("Unknown release policy");
        history = Objects.requireNonNull(history);
        if (LoanClubHistory.POLICY.equals(policy) != history.isPresent()) throw new IllegalArgumentException("Historical policy/input mismatch");
        if (history.isPresent() && (history.orElseThrow().currentSeason() != season || !history.orElseThrow().currentGames().equals(games)))
            throw new IllegalArgumentException("Current and historical inputs diverge");
    }
    LoanReleasePolicy.Result release(int index, List<Integer> strengths, int value) {
        if (nativeListed()) return new LoanReleasePolicy.Result(LoanReleasePolicy.Decision.FREE, 0, "Disponivel para emprestimo no jogo; fluxo nativo sem taxa ou avaliacao adicional do mod");
        return history.isPresent() ? history.orElseThrow().evaluate(index, strengths, value) : LoanReleasePolicy.evaluate(policy, index, strengths, games, value);
    }
    FinanceDelta.Snapshot predicted(boolean origin, int fee) {
        if (fee < 0) throw new IllegalArgumentException("Negative fee");
        var before = origin ? originFinance : destinationFinance;
        if (fee == 0) return before;
        if (origin && !originHuman) return new FinanceDelta.Snapshot(Math.addExact(before.balance(), fee), before.ledger());
        return FinanceDelta.predict(before, origin ? FinanceDelta.Direction.CREDIT_RAW_6 : FinanceDelta.Direction.DEBIT_RAW_7, fee);
    }
    void write(DataOutputStream out) throws IOException {
        out.writeUTF(policy); out.writeInt(season); out.writeInt(games.size());
        for (int count : games) out.writeInt(count);
        finance(out, originFinance); finance(out, destinationFinance);
        out.writeBoolean(originHuman);
        if (history.isPresent()) history.orElseThrow().write(out);
    }
    static LoanTerms read(DataInputStream in) throws IOException {
        String policy = in.readUTF();
        if (!LoanReleasePolicy.POLICY.equals(policy) && !LoanReleasePolicy.LEGACY_POLICY.equals(policy) && !LoanClubHistory.POLICY.equals(policy) && !NATIVE_POLICY.equals(policy)) throw new IOException("Unknown release policy");
        int season = in.readInt(), count = in.readInt();
        if (count < 0 || count > 500) throw new IOException("Invalid participation count");
        var games = new ArrayList<Integer>(); for (int i = 0; i < count; i++) games.add(in.readInt());
        var origin = finance(in); var destination = finance(in); boolean human = in.readBoolean();
        return new LoanTerms(season, games, origin, destination, human, policy,
                LoanClubHistory.POLICY.equals(policy) ? Optional.of(LoanClubHistory.read(in)) : Optional.empty());
    }
    private static void finance(DataOutputStream out, FinanceDelta.Snapshot value) throws IOException {
        out.writeLong(value.balance()); for (String field : new TreeSet<>(FinanceDelta.FIELDS)) out.writeLong(value.ledger().get(field));
    }
    private static FinanceDelta.Snapshot finance(DataInputStream in) throws IOException {
        long balance = in.readLong(); var fields = new TreeMap<String, Long>();
        for (String field : new TreeSet<>(FinanceDelta.FIELDS)) fields.put(field, in.readLong());
        return new FinanceDelta.Snapshot(balance, fields);
    }
}
