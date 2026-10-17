package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import javax.swing.JFrame;

/** Original decode + read-only SDK queries in a private fixture; NOT full UI startup/gameplay. */
public final class CareerQueryBenchmark {
    private static volatile Object blackhole;
    private static long heap() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum(); }
    private static long gcMillis() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum(); }
    private static com.sun.management.ThreadMXBean allocations;
    private static long allocated() {
        return allocations == null ? -1 : allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
    private record Sample(long wall, long edt, long allocated, int returned) { }
    private static Sample sample(Callable<Integer> query) {
        long wall = System.nanoTime();
        Sample result = CareerBridge.onEdt(() -> {
            long before = allocated(), start = System.nanoTime();
            int returned = query.call();
            long elapsed = System.nanoTime() - start, after = allocated();
            return new Sample(0, elapsed, before >= 0 && after >= before ? after - before : -1, returned);
        });
        return new Sample(System.nanoTime() - wall, result.edt(), result.allocated(), result.returned());
    }
    private static void summary(Properties report, String key, long[] values) {
        var stats = QueryMeasurements.summarize(values);
        report.setProperty(key + ".min", Long.toString(stats.min())); report.setProperty(key + ".p50", Long.toString(stats.p50()));
        report.setProperty(key + ".p95", Long.toString(stats.p95())); report.setProperty(key + ".max", Long.toString(stats.max()));
    }
    private static void measure(Properties report, String name, int count, Callable<Integer> query) {
        for (int i = 0; i < 20; i++) sample(query);
        long beforeHeap = heap(), beforeGc = gcCount(), beforeGcMillis = gcMillis(), maxHeap = beforeHeap;
        long[] wall = new long[count], edt = new long[count], bytes = new long[count];
        int minReturned = Integer.MAX_VALUE, maxReturned = 0;
        boolean available = true;
        for (int i = 0; i < count; i++) {
            Sample sample = sample(query); wall[i] = sample.wall(); edt[i] = sample.edt(); bytes[i] = sample.allocated();
            available &= bytes[i] >= 0; minReturned = Math.min(minReturned, sample.returned()); maxReturned = Math.max(maxReturned, sample.returned());
            maxHeap = Math.max(maxHeap, heap());
        }
        summary(report, name + ".wall.ns", wall); summary(report, name + ".edt.ns", edt);
        report.setProperty(name + ".allocation.available", Boolean.toString(available));
        if (available) summary(report, name + ".edt.allocated.bytes", bytes);
        report.setProperty(name + ".returned.min", Integer.toString(minReturned)); report.setProperty(name + ".returned.max", Integer.toString(maxReturned));
        report.setProperty(name + ".heap.before.bytes", Long.toString(beforeHeap)); report.setProperty(name + ".heap.after.bytes", Long.toString(heap()));
        report.setProperty(name + ".heap.sampledPeak.bytes", Long.toString(maxHeap));
        report.setProperty(name + ".gc.count", Long.toString(gcCount() - beforeGc)); report.setProperty(name + ".gc.millis", Long.toString(gcMillis() - beforeGcMillis));
        var stats = QueryMeasurements.summarize(edt);
        System.out.printf(Locale.ROOT, "%s: EDT p50=%.3f ms p95=%.3f ms max=%.3f ms; returned=%d..%d%n", name,
                stats.p50() / 1_000_000.0, stats.p95() / 1_000_000.0, stats.max() / 1_000_000.0, minReturned, maxReturned);
        blackhole = null;
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        int samples = Integer.parseInt(args[2]);
        if (samples < 20 || samples > 1000 || !Files.isRegularFile(game.resolve(".query-benchmark-fixture"))
                || !Path.of("").toAbsolutePath().toRealPath().equals(game) || Files.exists(home.resolve("query-benchmark.properties"))) throw new IllegalArgumentException("Requires fresh private fixture/cwd, 20..1000 samples");
        String inputHash = Profile.digest(game.resolve("sav/input.s22")), exeHash = Profile.digest(Profile.verify(game));
        Properties report = new Properties(); report.setProperty("format", "1"); report.setProperty("coverage", "Original decode with synthetic frame; warmed scoped SDK queries, no gameplay/full UI startup");
        report.setProperty("java.version", System.getProperty("java.version")); report.setProperty("java.vendor", System.getProperty("java.vendor"));
        report.setProperty("loader.version", br.vbrasfoot.api.Sdk.VERSION); report.setProperty("profile.sha256", exeHash); report.setProperty("input.sha256", inputHash);
        report.setProperty("samples", Integer.toString(samples)); report.setProperty("warmup.perScenario", "20"); report.setProperty("maxHeap.bytes", Long.toString(Runtime.getRuntime().maxMemory()));
        report.setProperty("processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        report.setProperty("percentile", "nearest-rank"); report.setProperty("memory.coverage", "EDT allocation counter if supported; sampled whole-JVM used heap, not retained memory/leak proof; no forced GC");
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean counter && counter.isThreadAllocatedMemorySupported()) {
            try { counter.setThreadAllocatedMemoryEnabled(true); allocations = counter; }
            catch (UnsupportedOperationException | SecurityException unavailable) { allocations = null; }
        }
        long prepareStart = System.nanoTime();
        try (LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home, false)) {
            report.setProperty("runtime.prepare.ns", Long.toString(System.nanoTime() - prepareStart));
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            JFrame frame = CareerBridge.onEdt(() -> {
                JFrame value = new JFrame("vbrasfoot read-only query benchmark — synthetic frame"); value.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, value); return value;
            });
            try {
                long loadStart = System.nanoTime();
                boolean loaded = CareerBridge.onEdt(() -> (boolean) Class.forName("c.a", true, runtime).getMethod("c", String.class, boolean.class).invoke(null, "input", false));
                report.setProperty("original.decode.ns", Long.toString(System.nanoTime() - loadStart));
                if (!loaded) throw new AssertionError("Fixture decode refused");
                CareerBridge bridge = new CareerBridge(runtime);
                var first = bridge.scopedClubs("", 0, 100);
                if (!first.loaded() || first.items().isEmpty()) throw new AssertionError("No loaded SDK fixture");
                int totalClubs = first.total(), maxPlayers = -1, largestIndex = -1; long totalPlayers = 0;
                for (int offset = 0; offset < totalClubs; offset += 500) {
                    var page = bridge.scopedClubs("", offset, 500);
                    totalPlayers += page.items().stream().mapToLong(v -> v.club().playerCount()).sum();
                    for (var item : page.items()) if (item.club().playerCount() > maxPlayers) { maxPlayers = item.club().playerCount(); largestIndex = item.club().index(); }
                }
                report.setProperty("clubs", Integer.toString(totalClubs)); report.setProperty("players", Long.toString(totalPlayers));
                report.setProperty("selectedRoster.players", Integer.toString(maxPlayers));
                report.setProperty("selectedRoster.policy", "largest roster observed by paginated club summaries, not synthetic padding to requested limit");
                String filter = first.items().get(0).club().name(); // Never serialize private club name or player names.
                measure(report, "clubs.page100", samples, () -> { var page = bridge.scopedClubs("", 0, 100); blackhole = page; return page.items().size(); });
                measure(report, "clubs.filtered100", samples, () -> { var page = bridge.scopedClubs(filter, 0, 100); blackhole = page; return page.items().size(); });
                measure(report, "clubs.page500", samples, () -> { var page = bridge.scopedClubs("", 0, 500); blackhole = page; return page.items().size(); });
                var refreshed = bridge.scopedClubs("", largestIndex, 1);
                var selected = refreshed.items().get(0);
                measure(report, "players.page100", samples, () -> { var page = bridge.scopedPlayers(selected.reference(), 0, 100); blackhole = page; return page.items().size(); });
                measure(report, "players.page500", samples, () -> { var page = bridge.scopedPlayers(selected.reference(), 0, 500); blackhole = page; return page.items().size(); });
                if (!Profile.digest(game.resolve("sav/input.s22")).equals(inputHash) || !Profile.digest(game.resolve("bf22-23.exe")).equals(exeHash)) throw new AssertionError("Fixture changed during read-only measurement");
                try (var entries = Files.list(game.resolve("sav"))) { if (entries.count() != 1) throw new AssertionError("Benchmark produced unexpected save files"); }
                report.setProperty("fixture.inputUnchanged", "true"); report.setProperty("fixture.exeUnchanged", "true");
                try (var writer = Files.newBufferedWriter(home.resolve("query-benchmark.properties"), StandardOpenOption.CREATE_NEW)) { report.store(writer, "Private measured evidence, not a universal release budget"); }
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { blackhole = null; CareerBridge.onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; }); }
        System.out.println("PASS: read-only measurements recorded; fixture unchanged. No full UI/gameplay/performance gate inferred.");
    }
}
