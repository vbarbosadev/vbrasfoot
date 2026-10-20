package br.vbrasfoot;

import java.io.*;
import java.security.*;
import java.util.*;

/** Counts bytes successfully delegated by the original writer. No extra flush, close, seek, retry or file open. */
final class SaveOutputTrace extends FilterOutputStream {
    record Result(long bytes, String sha256, boolean closed, boolean reliable) { }
    private final MessageDigest digest;
    private final long thread = Thread.currentThread().getId();
    private long bytes;
    private boolean closed, finalized;
    private volatile boolean failed;
    private Result result;
    SaveOutputTrace(OutputStream output) {
        super(Objects.requireNonNull(output));
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private boolean counted(int count) {
        if (closed || finalized || Thread.currentThread().getId() != thread) failed = true;
        if (count > 0) {
            if (bytes > SaveFileObserver.MAX_SAVE_BYTES - count) { failed = true; return false; }
            bytes += count;
        }
        return !failed;
    }
    @Override public void write(int value) throws IOException {
        try {
            out.write(value);
            if (counted(1)) try { digest.update((byte) value); } catch (Throwable diagnosticFailure) { failed = true; }
        } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
    }
    @Override public void write(byte[] buffer, int offset, int length) throws IOException {
        try {
            out.write(buffer, offset, length); // Do not use FilterOutputStream's per-byte implementation.
            if (counted(length)) try { digest.update(buffer, offset, length); } catch (Throwable diagnosticFailure) { failed = true; }
        } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
    }
    @Override public void flush() throws IOException {
        if (closed || finalized || Thread.currentThread().getId() != thread) failed = true;
        try { out.flush(); } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
    }
    @Override public void close() throws IOException {
        if (closed || finalized || Thread.currentThread().getId() != thread) failed = true;
        try { out.close(); closed = true; } catch (IOException | RuntimeException | Error failure) { failed = true; throw failure; }
    }
    Result finish() {
        if (result == null) {
            finalized = true; String hash = "";
            try { hash = HexFormat.of().formatHex(digest.digest()); } catch (Throwable diagnosticFailure) { failed = true; }
            result = new Result(bytes, hash, closed, closed && !failed && bytes > 0 && Thread.currentThread().getId() == thread);
        }
        if (result.reliable() && failed) result = new Result(result.bytes(), result.sha256(), result.closed(), false);
        return result;
    }
}
