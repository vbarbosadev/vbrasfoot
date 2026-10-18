package br.vbrasfoot;

import br.vbrasfoot.api.Sdk;
import java.nio.file.Path;
import java.io.PrintStream;
import java.util.Collection;

/** Small target-free author tool. Does not prepare a game runtime or create a loader home. */
public final class SdkValidator {
    private SdkValidator() { }
    public static void main(String[] args) {
        int exit = run(args, System.out, System.err);
        if (exit != 0) System.exit(exit);
    }
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals("--describe-sdk")) {
            out.println("{\"format\":1,\"kind\":\"sdk-description\",\"sdkVersion\":" + quote(Sdk.VERSION)
                    + ",\"apiMajor\":" + Sdk.API_VERSION + ",\"gameProfile\":" + quote(Sdk.GAME_PROFILE)
                    + ",\"experimental\":true,\"permissions\":" + strings(ModManifest.KNOWN_PERMISSIONS.stream().sorted().toList())
                    + ",\"capabilities\":" + strings(ModManifest.KNOWN_CAPABILITIES.stream().sorted().toList())
                    + ",\"conditionalCapabilities\":[\"events.career.observed\",\"events.career.io\"],\"observationFlag\":\"--probe-career-hooks\""
                    + ",\"featureMinimums\":{\"query.scoped\":\"0.3.0\",\"query.player.details\":\"0.9.0\",\"query.calendar.current\":\"0.5.0\",\"query.club.balance\":\"0.5.0\",\"events.career.io\":\"0.8.0\"}"
                    + ",\"uiFeatures\":{\"ui.panel.embedded\":{\"minimumLoader\":\"0.6.0\",\"method\":\"ModContext.registerPanel\",\"placement\":\"career-window-overlay\",\"originalContentPreserved\":true}}"
                    + ",\"themeRolesMinimumLoader\":\"0.7.0\""
                    + ",\"readMethods\":{\"query.player.details\":\"GameAccess.scopedPlayerDetails\",\"query.calendar.current\":\"GameAccess.scopedCalendar\",\"query.club.balance\":\"GameAccess.scopedClubBalance\"}"
                    + ",\"unsupported\":[\"gameplay.write\",\"career.identity.persistent\",\"career.sidecars\",\"events.career.complete\",\"events.match\",\"ui.original-screen-injection\",\"hot-reload\"]"
                    + ",\"limits\":{\"queryPageMax\":500,\"clubHandles\":4096,\"subscriptionsPerMod\":4},\"sandbox\":false}");
            return 0;
        }
        if (args.length > 0 && args[0].equals("--json")) {
            if (args.length != 2 || args[1].startsWith("--")) {
                out.println(error("INVALID_ARGUMENTS", "arguments", "Use --json caminho/mod.jar", "Forneca exatamente um JAR local."));
                return 1;
            }
            String stage = "manifest";
            try {
                ModManifest manifest = ModManifest.read(Path.of(args[1]).toRealPath());
                stage = "entrypoint";
                ModValidator.validateClass(manifest);
                out.println("{\"format\":1,\"kind\":\"mod-validation\",\"valid\":true,\"sdkVersion\":" + quote(Sdk.VERSION)
                        + ",\"apiMajor\":" + Sdk.API_VERSION + ",\"gameProfile\":" + quote(Sdk.GAME_PROFILE)
                        + ",\"mod\":{\"id\":" + quote(manifest.id()) + ",\"name\":" + quote(manifest.name())
                        + ",\"version\":" + quote(manifest.version()) + ",\"mainClass\":" + quote(manifest.mainClass())
                        + ",\"permissions\":" + strings(manifest.permissions().stream().sorted().toList())
                        + ",\"dependencies\":" + strings(manifest.dependencies())
                        + ",\"optionalDependencies\":" + strings(manifest.optionalDependencies())
                        + ",\"conflicts\":" + strings(manifest.conflicts())
                        + ",\"requiredCapabilities\":" + strings(manifest.requiredCapabilities().stream().sorted().toList()) + "}"
                        + ",\"observationModeRequired\":" + (manifest.requiredCapabilities().contains("events.career.observed") || manifest.requiredCapabilities().contains("events.career.io"))
                        + ",\"codeInitialized\":false,\"scope\":\"single-jar\",\"installedSetValidated\":false,\"sandbox\":false}");
                return 0;
            } catch (Exception | LinkageError failure) {
                out.println(error(stage.equals("manifest") ? "MANIFEST_REJECTED" : "ENTRYPOINT_REJECTED", stage,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage(), stage.equals("manifest")
                                ? "Confira caminho/JAR, manifesto, permissoes, perfil e compatibilidade."
                                : "Compile contra a API publica; confira mainClass publica, concreta, Mod e construtor publico sem argumentos."));
                return 1;
            }
        }
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("vbrasfoot sdk-validator " + Sdk.VERSION + " | API " + Sdk.API_VERSION + " | " + Sdk.GAME_PROFILE);
            return 0;
        }
        if (args.length == 1 && args[0].equals("--help")) { help(out); return 0; }
        if (args.length != 1 || args[0].startsWith("--")) { help(out); return 1; }
        try { ModValidator.inspect(Path.of(args[0])); }
        catch (Exception | LinkageError failure) {
            err.println("Mod recusado: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return 1;
        }
        return 0;
    }
    private static String error(String code, String stage, String message, String remedy) {
        return "{\"format\":1,\"kind\":\"mod-validation\",\"valid\":false,\"error\":{\"code\":" + quote(code)
                + ",\"stage\":" + quote(stage) + ",\"message\":" + quote(message == null ? "" : message.substring(0, Math.min(2048, message.length())))
                + ",\"remedy\":" + quote(remedy) + "},\"codeInitialized\":false,\"scope\":\"single-jar\",\"sandbox\":false}";
    }
    static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32 || c > 126) result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
    private static String strings(Collection<String> values) {
        return values.stream().map(SdkValidator::quote).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    private static void help(PrintStream out) {
        out.println("Uso: java -jar vbrasfoot-validator.jar mod.jar | --json mod.jar | --describe-sdk | --version | --help");
        out.println("JSON format=1; exit 0 valido, 1 recusado. Erros podem conter caminhos privados; revise antes de compartilhar.");
        out.println("Valida um JAR sem inicializar mod/jogo. Nao e auditoria/sandbox nem validacao do conjunto instalado.");
    }
}
