package br.vbrasfoot;

import br.vbrasfoot.api.Sdk;
import java.nio.file.Path;

/** Small target-free author tool. Does not prepare a game runtime or create a loader home. */
public final class SdkValidator {
    private SdkValidator() { }
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("vbrasfoot sdk-validator " + Sdk.VERSION + " | API " + Sdk.API_VERSION + " | " + Sdk.GAME_PROFILE);
            return;
        }
        if (args.length == 1 && args[0].equals("--help")) { help(); return; }
        if (args.length != 1 || args[0].startsWith("--")) { help(); System.exit(1); return; }
        try { ModValidator.inspect(Path.of(args[0])); }
        catch (Exception | LinkageError failure) {
            System.err.println("Mod recusado: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            System.exit(1);
        }
    }
    private static void help() {
        System.out.println("Uso: java -jar vbrasfoot-validator.jar mod.jar | --version | --help");
        System.out.println("Valida um JAR sem inicializar mod/jogo. Nao e auditoria/sandbox nem validacao do conjunto instalado.");
    }
}
