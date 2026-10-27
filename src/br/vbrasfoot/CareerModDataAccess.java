package br.vbrasfoot;

import br.vbrasfoot.api.CareerDataAccess;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Mod-local sidecars isolated by an explicit career association; never writes game save bytes. */
final class CareerModDataAccess implements CareerDataAccess {
    private static final String FORMAT = "vbrasfoot.career-mod-data/1";
    static final int MAX_MOD_VALUE_BYTES = CareerDataAccess.MAX_VALUE_BYTES;
    private static final CareerSidecarStore.SaveRevision ANCHOR = new CareerSidecarStore.SaveRevision("0".repeat(64), 0);
    private final CareerAssociationCoordinator coordinator;
    private final CareerSidecarStore store;
    private final String modId;
    CareerModDataAccess(CareerAssociationCoordinator coordinator, CareerSidecarStore store, String modId) {
        this.coordinator = coordinator; this.store = Objects.requireNonNull(store); this.modId = Objects.requireNonNull(modId);
    }
    @Override public Optional<Scope> current() throws IOException {
        if (coordinator == null) return Optional.empty();
        try { return Optional.ofNullable(coordinator.currentCareerDataScope()); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Career scope lookup interrupted", interrupted); }
    }
    @Override public byte[] read(Scope scope, String file) throws IOException {
        validate(scope, file);
        var key = key(scope, file); var inventory = store.inventoryBinding(scope.career(), scope.binding());
        String token = inventory.tokens().get(key.mod());
        if (token == null) return new byte[0];
        if (EMPTY.equals(token)) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Career mod-data namespace is incomplete; preserve it for review");
        try {
            var value = store.read(key, ANCHOR).value().orElseThrow(
                    () -> new Failure(Reason.RECONCILIATION_REQUIRED, "Career mod-data HEAD disappeared"));
            if (value.schema() != 1) throw new Failure(Reason.CORRUPT, "Unknown career mod-data schema");
            byte[] payload = value.payload();
            if (payload.length < FORMAT.length() || payload.length > CareerSidecarStore.MAX_PAYLOAD) throw new Failure(Reason.CORRUPT, "Career mod-data size invalid");
            try (var input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload))) {
                if (!FORMAT.equals(input.readUTF()) || !modId.equals(input.readUTF()) || !file.equals(input.readUTF()))
                    throw new Failure(Reason.CORRUPT, "Career mod-data owner/key mismatch");
                int length = input.readInt();
                if (length < 0 || length > MAX_MOD_VALUE_BYTES || length > input.available()) throw new Failure(Reason.CORRUPT, "Career mod-data value length invalid");
                byte[] bytes = input.readNBytes(length);
                if (input.available() != 0) throw new Failure(Reason.CORRUPT, "Career mod-data trailing bytes");
                return bytes;
            }
        } catch (CareerSidecarStore.Failure failure) { throw failure; }
    }
    @Override public void write(Scope scope, String file, byte[] bytes) throws IOException {
        validate(scope, file);
        if (bytes == null || bytes.length > MAX_MOD_VALUE_BYTES) throw new IOException("Career mod-data value exceeds the bounded sidecar quota");
        var key = key(scope, file); var inventory = store.inventoryBinding(scope.career(), scope.binding());
        if (EMPTY.equals(inventory.tokens().get(key.mod()))) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Career mod-data namespace is incomplete; preserve it for review");
        var view = store.read(key, ANCHOR);
        var payload = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(payload)) {
            output.writeUTF(FORMAT); output.writeUTF(modId); output.writeUTF(file); output.writeInt(bytes.length); output.write(bytes);
        }
        validate(scope, file);
        store.write(key, ANCHOR, 1, view.token(), payload.toByteArray());
    }
    private void validate(Scope scope, String file) throws IOException {
        if (scope == null || file == null || !file.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,79}"))
            throw new IOException("Invalid career scope or mod-data key");
        if (coordinator == null) throw new IOException("Career data requires --probe-career-hooks and an explicit association selection");
        try { coordinator.validateCareerDataScope(scope); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Career scope validation interrupted", interrupted); }
    }
    private CareerSidecarStore.Key key(Scope scope, String file) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((modId + "\n" + file).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return new Key(scope.career(), scope.binding(), "cd-" + digest);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
