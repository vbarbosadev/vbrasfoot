package br.vbrasfoot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Session-owned capture. The shutdown hook never calls mods, Swing or the game. */
final class SessionDiagnostics implements AutoCloseable {
    private static final long CAPTURE_LIMIT = 8L * 1024 * 1024;
    private final PrintStream originalOut = System.out, originalErr = System.err;
    private final PrintStream out, err;
    private final FileHandler handler;
    private final Logger logger;
    private final boolean parentHandlers;
    private final Thread hook;
    private final Path directory;
    private boolean closed;

    static SessionDiagnostics open(Path home, Path game, String[] args) throws IOException {
        Path logs = home.resolve("logs");
        Files.createDirectories(logs);
        Path directory = Files.createDirectory(logs.resolve("session-" + UUID.randomUUID()));
        Properties metadata = new Properties();
        metadata.setProperty("started", java.time.Instant.now().toString());
        metadata.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        metadata.setProperty("java.version", System.getProperty("java.version"));
        metadata.setProperty("java.vendor", System.getProperty("java.vendor"));
        metadata.setProperty("java.home", System.getProperty("java.home"));
        metadata.setProperty("jvm.arguments", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
        metadata.setProperty("launcher.arguments", java.util.Arrays.toString(args));
        metadata.setProperty("gameRoot", game.toString());
        metadata.setProperty("home", home.toString());
        metadata.setProperty("profile", Profile.ID);
        metadata.setProperty("expected.sha256", Profile.SHA256);
        metadata.setProperty("capture.limit.bytes", Long.toString(CAPTURE_LIMIT));
        metadata.setProperty("privacy", "Local paths and arguments; review before sharing. No saves included.");
        try (var writer = Files.newBufferedWriter(directory.resolve("session.properties"))) {
            metadata.store(writer, "vbrasfoot session diagnostic");
        }
        return new SessionDiagnostics(directory);
    }

    private SessionDiagnostics(Path directory) throws IOException {
        this.directory = directory;
        handler = new FileHandler(directory.resolve("loader-%g.log").toString(), 1024 * 1024, 3, false);
        handler.setFormatter(new SimpleFormatter());
        PrintStream newOut;
        try {
            newOut = new PrintStream(new Capture(originalOut, Files.newOutputStream(directory.resolve("stdout.log")), CAPTURE_LIMIT), true, StandardCharsets.UTF_8);
            try {
                err = new PrintStream(new Capture(originalErr, Files.newOutputStream(directory.resolve("stderr.log")), CAPTURE_LIMIT), true, StandardCharsets.UTF_8);
            } catch (IOException failure) { newOut.close(); throw failure; }
        } catch (IOException failure) { handler.close(); throw failure; }
        out = newOut;
        logger = Logger.getLogger("vbrasfoot");
        parentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        hook = new Thread(() -> finish("jvm-shutdown"), "vbrasfoot-diagnostic-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        System.setOut(out);
        System.setErr(err);
        logger.info("stage=diagnostic-ready directory=" + directory);
        originalOut.println("Diagnostico da sessao: " + directory);
    }

    Logger logger() { return logger; }
    Path directory() { return directory; }

    @Override public void close() {
        try { Runtime.getRuntime().removeShutdownHook(hook); }
        catch (IllegalStateException shuttingDown) { /* Hook may already be running. */ }
        finish("launcher-returned");
    }

    private synchronized void finish(String reason) {
        if (closed) return;
        closed = true;
        // LogManager has its own concurrent JVM hook and may already have closed handlers.
        try { Files.writeString(directory.resolve("termination.txt"), reason + "\n"); }
        catch (IOException failure) { originalErr.println("Falha ao registrar encerramento: " + failure); }
        logger.info("stage=diagnostic-close reason=" + reason);
        logger.removeHandler(handler);
        handler.close();
        logger.setUseParentHandlers(parentHandlers);
        System.setOut(originalOut);
        System.setErr(originalErr);
        out.close();
        err.close();
    }

    /** Bounded disk output, full console output; never closes the caller's console. */
    static final class Capture extends OutputStream {
        private final OutputStream console, file;
        private final long limit;
        private long written;
        private boolean truncated, closed;
        Capture(OutputStream console, OutputStream file, long limit) {
            this.console = console; this.file = file; this.limit = limit;
        }
        @Override public synchronized void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }
        @Override public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            if (closed) return;
            console.write(bytes, offset, length);
            int retained = (int) Math.min(length, limit - written);
            if (retained > 0) { file.write(bytes, offset, retained); written += retained; }
            if (retained < length && !truncated) {
                truncated = true;
                file.write("\n[vbrasfoot: capture limit reached; remaining output only on console]\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        @Override public synchronized void flush() throws IOException {
            if (!closed) { console.flush(); file.flush(); }
        }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try { console.flush(); } finally { file.close(); }
        }
    }
}
