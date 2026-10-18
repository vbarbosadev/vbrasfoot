package br.vbrasfoot;

import java.util.ArrayDeque;

/** Strict online recognition of one observed shape, never career readiness or global task/save absence. */
final class CreationPathQualification {
    enum Status { RUNNING, REJECTED, PATH_RETURNED }
    enum Reason { NONE, UNBOUND, NOT_EDT, REUSED_BINDING, STALE_BINDING, OVERLAP, CAPACITY,
        BAD_ORDER, OTHER_CHOICE, OTHER_CANDIDATE, ORIGINAL_FAILURE, OTHER_PROGRESSION,
        WRONG_RECEIVER, OTHER_PANEL, UNSUPPORTED_SHAPE, DEPTH_LIMIT, EVENT_LIMIT, ASYNC_OBSERVED, MUTATION_OBSERVED, SAVE_OBSERVED }
    private enum Phase { OQ_ENTER, PL_ENTER, CHOICE, AB_ENTER, BEFORE_AZ, AFTER_AZ, BEFORE_V,
        PROGRESS, AFTER_V, AB_RETURN, PL_RETURN, OQ_RETURN, FINISHED }
    record Snapshot(Status status, Reason reason, int signals, int methodEntries, int maxDepth, int panelReturns) { }
    private final ArrayDeque<String> methods = new ArrayDeque<>();
    private Phase phase = Phase.OQ_ENTER;
    private Status status = Status.RUNNING;
    private Reason reason = Reason.NONE;
    private int signals, entries, maxDepth, panelReturns, panelDepth;
    private boolean panelRequested;
    void reject(Reason value) { if (status == Status.RUNNING) { status = Status.REJECTED; reason = value; methods.clear(); } }
    private boolean signal() {
        if (status != Status.RUNNING) return false;
        if (++signals > 2048) { reject(Reason.EVENT_LIMIT); return false; }
        return true;
    }
    private void advance(Phase expected, Phase next) {
        if (phase != expected) reject(Reason.BAD_ORDER); else phase = next;
    }
    void dialog(String method, String stage, int value) {
        if (!method.equals("oq") && !method.equals("pl")) return;
        if (!signal()) return;
        if (stage.equals("FAILED")) { reject(Reason.ORIGINAL_FAILURE); return; }
        if (method.equals("oq") && stage.equals("ENTER")) advance(Phase.OQ_ENTER, Phase.PL_ENTER);
        else if (method.equals("pl") && stage.equals("ENTER")) advance(Phase.PL_ENTER, Phase.CHOICE);
        else if (method.equals("pl") && stage.equals("DIALOG_RETURN")) {
            if (phase != Phase.CHOICE) reject(Reason.BAD_ORDER);
            else if (value != 1) reject(Reason.OTHER_CHOICE); // NO_OPTION only; never treat closed/cancelled as No.
            else phase = Phase.AB_ENTER;
        } else if (method.equals("pl") && stage.equals("RETURN")) advance(Phase.PL_RETURN, Phase.OQ_RETURN);
        else if (method.equals("oq") && stage.equals("RETURN")) {
            advance(Phase.OQ_RETURN, Phase.FINISHED);
            if (status == Status.RUNNING) status = Status.PATH_RETURNED;
        } else reject(Reason.BAD_ORDER);
    }
    void candidate(String label, String stage) {
        if (!signal()) return;
        if (!label.equals("a.bV.AB")) { reject(Reason.OTHER_CANDIDATE); return; }
        if (stage.equals("FAILED")) reject(Reason.ORIGINAL_FAILURE);
        else if (stage.equals("ENTER")) advance(Phase.AB_ENTER, Phase.BEFORE_AZ);
        else if (stage.equals("RETURN")) advance(Phase.AB_RETURN, Phase.PL_RETURN);
        else reject(Reason.BAD_ORDER);
    }
    void step(String label, String stage) {
        if (!signal()) return;
        if (!label.equals("a.bV.AB")) { reject(Reason.OTHER_CANDIDATE); return; }
        switch (stage) {
            case "before-az" -> advance(Phase.BEFORE_AZ, Phase.AFTER_AZ);
            case "after-az" -> advance(Phase.AFTER_AZ, Phase.BEFORE_V);
            case "before-V" -> advance(Phase.BEFORE_V, Phase.PROGRESS);
            case "after-V" -> advance(Phase.AFTER_V, Phase.AB_RETURN);
            default -> reject(Reason.BAD_ORDER);
        }
    }
    void progress(String method, String stage, int panel, boolean receiverMatched) {
        if (!signal()) return;
        if (phase != Phase.PROGRESS) { reject(Reason.BAD_ORDER); return; }
        if (stage.equals("FAILED")) { reject(Reason.ORIGINAL_FAILURE); return; }
        if (!java.util.Set.of("V", "ap", "at", "ax").contains(method)) { reject(Reason.OTHER_PROGRESSION); return; }
        if (stage.equals("ENTER")) {
            if (!receiverMatched) { reject(Reason.WRONG_RECEIVER); return; }
            if (panelDepth != 0) { reject(Reason.BAD_ORDER); return; }
            String parent = methods.peek();
            boolean edge = parent == null ? method.equals("V") && entries == 0
                    : switch (parent) { case "V", "ax" -> method.equals("ap"); case "ap" -> method.equals("at"); case "at" -> method.equals("ax"); default -> false; };
            if (!edge) { reject(Reason.OTHER_PROGRESSION); return; }
            if (methods.size() >= 64) { reject(Reason.DEPTH_LIMIT); return; }
            methods.push(method); entries++; maxDepth = Math.max(maxDepth, methods.size());
        } else if (stage.equals("RETURN")) {
            if (panelDepth != 0 || methods.isEmpty() || !methods.peek().equals(method)) { reject(Reason.BAD_ORDER); return; }
            methods.pop();
            if (methods.isEmpty()) {
                if (!method.equals("V") || panelReturns != 1) reject(Reason.OTHER_PANEL);
                else if (entries != 38 || maxDepth != 38) reject(Reason.UNSUPPORTED_SHAPE);
                else phase = Phase.AFTER_V;
            }
        } else if (stage.equals("PANEL_REQUEST")) {
            if (!method.equals("ap") || methods.isEmpty() || !methods.peek().equals(method) || panelRequested || panel != 2) reject(Reason.OTHER_PANEL);
            else { panelRequested = true; panelDepth = methods.size(); }
        } else if (stage.equals("PANEL_RETURN")) {
            if (!method.equals("ap") || methods.isEmpty() || !methods.peek().equals(method)
                    || panelDepth == 0 || panelDepth != methods.size() || panel != -1) reject(Reason.BAD_ORDER);
            else { panelDepth = 0; panelReturns++; }
        } else reject(Reason.BAD_ORDER);
    }
    Snapshot snapshot() { return new Snapshot(status, reason, signals, entries, maxDepth, panelReturns); }
}
