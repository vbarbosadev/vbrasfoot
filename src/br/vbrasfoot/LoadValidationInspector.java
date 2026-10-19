package br.vbrasfoot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;

/** Offline, bounded report reader only. Never opens game classes, writes files or grants a live context. */
final class LoadValidationInspector {
    enum Status { QUALIFIED_LOAD_OBSERVED, NOT_QUALIFIED, INCOMPLETE }
    record Report(Status status, long qualifiedLoads, long saves, boolean initializationPath, List<String> observations) {
        Report { observations = List.copyOf(observations); }
        void print(PrintStream output) {
            output.println("Carga observada: " + status);
            output.println("Cargas qualificadas=" + qualifiedLoads + "; saves=" + saves + "; ramo init=" + initializationPath);
            observations.forEach(value -> output.println("- " + value));
            output.println("Somente evidencia diagnostica encerrada; NAO e aceite humano, prontidao global ou autoridade de escrita.");
            output.println("Nenhum jogo/mod iniciado, arquivo alterado, diario reconciliado ou comando autorizado.");
        }
    }
    private static final List<String> FILES = List.of("session.properties", "career-lifecycle.properties", "decoded-save-provenance.properties",
            "original-io-interlock.properties", "creation-entry.properties", "probe-delivery.properties", "termination.txt");
    static final int MAX_REPORT_BYTES = 128 * 1024;
    private LoadValidationInspector() { }
    static Report inspect(Path session) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Inspect diagnostic reports off EDT");
        try {
            Objects.requireNonNull(session);
            if (!session.equals(session.normalize())) throw new IOException("non-normalized path");
            Path directory = session.toAbsolutePath(); CareerSidecarStore.plain(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("directory required");
            Map<String, byte[]> bytes = new LinkedHashMap<>();
            for (String file : FILES) bytes.put(file, read(directory.resolve(file)));
            Map<String, Properties> reports = new HashMap<>();
            for (String file : FILES) if (file.endsWith(".properties")) reports.put(file, properties(bytes.get(file)));
            String termination = new String(bytes.get("termination.txt"), StandardCharsets.UTF_8).trim();
            if (!Set.of("launcher-returned", "jvm-shutdown").contains(termination)) throw new IOException("unknown termination");
            var metadata = reports.get("session.properties");
            if (!Profile.ID.equals(required(metadata, "profile")) || !Profile.SHA256.equals(required(metadata, "expected.sha256"))) throw new IOException("profile mismatch");
            var lifecycle = reports.get("career-lifecycle.properties"); var decoded = reports.get("decoded-save-provenance.properties");
            var io = reports.get("original-io-interlock.properties"); var creation = reports.get("creation-entry.properties");
            var delivery = reports.get("probe-delivery.properties");
            String id = required(lifecycle, "session");
            if (!UUID.fromString(id).toString().equals(id) || !id.equals(required(decoded, "session")) || !id.equals(required(io, "session"))) throw new IOException("mixed report sessions");
            if (!required(decoded, "format").equals("1") || !required(io, "format").equals("1") || !required(creation, "format").equals("1")) throw new IOException("unknown report format");
            if (bool(decoded, "writeAuthority") || bool(io, "writeAuthority")) throw new IOException("unexpected authority assertion");
            long generation = number(lifecycle, "generation"), loads = number(lifecycle, "loads");
            long qualified = number(lifecycle, "io.existingLoads"), initialized = number(lifecycle, "read.initializedPaths");
            long saves = number(lifecycle, "saves");
            var phase = CareerLifecycle.Phase.valueOf(required(lifecycle, "phase.beforeClose"));
            var ioPhase = CareerLifecycle.Phase.valueOf(required(lifecycle, "io.phase.beforeClose"));
            if (!required(lifecycle, "phase.afterClose").equals("CLOSED") || initialized > loads || qualified > initialized) throw new IOException("inconsistent lifecycle");
            var provenanceStatus = DecodedSaveProvenance.Status.valueOf(required(decoded, "status"));
            var provenanceReason = DecodedSaveProvenance.Reason.valueOf(required(decoded, "reason"));
            boolean streamBound = provenanceStatus == DecodedSaveProvenance.Status.STREAM_BOUND;
            boolean init = streamBound && bool(decoded, "initializationPathObserved");
            if (streamBound && (provenanceReason != DecodedSaveProvenance.Reason.NONE || number(decoded, "generation") != generation
                    || !required(decoded, "bytes.sha256").matches("[a-f0-9]{64}") || number(decoded, "bytes.size") < 1
                    || number(decoded, "bytes.size") > SaveFileObserver.MAX_SAVE_BYTES)) throw new IOException("inconsistent decoder evidence");
            if (streamBound) bool(decoded, "backup");
            long admitted = number(io, "admitted"), completed = number(io, "completed"), depth = number(io, "depth.beforeClose");
            if (completed > admitted) throw new IOException("invalid interlock counters");
            long accepted = number(delivery, "accepted"), delivered = number(delivery, "delivered");
            if (delivered > accepted) throw new IOException("invalid delivery counters");
            List<String> notes = new ArrayList<>();
            if (qualified == 0 || !init || !streamBound || generation == 0 || phase != CareerLifecycle.Phase.ACTIVE || ioPhase != CareerLifecycle.Phase.ACTIVE)
                notes.add("Carga atual sem conjunto completo de markers de init, stream e lifecycle qualificado.");
            if (!bool(lifecycle, "healthy") || number(lifecycle, "inFlight.beforeClose") != 0 || number(lifecycle, "save.inFlight.beforeClose") != 0
                    || number(lifecycle, "staleCompletions") != 0 || number(lifecycle, "unboundSaves") != 0)
                notes.add("Lifecycle com trabalho pendente, interferencia ou saude insuficiente.");
            if (saves != 0 || number(lifecycle, "save.completeSequences") != 0 || number(lifecycle, "save.partialSequences") != 0
                    || number(lifecycle, "save.failedSequences") != 0 || number(lifecycle, "save.streamCloseFailures") != 0)
                notes.add("Houve tentativa de save; este roteiro e de carga sem save. Rever autosave/erros, sem retarget automatico.");
            if (admitted < loads || depth != 0 || admitted != completed || number(io, "refused") != 0)
                notes.add("Interlock incompleto ou com recusa; nao assumir que toda rotina encerrou normalmente.");
            if (number(creation, "attempts.bound") != 0 || number(creation, "attempts.unbound") != 0
                    || number(creation, "dropped") != 0 || number(creation, "rejected") != 0)
                notes.add("Criacao/interferencia observada ou diagnostico de criacao incompleto; nao e sessao delimitada de carga existente.");
            if (accepted < loads || accepted != delivered || number(delivery, "dropped") != 0 || number(delivery, "failed") != 0
                    || number(delivery, "discarded") != 0 || bool(delivery, "workerAlive"))
                notes.add("Entrega diagnostica teve perdas, falhas ou worker ainda ativo.");
            // Re-read bounded inputs rather than accepting a directory being published while inspecting it.
            for (String file : FILES) if (!Arrays.equals(bytes.get(file), read(directory.resolve(file)))) throw new IOException("reports changed while reading");
            Status status = notes.isEmpty() ? Status.QUALIFIED_LOAD_OBSERVED : Status.NOT_QUALIFIED;
            if (notes.isEmpty()) notes.add("Markers coerentes de carga existente/init/stream e exclusao encerrada, sem save observado.");
            return new Report(status, qualified, saves, init, notes);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            // Do not echo filenames/arguments or arbitrary target-authored report text.
            return new Report(Status.INCOMPLETE, 0, 0, false, List.of("Relatorios ausentes, incompatíveis, inseguros, misturados, alterados ou malformados. Use uma sessao encerrada do build atual."));
        }
    }
    private static byte[] read(Path path) throws IOException {
        CareerSidecarStore.plain(path);
        if (!Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) throw new IOException("regular report required");
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() > MAX_REPORT_BYTES) throw new IOException("report bound exceeded");
            var output = new ByteArrayOutputStream(); ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) >= 0) {
                buffer.flip(); if (output.size() > MAX_REPORT_BYTES - buffer.remaining()) throw new IOException("report grew");
                output.write(buffer.array(), 0, buffer.remaining()); buffer.clear();
            }
            return output.toByteArray();
        }
    }
    private static Properties properties(byte[] bytes) throws IOException {
        Properties result = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("duplicate report field"); return super.put(key, value);
            }
        };
        result.load(new StringReader(new String(bytes, StandardCharsets.UTF_8))); return result;
    }
    private static String required(Properties values, String key) throws IOException {
        String value = values.getProperty(key); if (value == null) throw new IOException("missing report field"); return value;
    }
    private static long number(Properties values, String key) throws IOException {
        String value = required(values, key); if (!value.matches("0|[1-9][0-9]{0,18}")) throw new IOException("invalid counter"); return Long.parseLong(value);
    }
    private static boolean bool(Properties values, String key) throws IOException {
        String value = required(values, key); if (!value.equals("true") && !value.equals("false")) throw new IOException("invalid boolean"); return Boolean.parseBoolean(value);
    }
}
