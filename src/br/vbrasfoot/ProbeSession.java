package br.vbrasfoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;

/** Owns diagnostic worker/subscription. Shutdown never queries legacy objects or Swing. */
final class ProbeSession implements AutoCloseable {
    private final ProbeDispatcher dispatcher;
    private final AutoCloseable subscription;
    private final Thread shutdown;
    private final Logger logger;
    private final Path report;
    private boolean closed;

    ProbeSession(Logger logger, Path report) {
        this.logger = logger; this.report = report;
        dispatcher = new ProbeDispatcher(128, Duration.ofMillis(500), observation -> logger.info("career-hook-probe=" + observation));
        AutoCloseable attached;
        try { attached = HookProbe.attach(dispatcher::offer); }
        catch (RuntimeException failure) { dispatcher.close(); throw failure; }
        subscription = attached;
        shutdown = new Thread(this::finish, "vbrasfoot-probe-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
    }
    @Override public void close() {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException closingJvm) { }
        finish();
    }
    private synchronized void finish() {
        if (closed) return;
        closed = true;
        try { subscription.close(); } catch (Exception failure) { logger.warning("Could not detach probe subscription"); }
        dispatcher.close();
        ProbeDispatcher.Stats stats = dispatcher.stats();
        logger.info("career-hook-probe-delivery=" + stats);
        Properties values = new Properties();
        values.setProperty("capacity", "128");
        values.setProperty("close.graceMillis", "500");
        values.setProperty("accepted", Long.toString(stats.accepted()));
        values.setProperty("dropped", Long.toString(stats.dropped()));
        values.setProperty("delivered", Long.toString(stats.delivered()));
        values.setProperty("failed", Long.toString(stats.failed()));
        values.setProperty("discarded", Long.toString(stats.discarded()));
        values.setProperty("workerAlive", Boolean.toString(stats.workerAlive()));
        values.setProperty("coverage", "Diagnostic best-effort delivery, not durable events or sidecar transaction");
        try (var writer = Files.newBufferedWriter(report)) { values.store(writer, "Bounded probe delivery"); }
        catch (Exception failure) { System.err.println("Falha ao registrar estatisticas da sonda: " + failure); }
    }
}
