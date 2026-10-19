package br.vbrasfoot;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Internal context association only. No owner permissions, handlers, public commands or graph-wide readiness claim. */
final class CareerOperationalContext {
    enum Status { NOT_BOUND, BOUND_NO_PUBLIC_WRITES, INVALIDATED }
    record Snapshot(Status status, Optional<UUID> career, Optional<UUID> binding, long generation) { }
    private final CareerCommandGate gate;
    private final AtomicReference<CareerCommandGate.Context> context = new AtomicReference<>();
    CareerOperationalContext(CareerCommandGate gate) { this.gate = Objects.requireNonNull(gate); }
    CareerCommandGate.Preparation begin() { context.set(null); return gate.prepareContext(); }
    void cancel(CareerCommandGate.Preparation preparation) { gate.cancelPreparation(preparation); }
    void accepted(CareerCommandGate.Context installed) {
        if (!gate.currentContext(installed)) throw new IllegalStateException("Prepared context already invalidated");
        context.set(installed);
    }
    void invalidate() { gate.invalidate(); }
    CareerCommandGate gate() { return gate; }
    Snapshot snapshot() {
        var current = context.get();
        return current == null ? new Snapshot(Status.NOT_BOUND, Optional.empty(), Optional.empty(), 0)
                : new Snapshot(gate.currentContext(current) ? Status.BOUND_NO_PUBLIC_WRITES : Status.INVALIDATED,
                Optional.of(current.career()), Optional.of(current.binding()), current.generation());
    }
}
