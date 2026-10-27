package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Artificial authority and scalar handlers only; never runs the original game financial adapter. */
public final class SponsorActivationPipelineSelfTest {
    private static int checks;
    private static final SaveRevision SAVE = new SaveRevision("a".repeat(64), 20);
    private static final String TERMS = "c".repeat(64);
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class Handler implements CareerCommandDispatcher.Handler {
        private final FinanceRequest request;
        private final Callable<CareerCommandGate.Status> effect;
        Handler(FinanceRequest request, Callable<CareerCommandGate.Status> effect) { this.request = request; this.effect = effect; }
        public boolean supportsExecutionWitness() { return true; }
        public Optional<FinanceRequest> persistentRequest() { return Optional.of(request); }
        public boolean accepts(CareerCommandJournal.Intent intent) { return request.intent().equals(intent); }
        public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return request.matches(context, owner); }
        public CareerCommandGate.Status apply() throws Exception {
            if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("Handler must run on EDT");
            return effect.call();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final CareerSaveAssociations.Handle handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        final UUID sponsor = UUID.randomUUID();
        final CareerCommandGate gate = new CareerCommandGate();
        final Object primary = new Object(), secondary = new Object();
        final CareerCommandGate.Context context;
        final CareerCommandGate.Owner owner;
        final FinanceRequest request;
        final SponsorActivationStore activations;
        final CareerCommandJournal journal;
        final SponsorActivationPipeline pipeline;
        final AtomicInteger effects = new AtomicInteger();
        final AtomicInteger activationWrites = new AtomicInteger();
        Fixture(Path root, List<String> events, String faultAt, Duration timeout) {
            var key = new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER);
            activations = new SponsorActivationStore(new CareerSidecarStore(root.resolve("activation"), stage -> {
                boolean blocked = false;
                try { var fence = gate.beginSave(); gate.endSave(fence); }
                catch (IllegalStateException expected) { blocked = true; }
                check(blocked, "original save excluded throughout sponsor claim and terminal persistence " + stage);
                if (stage == Stage.RECORD_WRITTEN) activationWrites.incrementAndGet();
                if (stage == Stage.HEAD_STAGED && ("activation:" + activationWrites.get()).equals(faultAt))
                    throw new IOException("owned injected commit failure");
                if (stage == Stage.HEAD_COMMITTED) events.add("activation");
            }), handle);
            journal = new CareerCommandJournal(disk(root.resolve("journal"), "journal", events, faultAt), key);
            var contracts = new FinanceRequestStore(disk(root.resolve("contracts"), "contracts", events, faultAt), key);
            var witnesses = new FinanceExecutionWitnessStore(disk(root.resolve("witnesses"), "witnesses", events, faultAt), key);
            context = gate.installContext(handle.career(), handle.binding(), 1, SAVE, primary, secondary);
            owner = gate.owner(SponsorActivationStore.OWNER, Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            request = SponsorActivationStoreSelfTest.request(handle, sponsor, SAVE);
            pipeline = new SponsorActivationPipeline(gate, journal, contracts, witnesses, activations,
                    () -> new CareerCommandDispatcher.Roots(primary, secondary), timeout);
        }
        SponsorActivationPipeline.Result execute(Callable<CareerCommandGate.Status> effect) throws Exception {
            return pipeline.execute(sponsor, TERMS, owner, context, request, EMPTY, EMPTY, EMPTY, EMPTY,
                    new Handler(request, effect));
        }
        public void close() { gate.close(); }
    }
    private static CareerSidecarStore disk(Path path, String name, List<String> events, String faultAt) {
        var writes = new AtomicInteger();
        return new CareerSidecarStore(path, stage -> {
            if (stage == Stage.RECORD_WRITTEN) writes.incrementAndGet();
            if (stage == Stage.HEAD_STAGED && (name + ":" + writes.get()).equals(faultAt)) throw new IOException("owned injected commit failure");
            if (stage == Stage.HEAD_COMMITTED) events.add(name);
        });
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsor-pipeline-");
        // Observe durable commit ordering without performing disk access on the handler's EDT.
        for (var outcome : List.of(CareerCommandGate.Status.APPLIED, CareerCommandGate.Status.REJECTED, CareerCommandGate.Status.OUTCOME_UNKNOWN)) {
            var events = Collections.synchronizedList(new ArrayList<String>());
            try (var fixture = new Fixture(root.resolve("ordered-" + outcome), events, "", Duration.ofSeconds(2))) {
                var result = fixture.execute(() -> { fixture.effects.incrementAndGet(); events.add("effect"); return outcome; });
                check(events.equals(List.of("activation", "contracts", "journal", "witnesses", "effect", "witnesses", "journal", "activation")),
                        "activation -> payload -> PREPARED -> ARMED -> effect -> terminals ordering " + outcome);
                var expected = outcome == CareerCommandGate.Status.APPLIED ? SponsorActivationStore.State.MEMORY_APPLIED
                        : outcome == CareerCommandGate.Status.REJECTED ? SponsorActivationStore.State.NO_EFFECT : SponsorActivationStore.State.UNKNOWN;
                check(result.activation().entries().get(0).state() == expected, "observed state never claims saved activation " + outcome);
                check(!result.activation().entries().get(0).savedActivationProven(), "no synthetic proof of save inclusion");
                try { fixture.execute(() -> { fixture.effects.incrementAndGet(); return outcome; }); throw new AssertionError("Replay accepted"); }
                catch (Failure | IllegalArgumentException expectedFailure) { checks++; }
                check(fixture.effects.get() == 1, "repeated submission never dispatches a second effect");
            }
        }
        for (String fault : List.of("activation:1", "activation:2", "contracts:1", "journal:1", "journal:2", "witnesses:1", "witnesses:2")) {
            var events = Collections.synchronizedList(new ArrayList<String>());
            try (var fixture = new Fixture(root.resolve("fault-" + fault.replace(':', '-')), events, fault, Duration.ofSeconds(2))) {
                SponsorActivationPipeline.Result result = null;
                try { result = fixture.execute(() -> { fixture.effects.incrementAndGet(); return CareerCommandGate.Status.APPLIED; }); }
                catch (IOException preClaimFailure) { check(fault.equals("activation:1"), "only preclaim failure propagates before dispatch"); }
                boolean afterEffect = Set.of("activation:2", "journal:2", "witnesses:2").contains(fault);
                check(fixture.effects.get() == (afterEffect ? 1 : 0), "failure never replays or starts effect before prerequisite commits " + fault);
                if (result != null) {
                    check(result.kind() == CareerCommandDispatcher.Kind.UNCERTAIN, "failed persistence reports uncertainty " + fault);
                    check(fixture.activations.read().blocksNewActivation(), "uncertain operation leaves blocking activation tombstone " + fault);
                } else {
                    try { fixture.activations.read(); throw new AssertionError("Orphan accepted as empty history"); }
                    catch (Failure expected) { check(expected.reason() == Reason.RECONCILIATION_REQUIRED, "preclaim orphan requires review"); }
                }
            }
        }
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var fixture = new Fixture(root.resolve("timeout"), Collections.synchronizedList(new ArrayList<>()), "", Duration.ofMillis(100))) {
            try {
                var result = fixture.execute(() -> {
                    entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture wait timeout");
                    fixture.effects.incrementAndGet(); return CareerCommandGate.Status.APPLIED;
                });
                check(entered.getCount() == 0 && result.kind() == CareerCommandDispatcher.Kind.UNCERTAIN, "in-flight timeout remains uncertain");
                check(result.activation().entries().get(0).state() == SponsorActivationStore.State.UNKNOWN, "activation records timeout as UNKNOWN");
                boolean saveBlocked = false;
                try { fixture.gate.beginSave(); } catch (IllegalStateException expected) { saveBlocked = true; }
                check(saveBlocked, "original save excluded while timed-out effect is in flight");
            } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            check(fixture.effects.get() == 1 && fixture.activations.read().entries().get(0).state() == SponsorActivationStore.State.UNKNOWN,
                    "late applied return does not upgrade uncertainty or repeat credit");
        }
        System.out.println("PASS: " + checks + " sponsor activation pipeline checks; artificial authority, no original finance calls. Fixture: " + root);
    }
}
