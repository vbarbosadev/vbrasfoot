package br.vbrasfoot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerSaveAssociations.*;

/** Exact-path identity index. It never edits association records or decides historical effects. */
final class CareerAssociationRegistry {
    private static final int MAGIC = 0x56424947, FORMAT = 1, LIMIT = 4096, MAX_RECORD = 1024 * 1024;
    private static final Comparator<Association> ORDER = Comparator.comparing((Association a) -> a.handle().career().toString())
            .thenComparing(a -> a.handle().binding().toString());
    interface IO<T> { T run() throws IOException; }
    interface Reader { Association inspect(Handle handle) throws IOException; }
    private record Entry(Path source, Handle primary, Map<Handle, String> members, String token, String storageReference) { }
    private record Snapshot(List<Association> associations, Map<Path, Entry> entries) { }
    private final CareerSidecarStore store;
    private final Path root, directory;
    private final Reader reader;
    private final PortableCareerPaths paths;

    CareerAssociationRegistry(CareerSidecarStore store, Reader reader) {
        this(store, reader, new PortableCareerPaths(store.root(), null));
    }
    CareerAssociationRegistry(CareerSidecarStore store, Reader reader, PortableCareerPaths paths) {
        this.store = store; this.root = store.root(); this.directory = root.resolve(".identity-registry"); this.reader = reader;
        this.paths = paths;
    }

    <T> T locked(IO<T> operation) throws IOException {
        offEdt(); makeDirectory(directory);
        Path path = directory.resolve(".lock"); plain(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { throw new Failure(Reason.BUSY, "Association identity writer already active", busy); }
            if (lock == null) throw new Failure(Reason.BUSY, "Association identity writer already active");
            try (lock) { plain(directory); return operation.run(); }
        }
    }

