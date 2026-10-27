package br.vbrasfoot;
import java.io.IOException;

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
        if (options.inspectLoadValidation != null) {
            var report = LoadValidationInspector.inspect(options.inspectLoadValidation); report.print(System.out);
            if (report.status() != LoadValidationInspector.Status.QUALIFIED_LOAD_OBSERVED) throw new IllegalStateException("Evidencia de carga nao qualificada; nenhuma escrita habilitada");
            return;
        }
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
        applyDistributionProfile(options, home);
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
            List<ModManifest> manifests = selectedManifests(options, home);
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
                 CareerAssociationCoordinator coordinator = null;
                 if (!options.safe && options.probe) {
                     var associationOwners = java.util.stream.Stream.concat(manifests.stream().map(ModManifest::id),
                             java.util.stream.Stream.of(LoanManagerPanel.OWNER)).distinct().toList();
                     if (associationOwners.size() <= 64) {
                         coordinator = new CareerAssociationCoordinator(game.resolve("sav"), home.resolve("data").resolve(".career-state"), runtime, associationOwners)
                                 .operational(probe.operational());
                     } else logger.warning("career-association-panel=unavailable reason=configured-owner-capacity; no partial inventory or write authority");
                 }
                 try (ModUi ui = new ModUi(runtime, logger); ModLoader mods = new ModLoader(logger,
                         probe == null ? null : probe.observations(), probe == null ? null : probe.ioEvents(), coordinator)) {
                     if (!options.safe) {
                         var bridge = ui.bridge();
                         if (coordinator != null && !options.sponsorsOnly) {
                             var windows = new TransferWindowService(new CareerSidecarStore(home.resolve("data/.career-state/transfer-windows")), coordinator, bridge);
                             coordinator.transferWindows(windows);
                             var commands = coordinator;
                             mods.gameplay((owner, active) -> new LoanService(commands, runtime, bridge, active), windows);
                         }
                           if (coordinator != null && !options.loansOnly) {
                               var sponsors = new SponsorService(coordinator, runtime);
                               mods.sponsors(sponsors);
                               ui.own(new NativeSponsorMessages(runtime, sponsors, logger));
                           }
                        mods.load(manifests, bridge, ui, home.resolve("data"));
                          if (!options.loansOnly && !options.sponsorsOnly && !options.combined) ModManagerPanel.register(ui, home, sessionDirectory, mods::fileStates, mods::failures,
                                DiagnosticExport.Environment.current(options.safe, options.probe));
                          if (coordinator != null) {
                                if (!options.loansOnly && !options.sponsorsOnly) CareerAssociationPanel.register(ui, coordinator, logger);
                              var sessionCoordinator = coordinator;
                              ui.own(probe.ioEvents().subscribe(update -> sessionCoordinator.clearCareerDataSelection()));
                              if (!options.sponsorsOnly && manifests.stream().noneMatch(manifest -> manifest.id().equals(LoanManagerPanel.OWNER))) {
                                    if (!options.loansOnly && !options.combined) LoanManagerPanel.register(ui, coordinator, bridge, () -> new LegacyLoanAdapter(runtime), logger);
                                   else ui.register("vbrasfoot:loans", "Emprestimos", () -> javax.swing.JOptionPane.showMessageDialog(null,
                                            "Abra Times, selecione o clube e o jogador e clique em Emprestimos na tela original.\n"
                                            + "Ja listado: transferencia direta, sem taxa. Fora da lista: revise e confirme a proposta. Depois, salve pelo jogo.",
                                           "Emprestimos", javax.swing.JOptionPane.INFORMATION_MESSAGE));
                                 ui.own(new NativeLoanUi(runtime, bridge, coordinator, home.resolve("data").resolve(".career-state"),
                                         home.resolve("data").resolve("native-loan-association.properties"), logger));
                             }
                              else if (!options.sponsorsOnly) logger.warning("loan-manager unavailable: reserved built-in owner conflicts with selected mod ID");
                         }
                         ui.start(!options.loansOnly && !options.sponsorsOnly);
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
    /** Distribution profile disables external plugins without deleting any existing JAR, setting or history. */
    static boolean loansOnlyProfile(Path home) throws IOException {
        Path marker = home.resolve("loans-only.txt"); CareerSidecarStore.plain(marker);
        if (!Files.exists(marker)) return false;
        if (!Files.isRegularFile(marker) || Files.size(marker) > 64 || !Files.readString(marker).trim().equals("vbrasfoot-loans-only-v1"))
            throw new IOException("Perfil de emprestimos invalido; arquivo loans-only.txt preservado");
        return true;
    }
    static List<ModManifest> selectedManifests(Options options, Path home) throws IOException {
        if (options.safe || options.loansOnly || options.checkSave != null) return List.of();
        if (options.sponsorsOnly || options.combined) {
            Path jar = home.resolve("mods/br.vbrasfoot.sponsor-manager.jar"); CareerSidecarStore.plain(jar);
            var manifest = ModManifest.read(jar);
            if (!SponsorActivationStore.OWNER.equals(manifest.id())) throw new IOException("Pacote de patrocinio com ID incorreto; nenhum outro plugin sera carregado");
            return List.of(manifest);
        }
        return ModSelection.read(home).selected();
    }
    static boolean sponsorsOnlyProfile(Path home) throws IOException {
        return distributionProfile(home).equals("vbrasfoot-sponsors-only-v1");
    }
    static void applyDistributionProfile(Options options, Path home) throws IOException {
        if (options.loansOnly || options.sponsorsOnly || options.combined || options.developer) return;
        String profile = distributionProfile(home);
        if (profile.equals("vbrasfoot-developer-v1")) options.developer = true;
        else if (profile.equals("vbrasfoot-combined-v1")) options.combined = true;
        else if (profile.equals("vbrasfoot-sponsors-only-v1")) options.sponsorsOnly = true;
        else options.loansOnly = loansOnlyProfile(home);
    }
    private static String distributionProfile(Path home) throws IOException {
        Path marker = home.resolve("distribution-profile.txt"); CareerSidecarStore.plain(marker);
        if (!Files.exists(marker)) return "";
        if (!Files.isRegularFile(marker) || Files.size(marker) > 64)
            throw new IOException("Perfil de distribuicao invalido; nenhum plugin extra sera carregado");
        String value = Files.readString(marker).trim();
        if (!value.equals("vbrasfoot-sponsors-only-v1") && !value.equals("vbrasfoot-combined-v1") && !value.equals("vbrasfoot-developer-v1"))
            throw new IOException("Perfil de distribuicao invalido; nenhum plugin extra sera carregado");
        return value;
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
        System.out.println("--probe-career-hooks: sonda experimental opt-in de carregar/salvar (nao habilita escrita ou prontidao da carreira).");
        System.out.println("Com a sonda: menu Carreira e diarios verifica stream/roots e permite confirmar apenas metadados externos; nao habilita escritas no jogo.");
        System.out.println("--validate-mod JAR: valida manifesto/mainClass sem inicializar codigo; nao precisa do jogo nem grava cache/logs.");
        System.out.println("--storage-status: inventario somente leitura do home existente; sem jogo/mods, logs/cache/backup ou limpeza.");
        System.out.println("--inspect-load-validation PASTA-SESSAO: le relatorios encerrados sem jogo, logs novos ou permissao de escrita.");
        System.out.println("--disable-mod nome.jar | --enable-mod nome.jar: selecao para proximo inicio, com jogo fechado; sem hot reload.");
        System.out.println("Avancado: --home PASTA --game-dir PASTA; apos --, argumentos do jogo.");
    }

    static final class Options {
        Path home = Path.of("vbrasfoot"), game = Path.of("Brasfoot22-23"), checkSave, validateMod, inspectLoadValidation;
        boolean validate, list, safe, help, probe, storageStatus, loansOnly, sponsorsOnly, combined, developer;
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
                    case "--inspect-load-validation" -> { result.inspectLoadValidation = Path.of(value(args, ++i)); modes++; }
                    case "--validate-only" -> { result.validate = true; modes++; }
                    case "--list-mods" -> { result.list = true; modes++; }
                    case "--disable-mod" -> { result.manageMod = value(args, ++i); result.enableMod = false; modes++; }
                    case "--enable-mod" -> { result.manageMod = value(args, ++i); result.enableMod = true; modes++; }
                    case "--safe-mode" -> result.safe = true;
                    case "--probe-career-hooks" -> result.probe = true;
                    case "--loans-only" -> result.loansOnly = true;
                    case "--sponsors-only" -> result.sponsorsOnly = true;
                    case "--combined" -> result.combined = true;
                    case "--developer-mods" -> result.developer = true;
                    case "--help" -> result.help = true;
                    case "--" -> { result.gameArguments.addAll(List.of(args).subList(i + 1, args.length)); i = args.length; }
                    default -> throw new IllegalArgumentException("Opcao desconhecida: " + args[i]);
                }
            }
            if (modes > 1) throw new IllegalArgumentException("Escolha somente um modo de verificacao/listagem");
            if ((result.loansOnly ? 1 : 0) + (result.sponsorsOnly ? 1 : 0) + (result.combined ? 1 : 0) + (result.developer ? 1 : 0) > 1)
                throw new IllegalArgumentException("Escolha somente um perfil: emprestimos, patrocinios ou combinado");
            if (result.probe && (result.safe || result.list || result.checkSave != null)) throw new IllegalArgumentException("Sonda nao combina com modo seguro, listagem ou verificacao offline");
            if (result.validateMod != null && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Validacao individual de mod nao aceita modo seguro/sonda/argumentos do jogo");
            if (result.manageMod != null && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Selecao de mods nao aceita modo seguro/sonda/argumentos do jogo");
            if (result.storageStatus && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Inventario nao aceita modo seguro/sonda/argumentos do jogo");
            if (result.inspectLoadValidation != null && (result.safe || result.probe || !result.gameArguments.isEmpty())) throw new IllegalArgumentException("Inspecao de relatorios nao aceita modo seguro/sonda/argumentos do jogo");
            return result;
        }
        private static String value(String[] args, int index) {
            if (index >= args.length || args[index].startsWith("--")) throw new IllegalArgumentException("Valor de opcao ausente");
            return args[index];
        }
    }
}
