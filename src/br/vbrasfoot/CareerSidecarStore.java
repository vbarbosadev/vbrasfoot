package br.vbrasfoot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import javax.swing.SwingUtilities;

/** Internal storage primitive only. No automatic save association or public mod service. */
final class CareerSidecarStore {
    static final int MAX_PAYLOAD = 1024 * 1024, MAX_FILES = 128;
    static final long MAX_BYTES = 16L * 1024 * 1024;
    static final int MAX_BINDING_NAMESPACES = 64;
    record NamespaceInventory(Map<String, String> tokens) {
        NamespaceInventory { tokens = Map.copyOf(tokens); }
    }
    static final String EMPTY = "EMPTY";
    static final String ASSOCIATION_OWNER = "br.vbrasfoot.association";
    enum Reason { WRONG_THREAD, UNSAFE_PATH, CORRUPT, CONFLICT, SAVE_REVISION_MISMATCH, RECONCILIATION_REQUIRED, BUSY, QUOTA, NOT_FOUND, ATOMIC_MOVE_UNSUPPORTED, OUTCOME_UNKNOWN }
    static final class Failure extends IOException {
        private final Reason reason;
        Failure(Reason reason, String message) { super(message); this.reason = reason; }
        Failure(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
        Reason reason() { return reason; }
    }
    record Key(UUID career, UUID binding, String mod) {
        Key {
            Objects.requireNonNull(career); Objects.requireNonNull(binding);
            if (mod == null || !mod.matches("[a-z0-9][a-z0-9._-]{0,99}") || mod.endsWith(".")
                    || mod.equals(".") || mod.equals("..") || mod.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"))
                throw new IllegalArgumentException("Invalid mod namespace");
        }
    }
    /** Identifies observed bytes of one revision, never the logical identity of a career. */
    record SaveRevision(String sha256, long size) {
        SaveRevision { if (sha256 == null || !sha256.matches("[a-f0-9]{64}") || size < 0) throw new IllegalArgumentException("Invalid save revision"); }
    }
    record Value(long revision, int schema, SaveRevision save, byte[] payload) {
        Value {
            if (revision < 1 || schema < 1) throw new IllegalArgumentException("Invalid revision/schema");
            Objects.requireNonNull(save); Objects.requireNonNull(payload);
            if (payload.length > MAX_PAYLOAD) throw new IllegalArgumentException("Payload exceeds quota");
            payload = payload.clone();
        }
        @Override public byte[] payload() { return payload.clone(); }
    }
    record View(String token, Optional<Value> value, boolean previousRecorded) { }
    enum Stage { RECORD_WRITTEN, HEAD_STAGED, HEAD_COMMITTED }
    interface FaultProbe { void at(Stage stage) throws IOException; }
    private record Pointer(UUID record, long revision, String digest) { }
    private record Head(Pointer current, Pointer previous, String token) { }
    private static final int RECORD_MAGIC = 0x56424352, HEAD_MAGIC = 0x56424348, FORMAT = 1;
    private final Path root;
    private final FaultProbe probe;
    CareerSidecarStore(Path root) { this(root, stage -> { }); }
    CareerSidecarStore(Path root, FaultProbe probe) {
        Objects.requireNonNull(root);
        if (!root.equals(root.normalize())) throw new IllegalArgumentException("Normalize sidecar root explicitly before selecting it");
        this.root = root.toAbsolutePath().normalize(); this.probe = Objects.requireNonNull(probe);
    }

