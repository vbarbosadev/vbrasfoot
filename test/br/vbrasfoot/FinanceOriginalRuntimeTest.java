package br.vbrasfoot;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.swing.JFrame;
import static br.vbrasfoot.CareerCommandDispatcher.*;

/** Original credit/debit+ledger and original save/reload on an explicitly marked fresh private fixture only. */
public final class FinanceOriginalRuntimeTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".finance-test-fixture")) || !Path.of("").toAbsolutePath().toRealPath().equals(game)
                || Files.exists(game.resolve("sav/finance-output.s22")) || Files.exists(home.resolve("finance-journal")))
            throw new IllegalArgumentException("Fresh private fixture/cwd required; no user saves may be targeted");
        String inputHash = Profile.digest(game.resolve("sav/input.s22"));
        Thread current = Thread.currentThread(); ClassLoader previous = current.getContextClassLoader();
        try (LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("runtime"))) {
            current.setContextClassLoader(runtime);
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            Class<?> state = Class.forName("c.a", false, runtime);
            JFrame frame = CareerBridge.onEdt(() -> {
                JFrame value = new JFrame("Owned finance fixture — not game UI"); value.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, value); return value;
            });
            try {
                var load = state.getMethod("c", String.class, boolean.class); var saveMethod = state.getMethod("ae", String.class);
                check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false)), "private original save decoded by original loader");
                var adapter = new LegacyFinanceAdapter(runtime);
                var roots = CareerBridge.onEdt(adapter::roots);
                Object human = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(roots.primary(), "aN")).get(0));
                var before = CareerBridge.onEdt(() -> adapter.snapshot(human));
                int clubCount = new CareerBridge(runtime).snapshot().clubs().size();
                var observer = new SaveFileObserver(); var sourceObservation = observer.observe(game.resolve("sav"), "input.s22");
                var associations = new CareerSaveAssociations(home.resolve("association-metadata"));
                var associated = associations.create(sourceObservation, CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
                check(associated.save().sha256().equals(inputHash) && associations.inspect(associated.handle()).token().equals(associated.token()), "original bytes observed and explicitly associated only inside private fixture");
                var revision = sourceObservation.revision();
                UUID career = associated.handle().career(), binding = associated.handle().binding(); var key = new CareerSidecarStore.Key(career, binding, "br.owned.finance-original");
                var journal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-journal")), key);
                try (var gate = new CareerCommandGate()) {
                    var owner = gate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT, CareerCommandGate.Operation.FINANCE_DEBIT));
                    // Explicit fixture authority only; this is NOT production discovery/qualification of a career.
                    var context = gate.installContext(career, binding, 1, revision, roots.primary(), roots.secondary());
                    var dispatcher = new CareerCommandDispatcher(gate, journal, adapter::roots, Duration.ofSeconds(5));
                    var credit = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, 123));
                    var mismatchedIntent = new CareerCommandJournal.Intent("wrong-direction", CareerCommandGate.Operation.FINANCE_DEBIT, credit.intent("ignored").payloadDigest());
                    check(dispatcher.execute(owner, context, mismatchedIntent, CareerSidecarStore.EMPTY, credit).detail() == Detail.HANDLER_MISMATCH,
                            "original credit adapter cannot be dispatched under debit authority");
                    check(journal.read(revision).receipts().isEmpty(), "mismatched adapter never persists intent");
                    var applied = dispatcher.execute(owner, context, credit.intent("owned-credit"), CareerSidecarStore.EMPTY, credit);
                    check(applied.kind() == Kind.EXECUTED, "journaled original v(int,int) invoked on EDT");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(credit.expected()), "original balance and all14numeric ledger fields match exact predicted credit");
                    check(credit.expected().balance() == before.balance() + 123 && credit.expected().ledger().get("dO") == before.ledger().get("dO") + 123, "original raw category6 changes correct accumulator");
                    check(dispatcher.execute(owner, context, credit.intent("owned-credit"), applied.journal().orElseThrow().token(), credit).kind() == Kind.DENIED,
                            "duplicate original command not invoked twice");
                    var debit = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.DEBIT_RAW_7, 23));
                    var debited = dispatcher.execute(owner, context, debit.intent("owned-debit"), applied.journal().orElseThrow().token(), debit);
                    check(debited.kind() == Kind.EXECUTED, "journaled original w(int,int) invoked");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(debit.expected()) && debit.expected().balance() == before.balance() + 100,
                            "original debit preserves exact ledger and net100balance");
                    var stale = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, 11));
                    var changed = CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, 5));
                    var changedResult = dispatcher.execute(owner, context, changed.intent("owned-change"), debited.journal().orElseThrow().token(), changed);
                    check(changedResult.kind() == Kind.EXECUTED, "owned valid change before stale plan");
                    var staleResult = dispatcher.execute(owner, context, stale.intent("owned-stale"), changedResult.journal().orElseThrow().token(), stale);
                    check(staleResult.kind() == Kind.NO_EFFECT && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()), "stale financial expectation rejected before original mutation");
                    check(journal.read(revision).receipts().stream().filter(r -> r.state() == CareerCommandJournal.State.APPLIED).count() == 3,
                            "journal records exactly3original effects,not stale or duplicate");
                    boolean negative = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(human, FinanceDelta.Direction.CREDIT_RAW_6, -1)); }
                    catch (IllegalArgumentException expected) { negative = true; }
                    check(negative && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()), "negative credit refused without touching original graph");
                    boolean nonEdt = false; try { adapter.snapshot(human); } catch (IllegalStateException expected) { nonEdt = true; }
                    check(nonEdt, "original finance reads refuse caller outside EDT");
                    var beforeSave = CareerBridge.onEdt(() -> adapter.snapshot(human));
                    var saveFence = gate.beginSave();
                    try { CareerBridge.onEdt(() -> { saveMethod.invoke(null, "finance-output"); return null; }); }
                    finally { gate.endSave(saveFence); } // Even writer failure must not restore the old context.
                    check(Files.isRegularFile(game.resolve("sav/finance-output.s22")) && Files.mismatch(game.resolve("sav/finance-output.s22"), game.resolve("sav/finance-output.sbck")) == -1,
                            "original writer produces private output and matching backup");
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "finance-output", false)), "original loader reloads private financially modified save");
                    var restoredRoots = CareerBridge.onEdt(adapter::roots);
                    Object restoredHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(restoredRoots.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.snapshot(restoredHuman)).equals(beforeSave), "exact balance and all ledger fields survive original write/reload");
                    check(new CareerBridge(runtime).snapshot().clubs().size() == clubCount, "club count retained after original finance roundtrip");
                    var outputObservation = observer.observe(game.resolve("sav"), "finance-output.s22");
                    check(!outputObservation.revision().equals(sourceObservation.revision())
                            && associations.assess(associated, outputObservation) != CareerSaveAssociations.Match.EXACT_OBSERVATION,
                            "newly written original save is not silently the old file/revision association");
                    boolean needsReconciliation = false;
                    try { associations.acceptRevision(associated.handle(), associated.token(), outputObservation, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION); }
                    catch (CareerSidecarStore.Failure expected) { needsReconciliation = expected.reason() == CareerSidecarStore.Reason.RECONCILIATION_REQUIRED; }
                    check(needsReconciliation && associations.inspect(associated.handle()).token().equals(associated.token()), "save-as with new name and bytes requires separate policy, cannot silently move association");
                    boolean journalMismatch = false;
                    try { journal.read(outputObservation.revision()); }
                    catch (CareerSidecarStore.Failure expected) { journalMismatch = expected.reason() == CareerSidecarStore.Reason.SAVE_REVISION_MISMATCH; }
                    check(journalMismatch, "journal not silently reanchored to financially modified output save");
                    var recovery = journal.inspectRecovery(revision, outputObservation.revision());
                    check(recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS && recovery.revisionChanged(),
                            "original roundtrip still classified as historical effects, not automatic journal reconciliation");
                    check(journal.quarantinePending(revision, recovery.snapshot().token()).token().equals(recovery.snapshot().token()),
                            "startup quarantine cannot rewrite terminal original financial receipts");
                    var oldPlanResult = dispatcher.execute(owner, context, changed.intent("old-roots"), journal.read(revision).token(), changed);
                    check(oldPlanResult.kind() == Kind.DENIED && oldPlanResult.receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT, "save window invalidates old context; reloaded roots never automatically authorize command");
                }
                check(Profile.digest(game.resolve("sav/input.s22")).equals(inputHash), "private input save untouched");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { current.setContextClassLoader(previous); }
        System.out.println("PASS: " + checks + " original finance/ledger dispatch roundtrip checks; private fixture only, not standalone EXE reopening or production context acceptance.");
    }
}
