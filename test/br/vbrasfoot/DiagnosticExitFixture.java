package br.vbrasfoot;

import java.nio.file.Path;

/** Separate JVM: exercises System.exit without terminating the test runner. */
public final class DiagnosticExitFixture {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]);
        try (var diagnostic = SessionDiagnostics.open(home, home, args)) {
            System.err.println("child-error");
            if (args[1].equals("exit")) System.exit(0);
            if (args[1].equals("exit-edt")) {
                javax.swing.SwingUtilities.invokeLater(() -> System.exit(0));
                new java.util.concurrent.CountDownLatch(1).await();
            }
        }
    }
}
