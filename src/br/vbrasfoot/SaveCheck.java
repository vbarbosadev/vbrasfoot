package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Offline check on private copies; never invokes the game's load/save/UI entry points. */
final class SaveCheck {
    record Roots(Object game, Object secondary) { }

    static void run(Path original, Path home, LegacyRuntime runtime) throws Exception {
        original = original.toRealPath();
        if (!Files.isRegularFile(original) || !original.toString().matches("(?i).*\\.(s22|sbck)$")) {
            throw new IllegalArgumentException("Informe um arquivo .s22 ou .sbck existente");
        }
        String before = Profile.digest(original);
        Path checks = home.resolve("verification");
        Files.createDirectories(checks);
        Path directory = Files.createTempDirectory(checks, "save-");
        Path copy = directory.resolve("input.s22");
        Files.copy(original, copy);
        if (!before.equals(Profile.digest(copy))) throw new IllegalStateException("Save mudou durante copia; feche o jogo e tente novamente");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(runtime);
        try {
            // The original entry point initializes this resource before any Kryo constructors run.
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            Roots first = read(copy, runtime);
            GameAccess.Snapshot snapshot = snapshot(first, runtime);
            if (!snapshot.loaded()) throw new IllegalStateException("Save decodificado, mas nenhuma carreira com clube humano foi identificada");
            Path output = directory.resolve("roundtrip.s22");
            write(output, first, runtime);
            // Distinguish same-object nondeterminism from a changed decoded representation.
            // Neither byte stability nor public snapshots prove full graph equivalence.
            Path repeat = directory.resolve("same-roots-repeat.s22");
            write(repeat, first, runtime);
            Roots second = read(output, runtime);
            GameAccess.Snapshot restored = snapshot(second, runtime);
            if (!snapshot.equals(restored)) throw new IllegalStateException("Clubes/jogadores diferem apos round-trip");
            Path generation2 = directory.resolve("second-generation.s22");
            write(generation2, second, runtime);
            if (!snapshot.equals(snapshot(read(generation2, runtime), runtime))) {
                throw new IllegalStateException("Clubes/jogadores diferem apos segunda geracao");
            }
            String after = Profile.digest(original);
            if (!before.equals(after)) throw new IllegalStateException("Save original mudou durante verificacao");
            Properties report = new Properties();
            report.setProperty("profile", Profile.ID);
            report.setProperty("executable.sha256", Profile.SHA256);
            report.setProperty("original", original.toString());
            report.setProperty("original.sha256.before", before);
            report.setProperty("original.sha256.after", after);
            report.setProperty("roundtrip.sha256", Profile.digest(output));
            report.setProperty("byteIdentical", Boolean.toString(before.equals(Profile.digest(output))));
            report.setProperty("original.size", Long.toString(Files.size(copy)));
            report.setProperty("roundtrip.size", Long.toString(Files.size(output)));
            report.setProperty("original.roundtrip.firstMismatch", Long.toString(Files.mismatch(copy, output)));
            report.setProperty("sameRoots.repeat.byteIdentical", Boolean.toString(Files.mismatch(output, repeat) == -1));
            report.setProperty("secondGeneration.byteIdentical", Boolean.toString(Files.mismatch(output, generation2) == -1));
            report.setProperty("secondGeneration.clubPlayerSnapshotsEqual", "true");
            report.setProperty("binaryDifference.cause", "Unresolved; repeated writes are diagnostic observations, not proof of compatibility");
            report.setProperty("clubs", Integer.toString(snapshot.clubs().size()));
            report.setProperty("players", Integer.toString(snapshot.clubs().stream().mapToInt(c -> c.players().size()).sum()));
            report.setProperty("clubPlayerSnapshotsEqual", "true");
            report.setProperty("coverage", "Offline Kryo read/write of both roots; not a gameplay or original-exe reopening test");
            try (var writer = Files.newBufferedWriter(directory.resolve("report.properties"))) { report.store(writer, "vbrasfoot save verification"); }
            System.out.println("Save verificado: " + report.getProperty("clubs") + " clubes; " + report.getProperty("players") + " jogadores.");
            System.out.println("Original intacto. Round-trip identico em bytes: " + report.getProperty("byteIdentical"));
            System.out.println("Relatorio e copias: " + directory);
        } finally {
            Class<?> state = Class.forName("c.a", false, runtime);
            state.getField("SR").set(null, null);
            state.getField("afQ").set(null, null);
            thread.setContextClassLoader(previous);
        }
    }

    private static Object kryo(ClassLoader runtime) throws Exception {
        Class<?> type = Class.forName("com.esotericsoftware.kryo.Kryo", true, runtime);
        Object kryo = type.getConstructor().newInstance();
        type.getMethod("setClassLoader", ClassLoader.class).invoke(kryo, runtime);
        type.getMethod("setRegistrationRequired", boolean.class).invoke(kryo, false);
        return kryo;
    }

    private static Roots read(Path path, ClassLoader runtime) throws Exception {
        Object kryo = kryo(runtime);
        Class<?> inputType = Class.forName("com.esotericsoftware.kryo.io.Input", true, runtime);
        try (InputStream stream = Files.newInputStream(path)) {
            Object input = inputType.getConstructor(InputStream.class).newInstance(stream);
            var read = kryo.getClass().getMethod("readClassAndObject", inputType);
            Object game = read.invoke(kryo, input);
            Object secondary = read.invoke(kryo, input);
            if (game == null || secondary == null || !game.getClass().getName().equals("best.f")
                    || !secondary.getClass().getName().equals("best.ay")) {
                throw new IllegalStateException("Raizes de save incompativeis com o perfil 22-23");
            }
            if (((Number) inputType.getMethod("read").invoke(input)).intValue() != -1) {
                throw new IllegalStateException("Dados extras apos as duas raizes do save");
            }
            return new Roots(game, secondary);
        }
    }

    private static void write(Path path, Roots roots, ClassLoader runtime) throws Exception {
        Object kryo = kryo(runtime);
        Class<?> outputType = Class.forName("com.esotericsoftware.kryo.io.Output", true, runtime);
        try (OutputStream stream = Files.newOutputStream(path)) {
            Object output = outputType.getConstructor(OutputStream.class).newInstance(stream);
            var write = kryo.getClass().getMethod("writeClassAndObject", outputType, Object.class);
            write.invoke(kryo, output, roots.game());
            write.invoke(kryo, output, roots.secondary());
            outputType.getMethod("flush").invoke(output);
        }
    }

    private static GameAccess.Snapshot snapshot(Roots roots, ClassLoader runtime) throws Exception {
        Class<?> state = Class.forName("c.a", false, runtime);
        state.getField("SR").set(null, roots.game());
        state.getField("afQ").set(null, roots.secondary());
        return new CareerBridge(runtime).snapshot();
    }
}
