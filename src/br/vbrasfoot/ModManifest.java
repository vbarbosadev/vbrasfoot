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
                   List<String> dependencies, Set<String> permissions) {
    static ModManifest read(Path jar) throws IOException {
        Properties values = new Properties();
        try (JarFile file = new JarFile(jar.toFile())) {
            var entry = file.getJarEntry("vbrasfoot.mod.properties");
            if (entry == null || entry.getSize() > 16384) throw new IOException("Manifesto vbrasfoot ausente ou muito grande: " + jar);
            try (var reader = new InputStreamReader(file.getInputStream(entry), StandardCharsets.UTF_8)) { values.load(reader); }
        }
        String id = required(values, "id");
        if (!id.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) throw new IOException("ID de mod invalido: " + id);
        if (!required(values, "apiVersion").equals("1")) throw new IOException("API nao suportada: " + id);
        if (!required(values, "gameProfile").equals(Profile.ID)) throw new IOException("Mod de outra versao do jogo: " + id);
        Set<String> permissions = Set.copyOf(split(values.getProperty("permissions", "")));
        if (!Set.of("game.read", "ui.register", "data.read", "data.write").containsAll(permissions)) {
            throw new IOException("Permissao desconhecida ou de escrita na carreira: " + id);
        }
        return new ModManifest(jar, id, required(values, "name"), required(values, "version"),
                required(values, "mainClass"), split(values.getProperty("dependencies", "")), permissions);
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
