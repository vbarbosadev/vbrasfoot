package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Per-mod ephemeral quotes, shared coordinator history. No private game objects escape to authors. */
final class LoanService implements LoanAccess {
    private final CareerAssociationCoordinator coordinator;
    private final LegacyRuntime runtime;
    private final CareerBridge bridge;
    private final BooleanSupplier active;
    private Pending pending;
    private record Pending(Quote quote, CareerDataAccess.Scope scope, CareerAssociationCoordinator.NativeLoanQuote internal, LegacyLoanAdapter adapter) { }
    LoanService(CareerAssociationCoordinator coordinator, LegacyRuntime runtime, CareerBridge bridge, BooleanSupplier active) {
        this.coordinator = coordinator; this.runtime = runtime; this.bridge = bridge; this.active = active;
    }
    private void check() { if (!active.getAsBoolean()) throw new IllegalStateException("Mod encerrado; comando revogado"); }
    public CareerDataAccess.Scope bindCareer() throws IOException {
        check(); try { return coordinator.bindSponsorCareer(); } catch (Exception e) { throw failure(e); }
    }
    public synchronized Quote quote(CareerDataAccess.Scope scope, GameAccess.PlayerRef player, GameAccess.ClubRef origin, GameAccess.ClubRef destination) throws IOException {
        check(); pending = null;
        try {
            coordinator.validateCareerDataScope(scope);
            var adapter = new LegacyLoanAdapter(runtime, () -> active.getAsBoolean() && coordinator.careerDataSelected(scope));
            var quote = coordinator.quoteNativeLoan(new CareerSaveAssociations.Handle(scope.career(), scope.binding()), bridge, adapter, player, origin, destination);
            var req = quote.request();
            var result = new Quote(UUID.randomUUID(), req.player().name(), req.origin().name(), req.destination().name(), req.fee(), 366, req.nativeListed());
            check(); pending = new Pending(result, scope, quote, adapter); return result;
        } catch (Exception e) { throw failure(e); }
    }
    public Result accept(Quote quote) throws IOException {
        check(); Pending claim;
        synchronized (this) { claim = pending; pending = null; }
        if (claim == null || !claim.quote().equals(quote)) throw new IOException("Cotacao estrangeira/consumida; nao repetir");
        try {
            coordinator.validateCareerDataScope(claim.scope()); check();
            var result = coordinator.executeNativeLoan(claim.internal(), claim.adapter());
            return new Result(switch (result.kind()) {
                case EXECUTED -> State.APPLIED_IN_MEMORY;
                case NO_EFFECT, DENIED, RECORDED_ONLY -> State.NO_EFFECT;
                default -> State.REVIEW_REQUIRED;
            }, NativeLoanPresentation.result(result.kind(), quote.fee()));
        } catch (Exception e) { throw failure(e); }
    }
    private static IOException failure(Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return e instanceof IOException io ? io : new IOException(e.getMessage(), e);
    }
}