    List<Association> inventory() throws IOException {
        offEdt(); plain(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        return locked(() -> snapshot().associations());
    }
    List<Association> inventoryLocked() throws IOException { return snapshot().associations(); }

    Group group(Path source) throws IOException {
        source = source(source); final Path selected = source;
        offEdt(); plain(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new Group(source, null, List.of(), EMPTY);
        return locked(() -> groupLocked(selected));
    }

    Group groupLocked(Path source) throws IOException { return group(source(source), snapshot(), false); }

    boolean recorded(Path source) throws IOException { return snapshot().entries().containsKey(source(source)); }

    Group requirePrimary(Association value) throws IOException {
        Group group = groupLocked(value.directory().resolve(value.name()));
        if (!value.handle().equals(group.primary()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Only the group's primary association may change; alias metadata is frozen");
        return group;
    }

    /** Explicit offline authorization is required by the caller; this only consolidates identity. */
    Group consolidate(Path source) throws IOException {
        source = source(source); Snapshot snapshot = snapshot();
        if (snapshot.entries().containsKey(source)) return group(source, snapshot, false);
        Group selected = group(source, snapshot, true);
        if (selected.members().isEmpty()) throw new Failure(Reason.NOT_FOUND, "No association exists for selected save path");
        byte[] bytes = encode(selected); Path target = directory.resolve(digest(paths.sourceReference(source).getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".group");
        Path staged = directory.resolve("pending-" + UUID.randomUUID());
        writeNew(staged, bytes); forceDirectory(directory);
        plain(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new Failure(Reason.CONFLICT, "Identity group appeared during consolidation");
        try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { throw new Failure(Reason.ATOMIC_MOVE_UNSUPPORTED, "Atomic identity record installation required", unsupported); }
        catch (IOException uncertain) { throw new Failure(Reason.OUTCOME_UNKNOWN, "Identity record installation uncertain; inspect before retry", uncertain); }
        forceDirectory(directory);
        return groupLocked(source);
    }

    void beginCreation(Path source) throws IOException {
        // A surviving intent blocks every registry operation, including a retry after HEAD committed.
        writeNew(directory.resolve("creation.pending"), source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        forceDirectory(directory);
    }

    void finishCreation() throws IOException {
        Path pending = directory.resolve("creation.pending"); plain(pending); Files.delete(pending); forceDirectory(directory);
    }

    private Snapshot snapshot() throws IOException {
        long started = System.nanoTime(); List<Association> associations = new ArrayList<>(); int[] count = {0};
        for (Path career : entries(root, started, count)) {
            if (career.getFileName().toString().equals(".identity-registry")) { requireDirectory(career); continue; }
            UUID careerId = uuidName(career); requireDirectory(career);
            for (Path binding : entries(career, started, count)) {
                UUID bindingId = uuidName(binding); requireDirectory(binding);
                var namespaces = store.inventoryBinding(careerId, bindingId).tokens();
                if (!namespaces.containsKey(ASSOCIATION_OWNER) || namespaces.get(ASSOCIATION_OWNER).equals(EMPTY))
                    throw new Failure(Reason.RECONCILIATION_REQUIRED, "Incomplete association binding preserved; inspect interrupted creation");
                if (associations.size() >= LIMIT) throw new Failure(Reason.QUOTA, "Association inventory bound exceeded; no partial result");
                associations.add(reader.inspect(new Handle(careerId, bindingId))); budget(started);
            }
        }
        associations.sort(ORDER);
        Map<Path, Entry> groups = new HashMap<>();
        for (Path path : entries(directory, started, count)) {
            String name = path.getFileName().toString(); requireFile(path);
            if (name.equals(".lock")) continue;
            if (name.equals(PortableCareerPaths.MARKER)) { paths.legacyRoot(); continue; }
            if (name.equals("creation.pending") || name.startsWith("pending-"))
                throw new Failure(Reason.RECONCILIATION_REQUIRED, "Interrupted identity operation preserved; explicit inspection required");
            if (!name.matches("[a-f0-9]{64}\\.group")) throw new Failure(Reason.CORRUPT, "Unknown identity registry entry preserved");
            Entry entry = decode(read(path));
            if (!name.equals(digest(entry.storageReference().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".group")
                    || groups.put(entry.source(), entry) != null) throw new Failure(Reason.CORRUPT, "Identity group key mismatch");
        }
        Snapshot snapshot = new Snapshot(List.copyOf(associations), Map.copyOf(groups));
        for (Entry entry : groups.values()) { group(entry.source(), snapshot, false); budget(started); }
        return snapshot;
    }

    private Group group(Path source, Snapshot snapshot, boolean allowDuplicates) throws IOException {
        List<Association> members = snapshot.associations().stream().filter(a -> a.directory().resolve(a.name()).equals(source)).toList();
        Entry entry = snapshot.entries().get(source);
        Handle primary = members.isEmpty() ? null : members.get(0).handle();
        if (entry != null) {
            if (members.size() != entry.members().size()) throw changed();
            for (Association member : members) {
                String frozen = entry.members().get(member.handle());
                if (frozen == null || !member.handle().equals(entry.primary()) && !frozen.equals(frozenHistory(member))) throw changed();
            }
            if (members.stream().noneMatch(a -> a.handle().equals(entry.primary()))) throw changed();
            primary = entry.primary();
        } else if (members.size() > 1 && !allowDuplicates) {
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Duplicate exact-path associations require explicit consolidation");
        }
        StringBuilder identity = new StringBuilder(source.toString()).append('\n').append(entry == null ? EMPTY : entry.token());
        for (Association member : members) identity.append('\n').append(member.handle()).append(':').append(member.token());
        return new Group(source, primary, members, members.isEmpty() ? EMPTY : digest(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static Failure changed() { return new Failure(Reason.RECONCILIATION_REQUIRED, "Frozen identity group membership/alias metadata changed; no partial group accepted"); }

    private byte[] encode(Group group) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC); output.writeInt(paths.relative() ? 2 : FORMAT); output.writeUTF(paths.sourceReference(group.source())); handle(output, group.primary());
            output.writeInt(group.members().size());
            for (Association value : group.members()) { handle(output, value.handle()); output.writeUTF(frozenHistory(value)); }
        }
        byte[] content = buffer.toByteArray(); buffer.write(hash(content));
        if (buffer.size() > MAX_RECORD) throw new Failure(Reason.QUOTA, "Identity record exceeds bound");
        return buffer.toByteArray();
    }

    /** Freeze all production history heads of aliases, not just their identity record. */
    private String frozenHistory(Association value) throws IOException {
        var text = new StringBuilder(value.token());
        for (String name : List.of("journals", "finance-requests", "finance-execution-witnesses", "loan-requests", "loan-continuation")) {
            var inventory = new CareerSidecarStore(root.getParent().resolve(name))
                    .inventoryBinding(value.handle().career(), value.handle().binding());
            text.append('\n').append(name);
            for (var token : new TreeMap<>(inventory.tokens()).entrySet())
                text.append('\n').append(token.getKey()).append(':').append(token.getValue());
        }
        return digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Entry decode(byte[] bytes) throws IOException {
        if (bytes.length < 32) throw new Failure(Reason.CORRUPT, "Truncated identity group");
        byte[] content = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(hash(content), Arrays.copyOfRange(bytes, content.length, bytes.length)))
            throw new Failure(Reason.CORRUPT, "Identity group checksum mismatch");
        try (var input = new DataInputStream(new ByteArrayInputStream(content))) {
            if (input.readInt() != MAGIC) throw new IOException("Unknown identity group format");
            int format = input.readInt(); if (format != 1 && format != 2) throw new IOException("Unknown identity group format");
            String reference = input.readUTF(); Path path = paths.source(reference, format);
            if (!path.isAbsolute() || !path.equals(path.normalize()) || path.toString().length() > 8192) throw new IOException("Invalid group path");
            Handle primary = handle(input); int count = input.readInt();
            if (count < 1 || count > LIMIT) throw new IOException("Invalid group member count");
            Map<Handle, String> members = new HashMap<>();
            for (int i = 0; i < count; i++) {
                Handle member = handle(input); String token = input.readUTF();
                if (!token.matches("[a-f0-9]{64}") || members.put(member, token) != null) throw new IOException("Invalid/duplicate group member");
            }
            if (input.available() != 0 || !members.containsKey(primary)) throw new IOException("Invalid primary or trailing data");
            return new Entry(path, primary, Map.copyOf(members), digest(bytes), reference);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid identity group", invalid); }
    }

    private static List<Path> entries(Path directory, long started, int[] count) throws IOException {
        requireDirectory(directory); var result = new ArrayList<Path>();
        try (var stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                budget(started); if (++count[0] > 16384) throw new Failure(Reason.QUOTA, "Identity inventory entry bound exceeded");
                plain(path); result.add(path);
            }
        } catch (DirectoryIteratorException invalid) { throw invalid.getCause(); }
        return result;
    }
    private static void budget(long started) throws Failure {
        if (System.nanoTime() - started > 10_000_000_000L) throw new Failure(Reason.QUOTA, "Identity inventory time bound exceeded; no partial result");
    }
    private static UUID uuidName(Path path) throws Failure {
        try {
            String name = path.getFileName().toString(); UUID id = UUID.fromString(name);
            if (!id.toString().equals(name)) throw new IllegalArgumentException("Noncanonical UUID"); return id;
        } catch (IllegalArgumentException invalid) { throw new Failure(Reason.UNSAFE_PATH, "Unknown association inventory entry preserved", invalid); }
    }
    private static Path source(Path path) {
        Objects.requireNonNull(path);
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.toString().length() > 8192) throw new IllegalArgumentException("Save path exceeds identity bound");
        return normalized;
    }
    private static void requireDirectory(Path path) throws IOException {
        plain(path);
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new Failure(Reason.UNSAFE_PATH, "Identity inventory expected directory");
    }
    private static void requireFile(Path path) throws IOException {
        plain(path);
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile())
            throw new Failure(Reason.UNSAFE_PATH, "Identity registry expected regular file");
    }
    private static void makeDirectory(Path path) throws IOException {
        plain(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = path.getParent(); if (parent != null) makeDirectory(parent);
            try { Files.createDirectory(path); } catch (FileAlreadyExistsException raced) { requireDirectory(path); }
        }
        requireDirectory(path);
    }
    private static byte[] read(Path path) throws IOException {
        requireFile(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size > MAX_RECORD) throw new Failure(Reason.CORRUPT, "Oversized identity record");
            ByteBuffer buffer = ByteBuffer.allocate(MAX_RECORD + 1);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            if (buffer.position() != size || channel.size() != size) throw new Failure(Reason.CORRUPT, "Identity record changed/exceeds bound");
            return Arrays.copyOf(buffer.array(), buffer.position());
        }
    }
    private static void writeNew(Path path, byte[] bytes) throws IOException {
        plain(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void forceDirectory(Path path) throws IOException {
        plain(path);
        // Windows does not expose a directory fsync through FileChannel; payload files are always forced.
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
        catch (AccessDeniedException unsupportedOnWindows) {
            if (!System.getProperty("os.name").startsWith("Windows")) throw unsupportedOnWindows;
        }
    }
    private static void offEdt() throws Failure {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new Failure(Reason.WRONG_THREAD, "Identity disk operations must run off EDT");
    }
    private static Handle handle(DataInputStream in) throws IOException { return new Handle(new UUID(in.readLong(), in.readLong()), new UUID(in.readLong(), in.readLong())); }
    private static void handle(DataOutputStream out, Handle handle) throws IOException {
        out.writeLong(handle.career().getMostSignificantBits()); out.writeLong(handle.career().getLeastSignificantBits());
        out.writeLong(handle.binding().getMostSignificantBits()); out.writeLong(handle.binding().getLeastSignificantBits());
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String digest(byte[] bytes) { return HexFormat.of().formatHex(hash(bytes)); }
}
