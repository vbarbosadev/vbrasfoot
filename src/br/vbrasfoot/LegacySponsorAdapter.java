package br.vbrasfoot;

import java.util.*;
import javax.swing.SwingUtilities;

/** Uses the original credit method and original stadium name setter, preserving the stadium object/capacity. */
final class LegacySponsorAdapter {
    private final LegacyRuntime runtime;
    final LegacyFinanceAdapter finance;
    LegacySponsorAdapter(LegacyRuntime runtime) throws Exception {
        this.runtime = runtime; finance = new LegacyFinanceAdapter(runtime);
        try (var in = runtime.findResource("best/v.class").openStream()) {
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            if (!STADIUM_HASH.equals(hash)) throw new IllegalStateException("Classe de estadio fora do perfil qualificado");
        }
    }
    static final String STADIUM_HASH = "535883966b8779699776a1b6f15c4045b8918e69632dc96f1ccab2bb9edea8d2";
    NativeSponsorSaveMessage nativeMessages() throws Exception { return new NativeSponsorSaveMessage(runtime); }
    CareerCommandDispatcher.Handler endHandler(SponsorActivationStore.Entry activation, SponsorActivationStore.EndAttempt ending,
            java.util.function.BooleanSupplier selectionGuard) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Prepare ending on EDT");
        var request = activation.request(); var intent = SponsorActivationStore.endIntent(activation, ending);
        var capturedRoots = finance.roots();
        Object club = finance.humanClub(request.club().id(), request.club().name()), stadium = CareerBridge.call(club, "ev");
        String original = activation.stadiumBefore();
        if (original.isBlank() || !ending.stadiumCurrent().equals(CareerBridge.call(stadium, "dS"))
                || !ending.finances().equals(finance.snapshot(club))) throw new IllegalStateException("Estadio/saldo mudou desde a cotacao");
        return new CareerCommandDispatcher.Handler() {
            public boolean accepts(CareerCommandJournal.Intent proposed) { return intent.equals(proposed); }
            public boolean acceptsContext(CareerCommandGate.Context context, String owner) {
                return SponsorEndPipeline.OWNER.equals(owner) && selectionGuard.getAsBoolean()
                        && request.career().equals(context.career()) && request.binding().equals(context.binding()) && ending.save().equals(context.save());
            }
            public CareerCommandGate.Status apply() throws Exception {
                if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("End on EDT");
                var current = finance.roots();
                if (!selectionGuard.getAsBoolean() || current.primary() != capturedRoots.primary() || current.secondary() != capturedRoots.secondary()
                        || finance.humanClub(request.club().id(), request.club().name()) != club || CareerBridge.call(club, "ev") != stadium
                        || !ending.stadiumCurrent().equals(CareerBridge.call(stadium, "dS")) || !ending.finances().equals(finance.snapshot(club)))
                    return CareerCommandGate.Status.REJECTED;
                // Deliberately no financial method, debit, refund or reconstructed stadium.
                if (!original.equals(ending.stadiumCurrent())) stadium.getClass().getMethod("i", String.class).invoke(stadium, original);
                return original.equals(CareerBridge.call(stadium, "dS")) && CareerBridge.call(club, "ev") == stadium
                        && ending.finances().equals(finance.snapshot(club)) ? CareerCommandGate.Status.APPLIED : CareerCommandGate.Status.OUTCOME_UNKNOWN;
            }
        };
    }
    String stadium(Object club) throws Exception { return (String) CareerBridge.call(CareerBridge.call(club, "ev"), "dS"); }
    CareerCommandDispatcher.Handler handler(FinanceRequest request, String before, String after) throws Exception {
        return handler(request, before, after, () -> true);
    }
    CareerCommandDispatcher.Handler handler(FinanceRequest request, String before, String after,
            java.util.function.BooleanSupplier selectionGuard) throws Exception {
        return handler(request, before, after, selectionGuard, () -> {});
    }
    CareerCommandDispatcher.Handler handler(FinanceRequest request, String before, String after,
            java.util.function.BooleanSupplier selectionGuard, CareerCommandGate.MessageUpdate afterCredit) throws Exception {
        var plan = finance.prepare(request); Object club = finance.humanClub(request.club().id(), request.club().name());
        Object stadium = CareerBridge.call(club, "ev");
        if (!Objects.equals(before, CareerBridge.call(stadium, "dS"))) throw new IllegalStateException("Nome do estadio mudou");
        return new CareerCommandDispatcher.Handler() {
            public boolean accepts(CareerCommandJournal.Intent intent) { return plan.accepts(intent); }
            public boolean acceptsContext(CareerCommandGate.Context context, String owner) { return selectionGuard.getAsBoolean() && plan.acceptsContext(context, owner); }
            public Optional<FinanceRequest> persistentRequest() { return Optional.of(request); }
            public boolean supportsExecutionWitness() { return true; }
            public void observeSerialization(CareerCommandJournal.ExecutionPermit permit) { plan.observeSerialization(permit); }
            public CareerCommandGate.Status apply() throws Exception {
                if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Ativacao exige EDT");
                if (!selectionGuard.getAsBoolean() || CareerBridge.call(club, "ev") != stadium || !Objects.equals(before, CareerBridge.call(stadium, "dS")))
                    return CareerCommandGate.Status.REJECTED;
                var result = plan.apply();
                if (result != CareerCommandGate.Status.APPLIED) return result;
                if (!before.equals(after)) stadium.getClass().getMethod("i", String.class).invoke(stadium, after);
                afterCredit.run(); // Same EDT effect and original-I/O exclusion as the financial/stadium change.
                return Objects.equals(after, CareerBridge.call(stadium, "dS")) && CareerBridge.call(club, "ev") == stadium
                        ? CareerCommandGate.Status.APPLIED : CareerCommandGate.Status.OUTCOME_UNKNOWN;
            }
        };
    }
}
