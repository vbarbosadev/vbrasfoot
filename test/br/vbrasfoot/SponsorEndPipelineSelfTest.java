package br.vbrasfoot;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Synthetic one-shot ending, failures and original-I/O exclusion; never invokes original game methods. */
public final class SponsorEndPipelineSelfTest {
    static int checks;
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsor-end-test-");
        for (String mode : List.of("applied", "rejected", "throw", "commit-failure", "timeout")) {
            Path home = root.resolve(mode); var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
            UUID sponsor = UUID.randomUUID(); var revision = new SaveRevision("a".repeat(64), 20);
            var request = SponsorActivationStoreSelfTest.request(handle, sponsor, revision);
            var fail = new AtomicBoolean(); var effects = new AtomicInteger(); var release = new CountDownLatch(1);
            try (var gate = new CareerCommandGate()) {
                var disk = new CareerSidecarStore(home.resolve("activation"), stage -> {
                    if (fail.get() && stage == Stage.HEAD_STAGED) throw new java.io.IOException("owned terminal failure");
                });
                var ledger = new SponsorActivationStore(disk, handle);
                var claim = ledger.claim(sponsor, "b".repeat(64), request, EMPTY, "Original", "Sponsor Arena");
                var applied = ledger.complete(claim.lease(), claim.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
                var activation = applied.entries().get(0);
                var ending = new SponsorActivationStore.EndAttempt(UUID.randomUUID(), revision, "Sponsor Arena", request.before(), SponsorActivationStore.EndState.CLAIMED);
                Object primary = new Object(), secondary = new Object();
                var context = gate.installContext(handle.career(), handle.binding(), 1, revision, primary, secondary);
                var owner = gate.owner(SponsorEndPipeline.OWNER, Set.of(CareerCommandGate.Operation.SPONSOR_END));
                var intent = SponsorActivationStore.endIntent(activation, ending);
                var journal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("journal")), new Key(handle.career(), handle.binding(), SponsorEndPipeline.OWNER));
                var handler = new CareerCommandDispatcher.Handler() {
                    public boolean accepts(CareerCommandJournal.Intent value) { return intent.equals(value); }
                    public CareerCommandGate.Status apply() throws Exception {
                        check(SwingUtilities.isEventDispatchThread(), "ending runs on EDT");
                        boolean blocked = false; try { gate.beginSave(); } catch (IllegalStateException expected) { blocked = true; }
                        check(blocked, "save excluded during ending");
                        if (mode.equals("rejected")) return CareerCommandGate.Status.REJECTED;
                        if (mode.equals("timeout")) release.await(5, TimeUnit.SECONDS);
                        effects.incrementAndGet();
                        if (mode.equals("throw")) throw new java.io.IOException("owned effect failure");
                        if (mode.equals("commit-failure")) fail.set(true);
                        return CareerCommandGate.Status.APPLIED;
                    }
                };
                var pipeline = new SponsorEndPipeline(gate, journal, ledger, () -> new CareerCommandDispatcher.Roots(primary, secondary),
                        mode.equals("timeout") ? Duration.ofMillis(100) : Duration.ofSeconds(3));
                try {
                    var result = pipeline.execute(owner, context, activation, ending, applied.token(), EMPTY, handler);
                    check(result.kind() == (mode.equals("applied") ? CareerCommandDispatcher.Kind.EXECUTED
                            : mode.equals("rejected") ? CareerCommandDispatcher.Kind.NO_EFFECT : CareerCommandDispatcher.Kind.UNCERTAIN), "honest result " + mode);
                    if (mode.equals("timeout")) {
                        boolean blocked = false; try { gate.beginSave(); } catch (IllegalStateException expected) { blocked = true; }
                        check(blocked, "late in-flight ending excludes save after timeout");
                    }
                } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); fail.set(false); }
                var reopened = new SponsorActivationStore(new CareerSidecarStore(home.resolve("activation")), handle);
                var history = reopened.read();
                check(history.entries().get(0).request().equals(request) && history.entries().get(0).state() == SponsorActivationStore.State.MEMORY_APPLIED,
                        "activation financial tombstone unchanged " + mode);
                try { reopened.claimEnd(sponsor, ending, history.token()); throw new AssertionError("Ending replay"); }
                catch (Failure expected) { checks++; }
                try { reopened.claim(sponsor, "b".repeat(64), request, history.token()); throw new AssertionError("Credit replay"); }
                catch (Failure expected) { checks++; }
                check(effects.get() == (mode.equals("rejected") ? 0 : 1), "exactly one owned invocation/no retry " + mode);
                check(history.permitsNextSponsor() == mode.equals("applied"), "only confirmed ending permits distinct sponsor " + mode);
                if (mode.equals("applied")) {
                    UUID nextSponsor = UUID.randomUUID();
                    var nextRequest = SponsorActivationStoreSelfTest.request(handle, nextSponsor, revision);
                    var nextClaim = reopened.claim(nextSponsor, "c".repeat(64), nextRequest, history.token(), "Original", "Next Arena");
                    check(nextClaim.snapshot().entries().size() == 2 && nextClaim.snapshot().entries().get(0).equals(history.entries().get(0)),
                            "new sponsor retains exact ended predecessor");
                    check(!nextClaim.snapshot().permitsNextSponsor(), "new in-flight sponsor prevents parallel credit");
                }
            }
        }
        System.out.println("PASS: " + checks + " sponsor ending pipeline checks. Fixture: " + root);
    }
}
