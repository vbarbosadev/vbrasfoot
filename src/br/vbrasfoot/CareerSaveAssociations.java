package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Internal explicit-confirmation metadata service; never opens a career or activates a command context. */
final class CareerSaveAssociations {
    enum Confirmation { CREATE_NEW_CAREER, FORK_COPY_WITH_EMPTY_MOD_DATA, RENAME_SAME_FILE, ASSERT_RENAME_WITHOUT_FILE_IDENTITY, ACCEPT_CHANGED_REVISION, RESTORE_RECORDED_PREVIOUS }
    enum Match { EXACT_OBSERVATION, DIRECTORY_CHANGED, NAME_CHANGED, NAME_CHANGED_IDENTITY_UNAVAILABLE, REVISION_CHANGED, FILE_REPLACED_OR_COPIED, METADATA_CHANGED, FILE_IDENTITY_UNAVAILABLE }
    record Handle(UUID career, UUID binding) { Handle { Objects.requireNonNull(career); Objects.requireNonNull(binding); } }
    record Origin(Handle handle, String token, long revision, SaveRevision save) { }
    record Association(Handle handle, String token, long revision, Path directory, String name, String fileKey, String modified, String created,
                       SaveRevision save, Optional<Origin> parent, Confirmation decision) { }
    /** Membership is administrative identity only; every member's history still requires reconciliation. */
    record Group(Path source, Handle primary, List<Association> members, String token) {
        Group { members = List.copyOf(members); }
    }
    static final class CreationFailure extends IOException {
        private final Handle attempted;
        CreationFailure(Handle attempted, IOException cause) { super("Association creation may require inspection of the attempted handle before retry", cause); this.attempted = attempted; }
        Handle attempted() { return attempted; }
    }
    private static final int MAGIC = 0x56424153, FORMAT = 1, SCHEMA = 1;
    private final CareerSidecarStore store;
    private final SaveFileObserver observer;
    private final CareerAssociationRegistry registry;
    private final PortableCareerPaths paths;
    CareerSaveAssociations(Path metadataRoot) { this(new CareerSidecarStore(metadataRoot), new SaveFileObserver()); }
    CareerSaveAssociations(Path metadataRoot, Path saves) { this(new CareerSidecarStore(metadataRoot), new SaveFileObserver(), saves); }
    CareerSaveAssociations(CareerSidecarStore store, SaveFileObserver observer) {
        this(store, observer, null);
    }
    CareerSaveAssociations(CareerSidecarStore store, SaveFileObserver observer, Path saves) {
        this.store = Objects.requireNonNull(store); this.observer = Objects.requireNonNull(observer);
        this.paths = new PortableCareerPaths(store.root(), saves);
        this.registry = new CareerAssociationRegistry(store, this::inspect, paths);
    }
    /** Explicit offline migration, preserving the immutable association records and every history head. */
    void enablePortable(Path legacyDirectory) throws IOException {
        registry.locked(() -> {
            if (paths.legacyRoot().isEmpty()) {
                var entries = registry.inventoryLocked();
                if (entries.isEmpty() || entries.stream().anyMatch(a -> !a.directory().equals(legacyDirectory)))
                    throw new Failure(Reason.RECONCILIATION_REQUIRED, "Select one complete legacy installation; mixed roots cannot be silently merged");
            }
            paths.enable(legacyDirectory); return null;
        });
    }
    /** Same-location initialization needs no identity inference; prepares legacy installations for their next move. */
    void rememberCurrentInstallation(Path currentDirectory) throws IOException {
        registry.locked(() -> {
            if (paths.legacyRoot().isPresent()) return null;
            var entries = registry.inventoryLocked();
            if (!entries.isEmpty() && entries.stream().allMatch(a -> a.directory().equals(currentDirectory)))
                paths.enable(currentDirectory);
            return null;
        });
    }
    List<Association> inventory() throws IOException { return registry.inventory(); }
    Group group(Path source) throws IOException { return registry.group(source); }
    Optional<Association> existing(Path source) throws IOException {
        Group value = group(source);
        return value.members().stream().filter(a -> a.handle().equals(value.primary())).findFirst();
    }
    Group consolidate(SaveFileObserver.Observation observed) throws IOException {
        return registry.locked(() -> { observer.verify(observed); return registry.consolidate(observed.directory().resolve(observed.name())); });
    }
    Association create(SaveFileObserver.Observation observed, Confirmation confirmation) throws IOException {
        require(confirmation, Confirmation.CREATE_NEW_CAREER); observer.verify(observed);
        return registry.locked(() -> createUnique(observed, Optional.empty(), confirmation));
    }
    Association inspect(Handle handle) throws IOException {
        View view = store.inspectAssociation(key(handle));
        if (view.value().isEmpty()) throw new Failure(Reason.NOT_FOUND, "Association does not exist");
        return decode(handle, view);
    }
    Match assess(Association association, SaveFileObserver.Observation observed) {
        Objects.requireNonNull(association); Objects.requireNonNull(observed);
        if (!association.directory().equals(observed.directory())) return Match.DIRECTORY_CHANGED;
        if (!association.save().equals(observed.revision())) return Match.REVISION_CHANGED;
        if (association.fileKey().isEmpty() || observed.fileKey().isEmpty())
            return association.name().equals(observed.name()) ? Match.FILE_IDENTITY_UNAVAILABLE : Match.NAME_CHANGED_IDENTITY_UNAVAILABLE;
        if (!association.fileKey().equals(observed.fileKey())) return Match.FILE_REPLACED_OR_COPIED;
        if (!association.name().equals(observed.name())) return Match.NAME_CHANGED;
        if (!association.modified().equals(observed.modified()) || !association.created().equals(observed.created())) return Match.METADATA_CHANGED;
        return Match.EXACT_OBSERVATION;
    }
    Association fork(Handle original, String token, SaveFileObserver.Observation copy, Confirmation confirmation) throws IOException {
        require(confirmation, Confirmation.FORK_COPY_WITH_EMPTY_MOD_DATA);
        return registry.locked(() -> forkLocked(original, token, copy, confirmation));
    }
    private Association forkLocked(Handle original, String token, SaveFileObserver.Observation copy, Confirmation confirmation) throws IOException {
        Association source = current(original, token); observer.verify(copy);
        registry.requirePrimary(source);
        if (!source.save().equals(copy.revision()) || source.directory().equals(copy.directory()) && source.name().equals(copy.name()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Fork requires explicitly selected different path with matching observed source bytes");
        if (!source.fileKey().isEmpty() && source.fileKey().equals(copy.fileKey()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Same physical file/hardlink is not an independent copy");
        Path originalPath = source.directory().resolve(source.name()); CareerSidecarStore.plain(originalPath);
        try {
            Files.readAttributes(originalPath, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (Files.isSameFile(originalPath, copy.directory().resolve(copy.name())))
                throw new Failure(Reason.RECONCILIATION_REQUIRED, "Same physical file/hardlink is not an independent copy");
        } catch (NoSuchFileException missing) { /* A user may explicitly choose a fresh fork when the original path is absent. */ }
        current(original, token);
        return createUnique(copy, Optional.of(new Origin(original, source.token(), source.revision(), source.save())), confirmation); // No mod payload or command journal is copied.
    }
    Association rename(Handle handle, String token, SaveFileObserver.Observation renamed, Confirmation confirmation) throws IOException {
        if (confirmation != Confirmation.RENAME_SAME_FILE && confirmation != Confirmation.ASSERT_RENAME_WITHOUT_FILE_IDENTITY)
            throw new IllegalArgumentException("Explicit rename confirmation required");
        return registry.locked(() -> renameLocked(handle, token, renamed, confirmation));
    }
    private Association renameLocked(Handle handle, String token, SaveFileObserver.Observation renamed, Confirmation confirmation) throws IOException {
        Association old = current(handle, token); observer.verify(renamed);
        Group previousGroup = registry.requirePrimary(old);
        if (previousGroup.members().size() > 1 || registry.recorded(previousGroup.source()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Durable group rename requires explicit group migration; aliases must not be stranded");
        if (!registry.groupLocked(renamed.directory().resolve(renamed.name())).members().isEmpty())
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Rename destination already has an association");
        Match expected = confirmation == Confirmation.RENAME_SAME_FILE ? Match.NAME_CHANGED : Match.NAME_CHANGED_IDENTITY_UNAVAILABLE;
        if (assess(old, renamed) != expected) throw new Failure(Reason.RECONCILIATION_REQUIRED, "Rename evidence must match the explicitly selected confirmation strength");
        Path previous = old.directory().resolve(old.name()); CareerSidecarStore.plain(previous);
        try { Files.readAttributes(previous, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            throw new Failure(Reason.CONFLICT, "Old save path still exists; copy/hardlink is not a confirmed rename"); }
        catch (NoSuchFileException missing) { /* Explicit rename may proceed; never delete either path. */ }
        return replace(old, renamed, confirmation);
    }
    Association acceptRevision(Handle handle, String token, SaveFileObserver.Observation revised, Confirmation confirmation) throws IOException {
        require(confirmation, Confirmation.ACCEPT_CHANGED_REVISION);
        return registry.locked(() -> acceptRevisionLocked(handle, token, revised, confirmation));
    }
    private Association acceptRevisionLocked(Handle handle, String token, SaveFileObserver.Observation revised, Confirmation confirmation) throws IOException {
        Association old = current(handle, token); observer.verify(revised); samePath(old, revised);
        registry.requirePrimary(old);
        if (old.save().equals(revised.revision()) && old.fileKey().equals(revised.fileKey()) && old.modified().equals(revised.modified()) && old.created().equals(revised.created()))
            throw new Failure(Reason.CONFLICT, "No observed metadata changed; confirmation cannot invent stronger file identity");
        return replace(old, revised, confirmation); // User acknowledgment, not proof of a qualified game save.
    }
    Association restorePrevious(Handle handle, String token, SaveFileObserver.Observation restored, Confirmation confirmation) throws IOException {
        require(confirmation, Confirmation.RESTORE_RECORDED_PREVIOUS);
        return registry.locked(() -> restorePreviousLocked(handle, token, restored, confirmation));
    }
    private Association restorePreviousLocked(Handle handle, String token, SaveFileObserver.Observation restored, Confirmation confirmation) throws IOException {
        Association old = current(handle, token); observer.verify(restored); samePath(old, restored);
        registry.requirePrimary(old);
        Value previous = store.readPrevious(key(handle), restored.revision(), token);
        decode(handle, new View(token, Optional.of(previous), false)); // Validate previous metadata/schema, not just a matching digest.
        return replace(old, restored, confirmation); // Does not roll back mod data or command receipts.
    }
    private Association createUnique(SaveFileObserver.Observation observed, Optional<Origin> parent, Confirmation confirmation) throws IOException {
        observer.verify(observed);
        Group group = registry.groupLocked(observed.directory().resolve(observed.name()));
        if (!group.members().isEmpty()) return group.members().stream().filter(a -> a.handle().equals(group.primary())).findFirst().orElseThrow();
        registry.beginCreation(observed.directory().resolve(observed.name()));
        Association created = createNew(observed, parent, confirmation);
        registry.finishCreation();
        return created;
    }
    private Association createNew(SaveFileObserver.Observation observed, Optional<Origin> parent, Confirmation confirmation) throws IOException {
        var handle = new Handle(UUID.randomUUID(), UUID.randomUUID());
        try { return decode(handle, store.write(key(handle), observed.revision(), SCHEMA, EMPTY, encode(observed, parent, confirmation))); }
        catch (IOException failure) { throw new CreationFailure(handle, failure); } // Never lose the handle on an ambiguous post-commit result.
    }
    private Association replace(Association old, SaveFileObserver.Observation observed, Confirmation confirmation) throws IOException {
        observer.verify(observed);
        return decode(old.handle(), store.changeAssociationRevision(key(old.handle()), old.save(), observed.revision(), SCHEMA,
                old.token(), encode(observed, old.parent(), confirmation)));
    }
    private Association current(Handle handle, String token) throws IOException {
        Association value = inspect(handle);
        if (!value.token().equals(token)) throw new Failure(Reason.CONFLICT, "Association changed; inspect before confirming");
        return value;
    }
    private static void samePath(Association old, SaveFileObserver.Observation observed) throws Failure {
        if (!old.directory().equals(observed.directory()) || !old.name().equals(observed.name()))
            throw new Failure(Reason.RECONCILIATION_REQUIRED, "Revision/restore requires same selected path; do not silently merge copies");
    }
    private static Key key(Handle handle) { return new Key(handle.career(), handle.binding(), ASSOCIATION_OWNER); }
    private static void require(Confirmation actual, Confirmation expected) {
        if (actual != expected) throw new IllegalArgumentException("Explicit confirmation required: " + expected);
    }
    private byte[] encode(SaveFileObserver.Observation observation, Optional<Origin> parent, Confirmation confirmation) throws IOException {
        if (observation.directory().toString().length() > 4096 || observation.fileKey().length() > 1024
                || observation.modified().length() > 128 || observation.created().length() > 128)
            throw new IllegalArgumentException("Observed metadata exceeds association format bounds");
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC); output.writeInt(paths.relative() ? 2 : FORMAT); output.writeUTF(paths.directoryReference(observation.directory())); output.writeUTF(observation.name());
            output.writeUTF(observation.fileKey()); output.writeUTF(observation.modified()); output.writeUTF(observation.created()); output.writeUTF(confirmation.name()); output.writeBoolean(parent.isPresent());
            if (parent.isPresent()) {
                var value = parent.orElseThrow(); uuid(output, value.handle().career()); uuid(output, value.handle().binding());
                output.writeUTF(value.token()); output.writeLong(value.revision()); output.write(HexFormat.of().parseHex(value.save().sha256())); output.writeLong(value.save().size());
            }
        }
        return bytes.toByteArray();
    }
    private Association decode(Handle handle, View view) throws IOException {
        Value value = view.value().orElseThrow();
        if (value.schema() != SCHEMA) throw new Failure(Reason.CORRUPT, "Unknown association schema; no migration performed");
        try (var input = new DataInputStream(new ByteArrayInputStream(value.payload()))) {
            if (input.readInt() != MAGIC) throw new IOException("Unknown association format");
            int format = input.readInt(); if (format != 1 && format != 2) throw new IOException("Unknown association format");
            Path directory = paths.directory(input.readUTF(), format); String name = input.readUTF(), fileKey = input.readUTF(), modified = input.readUTF(), created = input.readUTF();
            if (!directory.isAbsolute() || !directory.equals(directory.normalize()) || directory.toString().length() > 4096
                    || fileKey.length() > 1024 || modified.length() > 128 || modified.isBlank() || created.length() > 128 || created.isBlank()) throw new IOException("Invalid association fields");
            SaveFileObserver.validateName(name); Confirmation decision = Confirmation.valueOf(input.readUTF());
            Optional<Origin> parent = Optional.empty();
            if (input.readBoolean()) {
                Handle source = new Handle(uuid(input), uuid(input)); String token = input.readUTF(); long revision = input.readLong();
                SaveRevision save = new SaveRevision(HexFormat.of().formatHex(input.readNBytes(32)), input.readLong());
                if (!token.matches("[a-f0-9]{64}") || revision < 1 || source.equals(handle)) throw new IOException("Invalid fork origin");
                parent = Optional.of(new Origin(source, token, revision, save));
            }
            if (input.available() != 0) throw new IOException("Invalid association length");
            return new Association(handle, view.token(), value.revision(), directory, name, fileKey, modified, created, value.save(), parent, decision);
        } catch (IOException | IllegalArgumentException invalid) { throw new Failure(Reason.CORRUPT, "Invalid association metadata", invalid); }
    }
    private static UUID uuid(DataInputStream input) throws IOException { return new UUID(input.readLong(), input.readLong()); }
    private static void uuid(DataOutputStream output, UUID value) throws IOException { output.writeLong(value.getMostSignificantBits()); output.writeLong(value.getLeastSignificantBits()); }
}
