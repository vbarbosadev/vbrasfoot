package br.vbrasfoot;

import br.vbrasfoot.api.Mod;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.jar.JarFile;

/** Individual SDK-author validation: reads/links classes, never initializes mod code. */
final class ModValidator {
    static void validateClass(ModManifest manifest) throws Exception {
        String entry = manifest.mainClass().replace('.', '/') + ".class";
        try (JarFile jar = new JarFile(manifest.jar().toFile())) {
            if (jar.getJarEntry(entry) == null) throw new IOException("mainClass nao esta no JAR: " + manifest.mainClass());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {manifest.jar().toUri().toURL()}, Main.class.getClassLoader())) {
            Class<?> type = Class.forName(manifest.mainClass(), false, loader).asSubclass(Mod.class);
            if (!Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers())) throw new IOException("mainClass deve ser publica e concreta");
            type.getConstructor();
        }
    }
    static void inspect(Path path) throws Exception {
        ModManifest manifest = ModManifest.read(path.toRealPath());
        validateClass(manifest);
        System.out.println("JAR validado sem inicializar mod/jogo: " + manifest.id() + " " + manifest.version());
        System.out.println("Permissoes: " + manifest.permissions() + "; dependencias declaradas: " + manifest.dependencies());
        System.out.println("Capacidades requeridas: " + manifest.requiredCapabilities() + "; opcionais: " + manifest.optionalDependencies() + "; conflitos: " + manifest.conflicts());
        System.out.println("Valida somente este JAR; valide o conjunto instalado separadamente. Nao e auditoria de seguranca ou sandbox.");
    }
}
