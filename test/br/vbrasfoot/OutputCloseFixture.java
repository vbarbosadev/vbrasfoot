package br.vbrasfoot;

import java.io.IOException;
import java.io.OutputStream;

/** Owned synthetic code with the same close policy, not copied library bytecode. */
public final class OutputCloseFixture {
    private final OutputStream stream;
    public OutputCloseFixture(OutputStream stream) { this.stream = stream; }
    public void flush() { }
    public void close() {
        flush();
        if (stream != null) {
            try { stream.close(); }
            catch (IOException ignored) { }
        }
    }
}
