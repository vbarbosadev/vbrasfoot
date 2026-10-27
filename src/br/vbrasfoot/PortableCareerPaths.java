package br.vbrasfoot;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Installation-relative location only. Never generates IDs, edits histories or infers effect inclusion. */
final class PortableCareerPaths {
    static final String MARKER = "portable-root.properties";
    private final Path saves, marker;
    PortableCareerPaths(Path associations, Path saves) {
        this.saves = saves == null ? null : saves.toAbsolutePath().normalize();
        marker = associations.resolve(".identity-registry").resolve(MARKER);
    }
    boolean relative() { return saves != null; }
    String directoryReference(Path directory) {
        if (saves == null) return directory.toString();
        if (!saves.equals(directory)) throw new IllegalArgumentException("Save outside selected installation");
        return ".";
    }
    Path directory(String reference, int format) throws IOException {
        if (format == 2) {
            if (saves == null || !reference.equals(".")) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Portable association requires the current installation save directory");
            return saves;
        }
        Path original = Path.of(reference);
        if (!original.isAbsolute() || !original.equals(original.normalize())) throw new IOException("Invalid legacy directory");
        Optional<Path> legacy = legacyRoot();
        return saves != null && legacy.isPresent() && original.equals(legacy.orElseThrow()) ? saves : original;
    }
    String sourceReference(Path source) {
        if (saves == null) return source.toString();
        directoryReference(source.getParent());
        return source.getFileName().toString();
    }
    Path source(String reference, int format) throws IOException {
        if (format == 2) {
            SaveFileObserver.validateName(reference);
            if (saves == null) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Portable group requires the current installation save directory");
            return saves.resolve(reference);
        }
        Path old = Path.of(reference);
        if (!old.isAbsolute() || !old.equals(old.normalize()) || old.getParent() == null) throw new IOException("Invalid legacy source");
        return directory(old.getParent().toString(), 1).resolve(old.getFileName());
    }
    Optional<Path> legacyRoot() throws IOException {
        plain(marker);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 16384) throw new IOException("Invalid portable root marker");
        var values = new Properties();
        try (var in = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) { values.load(in); }
        if (!values.stringPropertyNames().equals(Set.of("format", "legacyDirectory")) || !"1".equals(values.getProperty("format")))
            throw new IOException("Unknown portable root marker");
        Path path = Path.of(values.getProperty("legacyDirectory"));
        if (!path.isAbsolute() || !path.equals(path.normalize()) || path.toString().length() > 4096) throw new IOException("Invalid portable legacy directory");
        return Optional.of(path);
    }
    /** Caller holds the identity lock and has explicitly selected the old installation root. */
    void enable(Path legacy) throws IOException {
        if (saves == null || !legacy.isAbsolute() || !legacy.equals(legacy.normalize())) throw new IllegalArgumentException("Explicit installation directories required");
        var previous = legacyRoot();
        if (previous.isPresent()) {
            if (!previous.orElseThrow().equals(legacy)) throw new Failure(Reason.CONFLICT, "Portable root already selected; never overwrite identity mapping");
            return;
        }
        var values = new Properties(); values.setProperty("format", "1"); values.setProperty("legacyDirectory", legacy.toString());
        var bytes = new ByteArrayOutputStream(); values.store(bytes, "Legacy location only; career IDs and all histories preserved");
        Path staged = marker.resolveSibling("pending-portable-" + UUID.randomUUID());
        plain(staged);
        try (var out = FileChannel.open(staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes.toByteArray()); while (buffer.hasRemaining()) out.write(buffer); out.force(true);
        }
        Files.move(staged, marker, StandardCopyOption.ATOMIC_MOVE);
    }
}
