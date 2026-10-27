package br.vbrasfoot.sponsormanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Explicit logo-display choice only; never records a payment or active financial contract. */
public final class SponsorFeatureStore {
    private static final String MAGIC = "vbrasfoot.sponsor-feature/1";
    private SponsorFeatureStore() { }

    public static byte[] encode(Optional<UUID> id) throws IOException {
        if (id == null) throw new IOException("Selecao de logo ausente");
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF(MAGIC); out.writeBoolean(id.isPresent());
            if (id.isPresent()) out.writeUTF(id.orElseThrow().toString());
        }
        return bytes.toByteArray();
    }

    public static Optional<UUID> decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return Optional.empty();
        if (bytes.length > 128) throw new IOException("Selecao de logo acima do limite");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!MAGIC.equals(in.readUTF())) throw new IOException("Formato de selecao de logo desconhecido");
            Optional<UUID> result = in.readBoolean() ? Optional.of(UUID.fromString(in.readUTF())) : Optional.empty();
            if (in.available() != 0) throw new IOException("Dados extras na selecao de logo");
            return result;
        } catch (IllegalArgumentException invalid) { throw new IOException("ID de logo invalido", invalid); }
    }
}
