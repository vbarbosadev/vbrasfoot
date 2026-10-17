package br.vbrasfoot.observer;

import br.vbrasfoot.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.*;

/** Public-SDK fake only: no real hooks, persistence or game runtime. */
public final class CareerObserverModTest {
    public static void main(String[] args) {
        List<String> messages = new ArrayList<>();
        Logger logger = Logger.getLogger("observer-sdk-fixture"); logger.setUseParentHandlers(false);
        Handler handler = new Handler() {
            public void publish(LogRecord record) { messages.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        AtomicInteger subscriptions = new AtomicInteger(), closed = new AtomicInteger();
        CareerObservations service = new CareerObservations() {
            public State state() { return new State("fixture", 1, 1, Phase.ACTIVE, 1, 0, true); }
            public Subscription subscribe(Consumer<Update> callback) {
                subscriptions.incrementAndGet(); callback.accept(new Update(Kind.READ_COMPLETED, state(), false));
                return new Subscription() {
                    private boolean active = true;
                    public Status status() { return active ? Status.ACTIVE : Status.CLOSED; }
                    public void close() { if (active) { active = false; closed.incrementAndGet(); } }
                };
            }
        };
        ModContext context = new ModContext() {
            public GameAccess game() { throw new AssertionError("No game.read permission"); }
            public Logger logger() { return logger; }
            public CareerObservations careerObservations() { return service; }
            public void registerAction(String id, String label, Runnable action) { throw new AssertionError("No UI permission"); }
            public byte[] readData(String file) { throw new AssertionError("No data permission"); }
            public void writeData(String file, byte[] bytes) { throw new AssertionError("No data permission"); }
        };
        try {
            var mod = new CareerObserverMod(); mod.initialize(context);
            if (subscriptions.get() != 1 || messages.size() != 1 || !messages.get(0).contains("kind=READ_COMPLETED")
                    || !messages.get(0).contains("reads=1")) throw new AssertionError("Observer subscription/report contract");
            mod.shutdown(); mod.shutdown();
            if (closed.get() != 1) throw new AssertionError("Observer resource cleanup");
        } finally { logger.removeHandler(handler); }
        System.out.println("PASS: SDK-only observer subscription/report/cleanup; synthetic, no game events.");
    }
}
