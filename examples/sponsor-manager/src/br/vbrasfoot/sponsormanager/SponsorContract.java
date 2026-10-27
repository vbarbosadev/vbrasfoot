package br.vbrasfoot.sponsormanager;

import java.util.Objects;
import java.util.UUID;

/** Saved configuration only. It is not an active game contract and has no financial effects. */
public final class SponsorContract {
    public enum Cadence {
        ON_ACTIVATION("Uma vez ao ativar (proposta)"), MONTHLY("Mensal (proposta)"), EVERY_N_DAYS("A cada N dias (proposta)");
        private final String label;
        Cadence(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    private final UUID id;
    private final String profile, clubName, sponsorName, stadiumName, message;
    private final int clubId, intervalDays;
    private final long baseAmount, homeWinBonus;
    private final Cadence cadence;
    private final boolean bonusEnabled, namingRights, ended;
    private final byte[] logoPng;

    public SponsorContract(UUID id, String profile, int clubId, String clubName, String sponsorName,
                           long baseAmount, Cadence cadence, int intervalDays, boolean bonusEnabled,
                           long homeWinBonus, boolean namingRights, String stadiumName,
                            byte[] logoPng, boolean ended) {
        this(id, profile, clubId, clubName, sponsorName, baseAmount, cadence, intervalDays, bonusEnabled,
                homeWinBonus, namingRights, stadiumName, logoPng, ended, "A {patrocinador} oferece {valor} ao {clube}. Clique em Receber patrocinio para receber.");
    }
    public SponsorContract(UUID id, String profile, int clubId, String clubName, String sponsorName,
                           long baseAmount, Cadence cadence, int intervalDays, boolean bonusEnabled,
                           long homeWinBonus, boolean namingRights, String stadiumName,
                           byte[] logoPng, boolean ended, String message) {
        this.id = Objects.requireNonNull(id);
        this.profile = bounded(profile, "save profile", 80);
        this.clubName = bounded(clubName, "club", 120);
        this.sponsorName = bounded(sponsorName, "sponsor", 100);
        this.stadiumName = stadiumName == null ? "" : stadiumName.trim();
        if (this.stadiumName.length() > 120) throw new IllegalArgumentException("Nome do estadio muito longo");
        // The current Brasfoot bridge reports -1 for clubs whose native ID is unavailable.
        // The fixed-club view also matches the exact name, so retain that sentinel instead of
        // rejecting a valid human-club selection. Values below -1 remain invalid.
        if (clubId < -1) throw new IllegalArgumentException("ID do clube fora dos limites do prototipo");
        if (baseAmount < 0 || baseAmount > Integer.MAX_VALUE
                || homeWinBonus < 0 || homeWinBonus > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Valor fora dos limites do prototipo");
        this.clubId = clubId;
        this.baseAmount = baseAmount;
        this.cadence = Objects.requireNonNull(cadence);
        if (cadence == Cadence.EVERY_N_DAYS ? intervalDays < 1 || intervalDays > 3660 : intervalDays != 0)
            throw new IllegalArgumentException("Periodicidade invalida");
        if (!bonusEnabled && homeWinBonus != 0) throw new IllegalArgumentException("Bonus desabilitado deve ter valor zero");
        if (bonusEnabled && homeWinBonus == 0) throw new IllegalArgumentException("Informe um bonus positivo ou desative-o");
        if (namingRights && this.stadiumName.isBlank()) throw new IllegalArgumentException("Informe o nome proposto para o estadio");
        if (!namingRights && !this.stadiumName.isBlank()) throw new IllegalArgumentException("Nome do estadio sem naming rights");
        if (logoPng == null) logoPng = new byte[0];
        if (logoPng.length > SponsorLogo.MAX_PNG_BYTES) throw new IllegalArgumentException("Logo PNG acima do limite");
        this.message = bounded(message, "mensagem", 2000);
        this.intervalDays = intervalDays;
        this.bonusEnabled = bonusEnabled;
        this.homeWinBonus = homeWinBonus;
        this.namingRights = namingRights;
        this.logoPng = logoPng.clone();
        this.ended = ended;
    }

    private static String bounded(String value, String label, int maximum) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximum) throw new IllegalArgumentException("Campo " + label + " vazio ou longo demais");
        return trimmed;
    }

    public UUID id() { return id; }
    public String profile() { return profile; }
    public int clubId() { return clubId; }
    public String clubName() { return clubName; }
    public String sponsorName() { return sponsorName; }
    public long baseAmount() { return baseAmount; }
    public Cadence cadence() { return cadence; }
    public int intervalDays() { return intervalDays; }
    public boolean bonusEnabled() { return bonusEnabled; }
    public long homeWinBonus() { return homeWinBonus; }
    public boolean namingRights() { return namingRights; }
    public String stadiumName() { return stadiumName; }
    public byte[] logoPng() { return logoPng.clone(); }
    public boolean ended() { return ended; }
    public String message() { return message; }

    SponsorContract withEnded(boolean value) {
        return new SponsorContract(id, profile, clubId, clubName, sponsorName, baseAmount, cadence,
                intervalDays, bonusEnabled, homeWinBonus, namingRights, stadiumName, logoPng, value, message);
    }
}
