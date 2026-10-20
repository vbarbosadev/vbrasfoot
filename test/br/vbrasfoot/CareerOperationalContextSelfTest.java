package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import static br.vbrasfoot.CareerSidecarStore.*;
import static br.vbrasfoot.CareerAssociationCoordinator.*;

/** Synthetic init markers and roots. Does not claim original global readiness or authorize public mods. */
public final class CareerOperationalContextSelfTest {
    private static int checks;
    private interface ActionCall { void run() throws Exception; }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void rejects(Reason reason, ActionCall action) throws Exception {
        try { action.run(); } catch (Failure expected) { check(expected.reason() == reason, "expected " + reason + " got " + expected.reason()); return; }
        throw new AssertionError("Expected " + reason);
    }
    private static final class Harness implements AutoCloseable {
        final Object primary = new Object(), secondary = new Object();
        final CareerLifecycle lifecycle = new CareerLifecycle();
        final CareerCommandGate gate = new CareerCommandGate();
        final CareerIoInterlock io = new CareerIoInterlock(gate);
        final CareerOperationalContext operational = new CareerOperationalContext(gate);
        final AutoCloseable guarded, attached;
        final Path root, saves, source;
        final SaveFileObserver observer = new SaveFileObserver();
        final LoadedSaveVerifier verifier;
        final CareerSaveAssociations associations;
        final CareerSidecarStore journals, requests, executions;
        final CareerAssociationCoordinator coordinator;
        final String owner = "br.owned.operational";
        final Map<String, FinanceExecutionWitnessStore> executionInstances = new HashMap<>();
        CareerSaveAssociations.Handle handle;
        Harness(Path parent) throws Exception {
            root = Files.createTempDirectory(parent, "operational-context-").toRealPath(); saves = Files.createDirectory(root.resolve("sav"));
            source = Files.write(saves.resolve("owned.s22"), new byte[] {1, 2, 3});
            verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
            associations = new CareerSaveAssociations(root.resolve("associations")); journals = new CareerSidecarStore(root.resolve("journals"));
            requests = new CareerSidecarStore(root.resolve("finance-requests"));
            executions = new CareerSidecarStore(root.resolve("finance-execution-witnesses"));
            coordinator = new CareerAssociationCoordinator(saves, associations, journals, requests, executions, observer, verifier, List.of(owner)).operational(operational);
            guarded = HookProbe.attachLifecycle(lifecycle); attached = HookProbe.attachIoInterlock(io);
        }
        void load(boolean initialized) throws Exception {
            HookProbe.beforeOriginalIo("LOAD");
            try {
                HookProbe.enterLoad("owned", false); HookProbe.loadSource(source.toString());
                var input = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(source))); input.readAllBytes(); input.close();
                HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary);
                if (initialized) HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            } finally { HookProbe.afterOriginalIo(); }
        }
        void associate() throws Exception { handle = coordinator.confirm(coordinator.review(Optional.empty()), Action.CREATE_NEW, Optional.empty()); }
        void bind() throws Exception { coordinator.confirm(coordinator.review(Optional.of(handle)), Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()); }
        CareerCommandJournal journal() { return new CareerCommandJournal(journals, new Key(handle.career(), handle.binding(), owner)); }
        CareerCommandJournal.Intent intent(String key) { return new CareerCommandJournal.Intent(key, CareerCommandGate.Operation.FINANCE_CREDIT, "c".repeat(64)); }
        FinanceRequest request(String owner, String operation) throws Exception {
            var ledger = new HashMap<String, Long>(); for (String field : FinanceDelta.FIELDS) ledger.put(field, 0L);
            return new FinanceRequest(handle.career(), handle.binding(), observer.observe(saves, "owned.s22").revision(), owner, operation,
                    new FinanceRequest.Club(0, 1, "Synthetic club"), FinanceDelta.Direction.CREDIT_RAW_6, 1, new FinanceDelta.Snapshot(100, ledger));
        }
        FinanceRequestStore requestStore(String owner) { return new FinanceRequestStore(requests, new Key(handle.career(), handle.binding(), owner)); }
        FinanceExecutionWitnessStore executionStore(String owner) {
            return executionInstances.computeIfAbsent(owner, value -> new FinanceExecutionWitnessStore(executions, new Key(handle.career(), handle.binding(), value)));
        }
        FinanceExecutionWitnessStore.Armed arm(String owner, CareerCommandJournal journal, CareerCommandJournal.Prepared prepared, String token) throws Exception {
            var context = new CareerCommandGate.Context(UUID.randomUUID(), handle.career(), handle.binding(), lifecycle.ticket().generation(),
                    observer.observe(saves, "owned.s22").revision());
            return executionStore(owner).arm(context, journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), token);
        }
        @Override public void close() throws Exception { attached.close(); guarded.close(); io.close(); gate.close(); lifecycle.close(); }
    }
    public static void main(String[] args) throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path parent = local == null ? Files.createDirectories(Path.of("build")) : Files.createDirectories(Path.of(local, "Temp", "opencode"));
        for (var terminal : FinanceExecutionWitnessStore.State.values()) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate(); var request = h.request(h.owner, "execution-review");
                h.requestStore(h.owner).persist(request, EMPTY);
                var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent());
                var store = h.executionStore(h.owner); var armed = h.arm(h.owner, journal, prepared, EMPTY);
                if (terminal != FinanceExecutionWitnessStore.State.ARMED) store.complete(armed.lease(), terminal, armed.snapshot().token());
                if (terminal == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED)
                    journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
                var review = h.coordinator.review(Optional.of(h.handle));
                var status = switch (terminal) {
                    case ARMED -> FinanceExecutionRecovery.Status.ARMED_UNCERTAIN;
                    case MEMORY_APPLIED -> FinanceExecutionRecovery.Status.MEMORY_APPLIED_SAVE_UNPROVEN;
                    case NO_EFFECT_CONFIRMED -> FinanceExecutionRecovery.Status.NO_EFFECT_CONFIRMED;
                    default -> FinanceExecutionRecovery.Status.EXECUTION_UNCERTAIN;
                };
                check(review.executions().get(0).operations().get(0).status() == status, "witness review keeps state distinct " + terminal);
                String text = CareerAssociationPanel.report(review);
                check(text.contains(status.name()) && text.contains(prepared.snapshot().receipts().get(0).operationId().toString()), "panel shows exact execution UUID/status " + terminal);
                if (terminal == FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED) {
                    h.bind(); check(h.gate.contextInstalled(), "only matching same-anchor proven no-effect histories permit internal context");
                } else rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); h.bind(); String disabled = "br.owned.execution-only";
            var request = h.request(disabled, "orphan-operation");
            var journal = new CareerCommandJournal(new CareerSidecarStore(h.root.resolve("other-journal-root")), new Key(h.handle.career(), h.handle.binding(), disabled));
            var prepared = journal.prepare(request.save(), EMPTY, request.intent());
            var armed = h.arm(disabled, journal, prepared, EMPTY);
            h.executionStore(disabled).complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            var review = h.coordinator.review(Optional.of(h.handle));
            check(review.executions().stream().anyMatch(row -> row.owner().equals(disabled) && !row.configured()
                    && row.operations().get(0).status() == FinanceExecutionRecovery.Status.CONFLICT), "execution-only disabled owner without matching journal is included and conflicts even for NO_EFFECT");
            check(review.journals().stream().anyMatch(row -> row.owner().equals(disabled)) && review.requests().stream().anyMatch(row -> row.owner().equals(disabled)), "execution root contributes to complete union of owners");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            check(!h.gate.contextInstalled(), "execution-only conflict invalidates old operational context on preparation");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "contradictory"); h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent()); var armed = h.arm(h.owner, journal, prepared, EMPTY);
            h.executionStore(h.owner).complete(armed.lease(), FinanceExecutionWitnessStore.State.MEMORY_APPLIED, armed.snapshot().token());
            journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            check(h.coordinator.review(Optional.of(h.handle)).executions().get(0).operations().get(0).status() == FinanceExecutionRecovery.Status.CONFLICT,
                    "execution/journal contradiction cannot be hidden behind clean NO_EFFECT journal and contract");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "no-effect-unknown-journal"); h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent()); var armed = h.arm(h.owner, journal, prepared, EMPTY);
            h.executionStore(h.owner).complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            check(h.coordinator.review(Optional.of(h.handle)).executions().get(0).operations().get(0).status() == FinanceExecutionRecovery.Status.NO_EFFECT_CONFIRMED,
                    "execution evidence can preserve no-effect observation separately from unknown journal");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            check(journal.read(request.save()).receipts().get(0).state() == CareerCommandJournal.State.OUTCOME_UNKNOWN,
                    "review does not silently rewrite journal uncertainty based on independent execution evidence");
        }
        for (int schema : List.of(1, 2)) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate();
                var save = h.observer.observe(h.saves, "owned.s22").revision();
                h.executions.write(new Key(h.handle.career(), h.handle.binding(), h.owner), save, schema, EMPTY, new byte[] {1});
                var review = h.coordinator.review(Optional.of(h.handle));
                check(review.executions().get(0).failure().orElseThrow() == Reason.CORRUPT,
                        "valid envelope with malformed or unknown witness schema is not skipped " + schema);
                check(CareerAssociationPanel.report(review).contains("RECUSADO CORRUPT"), "panel reports witness decode failure " + schema);
                rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "stale-witness"); h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent()); var armed = h.arm(h.owner, journal, prepared, EMPTY);
            journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            var stale = h.coordinator.review(Optional.of(h.handle));
            h.executionStore(h.owner).complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(stale, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            h.bind(); check(h.gate.contextInstalled(), "fresh review can accept completed no-effect evidence, not stale HEAD token");
            var old = h.coordinator.review(Optional.of(h.handle));
            String disabled = "br.owned.new-execution-owner";
            var alternate = new CareerCommandJournal(new CareerSidecarStore(h.root.resolve("other-journal")), new Key(h.handle.career(), h.handle.binding(), disabled));
            var pending = alternate.prepare(request.save(), EMPTY, h.request(disabled, "added-owner").intent()); h.arm(disabled, alternate, pending, EMPTY);
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(old, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            check(!h.gate.contextInstalled(), "namespace added in execution root invalidates stale review and authority");
        }
        for (boolean orphan : List.of(false, true)) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate(); var request = h.request(h.owner, "broken");
                var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent());
                if (orphan) {
                    var faulty = new FinanceExecutionWitnessStore(new CareerSidecarStore(h.root.resolve("finance-execution-witnesses"), stage -> {
                        if (stage == Stage.RECORD_WRITTEN) throw new IOException("Owned interruption");
                    }), new Key(h.handle.career(), h.handle.binding(), h.owner));
                    var context = new CareerCommandGate.Context(UUID.randomUUID(), h.handle.career(), h.handle.binding(), 1, request.save());
                    try { faulty.arm(context, journal.executionPermit(prepared.lease().orElseThrow(), prepared.snapshot().token()), EMPTY); } catch (IOException expected) { }
                    check(h.coordinator.review(Optional.of(h.handle)).executions().get(0).failure().orElseThrow() == Reason.RECONCILIATION_REQUIRED, "witness orphan is pending, not absent evidence");
                    rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
                } else {
                    h.arm(h.owner, journal, prepared, EMPTY);
                    Files.write(h.root.resolve("finance-execution-witnesses").resolve(h.handle.career().toString()).resolve(h.handle.binding().toString()).resolve(h.owner).resolve("HEAD"), new byte[] {1});
                    rejects(Reason.CORRUPT, h::bind);
                }
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "old-witness");
            var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent()); var armed = h.arm(h.owner, journal, prepared, EMPTY);
            h.executionStore(h.owner).complete(armed.lease(), FinanceExecutionWitnessStore.State.NO_EFFECT_CONFIRMED, armed.snapshot().token());
            journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            Files.write(h.source, new byte[] {8, 9}); h.load(true);
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.ACCEPT_REVISION, Optional.empty());
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.REANCHOR_NO_EFFECT_JOURNAL, Optional.of(h.owner));
            var row = h.coordinator.review(Optional.of(h.handle)).executions().get(0);
            check(row.recovery().orElseThrow().revisionChanged() && row.recovery().orElseThrow().recordedSave().equals(request.save()), "review exposes original witness anchor even after journal-only reanchor");
            check(CareerAssociationPanel.report(h.coordinator.review(Optional.of(h.handle))).contains("Ancora de execucao SHA256: " + request.save().sha256()), "panel displays actual old execution anchor");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> h.executionStore(h.owner).read(h.observer.observe(h.saves, "owned.s22").revision()));
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); String owner = "br.owned.execution-overflow"; var request = h.request(owner, "overflow");
            var journal = new CareerCommandJournal(new CareerSidecarStore(h.root.resolve("other-root")), new Key(h.handle.career(), h.handle.binding(), owner));
            var prepared = journal.prepare(request.save(), EMPTY, request.intent()); h.arm(owner, journal, prepared, EMPTY);
            var capacity = new ArrayList<String>(); for (int i = 0; i < 64; i++) capacity.add("br.owned.capacity" + i);
            var limited = new CareerAssociationCoordinator(h.saves, h.associations, h.journals, h.requests, h.executions, h.observer, h.verifier, capacity);
            rejects(Reason.QUOTA, () -> limited.review(Optional.of(h.handle)));
        }
        try (var h = new Harness(parent)) {
            h.load(false); h.associate();
            check(!h.gate.contextInstalled(), "metadata association never automatically installs operational context");
            rejects(Reason.CONFLICT, h::bind);
            check(!h.gate.contextInstalled(), "unqualified initialization cannot become prepared context");
            h.load(true); h.bind();
            check(h.gate.contextInstalled() && h.operational.snapshot().status() == CareerOperationalContext.Status.BOUND_NO_PUBLIC_WRITES,
                    "explicit reviewed live initialized load binds only internal no-public-write context");
            var old = h.coordinator.review(Optional.of(h.handle)); h.load(true);
            check(h.operational.snapshot().status() == CareerOperationalContext.Status.INVALIDATED, "real I/O bridge invalidates previous internal context before synthetic reload");
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(old, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            h.bind(); HookProbe.rootAssigned();
            check(!h.gate.contextInstalled(), "standalone root assignment also invalidates owning gate");
            h.load(true); h.bind();
            HookProbe.beforeOriginalIo("SAVE");
            try { HookProbe.enterSave("owned"); HookProbe.exitSave(); } finally { HookProbe.afterOriginalIo(); }
            check(!h.gate.contextInstalled() && h.operational.snapshot().status() == CareerOperationalContext.Status.INVALIDATED, "save return never restores operational context");
            check(Arrays.equals(Files.readAllBytes(h.source), new byte[] {1, 2, 3}), "all context preparation/invalidation leaves owned source untouched");
        }
        for (var terminal : CareerCommandJournal.State.values()) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate(); var save = h.observer.observe(h.saves, "owned.s22").revision(); var journal = h.journal();
                var pending = journal.prepare(save, EMPTY, h.intent("historical"));
                if (terminal != CareerCommandJournal.State.PREPARED) journal.complete(pending.lease().orElseThrow(), terminal, pending.snapshot().token());
                if (terminal == CareerCommandJournal.State.NO_EFFECT_CONFIRMED) {
                    h.bind(); check(h.gate.contextInstalled(), "reviewed same-anchor no-effect history does not invent an unresolved effect");
                    check(h.coordinator.review(Optional.of(h.handle)).requests().get(0).links().get(0).status()
                            == FinanceContractReview.Status.LEGACY_NO_EFFECT_WITHOUT_REQUEST, "legacy no-effect receipt is explicit and never manufactures a missing contract");
                } else {
                    rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
                    check(!h.gate.contextInstalled(), "historical/uncertain command blocks activation " + terminal);
                }
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate();
            var save = h.observer.observe(h.saves, "owned.s22").revision();
            var disabled = new CareerCommandJournal(h.journals, new Key(h.handle.career(), h.handle.binding(), "br.owned.disabled"));
            var pending = disabled.prepare(save, EMPTY, h.intent("disabled-applied"));
            disabled.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.APPLIED, pending.snapshot().token());
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            check(!h.gate.contextInstalled(), "unselected owner's historical effect blocks whole binding context preparation");
        }
        for (var terminal : CareerCommandJournal.State.values()) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate(); var request = h.request(h.owner, "paired");
                h.requestStore(h.owner).persist(request, EMPTY);
                var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent());
                if (terminal != CareerCommandJournal.State.PREPARED) journal.complete(prepared.lease().orElseThrow(), terminal, prepared.snapshot().token());
                var review = h.coordinator.review(Optional.of(h.handle)); var row = review.requests().get(0);
                var expected = terminal == CareerCommandJournal.State.NO_EFFECT_CONFIRMED ? FinanceContractReview.Status.MATCHED_NO_EFFECT
                        : terminal == CareerCommandJournal.State.APPLIED ? FinanceContractReview.Status.MATCHED_HISTORICAL_EFFECT : FinanceContractReview.Status.MATCHED_UNCERTAIN;
                check(row.links().size() == 1 && row.links().get(0).status() == expected, "contract/receipt comparison preserves state " + terminal);
                if (terminal == CareerCommandJournal.State.NO_EFFECT_CONFIRMED) {
                    h.bind(); check(h.gate.contextInstalled(), "matching same-anchor contract with proven no effect permits only internal context");
                } else rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); h.bind();
            String disabled = "br.owned.disabled-contract"; var request = h.request(disabled, "payload-only");
            h.requestStore(disabled).persist(request, EMPTY);
            var review = h.coordinator.review(Optional.of(h.handle));
            check(review.requests().stream().anyMatch(row -> row.owner().equals(disabled) && !row.configured()
                    && row.links().get(0).status() == FinanceContractReview.Status.REQUEST_WITHOUT_RECEIPT), "unselected request-only owner is included even without journal namespace");
            check(review.journals().stream().anyMatch(row -> row.owner().equals(disabled) && row.recovery().orElseThrow().snapshot().receipts().isEmpty()), "union includes missing journal counterpart without inventing a receipt");
            check(CareerAssociationPanel.report(review).contains("REQUEST_WITHOUT_RECEIPT"), "panel discloses request-only uncertainty rather than proposing replay/deletion");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            check(!h.gate.contextInstalled(), "request without receipt blocks fresh preparation and invalidates old context");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "conflicting");
            h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var pending = journal.prepare(request.save(), EMPTY, h.intent(request.key()));
            journal.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, pending.snapshot().token());
            var review = h.coordinator.review(Optional.of(h.handle));
            check(review.requests().get(0).links().get(0).status() == FinanceContractReview.Status.PAYLOAD_CONFLICT, "same-key no-effect receipt with another digest does not validate contract");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "existing-no-effect");
            var committed = h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var pending = journal.prepare(request.save(), EMPTY, request.intent());
            journal.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, pending.snapshot().token());
            h.bind(); var stale = h.coordinator.review(Optional.of(h.handle));
            h.requestStore(h.owner).persist(h.request(h.owner, "changed-after-review"), committed.token());
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(stale, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            check(!h.gate.contextInstalled(), "contract inventory change invalidates stale review before context installation");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); h.bind(); var stale = h.coordinator.review(Optional.of(h.handle));
            h.requestStore("br.owned.new-contract-owner").persist(h.request("br.owned.new-contract-owner", "new-owner"), EMPTY);
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(stale, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            check(!h.gate.contextInstalled(), "new contract namespace after review also invalidates optimistic context installation");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate();
            h.requestStore("br.owned.unselected").persist(h.request("br.owned.unselected", "extra-owner"), EMPTY);
            var capacity = new ArrayList<String>(); for (int i = 0; i < 64; i++) capacity.add("br.owned.limit" + i);
            var limited = new CareerAssociationCoordinator(h.saves, h.associations, h.journals, h.requests, h.executions, h.observer, h.verifier, capacity);
            rejects(Reason.QUOTA, () -> limited.review(Optional.of(h.handle)));
        }
        for (boolean orphan : List.of(false, true)) {
            try (var h = new Harness(parent)) {
                h.load(true); h.associate(); var request = h.request(h.owner, "incomplete");
                if (orphan) {
                    var faults = new FinanceRequestStore(new CareerSidecarStore(h.root.resolve("finance-requests"), stage -> { if (stage == Stage.RECORD_WRITTEN) throw new IOException("Owned interruption"); }),
                            new Key(h.handle.career(), h.handle.binding(), h.owner));
                    try { faults.persist(request, EMPTY); } catch (IOException expected) { }
                } else {
                    h.requestStore(h.owner).persist(request, EMPTY);
                    Files.write(h.root.resolve("finance-requests").resolve(h.handle.career().toString()).resolve(h.handle.binding().toString()).resolve(h.owner).resolve("HEAD"), new byte[] {1});
                }
                if (orphan) {
                    var review = h.coordinator.review(Optional.of(h.handle));
                    check(review.requests().get(0).failure().orElseThrow() == Reason.RECONCILIATION_REQUIRED, "request orphan is not clean empty history");
                    rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
                } else rejects(Reason.CORRUPT, h::bind); // Inventory refuses an unreadable HEAD before producing partial rows.
            }
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var request = h.request(h.owner, "old-revision");
            h.requestStore(h.owner).persist(request, EMPTY);
            var journal = h.journal(); var prepared = journal.prepare(request.save(), EMPTY, request.intent());
            journal.complete(prepared.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, prepared.snapshot().token());
            Files.write(h.source, new byte[] {6, 7}); h.load(true);
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.ACCEPT_REVISION, Optional.empty());
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.REANCHOR_NO_EFFECT_JOURNAL, Optional.of(h.owner));
            var review = h.coordinator.review(Optional.of(h.handle));
            check(review.requests().get(0).recovery().orElseThrow().revisionChanged()
                    && review.requests().get(0).recovery().orElseThrow().recordedSave().equals(request.save()), "administrative review exposes actual old contract anchor after journal-only reanchor");
            check(CareerAssociationPanel.report(review).contains(request.save().sha256()), "panel shows exact old contract anchor without silently migrating payload");
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> h.requestStore(h.owner).read(h.observer.observe(h.saves, "owned.s22").revision()));
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var before = h.observer.observe(h.saves, "owned.s22").revision(); var journal = h.journal();
            var pending = journal.prepare(before, EMPTY, h.intent("no-effect"));
            var completed = journal.complete(pending.lease().orElseThrow(), CareerCommandJournal.State.NO_EFFECT_CONFIRMED, pending.snapshot().token());
            Files.write(h.source, new byte[] {4, 5}); h.load(true);
            rejects(Reason.CONFLICT, h::bind);
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.ACCEPT_REVISION, Optional.empty());
            rejects(Reason.RECONCILIATION_REQUIRED, h::bind);
            var next = h.observer.observe(h.saves, "owned.s22").revision();
            rejects(Reason.SAVE_REVISION_MISMATCH, () -> journal.read(next));
            h.coordinator.confirm(h.coordinator.review(Optional.of(h.handle)), Action.REANCHOR_NO_EFFECT_JOURNAL, Optional.of(h.owner));
            check(journal.read(next).receipts().equals(completed.receipts()) && !journal.inspectRecordedRecovery(next).revisionChanged(),
                    "explicit narrow no-effect reconciliation preserves receipt identities and updates only reviewed anchor");
            h.bind(); check(h.gate.contextInstalled(), "internal context can be prepared after all reviewed no-effect histories are explicitly reanchored");
            check(journal.prepare(next, journal.read(next).token(), h.intent("no-effect")).lease().isEmpty(), "migration never enables replay of old keys");
            var stale = h.coordinator.review(Optional.of(h.handle)); journal.prepare(next, journal.read(next).token(), h.intent("new-uncertain"));
            rejects(Reason.CONFLICT, () -> h.coordinator.confirm(stale, Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()));
            check(!h.gate.contextInstalled(), "stale inventory cannot leave previously bound context active");
        }
        try (var h = new Harness(parent); var foreignGate = new CareerCommandGate()) {
            h.load(true); h.associate();
            var foreign = new CareerAssociationCoordinator(h.saves, h.associations, h.journals, h.requests, h.executions, h.observer, h.verifier, List.of(h.owner))
                    .operational(new CareerOperationalContext(foreignGate));
            boolean refused = false;
            try { foreign.confirm(foreign.review(Optional.of(h.handle)), Action.PREPARE_INTERNAL_CONTEXT, Optional.empty()); }
            catch (LoadedSaveVerifier.Failure expected) { refused = expected.reason() == LoadedSaveVerifier.Reason.STALE_ROOTS; }
            check(refused && !foreignGate.contextInstalled(), "a different gate cannot borrow live provenance owned by session interlock");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); HookProbe.beforeOriginalIo("LOAD");
            try {
                boolean nested = false;
                try { HookProbe.beforeOriginalIo("SAVE"); } catch (IllegalStateException expected) { nested = true; }
                check(nested, "owned nested original save refusal established");
            } finally { HookProbe.afterOriginalIo(); }
            boolean refused = false;
            try { h.bind(); } catch (LoadedSaveVerifier.Failure expected) { refused = true; }
            check(refused && !h.gate.contextInstalled(), "session I/O refusal cannot be hidden by otherwise current initialized provenance");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate();
            var save = h.observer.observe(h.saves, "owned.s22").revision();
            var context = h.gate.installContext(h.handle.career(), h.handle.binding(), h.lifecycle.ticket().generation(), save, h.primary, h.secondary);
            var owner = h.gate.owner(h.owner, Set.of(CareerCommandGate.Operation.FINANCE_CREDIT));
            var request = new CareerCommandGate.Request(context.token(), "raw-running-fixture", CareerCommandGate.Operation.FINANCE_CREDIT, "f".repeat(64));
            var permit = h.gate.reserve(owner, request, h.primary, h.secondary).permit().orElseThrow();
            h.gate.retainDispatch(permit); SwingUtilities.invokeAndWait(() -> h.gate.begin(permit, h.primary, h.secondary));
            boolean busy = false;
            try { h.bind(); } catch (IllegalStateException expected) { busy = true; }
            check(busy && !h.gate.contextInstalled(), "busy preparation fails closed and invalidates old authority without pretending to cancel running handler");
            h.gate.finish(permit, CareerCommandGate.Status.APPLIED, h.primary, h.secondary); h.gate.releaseDispatch(permit);
            boolean uncertain = false;
            try { h.bind(); } catch (IllegalStateException expected) { uncertain = true; }
            check(uncertain && !h.gate.contextInstalled(), "session memory uncertainty cannot be erased by an empty persistent inventory");
        }
        try (var h = new Harness(parent)) {
            h.load(true); h.associate(); var verified = h.coordinator.review(Optional.of(h.handle)).decoded();
            var parked = new CountDownLatch(1); var release = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> { parked.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } });
            check(parked.await(2, TimeUnit.SECONDS), "owned EDT parking established");
            var preparation = h.gate.prepareContext();
            var timed = new LoadedSaveVerifier(h.observer, () -> new CareerCommandDispatcher.Roots(h.primary, h.secondary), Duration.ofMillis(40));
            try {
                boolean timeout = false;
                try { timed.installReviewedContext(verified, h.handle, h.gate, preparation); }
                catch (LoadedSaveVerifier.Failure expected) { timeout = expected.reason() == LoadedSaveVerifier.Reason.ROOTS_TIMEOUT; }
                check(timeout && !h.gate.contextInstalled(), "queued activation timeout cannot grant context");
            } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            check(!h.gate.contextInstalled(), "cancelled queued task never installs after caller timeout");
            var entered = new CountDownLatch(1); var letReturn = new CountDownLatch(1);
            var running = new LoadedSaveVerifier(h.observer, () -> {
                entered.countDown(); if (!letReturn.await(5, TimeUnit.SECONDS)) throw new IOException("owned activation wait timeout");
                return new CareerCommandDispatcher.Roots(h.primary, h.secondary);
            }, Duration.ofMillis(60));
            var runningPreparation = h.gate.prepareContext();
            try {
                boolean timeout = false;
                try { running.installReviewedContext(verified, h.handle, h.gate, runningPreparation); }
                catch (LoadedSaveVerifier.Failure expected) { timeout = expected.reason() == LoadedSaveVerifier.Reason.ROOTS_TIMEOUT; }
                check(timeout && entered.getCount() == 0 && !h.gate.contextInstalled(), "running roots lookup timeout revokes installation authority");
            } finally { letReturn.countDown(); SwingUtilities.invokeAndWait(() -> { }); }
            check(!h.gate.contextInstalled(), "already-running EDT callback cannot install late after timeout");
            var first = h.gate.prepareContext(); var installed = h.verifier.installReviewedContext(verified, h.handle, h.gate, first);
            check(h.gate.currentContext(installed), "reviewed live verifier installs through one-shot preparation");
            h.gate.cancelPreparation(first); check(!h.gate.contextInstalled(), "cancellation after installation revokes matching context");
            var second = h.gate.prepareContext(); var other = h.verifier.installReviewedContext(verified, h.handle, h.gate, second);
            h.gate.cancelPreparation(first); check(h.gate.currentContext(other), "older cancellation cannot erase unrelated newer preparation");
            var superseded = h.gate.prepareContext(); HookProbe.beforeOriginalIo("RESET"); HookProbe.afterOriginalIo();
            boolean changed = false;
            try { h.verifier.installReviewedContext(verified, h.handle, h.gate, superseded); }
            catch (LoadedSaveVerifier.Failure expected) { changed = true; }
            check(changed && !h.gate.contextInstalled(), "I/O epoch change prevents optimistic installation even if same references remain");
        }
        System.out.println("PASS: " + checks + " operational context checks; owned fixtures under " + parent + "; synthetic init is not original global readiness or public write authority.");
    }
}
