package br.vbrasfoot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

record ModManifest(Path jar, String id, String name, String version, String mainClass,
                   List<String> dependencies, Set<String> permissions, List<String> optionalDependencies,
                    List<String> conflicts, Set<String> requiredCapabilities) {
    static final Set<String> KNOWN_CAPABILITIES = Set.of("game.read", "ui.register", "ui.panel.embedded", "data.read", "data.write", "query.paged", "query.scoped", "query.player.details", "query.calendar.current", "query.club.balance", "events.career.observed", "events.career.io");
    static final Set<String> KNOWN_PERMISSIONS = Set.of("game.read", "ui.register", "data.read", "data.write", "events.career.read");
    ModManifest(Path jar, String id, String name, String version, String mainClass,
                List<String> dependencies, Set<String> permissions) {
        this(jar, id, name, version, mainClass, dependencies, permissions, List.of(), List.of(), Set.of());
    }
    static ModManifest read(Path jar) throws IOException {
        Properties values = new Properties();
        try (JarFile file = new JarFile(jar.toFile())) {
            var entry = file.getJarEntry("vbrasfoot.mod.properties");
            if (entry == null || entry.getSize() > 16384) throw new IOException("Manifesto vbrasfoot ausente ou muito grande: " + jar);
            try (var reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) { values.load(reader); }
        }
        String id = required(values, "id");
        Set<String> known = Set.of("id", "name", "version", "mainClass", "apiVersion", "gameProfile", "dependencies",
                "permissions", "manifestVersion", "minLoaderVersion", "optionalDependencies", "conflicts", "requiredCapabilities");
        for (String key : values.stringPropertyNames()) if (!known.contains(key)) throw new IOException("Campo desconhecido: " + key);
        if (!values.getProperty("manifestVersion", "1").equals("1")) throw new IOException("manifestVersion nao suportada: " + id);
        String minimum = values.getProperty("minLoaderVersion", "0.1.0");
        if (compareVersions(minimum, br.vbrasfoot.api.Sdk.VERSION) > 0) throw new IOException("Atualize o loader para " + minimum + ": " + id);
        Set<String> requiredCapabilities = Set.copyOf(split(values.getProperty("requiredCapabilities", "")));
        if (!KNOWN_CAPABILITIES.containsAll(requiredCapabilities)) throw new IOException("Capacidade indisponivel: " + id + " requer " + requiredCapabilities);
        if (!id.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) throw new IOException("ID de mod invalido: " + id);
        String api = required(values, "apiVersion");
        // Closed integer API-major interval, e.g. [1,2); never infer SemVer compatibility.
        boolean supported = api.equals("1");
        if (api.matches("\\[\\d{1,6},\\d{1,6}\\)")) {
            String[] bounds = api.substring(1, api.length() - 1).split(",");
            supported = Integer.parseInt(bounds[0]) <= 1 && 1 < Integer.parseInt(bounds[1]);
        }
        if (!supported) throw new IOException("API nao suportada: " + id + "; use 1 ou intervalo [min,max) contendo 1");
        if (!required(values, "gameProfile").equals(br.vbrasfoot.api.Sdk.GAME_PROFILE)) throw new IOException("Mod de outra versao do jogo: " + id);
        Set<String> permissions = Set.copyOf(split(values.getProperty("permissions", "")));
        if (!KNOWN_PERMISSIONS.containsAll(permissions)) {
            throw new IOException("Permissao desconhecida ou de escrita na carreira: " + id);
        }
        return new ModManifest(jar, id, required(values, "name"), required(values, "version"),
                required(values, "mainClass"), split(values.getProperty("dependencies", "")), permissions,
                split(values.getProperty("optionalDependencies", "")), split(values.getProperty("conflicts", "")), requiredCapabilities);
    }
    private static int compareVersions(String left, String right) throws IOException {
        if (!left.matches("\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}")) throw new IOException("Versao minima deve usar major.minor.patch: " + left);
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) { int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i])); if (result != 0) return result; }
        return 0;
    }
    private static String required(Properties values, String key) throws IOException {
        String value = values.getProperty(key, "").trim();
        if (value.isEmpty()) throw new IOException("Campo obrigatorio ausente: " + key);
        return value;
    }
    private static List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
