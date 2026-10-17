package br.vbrasfoot;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProbeDispatcherSelfTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static HookProbe.Observation observation(long sequence) {
        return new HookProbe.Observation("load", "fixture", true, false, true, false, false, Thread.currentThread().getName(), sequence);
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("callback latch timed out");
    }
    public static void main(String[] args) throws Exception {
        List<Long> delivered = Collections.synchronizedList(new ArrayList<>());
        List<String> threads = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch drained = new CountDownLatch(3);
        ProbeDispatcher ordered = new ProbeDispatcher(8, Duration.ofMillis(500), event -> {
            threads.add(Thread.currentThread().getName()); delivered.add(event.sequence()); drained.countDown();
        });
        ordered.offer(observation(1)); ordered.offer(observation(2)); ordered.offer(observation(3));
        await(drained); ordered.close();
        check(delivered.equals(List.of(1L, 2L, 3L)), "FIFO order");
        check(threads.stream().allMatch("vbrasfoot-probe-dispatch"::equals), "callbacks not on game/EDT thread");
        check(ordered.stats().accepted() == 3 && ordered.stats().delivered() == 3 && !ordered.stats().workerAlive(), "drained close");
        ordered.offer(observation(4));
        check(ordered.stats().dropped() == 1, "offer after close rejected");
        ordered.close();
        check(ordered.stats().delivered() == 3, "close idempotent");

        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        ProbeDispatcher saturated = new ProbeDispatcher(2, Duration.ofMillis(50), event -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        saturated.offer(observation(1)); await(started);
        saturated.offer(observation(2)); saturated.offer(observation(3)); saturated.offer(observation(4));
        check(saturated.stats().accepted() == 3 && saturated.stats().dropped() == 1, "bounded queue rejects newest when full");
        long start = System.nanoTime();
        saturated.close(); release.countDown();
        check(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500, "close bounded under slow callback");
        check(saturated.stats().discarded() == 2, "pending work counted on shutdown timeout");

        AtomicInteger invoked = new AtomicInteger();
        CountDownLatch afterFailure = new CountDownLatch(1);
        ProbeDispatcher failures = new ProbeDispatcher(4, Duration.ofMillis(500), event -> {
            if (invoked.incrementAndGet() == 1) throw new AssertionError("fixture callback failure");
            afterFailure.countDown();
        });
        failures.offer(observation(1)); failures.offer(observation(2));
        await(afterFailure); failures.close();
        check(failures.stats().failed() == 1 && failures.stats().delivered() == 1, "callback failure isolated");

        java.nio.file.Path temp = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("build"), "probe-exit-");
        Process child = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), ProbeShutdownFixture.class.getName(), temp.toAbsolutePath().toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("child.log").toFile()).start();
        if (!child.waitFor(10, TimeUnit.SECONDS)) { child.destroyForcibly(); throw new AssertionError("owned fixture shutdown timed out"); }
        check(child.exitValue() == 0, "System.exit on EDT never waits for EDT in dispatcher shutdown");
        Properties stats = new Properties();
        try (var input = java.nio.file.Files.newInputStream(temp.resolve("delivery.properties"))) { stats.load(input); }
        check(stats.getProperty("accepted").equals("1") && stats.getProperty("dropped").equals("0"), "shutdown stats persisted separately from LogManager");
        System.out.println("PASS: " + checks + " bounded dispatch tests (synthetic; no public mod events).");
    }
}
