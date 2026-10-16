package br.vbrasfoot;

import java.awt.Frame;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        try { run(args); }
        catch (Exception | LinkageError failure) {
            System.err.println("vbrasfoot: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void run(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help) { help(); return; }
        Path game = options.game.toRealPath();
        Path home = options.home.toAbsolutePath().normalize();
        // Reject original resources before creating anything in the supplied home.
        if (home.startsWith(game)) throw new IllegalArgumentException("--home deve ficar fora da pasta do jogo");
        Path ancestor = home;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        if (ancestor.toRealPath().resolve(ancestor.relativize(home)).normalize().startsWith(game)) {
            throw new IllegalArgumentException("--home aponta para a pasta do jogo atraves de link/junction");
        }
        Files.createDirectories(home);
        home = home.toRealPath();
        if (home.startsWith(game)) throw new IllegalArgumentException("--home deve ficar fora da pasta do jogo");
        try (SessionDiagnostics diagnostic = SessionDiagnostics.open(home, game, args)) {
            try { launch(options, game, home, diagnostic.logger()); }
            catch (Exception | LinkageError failure) {
                diagnostic.logger().log(java.util.logging.Level.SEVERE, "stage=failed", failure);
                failure.printStackTrace(System.err);
                throw failure;
            }
        }
    }

    private static void launch(Options options, Path game, Path home, Logger logger) throws Exception {
        logger.info("stage=profile-verification");
        Path executable = Profile.verify(game);
        logger.info("stage=profile-verified sha256=" + Profile.SHA256);
        System.out.println("vbrasfoot 0.1.0 | " + Profile.ID + " | SHA-256 " + Profile.SHA256);
        // Safe mode and offline save checks deliberately ignore even malformed mod JARs.
        List<ModManifest> manifests = options.safe || options.checkSave != null ? List.of() : ModLoader.discover(home.resolve("mods"));
        for (ModManifest manifest : manifests) System.out.println("Mod: " + manifest.id() + " " + manifest.version());
        if (options.list) return;
        try (FileChannel lockChannel = FileChannel.open(home.resolve(".runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Outra instancia do vbrasfoot esta usando este diretorio");
            logger.info("stage=runtime-preparation");
            try (LegacyRuntime runtime = LegacyRuntime.prepare(executable, home)) {
                logger.info("stage=runtime-verified");
                if (options.validate) {
                    // Does not initialize plugin classes or any game class.
                    validateMods(manifests);
                    System.out.println("Validacao concluida: perfil, entrada, bibliotecas, bridge e manifestos. Jogo nao iniciado.");
                    return;
                }
                if (options.checkSave != null) { SaveCheck.run(options.checkSave, home, runtime); return; }
                if (!Path.of("").toAbsolutePath().toRealPath().equals(game)) {
                    throw new IllegalStateException("Inicie pelo run.ps1 (o diretorio de trabalho real deve ser o do jogo)");
                }
                Path backup = backup(game, home);
                System.out.println("Backup antes de iniciar: " + backup);
                logger.info("stage=backup-verified directory=" + backup);
                try (ModUi ui = new ModUi(runtime, logger); ModLoader mods = new ModLoader(logger)) {
                    if (!options.safe) {
                        mods.load(manifests, new CareerBridge(runtime), ui, home.resolve("data"));
                        ui.start();
                    }
                    Thread current = Thread.currentThread();
                    ClassLoader previous = current.getContextClassLoader();
                    current.setContextClassLoader(runtime);
                    try {
                        logger.info("stage=game-entry safeMode=" + options.safe);
                        Class.forName(Profile.ENTRYPOINT, true, runtime).getMethod("main", String[].class)
                                .invoke(null, (Object) options.gameArguments.toArray(String[]::new));
                        awaitWindows();
                        logger.info("stage=game-windows-closed");
                    } finally { current.setContextClassLoader(previous); }
                }
            }
        }
    }

    private static void validateMods(List<ModManifest> manifests) throws Exception {
        for (ModManifest manifest : manifests) {
            try (var loader = new java.net.URLClassLoader(new java.net.URL[] {manifest.jar().toUri().toURL()}, Main.class.getClassLoader())) {
                Class.forName(manifest.mainClass(), false, loader).asSubclass(br.vbrasfoot.api.Mod.class).getConstructor();
            }
        }
    }

    private static Path backup(Path game, Path home) throws Exception {
        Path backups = home.resolve("backups");
        Files.createDirectories(backups);
        Path output = Files.createTempDirectory(backups, "session-");
        List<Path> files = new ArrayList<>();
        Path saves = game.resolve("sav");
        if (Files.isDirectory(saves)) {
            try (var entries = Files.list(saves)) {
                files.addAll(entries.filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p)).toList());
            }
        }
        if (Files.isRegularFile(game.resolve("options.bcf"))) files.add(game.resolve("options.bcf"));
        for (Path source : files) {
            Path target = output.resolve(game.relativize(source));
            Files.createDirectories(target.getParent());
            String before = Profile.digest(source);
            Files.copy(source, target);
            if (!before.equals(Profile.digest(target)) || !before.equals(Profile.digest(source))) {
                throw new IllegalStateException("Arquivo mudou durante backup: " + source + ". Feche o outro jogo e tente novamente.");
            }
        }
        return output;
    }

    private static void awaitWindows() throws Exception {
        long deadline = System.nanoTime() + 60_000_000_000L;
        long emptySince = 0;
        boolean observed = false;
        while (true) {
            boolean hasWindows = CareerBridge.onEdt(() -> {
                for (Frame frame : Frame.getFrames()) if (frame.isDisplayable()) return true;
                return false;
            });
            if (hasWindows) { observed = true; emptySince = 0; }
            else if (observed) {
                if (emptySince == 0) emptySince = System.nanoTime();
                if (System.nanoTime() - emptySince >= 3_000_000_000L) return;
            } else if (System.nanoTime() >= deadline) throw new IllegalStateException("Nenhuma janela do jogo abriu em 60 segundos");
            Thread.sleep(250);
        }
    }

    private static void help() {
        System.out.println("vbrasfoot: use run.ps1 [--validate-only | --list-mods | --safe-mode | --check-save CAMINHO]");
        System.out.println("--validate-only nao inicia o jogo nem inicializa mods; --safe-mode ignora todos os mods.");
        System.out.println("--check-save verifica uma copia privada com o Kryo original; nao inicia a interface do jogo.");
        System.out.println("Avancado: --home PASTA --game-dir PASTA; apos --, argumentos do jogo.");
    }

    static final class Options {
        Path home = Path.of("vbrasfoot"), game = Path.of("Brasfoot22-23"), checkSave;
        boolean validate, list, safe, help;
        List<String> gameArguments = new ArrayList<>();
        static Options parse(String[] args) {
            Options result = new Options();
            int modes = 0;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--home" -> result.home = Path.of(value(args, ++i));
                    case "--game-dir" -> result.game = Path.of(value(args, ++i));
                    case "--check-save" -> { result.checkSave = Path.of(value(args, ++i)); modes++; }
                    case "--validate-only" -> { result.validate = true; modes++; }
                    case "--list-mods" -> { result.list = true; modes++; }
                    case "--safe-mode" -> result.safe = true;
                    case "--help" -> result.help = true;
                    case "--" -> { result.gameArguments.addAll(List.of(args).subList(i + 1, args.length)); i = args.length; }
                    default -> throw new IllegalArgumentException("Opcao desconhecida: " + args[i]);
                }
            }
            if (modes > 1) throw new IllegalArgumentException("Escolha somente um modo de verificacao/listagem");
            return result;
        }
        private static String value(String[] args, int index) {
            if (index >= args.length || args[index].startsWith("--")) throw new IllegalArgumentException("Valor de opcao ausente");
            return args[index];
        }
    }
}
