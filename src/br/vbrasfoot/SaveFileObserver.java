package br.vbrasfoot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import javax.swing.SwingUtilities;

/** Read-only bounded observation, NOT proof these bytes are the graph currently loaded by the game. */
final class SaveFileObserver {
    static final long MAX_SAVE_BYTES = 256L * 1024 * 1024;
    interface Probe { void afterFirstHash(Path ownedFile) throws IOException; }
    static final class Observation {
        private final Path directory;
        private final String name, fileKey, modified, created;
        private final CareerSidecarStore.SaveRevision revision;
        private Observation(Path directory, String name, String fileKey, String modified, String created, CareerSidecarStore.SaveRevision revision) {
            this.directory = directory; this.name = name; this.fileKey = fileKey; this.modified = modified; this.created = created; this.revision = revision;
        }
        Path directory() { return directory; } String name() { return name; } String fileKey() { return fileKey; }
        String modified() { return modified; } CareerSidecarStore.SaveRevision revision() { return revision; }
        String created() { return created; }
        private boolean same(Observation other) {
            return directory.equals(other.directory) && name.equals(other.name) && fileKey.equals(other.fileKey)
                    && modified.equals(other.modified) && created.equals(other.created) && revision.equals(other.revision);
        }
    }
    private final Probe probe;
    SaveFileObserver() { this(path -> { }); }
    SaveFileObserver(Probe probe) { this.probe = Objects.requireNonNull(probe); }
    Observation observe(Path directory, String name) throws IOException {
        offEdt(); validateName(name); Objects.requireNonNull(directory);
        if (!directory.equals(directory.normalize())) throw new IllegalArgumentException("Normalized save directory required");
        Path root = directory.toAbsolutePath().normalize(); CareerSidecarStore.plain(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.UNSAFE_PATH, "Save directory required");
        Path path = root.resolve(name); CareerSidecarStore.plain(path);
        BasicFileAttributes before = attributes(path);
        if (before.size() > MAX_SAVE_BYTES) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.QUOTA, "Save exceeds observation bound");
        String first, second;
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            first = digest(channel, before.size()); probe.afterFirstHash(path);
            if (!same(before, attributes(path))) throw changed();
            channel.position(0); second = digest(channel, before.size());
            if (channel.size() != before.size()) throw changed();
        }
        CareerSidecarStore.plain(path); BasicFileAttributes after = attributes(path);
        if (!same(before, after) || !first.equals(second)) throw changed();
        return new Observation(root.toRealPath(), path.toRealPath().getFileName().toString(), key(after), after.lastModifiedTime().toString(), after.creationTime().toString(),
                new CareerSidecarStore.SaveRevision(first, after.size()));
    }
    void verify(Observation observed) throws IOException {
        Objects.requireNonNull(observed);
        if (!observed.same(observe(observed.directory(), observed.name()))) throw changed();
    }
    static void validateName(String name) {
        if (name == null || name.length() > 160 || !name.matches("(?i).+\\.(s22|sbck)") || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.startsWith(".") || name.endsWith(" ") || name.endsWith(".")
                || name.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"))
            throw new IllegalArgumentException("One safe .s22/.sbck filename required");
    }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        var value = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!value.isRegularFile() || value.isSymbolicLink() || value.isOther())
            throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.UNSAFE_PATH, "Regular unlinked save file required");
        return value;
    }
    private static String key(BasicFileAttributes value) { return value.fileKey() == null ? "" : value.fileKey().toString(); }
    private static boolean same(BasicFileAttributes a, BasicFileAttributes b) {
        return a.size() == b.size() && a.lastModifiedTime().equals(b.lastModifiedTime()) && a.creationTime().equals(b.creationTime()) && key(a).equals(key(b));
    }
    private static String digest(FileChannel channel, long expectedSize) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024); long count = 0;
        while (channel.read(buffer) >= 0) {
            buffer.flip(); count += buffer.remaining();
            if (count > expectedSize || count > MAX_SAVE_BYTES) throw changed();
            digest.update(buffer); buffer.clear();
        }
        if (count != expectedSize) throw changed();
        return HexFormat.of().formatHex(digest.digest());
    }
    private static CareerSidecarStore.Failure changed() { return new CareerSidecarStore.Failure(CareerSidecarStore.Reason.CONFLICT, "Save changed during observation or before confirmation; observe again"); }
    private static void offEdt() throws CareerSidecarStore.Failure {
        if (SwingUtilities.isEventDispatchThread()) throw new CareerSidecarStore.Failure(CareerSidecarStore.Reason.WRONG_THREAD, "Hash save files off EDT");
    }
}
