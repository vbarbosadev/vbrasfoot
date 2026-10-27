package br.vbrasfoot;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.swing.JFrame;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Executes real original methods on a disposable marked copy only. Context authority is ARTIFICIAL, not a UI acceptance. */
public final class SponsorOriginalRuntimeTest {
    private static int checks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".sponsor-test-fixture")) || !game.equals(Path.of("").toAbsolutePath().toRealPath())
                || Files.exists(game.resolve("sav/sponsor-output.s22")) || Files.exists(home.resolve("activations")))
            throw new IllegalArgumentException("Fresh marked disposable fixture and cwd required; never target personal saves");
        String originalHash = Profile.digest(game.resolve("sav/input.s22"));
        Thread thread = Thread.currentThread(); ClassLoader previous = thread.getContextClassLoader();
        try (var runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("runtime"), false)) {
            thread.setContextClassLoader(runtime);
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            Class<?> state = Class.forName("c.a", false, runtime);
            JFrame frame = CareerBridge.onEdt(() -> {
                var window = new JFrame("Disposable sponsor fixture — NOT personal game"); window.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, window); return window;
            });
            try {
                var load = state.getMethod("c", String.class, boolean.class); var save = state.getMethod("ae", String.class);
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false)), "original loader accepts private input");
                var adapter = new LegacySponsorAdapter(runtime); var roots = CareerBridge.onEdt(adapter.finance::roots);
                Object human = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(roots.primary(), "aN")).get(0));
                var before = CareerBridge.onEdt(() -> adapter.finance.snapshot(human));
                String oldName = CareerBridge.onEdt(() -> adapter.stadium(human)), newName = "Arena Sponsor Test";
                Object stadium = CareerBridge.onEdt(() -> CareerBridge.call(human, "ev"));
                int[] capacity = CareerBridge.onEdt(() -> ((int[]) CareerBridge.call(stadium, "dT")).clone());
                int[] prices = CareerBridge.onEdt(() -> ((int[]) CareerBridge.call(stadium, "dU")).clone());
                var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID()); var sponsor = UUID.randomUUID();
                var key = new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER);
                var revision = new SaveRevision(originalHash, Files.size(game.resolve("sav/input.s22")));
                var disk = new CareerSidecarStore(home.resolve("activations")); var activations = new SponsorActivationStore(disk, handle);
                try (var gate = new CareerCommandGate()) {
                    // Explicitly artificial test authority. Production uses the owning operational-load verifier.
                    var context = gate.installContext(handle.career(), handle.binding(), 1, revision, roots.primary(), roots.secondary());
                    var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                    var request = CareerBridge.onEdt(() -> adapter.finance.request(context, key.mod(), SponsorActivationStore.key(sponsor), human,
                            FinanceDelta.Direction.CREDIT_RAW_6, 2_000_000));
                    var handler = CareerBridge.onEdt(() -> adapter.handler(request, oldName, newName));
                    var pipeline = new SponsorActivationPipeline(gate, new CareerCommandJournal(new CareerSidecarStore(home.resolve("journals")), key),
                            new FinanceRequestStore(new CareerSidecarStore(home.resolve("requests")), key),
                            new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("witnesses")), key), activations,
                            adapter.finance::roots, Duration.ofSeconds(10));
                    var result = pipeline.execute(sponsor, "a".repeat(64), owner, context, request, EMPTY, EMPTY, EMPTY, EMPTY,
                            handler, oldName, newName);
                    check(result.kind() == CareerCommandDispatcher.Kind.EXECUTED, "real original credit and name setter executed once");
                    var expected = FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, 2_000_000);
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(human)).equals(expected), "balance and all ledger fields match exact original credit delta");
                    check(CareerBridge.onEdt(() -> adapter.stadium(human)).equals(newName), "stadium name changes in actual original object");
                    check(CareerBridge.onEdt(() -> CareerBridge.call(human, "ev")) == stadium, "stadium object is not replaced or reconstructed");
                    check(Arrays.equals(capacity, CareerBridge.onEdt(() -> (int[]) CareerBridge.call(stadium, "dT")))
                            && Arrays.equals(prices, CareerBridge.onEdt(() -> (int[]) CareerBridge.call(stadium, "dU"))), "capacity and ticket prices unchanged");
                    check(result.activation().entries().get(0).stadiumBefore().equals(oldName)
                            && result.activation().entries().get(0).stadiumAfter().equals(newName), "durable claim contains original and applied stadium names");
                    var fence = gate.beginSave();
                    try { CareerBridge.onEdt(() -> { save.invoke(null, "sponsor-output"); return null; }); }
                    finally { gate.endSave(fence); }
                    check(Files.isRegularFile(game.resolve("sav/sponsor-output.s22")), "original writer creates private output");
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "sponsor-output", false)), "original loader reopens financially modified private save");
                    var restored = CareerBridge.onEdt(adapter.finance::roots);
                    Object restoredHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(restored.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(restoredHuman)).equals(expected), "credit and complete ledger persist through original save/reload");
                    check(CareerBridge.onEdt(() -> adapter.stadium(restoredHuman)).equals(newName), "new stadium name persists through original save/reload");
                    var reopened = new SponsorActivationStore(new CareerSidecarStore(home.resolve("activations")), handle);
                    var history = reopened.read();
                    try { reopened.claim(sponsor, "a".repeat(64), request, history.token()); throw new AssertionError("Replay accepted"); }
                    catch (Failure denied) { check(denied.reason() == Reason.CONFLICT, "reopening history never permits credit repetition"); }
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(restoredHuman)).equals(expected), "rejected replay does not alter restored balance");
                    var outputRevision = new SaveRevision(Profile.digest(game.resolve("sav/sponsor-output.s22")), Files.size(game.resolve("sav/sponsor-output.s22")));
                    var endContext = gate.installContext(handle.career(), handle.binding(), 2, outputRevision, restored.primary(), restored.secondary());
                    var endOwner = gate.owner(SponsorEndPipeline.OWNER, Set.of(CareerCommandGate.Operation.SPONSOR_END));
                    var activation = history.entries().get(0);
                    var ending = new SponsorActivationStore.EndAttempt(UUID.randomUUID(), outputRevision, newName, expected, SponsorActivationStore.EndState.CLAIMED);
                    var endHandler = CareerBridge.onEdt(() -> adapter.endHandler(activation, ending, () -> true));
                    var endJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("end-journal")),
                            new Key(handle.career(), handle.binding(), SponsorEndPipeline.OWNER));
                    var endingPipeline = new SponsorEndPipeline(gate, endJournal, reopened, adapter.finance::roots, Duration.ofSeconds(10));
                    var ended = endingPipeline.execute(endOwner, endContext, activation, ending, history.token(), EMPTY, endHandler);
                    check(ended.kind() == CareerCommandDispatcher.Kind.EXECUTED, "original stadium restoration ends sponsor");
                    check(CareerBridge.onEdt(() -> adapter.stadium(restoredHuman)).equals(oldName), "recorded original stadium name restored");
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(restoredHuman)).equals(expected), "ending preserves money and every ledger field");
                    Object endedStadium = CareerBridge.onEdt(() -> CareerBridge.call(restoredHuman, "ev"));
                    check(Arrays.equals(capacity, CareerBridge.onEdt(() -> (int[]) CareerBridge.call(endedStadium, "dT")))
                            && Arrays.equals(prices, CareerBridge.onEdt(() -> (int[]) CareerBridge.call(endedStadium, "dU"))), "ending preserves stadium capacity/prices");
                    CareerBridge.onEdt(() -> { save.invoke(null, "sponsor-ended"); return null; });
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "sponsor-ended", false)), "original writer/loader persist ended stadium");
                    var endRoots = CareerBridge.onEdt(adapter.finance::roots);
                    Object endHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(endRoots.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.stadium(endHuman)).equals(oldName)
                            && CareerBridge.onEdt(() -> adapter.finance.snapshot(endHuman)).equals(expected), "restored original name and credited money survive reload");
                    var endedHistory = new SponsorActivationStore(new CareerSidecarStore(home.resolve("activations")), handle).read();
                    check(endedHistory.entries().get(0).state() == SponsorActivationStore.State.MEMORY_APPLIED
                            && endedHistory.entries().get(0).ending().orElseThrow().state() == SponsorActivationStore.EndState.ENDED,
                            "ending keeps immutable activation and durable ending audit");
                    try { reopened.claim(sponsor, "a".repeat(64), request, endedHistory.token()); throw new AssertionError("Ended credit replay accepted"); }
                    catch (Failure denied) { check(denied.reason() == Reason.CONFLICT, "ended contract can never receive credit again"); }
                    try { reopened.claimEnd(sponsor, ending, endedHistory.token()); throw new AssertionError("Ending replay accepted"); }
                    catch (Failure denied) { check(denied.reason() == Reason.CONFLICT, "ending is one-shot after reopen"); }
                    UUID nextSponsor = UUID.randomUUID();
                    var nextRevision = new SaveRevision(Profile.digest(game.resolve("sav/sponsor-ended.s22")), Files.size(game.resolve("sav/sponsor-ended.s22")));
                    var nextContext = gate.installContext(handle.career(), handle.binding(), 3, nextRevision, endRoots.primary(), endRoots.secondary());
                    var nextRequest = CareerBridge.onEdt(() -> adapter.finance.request(nextContext, key.mod(), SponsorActivationStore.key(nextSponsor), endHuman,
                            FinanceDelta.Direction.CREDIT_RAW_6, 3_000_000));
                    var nextKey = new Key(handle.career(), handle.binding(), SponsorActivationStore.OWNER);
                    var nextPipeline = new SponsorActivationPipeline(gate,
                            new CareerCommandJournal(new CareerSidecarStore(home.resolve("next/journal")), nextKey),
                            new FinanceRequestStore(new CareerSidecarStore(home.resolve("next/requests")), nextKey),
                            new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("next/witnesses")), nextKey), reopened,
                            adapter.finance::roots, Duration.ofSeconds(10));
                    var nextHandler = CareerBridge.onEdt(() -> adapter.handler(nextRequest, oldName, "Next Sponsor Arena"));
                    var nextResult = nextPipeline.execute(nextSponsor, "d".repeat(64), owner, nextContext, nextRequest,
                            endedHistory.token(), EMPTY, EMPTY, EMPTY, nextHandler, oldName, "Next Sponsor Arena");
                    check(nextResult.kind() == CareerCommandDispatcher.Kind.EXECUTED, "new distinct sponsor activates after completed ending");
                    var nextExpected = FinanceDelta.predict(expected, FinanceDelta.Direction.CREDIT_RAW_6, 3_000_000);
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(endHuman)).equals(nextExpected)
                            && CareerBridge.onEdt(() -> adapter.stadium(endHuman)).equals("Next Sponsor Arena"), "second sponsor credits only its own amount and name");
                    CareerBridge.onEdt(() -> { save.invoke(null, "sponsor-next"); return null; });
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "sponsor-next", false)), "second sponsor original save reloads");
                    var nextRoots = CareerBridge.onEdt(adapter.finance::roots);
                    Object nextHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(nextRoots.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(nextHuman)).equals(nextExpected)
                            && CareerBridge.onEdt(() -> adapter.stadium(nextHuman)).equals("Next Sponsor Arena"), "new sponsor survives original save/reload without repeating first credit");
                    check(reopened.read().entries().get(0).equals(endedHistory.entries().get(0)), "second sponsor never rewrites first activation or ending");
                }
                check(Profile.digest(game.resolve("sav/input.s22")).equals(originalHash), "private baseline input remains unchanged");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { thread.setContextClassLoader(previous); }
        System.out.println("PASS: " + checks + " real sponsor credit/stadium/save/reload checks. Disposable fixture only; no universal save-inclusion claim.");
    }
}
