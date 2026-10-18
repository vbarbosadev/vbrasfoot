package br.vbrasfoot;

import java.lang.ref.WeakReference;
import java.util.*;

/** Same-thread diagnostic nesting only; never classifies a career or changes lifecycle. */
final class CreationDialogTracker {
    record Event(long sequence, long frame, long parent, String method, String stage, int result,
                 boolean resultKnown, long generation, long thread, boolean rootsMatched) { }
    record Snapshot(List<Event> events, long dropped, long rejected, long outside) { }
    private record Frame(long epoch, long id, long parent, String method, long generation,
                         WeakReference<Object> primary, WeakReference<Object> secondary) { }
    private final ThreadLocal<ArrayDeque<Frame>> frames = ThreadLocal.withInitial(ArrayDeque::new);
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long epoch, nextId, sequence, dropped, rejected, outside;
    synchronized void reset() { epoch++; frames.remove(); events.clear(); sequence = dropped = rejected = outside = 0; }
    synchronized void marker(String method, String stage, int result, Object primary, Object secondary, long generation) {
        boolean boundary = method.equals("oq") || method.equals("pl");
        if (!(boundary && Set.of("ENTER", "RETURN", "FAILED").contains(stage))
                && !(method.equals("pl") && stage.equals("DIALOG_RETURN"))
                && !(method.equals("AB") && stage.equals("CANDIDATE_ENTER"))) { rejected++; return; }
        var stack = frames.get();
        if (!stack.isEmpty() && stack.peek().epoch() != epoch) stack.clear();
        if (stage.equals("ENTER")) {
            if (stack.size() >= 8) { stack.clear(); rejected++; }
            stack.push(new Frame(epoch, ++nextId, stack.isEmpty() ? 0 : stack.peek().id(), method, generation,
                    new WeakReference<>(primary), new WeakReference<>(secondary)));
        }
        Frame frame = stack.peek();
        if (frame == null) { outside++; frames.remove(); return; }
        if (!method.equals("AB") && !frame.method().equals(method)) { rejected++; stack.clear(); frames.remove(); return; }
        boolean matched = generation >= 0 && frame.generation() == generation && primary != null && secondary != null
                && frame.primary().get() == primary && frame.secondary().get() == secondary;
        if (events.size() == 512) { events.removeFirst(); dropped++; }
        boolean known = stage.equals("DIALOG_RETURN");
        events.addLast(new Event(++sequence, frame.id(), frame.parent(), method, stage, known ? result : 0,
                known, generation, Thread.currentThread().getId(), matched));
        if (stage.equals("RETURN") || stage.equals("FAILED")) stack.pop();
        if (stack.isEmpty()) frames.remove();
    }
    synchronized Snapshot capture() { return new Snapshot(List.copyOf(events), dropped, rejected, outside); }
}
