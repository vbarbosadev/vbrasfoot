package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

public final class SponsorOffersCoordinatorSelfTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    interface Action { void run() throws Exception; }
    private static void denied(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sponsor-offers-");
        Path saves = Files.createDirectory(root.resolve("sav")); Path source = Files.write(saves.resolve("new.s22"), new byte[]{1,2,3});
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(root.resolve("associations"));
        var handle = associations.create(observer.observe(saves, "new.s22"), CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER).handle();
        Object primary = new Object(), secondary = new Object();
        var coordinator = new CareerAssociationCoordinator(saves, associations, new CareerSidecarStore(root.resolve("journals")),
                new CareerSidecarStore(root.resolve("requests")), new CareerSidecarStore(root.resolve("executions")),
                new CareerSidecarStore(root.resolve("loans")), new CareerSidecarStore(root.resolve("continuation")), observer,
                new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2)), List.of(SponsorActivationStore.OWNER));
        UUID id = UUID.randomUUID();
        var offer = new SponsorOfferStore.Offer(id, -1, "Clube", "<html>{valor} $ponsor", 1_000_000, "Arena", "{patrocinador}: {valor} ao {clube}.\nLiteral <script>.", "02/01/2022", false);
        try (var guard = HookProbe.attachLifecycle(new CareerLifecycle())) {
            HookProbe.enterLoad("new", false); HookProbe.loadSource(source.toString());
            var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(source))); trace.readAllBytes(); trace.close();
            HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary); HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            coordinator.selectCareerData(coordinator.review(Optional.of(handle))); var scope = coordinator.currentCareerDataScope();
            coordinator.publishSponsorOffer(scope, offer);
            check(coordinator.sponsorState(scope, id) == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT, "activation is pending, not a financial credit");
            check(Arrays.equals(Files.readAllBytes(source), new byte[]{1,2,3}) && !Files.exists(root.resolve("sponsor-activations")), "publishing never edits save or creates financial ledger");
            var store = new SponsorOfferStore(new CareerSidecarStore(root.resolve("sponsor-offers")), handle);
            var before = store.read();
            check(before.entries().equals(List.of(offer)), "pending message survives store reopen with exact terms");
            check(offer.body().contains("<html>{valor} $ponsor: R$") && offer.body().contains("1.000.000,00") && offer.body().contains("<script>"), "bounded plain-text placeholders are replaced once, no HTML execution");
            check(coordinator.sponsorInbox().offers().equals(List.of(offer)), "native inbox reads same career offer");
            denied(() -> coordinator.publishSponsorOffer(scope, offer));
            var second = new SponsorOfferStore.Offer(UUID.randomUUID(), -1, "Clube", "Other", 5, "", "Teste", "02/01/2022", false);
            denied(() -> coordinator.publishSponsorOffer(scope, second));
            coordinator.cancelSponsorOffer(scope, id);
            check(coordinator.sponsorState(scope, id) == br.vbrasfoot.api.SponsorAccess.State.ENDED, "cancel unpaid offer blocks receipt without financial call");
            denied(() -> coordinator.cancelSponsorOffer(scope, id));
            denied(() -> coordinator.publishSponsorOffer(scope, offer));
            coordinator.publishSponsorOffer(scope, second);
            var ledger = new SponsorActivationStore(new CareerSidecarStore(root.resolve("sponsor-activations")), handle);
            var request = SponsorActivationStoreSelfTest.request(handle, second.sponsor(), new SaveRevision(scope.saveSha256(), scope.saveSize()));
            var claim = ledger.claim(second.sponsor(), "a".repeat(64), request, EMPTY, "Original", "Arena");
            check(coordinator.sponsorState(scope, second.sponsor()) == br.vbrasfoot.api.SponsorAccess.State.REVIEW_REQUIRED, "crash after financial claim does not become receivable pending offer");
            denied(() -> coordinator.cancelSponsorOffer(scope, second.sponsor()));
            ledger.complete(claim.lease(), claim.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
            check(coordinator.sponsorState(scope, second.sponsor()) == br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY, "immutable financial history overrides unpaid-message metadata");
            denied(() -> coordinator.cancelSponsorOffer(scope, second.sponsor()));
            var other = new CareerSaveAssociations.Handle(UUID.randomUUID(), UUID.randomUUID());
            check(new SponsorOfferStore(new CareerSidecarStore(root.resolve("sponsor-offers")), other).read().entries().isEmpty(), "different career never inherits messages/payments");
            denied(() -> store.publish(second, before.token()));
            check(coordinator.operationalSnapshot().status() == CareerOperationalContext.Status.NOT_BOUND, "publish/cancel never grants game write authority");
        }
        System.out.println("PASS: " + checks + " unpaid message/coordinator checks; synthetic, no original money calls. " + root);
    }
}
