package br.vbrasfoot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static br.vbrasfoot.CareerCommandGate.*;

/** Session-only authority tests with owned objects/effects; no game handler or financial operation. */
public final class CareerCommandGateSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static Request request(Context context, String key) { return new Request(context.token(), key, Operation.FINANCE_CREDIT, "a".repeat(64)); }
    private static Context context(CareerCommandGate gate, Object primary, Object secondary) {
        return gate.installContext(UUID.randomUUID(), UUID.randomUUID(), 4, new CareerSidecarStore.SaveRevision("b".repeat(64), 20), primary, secondary);
    }
    private static Start start(CareerCommandGate gate, Permit permit, Object primary, Object secondary) throws Exception {
        var result = new AtomicReference<Start>(); javax.swing.SwingUtilities.invokeAndWait(() -> result.set(gate.begin(permit, primary, secondary))); return result.get();
    }
    public static void main(String[] args) throws Exception {
        Object primary = new Object(), secondary = new Object(); var effects = new AtomicInteger();
        try (var gate = new CareerCommandGate()) {
            var initial = context(gate, primary, secondary);
            var handle = new CareerSaveAssociations.Handle(initial.career(), initial.binding());
            var activation = SponsorActivationStoreSelfTest.request(handle, UUID.randomUUID(), initial.save());
            var owner = gate.owner(activation.owner(), Set.of(Operation.FINANCE_CREDIT));
            var permit = gate.reserve(owner, new Request(initial.token(), activation.key(), Operation.FINANCE_CREDIT, activation.digest()), primary, secondary).permit().orElseThrow();
            start(gate, permit, primary, secondary); gate.finish(permit, Status.APPLIED, primary, secondary);
            var preparation = gate.prepareSponsorEnd(activation);
            var endContext = gate.installPrepared(preparation, initial.career(), initial.binding(), 1, initial.save(), primary, secondary);
            check(gate.currentContext(endContext) && gate.receipts().size() == 1, "ending can follow exact applied sponsor without dropping financial receipt");
            check(gate.reserve(owner, new Request(endContext.token(), activation.key(), Operation.FINANCE_CREDIT, activation.digest()), primary, secondary).permit().isEmpty(),
                    "ending preparation never reissues credit permit");
            var endOwner = gate.owner(SponsorEndPipeline.OWNER, Set.of(Operation.SPONSOR_END));
            var endedIntent = new CareerCommandJournal.Intent("sponsor_end_test", Operation.SPONSOR_END, "c".repeat(64));
            var endPermit = gate.reserve(endOwner, new Request(endContext.token(), endedIntent.key(), endedIntent.operation(), endedIntent.payloadDigest()), primary, secondary).permit().orElseThrow();
            start(gate, endPermit, primary, secondary); gate.finish(endPermit, Status.APPLIED, primary, secondary);
            var nextPreparation = gate.prepareSponsorContinuation(Set.of(), Set.of(activation.intent(), endedIntent));
            var nextContext = gate.installPrepared(nextPreparation, initial.career(), initial.binding(), 1, initial.save(), primary, secondary);
            check(gate.currentContext(nextContext) && gate.receipts().size() == 2, "new sponsor preparation accounts for exact completed credit and ending without clearing receipts");
            var other = gate.owner("br.owned.unknown", Set.of(Operation.FINANCE_CREDIT));
            var uncertain = gate.reserve(other, request(nextContext, "unknown"), primary, secondary).permit().orElseThrow();
            start(gate, uncertain, primary, secondary); gate.finish(uncertain, Status.OUTCOME_UNKNOWN, primary, secondary);
            boolean blocked = false; try { gate.prepareSponsorEnd(activation); } catch (IllegalStateException expected) { blocked = true; }
            check(blocked, "uncertain operation is never bypassed by ending preparation");
        }
        try (var gate = new CareerCommandGate()) {
            var context = context(gate, primary, secondary);
            gate.retainCompound(context);
            for (var kind : IoKind.values()) {
                boolean blocked = false;
                try { gate.beginOriginalIo(kind); } catch (IllegalStateException expected) { blocked = true; }
                check(blocked, "compound claim/commit excludes original I/O " + kind);
            }
            var owner = gate.owner("br.owned.compound", Set.of(Operation.FINANCE_CREDIT));
            var permit = gate.reserve(owner, request(context, "compound"), primary, secondary).permit().orElseThrow();
            check(start(gate, permit, primary, secondary).invocationGranted(), "compound fence permits its financial dispatch");
            gate.finish(permit, Status.APPLIED, primary, secondary);
            boolean blocked = false;
            try { gate.beginSave(); } catch (IllegalStateException expected) { blocked = true; }
            check(blocked, "original save remains excluded after handler returns until sponsor terminal commit");
            gate.invalidate(); gate.releaseCompound(context);
            var save = gate.beginSave(); gate.endSave(save);
            check(!gate.contextInstalled(), "compound cleanup allows original save without restoring authority");
        }
        try (var gate = new CareerCommandGate(); var foreign = new CareerCommandGate()) {
            Owner owner = gate.owner("br.owned.finance", Set.of(Operation.FINANCE_CREDIT));
            Owner denied = gate.owner("br.owned.readonly", Set.of());
            var context = context(gate, primary, secondary); var request = request(context, "payment-1");
            var refused = gate.reserve(denied, request, primary, secondary);
            check(refused.receipt().reason() == Reason.PERMISSION && refused.permit().isEmpty(), "permission refused before reservation");
            var reservation = gate.reserve(owner, request, primary, secondary); Permit permit = reservation.permit().orElseThrow();
            check(reservation.receipt().status() == Status.RESERVED && gate.receipts().size() == 1, "one initial reservation");
            check(gate.reserve(owner, request, primary, secondary).permit().isEmpty(), "duplicate reservation cannot mint second permit");
            check(gate.reserve(owner, new Request(context.token(), request.key(), request.operation(), "c".repeat(64)), primary, secondary).receipt().reason() == Reason.REQUEST_CONFLICT,
                    "same owner/key with changed payload refused");
            check(gate.reserve(owner, request(context, "payment-2"), primary, secondary).receipt().reason() == Reason.BUSY, "global exclusive command slot");
            if (start(gate, permit, primary, secondary).invocationGranted()) effects.incrementAndGet();
            if (start(gate, permit, primary, secondary).invocationGranted()) effects.incrementAndGet();
            check(effects.get() == 1, "same permit begin twice grants effect only once");
            check(gate.cancel(permit).status() == Status.RUNNING, "cancellation cannot claim no effect after start");
            check(gate.finish(permit, Status.APPLIED, primary, secondary).status() == Status.APPLIED, "trusted owned handler completion recorded");
            check(gate.finish(permit, Status.OUTCOME_UNKNOWN, primary, secondary).status() == Status.APPLIED, "late completion cannot overwrite terminal receipt");
            check(gate.reserve(owner, request, primary, secondary).receipt().status() == Status.APPLIED && gate.reserve(owner, request, primary, secondary).permit().isEmpty(), "applied idempotent result, no replay");
            var uncertain = gate.reserve(owner, request(context, "payment-uncertain"), primary, secondary).permit().orElseThrow();
            if (start(gate, uncertain, primary, secondary).invocationGranted()) effects.incrementAndGet(); // Owned handler mutated then failed.
            check(gate.finish(uncertain, Status.OUTCOME_UNKNOWN, primary, secondary).reason() == Reason.EXECUTION_UNCERTAIN, "exception after start is uncertain, not rejected");
            check(gate.reserve(owner, request(context, "payment-uncertain"), primary, secondary).permit().isEmpty(), "uncertain result cannot reapply on retry");
            check(gate.reserve(owner, request(context, "after-uncertain"), primary, secondary).receipt().reason() == Reason.NO_CONTEXT, "uncertain effect suspends context pending trusted reconciliation");
            context = context(gate, primary, secondary); // Owned explicit new verification, not an automatic replay.
            var wrongThread = gate.reserve(owner, request(context, "off-edt"), primary, secondary).permit().orElseThrow();
            check(!gate.begin(wrongThread, primary, secondary).invocationGranted() && gate.receipts().get(2).reason() == Reason.WRONG_THREAD, "begin outside EDT refused");
            var wrongRoots = gate.reserve(owner, request(context, "wrong-roots"), primary, secondary).permit().orElseThrow();
            check(!start(gate, wrongRoots, new Object(), secondary).invocationGranted(), "root identity rechecked immediately before original effect");
            var cancelled = gate.reserve(owner, request(context, "cancelled"), primary, secondary).permit().orElseThrow();
            check(gate.cancel(cancelled).reason() == Reason.NO_EFFECT_CONFIRMED && !start(gate, cancelled, primary, secondary).invocationGranted(), "pre-start cancellation releases without effect");
            var stale = gate.reserve(owner, request(context, "old-context"), primary, secondary).permit().orElseThrow();
            Context replacement = context(gate, primary, secondary);
            check(!start(gate, stale, primary, secondary).invocationGranted(), "context replacement revokes reserved permit even same roots");
            check(gate.reserve(owner, request(context, "new-on-old-context"), primary, secondary).receipt().reason() == Reason.STALE_CONTEXT, "old context token refused");
            check(gate.reserve(owner, request, primary, secondary).receipt().status() == Status.APPLIED, "historical receipt retained after context switch");
            var inFlight = gate.reserve(owner, request(replacement, "in-flight-old-context"), primary, secondary).permit().orElseThrow();
            check(start(gate, inFlight, primary, secondary).invocationGranted(), "owned in-flight transition");
            gate.invalidate();
            check(gate.finish(inFlight, Status.APPLIED, primary, secondary).status() == Status.OUTCOME_UNKNOWN, "late effect after invalidation never reported safely applied");
            check(gate.reserve(owner, request(replacement, "no-context"), primary, secondary).receipt().reason() == Reason.NO_CONTEXT, "no verified context denied");
            Context after = context(gate, primary, secondary);
            Owner other = gate.owner("br.owned.other", Set.of(Operation.FINANCE_CREDIT));
            var isolated = gate.reserve(other, request(after, request.key()), primary, secondary).permit().orElseThrow();
            check(gate.cancel(isolated).status() == Status.REJECTED, "different owner namespace does not collide by command key");
            Owner foreignOwner = foreign.owner("br.owned.foreign", Set.of(Operation.FINANCE_CREDIT)); boolean refusedForeign = false;
            try { gate.reserve(foreignOwner, request(after, "foreign-owner"), primary, secondary); } catch (IllegalArgumentException expected) { refusedForeign = true; }
            check(refusedForeign, "owner authority from another gate refused");
            var foreignContext = context(foreign, primary, secondary);
            var foreignPermit = foreign.reserve(foreignOwner, request(foreignContext, "foreign-permit"), primary, secondary).permit().orElseThrow();
            refusedForeign = false; try { gate.begin(foreignPermit, primary, secondary); } catch (IllegalArgumentException expected) { refusedForeign = true; }
            check(refusedForeign, "permit from another gate refused");
            var revocable = gate.reserve(owner, request(after, "revoked-before-start"), primary, secondary).permit().orElseThrow(); gate.revoke(owner);
            check(!start(gate, revocable, primary, secondary).invocationGranted() && gate.reserve(owner, request(after, "after-revoke"), primary, secondary).receipt().reason() == Reason.OWNER_REVOKED, "revoked owner cannot execute or reserve");
            var runningOwner = gate.owner("br.owned.running", Set.of(Operation.FINANCE_CREDIT));
            var running = gate.reserve(runningOwner, request(after, "running-revoke"), primary, secondary).permit().orElseThrow(); start(gate, running, primary, secondary); gate.revoke(runningOwner);
            check(gate.finish(running, Status.APPLIED, primary, secondary).status() == Status.OUTCOME_UNKNOWN, "revoked running owner has uncertain effect, no invented rollback");
            var retained = gate.receipts(); gate.close(); gate.close();
            check(retained.size() == gate.receipts().size() && gate.reserve(other, request(after, "after-close"), primary, secondary).receipt().reason() == Reason.CLOSED, "shutdown idempotent and denies future reservations");
            check(effects.get() == 2, "only two explicitly granted owned effects ever invoked");
        }
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner("br.owned.capacity", Set.of(Operation.FINANCE_CREDIT)); var context = context(gate, primary, secondary);
            for (int i = 0; i < 256; i++) {
                var permit = gate.reserve(owner, request(context, "command-" + i), primary, secondary).permit().orElseThrow(); gate.cancel(permit);
            }
            check(gate.reserve(owner, request(context, "command-257"), primary, secondary).receipt().reason() == Reason.CAPACITY, "256 receipts retained, no eviction to permit duplicate effects");
            check(gate.reserve(owner, request(context, "command-0"), primary, secondary).permit().isEmpty() && gate.receipts().size() == 256, "old rejected reservation retained within capacity");
        }
        try (var gate = new CareerCommandGate()) {
            var owner = gate.owner("br.owned.race", Set.of(Operation.FINANCE_CREDIT)); var context = context(gate, primary, secondary);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var start = new CountDownLatch(1); var futures = new ArrayList<Future<Reservation>>();
                for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> { start.await(); return gate.reserve(owner, request(context, "same-key"), primary, secondary); }));
                start.countDown(); int permits = 0; Permit granted = null;
                for (var future : futures) { var result = future.get(5, TimeUnit.SECONDS); if (result.permit().isPresent()) { permits++; granted = result.permit().orElseThrow(); } }
                check(permits == 1 && gate.receipts().size() == 1, "concurrent duplicate submissions mint exactly one permit"); gate.cancel(granted);
            } finally { executor.shutdownNow(); }
        }
        System.out.println("PASS: " + checks + " session command gate checks; owned effects only, no durable ledger or game command API.");
    }
}
