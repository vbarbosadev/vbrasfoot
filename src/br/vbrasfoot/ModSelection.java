package br.vbrasfoot;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Filename-based selection also permits disabling a malformed JAR without loading it. */
final class ModSelection {
    record Entry(String file, ModManifest manifest, boolean enabled, String error) { }
    private static final int MAX_BYTES = 65536, MAX_ENTRIES = 512;
    private static final Object WRITE_LOCK = new Object();
    private final Path home, file;
    private final byte[] original;
    private final Set<String> disabled;

    private ModSelection(Path home, byte[] original, Set<String> disabled) {
        this.home = home; this.file = home.resolve("mods-selection.properties");
        this.original = original.clone(); this.disabled = Set.copyOf(disabled);
    }
    private static void safe(Path path) throws IOException {
        for (Path current = path.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            var attributes = Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || !current.toRealPath().equals(current)) throw new IOException("Link/alias recusado: " + current);
        }
    }
    private static String filename(String name) throws IOException {
        if (name == null || name.length() > 160 || !name.endsWith(".jar") || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.startsWith(".") || name.endsWith(" ")) throw new IOException("Informe somente nome.jar, sem caminho: " + name);
        return name;
    }
    private static byte[] bytes(Path path) throws IOException {
        safe(path);
        if (!Files.exists(path)) return new byte[0];
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("Configuracao de mods invalida/grande demais");
        try (var input = Files.newInputStream(path)) {
            byte[] result = input.readNBytes(MAX_BYTES + 1);
            if (result.length > MAX_BYTES) throw new IOException("Configuracao de mods grande demais");
            return result;
        }
    }
    static ModSelection read(Path home) throws IOException {
        home = home.toAbsolutePath().normalize();
        byte[] original = bytes(home.resolve("mods-selection.properties"));
        if (original.length == 0 && Files.exists(home.resolve("mods-selection.properties"))) throw new IOException("Configuracao de mods vazia; preserve e revise antes de iniciar");
        Properties values = new Properties();
        try { values.load(new StringReader(new String(original, StandardCharsets.UTF_8))); }
        catch (IllegalArgumentException failure) { throw new IOException("Configuracao de mods corrompida; preserve e revise mods-selection.properties", failure); }
        Set<String> disabled = new TreeSet<>();
        if (original.length > 0) {
            if (!"1".equals(values.getProperty("format"))) throw new IOException("Formato de selecao de mods nao suportado");
            for (String key : values.stringPropertyNames()) {
                if (key.equals("format")) continue;
                if (!key.startsWith("disabled.") || !"true".equals(values.getProperty(key))) throw new IOException("Campo de selecao invalido: " + key);
                disabled.add(filename(key.substring("disabled.".length())));
            }
        }
        if (disabled.size() > MAX_ENTRIES) throw new IOException("Muitos mods desabilitados");
        return new ModSelection(home, original, disabled);
    }
    List<Entry> inventory() throws IOException {
        Path directory = home.resolve("mods"); safe(directory);
        if (!Files.exists(directory)) return List.of();
        List<Entry> result = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            for (Path path : entries.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                String name = path.getFileName().toString(); safe(path);
                if (result.size() >= MAX_ENTRIES) throw new IOException("Limite de 512 JARs no painel");
                try { result.add(new Entry(name, ModManifest.read(path), !disabled.contains(name), "")); }
                catch (IOException failure) { result.add(new Entry(name, null, !disabled.contains(name), failure.getMessage())); }
            }
        }
        return List.copyOf(result);
    }
    List<ModManifest> selected() throws IOException { inventory(); return ModLoader.discover(home.resolve("mods"), disabled); }

    /** Requires the caller's runtime lock. Candidate graph validated before an atomic config-only write. */
    ModSelection change(String jar, boolean enabled) throws IOException {
        synchronized (WRITE_LOCK) { return changeLocked(jar, enabled); }
    }
    private ModSelection changeLocked(String jar, boolean enabled) throws IOException {
        filename(jar);
        Path target = home.resolve("mods").resolve(jar); safe(target);
        if (!Files.isRegularFile(target)) throw new IOException("JAR nao encontrado: " + jar);
        List<Entry> entries = inventory(); // Reject newly introduced links before reading the candidate graph.
        if (entries.stream().noneMatch(e -> e.file().equals(jar))) throw new IOException("Use o nome exato do JAR mostrado em --list-mods: " + jar);
        Set<String> next = new TreeSet<>(disabled);
        if (enabled) next.remove(jar); else next.add(jar);
        if (next.size() > MAX_ENTRIES) throw new IOException("Limite de selecao excedido");
        ModLoader.discover(home.resolve("mods"), next); // Missing dependency/cycle/conflict refuses the whole change.
        Properties values = new Properties(); values.setProperty("format", "1");
        for (String name : next) values.setProperty("disabled." + name, "true");
        StringWriter writer = new StringWriter(); values.store(writer, "vbrasfoot: applies on next startup; no hot reload");
        byte[] encoded = writer.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_BYTES) throw new IOException("Configuracao grande demais");
        safe(home); safe(file);
        if (!Arrays.equals(original, bytes(file))) throw new IOException("Selecao mudou externamente; reabra o painel antes de salvar");
        Path temporary = Files.createTempFile(home, "mods-selection-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            safe(file);
            if (!Arrays.equals(original, bytes(file))) throw new IOException("Selecao mudou durante gravacao");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return new ModSelection(home, encoded, next);
    }
}
