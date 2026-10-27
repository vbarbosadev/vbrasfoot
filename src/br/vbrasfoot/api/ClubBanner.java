package br.vbrasfoot.api;

import java.util.Objects;

/** Read-only image banner scoped to an exact current human-club ID/name pair. */
public record ClubBanner(int clubId, String clubName, String label, byte[] png, GameAccess.CareerScope scope) {
    public static final int MAX_PNG_BYTES = 256 * 1024;

    public ClubBanner {
        if (clubId < -1) throw new IllegalArgumentException("Invalid club ID");
        clubName = bounded(clubName, "club name", 120);
        label = bounded(label, "banner label", 120);
        Objects.requireNonNull(scope, "current career query scope");
        Objects.requireNonNull(png, "png");
        if (png.length == 0 || png.length > MAX_PNG_BYTES) throw new IllegalArgumentException("PNG must be 1..256 KiB");
        png = png.clone();
    }

    @Override public byte[] png() { return png.clone(); }

    private static String bounded(String value, String field, int maximum) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximum) throw new IllegalArgumentException("Invalid " + field);
        return trimmed;
    }
}
