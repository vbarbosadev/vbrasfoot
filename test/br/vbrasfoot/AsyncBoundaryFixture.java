package br.vbrasfoot;

/** Owned synthetic schedule/run bodies, no original code or game classes. */
public final class AsyncBoundaryFixture {
    public static boolean fail;
    public static int runs;
    private java.util.Timer timer;
    public static final class Task extends java.util.TimerTask { public void run() { } }
    public void oR() {
        timer = new java.util.Timer(true);
        try { timer.schedule(new Task(), 100000L); } finally { timer.cancel(); }
        if (fail) throw new IllegalStateException("owned scheduler failure");
    }
    public void progress() { if (fail) throw new IllegalStateException("owned run failure"); runs++; }
    public void run() { new java.util.Timer(true).cancel(); progress(); }
}
