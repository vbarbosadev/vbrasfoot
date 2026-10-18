package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded causal diagnostics; roots/task identities are weak and never imply career readiness. */
final class AsyncCreationTracker {
    record Report(long id, long origin, long parent, String originLabel, String label, long generation, boolean scheduleReturned,
                  boolean scheduleFailed, boolean started, boolean returned, boolean failed, boolean stale) { }
    record Progress(long sequence, long origin, long parent, String originLabel, String method, String stage,
                    int panel, long generation, long thread, boolean matched, boolean rootsMatched, boolean receiverKnown) { }
    record Snapshot(List<Report> tasks, List<Progress> progress, long rejected, long unbound, long dropped, long outside) { }
    private static final Set<String> METHODS = Set.of("V", "aa", "ap", "at", "ax");
    private static final Set<String> PROGRESS_STAGES = Set.of("ENTER", "RETURN", "FAILED", "PANEL_REQUEST", "PANEL_RETURN");
    private record Context(long epoch, long origin, long parent, String originLabel, String label, long generation,
                           WeakReference<Object> primary, WeakReference<Object> secondary) { }
    private static final class Task {
        final long id; final Context context; final String label; final WeakReference<Object> identity;
        boolean scheduleReturned, scheduleFailed, started, returned, failed, stale;
        Task(long id, Context context, String label, Object task) { this.id = id; this.context = context; this.label = label; identity = new WeakReference<>(task); }
        Report report() { return new Report(id, context.origin(), context.parent(), context.originLabel(), label, context.generation(),
                scheduleReturned, scheduleFailed, started, returned, failed, stale); }
    }
    private record Schedule(String label, long epoch, Task task) { }
    private final ThreadLocal<ArrayDeque<Context>> contexts = ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<Schedule>> schedules = ThreadLocal.withInitial(ArrayDeque::new);
    private final List<Task> tasks = new ArrayList<>();
    private final ArrayDeque<Progress> progress = new ArrayDeque<>();
    private long progressSequence, progressDropped, progressOutside;
    private long epoch, nextId, rejected, unbound;
    synchronized void reset() {
        epoch++; tasks.clear(); progress.clear(); progressSequence = 0; progressDropped = 0; progressOutside = 0;
        rejected = 0; unbound = 0; contexts.remove(); schedules.remove();
    }
    synchronized void progress(String method, String stage, int panel, Object receiver, Object primary, Object secondary, long generation) {
        if (!METHODS.contains(method) || !PROGRESS_STAGES.contains(stage)) { rejected++; return; }
        var stack = contexts.get(); Context context = stack.peek();
        if (stack.isEmpty()) contexts.remove();
        if (context == null || context.origin() == 0 || context.epoch() != epoch) { progressOutside++; return; }
        if (progress.size() >= 512) { progress.removeFirst(); progressDropped++; }
        boolean rootsMatched = valid(context, epoch, primary, secondary, generation);
        progress.addLast(new Progress(++progressSequence, context.origin(), context.parent(), context.originLabel(), method, stage,
                panel, generation, Thread.currentThread().getId(), receiver != null && receiver == primary && rootsMatched,
                rootsMatched, receiver != null));
    }
    synchronized List<Progress> progress() { return List.copyOf(progress); }
    synchronized long progressDropped() { return progressDropped; }
    synchronized long progressOutside() { return progressOutside; }
    synchronized Snapshot capture() { return new Snapshot(snapshot(), progress(), rejected, unbound, progressDropped, progressOutside); }
    private static boolean valid(Context context, long epoch, Object primary, Object secondary, long generation) {
        return context != null && context.epoch() == epoch && generation >= 0 && context.generation() == generation
                && primary != null && secondary != null && context.primary().get() == primary && context.secondary().get() == secondary;
    }
    synchronized void origin(String label, Object primary, Object secondary, long generation) {
        var stack = contexts.get();
        if (stack.size() >= 8) { stack.clear(); rejected++; }
        long id = ++nextId;
        stack.push(new Context(epoch, id, 0, label, label, generation, new WeakReference<>(primary), new WeakReference<>(secondary)));
    }
    synchronized void endOrigin(String label) {
        var stack = contexts.get();
        if (stack.isEmpty() || !stack.peek().label().equals(label)) { stack.clear(); rejected++; }
        else stack.pop();
        if (stack.isEmpty()) contexts.remove();
    }
    synchronized void scheduleStarted(String label) {
        var stack = schedules.get();
        if (stack.size() >= 8) { stack.clear(); rejected++; }
        stack.push(new Schedule(label, epoch, null));
    }
    synchronized void taskCreated(String label, Object identity, Object primary, Object secondary, long generation) {
        var stack = schedules.get();
        var contextStack = contexts.get(); Context context = contextStack.peek();
        if (contextStack.isEmpty()) contexts.remove();
        if (!valid(context, epoch, primary, secondary, generation)) { unbound++; return; }
        if (identity == null || stack.isEmpty() || stack.peek().epoch() != epoch || !stack.peek().label().equals(label) || stack.peek().task() != null
                || tasks.stream().anyMatch(t -> t.identity.get() == identity)) { rejected++; return; }
        // Keep at most128 reports/weak identities; only finished or collected entries may be evicted.
        if (tasks.size() >= 128) {
            var removable = tasks.stream().filter(t -> t.identity.get() == null || ((t.returned || t.failed || t.scheduleFailed || t.stale) && (t.scheduleReturned || t.scheduleFailed))).findFirst();
            if (removable.isEmpty()) { rejected++; return; }
            tasks.remove(removable.get());
        }
        long live = tasks.stream().filter(t -> t.identity.get() != null && !t.returned && !t.failed && !t.scheduleFailed && !t.stale).count();
        if (live >= 64) { rejected++; return; }
        Task task = new Task(++nextId, context, label, identity); tasks.add(task);
        stack.pop(); stack.push(new Schedule(label, epoch, task));
    }
    synchronized void scheduleFinished(String label, boolean returned) {
        var stack = schedules.get();
        if (stack.isEmpty() || !stack.peek().label().equals(label)) { stack.clear(); rejected++; }
        else {
            var entry = stack.pop();
            if (entry.epoch() == epoch && entry.task() != null) {
                if (returned) entry.task().scheduleReturned = true; else entry.task().scheduleFailed = true;
            }
        }
        if (stack.isEmpty()) schedules.remove();
    }
    synchronized void runStarted(String label, Object identity, Object primary, Object secondary, long generation) {
        var task = tasks.stream().filter(t -> t.identity.get() == identity && identity != null && t.label.equals(label)).findFirst().orElse(null);
        var stack = contexts.get();
        if (stack.size() >= 8) { stack.clear(); rejected++; }
        if (task == null) { unbound++; stack.push(new Context(epoch, 0, 0, "UNBOUND", label, -1, new WeakReference<>(null), new WeakReference<>(null))); return; }
        if (task.started || task.scheduleFailed || !valid(task.context, epoch, primary, secondary, generation)) {
            task.stale = true; rejected++;
            stack.push(new Context(epoch, 0, 0, "UNBOUND", label, -1, new WeakReference<>(null), new WeakReference<>(null))); return;
        }
        task.started = true;
        stack.push(new Context(epoch, task.context.origin(), task.id, task.context.originLabel(), label, generation, task.context.primary(), task.context.secondary()));
    }
    synchronized void runFinished(String label, Object identity, Object primary, Object secondary, long generation, boolean returned) {
        var stack = contexts.get();
        Context entry = stack.isEmpty() ? null : stack.pop();
        if (entry == null || !entry.label().equals(label)) { stack.clear(); rejected++; }
        else if (entry.epoch() == epoch && entry.parent() != 0) {
            var task = tasks.stream().filter(t -> t.id == entry.parent() && t.identity.get() == identity).findFirst().orElse(null);
            if (task != null) {
                if (!valid(entry, epoch, primary, secondary, generation)) task.stale = true;
                if (returned) task.returned = true; else task.failed = true;
            }
        }
        if (stack.isEmpty()) contexts.remove();
    }
    synchronized List<Report> snapshot() { return tasks.stream().map(Task::report).toList(); }
    synchronized long rejected() { return rejected; }
    synchronized long unbound() { return unbound; }
}
