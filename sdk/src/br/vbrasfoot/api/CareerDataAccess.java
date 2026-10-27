package br.vbrasfoot.api;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Mod-owned data bound to a save association explicitly selected this session; this is not game/save write access. */
public interface CareerDataAccess {
    int MAX_VALUE_BYTES = 1024 * 1024 - 320;

    /** Session capability plus exact observed revision. Reacquire after save, reload, association change, or restart. */
    record Scope(UUID career, UUID binding, UUID selection, String saveSha256, long saveSize) {
        public Scope {
            java.util.Objects.requireNonNull(career); java.util.Objects.requireNonNull(binding); java.util.Objects.requireNonNull(selection);
            if (saveSha256 == null || !saveSha256.matches("[a-f0-9]{64}") || saveSize < 0)
                throw new IllegalArgumentException("Invalid explicitly selected save scope");
        }
    }

    /** Empty until the user explicitly selects a verified association for mod data in the career/diary UI. */
    Optional<Scope> current() throws IOException;

    /** Fast session-only selection check; performs no file I/O and grants no read/write authority by itself. */
    default boolean isSelected(Scope scope) { return false; }

    /** Missing values return an empty byte array. File keys are mod-local, bounded identifiers, not paths. */
    byte[] read(Scope scope, String file) throws IOException;

    /** Writes only this mod's sidecar data, never the .s22 file or game state. */
    void write(Scope scope, String file, byte[] bytes) throws IOException;
}
