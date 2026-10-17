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
        if (options.validateMod != null) { ModValidator.inspect(options.validateMod); return; }
        if (options.storageStatus) {
            StorageInventory.Report report = StorageInventory.inspect(options.game, options.home);
            report.print(System.out);
            if (!report.complete()) throw new IllegalStateException("Inventario incompleto/recusado; consulte as acoes por categoria acima. Nenhum arquivo alterado.");
            return;
        }
        InstallPaths paths = InstallPaths.resolve(options.game, options.home);
        Path game = paths.gameRoot(), home = paths.installRoot();
        if (Files.exists(home.resolve(".update-pending"))) {
            throw new IllegalStateException("Atualizacao incompleta: execute recover-update.ps1 antes de iniciar o loader");
        }
        Files.createDirectories(home);
        try (SessionDiagnostics diagnostic = SessionDiagnostics.open(home, game, args)) {
            try { launch(options, game, home, diagnostic.logger(), diagnostic.directory()); }
            catch (Exception | LinkageError failure) {
                diagnostic.logger().log(java.util.logging.Level.SEVERE, "stage=failed", failure);
                failure.printStackTrace(System.err);
                throw failure;
            }
        }
    }

    private static void launch(Options options, Path game, Path home, Logger logger, Path sessionDirectory) throws Exception {
        logger.info("stage=profile-verification");
        Path executable = Profile.verify(game);
        logger.info("stage=profile-verified sha256=" + Profile.SHA256);
        System.out.println("vbrasfoot " + br.vbrasfoot.api.Sdk.VERSION + " | " + Profile.ID + " | SHA-256 " + Profile.SHA256);
        if (options.manageMod != null || options.list) {
            try (FileChannel channel = FileChannel.open(home.resolve(".runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IllegalStateException("Feche o loader antes de alterar/listar a selecao pela linha de comando");
                if (options.safe) { System.out.println("Modo seguro ignora mods e selecao."); return; }
                ModSelection selection = ModSelection.read(home);
                if (options.manageMod != null) {
                    selection = selection.change(options.manageMod, options.enableMod);
                    System.out.println("Selecao salva para proximo inicio; JARs/saves intactos. Sem hot reload.");
                }
                for (var entry : selection.inventory()) System.out.println((entry.enabled() ? "HABILITADO " : "DESABILITADO ") + entry.file()
                        + (entry.manifest() == null ? " INVALIDO: " + entry.error() : " id=" + entry.manifest().id() + " version=" + entry.manifest().version()));
                return;
            }
        }
        try (FileChannel lockChannel = FileChannel.open(home.resolve(".runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Outra instancia do vbrasfoot esta usando este diretorio");
            // Selection is read while owning the same lock as CLI writes.
            // Safe mode and offline save checks deliberately ignore malformed mod JARs/config.
            List<ModManifest> manifests = options.safe || options.checkSave != null ? List.of() : ModSelection.read(home).selected();
            for (ModManifest manifest : manifests) {
                System.out.println("Mod: " + manifest.id() + " " + manifest.version());
                if (options.validate) System.out.println("  dependencias=" + manifest.dependencies()
                        + " opcionais=" + manifest.optionalDependencies() + " conflitos=" + manifest.conflicts()
                        + " capacidades=" + manifest.requiredCapabilities() + " permissoes=" + manifest.permissions());
            }
            ModLoader.requireObservationMode(manifests, options.probe);
            logger.info("stage=runtime-preparation");
            try (ProbeSession probe = options.probe ? new ProbeSession(logger, sessionDirectory.resolve("probe-delivery.properties")) : null;
                 LegacyRuntime runtime = LegacyRuntime.prepare(executable, home, options.probe)) {
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
                try (ModUi ui = new ModUi(runtime, logger); ModLoader mods = new ModLoader(logger, probe == null ? null : probe.observations())) {
                    if (!options.safe) {
                        mods.load(manifests, new CareerBridge(runtime), ui, home.resolve("data"));
                        ModManagerPanel.register(ui, home, sessionDirectory, mods::fileStates, mods::failures,
                                DiagnosticExport.Environment.current(options.safe, options.probe));
                        ui.start();
                    }
                    Thread current = Thread.currentThread();
                    ClassLoader previous = current.getContextClassLoader();
                    current.setContextClassLoader(runtime);
                    try {
                        logger.info("stage=game-entry safeMode=" + options.safe + " careerHookProbe=" + options.probe);
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
            ModValidator.validateClass(manifest);
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
        System.out.println("--probe-career-hooks: sonda experimental opt-in de carregar/salvar (nao habilita eventos de mods).");
        System.out.println("--validate-mod JAR: valida manifesto/mainClass sem inicializar codigo; nao precisa do jogo nem grava cache/logs.");
        System.out.println("--storage-status: inventario somente leitura do home existente; sem jogo/mods, logs/cache/backup ou limpeza.");
        System.out.println("--disable-mod nome.jar | --enable-mod nome.jar: selecao para proximo inicio, com jogo fechado; sem hot reload.");
        System.out.println("Avancado: --home PASTA --game-dir PASTA; apos --, argumentos do jogo.");
    }

    static final class Options {
        Path home = Path.of("vbrasfoot"), game = Path.of("Brasfoot22-23"), checkSave, validateMod;
        boolean validate, list, safe, help, probe, storageStatus;
        String manageMod;
        boolean enableMod;
        List<String> gameArguments = new ArrayList<>();
        static Options parse(String[] args) {
            Options result = new Options();
            int modes = 0;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--home" -> result.home = Path.of(value(args, ++i));
                    case "--game-dir" -> result.game = Path.of(value(args, ++i));
                    case "--check-save" -> { result.checkSave = Path.of(value(args, ++i)); modes++; }
                    case "--validate-mod" -> { result.validateMod = Path.of(value(args, ++i)); modes++; }
                    case "--storage-status" -> { result.storageStatus = true; modes++; }
                    case "--validate-only" -> { result.validate = true; modes++; }
                    case "--list-mods" -> { result.list = true; modes++; }
                    case "--disable-mod" -> { result.manageMod = value(args, ++i); result.enableMod = false; modes++; }
                    case "--enable-mod" -> { result.manageMod = value(args, ++i); result.enableMod = true; modes++; }
                    case "--safe-mode" -> result.safe = true;
                    case "--probe-career-hooks" -> result.probe = true;
                    case "--help" -> result.help = true;
                    case "--" -> { result.gameArguments.addAll(List.of(args).subList(i + 1, args.length)); i = args.length; }
                    default -> throw new IllegalArgumentException("Opcao desconhecida: " + args[i]);
                }
            }
            if (modes > 1) throw new IllegalArgumentException("Escolha somente um modo de verificacao/listagem");
            if (result.probe && (result.safe || result.list || result.checkSave != null)) throw new IllegalArgumentException("Sonda nao combina com modo seguro, listagem ou verificacao offline");
            if (result.validateMod != null && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Validacao individual de mod nao aceita modo seguro/sonda/argumentos do jogo");
            if (result.manageMod != null && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Selecao de mods nao aceita modo seguro/sonda/argumentos do jogo");
            if (result.storageStatus && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Inventario nao aceita modo seguro/sonda/argumentos do jogo");
            return result;
        }
        private static String value(String[] args, int index) {
            if (index >= args.length || args[index].startsWith("--")) throw new IllegalArgumentException("Valor de opcao ausente");
            return args[index];
        }
    }
}
