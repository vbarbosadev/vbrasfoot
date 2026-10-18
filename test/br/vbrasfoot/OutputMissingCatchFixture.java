package br.vbrasfoot;

import java.io.IOException;
import java.io.OutputStream;

/** Deliberately incompatible owned close path; must be refused before definition. */
public final class OutputMissingCatchFixture {
    private final OutputStream stream;
    public OutputMissingCatchFixture(OutputStream stream) { this.stream = stream; }
    public void flush() { }
    public void close() throws IOException { flush(); if (stream != null) stream.close(); }
}