    View read(Key key, SaveRevision expectedSave) throws IOException {
        offEdt(); Objects.requireNonNull(expectedSave);
        View view = inspect(key);
        if (view.value().isPresent()) requireSave(view.value().orElseThrow(), expectedSave);
        return view;
    }
    /** Administrative bounded read of ONE explicitly supplied binding. No UUID/path/hash identity search.
     * EMPTY token here means an existing uncommitted namespace, NOT proof of an empty command history. */
    NamespaceInventory inventoryBinding(UUID career, UUID binding) throws IOException {
        offEdt(); Objects.requireNonNull(career); Objects.requireNonNull(binding);
        Path selected = root.resolve(career.toString()).resolve(binding.toString()); plain(selected);
        BasicFileAttributes selectedAttributes;
        try { selectedAttributes = Files.readAttributes(selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException missing) { return new NamespaceInventory(Map.of()); }
        if (!selectedAttributes.isDirectory()) throw new Failure(Reason.UNSAFE_PATH, "Selected binding is not a directory");
        long started = System.nanoTime(); Map<String, String> tokens = new TreeMap<>();
        try (var entries = Files.newDirectoryStream(selected)) {
            for (Path entry : entries) {
                if (tokens.size() >= MAX_BINDING_NAMESPACES) throw new Failure(Reason.QUOTA, "Binding namespace limit exceeded; no partial inventory accepted");
                inventoryBudget(started); plain(entry); String owner = entry.getFileName().toString();
                try { new Key(career, binding, owner); }
                catch (IllegalArgumentException unsafe) { throw new Failure(Reason.UNSAFE_PATH, "Unknown namespace entry preserved", unsafe); }
                if (!Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
                    throw new Failure(Reason.UNSAFE_PATH, "Namespace entry is not a directory");
                int count = 0; long bytes = 0;
                try (var files = Files.newDirectoryStream(entry)) {
                    for (Path file : files) {
                        inventoryBudget(started); plain(file);
                        if (++count > MAX_FILES) throw new Failure(Reason.QUOTA, "Namespace file inventory limit exceeded");
                        String name = file.getFileName().toString();
                        if (!(name.equals("HEAD") || name.equals(".lock") || name.matches("r-[a-f0-9-]{36}\\.bin") || name.matches("HEAD\\.tmp-[a-f0-9-]{36}")))
                            throw new Failure(Reason.UNSAFE_PATH, "Unfamiliar namespace contents preserved");
                        var attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        if (!attrs.isRegularFile()) throw new Failure(Reason.UNSAFE_PATH, "Namespace contains non-file entry");
                        if (attrs.size() > MAX_BYTES - bytes) throw new Failure(Reason.QUOTA, "Namespace byte inventory limit exceeded");
                        bytes += attrs.size();
                    }
                }
                Head head = readHead(entry); tokens.put(owner, head == null ? EMPTY : head.token());
            }
        } catch (DirectoryIteratorException failure) { throw failure.getCause(); }
        inventoryBudget(started); plain(selected); return new NamespaceInventory(tokens);
    }
    private static void inventoryBudget(long started) throws Failure {
        if (System.nanoTime() - started > 5_000_000_000L) throw new Failure(Reason.QUOTA, "Binding inventory time limit exceeded; no partial result accepted");
    }
    /** Administrative association metadata only, never a way for a mod to bypass revision checks. */
    View inspectAssociation(Key key) throws IOException {
        offEdt(); requireAssociation(key); return inspect(key);
    }
    private View inspect(Key key) throws IOException {
        Path directory = directory(key); plain(directory);
        Head head = readHead(directory);
        if (head == null) return new View(EMPTY, Optional.empty(), false);
        Value value = readValue(directory, key, head.current());
        unchanged(directory, head.token());
        return new View(head.token(), Optional.of(value), head.previous() != null);
    }
    Value readPrevious(Key key, SaveRevision expectedSave, String expectedToken) throws IOException {
        offEdt(); Objects.requireNonNull(expectedSave); requireToken(expectedToken);
        Path directory = directory(key); plain(directory); Head head = requireHead(directory, expectedToken);
        if (head == null || head.previous() == null) throw new Failure(Reason.NOT_FOUND, "No previous committed revision");
        Value value = readValue(directory, key, head.previous()); requireSave(value, expectedSave);
        unchanged(directory, head.token()); return value;
    }
    View write(Key key, SaveRevision save, int schema, String expectedToken, byte[] payload) throws IOException {
        offEdt(); requireToken(expectedToken);
        Value input = new Value(1, schema, save, payload); // Validate and detach before touching disk.
        return mutate(key, save, expectedToken, input, false, save, false);
    }
    /** Only association metadata may explicitly change its observed save anchor. Other namespaces stay blocked. */
    View changeAssociationRevision(Key key, SaveRevision previous, SaveRevision next, int schema, String expectedToken, byte[] payload) throws IOException {
        offEdt(); requireAssociation(key); Objects.requireNonNull(previous); requireToken(expectedToken);
        return mutate(key, next, expectedToken, new Value(1, schema, next, payload), false, previous, true);
    }
    private static void requireAssociation(Key key) {
        if (!Objects.requireNonNull(key).mod().equals(ASSOCIATION_OWNER)) throw new IllegalArgumentException("Administrative association namespace required");
    }
    /** Explicit recovery only; corrupt HEAD never triggers a scan for the newest orphan. */
    View recoverPrevious(Key key, SaveRevision save, String expectedToken) throws IOException {
        offEdt(); Objects.requireNonNull(save); requireToken(expectedToken);
        return mutate(key, save, expectedToken, null, true, save, true);
    }
    private View mutate(Key key, SaveRevision save, String expectedToken, Value input, boolean recover, SaveRevision expectedPrevious, boolean requireExisting) throws IOException {
        Path directory = directory(key); makeDirectories(directory); plain(directory);
        Path lockPath = directory.resolve(".lock"); plain(lockPath);
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            FileLock lock;
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { throw new Failure(Reason.BUSY, "Sidecar writer already active", busy); }
            if (lock == null) throw new Failure(Reason.BUSY, "Sidecar writer already active");
            try (lock) {
                plain(directory); Head old = requireHead(directory, expectedToken);
                if (old == null && requireExisting) throw new Failure(Reason.NOT_FOUND, "Existing committed association/recovery source required");
                Pointer previous = old == null ? null : old.current();
                if (recover) {
                    if (old == null || old.previous() == null) throw new Failure(Reason.NOT_FOUND, "No previous committed revision");
                    input = readValue(directory, key, old.previous()); requireSave(input, save); previous = old.previous();
                } else if (old != null) requireSave(readValue(directory, key, old.current()), expectedPrevious);
                long revision;
                try { revision = old == null ? 1 : Math.addExact(old.current().revision(), 1); }
                catch (ArithmeticException exhausted) { throw new Failure(Reason.QUOTA, "Revision counter exhausted", exhausted); }
                Value next = new Value(revision, input.schema(), save, input.payload());
                byte[] recordBytes = recordBytes(key, next);
                Pointer pointer = new Pointer(UUID.randomUUID(), revision, digest(recordBytes));
                byte[] headBytes = headBytes(pointer, previous);
                quota(directory, recordBytes.length + headBytes.length);
                Path record = recordPath(directory, pointer), staged = directory.resolve("HEAD.tmp-" + UUID.randomUUID());
                boolean moved = false;
                try {
                    writeNew(record, recordBytes); probe.at(Stage.RECORD_WRITTEN);
                    writeNew(staged, headBytes); probe.at(Stage.HEAD_STAGED);
                    unchanged(directory, expectedToken); plain(directory.resolve("HEAD"));
                    try { Files.move(staged, directory.resolve("HEAD"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException unsupported) { throw new Failure(Reason.ATOMIC_MOVE_UNSUPPORTED, "Atomic HEAD replacement required; no fallback", unsupported); }
                    catch (IOException uncertain) { throw new Failure(Reason.OUTCOME_UNKNOWN, "HEAD move failed; inspect state before retrying", uncertain); }
                    moved = true; probe.at(Stage.HEAD_COMMITTED);
                    return new View(digest(headBytes), Optional.of(next), previous != null);
                } catch (IOException failure) {
                    if (moved) throw new Failure(Reason.OUTCOME_UNKNOWN, "HEAD may be committed; read and compare before retrying", failure);
                    throw failure; // Preserve transaction-owned orphan files; never silently promote or delete them.
                }
            }
        }
    }
    private Path directory(Key key) {
        Objects.requireNonNull(key); return root.resolve(key.career().toString()).resolve(key.binding().toString()).resolve(key.mod());
    }
    private static Path recordPath(Path directory, Pointer pointer) { return directory.resolve("r-" + pointer.record() + ".bin"); }
    private static void offEdt() throws Failure { if (SwingUtilities.isEventDispatchThread()) throw new Failure(Reason.WRONG_THREAD, "Sidecar disk operations must run off EDT"); }
    private static void requireToken(String token) {
        if (token == null || !(token.equals(EMPTY) || token.matches("[a-f0-9]{64}"))) throw new IllegalArgumentException("Invalid expected token");
    }
    private static void requireSave(Value value, SaveRevision expected) throws Failure {
        if (!value.save().equals(expected)) throw new Failure(Reason.SAVE_REVISION_MISMATCH, "Observed save revision differs; explicit association/reconciliation required");
    }
    private static Head requireHead(Path directory, String expected) throws IOException {
        Head value = readHead(directory);
        if (!(value == null ? EMPTY : value.token()).equals(expected)) throw new Failure(Reason.CONFLICT, "Sidecar changed; reread before deciding");
        return value;
    }
    private static void unchanged(Path directory, String expected) throws IOException { requireHead(directory, expected); }
    private static Head readHead(Path directory) throws IOException {
        Path path = directory.resolve("HEAD"); plain(path);
        try {
            if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile())
                throw new Failure(Reason.UNSAFE_PATH, "Sidecar HEAD is not a regular file");
        } catch (NoSuchFileException absent) { return null; }
        byte[] bytes = boundedRead(path, 256);
        if (bytes.length < 32) throw new Failure(Reason.CORRUPT, "Truncated sidecar HEAD");
        byte[] content = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(hash(content), Arrays.copyOfRange(bytes, content.length, bytes.length))) throw new Failure(Reason.CORRUPT, "Sidecar HEAD checksum mismatch");
        try (var input = new DataInputStream(new ByteArrayInputStream(content))) {
            if (input.readInt() != HEAD_MAGIC || input.readInt() != FORMAT) throw new IOException("Unknown HEAD format");
            Pointer current = pointer(input), previous = input.readBoolean() ? pointer(input) : null;
            if (input.available() != 0 || previous != null && previous.revision() >= current.revision()) throw new IOException("Invalid HEAD chain");
            return new Head(current, previous, digest(bytes));
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid sidecar HEAD", invalid); }
    }
    private static Value readValue(Path directory, Key key, Pointer pointer) throws IOException {
        byte[] bytes;
        try { bytes = boundedRead(recordPath(directory, pointer), MAX_PAYLOAD + 512); }
        catch (NoSuchFileException missing) { throw new Failure(Reason.CORRUPT, "Committed sidecar record is missing", missing); }
        if (!pointer.digest().equals(digest(bytes))) throw new Failure(Reason.CORRUPT, "Sidecar record checksum mismatch");
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != RECORD_MAGIC || input.readInt() != FORMAT) throw new IOException("Unknown record format");
            Key actual = new Key(uuid(input), uuid(input), input.readUTF());
            if (!actual.equals(key)) throw new IOException("Sidecar owner mismatch");
            long revision = input.readLong(); int schema = input.readInt();
            SaveRevision save = new SaveRevision(HexFormat.of().formatHex(input.readNBytes(32)), input.readLong());
            int length = input.readInt();
            if (length < 0 || length > MAX_PAYLOAD || input.available() != length + 32 || revision != pointer.revision()) throw new IOException("Invalid sidecar lengths/revision");
            byte[] payload = input.readNBytes(length), checksum = input.readNBytes(32);
            if (!MessageDigest.isEqual(hash(payload), checksum)) throw new IOException("Sidecar payload checksum mismatch");
            return new Value(revision, schema, save, payload);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid sidecar record", invalid); }
    }
    private static byte[] recordBytes(Key key, Value value) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(RECORD_MAGIC); output.writeInt(FORMAT); uuid(output, key.career()); uuid(output, key.binding()); output.writeUTF(key.mod());
            output.writeLong(value.revision()); output.writeInt(value.schema());
            output.write(HexFormat.of().parseHex(value.save().sha256())); output.writeLong(value.save().size());
            byte[] payload = value.payload(); output.writeInt(payload.length); output.write(payload); output.write(hash(payload));
        }
        return buffer.toByteArray();
    }
    private static byte[] headBytes(Pointer current, Pointer previous) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(HEAD_MAGIC); output.writeInt(FORMAT); pointer(output, current); output.writeBoolean(previous != null);
            if (previous != null) pointer(output, previous);
        }
        byte[] content = buffer.toByteArray(); buffer.write(hash(content)); return buffer.toByteArray();
    }
    private static Pointer pointer(DataInputStream input) throws IOException {
        UUID id = uuid(input); long revision = input.readLong(); byte[] digest = input.readNBytes(32);
        if (revision < 1 || digest.length != 32) throw new IOException("Invalid HEAD pointer");
        return new Pointer(id, revision, HexFormat.of().formatHex(digest));
    }
    private static void pointer(DataOutputStream output, Pointer pointer) throws IOException { uuid(output, pointer.record()); output.writeLong(pointer.revision()); output.write(HexFormat.of().parseHex(pointer.digest())); }
    private static UUID uuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
    private static void uuid(DataOutputStream output, UUID value) throws IOException { output.writeLong(value.getMostSignificantBits()); output.writeLong(value.getLeastSignificantBits()); }
    private static byte[] hash(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String digest(byte[] input) { return HexFormat.of().formatHex(hash(input)); }
    private static byte[] boundedRead(Path path, int limit) throws IOException {
        plain(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > limit) throw new Failure(Reason.CORRUPT, "Sidecar file exceeds format bound");
            ByteBuffer buffer = ByteBuffer.allocate(limit + 1);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (buffer.position() > limit || channel.size() != size || buffer.position() != size) throw new Failure(Reason.CORRUPT, "Sidecar changed or exceeds format bound");
            return Arrays.copyOf(buffer.array(), buffer.position());
        }
    }
    private static void writeNew(Path path, byte[] bytes) throws IOException {
        plain(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void quota(Path directory, long addition) throws IOException {
        int count = 0; long bytes = 0;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (++count > MAX_FILES - 2) throw new Failure(Reason.QUOTA, "Sidecar file quota exhausted; inspect orphans/history explicitly");
                plain(file); String name = file.getFileName().toString();
                if (!(name.equals("HEAD") || name.equals(".lock") || name.matches("r-[a-f0-9-]{36}\\.bin") || name.matches("HEAD\\.tmp-[a-f0-9-]{36}"))) throw new Failure(Reason.UNSAFE_PATH, "Unfamiliar sidecar entry preserved");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new Failure(Reason.UNSAFE_PATH, "Non-file sidecar entry refused");
                bytes += Files.size(file);
                if (bytes > MAX_BYTES - addition) throw new Failure(Reason.QUOTA, "Sidecar byte quota exhausted");
            }
        }
        if (bytes + addition > MAX_BYTES) throw new Failure(Reason.QUOTA, "Sidecar byte quota exhausted");
    }
    static void plain(Path path) throws IOException {
        for (Path current = path.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            BasicFileAttributes attrs;
            try { attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException absent) { continue; }
            if (attrs.isSymbolicLink() || attrs.isOther() || !current.toRealPath().equals(current)) throw new Failure(Reason.UNSAFE_PATH, "Link/reparse or non-canonical sidecar path refused");
        }
    }
    private static void makeDirectories(Path path) throws IOException {
        plain(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = path.getParent(); if (parent != null) makeDirectories(parent);
            try { Files.createDirectory(path); } catch (FileAlreadyExistsException raced) { if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw raced; }
        }
        plain(path); if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new Failure(Reason.UNSAFE_PATH, "Sidecar parent is not a directory");
    }
}
