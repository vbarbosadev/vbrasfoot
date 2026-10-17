package br.vbrasfoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Validates every existing component before the first managed write. */
record InstallPaths(Path gameRoot, Path installRoot) {
    static InstallPaths resolve(Path game, Path home) throws IOException {
        Path root = game.toRealPath();
        Path target = home.toAbsolutePath().normalize();
        rejectRedirects(target);
        Path ancestor = target;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        target = ancestor.toRealPath().resolve(ancestor.relativize(target)).normalize();
        if (root.startsWith(target) || (target.startsWith(root) && !target.equals(root.resolve("vbrasfoot")))) {
            throw new IOException("Instalacao interna permitida somente em gameRoot/vbrasfoot; recursos originais protegidos");
        }
        // Existing writable subtrees must not redirect managed files into game resources.
        if (Files.exists(target)) {
            try (var entries = Files.walk(target)) {
                for (Path entry : entries.toList()) rejectRedirects(entry);
            }
        }
        return new InstallPaths(root, target);
    }

    static void rejectRedirects(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(current) || !current.toRealPath().equals(current.toAbsolutePath().normalize())) {
                throw new IOException("Link/junction ou alias de caminho recusado: " + current);
            }
            if (Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isOther()) throw new IOException("Reparse point recusado: " + current);
        }
    }
}
