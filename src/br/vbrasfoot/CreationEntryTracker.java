package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;

/** Diagnostic bridge across separate UI callbacks, not a ready-career classifier. */
final class CreationEntryTracker {
    record Event(long sequence, long entry, long attempt, String stage, long generation, long thread,
                 boolean bound, boolean rootsMatched, boolean resultKnown, int result) { }
    record Snapshot(List<Event> events, long dropped, long rejected, long outside, long boundAttempts, long unboundAttempts) { }
    record QualifiedAttempt(long entry, long attempt, long generation, CreationPathQualification.Snapshot path) { }
    record QualificationSnapshot(List<QualifiedAttempt> attempts, long dropped, int active, long capacityRejected) { }
    private static final class Entry {
        final long epoch, id, generation;
        final WeakReference<Object> primary, secondary;
        WeakReference<Object> panel = new WeakReference<>(null);
        boolean settings, panelReturned, assigned, invalid, prepared, qualificationUsed;
        Entry(long epoch, long id, long generation, Object primary, Object secondary) {
            this.epoch = epoch; this.id = id; this.generation = generation;
            this.primary = new WeakReference<>(primary); this.secondary = new WeakReference<>(secondary);
        }
    }
    private record Attempt(long epoch, long id, Entry entry, long generation, CreationPathQualification qualification) { }
    private final ThreadLocal<ArrayDeque<Entry>> preparing = ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<Attempt>> attempts = ThreadLocal.withInitial(ArrayDeque::new);
    private final List<Entry> bindings = new ArrayList<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final Map<Long, Attempt> active = new LinkedHashMap<>();
    private final ArrayDeque<QualifiedAttempt> qualifications = new ArrayDeque<>();
    private long qualificationsDropped, qualificationCapacityRejected;
    private long epoch, nextId, sequence, dropped, rejected, outside, boundAttempts, unboundAttempts;
    synchronized void reset() {
        epoch++; preparing.remove(); attempts.remove(); bindings.clear(); events.clear();
        sequence = dropped = rejected = outside = boundAttempts = unboundAttempts = 0;
        active.clear(); qualifications.clear(); qualificationsDropped = qualificationCapacityRejected = 0;
    }
    private boolean current(Entry entry, Object primary, Object secondary, long generation) {
        return entry != null && entry.epoch == epoch && !entry.invalid && generation >= 0 && entry.generation == generation
                && primary != null && secondary != null && entry.primary.get() == primary && entry.secondary.get() == secondary;
    }
    private void record(Entry entry, long attempt, String stage, Object primary, Object secondary, long generation, boolean known, int result) {
        if (events.size() == 512) { events.removeFirst(); dropped++; }
        events.addLast(new Event(++sequence, entry == null ? 0 : entry.id, attempt, stage, generation,
                Thread.currentThread().getId(), entry != null && entry.prepared && current(entry, primary, secondary, generation),
                current(entry, primary, secondary, generation), known, known ? result : 0));
    }
    synchronized void preparation(String stage, Object panel, Object primary, Object secondary, long generation) {
        if (!Set.of("ENTER", "SETTINGS_RETURN", "PANEL_ASSIGNED", "PANEL_RETURN", "RETURN", "FAILED").contains(stage)) { rejected++; return; }
        if (stage.equals("ENTER")) interference(CreationPathQualification.Reason.OVERLAP);
        var stack = preparing.get();
        if (!stack.isEmpty() && stack.peek().epoch != epoch) stack.clear();
        if (stage.equals("ENTER")) {
            boolean overflow = stack.size() >= 8;
            if (overflow) { for (var e : stack) e.invalid = true; stack.clear(); rejected++; }
            if (!stack.isEmpty()) stack.peek().invalid = true; // Reentrant preparation cannot silently qualify its parent.
            var entry = new Entry(epoch, ++nextId, generation, primary, secondary); entry.invalid = overflow;
            stack.push(entry);
        }
        if (stage.equals("PANEL_ASSIGNED")) {
            // Any reassignment of the same panel outside its original operation revokes its old binding.
            for (var old : bindings) if (panel != null && old.panel.get() == panel) old.invalid = true;
        }
        Entry entry = stack.peek();
        if (entry == null) { outside++; preparing.remove(); return; }
        if (!current(entry, primary, secondary, generation)) entry.invalid = true;
        switch (stage) {
            case "SETTINGS_RETURN" -> { if (entry.settings || entry.assigned) entry.invalid = true; entry.settings = true; }
            case "PANEL_ASSIGNED" -> {
                if (!entry.settings || entry.assigned || panel == null) entry.invalid = true;
                entry.assigned = true; entry.panel = new WeakReference<>(panel);
            }
            case "PANEL_RETURN" -> {
                if (!entry.assigned || entry.panelReturned) entry.invalid = true;
                entry.panelReturned = true;
            }
            case "RETURN" -> {
                bindings.removeIf(e -> e.invalid || e.panel.get() == null || e.primary.get() == null || e.secondary.get() == null);
                if (entry.settings && entry.assigned && entry.panelReturned && entry.panel.get() != null && current(entry, primary, secondary, generation)) {
                    if (bindings.size() < 64) { entry.prepared = true; bindings.add(entry); }
                    else { entry.invalid = true; rejected++; }
                }
            }
            case "FAILED" -> entry.invalid = true;
            default -> { }
        }
        record(entry, 0, "PREPARE_" + stage, primary, secondary, generation, false, 0);
        if (stage.equals("RETURN") || stage.equals("FAILED")) stack.pop();
        if (stack.isEmpty()) preparing.remove();
    }
    synchronized void attempt(Object panel, Object primary, Object secondary, long generation) {
        attempt(panel, primary, secondary, generation, false);
    }
    synchronized void attempt(Object panel, Object primary, Object secondary, long generation, boolean edt) {
        var stack = attempts.get();
        if (!stack.isEmpty() && stack.peek().epoch() != epoch) stack.clear();
        boolean overflow = stack.size() >= 8;
        if (overflow) {
            for (var attempt : stack) {
                if (attempt.entry() != null) attempt.entry().invalid = true;
                attempt.qualification().reject(CreationPathQualification.Reason.OVERLAP);
                active.remove(attempt.id()); remember(attempt);
            }
            stack.clear(); rejected++;
        }
        if (!stack.isEmpty() && stack.peek().entry() != null) stack.peek().entry().invalid = true;
        Entry binding = bindings.stream().filter(e -> panel != null && e.panel.get() == panel && e.prepared
                && current(e, primary, secondary, generation)).findFirst().orElse(null);
        if (overflow || !stack.isEmpty()) binding = null; // Nested/overflowed attempts are not exclusive.
        var qualification = new CreationPathQualification();
        if (binding == null) qualification.reject(CreationPathQualification.Reason.UNBOUND);
        else if (!edt) qualification.reject(CreationPathQualification.Reason.NOT_EDT);
        else if (binding.qualificationUsed) qualification.reject(CreationPathQualification.Reason.REUSED_BINDING);
        if (binding != null) binding.qualificationUsed = true;
        if (!active.isEmpty()) {
            for (var previous : active.values()) previous.qualification().reject(CreationPathQualification.Reason.OVERLAP);
            qualification.reject(CreationPathQualification.Reason.OVERLAP);
        }
        Attempt attempt = new Attempt(epoch, ++nextId, binding, generation, qualification); stack.push(attempt);
        if (active.size() < 64) active.put(attempt.id(), attempt);
        else { qualification.reject(CreationPathQualification.Reason.CAPACITY); qualificationCapacityRejected++; }
        if (binding == null) unboundAttempts++; else boundAttempts++;
        record(binding, attempt.id(), "ATTEMPT_ENTER", primary, secondary, generation, false, 0);
    }
    synchronized void dialog(String method, String stage, int result, Object primary, Object secondary, long generation) {
        boolean end = method.equals("oq") && (stage.equals("RETURN") || stage.equals("FAILED"));
        boolean dialog = method.equals("pl") && stage.equals("DIALOG_RETURN");
        boolean candidate = method.equals("AB") && stage.equals("CANDIDATE_ENTER");
        Attempt observed = activeAttempt(primary, secondary, generation);
        if (observed != null) observed.qualification().dialog(method, stage, result);
        if (!end && !dialog && !candidate) return;
        var stack = attempts.get();
        if (!stack.isEmpty() && stack.peek().epoch() != epoch) stack.clear();
        Attempt attempt = stack.peek();
        if (attempt == null) { outside++; attempts.remove(); return; }
        record(attempt.entry(), attempt.id(), method + "_" + stage, primary, secondary, generation, dialog, result);
        if (end) { stack.pop(); active.remove(attempt.id()); remember(attempt); }
        if (stack.isEmpty()) attempts.remove();
    }
    synchronized Snapshot capture() { return new Snapshot(List.copyOf(events), dropped, rejected, outside, boundAttempts, unboundAttempts); }
    private Attempt activeAttempt(Object primary, Object secondary, long generation) {
        var stack = attempts.get();
        if (!stack.isEmpty() && stack.peek().epoch() != epoch) stack.clear();
        if (stack.isEmpty()) {
            attempts.remove();
            if (!active.isEmpty()) interference(CreationPathQualification.Reason.OVERLAP);
            return null;
        }
        var attempt = stack.peek();
        if (!current(attempt.entry(), primary, secondary, generation) || attempt.entry().panel.get() == null)
            attempt.qualification().reject(CreationPathQualification.Reason.STALE_BINDING);
        return attempt;
    }
    synchronized void candidate(String label, String stage, Object primary, Object secondary, long generation) {
        var attempt = activeAttempt(primary, secondary, generation);
        if (attempt != null) attempt.qualification().candidate(label, stage);
    }
    synchronized void step(String label, String stage, Object primary, Object secondary, long generation) {
        var attempt = activeAttempt(primary, secondary, generation);
        if (attempt != null) attempt.qualification().step(label, stage);
    }
    synchronized void progress(String method, String stage, int panel, Object receiver, Object primary, Object secondary, long generation) {
        var attempt = activeAttempt(primary, secondary, generation);
        if (attempt != null) attempt.qualification().progress(method, stage, panel, receiver != null && receiver == primary);
    }
    synchronized void interference(CreationPathQualification.Reason reason) {
        for (var attempt : active.values()) attempt.qualification().reject(reason);
    }
    private QualifiedAttempt qualifiedAttempt(Attempt attempt) {
        return new QualifiedAttempt(attempt.entry() == null ? 0 : attempt.entry().id, attempt.id(), attempt.generation(), attempt.qualification().snapshot());
    }
    private void remember(Attempt attempt) {
        if (qualifications.size() == 128) { qualifications.removeFirst(); qualificationsDropped++; }
        qualifications.addLast(qualifiedAttempt(attempt));
    }
    synchronized QualificationSnapshot qualificationSnapshot() {
        var result = new ArrayList<>(qualifications);
        for (var attempt : active.values()) result.add(qualifiedAttempt(attempt));
        return new QualificationSnapshot(List.copyOf(result), qualificationsDropped, active.size(), qualificationCapacityRejected);
    }
}
