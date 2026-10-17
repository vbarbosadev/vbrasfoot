package br.vbrasfoot;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

/** Bounded session-only summaries. Never retain exceptions, callbacks, classes or legacy objects. */
final class ModFailures {
    enum Stage { INITIALIZE, UI_ACTION, CAREER_CALLBACK, CAREER_OVERFLOW, SHUTDOWN }
    record Entry(long sequence, Instant time, String file, String id, Stage stage, String type, String message) { }
    record Snapshot(List<Entry> entries, long evicted) { }
    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long sequence, evicted;
    ModFailures() { this(128); }
    ModFailures(int capacity) { if (capacity < 1 || capacity > 128) throw new IllegalArgumentException(); this.capacity = capacity; }
    private static String bounded(String value, int max) {
        if (value == null) return "";
        String result = value.substring(0, Math.min(value.length(), max)).replaceAll("[\\p{Cntrl}]", " ");
        return value.length() > max ? result + "..." : result;
    }
    void record(String file, String id, Stage stage, Throwable failure) {
        for (int i = 0; i < 8 && failure instanceof java.lang.reflect.InvocationTargetException invocation && invocation.getTargetException() != null; i++) {
            failure = invocation.getTargetException();
        }
        String type = failure.getClass().getName(), message;
        try { message = failure.getMessage(); }
        catch (Throwable inaccessible) { message = "Mensagem indisponivel"; }
        record(file, id, stage, type, message);
    }
    synchronized void record(String file, String id, Stage stage, String type, String message) {
        if (entries.size() == capacity) { entries.removeFirst(); evicted++; }
        entries.addLast(new Entry(++sequence, Instant.now(), bounded(file, 160), bounded(id, 160), stage,
                bounded(type, 160), bounded(message, 512)));
    }
    synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(entries), evicted); }
}
