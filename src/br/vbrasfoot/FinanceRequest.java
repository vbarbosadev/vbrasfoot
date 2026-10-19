package br.vbrasfoot;

import java.io.*;
import java.security.*;
import java.util.*;

/** Internal reproducible payload, NOT authority, a stored effect witness, or a cross-revision club identity. */
record FinanceRequest(UUID career, UUID binding, CareerSidecarStore.SaveRevision save, String owner, String key,
                      Club club, FinanceDelta.Direction direction, int amount, FinanceDelta.Snapshot before) {
    private static final String FORMAT = "vbrasfoot.finance-request/1";
    private static final int MAX_BYTES = 4096;
    record Club(int index, int id, String name) {
        Club {
            if (index < 0 || index > 100000 || name == null || name.isBlank() || name.length() > 512)
                throw new IllegalArgumentException("Bounded exact club locator required");
        }
    }
    FinanceRequest {
        Objects.requireNonNull(career); Objects.requireNonNull(binding); Objects.requireNonNull(save);
        Objects.requireNonNull(club); Objects.requireNonNull(direction); Objects.requireNonNull(before);
        new CareerSidecarStore.Key(career, binding, owner); // Same owner namespace rules as the journal.
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Durable request key required");
        FinanceDelta.predict(before, direction, amount);
    }
    CareerCommandGate.Operation operation() {
        return direction == FinanceDelta.Direction.CREDIT_RAW_6 ? CareerCommandGate.Operation.FINANCE_CREDIT : CareerCommandGate.Operation.FINANCE_DEBIT;
    }
    boolean matches(CareerCommandGate.Context context, String owner) {
        return context != null && this.owner.equals(owner) && career.equals(context.career())
                && binding.equals(context.binding()) && save.equals(context.save());
    }
    CareerCommandJournal.Intent intent() { return new CareerCommandJournal.Intent(key, operation(), digest()); }
    String digest() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode())); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeUTF(FORMAT); out.writeUTF(Profile.SHA256);
            uuid(out, career); uuid(out, binding); out.writeUTF(save.sha256()); out.writeLong(save.size());
            out.writeUTF(owner); out.writeUTF(key); out.writeInt(club.index()); out.writeInt(club.id()); out.writeUTF(club.name());
            out.writeUTF(direction.name()); out.writeInt(amount); out.writeLong(before.balance());
            for (String field : new TreeSet<>(FinanceDelta.FIELDS)) out.writeLong(before.ledger().get(field));
            out.flush(); byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Request too large");
            return result;
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    static FinanceRequest decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Missing or oversized finance request");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (!FORMAT.equals(in.readUTF()) || !Profile.SHA256.equals(in.readUTF())) throw new IOException("Unsupported request format/profile");
            UUID career = uuid(in), binding = uuid(in);
            var save = new CareerSidecarStore.SaveRevision(in.readUTF(), in.readLong());
            String owner = in.readUTF(), key = in.readUTF();
            var club = new Club(in.readInt(), in.readInt(), in.readUTF());
            var direction = FinanceDelta.Direction.valueOf(in.readUTF()); int amount = in.readInt(); long balance = in.readLong();
            var ledger = new TreeMap<String, Long>();
            for (String field : new TreeSet<>(FinanceDelta.FIELDS)) ledger.put(field, in.readLong());
            var request = new FinanceRequest(career, binding, save, owner, key, club, direction, amount, new FinanceDelta.Snapshot(balance, ledger));
            if (in.available() != 0 || !Arrays.equals(bytes, request.encode())) throw new IOException("Noncanonical or trailing request data");
            return request;
        } catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Invalid finance request", invalid); }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
