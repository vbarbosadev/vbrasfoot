package br.vbrasfoot;

public final class QueryMeasurementsSelfTest {
    public static void main(String[] args) {
        long[] unsorted = {100, 1, 3, 2};
        var result = QueryMeasurements.summarize(unsorted);
        if (result.min() != 1 || result.p50() != 2 || result.p95() != 100 || result.max() != 100 || unsorted[0] != 100) throw new AssertionError("nearest-rank/no mutation");
        long[] hundred = java.util.stream.LongStream.rangeClosed(1, 100).toArray();
        if (QueryMeasurements.summarize(hundred).p95() != 95) throw new AssertionError("95th nearest rank");
        if (QueryMeasurements.summarize(new long[] {7}).p50() != 7) throw new AssertionError("single sample");
        boolean rejected = false; try { QueryMeasurements.summarize(new long[0]); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("empty samples");
        rejected = false; try { QueryMeasurements.summarize(new long[] {-1}); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("negative sample");
        System.out.println("PASS: 5 query measurement statistics checks (synthetic, no performance gate).");
    }
}
