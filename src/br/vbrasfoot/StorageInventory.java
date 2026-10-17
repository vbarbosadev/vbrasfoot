package br.vbrasfoot;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Metadata-only inventory. Never creates files, reads file bodies, or scans unknown categories. */
final class StorageInventory {
    static final List<String> CATEGORIES = List.of("logs", "backups", "cache", "verification", "diagnostics", "data", "mods");
    static final long MAX_ENTRIES = 100_000;
    static final long MAX_MILLIS = 10_000;
    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};

    record Category(String name, long files, long directories, long bytes, String status, String action) {
        boolean complete() { return status.equals("COMPLETO") || status.equals("AUSENTE"); }
    }

    record Report(List<Category> categories, long entries, long maxEntries, long maxMillis) {
        boolean complete() { return categories.stream().allMatch(Category::complete); }

        void print(PrintStream output) {
            output.println("Inventario SOMENTE LEITURA: " + (complete() ? "COMPLETO" : "INCOMPLETO"));
            output.println("Escopo: somente categorias conhecidas do home; nao e o total do home/disco.");
            output.println("Bytes logicos de arquivos regulares (nao blocos alocados); diretorios incluem raiz da categoria.");
            output.println("Limites globais: entradas=" + maxEntries + ", tempo_ms=" + maxMillis + "; entradas observadas=" + entries);
            for (Category category : categories) {
                output.println(category.name + ": " + category.status + " arquivos=" + category.files
                        + " diretorios=" + category.directories + " bytes=" + category.bytes
                        + (category.complete() ? "" : " (parcial; nao representa total)"));
                if (!category.action.isEmpty()) output.println("  " + category.action);
            }
            output.println("Sem limpeza/restauracao; nomes e conteudos privados omitidos. Sem snapshot: feche escritores e repita para estabilidade.");
        }
    }

    private StorageInventory() { }

    static Report inspect(Path game, Path home) throws IOException {
        return inspect(game, home, MAX_ENTRIES, MAX_MILLIS);
    }

    static Report inspect(Path game, Path home, long maxEntries, long maxMillis) throws IOException {
        if (maxEntries < 1 || maxMillis < 1 || maxMillis > Long.MAX_VALUE / 1_000_000L) {
            throw new IllegalArgumentException("Limites do inventario devem ser positivos e representaveis");
        }
        // Do not use InstallPaths.resolve: its recursive validation also scans unknown subtrees.
        Path root = validatedDirectory(game, "gameRoot"), target = validatedDirectory(home, "home instalado");
        if (root.startsWith(target) || (target.startsWith(root) && !target.equals(root.resolve("vbrasfoot")))) {
            throw new IOException("Home recusado: dentro do gameRoot use somente gameRoot/vbrasfoot; saves originais nao sao inventariados");
        }
        Budget budget = new Budget(maxEntries, maxMillis);
        List<Category> categories = new ArrayList<>();
        for (String name : CATEGORIES) categories.add(scan(target.resolve(name), name, budget));
        return new Report(List.copyOf(categories), budget.entries, maxEntries, maxMillis);
    }

    private static Path validatedDirectory(Path path, String label) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            InstallPaths.rejectRedirects(absolute);
            if (!Files.readAttributes(absolute, BasicFileAttributes.class, NOFOLLOW).isDirectory()) throw new IOException();
            return absolute.toRealPath();
        } catch (IOException | SecurityException failure) {
            // No underlying path/cause: CLI stack traces must not disclose private names.
            throw new IOException(label + " deve existir como diretorio real acessivel, sem links/junctions/aliases; corrija --home/--game-dir e permissoes");
        }
    }

    private static Category scan(Path category, String name, Budget budget) {
        long files = 0, directories = 0, bytes = 0;
        ArrayDeque<Path> pending = new ArrayDeque<>();
        try {
            budget.check();
            BasicFileAttributes attributes;
            try { attributes = Files.readAttributes(category, BasicFileAttributes.class, NOFOLLOW); }
            catch (NoSuchFileException missing) { return new Category(name, 0, 0, 0, "AUSENTE", ""); }
            budget.take();
            safeEntry(category, attributes);
            if (!attributes.isDirectory()) throw new IOException("category-type");
            directories = 1;
            pending.add(category);
            while (!pending.isEmpty()) {
                budget.check();
                Path directory = pending.removeFirst();
                // Revalidate immediately before traversal, including every ancestor and Windows junction aliases.
                safeEntry(directory, Files.readAttributes(directory, BasicFileAttributes.class, NOFOLLOW));
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("changed-type");
                try (var stream = Files.newDirectoryStream(directory)) {
                    var iterator = stream.iterator();
                    while (true) {
                        budget.check();
                        if (!iterator.hasNext()) break;
                        budget.take();
                        Path entry = iterator.next();
                        BasicFileAttributes entryAttributes = Files.readAttributes(entry, BasicFileAttributes.class, NOFOLLOW);
                        safeEntry(entry, entryAttributes);
                        if (entryAttributes.isDirectory()) {
                            directories = add(directories, 1);
                            pending.addLast(entry);
                        } else if (entryAttributes.isRegularFile()) {
                            long nextBytes = add(bytes, entryAttributes.size());
                            files = add(files, 1);
                            bytes = nextBytes;
                        } else throw new IOException("special-file");
                    }
                }
            }
            budget.check();
            return new Category(name, files, directories, bytes, "COMPLETO", "");
        } catch (Limit reached) {
            return new Category(name, files, directories, bytes, "INCOMPLETO", "Limite de " + reached.getMessage() + " atingido; feche escritores e repita. Se persistir, solicite suporte a inventario maior; nao trate estes valores como total. Nada foi removido.");
        } catch (ArithmeticException overflow) {
            return new Category(name, files, directories, bytes, "INCOMPLETO", "Contagem excede long; nenhum total seguro disponivel. Nada foi removido.");
        } catch (IOException | DirectoryIteratorException | SecurityException unsafe) {
            return new Category(name, files, directories, bytes, "RECUSADO", "Verifique permissoes, tipo da categoria e links/junctions/reparse points; feche escritores e repita. Nenhum redirecionamento e seguido.");
        }
    }

    private static void safeEntry(Path path, BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("redirect-or-special");
        InstallPaths.rejectRedirects(path);
    }

    static long add(long current, long value) {
        if (current < 0 || value < 0) throw new ArithmeticException("negative-size");
        return Math.addExact(current, value);
    }

    private static final class Limit extends IOException {
        Limit(String reason) { super(reason); }
    }

    private static final class Budget {
        final long maxEntries, durationNanos, started = System.nanoTime();
        long entries;
        Budget(long maxEntries, long maxMillis) {
            this.maxEntries = maxEntries;
            durationNanos = maxMillis * 1_000_000L;
        }
        void check() throws Limit {
            if (System.nanoTime() - started >= durationNanos) throw new Limit("tempo");
        }
        void take() throws Limit {
            check();
            if (entries >= maxEntries) throw new Limit("entradas");
            entries++;
        }
    }
}
