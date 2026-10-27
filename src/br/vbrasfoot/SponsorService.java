package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.io.IOException;
import java.util.*;

/** Loader-owned capability. Quote tokens are bounded, one-shot and never reconstructed from saved mod settings. */
final class SponsorService implements SponsorAccess {
    private final CareerAssociationCoordinator coordinator;
    private final LegacyRuntime runtime;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private final Map<UUID, PendingEnd> pendingEnds = new LinkedHashMap<>();
    private record PendingEnd(EndQuote publicQuote, CareerAssociationCoordinator.SponsorEndQuote internal, LegacySponsorAdapter adapter) { }
    private record Pending(Quote publicQuote, CareerAssociationCoordinator.SponsorQuote internal,
                           LegacySponsorAdapter adapter, LegacyLoanAdapter loans) { }
    SponsorService(CareerAssociationCoordinator coordinator, LegacyRuntime runtime) { this.coordinator = coordinator; this.runtime = runtime; }
    public CareerDataAccess.Scope bindCareer() throws IOException {
        try { return coordinator.bindSponsorCareer(); } catch (Exception failure) { throw describe(failure); }
    }
    public Quote quote(CareerDataAccess.Scope scope, UUID sponsor, int clubId, String clubName, String sponsorName,
                       int credit, String stadiumName) throws IOException {
        if (sponsor == null || credit <= 0 || clubName == null || sponsorName == null || sponsorName.isBlank()
                || sponsorName.length() > 100 || stadiumName == null || stadiumName.length() > 120)
            throw new IOException("Informe patrocinador, credito positivo e nome de estadio opcional (ate 120 caracteres)");
        try {
            var adapter = new LegacySponsorAdapter(runtime); var loans = new LegacyLoanAdapter(runtime);
            var internal = coordinator.quoteSponsor(scope, sponsor, clubId, clubName, sponsorName, credit, stadiumName, adapter, loans);
            if (internal.offer.isPresent()) {
                var nativeMessages = new NativeSponsorSaveMessage(runtime);
                if (!CareerBridge.onEdt(() -> nativeMessages.present(internal.offer.orElseThrow())))
                    throw new IOException("Mensagem nao consta na carreira carregada (possivelmente nao foi salva). Recebimento bloqueado; historico preservado.");
            }
            var quote = new Quote(UUID.randomUUID(), sponsor, clubName, sponsorName, credit, internal.request.before().balance(),
                    FinanceDelta.predict(internal.request.before(), internal.request.direction(), credit).balance(), internal.beforeStadium, internal.afterStadium);
            synchronized (pending) {
                pending.clear(); // New review revokes every older unconfirmed quote.
                pending.put(quote.token(), new Pending(quote, internal, adapter, loans));
            }
            synchronized (pendingEnds) { pendingEnds.clear(); }
            return quote;
        } catch (Exception failure) { throw describe(failure); }
    }
    public Result activate(Quote quote) throws IOException {
        Pending claim;
        synchronized (pending) { claim = quote == null ? null : pending.remove(quote.token()); }
        if (claim == null || !claim.publicQuote.equals(quote)) throw new IOException("Cotacao expirada/consumida. Nao repetir automaticamente");
        try {
            var result = coordinator.executeSponsor(claim.internal, claim.adapter, claim.loans);
            if (claim.internal.offer.isPresent() && result.kind() != CareerCommandDispatcher.Kind.EXECUTED) {
                try {
                    coordinator.updateNativeSponsorMessage(claim.internal.scope, claim.internal.offer.orElseThrow(),
                            result.kind() == CareerCommandDispatcher.Kind.EXECUTED ? State.APPLIED_IN_MEMORY : State.REVIEW_REQUIRED,
                            false, new NativeSponsorSaveMessage(runtime));
                } catch (Exception messageFailure) {
                    // The immutable credit outcome wins; never turn a message-refresh failure into a credit retry.
                    java.util.logging.Logger.getLogger("vbrasfoot").warning("Credito retido; mensagem nao atualizada. NAO repetir: " + messageFailure.getMessage());
                }
            }
            return switch (result.kind()) {
                case EXECUTED -> new Result(State.APPLIED_IN_MEMORY, "Credito aplicado: " + quote.credit() + ". Estadio: " + quote.stadiumAfter()
                        + ". Salve pelo comando original do jogo. O historico impede repetir este credito; sem garantia de inclusao antes de salvar.");
                case NO_EFFECT -> new Result(State.NO_EFFECT, "Nenhum efeito confirmado. A tentativa fica retida para impedir replay; revise os dados.");
                default -> new Result(State.REVIEW_REQUIRED, "Resultado incerto. Dinheiro/estadio podem ter sido alterados. NAO tente novamente; preserve save e historico para revisao.");
            };
        } catch (Exception failure) { throw describe(failure); }
    }
    public State state(CareerDataAccess.Scope scope, UUID sponsor) throws IOException {
        try { return coordinator.sponsorState(scope, sponsor); } catch (Exception failure) { throw describe(failure); }
    }
    public Result publish(CareerDataAccess.Scope scope, UUID sponsor, int clubId, String clubName, String sponsorName,
                          int credit, String stadiumName, String message) throws IOException {
        try {
            var adapter = new LegacySponsorAdapter(runtime);
            String date = CareerBridge.onEdt(() -> {
                adapter.finance.humanClub(clubId, clubName);
                return (String) CareerBridge.call(adapter.finance.roots().primary(), "T");
            });
            var offer = new SponsorOfferStore.Offer(sponsor, clubId, clubName, sponsorName, credit, stadiumName, message, date, false);
            var nativeMessages = new NativeSponsorSaveMessage(runtime);
            coordinator.publishSponsorOffer(scope, offer);
            coordinator.updateNativeSponsorMessage(scope, offer, State.PENDING_RECEIPT, true, nativeMessages);
            return new Result(State.PENDING_RECEIPT, "Mensagem adicionada a carreira, sem credito. SALVE PELO JOGO para ve-la depois, inclusive sem mods. Para receber, abra a central pelo mod. Estadio e credito so mudam ao receber.");
        } catch (Exception failure) { throw describe(failure); }
    }
    public Result cancelPending(CareerDataAccess.Scope scope, UUID sponsor) throws IOException {
        try {
            coordinator.cancelSponsorOffer(scope, sponsor);
            var inbox = coordinator.sponsorInbox();
            var offer = inbox.offers().stream().filter(e -> e.sponsor().equals(sponsor)).findFirst().orElseThrow();
            coordinator.updateNativeSponsorMessage(scope, offer, State.ENDED, false, new NativeSponsorSaveMessage(runtime));
            return new Result(State.ENDED, "Patrocinio pendente cancelado. Nenhum dinheiro ou estadio foi alterado; recebimento bloqueado.");
        } catch (Exception failure) { throw describe(failure); }
    }
    record Message(CareerDataAccess.Scope scope, SponsorOfferStore.Offer offer, State state) { }
    boolean current(Message message) {
        return coordinator.careerDataSelected(message.scope()) && HookProbe.decodedProvenance().binding()
                .filter(b -> b.bytes().sha256().equals(message.scope().saveSha256()) && !b.backup()).isPresent();
    }
    List<Message> inbox() throws Exception {
        var inbox = coordinator.sponsorInbox(); var result = new ArrayList<Message>();
        if (inbox.offers().isEmpty()) return List.of();
        var nativeMessages = new NativeSponsorSaveMessage(runtime);
        var visible = CareerBridge.onEdt(() -> {
            var present = new HashSet<UUID>();
            for (var offer : inbox.offers()) if (nativeMessages.present(offer)) present.add(offer.sponsor());
            return present;
        });
        for (var offer : inbox.offers()) if (visible.contains(offer.sponsor())) result.add(new Message(inbox.scope(), offer,
                CareerAssociationCoordinator.sponsorMessageState(inbox.ledger(), offer.sponsor(), Optional.of(offer))));
        Collections.reverse(result);
        return List.copyOf(result);
    }
    public EndQuote quoteEnd(CareerDataAccess.Scope scope, UUID sponsor) throws IOException {
        try {
            var adapter = new LegacySponsorAdapter(runtime); var internal = coordinator.quoteSponsorEnd(scope, Objects.requireNonNull(sponsor), adapter);
            var quote = new EndQuote(UUID.randomUUID(), sponsor, internal.activation.request().club().name(), internal.ending.finances().balance(),
                    internal.ending.stadiumCurrent(), internal.activation.stadiumBefore(),
                    !internal.ending.stadiumCurrent().equals(internal.activation.stadiumAfter()));
            synchronized (pending) { pending.clear(); }
            synchronized (pendingEnds) { pendingEnds.clear(); pendingEnds.put(quote.token(), new PendingEnd(quote, internal, adapter)); }
            return quote;
        } catch (Exception failure) { throw describe(failure); }
    }
    public Result end(EndQuote quote) throws IOException {
        PendingEnd claim;
        synchronized (pendingEnds) { claim = quote == null ? null : pendingEnds.remove(quote.token()); }
        if (claim == null || !claim.publicQuote.equals(quote)) throw new IOException("Cotacao de encerramento expirada/consumida; nao repetir automaticamente");
        try {
            var result = coordinator.executeSponsorEnd(claim.internal, claim.adapter);
            return switch (result.kind()) {
                case EXECUTED -> new Result(State.ENDED, "Patrocinio encerrado. Estadio restaurado: " + quote.stadiumOriginal()
                        + ". Dinheiro e contabilidade preservados. Salve pelo comando original do jogo; o credito continua bloqueado contra repeticao.");
                case NO_EFFECT -> new Result(State.REVIEW_REQUIRED, "Estadio/saldo mudou; nenhum efeito do encerramento confirmado. Tentativa retida, sem repetir automaticamente.");
                default -> new Result(State.REVIEW_REQUIRED, "Encerramento incerto: o nome pode ter sido restaurado. Nao repetir; preserve save e historico para revisao. Nenhum credito/debito solicitado.");
            };
        } catch (Exception failure) { throw describe(failure); }
    }
    private static IOException describe(Exception failure) {
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        return failure instanceof IOException io ? io : new IOException(failure.getMessage(), failure);
    }
}
