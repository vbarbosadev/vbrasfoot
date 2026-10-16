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
    ModLoader(Logger logger) { this.logger = logger; }

    static List<ModManifest> discover(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        Map<String, ModManifest> manifests = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                ModManifest manifest = ModManifest.read(path);
                if (manifests.putIfAbsent(manifest.id(), manifest) != null) throw new IOException("ID de mod duplicado: " + manifest.id());
            }
        }
        return ordered(manifests);
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
        for (String dependency : manifest.dependencies()) visit(dependency, all, done, pending, result);
        pending.remove(id);
        done.add(id);
        result.add(manifest);
    }

    void load(List<ModManifest> manifests, GameAccess game, ModUi ui, Path data) throws Exception {
        // Separate loaders; dependencies order initialization, not access to each other's private classes.
        for (ModManifest manifest : manifests) {
            URLClassLoader loader = new URLClassLoader(new java.net.URL[] {manifest.jar().toUri().toURL()}, Mod.class.getClassLoader());
            classLoaders.add(loader);
            Class<?> type = Class.forName(manifest.mainClass(), true, loader);
            Mod mod = type.asSubclass(Mod.class).getConstructor().newInstance();
            loaded.add(mod);
            mod.initialize(new ModContext() {
                private void require(String permission) {
                    if (!manifest.permissions().contains(permission)) throw new SecurityException(manifest.id() + " nao declarou " + permission);
                }
                public GameAccess game() {
                    require("game.read");
                    return () -> { require("game.read"); return game.snapshot(); };
                }
                public Logger logger() { return logger; }
                public void registerAction(String id, String label, Runnable action) {
                    require("ui.register");
                    ui.register(manifest.id() + ":" + id, label, action);
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
            logger.info("Mod carregado: " + manifest.id() + " " + manifest.version());
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
        List<Mod> reverse = new ArrayList<>(loaded);
        Collections.reverse(reverse);
        for (Mod mod : reverse) {
            try { mod.shutdown(); } catch (Exception failure) { logger.log(Level.WARNING, "Erro ao encerrar mod", failure); }
        }
        for (URLClassLoader loader : classLoaders) {
            try { loader.close(); } catch (IOException failure) { logger.log(Level.WARNING, "Erro ao fechar JAR", failure); }
        }
        loaded.clear();
        classLoaders.clear();
    }
}
