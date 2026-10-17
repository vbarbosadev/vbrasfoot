package br.vbrasfoot;

import java.util.ArrayList;
import java.util.List;

public final class HookProbeSelfTest {
    private static int checks;
    private static void check(boolean condition) { if (!condition) throw new AssertionError("hook probe check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        List<HookProbe.Observation> events = new ArrayList<>();
        try (var subscription = HookProbe.attach(events::add)) {
            HookProbe.enterLoad("career", false); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
            check(events.size() == 1 && events.get(0).completed() && !events.get(0).backup());
            events.clear();
            HookProbe.enterLoad("fallback", false);
            HookProbe.enterLoad("fallback", true); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
            check(events.isEmpty());
            HookProbe.exitLoad(false);
            check(events.size() == 1 && events.get(0).completed() && events.get(0).backup() && events.get(0).fallback() && !events.get(0).returnedSuccess());
            events.clear();
            HookProbe.enterLoad(null, false); HookProbe.exitLoad(true);
            check(events.size() == 1 && !events.get(0).completed());
            HookProbe.enterLoad("error", false); HookProbe.decodedLoad(); HookProbe.abortLoad(new IllegalStateException());
            check(events.get(1).aborted() && !events.get(1).completed());
            HookProbe.enterLoad("after-error", false); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
            check(events.get(2).completed());
            events.clear();
            HookProbe.enterSave("save"); HookProbe.primaryClosed(); HookProbe.backupCopied(); HookProbe.exitSave();
            check(events.size() == 1 && events.get(0).completed() && events.get(0).backup());
            HookProbe.enterSave("failed"); HookProbe.exitSave();
            check(!events.get(1).completed() && !events.get(1).backup());
            HookProbe.enterSave("backup-failed"); HookProbe.primaryClosed(); HookProbe.exitSave();
            check(events.get(2).completed() && !events.get(2).backup());
            HookProbe.enterSave("aborted"); HookProbe.abortSave(new RuntimeException());
            check(events.get(3).aborted() && !events.get(3).completed());
            check(events.get(3).sequence() > events.get(2).sequence());
            HookProbe.rootsReset();
            check(events.get(4).operation().equals("roots-reset") && events.get(4).file() == null);
        }
        try (var subscription = HookProbe.attach(event -> { throw new AssertionError("broken observer"); })) {
            HookProbe.enterSave("isolated"); HookProbe.primaryClosed(); HookProbe.exitSave();
            HookProbe.enterLoad("isolated", false); HookProbe.decodedLoad(); HookProbe.exitLoad(true);
            check(true);
        }
        System.out.println("PASS: " + checks + " hook observation tests (synthetic; no game events).");
    }
}
