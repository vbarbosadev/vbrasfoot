package br.vbrasfoot;

import java.io.*;
import java.security.*;
import java.util.*;

/** Hashes only bytes requested by the original decoder. Never drains, reopens, seeks or retries its stream. */
final class LoadInputTrace extends FilterInputStream {
    record Result(long bytes, String sha256, boolean closed, boolean reliable) { }
    private final MessageDigest digest;
    private final long thread = Thread.currentThread().getId();
    private long bytes;
    private boolean closed, finalized;
    private volatile boolean failed;
    private Result result;
    LoadInputTrace(InputStream input) {
        super(Objects.requireNonNull(input));
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private boolean counted(int count) {
        if (finalized || closed || Thread.currentThread().getId() != thread) failed = true;
        if (count > 0) {
            if (bytes > SaveFileObserver.MAX_SAVE_BYTES - count) { failed = true; return false; }
            bytes += count;
        }
        return !failed;
    }
    @Override public int read() throws IOException {
        try {
            int value = in.read();
            if (counted(value < 0 ? 0 : 1) && value >= 0) { try { digest.update((byte) value); } catch (Throwable diagnosticFailure) { failed = true; } }
            return value;
        }
        catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }
    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        try {
            int count = in.read(buffer, offset, length);
            if (counted(Math.max(count, 0)) && count > 0) { try { digest.update(buffer, offset, count); } catch (Throwable diagnosticFailure) { failed = true; } }
            return count;
        }
        catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }
    @Override public long skip(long count) throws IOException {
        // Preserve skip behavior rather than reading skipped bytes just to manufacture a hash.
        try { long skipped = in.skip(count); if (skipped > 0) failed = true; return skipped; }
        catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }
    @Override public void mark(int limit) { failed = true; in.mark(limit); }
    @Override public void reset() throws IOException { failed = true; in.reset(); }
    @Override public void close() throws IOException {
        if (closed || finalized || Thread.currentThread().getId() != thread) failed = true;
        try { in.close(); closed = true; }
        catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }
    Result finish() {
        if (result == null) {
            finalized = true;
            String hash = "";
            try { hash = HexFormat.of().formatHex(digest.digest()); } catch (Throwable diagnosticFailure) { failed = true; }
            result = new Result(bytes, hash, closed, closed && !failed && bytes > 0 && Thread.currentThread().getId() == thread);
        }
        if (result.reliable() && failed) result = new Result(result.bytes(), result.sha256(), result.closed(), false);
        return result;
    }
}
