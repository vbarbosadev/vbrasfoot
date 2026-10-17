package br.vbrasfoot;

import java.util.ArrayList;
import java.util.concurrent.*;

public final class GetterCacheSelfTest {
    public static final class First { public String value() { return "first"; } }
    public static final class Second { public String value() { return "second"; } }
    public static final class Failure { public String value() { throw new IllegalArgumentException("owned-getter-failure"); } }
    private static final class FixtureLoader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(First.class.getName(), bytes, 0, bytes.length); }
    }
    public static void main(String[] args) throws Exception {
        if (!CareerBridge.call(new First(), "value").equals("first") || !CareerBridge.call(new Second(), "value").equals("second")) throw new AssertionError("Cache must be keyed by actual defining class");
        boolean rejected = false; try { CareerBridge.call(new First(), "missing"); } catch (NoSuchMethodException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Missing method is still rejected");
        rejected = false;
        try { CareerBridge.call(new Failure(), "value"); } catch (IllegalStateException expected) { rejected = expected.getCause() instanceof IllegalArgumentException; }
        if (!rejected) throw new AssertionError("Getter cause propagation unchanged");
        byte[] fixture;
        try (var stream = GetterCacheSelfTest.class.getResourceAsStream("/br/vbrasfoot/GetterCacheSelfTest$First.class")) { fixture = stream.readAllBytes(); }
        Object one = new FixtureLoader().define(fixture).getConstructor().newInstance();
        Object two = new FixtureLoader().define(fixture).getConstructor().newInstance();
        if (one.getClass() == two.getClass() || !CareerBridge.call(one, "value").equals("first") || !CareerBridge.call(two, "value").equals("first")) throw new AssertionError("Same binary name in distinct classloaders must not share Method");
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> {
                try { for (int j = 0; j < 1000; j++) if (!CareerBridge.call(new First(), "value").equals("first")) throw new AssertionError("Concurrent cache mismatch"); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }));
            for (var job : jobs) job.get(3, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        System.out.println("PASS: getter cache class isolation, missing/failing getter and concurrent access; synthetic, not a leak proof.");
    }
}
