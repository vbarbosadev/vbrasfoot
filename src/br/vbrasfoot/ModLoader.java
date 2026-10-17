package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ModLoader implements AutoCloseable {
    private final List<URLClassLoader> classLoaders = new ArrayList<>();
    private final List<Mod> loaded = new ArrayList<>();
    private final Logger logger;
    private final ModFailures failures = new ModFailures();
    private final Map<String, br.vbrasfoot.api.ModState> states = new LinkedHashMap<>();
    private final Map<String, String> loadedFiles = new LinkedHashMap<>();
    private final Map<Mod, String> identities = new java.util.IdentityHashMap<>();
    private static final class ResourceOwner {
        final List<br.vbrasfoot.api.CareerObservations.Subscription> items = new ArrayList<>();
        final Map<String, AutoCloseable> actions = new LinkedHashMap<>();
        boolean closed;
        volatile boolean disposed;
        synchronized void close() {
            closed = true;
            for (var item : items) item.close();
            items.clear();
            for (var action : actions.values()) {
                try { action.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
            actions.clear();
        }
    }
    private final Map<Mod, ResourceOwner> subscriptions = new java.util.IdentityHashMap<>();
    private final br.vbrasfoot.api.CareerObservations observations;
    private boolean closed;
    ModLoader(Logger logger) { this(logger, null); }
    ModLoader(Logger logger, br.vbrasfoot.api.CareerObservations observations) { this.logger = logger; this.observations = observations; }
    private synchronized void state(String id, br.vbrasfoot.api.ModState state) {
        states.put(id, state);
        logger.info("mod=" + id + " state=" + state);
    }

    static List<ModManifest> discover(Path directory) throws IOException {
        return discover(directory, Set.of());
    }
    static List<ModManifest> discover(Path directory, Set<String> disabledFiles) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        Map<String, ModManifest> manifests = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                if (disabledFiles.contains(path.getFileName().toString())) continue;
                ModManifest manifest = ModManifest.read(path);
                if (manifests.putIfAbsent(manifest.id(), manifest) != null) throw new IOException("ID de mod duplicado: " + manifest.id());
            }
        }
        return ordered(manifests);
    }
    synchronized Map<String, br.vbrasfoot.api.ModState> fileStates() {
        Map<String, br.vbrasfoot.api.ModState> result = new LinkedHashMap<>();
        loadedFiles.forEach((file, id) -> result.put(file, states.getOrDefault(id, br.vbrasfoot.api.ModState.DISCOVERED)));
        return Map.copyOf(result);
    }
    ModFailures.Snapshot failures() {
        // Poll terminal statuses without callbacks/I/O; overflow is visible even if the mod never polls.
        List<ResourceOwner> owners;
        synchronized (subscriptions) { owners = List.copyOf(subscriptions.values()); }
        for (ResourceOwner owner : owners) {
            synchronized (owner) { for (var subscription : owner.items) subscription.status(); }
        }
        return failures.snapshot();
    }
    static void requireObservationMode(List<ModManifest> manifests, boolean enabled) throws IOException {
        for (ModManifest manifest : manifests) if (!enabled && manifest.requiredCapabilities().contains("events.career.observed")) {
            throw new IOException("Mod " + manifest.id() + " requer --probe-career-hooks; observacoes experimentais indisponiveis neste modo");
        }
    }

    static List<ModManifest> ordered(Map<String, ModManifest> manifests) throws IOException {
        List<ModManifest> result = new ArrayList<>();
        Set<String> visited = new HashSet<>(), visiting = new HashSet<>();
        for (String id : manifests.keySet()) visit(id, manifests, visited, visiting, result);
        return List.copyOf(result);
    }

    private static void visit(String id, Map<String, ModManifest> all, Set<String> done,
                              Set<String> pending, List<ModManifest> result) throws IOException {
        if (done.contains(id)) return;
        ModManifest manifest = all.get(id);
        if (manifest == null) throw new IOException("Dependencia de mod ausente: " + id);
        if (!pending.add(id)) throw new IOException("Ciclo de dependencias: " + id);
        for (String conflict : manifest.conflicts()) if (all.containsKey(conflict)) throw new IOException("Conflito entre mods: " + id + " / " + conflict);
        for (String dependency : manifest.dependencies()) visit(dependency, all, done, pending, result);
        for (String dependency : manifest.optionalDependencies()) if (all.containsKey(dependency)) visit(dependency, all, done, pending, result);
        pending.remove(id);
        done.add(id);
        result.add(manifest);
    }

    void load(List<ModManifest> manifests, GameAccess game, ModUi ui, Path data) throws Exception {
        if (closed) throw new IllegalStateException("Loader encerrado; reinicie para carregar mods");
        // Separate loaders; dependencies order initialization, not access to each other's private classes.
        for (ModManifest manifest : manifests) {
            synchronized (this) { loadedFiles.put(manifest.jar().getFileName().toString(), manifest.id()); }
            state(manifest.id(), br.vbrasfoot.api.ModState.DISCOVERED);
            state(manifest.id(), br.vbrasfoot.api.ModState.VALIDATED);
            initializeMod(manifest, game, ui, data);
        }
    }

    private void initializeMod(ModManifest manifest, GameAccess game, ModUi ui, Path data) throws Exception {
        ResourceOwner owned = new ResourceOwner();
        try {
            URLClassLoader loader = new URLClassLoader(new java.net.URL[] {manifest.jar().toUri().toURL()}, Mod.class.getClassLoader());
            classLoaders.add(loader);
            Class<?> type = Class.forName(manifest.mainClass(), true, loader);
            Mod mod = type.asSubclass(Mod.class).getConstructor().newInstance();
            loaded.add(mod);
            identities.put(mod, manifest.id());
            synchronized (subscriptions) { subscriptions.put(mod, owned); }
            mod.initialize(new ModContext() {
                private void require(String permission) {
                    if (owned.disposed) throw new IllegalStateException("Contexto de mod encerrado: " + manifest.id());
                    if (!manifest.permissions().contains(permission)) throw new SecurityException(manifest.id() + " nao declarou " + permission);
                }
                public GameAccess game() {
                    require("game.read");
                    return new GameAccess() {
                        public Snapshot snapshot() { require("game.read"); return game.snapshot(); }
                        public ClubPage clubs(String filter, int offset, int limit) {
                            require("game.read"); return game.clubs(filter, offset, limit);
                        }
                        public PlayerPage players(int clubId, int offset, int limit) {
                            require("game.read"); return game.players(clubId, offset, limit);
                        }
                        public PlayerPage playersAt(int clubIndex, int offset, int limit) {
                            require("game.read"); return game.playersAt(clubIndex, offset, limit);
                        }
                        public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
                            require("game.read"); return game.scopedClubs(filter, offset, limit);
                        }
                        public ScopedPlayerPage scopedPlayers(ClubRef club, int offset, int limit) {
                            require("game.read"); return game.scopedPlayers(club, offset, limit);
                        }
                    };
                }
                public Logger logger() { return logger; }
                public br.vbrasfoot.api.CareerObservations careerObservations() {
                    require("events.career.read");
                    if (observations == null) throw new UnsupportedOperationException("Use --probe-career-hooks para observacoes experimentais");
                    return new br.vbrasfoot.api.CareerObservations() {
                        public State state() { require("events.career.read"); return observations.state(); }
                        public Subscription subscribe(java.util.function.Consumer<Update> callback) {
                            require("events.career.read");
                            synchronized (owned) {
                                if (owned.closed) throw new IllegalStateException("Mod encerrando");
                                owned.items.removeIf(s -> s.status() != Status.ACTIVE);
                                if (owned.items.size() >= 4) throw new IllegalStateException("Limite de 4 assinaturas por mod");
                                java.util.Objects.requireNonNull(callback);
                                Subscription delegate = observations.subscribe(update -> {
                                    synchronized (owned) { if (owned.closed) return; }
                                    try { callback.accept(update); }
                                    catch (RuntimeException | Error failure) {
                                        failures.record(manifest.jar().getFileName().toString(), manifest.id(), ModFailures.Stage.CAREER_CALLBACK, failure);
                                        throw failure;
                                    }
                                });
                                Subscription subscription = new Subscription() {
                                    private final java.util.concurrent.atomic.AtomicBoolean overflowRecorded = new java.util.concurrent.atomic.AtomicBoolean();
                                    public Status status() {
                                        Status status = delegate.status();
                                        if (status == Status.OVERFLOW && overflowRecorded.compareAndSet(false, true)) failures.record(
                                                manifest.jar().getFileName().toString(), manifest.id(), ModFailures.Stage.CAREER_OVERFLOW,
                                                "OVERFLOW", "Fila de observacoes cheia; assinatura encerrada. Reassinar explicitamente para recuperar.");
                                        return status;
                                    }
                                    public void close() { status(); delegate.close(); }
                                };
                                owned.items.add(subscription); return subscription;
                            }
                        }
                    };
                }
                public void registerAction(String id, String label, Runnable action) {
                    registerScopedAction(id, label, action);
                }
                public AutoCloseable registerScopedAction(String id, String label, Runnable action) {
                    require("ui.register");
                    java.util.Objects.requireNonNull(action);
                    java.util.Objects.requireNonNull(id);
                    String key = manifest.id() + ":" + id;
                    synchronized (owned) {
                        if (owned.closed) throw new IllegalStateException("Mod encerrando");
                        AutoCloseable registration = ui.registerOwned(key, label, () -> {
                            synchronized (owned) { if (owned.closed) return; }
                            try { action.run(); }
                            catch (RuntimeException | Error failure) {
                                failures.record(manifest.jar().getFileName().toString(), manifest.id(), ModFailures.Stage.UI_ACTION, failure);
                                throw failure;
                            }
                        });
                        owned.actions.put(key, registration);
                        return () -> {
                            synchronized (owned) {
                                registration.close();
                                owned.actions.remove(key, registration);
                            }
                        };
                    }
                }
                public byte[] readData(String file) throws IOException {
                    require("data.read");
                    Path path = dataPath(data, manifest.id(), file);
                    return Files.exists(path) ? Files.readAllBytes(path) : new byte[0];
                }
                public void writeData(String file, byte[] bytes) throws IOException {
                    require("data.write");
                    Path path = dataPath(data, manifest.id(), file);
                    Files.createDirectories(path.getParent());
                    Path temporary = Files.createTempFile(path.getParent(), "data-", ".tmp");
                    try {
                        Files.write(temporary, bytes);
                        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(temporary); }
                }
            });
            state(manifest.id(), br.vbrasfoot.api.ModState.INITIALIZED);
            state(manifest.id(), br.vbrasfoot.api.ModState.ACTIVE);
            logger.info("Mod carregado: " + manifest.id() + " " + manifest.version());
        } catch (Exception | Error failure) {
            failures.record(manifest.jar().getFileName().toString(), manifest.id(), ModFailures.Stage.INITIALIZE, failure);
            owned.close();
            owned.disposed = true;
            state(manifest.id(), br.vbrasfoot.api.ModState.FAILED);
            throw failure; // Fail closed: dependent mods must not run against a failed provider.
        }
    }

    static Path dataPath(Path root, String id, String file) throws IOException {
        if (!id.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*") || !file.matches("[a-zA-Z0-9_-][a-zA-Z0-9_.-]{0,100}")) {
            throw new IOException("Nome de arquivo de dados invalido");
        }
        Path base = root.toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path directory = base.resolve(id);
        if (Files.isSymbolicLink(directory)) throw new IOException("Link simbolico em dados de mod");
        Files.createDirectories(directory);
        Path path = directory.resolve(file);
        if (Files.isSymbolicLink(path) || !directory.toRealPath().startsWith(base.toRealPath())
                || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path))) {
            throw new IOException("Caminho de dados inseguro");
        }
        return path;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        List<Mod> reverse = new ArrayList<>(loaded);
        Collections.reverse(reverse);
        for (Mod mod : reverse) {
            ResourceOwner owned;
            synchronized (subscriptions) { owned = subscriptions.get(mod); }
            if (owned != null) owned.close();
            try {
                mod.shutdown();
                if (states.get(identities.get(mod)) != br.vbrasfoot.api.ModState.FAILED) state(identities.get(mod), br.vbrasfoot.api.ModState.STOPPED);
            }
            catch (Exception | Error failure) {
                String id = identities.get(mod);
                String file = loadedFiles.entrySet().stream().filter(e -> e.getValue().equals(id)).map(Map.Entry::getKey).findFirst().orElse("");
                failures.record(file, id, ModFailures.Stage.SHUTDOWN, failure);
                state(id, br.vbrasfoot.api.ModState.FAILED); logger.log(Level.WARNING, "Erro ao encerrar mod", failure);
            }
            finally { if (owned != null) owned.disposed = true; }
        }
        for (URLClassLoader loader : classLoaders) {
            try { loader.close(); } catch (IOException failure) { logger.log(Level.WARNING, "Erro ao fechar JAR", failure); }
        }
        loaded.clear();
        identities.clear();
        synchronized (subscriptions) { subscriptions.clear(); }
        classLoaders.clear();
    }
}
