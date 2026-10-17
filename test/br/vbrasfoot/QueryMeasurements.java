package br.vbrasfoot;

import java.util.Arrays;

/** Nearest-rank percentiles; measured units stay explicit. No inferred memory ownership. */
final class QueryMeasurements {
    record Summary(long min, long p50, long p95, long max) { }
    static Summary summarize(long[] samples) {
        if (samples.length == 0) throw new IllegalArgumentException("No samples");
        long[] sorted = samples.clone();
        for (long value : sorted) if (value < 0) throw new IllegalArgumentException("Negative sample");
        Arrays.sort(sorted);
        return new Summary(sorted[0], sorted[(int) Math.ceil(sorted.length * 0.50) - 1],
                sorted[(int) Math.ceil(sorted.length * 0.95) - 1], sorted[sorted.length - 1]);
    }
}
