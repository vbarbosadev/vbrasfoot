package br.vbrasfoot;

import java.util.*;

public final class CreationPathQualificationSelfTest {
    record Signal(String kind, String method, String stage, int value, boolean receiver) { }
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static Signal dialog(String method, String stage, int value) { return new Signal("dialog", method, stage, value, false); }
    private static Signal candidate(String stage) { return new Signal("candidate", "a.bV.AB", stage, 0, false); }
    private static Signal step(String stage) { return new Signal("step", "a.bV.AB", stage, 0, false); }
    private static Signal progress(String method, String stage, int value) { return new Signal("progress", method, stage, value, true); }
    private static void recursion(List<Signal> signals, int remaining) {
        signals.add(progress("ap", "ENTER", -1));
        if (remaining > 0) {
            signals.add(progress("at", "ENTER", -1)); signals.add(progress("ax", "ENTER", -1)); recursion(signals, remaining - 1);
            signals.add(progress("ax", "RETURN", -1)); signals.add(progress("at", "RETURN", -1));
        } else { signals.add(progress("ap", "PANEL_REQUEST", 2)); signals.add(progress("ap", "PANEL_RETURN", -1)); }
        signals.add(progress("ap", "RETURN", -1));
    }
    static List<Signal> path(int recursion) {
        var signals = new ArrayList<Signal>(List.of(dialog("oq", "ENTER", 0), dialog("pl", "ENTER", 0), dialog("pl", "DIALOG_RETURN", 1),
                candidate("ENTER"), step("before-az"), step("after-az"), step("before-V"), progress("V", "ENTER", -1)));
        recursion(signals, recursion); signals.addAll(List.of(progress("V", "RETURN", -1), step("after-V"), candidate("RETURN"), dialog("pl", "RETURN", 0), dialog("oq", "RETURN", 0)));
        return signals;
    }
    static void accept(CreationPathQualification path, Signal signal) {
        switch (signal.kind()) {
            case "dialog" -> path.dialog(signal.method(), signal.stage(), signal.value());
            case "candidate" -> path.candidate(signal.method(), signal.stage());
            case "step" -> path.step(signal.method(), signal.stage());
            case "progress" -> path.progress(signal.method(), signal.stage(), signal.value(), signal.receiver());
            default -> throw new AssertionError("unknown fixture signal");
        }
    }
    private static CreationPathQualification run(List<Signal> signals) { var result = new CreationPathQualification(); signals.forEach(s -> accept(result, s)); return result; }
    public static void main(String[] args) {
        var signals = path(12); var passed = run(signals).snapshot();
        check(passed.status() == CreationPathQualification.Status.PATH_RETURNED && passed.reason() == CreationPathQualification.Reason.NONE, "owned observed-shape sequence recognized, not game readiness");
        check(passed.signals() == 89 && passed.methodEntries() == 38 && passed.maxDepth() == 38 && passed.panelReturns() == 1, "shared online sequence includes all38entries/78progress signals");
        for (int i = 0; i < signals.size(); i++) {
            var incomplete = new ArrayList<>(signals); incomplete.remove(i);
            check(run(incomplete).snapshot().status() != CreationPathQualification.Status.PATH_RETURNED, "missing signal " + i);
        }
        for (int i = 0; i < signals.size() - 1; i++) {
            var duplicated = new ArrayList<>(signals); duplicated.add(i, signals.get(i));
            check(run(duplicated).snapshot().status() != CreationPathQualification.Status.PATH_RETURNED, "duplicate signal " + i);
        }
        for (int choice : new int[] {0, 2, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var changed = new ArrayList<>(signals); changed.set(2, dialog("pl", "DIALOG_RETURN", choice));
            check(run(changed).snapshot().reason() == CreationPathQualification.Reason.OTHER_CHOICE, "No only, not Yes/cancel/close " + choice);
        }
        for (int i = 0; i < signals.size() - 1; i++) {
            var failure = new ArrayList<>(signals); failure.add(i, new Signal("candidate", "a.bV.AB", "FAILED", 0, false));
            check(run(failure).snapshot().reason() == CreationPathQualification.Reason.ORIGINAL_FAILURE, "original failure never returns path " + i);
        }
        var wrongReceiver = new ArrayList<>(signals); wrongReceiver.set(7, new Signal("progress", "V", "ENTER", -1, false));
        check(run(wrongReceiver).snapshot().reason() == CreationPathQualification.Reason.WRONG_RECEIVER, "unknown/wrong receiver refused");
        var season = new ArrayList<>(signals); season.set(8, progress("aa", "ENTER", -1));
        check(run(season).snapshot().reason() == CreationPathQualification.Reason.OTHER_PROGRESSION, "aa path not treated as new career");
        for (String label : List.of("a.cR.AC", "a.cR.pn")) {
            var changed = new ArrayList<>(signals); changed.set(3, new Signal("candidate", label, "ENTER", 0, false));
            check(run(changed).snapshot().reason() == CreationPathQualification.Reason.OTHER_CANDIDATE, "shared candidate refused " + label);
        }
        var wrongPanel = new ArrayList<>(signals);
        int panelIndex = java.util.stream.IntStream.range(0, signals.size()).filter(i -> signals.get(i).stage().equals("PANEL_REQUEST")).findFirst().orElseThrow();
        wrongPanel.set(panelIndex, progress("ap", "PANEL_REQUEST", 5));
        check(run(wrongPanel).snapshot().reason() == CreationPathQualification.Reason.OTHER_PANEL, "other panel rejected");
        check(run(path(22)).snapshot().reason() == CreationPathQualification.Reason.DEPTH_LIMIT, "64depth refused");
        check(run(path(0)).snapshot().reason() == CreationPathQualification.Reason.UNSUPPORTED_SHAPE, "different complete recursion shape not silently generalized");
        var many = new ArrayList<>(path(0).subList(0, 9)); // V->ap active, before panel request.
        for (int i = 0; i < 400; i++) many.addAll(List.of(progress("at", "ENTER", -1), progress("ax", "ENTER", -1), progress("ap", "ENTER", -1),
                progress("ap", "RETURN", -1), progress("ax", "RETURN", -1), progress("at", "RETURN", -1)));
        check(run(many).snapshot().reason() == CreationPathQualification.Reason.EVENT_LIMIT, "2048signal bound refused");
        var frozen = run(signals); var before = frozen.snapshot(); frozen.reject(CreationPathQualification.Reason.SAVE_OBSERVED);
        check(frozen.snapshot().equals(before), "returned path remains historical, not retroactive lifecycle state");
        System.out.println("PASS: " + checks + " strict online creation path checks; synthetic, no career-ready events.");
    }
}
