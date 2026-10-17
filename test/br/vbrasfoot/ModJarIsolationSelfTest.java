package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModState;
import br.vbrasfoot.api.Sdk;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.logging.Logger;
import javax.tools.ToolProvider;

/** Real, independently compiled mod JARs; not a sandbox or game/runtime test. */
public final class ModJarIsolationSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static String value(String prefix, String key) { return System.getProperty(prefix + key); }
    private static Object object(String prefix, String key) { return System.getProperties().get(prefix + key); }

    private static Path fixture(Path root, Path sdk, String prefix, String id, String result,
                                String dependency, String filename) throws Exception {
        Path source = Files.createDirectories(root.resolve(id).resolve("src/isolation"));
        Path classes = Files.createDirectories(root.resolve(id).resolve("classes"));
        String main = """
                package isolation;
                import br.vbrasfoot.api.Mod;
                import br.vbrasfoot.api.ModContext;
                public final class Main implements Mod {
                    private static final String PREFIX = "%s";
                    private static final String ID = "%s";
                    static { System.setProperty(PREFIX + ID + ".static", "initialized"); }
                    private final Helper helper = new Helper();
                    public void initialize(ModContext context) throws Exception {
                        System.setProperty(PREFIX + "order", System.getProperty(PREFIX + "order", "") + ID + ";");
                        System.getProperties().put(PREFIX + ID + ".main", getClass());
                        System.getProperties().put(PREFIX + ID + ".instance", this);
                        System.getProperties().put(PREFIX + ID + ".helper", helper);
                        try {
                            Class<?> exclusive = Class.forName("isolation.ProviderOnly", false, getClass().getClassLoader());
                            System.getProperties().put(PREFIX + ID + ".exclusive", exclusive);
                            System.setProperty(PREFIX + ID + ".lookup", "found");
                        } catch (ClassNotFoundException expected) {
                            System.setProperty(PREFIX + ID + ".lookup", expected.getClass().getName());
                        }
                        context.registerAction("shared", ID, () -> {
                            System.setProperty(PREFIX + ID + ".result", helper.next());
                        });
                    }
                    public void shutdown() {
                        System.setProperty(PREFIX + "stopped", System.getProperty(PREFIX + "stopped", "") + ID + ";");
                        System.setProperty(PREFIX + ID + ".shutdown", "done");
                    }
                }
                """.formatted(prefix, id);
        String helper = """
                package isolation;
                public final class Helper {
                    static { System.setProperty("%s%s.helper.static", "initialized"); }
                    private int calls;
                    public String next() { return "%s:" + ++calls; }
                }
                """.formatted(prefix, id, result);
        Files.writeString(source.resolve("Main.java"), main, StandardCharsets.UTF_8);
        Files.writeString(source.resolve("Helper.java"), helper, StandardCharsets.UTF_8);
        if (dependency.isEmpty()) {
            Files.writeString(source.resolve("ProviderOnly.java"), """
                    package isolation;
                    public final class ProviderOnly {
                        static { System.setProperty("%sexclusive.static", "initialized"); }
                    }
                    """.formatted(prefix), StandardCharsets.UTF_8);
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "JDK JavaCompiler available");
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
             var files = Files.list(source)) {
            var units = manager.getJavaFileObjectsFromPaths(files.filter(p -> p.toString().endsWith(".java")).sorted().toList());
            boolean compiled = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "17", "-encoding", "UTF-8", "-classpath", sdk.toString(),
                            "-d", classes.toString()), null, units).call();
            check(compiled, "compile " + id + " against public SDK: " + diagnostics.getDiagnostics());
        }
        String manifest = "manifestVersion=1\nid=" + id + "\nname=Isolation " + id
                + "\nversion=1.0.0\nmainClass=isolation.Main\napiVersion=1\ngameProfile=" + Sdk.GAME_PROFILE
                + "\npermissions=ui.register\ndependencies=" + dependency + "\n";
        Files.writeString(root.resolve(id).resolve("vbrasfoot.mod.properties"), manifest, StandardCharsets.UTF_8);
        Path jar = root.resolve("mods").resolve(filename);
        Manifest jarManifest = new Manifest();
        jarManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (var output = new JarOutputStream(Files.newOutputStream(jar), jarManifest);
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("vbrasfoot.mod.properties"));
            output.write(manifest.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    public static void main(String[] args) throws Exception {
        String token = UUID.randomUUID().toString();
        String prefix = "vbrasfoot.jar-isolation." + token + ".";
        Path root = Files.createDirectories(Path.of("build", "jar-isolation-" + token));
        Files.createDirectories(root.resolve("mods"));
        Path sdk = Path.of("dist", "vbrasfoot-api.jar").toAbsolutePath();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        try {
            check(Files.isRegularFile(sdk), "public SDK JAR exists (run build.ps1 first)");
            Path provider = fixture(root, sdk, prefix, "provider", "ALPHA", "", "z-provider.jar");
            Path dependent = fixture(root, sdk, prefix, "dependent", "BETA", "provider", "a-dependent.jar");
            for (Path jar : List.of(provider, dependent)) {
                ModManifest manifest = ModManifest.read(jar);
                ModValidator.validateClass(manifest);
                check(value(prefix, manifest.id() + ".static") == null, "validation does not initialize main " + manifest.id());
                check(value(prefix, manifest.id() + ".helper.static") == null, "validation does not initialize helper " + manifest.id());
            }
            check(value(prefix, "exclusive.static") == null && value(prefix, "order") == null,
                    "validation does not initialize exclusive class or call initialize");
            List<ModManifest> discovered = ModLoader.discover(root.resolve("mods"));
            check(discovered.stream().map(ModManifest::id).toList().equals(List.of("provider", "dependent")),
                    "dependency ID order overrides opposite JAR filename order");
            check(discovered.get(1).dependencies().equals(List.of("provider")), "real manifest declares provider dependency");
            GameAccess game = () -> { throw new AssertionError("fixture must not access game"); };
            // No game loader supplied; headless invoke tests dispatch, not actual UI integration.
            try (var ui = new ModUi(new ClassLoader() { }, logger); var loader = new ModLoader(logger)) {
                loader.load(discovered, game, ui, root.resolve("data"));
                check("provider;dependent;".equals(value(prefix, "order")), "both real JARs initialized in dependency order");
                for (String id : List.of("provider", "dependent")) {
                    check("initialized".equals(value(prefix, id + ".static"))
                            && "initialized".equals(value(prefix, id + ".helper.static")),
                            "load initializes main and helper " + id);
                }
                check(loader.fileStates().values().stream().allMatch(state -> state == ModState.ACTIVE)
                        && loader.fileStates().size() == 2, "both mods simultaneously active");
                Object first = object(prefix, "provider.instance"), second = object(prefix, "dependent.instance");
                Class<?> firstMain = (Class<?>) object(prefix, "provider.main");
                Class<?> secondMain = (Class<?>) object(prefix, "dependent.main");
                check(first instanceof Mod && second instanceof Mod && first != second, "distinct instances share public Mod API identity");
                check(firstMain.getName().equals(secondMain.getName()) && firstMain != secondMain,
                        "same main binary name has distinct class identities");
                Object firstHelper = object(prefix, "provider.helper"), secondHelper = object(prefix, "dependent.helper");
                check(firstHelper != secondHelper && firstHelper.getClass() != secondHelper.getClass()
                        && firstHelper.getClass().getName().equals(secondHelper.getClass().getName()),
                        "same helper binary name has distinct instances and class identities");
                check(firstMain.getClassLoader() != secondMain.getClassLoader()
                        && firstHelper.getClass().getClassLoader() == firstMain.getClassLoader()
                        && secondHelper.getClass().getClassLoader() == secondMain.getClassLoader(),
                        "each helper belongs to its own mod loader");
                check("found".equals(value(prefix, "provider.lookup")) && object(prefix, "provider.exclusive") instanceof Class<?>,
                        "provider-only class exists and is visible to its owner");
                check("java.lang.ClassNotFoundException".equals(value(prefix, "dependent.lookup"))
                        && object(prefix, "dependent.exclusive") == null,
                        "dependency does not expose provider-only class to dependent loader");
                check(value(prefix, "exclusive.static") == null, "Class.forName(false) does not initialize exclusive class");
                ui.invoke("provider:shared"); ui.invoke("dependent:shared"); ui.invoke("provider:shared");
                check("ALPHA:2".equals(value(prefix, "provider.result")) && "BETA:1".equals(value(prefix, "dependent.result")),
                        "callbacks resolve their own helper code and independent counters");
                loader.close();
                ui.invoke("provider:shared"); ui.invoke("dependent:shared");
                check("ALPHA:2".equals(value(prefix, "provider.result")) && "BETA:1".equals(value(prefix, "dependent.result")),
                        "close revokes both callback registrations");
                check("dependent;provider;".equals(value(prefix, "stopped")), "reverse dependency shutdown order");
                check("done".equals(value(prefix, "provider.shutdown")) && "done".equals(value(prefix, "dependent.shutdown")),
                        "both real mod instances shut down");
                check(loader.fileStates().values().stream().allMatch(state -> state == ModState.STOPPED), "both terminal states stopped");
                check(loader.failures().entries().isEmpty(), "expected lookup rejection is handled by fixture, not a mod failure");
                loader.close();
                check("dependent;provider;".equals(value(prefix, "stopped")), "close is idempotent");
            }
            check(!Files.exists(root.resolve("data")), "no mod data or saves written");
            System.out.println("PASS: " + checks + " real mod JAR isolation checks (synthetic; no game/network/saves). Fixture: " + root);
        } finally {
            // Fixture artifacts stay under ignored build/; only this run's process-local evidence is removed.
            for (Object key : new ArrayList<>(System.getProperties().keySet())) {
                if (key instanceof String name && name.startsWith(prefix)) System.getProperties().remove(key);
            }
        }
    }
}
