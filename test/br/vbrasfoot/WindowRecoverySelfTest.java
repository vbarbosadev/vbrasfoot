package br.vbrasfoot;

import java.awt.*;

public final class WindowRecoverySelfTest {
    public static void main(String[] args) {
        if (!WindowRecovery.usable(new Rectangle(0, 0, 1920, 1080), new Insets(0, 0, 48, 0))
                .equals(new Rectangle(0, 0, 1920, 1032))) throw new AssertionError("taskbar area");
        if (!WindowRecovery.usable(new Rectangle(-1920, -200, 1920, 1080), new Insets(20, 40, 30, 10))
                .equals(new Rectangle(-1880, -180, 1870, 1030))) throw new AssertionError("negative monitor origin");
        if (!WindowRecovery.usable(new Rectangle(1920, 0, 1280, 720), new Insets(0, 0, 0, 0))
                .equals(new Rectangle(1920, 0, 1280, 720))) throw new AssertionError("secondary monitor");
        for (Insets invalid : new Insets[] {new Insets(-1, 0, 0, 0), new Insets(0, 0, 1080, 0)}) {
            boolean rejected = false;
            try { WindowRecovery.usable(new Rectangle(0, 0, 1920, 1080), invalid); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("invalid insets");
        }
        boolean rejected = false;
        try { WindowRecovery.restore(null, java.util.logging.Logger.getAnonymousLogger()); }
        catch (IllegalStateException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("non EDT refused before access");
        System.out.println("PASS: 6 window recovery geometry/thread checks; synthetic, not original UI fix proof.");
    }
}
