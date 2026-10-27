package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.Player;
import java.io.*;
import java.security.*;
import java.util.*;

/** Immutable revision-bound internal contract. Locators are not global identity or write authority. */
record LoanRequest(UUID career, UUID binding, CareerSidecarStore.SaveRevision save, String owner, String key,
                   FinanceRequest.Club origin, FinanceRequest.Club destination, int playerIndex,
                    List<Player> originRoster, List<Player> destinationRoster, long clock, Optional<LoanTerms> terms) {
    static final int MAX_BYTES = 128 * 1024;
    private static final String FORMAT = "vbrasfoot.loan-request/1";
    LoanRequest(UUID career, UUID binding, CareerSidecarStore.SaveRevision save, String owner, String key,
                FinanceRequest.Club origin, FinanceRequest.Club destination, int playerIndex,
                List<Player> originRoster, List<Player> destinationRoster, long clock) {
        this(career, binding, save, owner, key, origin, destination, playerIndex, originRoster, destinationRoster, clock, Optional.empty());
    }
    LoanRequest {
        Objects.requireNonNull(career); Objects.requireNonNull(binding); Objects.requireNonNull(save);
        new CareerSidecarStore.Key(career, binding, owner);
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid request key");
        Objects.requireNonNull(origin); Objects.requireNonNull(destination);
        if (origin.index() == destination.index()) throw new IllegalArgumentException("Distinct club positions required");
        originRoster = roster(originRoster); destinationRoster = roster(destinationRoster);
        if (playerIndex < 0 || playerIndex >= originRoster.size()) throw new IllegalArgumentException("Player outside origin roster");
        Math.addExact(clock, 366L * 86400000L);
        terms = Objects.requireNonNull(terms);
        if (terms.isPresent() && terms.orElseThrow().games().size() != originRoster.size()) throw new IllegalArgumentException("Participation roster mismatch");
    }
    private static List<Player> roster(List<Player> values) {
        var copy = List.copyOf(values);
        if (copy.size() > 500) throw new IllegalArgumentException("Oversized roster");
        for (Player player : copy) if (player.name() == null || player.name().isBlank() || player.name().length() > 512)
            throw new IllegalArgumentException("Bounded player name required");
        return copy;
    }
    Player player() { return originRoster.get(playerIndex); }
    boolean nativeListed() { return terms.isPresent() && terms.orElseThrow().nativeListed(); }
    LoanClubAssessment.Result assessment() {
        if (nativeListed()) return new LoanClubAssessment.Result(destinationRoster.size() >= 35 ? LoanClubAssessment.Decision.REFUSE : LoanClubAssessment.Decision.APPROVE,
                destinationRoster.size() >= 35 ? LoanClubAssessment.Reason.DESTINATION_FULL : LoanClubAssessment.Reason.NATIVE_LISTED);
        if (terms.isPresent()) {
            var release = release();
            if (!release.approved()) return new LoanClubAssessment.Result(release.decision() == LoanReleasePolicy.Decision.REFUSE
                    ? LoanClubAssessment.Decision.REFUSE : LoanClubAssessment.Decision.INSUFFICIENT_DATA, LoanClubAssessment.Reason.ORIGIN_NOT_RELEASED);
            if (fee() > terms.orElseThrow().destinationFinance().balance())
                return new LoanClubAssessment.Result(LoanClubAssessment.Decision.REFUSE, LoanClubAssessment.Reason.INSUFFICIENT_FEE_BALANCE);
        }
        return LoanClubAssessment.evaluate(player().strength(), destinationRoster.stream().map(Player::strength).toList());
    }
    LoanReleasePolicy.Result release() {
        if (terms.isEmpty()) return new LoanReleasePolicy.Result(LoanReleasePolicy.Decision.REFUSE, 0, "Contrato nativo antigo; sem regra nova ou taxa retroativa");
        return terms.orElseThrow().release(playerIndex, originRoster.stream().map(Player::strength).toList(), player().value());
    }
    int fee() { return terms.isEmpty() ? 0 : release().fee(); }
    String termsReport() { return terms.isEmpty() ? "Regra nativa antiga; taxa0" : release().reason() + " / taxa: " + fee() + " / destino: " + assessment().reason(); }
    boolean matches(CareerCommandGate.Context context, String expectedOwner) {
        return context != null && owner.equals(expectedOwner) && career.equals(context.career())
                && binding.equals(context.binding()) && save.equals(context.save());
    }
    CareerCommandJournal.Intent intent() { return new CareerCommandJournal.Intent(key, CareerCommandGate.Operation.LOAN_ACCEPT, digest()); }
    String digest() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode())); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeUTF(terms.isPresent() ? "vbrasfoot.loan-request/2" : FORMAT); out.writeUTF(Profile.SHA256); out.writeUTF(LoanClubAssessment.POLICY);
            uuid(out, career); uuid(out, binding); out.writeUTF(save.sha256()); out.writeLong(save.size());
            out.writeUTF(owner); out.writeUTF(key); club(out, origin); club(out, destination);
            out.writeInt(playerIndex); players(out, originRoster); players(out, destinationRoster); out.writeLong(clock);
            if (terms.isPresent()) terms.orElseThrow().write(out);
            out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Oversized loan payload");
            return result;
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    static LoanRequest decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Missing or oversized loan payload");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String format = in.readUTF();
            if ((!FORMAT.equals(format) && !"vbrasfoot.loan-request/2".equals(format)) || !Profile.SHA256.equals(in.readUTF()) || !LoanClubAssessment.POLICY.equals(in.readUTF()))
                throw new IOException("Unsupported loan format/profile/policy");
            var legacy = new LoanRequest(uuid(in), uuid(in), new CareerSidecarStore.SaveRevision(in.readUTF(), in.readLong()),
                     in.readUTF(), in.readUTF(), club(in), club(in), in.readInt(), players(in), players(in), in.readLong());
            var request = FORMAT.equals(format) ? legacy : new LoanRequest(legacy.career(), legacy.binding(), legacy.save(), legacy.owner(), legacy.key(),
                    legacy.origin(), legacy.destination(), legacy.playerIndex(), legacy.originRoster(), legacy.destinationRoster(), legacy.clock(), Optional.of(LoanTerms.read(in)));
            if (in.available() != 0 || !Arrays.equals(bytes, request.encode())) throw new IOException("Noncanonical loan payload");
            return request;
        } catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Invalid loan payload", invalid); }
    }
    private static void club(DataOutputStream out, FinanceRequest.Club club) throws IOException {
        out.writeInt(club.index()); out.writeInt(club.id()); out.writeUTF(club.name());
    }
    private static FinanceRequest.Club club(DataInputStream in) throws IOException { return new FinanceRequest.Club(in.readInt(), in.readInt(), in.readUTF()); }
    private static void players(DataOutputStream out, List<Player> players) throws IOException {
        out.writeInt(players.size());
        for (Player p : players) { out.writeInt(p.id()); out.writeUTF(p.name()); out.writeInt(p.age()); out.writeInt(p.position()); out.writeInt(p.strength()); out.writeInt(p.value()); }
    }
    private static List<Player> players(DataInputStream in) throws IOException {
        int count = in.readInt(); if (count < 0 || count > 500) throw new IOException("Invalid roster size");
        var result = new ArrayList<Player>();
        for (int i = 0; i < count; i++) result.add(new Player(in.readInt(), in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readInt()));
        return result;
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
