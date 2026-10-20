package br.vbrasfoot;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class LegacyRuntime extends URLClassLoader {
    private final boolean probe;
    private LegacyRuntime(URL[] urls, boolean probe) { super(urls, LegacyRuntime.class.getClassLoader()); this.probe = probe; }

    static LegacyRuntime prepare(Path executable, Path home) throws Exception {
        return prepare(executable, home, false);
    }

    static LegacyRuntime prepare(Path executable, Path home, boolean probe) throws Exception {
        if (probe && !Object.class.getModule().isExported("jdk.internal.org.objectweb.asm", LegacyRuntime.class.getModule())) {
            throw new IOException("Sonda exige --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED");
        }
        // Java ZipFile understands the PE prefix; .NET ZipArchive does not without rebasing.
        Path cache = home.resolve("cache").resolve(Profile.SHA256);
        Files.createDirectories(cache);
        Path jar = cache.resolve("game.jar");
        List<URL> urls = new ArrayList<>();
        urls.add(jar.toUri().toURL());
        try (ZipFile source = new ZipFile(executable.toFile())) {
            ZipEntry manifestEntry = source.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) throw new IOException("Manifesto do jogo ausente");
            try (var input = source.getInputStream(manifestEntry)) {
                Manifest manifest = new Manifest(input);
                if (!Profile.ENTRYPOINT.equals(manifest.getMainAttributes().getValue("Rsrc-Main-Class"))) {
                    throw new IOException("Ponto de entrada inesperado no executavel");
                }
            }
            Path temporary = Files.createTempFile(cache, "game-", ".tmp");
            try {
                try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                    var entries = source.entries();
                    var names = new HashSet<String>();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!names.add(entry.getName())) throw new IOException("Entrada duplicada: " + entry.getName());
                        output.putNextEntry(new ZipEntry(entry.getName()));
                        if (!entry.isDirectory()) {
                            try (var input = source.getInputStream(entry)) { input.transferTo(output); }
                        }
                        output.closeEntry();
                    }
                }
                Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            for (String name : Profile.LIBRARIES) {
                ZipEntry entry = source.getEntry(name);
                if (entry == null) throw new IOException("Biblioteca original ausente: " + name);
                Path library = cache.resolve(name);
                try (var input = source.getInputStream(entry)) {
                    Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
                }
                urls.add(library.toUri().toURL());
            }
        }
        // Check again after copying to guard against a changed input during preparation.
        if (!Profile.SHA256.equals(Profile.digest(executable))) throw new IOException("Executavel mudou durante preparacao");
        LegacyRuntime runtime = new LegacyRuntime(urls.toArray(URL[]::new), probe);
        try { runtime.verify(); return runtime; }
        catch (Exception | Error failure) { runtime.close(); throw failure; }
    }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!probe || (!name.equals("c.a") && !name.equals("best.f") && !name.equals("best.S") && !name.equals("a.hU") && !name.equals("a.bV") && !name.equals("a.cR") && !name.equals("a.cS") && !name.equals("a.hk") && !name.equals("a.ho")
                && !name.equals("best.ah") && !name.equals("best.C") && !numericCachedField(name) && !name.equals("com.esotericsoftware.kryo.io.Output"))) return super.findClass(name);
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) throw new ClassNotFoundException(name);
        try (var input = resource.openStream()) {
            byte[] original = input.readNBytes(1024 * 1024 + 1);
            if (original.length > 1024 * 1024) throw new IOException("Classe de hook grande demais");
            byte[] transformed = name.equals("c.a")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.CareerTransformer").getMethod("transform", byte[].class).invoke(null, (Object) original)
                    : numericCachedField(name)
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.FinanceFieldTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, original)
                    : name.equals("best.ah") || name.equals("best.C")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.FinanceMutationTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, original)
                    : name.equals("best.f")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.CreationProgressTransformer").getMethod("transform", byte[].class).invoke(null, (Object) original)
                    : name.equals("a.bV")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.CreationDialogTransformer").getMethod("transform", byte[].class).invoke(null, (Object) original)
                    : name.equals("a.hU") || name.equals("best.S")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.CreationEntryTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, original)
                    : name.equals("com.esotericsoftware.kryo.io.Output")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.OutputCloseTransformer").getMethod("transform", byte[].class).invoke(null, (Object) original)
                    : name.equals("a.cR") || name.equals("a.cS") || name.equals("a.hk") || name.equals("a.ho")
                    ? (byte[]) Class.forName("br.vbrasfoot.hooks.AsyncCreationTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, original)
                    : (byte[]) Class.forName("br.vbrasfoot.hooks.CreationPathTransformer").getMethod("transform", String.class, byte[].class).invoke(null, name, original);
            URL source = resource.openConnection() instanceof java.net.JarURLConnection jar ? jar.getJarFileURL() : getURLs()[0];
            Class<?> defined = defineClass(name, transformed, 0, transformed.length,
                    new java.security.CodeSource(source, (java.security.cert.Certificate[]) null));
            if (name.equals("best.ah") || name.equals("best.C")) HookProbe.financeInstrumented(defined);
            return defined;
        } catch (Exception | LinkageError failure) {
            // Do not fall back to an uninstrumented class after an explicitly requested probe fails.
            throw new IllegalStateException("Sonda recusada antes de definir " + name, failure);
        }
    }

    private static boolean numericCachedField(String name) {
        return name.equals("com.esotericsoftware.kryo.serializers.UnsafeCacheFields$UnsafeIntField")
                || name.equals("com.esotericsoftware.kryo.serializers.UnsafeCacheFields$UnsafeLongField")
                || name.equals("com.esotericsoftware.kryo.serializers.AsmCacheFields$AsmIntField")
                || name.equals("com.esotericsoftware.kryo.serializers.AsmCacheFields$AsmLongField")
                || name.equals("com.esotericsoftware.kryo.serializers.ObjectField$ObjectIntField")
                || name.equals("com.esotericsoftware.kryo.serializers.ObjectField$ObjectLongField");
    }

    void verify() throws Exception {
        Class.forName(Profile.ENTRYPOINT, false, this).getMethod("main", String[].class);
        Class<?> state = Class.forName("c.a", false, this);
        if (!state.getField("SR").getType().getName().equals("best.f")
                || !state.getField("afQ").getType().getName().equals("best.ay")) {
            throw new IOException("Estrutura da carreira incompativel");
        }
        Class.forName("best.f", false, this).getMethod("P");
        Class.forName("best.f", false, this).getMethod("aN");
        if (Class.forName("best.f", false, this).getMethod("bb").getReturnType() != java.util.Calendar.class
                || Class.forName("best.f", false, this).getMethod("J").getReturnType() != int.class) throw new IOException("Calendario incompativel");
        Class.forName("best.n", false, this).getMethod("db");
        Class<?> careerPanel = Class.forName("a.eg", false, this);
        if (careerPanel.getDeclaredField("ve").getType() != javax.swing.JToolBar.class
                || careerPanel.getDeclaredField("HX").getType() != javax.swing.JButton.class) {
            throw new IOException("Barra interna da carreira incompativel");
        }
        Class<?> club = Class.forName("best.ah", false, this);
        for (String method : List.of("getNome", "gD", "kc")) club.getMethod(method);
        if (club.getMethod("kb").getReturnType() != long.class) throw new IOException("Saldo de clube incompativel");
        Class<?> player = Class.forName("best.F", false, this);
        for (String method : List.of("getNome", "gD", "getIdade", "getPosicao", "fi", "fk")) player.getMethod(method);
        Class.forName("com.esotericsoftware.kryo.Kryo", false, this);
        if (probe) {
            Class.forName("com.esotericsoftware.kryo.io.Output", false, this).getMethod("close");
            Class.forName("a.bV", false, this).getMethod("AB");
            Class.forName("a.cR", false, this).getMethod("AC");
            Class.forName("a.cR", false, this).getMethod("pn");
            Class.forName("a.cR", false, this).getMethod("oR");
            Class.forName("a.hk", false, this).getMethod("oR");
            Class.forName("a.cS", false, this).getDeclaredMethod("run");
            Class.forName("a.ho", false, this).getDeclaredMethod("run");
        }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("br.vbrasfoot.")) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                try { result = findClass(name); }
                catch (ClassNotFoundException absent) { result = super.loadClass(name, false); }
            }
            if (resolve) resolveClass(result);
            return result;
        }
    }
}
