package br.vbrasfoot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Synthetic diagnostic files only. A positive fixture is NOT an original game or human acceptance. */
public final class LoadValidationInspectorSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void write(Path root, String file, Map<String, String> values) throws Exception {
        Properties props = new Properties(); props.putAll(values);
        try (var writer = Files.newBufferedWriter(root.resolve(file))) { props.store(writer, "owned synthetic reports"); }
    }
    private static void field(Path root, String file, String key, String value) throws Exception {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve(file))) { props.load(reader); }
        props.setProperty(key, value);
        try (var writer = Files.newBufferedWriter(root.resolve(file))) { props.store(writer, "owned synthetic mutation"); }
    }
    private static void valid(Path root) throws Exception {
        Files.createDirectories(root); String session = "11111111-1111-4111-8111-111111111111";
        write(root, "session.properties", Map.of("profile", Profile.ID, "expected.sha256", Profile.SHA256, "private.path", "DO-NOT-ECHO-PRIVATE-TEXT"));
        write(root, "career-lifecycle.properties", Map.ofEntries(Map.entry("session", session), Map.entry("generation", "4"), Map.entry("loads", "1"),
                Map.entry("saves", "0"), Map.entry("io.existingLoads", "1"), Map.entry("read.initializedPaths", "1"), Map.entry("phase.beforeClose", "ACTIVE"),
                Map.entry("phase.afterClose", "CLOSED"), Map.entry("io.phase.beforeClose", "ACTIVE"), Map.entry("healthy", "true"),
                Map.entry("inFlight.beforeClose", "0"), Map.entry("save.inFlight.beforeClose", "0"), Map.entry("staleCompletions", "0"),
                Map.entry("unboundSaves", "0"), Map.entry("save.completeSequences", "0"), Map.entry("save.partialSequences", "0"),
                Map.entry("save.failedSequences", "0"), Map.entry("save.streamCloseFailures", "0")));
        write(root, "decoded-save-provenance.properties", Map.ofEntries(Map.entry("session", session), Map.entry("format", "1"),
                Map.entry("writeAuthority", "false"), Map.entry("status", "STREAM_BOUND"), Map.entry("reason", "NONE"), Map.entry("generation", "4"),
                Map.entry("bytes.sha256", "a".repeat(64)), Map.entry("bytes.size", "10"), Map.entry("backup", "false"), Map.entry("initializationPathObserved", "true")));
        write(root, "original-io-interlock.properties", Map.of("session", session, "format", "1", "writeAuthority", "false", "admitted", "1", "completed", "1", "depth.beforeClose", "0", "refused", "0"));
        write(root, "creation-entry.properties", Map.of("format", "1", "attempts.bound", "0", "attempts.unbound", "0", "dropped", "0", "rejected", "0"));
        write(root, "probe-delivery.properties", Map.of("accepted", "1", "delivered", "1", "dropped", "0", "failed", "0", "discarded", "0", "workerAlive", "false"));
        Files.writeString(root.resolve("termination.txt"), "jvm-shutdown\n");
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        Path fixture = Files.createTempDirectory(parent, "load-validation-inspector-").toRealPath(), session = fixture.resolve("session");
        var missing = LoadValidationInspector.inspect(session);
        check(missing.status() == LoadValidationInspector.Status.INCOMPLETE && !Files.exists(session), "absent reports never create logs or directories");
        valid(session); var qualified = LoadValidationInspector.inspect(session);
        check(qualified.status() == LoadValidationInspector.Status.QUALIFIED_LOAD_OBSERVED && qualified.initializationPath() && qualified.qualifiedLoads() == 1,
                "consistent synthetic markers classified only as observed qualified load");
        var output = new ByteArrayOutputStream(); qualified.print(new PrintStream(output, true, StandardCharsets.UTF_8));
        check(!output.toString(StandardCharsets.UTF_8).contains("DO-NOT-ECHO") && output.toString(StandardCharsets.UTF_8).contains("NAO e aceite humano"), "output omits arbitrary target/private content and disclaims authority");
        field(session, "decoded-save-provenance.properties", "initializationPathObserved", "false");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "old synthetic-frame path cannot be promoted to real initialized path");
        valid(session); field(session, "career-lifecycle.properties", "saves", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "autosave or explicit save invalidates load-only scenario");
        valid(session); field(session, "career-lifecycle.properties", "save.failedSequences", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "failed swallowed writer still invalidates no-save scenario");
        valid(session); field(session, "original-io-interlock.properties", "depth.beforeClose", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "in-flight original work not declared drained at shutdown");
        valid(session); field(session, "original-io-interlock.properties", "refused", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "preflight refusal is not silently ignored");
        valid(session); field(session, "creation-entry.properties", "attempts.bound", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "creation attempt not accepted as existing-load-only run");
        valid(session); field(session, "probe-delivery.properties", "dropped", "1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.NOT_QUALIFIED, "diagnostic loss prevents positive claim");
        valid(session); field(session, "decoded-save-provenance.properties", "session", UUID.randomUUID().toString());
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "reports from different lifecycle sessions cannot be composed into proof");
        valid(session); field(session, "decoded-save-provenance.properties", "generation", "3");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "older decoder generation rejected");
        valid(session); field(session, "original-io-interlock.properties", "format", "2");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "unknown format not inferred compatible");
        valid(session); field(session, "career-lifecycle.properties", "healthy", "TRUE");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "strict booleans, no default healthy value");
        valid(session); field(session, "career-lifecycle.properties", "loads", "-1");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "negative counter rejected");
        valid(session); field(session, "career-lifecycle.properties", "loads", "9999999999999999999");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "counter overflow rejected");
        valid(session); Files.writeString(session.resolve("original-io-interlock.properties"), "refused=0\n", StandardOpenOption.APPEND);
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "duplicate required fields cannot overwrite earlier values");
        valid(session); Files.writeString(session.resolve("termination.txt"), "not-closed");
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "unknown/unfinished termination not accepted");
        valid(session); field(session, "session.properties", "expected.sha256", "b".repeat(64));
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "wrong original profile refused without starting game");
        valid(session); Files.write(session.resolve("creation-entry.properties"), new byte[LoadValidationInspector.MAX_REPORT_BYTES + 1]);
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "oversized report not read unbounded");
        valid(session); Files.delete(session.resolve("termination.txt"));
        check(LoadValidationInspector.inspect(session).status() == LoadValidationInspector.Status.INCOMPLETE, "missing completion marker not accepted as closed run");
        valid(session); var edtFailure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { LoadValidationInspector.inspect(session); } catch (Throwable expected) { edtFailure.set(expected); } });
        check(edtFailure.get() instanceof IllegalStateException, "offline filesystem inspection refused on EDT");
        check(Main.Options.parse(new String[] {"--inspect-load-validation", session.toString()}).inspectLoadValidation.equals(session), "dedicated target-free CLI parser");
        for (String extra : List.of("--safe-mode", "--probe-career-hooks", "--storage-status", "--validate-only")) {
            boolean rejected = false;
            try { Main.Options.parse(new String[] {"--inspect-load-validation", session.toString(), extra}); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "offline inspection refuses mixed execution mode " + extra);
        }
        String executable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var process = new ProcessBuilder(executable, "-jar", Path.of("dist/vbrasfoot.jar").toAbsolutePath().toString(),
                "--inspect-load-validation", session.toString(), "--home", fixture.resolve("must-not-create-home").toString(),
                "--game-dir", fixture.resolve("absent-game").toString()).directory(fixture.toFile()).redirectErrorStream(true).redirectOutput(fixture.resolve("cli.log").toFile()).start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("owned inspector process timeout"); }
        check(process.exitValue() == 0 && !Files.exists(fixture.resolve("must-not-create-home")) && !Files.exists(fixture.resolve("absent-game")), "actual inspector CLI runs without game, mods, logs, cache or home creation");
        Path ownedGame = Files.createDirectory(fixture.resolve("synthetic-game")), ownedHome = fixture.resolve("synthetic-home");
        Path syntheticSource = Files.write(ownedGame.resolve("owned.s22"), new byte[] {1, 2, 3});
        Path recorded;
        try (var diagnostics = SessionDiagnostics.open(ownedHome, ownedGame, new String[] {"synthetic-reporter-fixture"});
             var probe = new ProbeSession(diagnostics.logger(), diagnostics.directory().resolve("probe-delivery.properties"))) {
            recorded = diagnostics.directory();
            HookProbe.beforeOriginalIo("LOAD");
            try {
                HookProbe.enterLoad("owned", false); HookProbe.loadSource(syntheticSource.toString());
                var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(new byte[] {1, 2, 3})); trace.readAllBytes(); trace.close();
                HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(new Object(), new Object());
                HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            } finally { HookProbe.afterOriginalIo(); }
        }
        check(LoadValidationInspector.inspect(recorded).status() == LoadValidationInspector.Status.QUALIFIED_LOAD_OBSERVED,
                "actual report writers and offline reader agree on correlated synthetic markers; not original init evidence");
        check(LoadValidationInspector.inspect(session.resolve("child/..")).status() == LoadValidationInspector.Status.INCOMPLETE,
                "non-normalized report directory not silently reinterpreted");
        if (System.getProperty("os.name").startsWith("Windows") && fixture.toString().matches("[A-Za-z]:\\\\[A-Za-z0-9 _\\\\.-]+")) {
            Path junction = fixture.resolve("session-junction");
            var link = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", junction.toString(), session.toString()).redirectErrorStream(true).start();
            if (!link.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { link.destroyForcibly(); throw new AssertionError("owned junction timeout"); }
            link.getInputStream().readAllBytes();
            if (link.exitValue() == 0) check(LoadValidationInspector.inspect(junction).status() == LoadValidationInspector.Status.INCOMPLETE, "junction report directory refused without following its private contents");
            else System.out.println("SKIP owned junction unavailable; not accepted as passed.");
        }
        System.out.println("PASS: " + checks + " offline load validation checks; owned fixture " + fixture + "; no original initialized-load or human acceptance claimed.");
    }
}
