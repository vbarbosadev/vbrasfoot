package br.vbrasfoot;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Owned child JVM only, never a user game process. */
public final class ProbeShutdownFixture {
    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]).resolve("delivery.properties");
        try (var session = new ProbeSession(Logger.getLogger("probe-shutdown-fixture"), report)) {
            HookProbe.enterSave("fixture"); HookProbe.primaryClosed(); HookProbe.exitSave();
            javax.swing.SwingUtilities.invokeLater(() -> System.exit(0));
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }
}
