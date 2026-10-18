package br.vbrasfoot;

public final class CreationEntryTrackerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void prepare(CreationEntryTracker tracker, Object panel, Object primary, Object secondary, long generation) {
        tracker.preparation("ENTER", null, primary, secondary, generation);
        tracker.preparation("SETTINGS_RETURN", null, primary, secondary, generation);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, generation);
        tracker.preparation("PANEL_RETURN", null, primary, secondary, generation);
        tracker.preparation("RETURN", null, primary, secondary, generation);
    }
    private static CreationEntryTracker.Event last(CreationEntryTracker tracker) {
        var events = tracker.capture().events(); return events.get(events.size() - 1);
    }
    public static void main(String[] args) throws Exception {
        var tracker = new CreationEntryTracker(); Object panel = new Object(), primary = new Object(), secondary = new Object();
        tracker.reset(); prepare(tracker, panel, primary, secondary, 4);
        check(last(tracker).bound() && last(tracker).rootsMatched(), "normal preparation creates binding");
        long entry = last(tracker).entry(); var snapshot = tracker.capture();
        tracker.attempt(panel, primary, secondary, 4);
        check(last(tracker).bound() && last(tracker).entry() == entry && last(tracker).attempt() != 0, "same panel/roots/generation across callbacks");
        tracker.dialog("pl", "DIALOG_RETURN", 1, primary, secondary, 4);
        check(last(tracker).resultKnown() && last(tracker).result() == 1 && last(tracker).entry() == entry, "raw dialog joins matched attempt");
        tracker.dialog("AB", "CANDIDATE_ENTER", 0, primary, secondary, 4);
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 4);
        check(last(tracker).bound() && last(tracker).stage().equals("oq_RETURN") && snapshot.events().size() == 5, "normal attempt diagnostic, detached capture");
        tracker.attempt(new Object(), primary, secondary, 4); check(!last(tracker).bound(), "wrong panel never binds");
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 4);
        tracker.attempt(panel, new Object(), secondary, 4); check(!last(tracker).bound(), "wrong primary never binds");
        tracker.dialog("oq", "FAILED", 0, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 5); check(!last(tracker).bound(), "changed generation never binds");
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 5);
        tracker.attempt(panel, primary, secondary, 4);
        tracker.dialog("pl", "DIALOG_RETURN", -1, primary, secondary, 5);
        check(!last(tracker).bound() && !last(tracker).rootsMatched(), "generation changes during callback explicitly unmatched");
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 5);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 4); check(!last(tracker).bound(), "outside reassignment revokes earlier binding");
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 4);
        for (String missing : new String[] {"SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN"}) {
            tracker.reset(); tracker.preparation("ENTER", null, primary, secondary, 4);
            for (String stage : new String[] {"SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN", "RETURN"})
                if (!stage.equals(missing)) tracker.preparation(stage, stage.equals("PANEL_ASSIGNED") ? panel : null, primary, secondary, 4);
            tracker.attempt(panel, primary, secondary, 4); check(!last(tracker).bound(), "missing stage refuses " + missing);
        }
        tracker.reset(); tracker.preparation("ENTER", null, primary, secondary, 4);
        tracker.preparation("SETTINGS_RETURN", null, primary, secondary, 4);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4);
        tracker.preparation("FAILED", null, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 4); check(!last(tracker).bound(), "failure after assignment never prepares panel");
        tracker.reset(); prepare(tracker, panel, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 4); tracker.attempt(panel, primary, secondary, 4);
        check(!last(tracker).bound(), "nested attempt unbound");
        tracker.dialog("oq", "RETURN", 0, primary, secondary, 4); tracker.dialog("oq", "RETURN", 0, primary, secondary, 4);
        check(!last(tracker).bound(), "nested attempt invalidates outer binding too");
        tracker.reset();
        Object[] panels = new Object[65];
        for (int i = 0; i < panels.length; i++) { panels[i] = new Object(); prepare(tracker, panels[i], primary, secondary, 4); }
        check(!last(tracker).bound() && tracker.capture().rejected() == 1, "64 live weak bindings, capacity refuses without evicting live binding");
        tracker.attempt(panels[0], primary, secondary, 4); check(last(tracker).bound(), "earlier live binding retained");
        tracker.reset();
        for (int i = 0; i < 300; i++) { tracker.attempt(panel, primary, secondary, 4); tracker.dialog("oq", "RETURN", 0, primary, secondary, 4); }
        check(tracker.capture().events().size() == 512 && tracker.capture().dropped() == 88, "512 record ring reports evictions");
        tracker.reset(); prepare(tracker, panel, primary, secondary, 4);
        Thread callback = new Thread(() -> { tracker.attempt(panel, primary, secondary, 4); tracker.dialog("oq", "RETURN", 0, primary, secondary, 4); });
        callback.start(); callback.join(); check(last(tracker).bound(), "explicit panel binding crosses callbacks without inheriting threadlocals");
        tracker.reset(); tracker.attempt(panel, primary, secondary, 4); check(!last(tracker).bound(), "reset revokes bindings");
        tracker.reset(); tracker.preparation("ENTER", null, primary, secondary, 4);
        tracker.preparation("SETTINGS_RETURN", null, primary, secondary, 4);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4);
        tracker.preparation("PANEL_RETURN", null, primary, secondary, 4);
        tracker.preparation("RETURN", null, primary, secondary, 5);
        tracker.attempt(panel, primary, secondary, 5); check(!last(tracker).bound(), "changed generation during preparation refuses binding");
        tracker.reset(); tracker.preparation("ENTER", null, primary, secondary, 4);
        prepare(tracker, new Object(), primary, secondary, 4);
        tracker.preparation("SETTINGS_RETURN", null, primary, secondary, 4);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4);
        tracker.preparation("PANEL_RETURN", null, primary, secondary, 4);
        tracker.preparation("RETURN", null, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 4); check(!last(tracker).bound(), "reentrant preparation invalidates parent");
        tracker.reset(); for (int i = 0; i < 9; i++) tracker.preparation("ENTER", null, primary, secondary, 4);
        tracker.preparation("SETTINGS_RETURN", null, primary, secondary, 4);
        tracker.preparation("PANEL_ASSIGNED", panel, primary, secondary, 4);
        tracker.preparation("PANEL_RETURN", null, primary, secondary, 4);
        tracker.preparation("RETURN", null, primary, secondary, 4);
        tracker.attempt(panel, primary, secondary, 4);
        check(!last(tracker).bound() && tracker.capture().rejected() == 1, "preparation overflow cannot restart as qualified binding");
        tracker.reset(); prepare(tracker, panel, primary, secondary, 4);
        for (int i = 0; i < 9; i++) tracker.attempt(panel, primary, secondary, 4);
        check(!last(tracker).bound() && tracker.capture().rejected() == 1, "attempt overflow not bound");
        tracker.reset();
        var entered = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        Thread previousEpoch = new Thread(() -> {
            tracker.preparation("ENTER", null, primary, secondary, 4); entered.countDown();
            try { if (!resume.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("test resume timeout"); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            tracker.preparation("RETURN", null, primary, secondary, 4);
        });
        previousEpoch.start();
        check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "old epoch entered"); tracker.reset(); resume.countDown(); previousEpoch.join(6000);
        check(!previousEpoch.isAlive() && tracker.capture().events().isEmpty() && tracker.capture().outside() == 1, "reset discards previous-thread preparation");
        var guard = new CareerLifecycle();
        try (var probe = HookProbe.attach(event -> { }); var lifecycle = HookProbe.attachLifecycle(guard)) {
            var before = guard.snapshot(); HookProbe.creationEntry("ENTER", null, primary, secondary);
            HookProbe.creationEntry("SETTINGS_RETURN", null, primary, secondary); HookProbe.creationEntry("PANEL_ASSIGNED", panel, primary, secondary);
            HookProbe.creationEntry("PANEL_RETURN", null, primary, secondary); HookProbe.creationEntry("RETURN", null, primary, secondary);
            HookProbe.creationEntryAttempt(panel, primary, secondary); HookProbe.creationDialog("pl", "DIALOG_RETURN", 1, primary, secondary);
            HookProbe.creationDialog("oq", "RETURN", 0, primary, secondary);
            check(before.equals(guard.snapshot()) && HookProbe.creationEntries().boundAttempts() == 1, "real bridge leaves guard unchanged");
        }
        System.out.println("PASS: " + checks + " weak creation entry tracker checks; no readiness promotion.");
    }
}
