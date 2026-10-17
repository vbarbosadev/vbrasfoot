package br.vbrasfoot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Allowlisted summary only: never opens session files, logs, saves, JARs or network connections. */
final class DiagnosticExport {
    static final int MAX_BYTES = 256 * 1024;
    enum Os { WINDOWS, LINUX, MACOS, OTHER }
    enum Arch { X86_64, X86, ARM64, OTHER }
    record Environment(int feature, int interim, int update, int patch, Os os, Arch arch, boolean safe, boolean probe) {
        static Environment current(boolean safe, boolean probe) {
            var java = Runtime.version();
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            return new Environment(java.feature(), java.interim(), java.update(), java.patch(),
                    os.startsWith("windows") ? Os.WINDOWS : os.equals("linux") ? Os.LINUX : os.startsWith("mac") ? Os.MACOS : Os.OTHER,
                    switch (arch) { case "amd64", "x86_64" -> Arch.X86_64; case "x86", "i386", "i686" -> Arch.X86; case "aarch64", "arm64" -> Arch.ARM64; default -> Arch.OTHER; }, safe, probe);
        }
    }
    record ModInfo(String file, String id, String version, List<String> permissions, String current,
                   boolean enabled, boolean valid, String validationError) {
        ModInfo { permissions = List.copyOf(permissions); validationError = Objects.requireNonNullElse(validationError, ""); }
    }
    record Snapshot(Environment environment, List<ModInfo> mods, List<ModFailures.Entry> failures, long evicted) {
        Snapshot {
            mods = List.copyOf(mods); failures = List.copyOf(failures);
            if (mods.size() > 512 || failures.size() > 128) throw new IllegalArgumentException("Snapshot diagnostico excede limites");
        }
    }
    record Options(boolean modIdentity, boolean failureDetails) { }
    static Snapshot capture(List<ModSelection.Entry> inventory, Map<String, br.vbrasfoot.api.ModState> states,
                            ModFailures.Snapshot failures, Environment environment) {
        List<ModInfo> mods = new ArrayList<>();
        for (var entry : inventory) {
            ModManifest manifest = entry.manifest();
            mods.add(new ModInfo(entry.file(), manifest == null ? "" : manifest.id(), manifest == null ? "" : manifest.version(),
                    manifest == null ? List.of() : manifest.permissions().stream().sorted().toList(),
                    states.containsKey(entry.file()) ? states.get(entry.file()).name() : "NOT_LOADED", entry.enabled(), manifest != null, entry.error()));
        }
        mods.sort(Comparator.comparing(ModInfo::file));
        return new Snapshot(environment, mods, failures.entries(), failures.evicted());
    }
    private static String quoted(String value, int max) {
        if (value == null) value = "";
        StringBuilder text = new StringBuilder("\"");
        for (int i = 0; i < Math.min(value.length(), max); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') text.append('\\').append(c);
            else if (Character.isISOControl(c) || Character.isSurrogate(c) || Character.getType(c) == Character.FORMAT
                    || c == '\u2028' || c == '\u2029') {
                text.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) text.append("0123456789abcdef".charAt((c >>> shift) & 15));
            }
            else text.append(c);
        }
        if (value.length() > max) text.append("...");
        return text.append('"').toString();
    }
    static String preview(Snapshot snapshot, Options options) throws IOException {
        Environment env = snapshot.environment();
        StringBuilder text = new StringBuilder("vbrasfoot diagnostic summary\nformat=1\n");
        text.append("loader.version=").append(br.vbrasfoot.api.Sdk.VERSION).append("\napi.major=").append(br.vbrasfoot.api.Sdk.API_VERSION)
                .append("\nprofile.expected=").append(Profile.ID).append("\nprofile.expectedSha256=").append(Profile.SHA256)
                .append("\njava.version=").append(env.feature()).append('.').append(env.interim()).append('.').append(env.update()).append('.').append(env.patch())
                .append("\nos.family=").append(env.os()).append("\nos.arch=").append(env.arch())
                .append("\nmode.safe=").append(env.safe()).append("\nmode.probe=").append(env.probe())
                .append("\ninclude.modIdentity=").append(options.modIdentity()).append("\ninclude.failureDetails=").append(options.failureDetails())
                .append("\nmods.count=").append(snapshot.mods().size()).append("\nfailures.retained=").append(snapshot.failures().size())
                .append("\nfailures.evicted=").append(snapshot.evicted()).append('\n');
        Map<String, String> aliases = new LinkedHashMap<>();
        int index = 0;
        for (ModInfo mod : snapshot.mods()) {
            String alias = "mod-" + (++index); aliases.put(mod.file(), alias);
            text.append('\n').append(alias).append(".state=").append(mod.current()).append('\n')
                    .append(alias).append(".nextEnabled=").append(mod.enabled()).append('\n')
                    .append(alias).append(".manifestValid=").append(mod.valid()).append('\n');
            if (options.modIdentity()) {
                text.append(alias).append(".file=").append(quoted(mod.file(), 160)).append('\n')
                        .append(alias).append(".id=").append(quoted(mod.id(), 160)).append('\n')
                        .append(alias).append(".version=").append(quoted(mod.version(), 128)).append('\n');
                text.append(alias).append(".permissions=");
                for (String permission : mod.permissions().stream().limit(16).toList()) text.append(quoted(permission, 64)).append(' ');
                text.append('\n');
            }
            if (options.failureDetails() && !mod.validationError().isEmpty()) text.append(alias).append(".validationError=").append(quoted(mod.validationError(), 512)).append('\n');
        }
        text.append('\n');
        for (ModFailures.Stage stage : ModFailures.Stage.values()) text.append("failures.").append(stage).append('=')
                .append(snapshot.failures().stream().filter(f -> f.stage() == stage).count()).append('\n');
        if (options.failureDetails()) {
            int retired = 0;
            for (var failure : snapshot.failures()) {
                if (!aliases.containsKey(failure.file())) aliases.put(failure.file(), "unlisted-mod-" + (++retired));
                String alias = aliases.get(failure.file());
                text.append("\nfailure.sequence=").append(failure.sequence()).append("\nfailure.mod=").append(alias)
                        .append("\nfailure.stage=").append(failure.stage()).append("\nfailure.time=").append(failure.time()).append('\n');
                if (options.modIdentity()) text.append("failure.file=").append(quoted(failure.file(), 160))
                        .append("\nfailure.id=").append(quoted(failure.id(), 160)).append('\n');
                text.append("failure.type=").append(quoted(failure.type(), 160)).append("\nfailure.message=").append(quoted(failure.message(), 512)).append('\n');
            }
        }
        text.append("\ncoverage=Session summary; not exhaustive, durable or transactional.\n")
                .append("excluded=Raw logs, stack traces, session files, command lines, environment variables, dedicated path fields, saves, binaries, mod data and config contents.\n")
                .append("privacy=Optional identity/details or manual edits may contain private data. Review before sharing. No automatic sending.\n");
        String result = text.toString(); encode(result); return result;
    }
    private static ByteBuffer encode(String text) throws IOException {
        if (text.length() > MAX_BYTES) throw new IOException("Relatorio excede 256 KiB; remova detalhes antes de salvar");
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            if (bytes.remaining() > MAX_BYTES) throw new IOException("Relatorio UTF-8 excede 256 KiB");
            return bytes;
        } catch (CharacterCodingException failure) { throw new IOException("Texto contem Unicode invalido; revise a previa", failure); }
    }
    static Path saveReviewed(Path home, String reviewed) throws IOException { return saveReviewed(home, reviewed, UUID.randomUUID()); }
    static Path saveReviewed(Path home, String reviewed, UUID id) throws IOException {
        Objects.requireNonNull(id);
        ByteBuffer bytes = encode(reviewed); // Refuse before the first filesystem write.
        Path root = home.toAbsolutePath().normalize();
        InstallPaths.rejectRedirects(root);
        if (!Files.isDirectory(root)) throw new IOException("Instalacao inexistente para exportacao");
        Path directory = root.resolve("diagnostics"); InstallPaths.rejectRedirects(directory);
        try { Files.createDirectory(directory); } catch (FileAlreadyExistsException exists) { /* Validate below. */ }
        InstallPaths.rejectRedirects(directory);
        if (!Files.isDirectory(directory)) throw new IOException("Destino de diagnostico nao e diretorio");
        Path target = directory.resolve("diagnostic-" + id + ".txt");
        InstallPaths.rejectRedirects(target);
        boolean created = false, complete = false;
        try {
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            complete = true; return target;
        } finally { if (created && !complete) Files.deleteIfExists(target); }
    }
}
