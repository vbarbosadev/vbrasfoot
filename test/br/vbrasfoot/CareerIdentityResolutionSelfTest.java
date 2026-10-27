package br.vbrasfoot;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static br.vbrasfoot.CareerAssociationCoordinator.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Initialized decoder markers are synthetic. Exercises the shared production resolver, never game writes. */
public final class CareerIdentityResolutionSelfTest {
    private static int checks;
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    private interface Call { void run() throws Exception; }
    private static void denied(Call call, String text) throws Exception {
        try { call.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(text);
    }
    private static void load(Path source, Object primary, Object secondary) throws Exception {
        HookProbe.enterLoad(source.getFileName().toString(), false); HookProbe.loadSource(source.toString());
        var trace = HookProbe.traceLoadInput(new ByteArrayInputStream(Files.readAllBytes(source))); trace.readAllBytes(); trace.close();
        HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary, secondary);
        HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
    }
    private static CareerAssociationCoordinator coordinator(Path saves, Path metadata, CareerSaveAssociations associations,
                                                            SaveFileObserver observer, LoadedSaveVerifier verifier) {
        return new CareerAssociationCoordinator(saves, associations, new CareerSidecarStore(metadata.resolve("journals")),
                new CareerSidecarStore(metadata.resolve("finance-requests")), new CareerSidecarStore(metadata.resolve("finance-execution-witnesses")),
                new CareerSidecarStore(metadata.resolve("loan-requests")), new CareerSidecarStore(metadata.resolve("loan-continuation")),
                observer, verifier, List.of(SponsorActivationStore.OWNER, LoanManagerPanel.OWNER));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "career-identity-").toRealPath();
        Path saves = Files.createDirectory(root.resolve("sav")), metadata = root.resolve("metadata");
        Path flamengo = Files.write(saves.resolve("Flamengo.s22"), new byte[]{1,2,3});
        Path deSola = Files.write(saves.resolve("De sola - save.s22"), new byte[]{4,5,6});
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(metadata.resolve("associations"), saves);
        var old = associations.create(observer.observe(saves, "Flamengo.s22"), CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
        // Retain actual immutable sponsor history and mod data for the original career.
        var sponsorStore = new SponsorActivationStore(new CareerSidecarStore(metadata.resolve("sponsor-activations")), old.handle());
        UUID sponsor = UUID.randomUUID(); var request = SponsorActivationStoreSelfTest.request(old.handle(), sponsor, old.save());
        var claim = sponsorStore.claim(sponsor, "a".repeat(64), request, EMPTY, "Original", "Arena");
        sponsorStore.complete(claim.lease(), claim.snapshot().token(), SponsorActivationStore.State.MEMORY_APPLIED);
        var oldHistory = sponsorStore.read();
        Object primary = new Object(), secondary = new Object();
        var verifier = new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary, secondary), Duration.ofSeconds(2));
        var coordinator = coordinator(saves, metadata, associations, observer, verifier);
        var prompts = new AtomicInteger();
        UnknownCareerPrompt neverPrompt = (path, known) -> { throw new AssertionError("Known save must not ask for new identity"); };
        try (var attached = HookProbe.attachLifecycle(new CareerLifecycle())) {
            load(deSola, primary, secondary);
            var unknown = coordinator.review(Optional.empty());
            coordinator.preparePortableCareerLocation(unknown);
            check(associations.inventory().equals(List.of(old)), "new same-directory save is not rejected as folder relocation and creates nothing");
            denied(() -> coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), (path, known) -> UnknownCareerChoice.CANCEL), "cancel must not create");
            check(associations.inventory().size() == 1, "cancel preserves sole old association");
            denied(() -> coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), (path, known) -> UnknownCareerChoice.CONTINUATION), "continuation must not create blank history");
            check(associations.inventory().size() == 1 && sponsorStore.read().equals(oldHistory), "continuation preserves identities and sponsor history");
            var newHandle = coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), (path, known) -> {
                prompts.incrementAndGet();
                check(path.equals(deSola) && known.equals(List.of(old)), "question identifies exact loaded save and known career");
                return UnknownCareerChoice.NEW_CAREER;
            });
            check(!newHandle.career().equals(old.handle().career()) && !newHandle.binding().equals(old.handle().binding()), "explicit new career gets independent IDs");
            check(associations.inspect(old.handle()).equals(old) && sponsorStore.read().equals(oldHistory), "Flamengo association and applied payment history stay byte-logically intact");
            check(new SponsorActivationStore(new CareerSidecarStore(metadata.resolve("sponsor-activations")), newHandle).read().entries().isEmpty(), "new career does not inherit old payments");
            check(coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), neverPrompt).equals(newHandle) && prompts.get() == 1, "same save reuses ID without repeated question");
            var scope = coordinator.bindSponsorCareer();
            check(scope.career().equals(newHandle.career()) && scope.binding().equals(newHandle.binding()), "sponsor bind automatically selects recognized metadata without dialog");
            var data = new CareerModDataAccess(coordinator, new CareerSidecarStore(metadata.resolve("career-mod-data")), SponsorActivationStore.OWNER);
            data.write(scope, "owned.bin", new byte[]{9});
            var loanAssociation = new NativeLoanAssociation(metadata, metadata.resolve("native-preference.properties"), coordinator);
            check(loanAssociation.resolve(null, null).equals(newHandle), "native loan uses same recognized identity without confirmation or finance adapter");
            check(Arrays.equals(data.read(scope, "owned.bin"), new byte[]{9}), "loan identity lookup does not replace mod scope/data");
            coordinator.clearCareerDataSelection();
            var reopened = coordinator(saves, metadata, new CareerSaveAssociations(metadata.resolve("associations"), saves), observer, verifier);
            load(deSola, primary, secondary);
            var reopenedScope = reopened.bindSponsorCareer();
            check(reopenedScope.career().equals(scope.career()) && !reopenedScope.selection().equals(scope.selection()), "restart reuses persistent ID with fresh session selection");
            check(new NativeLoanAssociation(metadata, metadata.resolve("native-preference.properties"), reopened).resolve(null, null).equals(newHandle), "remembered loan choice uses shared persistent identity");
            Path copy = Files.copy(flamengo, saves.resolve("Flamengo copy.s22")); load(copy, primary, secondary);
            denied(() -> coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), neverPrompt), "equal-byte unknown copy cannot create new history");
            check(associations.inventory().size() == 2, "known-copy refusal creates no identity");
            Path other = Files.write(saves.resolve("another.s22"), new byte[]{7,8,9}); load(other, primary, secondary);
            var before = associations.inventory();
            denied(() -> coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), (path, known) -> {
                load(deSola, primary, secondary); return UnknownCareerChoice.NEW_CAREER;
            }), "load change during question must invalidate confirmation");
            check(associations.inventory().equals(before), "stale confirmation never creates an association");
            load(other, primary, secondary);
            denied(() -> coordinator.resolveLoadedCareer(coordinator.review(Optional.empty()), (path, known) -> {
                Files.write(other, new byte[]{10,11,12}); return UnknownCareerChoice.NEW_CAREER;
            }), "file change during question must invalidate confirmation");
            check(associations.inventory().equals(before), "changed file confirmation preserves all histories");
            check(coordinator.operationalSnapshot().status() == CareerOperationalContext.Status.NOT_BOUND, "metadata recognition never grants game write authority");
            check(Arrays.equals(Files.readAllBytes(flamengo), new byte[]{1,2,3}) && Arrays.equals(Files.readAllBytes(deSola), new byte[]{4,5,6}), "original and new career save bytes never modified by resolver");
        }
        System.out.println("PASS: " + checks + " shared career identity checks; synthetic markers, no original game or user metadata changed. " + root);
    }
}
