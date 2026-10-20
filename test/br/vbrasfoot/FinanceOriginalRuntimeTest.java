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
                || Files.exists(game.resolve("sav/finance-output.s22")) || Files.exists(home.resolve("finance-journal"))
                || Files.exists(home.resolve("finance-requests")) || Files.exists(home.resolve("finance-execution-witnesses")))
            throw new IllegalArgumentException("Fresh private fixture/cwd required; no user saves may be targeted");
        String inputHash = Profile.digest(game.resolve("sav/input.s22"));
        Thread current = Thread.currentThread(); ClassLoader previous = current.getContextClassLoader();
        boolean probeWriter = args.length == 3 && args[2].equals("--probe-writer");
        var writerLifecycle = new CareerLifecycle();
        try (LegacyRuntime runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("runtime"), probeWriter);
             AutoCloseable writerGuard = probeWriter ? HookProbe.attachLifecycle(writerLifecycle) : () -> { }) {
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
                    var requestStore = new FinanceRequestStore(new CareerSidecarStore(home.resolve("finance-requests")), key);
                    var witnessStore = new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("finance-execution-witnesses")), key);
                    var creditRequest = CareerBridge.onEdt(() -> adapter.request(context, key.mod(), "owned-credit", human, FinanceDelta.Direction.CREDIT_RAW_6, 123));
                    boolean duplicateRefused = CareerBridge.onEdt(() -> {
                        @SuppressWarnings("unchecked") var clubs = (List<Object>) CareerBridge.call(roots.primary(), "P");
                        int size = clubs.size(); clubs.add(human); // Owned graph only, removed before any financial effect/save.
                        try { adapter.prepare(creditRequest); return false; }
                        catch (IllegalStateException expected) { return true; }
                        finally { clubs.remove(size); }
                    });
                    check(duplicateRefused && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(before), "repeated compound locator refuses plan in owned graph without financial effect");
                    Path requestFile = Files.write(home.resolve("owned-credit.request"), creditRequest.encode());
                    var decodedRequest = FinanceRequest.decode(Files.readAllBytes(requestFile));
                    var credit = CareerBridge.onEdt(() -> adapter.prepare(decodedRequest));
                    var reconstructed = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(Files.readAllBytes(requestFile))));
                    check(credit.intent("owned-credit").equals(reconstructed.intent("owned-credit")), "separate weak plans reconstruct same versioned durable intent from stored request");
                    check(!credit.acceptsContext(new CareerCommandGate.Context(UUID.randomUUID(), career, UUID.randomUUID(), 1, revision), key.mod())
                                    && !credit.acceptsContext(context, "br.other.owner"), "persistent handler refuses wrong binding or owner");
                    boolean replacedKey = false;
                    try { credit.intent("other-key"); } catch (IllegalArgumentException expected) { replacedKey = true; }
                    check(replacedKey, "durable operation key cannot be substituted by plan caller");
                    var mismatchedIntent = new CareerCommandJournal.Intent("owned-credit", CareerCommandGate.Operation.FINANCE_DEBIT, creditRequest.digest());
                    check(dispatcher.execute(owner, context, mismatchedIntent, CareerSidecarStore.EMPTY, credit).detail() == Detail.HANDLER_MISMATCH,
                            "original credit adapter cannot be dispatched under debit authority");
                    check(journal.read(revision).receipts().isEmpty(), "mismatched adapter never persists intent");
                    var wrongOwnerRequest = new FinanceRequest(career, binding, revision, "br.other.owner", "owned-credit", creditRequest.club(), creditRequest.direction(), creditRequest.amount(), creditRequest.before());
                    var wrongOwnerPlan = CareerBridge.onEdt(() -> adapter.prepare(wrongOwnerRequest));
                    check(dispatcher.execute(owner, context, wrongOwnerRequest.intent(), CareerSidecarStore.EMPTY, wrongOwnerPlan).detail() == Detail.HANDLER_MISMATCH
                                    && journal.read(revision).receipts().isEmpty(), "dispatcher rejects foreign durable owner before journal or effect");
                    for (var wrongScope : List.of(
                            new FinanceRequest(UUID.randomUUID(), binding, revision, key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before),
                            new FinanceRequest(career, UUID.randomUUID(), revision, key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before),
                            new FinanceRequest(career, binding, new CareerSidecarStore.SaveRevision("0".repeat(64), revision.size()), key.mod(), creditRequest.key(), creditRequest.club(), creditRequest.direction(), creditRequest.amount(), before))) {
                        var wrongScopePlan = CareerBridge.onEdt(() -> adapter.prepare(wrongScope));
                        check(dispatcher.execute(owner, context, wrongScope.intent(), CareerSidecarStore.EMPTY, wrongScopePlan).detail() == Detail.HANDLER_MISMATCH
                                        && journal.read(revision).receipts().isEmpty(), "dispatcher refuses durable scope mismatch before any journal intent");
                    }
                    var wrongLocator = new FinanceRequest(career, binding, revision, key.mod(), creditRequest.key(),
                            new FinanceRequest.Club(creditRequest.club().index(), creditRequest.club().id(), "Not the original name"),
                            creditRequest.direction(), creditRequest.amount(), before);
                    boolean wrongTarget = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(wrongLocator)); } catch (IllegalStateException expected) { wrongTarget = true; }
                    check(wrongTarget && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(before), "exact original locator mismatch refuses plan without financial mutation");
                    check(dispatcher.execute(owner, context, creditRequest.intent(), CareerSidecarStore.EMPTY, credit).detail() == Detail.REQUEST_REQUIRED
                                    && journal.read(revision).receipts().isEmpty(), "persistent financial plan cannot bypass payload storage");
                    var storedCredit = dispatcher.executeWitnessedFinance(owner, context, creditRequest, CareerSidecarStore.EMPTY,
                            requestStore, CareerSidecarStore.EMPTY, witnessStore, CareerSidecarStore.EMPTY, credit);
                    var applied = storedCredit.dispatch();
                    check(requestStore.read(revision).requests().equals(List.of(creditRequest)), "complete credit payload committed before original effect");
                    check(applied.kind() == Kind.EXECUTED, "journaled original v(int,int) invoked on EDT");
                    var creditWitness = witnessStore.read(revision);
                    check(creditWitness.witnesses().get(0).operationId().equals(applied.journal().orElseThrow().receipts().get(0).operationId())
                                    && creditWitness.witnesses().get(0).state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED,
                            "original applied credit has independent durable causal execution evidence with exact operation UUID");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(credit.expected()), "original balance and all14numeric ledger fields match exact predicted credit");
                    check(credit.expected().balance() == before.balance() + 123 && credit.expected().ledger().get("dO") == before.ledger().get("dO") + 123, "original raw category6 changes correct accumulator");
                    boolean staleRequest = false;
                    try { CareerBridge.onEdt(() -> adapter.prepare(decodedRequest)); } catch (IllegalStateException expected) { staleRequest = true; }
                    check(staleRequest && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(credit.expected()), "reconstructing request after financial state changed cannot refresh its precondition");
                    check(dispatcher.executeStoredFinance(owner, context, creditRequest, applied.journal().orElseThrow().token(),
                                    requestStore, storedCredit.requests().token(), credit).dispatch().kind() == Kind.DENIED,
                            "duplicate original command not invoked twice");
                    var debitRequest = CareerBridge.onEdt(() -> adapter.request(context, key.mod(), "owned-debit", human, FinanceDelta.Direction.DEBIT_RAW_7, 23));
                    var debit = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(debitRequest.encode())));
                    check(debit.intent("owned-debit").equals(debitRequest.intent()), "raw debit also uses canonical durable request identity");
                    var storedDebit = dispatcher.executeWitnessedFinance(owner, context, debitRequest, applied.journal().orElseThrow().token(),
                            requestStore, storedCredit.requests().token(), witnessStore, creditWitness.token(), debit);
                    var debited = storedDebit.dispatch();
                    check(requestStore.read(revision).requests().equals(List.of(creditRequest, debitRequest)), "stored immutable credit and debit payloads retain order and exact scope");
                    check(debited.kind() == Kind.EXECUTED, "journaled original w(int,int) invoked");
                    check(witnessStore.read(revision).witnesses().size() == 2 && witnessStore.read(revision).witnesses().stream()
                                    .allMatch(value -> value.state() == FinanceExecutionWitnessStore.State.MEMORY_APPLIED), "credit and debit execution facts persist without declaring save inclusion");
                    check(CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(debit.expected()) && debit.expected().balance() == before.balance() + 100,
                            "original debit preserves exact ledger and net100balance");
                    if (probeWriter) {
                        var checkpoint = debit.executionMutationEnd().orElseThrow();
                        check(checkpoint.definingClassesObserved() && checkpoint.healthy() && checkpoint.active() == 0,
                                "both fingerprinted defining classes expose quiescent direct-write observation after original effect");
                        check(HookProbe.financeMutationSnapshot(runtime).stableSince(checkpoint), "unchanged diagnostic checkpoint remains stable before further original writes");
                        CareerBridge.onEdt(() -> { human.getClass().getMethod("e", long.class).invoke(human, debit.expected().balance()); return null; });
                        check(!HookProbe.financeMutationSnapshot(runtime).stableSince(checkpoint)
                                        && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(debit.expected()),
                                "original equal-value balance setter invalidates causal checkpoint even though balance and full ledger remain equal");
                    }
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
                    var noEffectKey = new CareerSidecarStore.Key(career, binding, "br.owned.finance-no-effect");
                    var noEffectJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-no-effect-journal")), noEffectKey);
                    var noEffectOwner = gate.owner(noEffectKey.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                    var noEffectDispatcher = new CareerCommandDispatcher(gate, noEffectJournal, adapter::roots, Duration.ofSeconds(5));
                    var noEffectResult = noEffectDispatcher.execute(noEffectOwner, context, stale.intent("no-effect-only"), CareerSidecarStore.EMPTY, stale);
                    check(noEffectResult.kind() == Kind.NO_EFFECT && CareerBridge.onEdt(() -> adapter.snapshot(human)).equals(changed.expected()),
                            "original adapter rejects consumed/stale plan with no mutation in separate no-effect journal");
                    var beforeSave = CareerBridge.onEdt(() -> adapter.snapshot(human));
                    var preWriterMutations = probeWriter ? HookProbe.financeMutationSnapshot(runtime) : null;
                    var saveFence = gate.beginSave();
                    try { CareerBridge.onEdt(() -> { saveMethod.invoke(null, "finance-output"); return null; }); }
                    finally { gate.endSave(saveFence); } // Even writer failure must not restore the old context.
                    check(Files.isRegularFile(game.resolve("sav/finance-output.s22")) && Files.mismatch(game.resolve("sav/finance-output.s22"), game.resolve("sav/finance-output.sbck")) == -1,
                            "original writer produces private output and matching backup");
                    var writtenStream = probeWriter ? new WrittenSaveVerifier(runtime).verify(observer.observe(game.resolve("sav"), "finance-output.s22")) : null;
                    if (probeWriter) {
                        check(writtenStream.bytes().sha256().equals(Profile.digest(game.resolve("sav/finance-output.s22"))), "post-financial-effect output is linked to actual original written stream, not balance inference");
                        var bindingWritten = HookProbe.writtenSaveProvenance().binding().orElseThrow();
                        check(bindingWritten.financeBefore().orElseThrow().definingClassesObserved() && bindingWritten.financeAfter().orElseThrow().definingClassesObserved(),
                                "serializer boundary captures checkpoints from the same instrumented runtime as original roots");
                        check(bindingWritten.noObservedFinanceWriteDuringSerialization(), "private original serializer observed no selected direct financial writes during the two root writes; not full mutation coverage");
                        var mutationAssessment = FinanceMutationAssessment.assess(changed.executionMutationEnd(), bindingWritten);
                        check(mutationAssessment.executionToSerializer() != FinanceMutationAssessment.Status.UNAVAILABLE
                                && mutationAssessment.serializerWindow() == FinanceMutationAssessment.Status.NO_OBSERVED_DIRECT_WRITE,
                                "original financial effect and serializer checkpoints can be compared without treating sampled ledger equality as causal proof");
                        check(preWriterMutations != null && preWriterMutations.healthy(), "original pre-writer mutation checkpoint remains diagnostic, not a save grant");
                    }
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "finance-output", false)), "original loader reloads private financially modified save");
                    var restoredRoots = CareerBridge.onEdt(adapter::roots);
                    Object restoredHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(restoredRoots.primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.snapshot(restoredHuman)).equals(beforeSave), "exact balance and all ledger fields survive original write/reload");
                    check(new CareerBridge(runtime).snapshot().clubs().size() == clubCount, "club count retained after original finance roundtrip");
                    var outputObservation = observer.observe(game.resolve("sav"), "finance-output.s22");
                    var reopenedWitnesses = new FinanceExecutionWitnessStore(new CareerSidecarStore(home.resolve("finance-execution-witnesses")), key);
                    check(reopenedWitnesses.read(revision).equals(witnessStore.read(revision)), "execution evidence reopens unchanged after original writer/reload");
                    boolean witnessRevisionRefused = false;
                    try { reopenedWitnesses.read(outputObservation.revision()); } catch (CareerSidecarStore.Failure expected) { witnessRevisionRefused = expected.reason() == CareerSidecarStore.Reason.SAVE_REVISION_MISMATCH; }
                    check(witnessRevisionRefused, "memory applied evidence never migrates to output save revision by balance/hash comparison");
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
                    boolean appliedMigrationRefused = false;
                    try { journal.reanchorNoEffects(revision, outputObservation.revision(), journal.read(revision).token()); }
                    catch (CareerSidecarStore.Failure expected) { appliedMigrationRefused = expected.reason() == CareerSidecarStore.Reason.RECONCILIATION_REQUIRED; }
                    check(appliedMigrationRefused, "original APPLIED financial effects remain historical; cannot use no-effect migration");
                    var preservedNoEffects = noEffectJournal.reanchorNoEffects(revision, outputObservation.revision(), noEffectResult.journal().orElseThrow().token());
                    check(preservedNoEffects.receipts().equals(noEffectResult.journal().orElseThrow().receipts())
                            && noEffectJournal.read(outputObservation.revision()).token().equals(preservedNoEffects.token()),
                            "only verified no-effect receipts reanchor across original save/reload without replay or graph write");
                    var recovery = journal.inspectRecovery(revision, outputObservation.revision());
                    check(recovery.status() == CareerCommandJournal.RecoveryStatus.HISTORICAL_EFFECTS && recovery.revisionChanged(),
                            "original roundtrip still classified as historical effects, not automatic journal reconciliation");
                    if (probeWriter) {
                        var decodedOutput = new LoadedSaveVerifier(runtime).verify(outputObservation);
                        var assessment = FinanceSaveAssessment.assess(key, requestStore.inspectRecordedRecovery(outputObservation.revision()), recovery,
                                decodedOutput, Optional.of(writtenStream));
                        check(assessment.relation() == FinanceSaveAssessment.SaveRelation.EXACT_WRITTEN_BYTES_RELOADED && assessment.blocksContext(),
                                "original writer/decoder bytes correlate but do not clear historical operation quarantine");
                        check(assessment.operations().stream().filter(operation -> operation.receipt().orElse(null) == CareerCommandJournal.State.APPLIED)
                                        .allMatch(operation -> operation.inclusion() == FinanceSaveAssessment.Inclusion.UNPROVEN && operation.operationId().isPresent()),
                                "per-operation APPLIED UUIDs retain unproven inclusion even with matching original output/reload bytes");
                    }
                    check(journal.quarantinePending(revision, recovery.snapshot().token()).token().equals(recovery.snapshot().token()),
                            "startup quarantine cannot rewrite terminal original financial receipts");
                    var oldPlanResult = dispatcher.execute(owner, context, changed.intent("old-roots"), journal.read(revision).token(), changed);
                    check(oldPlanResult.kind() == Kind.DENIED && oldPlanResult.receipt().reason() == CareerCommandGate.Reason.NO_CONTEXT, "save window invalidates old context; reloaded roots never automatically authorize command");
                    check(!decodedRequest.matches(new CareerCommandGate.Context(UUID.randomUUID(), career, binding, 2, outputObservation.revision()), key.mod()),
                            "persistent request cannot cross into output revision even after original write/reload");
                    check(CareerBridge.onEdt(() -> (boolean) load.invoke(null, "input", false)), "original input can be decoded again only inside fixture");
                    var inputRoots = CareerBridge.onEdt(adapter::roots);
                    Object inputHuman = CareerBridge.onEdt(() -> ((List<?>) CareerBridge.call(inputRoots.primary(), "aN")).get(0));
                    var restartPlan = CareerBridge.onEdt(() -> adapter.prepare(FinanceRequest.decode(Files.readAllBytes(requestFile))));
                    check(restartPlan.intent("owned-credit").equals(creditRequest.intent()), "request identity survives original graph reload without session nonce");
                    try (var restartGate = new CareerCommandGate()) {
                        var restartOwner = restartGate.owner(key.mod(), Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
                        var restartContext = restartGate.installContext(career, binding, 2, revision, inputRoots.primary(), inputRoots.secondary());
                        var restartJournal = new CareerCommandJournal(new CareerSidecarStore(home.resolve("finance-journal")), key);
                        var reopenedRequests = new FinanceRequestStore(new CareerSidecarStore(home.resolve("finance-requests")), key);
                        var retry = new CareerCommandDispatcher(restartGate, restartJournal, adapter::roots, Duration.ofSeconds(5))
                                .executeWitnessedFinance(restartOwner, restartContext, decodedRequest, restartJournal.read(revision).token(),
                                        reopenedRequests, reopenedRequests.read(revision).token(), reopenedWitnesses, reopenedWitnesses.read(revision).token(), restartPlan).dispatch();
                        check(retry.kind() == Kind.RECORDED_ONLY && CareerBridge.onEdt(() -> adapter.snapshot(inputHuman)).equals(before),
                                "reopened journal and fresh gate never replay historical APPLIED request on restored input graph");
                    }
                }
                check(Profile.digest(game.resolve("sav/input.s22")).equals(inputHash), "private input save untouched");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { current.setContextClassLoader(previous); }
        System.out.println("PASS: " + checks + " original finance/ledger dispatch roundtrip checks; private fixture only, not standalone EXE reopening or production context acceptance.");
    }
}
