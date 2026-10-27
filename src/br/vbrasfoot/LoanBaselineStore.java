package br.vbrasfoot;

import java.io.*;
import java.util.*;
import java.nio.file.Path;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Explicit user-selected baseline, not proof that historical APPLIED effects never occurred. */
final class LoanBaselineStore {
    static final String OWNER = "br.vbrasfoot.loan-baseline";
    private static final int MAGIC = 0x56424c42;
    record Baseline(String token, SaveRevision selectedSave, List<String> retired, Map<String,String> sources) {
        Baseline { retired = List.copyOf(retired); sources = Map.copyOf(sources); }
    }
    private final CareerSidecarStore store;
    private final Key key;
    LoanBaselineStore(CareerSidecarStore store, CareerSaveAssociations.Handle handle) {
        this.store = store; key = new Key(handle.career(), handle.binding(), OWNER);
    }
    Optional<Baseline> read() throws IOException {
        var inventory = store.inventoryBinding(key.career(), key.binding());
        if (inventory.tokens().keySet().stream().anyMatch(owner -> !owner.equals(OWNER))) throw invalid("Unexpected baseline namespace");
        var view = store.read(key, LoanContinuationStore.ANCHOR);
        if (view.value().isEmpty()) {
            if (!inventory.tokens().isEmpty()) throw invalid("Incomplete baseline");
            return Optional.empty();
        }
        if (view.value().orElseThrow().schema() != 1) throw invalid("Unknown baseline schema");
        try (var in = new DataInputStream(new ByteArrayInputStream(view.value().orElseThrow().payload()))) {
            if (in.readInt() != MAGIC || in.readInt() != 1) throw invalid("Unknown baseline format");
            var save = new SaveRevision(in.readUTF(), in.readLong());
            if (!in.readUTF().equals("USER_SELECTED_CURRENT_SAVE_NOT_PROOF_OF_NO_EFFECT")) throw invalid("Missing explicit decision");
            int count = in.readInt(); if (count < 1 || count > 64) throw invalid("Baseline request quota");
            var retired = new ArrayList<String>();
            for (int i = 0; i < count; i++) {
                String digest = in.readUTF();
                if (!digest.matches("[a-f0-9]{64}") || retired.contains(digest)) throw invalid("Invalid retired contract");
                retired.add(digest);
            }
            count = in.readInt(); if (count < 0 || count > 256) throw invalid("Baseline source quota");
            var sources = new TreeMap<String,String>();
            for (int i = 0; i < count; i++) if (sources.put(in.readUTF(), in.readUTF()) != null) throw invalid("Duplicate baseline source");
            LoanContinuationStore.validateSources(new LoanContinuationStore.Snapshot(EMPTY, List.of(), Map.of()), sources);
            if (in.available() != 0) throw invalid("Trailing baseline bytes");
            return Optional.of(new Baseline(view.token(), save, retired, sources));
        } catch (IllegalArgumentException bad) { throw invalid("Invalid baseline payload"); }
    }
    Baseline selectCurrentSave(SaveRevision selectedSave, List<LoanRequest> retired, Map<String,String> sources) throws IOException {
        if (read().isPresent()) throw invalid("Baseline already exists; never silently replace");
        if (retired.isEmpty() || retired.size() > 64 || retired.stream().anyMatch(r -> r.fee() != 0)
                || retired.stream().map(LoanRequest::digest).distinct().count() != retired.size())
            throw invalid("Only distinct verified zero-fee historical contracts supported");
        LoanContinuationStore.validateSources(new LoanContinuationStore.Snapshot(EMPTY, List.of(), Map.of()), sources);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(1); out.writeUTF(selectedSave.sha256()); out.writeLong(selectedSave.size());
            out.writeUTF("USER_SELECTED_CURRENT_SAVE_NOT_PROOF_OF_NO_EFFECT");
            out.writeInt(retired.size()); for (var request : retired) out.writeUTF(request.digest());
            out.writeInt(sources.size()); for (var entry : new TreeMap<>(sources).entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); }
        }
        store.write(key, LoanContinuationStore.ANCHOR, 1, EMPTY, bytes.toByteArray()); return read().orElseThrow();
    }
    static List<LoanRequest> validate(Baseline baseline, List<LoanRequest> active, Map<String,String> sources) throws IOException {
        LoanContinuationStore.validateSources(new LoanContinuationStore.Snapshot(EMPTY, List.of(), baseline.sources()), sources);
        var retired = new ArrayList<LoanRequest>();
        for (String digest : baseline.retired()) {
            var matches = active.stream().filter(request -> request.digest().equals(digest) && request.fee() == 0).toList();
            if (matches.size() != 1) throw invalid("Retired contract no longer matches qualified APPLIED history");
            retired.add(matches.get(0));
        }
        return List.copyOf(retired);
    }
    static Map<String,String> historicalSources(Path metadata, CareerSaveAssociations.Group group) throws IOException {
        var sources = new TreeMap<String,String>();
        for (var member : group.members()) {
            String prefix = member.handle().equals(group.primary()) ? "" : "alias/" + member.handle().career() + "/" + member.handle().binding() + "/";
            for (var root : Map.of("journal", "journals", "finance", "finance-requests", "execution", "finance-execution-witnesses", "loan", "loan-requests").entrySet()) {
                var inventory = new CareerSidecarStore(metadata.resolve(root.getValue())).inventoryBinding(member.handle().career(), member.handle().binding());
                for (var entry : inventory.tokens().entrySet()) {
                    if (entry.getValue().equals(EMPTY)) throw invalid("Incomplete historical namespace");
                    sources.put(prefix + root.getKey() + "/" + entry.getKey(), entry.getValue());
                }
            }
            var ledger = new LoanContinuationStore(new CareerSidecarStore(metadata.resolve("loan-continuation")), member.handle()).read();
            if (!ledger.entries().isEmpty()) throw invalid("Offline initial selection requires no continuation effects; use coordinated review");
            if (!prefix.isEmpty()) {
                sources.put(prefix + "association", member.token());
                sources.put(prefix + "continuation", ledger.token().equals(EMPTY) ? "0".repeat(64) : ledger.token());
            }
        }
        return Map.copyOf(sources);
    }
    private static Failure invalid(String message) { return new Failure(Reason.RECONCILIATION_REQUIRED, message); }
}
