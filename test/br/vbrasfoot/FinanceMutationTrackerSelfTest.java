package br.vbrasfoot;

public final class FinanceMutationTrackerSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        var tracker = new FinanceMutationTracker(); var loader = new ClassLoader() { };
        var empty = tracker.snapshot(loader); check(!empty.definingClassesObserved() && !empty.stableSince(empty), "missing instrumentation never permits stable observation");
        tracker.instrumented("best.ah", loader); check(!tracker.snapshot(loader).definingClassesObserved(), "one class is insufficient");
        tracker.instrumented("best.C", loader); var start = tracker.snapshot(loader);
        check(start.stableSince(start), "quiescent defining-class diagnostic coverage can be stable");
        check(!tracker.snapshot(new ClassLoader() { }).definingClassesObserved(), "another runtime cannot borrow registration");
        tracker.begin(); var running = tracker.snapshot(loader);
        check(running.active() == 1 && !running.stableSince(start), "write start invalidates old checkpoint before field changes");
        tracker.end(); var after = tracker.snapshot(loader);
        check(after.active() == 0 && after.started() == after.completed() && !after.stableSince(start), "a completed equal-value write still invalidates old checkpoint");
        check(after.stableSince(after), "new quiescent checkpoint is internally stable");
        tracker.begin(); tracker.begin(); tracker.end(); check(tracker.snapshot(loader).active() == 1, "nested writes do not prematurely drain");
        tracker.end(); check(tracker.snapshot(loader).active() == 0, "nested writes drain in same thread");
        var beforeReset = tracker.snapshot(loader); tracker.invalidate(); check(!tracker.snapshot(loader).stableSince(beforeReset), "root/reset invalidation prevents checkpoint reuse");
        var other = new FinanceMutationTracker(); other.instrumented("best.ah", loader); other.instrumented("best.C", loader);
        check(!other.snapshot(loader).stableSince(tracker.snapshot(loader)), "different tracker instance never matches by numeric counters alone");
        tracker.begin(); Thread competing = new Thread(tracker::end); competing.start(); competing.join();
        check(!tracker.snapshot(loader).healthy() && tracker.snapshot(loader).active() == 1, "foreign-thread completion cannot drain outstanding write");
        tracker.end(); check(!tracker.snapshot(loader).stableSince(tracker.snapshot(loader)), "unhealthy state is sticky");
        var mismatch = new FinanceMutationTracker(); mismatch.instrumented("best.ah", loader); mismatch.instrumented("best.C", new ClassLoader() { });
        check(!mismatch.snapshot(loader).healthy(), "defining classes from two runtimes cannot establish coverage");
        var interrupted = new FinanceMutationTracker(); interrupted.instrumented("best.ah", loader); interrupted.instrumented("best.C", loader); interrupted.begin(); interrupted.invalidate(); interrupted.end();
        check(!interrupted.snapshot(loader).healthy(), "invalidation during write never manufactures a clean post-write proof");
        var unmatched = new FinanceMutationTracker(); unmatched.end(); check(!unmatched.snapshot(loader).healthy(), "unmatched callback poisons diagnostic tracker");
        var serializerTracker = new FinanceMutationTracker(); serializerTracker.instrumented("best.ah", loader); serializerTracker.instrumented("best.C", loader);
        var beforeSerialize = serializerTracker.snapshot(loader);
        var provenance = new WrittenSaveProvenance();
        var ticket = new CareerLifecycle.Ticket("own", 1, true); Object primary = new Object(), secondary = new Object();
        var path = java.nio.file.Path.of("build", "own.s22").toAbsolutePath();
        var stream = new SaveOutputTrace.Result(10, "a".repeat(64), true, true);
        long revision = provenance.invalidate();
        provenance.complete(revision, ticket, ticket, path, stream, true, primary, secondary, beforeSerialize, serializerTracker.snapshot(loader));
        check(provenance.snapshot().binding().orElseThrow().noObservedFinanceWriteDuringSerialization(), "unchanged synthetic serializer checkpoint is only a diagnostic no-observed-write fact");
        check(FinanceMutationAssessment.assess(java.util.Optional.of(beforeSerialize), provenance.snapshot().binding().orElseThrow()).executionToSerializer()
                == FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE, "execution-to-serializer checkpoint agrees only within selected write coverage");
        serializerTracker.begin(); serializerTracker.end(); revision = provenance.invalidate();
        provenance.complete(revision, ticket, ticket, path, stream, true, primary, secondary, beforeSerialize, serializerTracker.snapshot(loader));
        check(!provenance.snapshot().binding().orElseThrow().noObservedFinanceWriteDuringSerialization(), "write inside serializer window invalidates diagnostic stability without inventing inclusion");
        check(FinanceMutationAssessment.assess(java.util.Optional.of(beforeSerialize), provenance.snapshot().binding().orElseThrow()).serializerWindow()
                == FinanceMutationAssessment.Status.OBSERVED_INTERFERENCE, "serializer write is reported as interference, not saved effect");
        revision = provenance.invalidate();
        provenance.complete(revision, ticket, ticket, path, stream, true, primary, secondary);
        check(!provenance.snapshot().binding().orElseThrow().noObservedFinanceWriteDuringSerialization(), "legacy byte provenance without mutation checkpoints never manufactures clean finance coverage");
        check(FinanceMutationAssessment.assess(java.util.Optional.empty(), provenance.snapshot().binding().orElseThrow()).executionToSerializer()
                == FinanceMutationAssessment.Status.UNAVAILABLE, "missing execution checkpoint is explicitly unavailable");
        var kryo = new FinanceMutationTracker(); kryo.instrumented("best.ah", loader); kryo.instrumented("best.C", loader);
        var directOnly = kryo.snapshot(loader);
        for (String name : FinanceMutationTracker.KRYO_CLASSES) kryo.kryoInstrumented(name, loader);
        var loaded = kryo.snapshot(loader);
        check(loaded.kryoClassesObserved().size() == 9 && !loaded.stableSince(directOnly), "loading selected decoder routes changes coverage checkpoint conservatively");
        boolean immutable = false; try { loaded.kryoClassesObserved().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "bounded loaded decoder class inventory is immutable");
        kryo.beginKryo(); var inKryo = kryo.snapshot(loader);
        check(inKryo.kryoStarted() == 1 && inKryo.active() == 1 && !inKryo.stableSince(loaded), "selected indirect setter starts before mutation");
        kryo.endKryo(); var afterKryo = kryo.snapshot(loader);
        check(afterKryo.kryoCompleted() == 1 && afterKryo.active() == 0 && !afterKryo.stableSince(loaded) && afterKryo.stableSince(afterKryo), "equal-value decoder/copy mutation invalidates shared checkpoint and drains normally");
        check(kryo.snapshot(new ClassLoader() { }).kryoClassesObserved().isEmpty(), "another runtime cannot borrow decoder route inventory");
        var badKryo = new FinanceMutationTracker(); badKryo.kryoInstrumented("unknown", loader);
        check(!badKryo.snapshot(loader).healthy(), "unknown decoder route fails closed");
        var mixedKryo = new FinanceMutationTracker(); mixedKryo.instrumented("best.ah", loader);
        mixedKryo.kryoInstrumented(FinanceMutationTracker.KRYO_CLASSES.iterator().next(), new ClassLoader() { });
        check(!mixedKryo.snapshot(loader).healthy(), "decoder route from another runtime cannot qualify");
        var failedKryo = new FinanceMutationTracker(); failedKryo.instrumented("best.ah", loader); failedKryo.instrumented("best.C", loader); failedKryo.beginKryo();
        check(!failedKryo.snapshot(loader).stableSince(failedKryo.snapshot(loader)), "setter exception without return leaves active coverage unavailable, never falsely drained");
        failedKryo.invalidate(); failedKryo.endKryo();
        check(!failedKryo.snapshot(loader).healthy(), "reset cannot manufacture health after interrupted decoder mutation");
        System.out.println("PASS: " + checks + " financial mutation tracker checks; selected direct/original Kryo sites only, not save inclusion.");
    }
}
