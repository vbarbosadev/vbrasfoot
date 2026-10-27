package br.vbrasfoot.sponsormanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict bounded codec for settings stored outside the career save. */
public final class SponsorStore {
    private static final String MAGIC = "vbrasfoot.sponsor-manager/2";
    public static final int MAX_CONTRACTS = 300;
    public static final int MAX_STORE_BYTES = 4 * 1024 * 1024;
    private SponsorStore() { }

    public static byte[] encode(List<SponsorContract> contracts) throws IOException {
        Objects.requireNonNull(contracts);
        if (contracts.size() > MAX_CONTRACTS) throw new IOException("Limite de configuracoes atingido");
        var ids = new HashSet<UUID>();
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF(MAGIC); out.writeInt(contracts.size());
            for (var item : contracts) {
                if (item == null || !ids.add(item.id())) throw new IOException("Contrato vazio/duplicado");
                writeString(out, item.id().toString(), 36); writeString(out, item.profile(), 80);
                out.writeInt(item.clubId()); writeString(out, item.clubName(), 120); writeString(out, item.sponsorName(), 100);
                out.writeLong(item.baseAmount()); out.writeUTF(item.cadence().name()); out.writeInt(item.intervalDays());
                out.writeBoolean(item.bonusEnabled()); out.writeLong(item.homeWinBonus());
                out.writeBoolean(item.namingRights()); writeString(out, item.stadiumName(), 120);
                out.writeBoolean(item.ended()); byte[] logo = item.logoPng(); out.writeInt(logo.length); out.write(logo);
                writeString(out, item.message(), 2000);
                if (bytes.size() > MAX_STORE_BYTES) throw new IOException("Arquivo de configuracao acima de 4 MiB");
            }
        }
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_STORE_BYTES) throw new IOException("Arquivo de configuracao acima de 4 MiB");
        return result;
    }

    public static List<SponsorContract> decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return List.of();
        if (bytes.length > MAX_STORE_BYTES) throw new IOException("Arquivo de configuracao acima de 4 MiB");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String format = in.readUTF();
            boolean legacy = format.equals("vbrasfoot.sponsor-manager/1");
            if (!MAGIC.equals(format) && !legacy) throw new IOException("Formato de configuracao desconhecido");
            int count = in.readInt(); if (count < 0 || count > MAX_CONTRACTS) throw new IOException("Quantidade de contratos invalida");
            var result = new ArrayList<SponsorContract>(count); var ids = new HashSet<UUID>();
            for (int i = 0; i < count; i++) {
                UUID id = UUID.fromString(readString(in, 36)); if (!ids.add(id)) throw new IOException("Contrato duplicado");
                String profile = readString(in, 80); int clubId = in.readInt(); String club = readString(in, 120);
                String sponsor = readString(in, 100); long base = in.readLong();
                SponsorContract.Cadence cadence = SponsorContract.Cadence.valueOf(in.readUTF()); int interval = in.readInt();
                boolean bonus = in.readBoolean(); long bonusAmount = in.readLong(); boolean naming = in.readBoolean();
                String stadium = readString(in, 120); boolean ended = in.readBoolean();
                int logoLength = in.readInt(); if (logoLength < (legacy ? 1 : 0) || logoLength > SponsorLogo.MAX_PNG_BYTES || logoLength > in.available()) throw new IOException("Tamanho de logo invalido");
                byte[] logo = in.readNBytes(logoLength); if (logoLength > 0) SponsorLogo.decode(logo);
                String message = legacy ? "A {patrocinador} oferece {valor} ao {clube}." : readString(in, 2000);
                result.add(new SponsorContract(id, profile, clubId, club, sponsor, base, cadence, interval,
                        bonus, bonusAmount, naming, stadium, logo, ended, message));
            }
            if (in.available() != 0) throw new IOException("Dados extras no arquivo de configuracao");
            return List.copyOf(result);
        } catch (IllegalArgumentException | ArithmeticException invalid) { throw new IOException("Contrato de patrocinio invalido", invalid); }
    }

    private static void writeString(DataOutputStream out, String value, int maxChars) throws IOException {
        if (value == null || value.length() > maxChars || value.getBytes(StandardCharsets.UTF_8).length > maxChars * 4) throw new IOException("Texto acima do limite");
        out.writeUTF(value);
    }
    private static String readString(DataInputStream in, int maxChars) throws IOException {
        String value = in.readUTF();
        if (value.length() > maxChars || value.getBytes(StandardCharsets.UTF_8).length > maxChars * 4) throw new IOException("Texto acima do limite");
        return value;
    }
}
