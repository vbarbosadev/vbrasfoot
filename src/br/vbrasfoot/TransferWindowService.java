package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Per-career persisted LOAN-only policy. Never edits the original calendar or claims to govern purchases/AI. */
final class TransferWindowService {
    private static final String OWNER = "br.vbrasfoot.transfer-windows";
    private final CareerSidecarStore disk;
    private final CareerAssociationCoordinator coordinator;
    private final CareerBridge bridge;
    private final Map<CareerSaveAssociations.Handle, TransferWindows.Snapshot> cache = new ConcurrentHashMap<>();
    TransferWindowService(CareerSidecarStore disk, CareerAssociationCoordinator coordinator, CareerBridge bridge) {
        this.disk = disk; this.coordinator = coordinator; this.bridge = bridge;
    }
    private Key key(CareerSaveAssociations.Handle h) { return new Key(h.career(), h.binding(), OWNER); }
    TransferWindows.Snapshot load(CareerSaveAssociations.Handle handle) throws IOException {
        var inventory = disk.inventoryBinding(handle.career(), handle.binding());
        if (inventory.tokens().keySet().stream().anyMatch(s -> !s.equals(OWNER))) throw new IOException("Namespace de janela inesperado");
        var view = disk.read(key(handle), SponsorActivationStore.ANCHOR);
        TransferWindows.Snapshot snapshot;
        if (view.value().isEmpty()) {
            if (!inventory.tokens().isEmpty()) throw new IOException("Politica de janela incompleta; nao assumir livre");
            snapshot = new TransferWindows.Snapshot(EMPTY, "", new TransferWindows.Rules(false, List.of()));
        } else try (var in = new DataInputStream(new ByteArrayInputStream(view.value().orElseThrow().payload()))) {
            if (view.value().orElseThrow().schema() != 1 || !in.readUTF().equals("vbrasfoot.loan-windows/1")) throw new IOException("Politica desconhecida");
            String by = in.readUTF(); if (!by.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*") || by.length() > 200) throw new IOException("Autor invalido");
            boolean enabled = in.readBoolean(); int count = in.readInt(); if (count < 0 || count > 32) throw new IOException("Limite de periodos");
            var periods = new ArrayList<TransferWindows.Period>();
            for (int i = 0; i < count; i++) periods.add(new TransferWindows.Period(LocalDate.parse(in.readUTF()), LocalDate.parse(in.readUTF())));
            var rules = new TransferWindows.Rules(enabled, periods);
            if (in.available() != 0) throw new IOException("Dados extras na politica");
            snapshot = new TransferWindows.Snapshot(view.token(), by, rules);
        } catch (RuntimeException bad) { throw new IOException("Politica corrompida; emprestimo bloqueado", bad); }
        cache.put(handle, snapshot); return snapshot;
    }
    TransferWindows forMod(String owner) {
        return new TransferWindows() {
            public Snapshot current(CareerDataAccess.Scope scope) throws IOException {
                try {
                    coordinator.validateCareerDataScope(scope);
                    var result = load(new CareerSaveAssociations.Handle(scope.career(), scope.binding()));
                    coordinator.validateCareerDataScope(scope); return result;
                } catch (Exception e) { throw failure(e); }
            }
            public Snapshot configure(CareerDataAccess.Scope scope, String revision, Rules rules) throws IOException {
                Objects.requireNonNull(rules); current(scope); // Worker-only provenance and corruption checks.
                try {
                    coordinator.validateCareerDataScope(scope);
                    var h = new CareerSaveAssociations.Handle(scope.career(), scope.binding());
                    var bytes = new ByteArrayOutputStream();
                    try (var out = new DataOutputStream(bytes)) {
                        out.writeUTF("vbrasfoot.loan-windows/1"); out.writeUTF(owner); out.writeBoolean(rules.enabled()); out.writeInt(rules.periods().size());
                        for (var p : rules.periods()) { out.writeUTF(p.start().toString()); out.writeUTF(p.end().toString()); }
                    }
                    var view = disk.write(key(h), SponsorActivationStore.ANCHOR, 1, revision, bytes.toByteArray());
                    var snapshot = new Snapshot(view.token(), owner, rules); cache.put(h, snapshot); return snapshot;
                } catch (Exception e) { throw failure(e); }
            }
        };
    }
    void checkLoan(CareerSaveAssociations.Handle handle) throws Exception {
        load(handle);
        if (!CareerBridge.onEdt(() -> allowedNow(handle))) throw new IOException("Janela de emprestimos fechada na data atual; calendario nao alterado");
    }
    boolean allowedNow(CareerSaveAssociations.Handle handle) {
        var snapshot = cache.get(handle);
        if (snapshot == null) return false;
        if (!snapshot.rules().enabled()) return true;
        if (snapshot.rules().periods().isEmpty()) return false;
        var date = bridge.scopedCalendar().date();
        return snapshot.rules().allows(LocalDate.of(date.year(), date.month(), date.day()));
    }
    private static IOException failure(Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return e instanceof IOException io ? io : new IOException(e.getMessage(), e);
    }
}
