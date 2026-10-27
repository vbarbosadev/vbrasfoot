package br.vbrasfoot;

import java.nio.file.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

public final class LoanBaselineStoreSelfTest {
    static int checks;
    interface Action { void run() throws Exception; }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static void refuses(Action action) throws Exception {
        try { action.run(); } catch (java.io.IOException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "loan-baseline-test-");
        var handle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        var save = new SaveRevision("a".repeat(64), 12);
        var first = LoanContinuationStoreSelfTest.request(handle, "historical", 1, save);
        var other = LoanContinuationStoreSelfTest.request(handle, "unrelated", 2, save);
        var sources = Map.of("journal/" + LoanManagerPanel.OWNER, "b".repeat(64));
        var store = new LoanBaselineStore(new CareerSidecarStore(root), handle);
        check(store.read().isEmpty(), "no implicit baseline created");
        refuses(() -> store.selectCurrentSave(save, List.of(), sources));
        refuses(() -> store.selectCurrentSave(save, List.of(first, first), sources));
        var baseline = store.selectCurrentSave(save, List.of(first), sources);
        check(store.read().orElseThrow().equals(baseline) && baseline.selectedSave().equals(save), "explicit decision and selected bytes survive restart");
        check(LoanBaselineStore.validate(baseline, List.of(first, other), sources).equals(List.of(first)), "only exact selected contract retired; unrelated APPLIED stays active");
        refuses(() -> store.selectCurrentSave(save, List.of(other), sources));
        refuses(() -> LoanBaselineStore.validate(baseline, List.of(other), sources));
        refuses(() -> LoanBaselineStore.validate(baseline, List.of(first), Map.of()));
        refuses(() -> LoanBaselineStore.validate(baseline, List.of(first), Map.of("journal/" + LoanManagerPanel.OWNER, "c".repeat(64))));
        var reviewed = new TreeMap<>(sources); reviewed.put("baseline", baseline.token());
        var continuation = new LoanContinuationStore.Snapshot(EMPTY, List.of(), reviewed);
        refuses(() -> LoanContinuationStore.validateSources(continuation, sources));
        var wrongHandle = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
        check(new LoanBaselineStore(new CareerSidecarStore(root), wrongHandle).read().isEmpty(), "decision scoped to exact career/binding");
        var disk = new CareerSidecarStore(root); var key = new Key(handle.career(), handle.binding(), LoanBaselineStore.OWNER);
        disk.write(key, LoanContinuationStore.ANCHOR, 99, baseline.token(), new byte[]{0});
        refuses(() -> store.read());
        System.out.println("PASS: " + checks + " explicit baseline preservation/source/scope checks; no gameplay effects");
    }
}
