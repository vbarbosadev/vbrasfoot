package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** Actual loader boundaries with synthetic mods; no original game/runtime/save. */
public final class ModContextLifecycleSelfTest {
    private static int checks, calls, shutdowns;
    private static boolean failInitialize, failShutdown;
    private static ModContext retained;
    private static GameAccess retainedGame;
    private static CareerObservations retainedObservations;
    private static AutoCloseable registration;
    private static ModContext retainedBannerContext;
    private static AutoCloseable bannerRegistration;
    private static ModContext sponsorContext;
    public static final class SponsorFeature implements Mod {
        public void initialize(ModContext context) { sponsorContext = context; }
    }
    private static int playerReferenceModCalls, playerReferenceProviderCalls;
    private static final List<String> stopped = new ArrayList<>();
    public static final class First implements Mod {
        public void initialize(ModContext context) { context.registerAction("shared", "First", () -> calls++); }
        public void shutdown() { stopped.add("first"); }
    }
    public static final class Second implements Mod {
        public void initialize(ModContext context) { context.registerAction("shared", "Second", () -> calls++); }
        public void shutdown() { stopped.add("second"); throw new IllegalArgumentException("second-shutdown"); }
    }
    public static final class PlayerReferenceFeature implements Mod {
        public void initialize(ModContext context) {
            context.game().scopedPlayersWithReferences(null, 0, 1);
            playerReferenceModCalls++;
        }
    }
    public static final class ClubBannerFeature implements Mod {
        public void initialize(ModContext context) {
            retainedBannerContext = context;
            bannerRegistration = context.registerClubBanner("logo", (club, scope) -> Optional.empty());
        }
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
    private static void rejected(Runnable operation, String label) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, label);
    }
    public static final class Fixture implements Mod {
        public void initialize(ModContext context) {
            retained = context; retainedGame = context.game(); retainedObservations = context.careerObservations();
            context.registerAction("legacy", "Legacy", () -> calls++);
            registration = context.registerScopedAction("scoped", "Scoped", () -> calls++);
            if (failInitialize) throw new IllegalArgumentException("owned-init-failure");
        }
        public void shutdown() throws Exception {
            shutdowns++;
            if (!failInitialize) {
                retained.writeData("shutdown.txt", new byte[] {42});
                check(retained.readData("shutdown.txt")[0] == 42, "data remains usable during normal shutdown");
            }
            rejected(() -> retained.registerAction("late", "Late", () -> calls++), "no registration during shutdown");
            if (failShutdown) throw new IllegalArgumentException("owned-shutdown-failure");
        }
    }
    private static ModManifest manifest() {
        return new ModManifest(Path.of("build/context-fixture.jar"), "context", "Fixture", "1", Fixture.class.getName(), List.of(),
                Set.of("ui.register", "game.read", "events.career.read", "data.read", "data.write"));
    }
    private static void revoked() throws Exception {
        rejected(() -> retained.game(), "context game revoked");
        rejected(() -> retainedGame.snapshot(), "previously obtained game facade revoked");
        rejected(() -> retainedGame.scopedCalendar(), "calendar facade revoked after cleanup");
        rejected(() -> retainedGame.scopedClubBalance(null), "balance facade revoked before delegating");
        rejected(() -> retainedGame.scopedPlayerDetails(null, 0, 1), "detail facade revoked before delegating");
        rejected(() -> retainedGame.scopedPlayersWithReferences(null, 0, 1), "player-reference facade revoked before delegating");
        rejected(() -> retainedObservations.state(), "previously obtained observation facade revoked");
        rejected(() -> retainedObservations.subscribe(update -> { }), "retained observation subscribe revoked");
        rejected(() -> retained.registerAction("later", "Later", () -> calls++), "late action rejected");
        rejected(() -> retained.registerPanel("later-panel", "Later", theme -> null), "panel registration revoked after cleanup");
        rejected(() -> retained.careerData(), "career-data facade acquisition revoked after cleanup");
        rejected(() -> retained.registerClubBanner("later-banner", (club, scope) -> Optional.empty()), "banner registration revoked after cleanup");
        rejected(() -> {
            try { retained.writeData("later.txt", new byte[0]); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }, "late data write rejected");
        rejected(() -> {
            try { retained.readData("shutdown.txt"); } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }, "late data read rejected");
        registration.close(); registration.close();
    }
    public static void main(String[] args) throws Exception {
        Logger logger = Logger.getLogger("context-lifecycle-test"); logger.setUseParentHandlers(false);
        Path data = Files.createTempDirectory(Path.of("build"), "context-lifecycle-");
        GameAccess game = () -> new GameAccess.Snapshot(false, List.of());
        try (var hub = new CareerObservationHub(); var ui = new ModUi(new ClassLoader() { }, logger)) {
            // Use no EDT flush before invoke: dispatch must be revoked synchronously.
            AutoCloseable old = ui.registerOwned("reuse", "Old", () -> calls++);
            old.close(); ui.invoke("reuse"); check(calls == 0, "close revokes before EDT cleanup");
            AutoCloseable replacement = ui.registerOwned("reuse", "New", () -> calls++);
            old.close(); ui.invoke("reuse"); check(calls == 1, "old handle cannot remove replacement");
            replacement.close();
            Runnable identicalCallback = () -> calls++;
            AutoCloseable identicalOld = ui.registerOwned("identical", "Same", identicalCallback);
            identicalOld.close();
            AutoCloseable identicalNew = ui.registerOwned("identical", "Same", identicalCallback);
            identicalOld.close(); ui.invoke("identical");
            check(calls == 2, "identity ownership even with identical label and callback");
            identicalNew.close(); calls = 1;
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            javax.swing.SwingUtilities.invokeLater(() -> {
                entered.countDown();
                try { release.await(3, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                check(entered.await(3, java.util.concurrent.TimeUnit.SECONDS), "EDT parked for nonblocking cleanup fixture");
                AutoCloseable blockedEdt = ui.registerOwned("blocked", "Blocked", identicalCallback);
                executor.submit(() -> { try { blockedEdt.close(); } catch (Exception failure) { throw new AssertionError(failure); } }).get(1, java.util.concurrent.TimeUnit.SECONDS);
                ui.invoke("blocked"); check(calls == 1, "dispatch revoked without waiting for parked EDT");
            } finally { release.countDown(); executor.shutdownNow(); }
            var loader = new ModLoader(logger, hub);
            loader.load(List.of(manifest()), game, ui, data);
            boolean capabilityRefused = false;
            try { retained.registerPanel("missing-capability", "Panel", theme -> { throw new AssertionError("must not call factory"); }); }
            catch (SecurityException expected) { capabilityRefused = true; }
            check(capabilityRefused, "embedded capability must be explicitly declared");
            boolean careerDataCapabilityRefused = false;
            try { retained.careerData(); } catch (SecurityException expected) { careerDataCapabilityRefused = true; }
            check(careerDataCapabilityRefused, "career-scoped data requires explicit capability declaration");
            boolean bannerCapabilityRefused = false;
            try { retained.registerClubBanner("missing-banner-capability", (club, scope) -> Optional.empty()); }
            catch (SecurityException expected) { bannerCapabilityRefused = true; }
            check(bannerCapabilityRefused, "original toolbar banner requires an explicit capability");
            boolean playerReferenceRefused = false;
            try { retainedGame.scopedPlayersWithReferences(null, 0, 1); }
            catch (SecurityException expected) { playerReferenceRefused = true; }
            check(playerReferenceRefused, "temporary player handles require explicit capability");
            ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "both action APIs active");
            registration.close(); ui.invoke("context:scoped"); check(calls == 3, "scoped close immediate");
            loader.close(); ui.invoke("context:legacy"); check(calls == 3, "legacy action owned and removed on close");
            check(loader.fileStates().get("context-fixture.jar") == ModState.STOPPED, "normal stop state");
            revoked(); loader.close(); check(shutdowns == 1, "idempotent shutdown");
            rejected(() -> {
                try { loader.load(List.of(manifest()), game, ui, data); } catch (IllegalStateException expected) { throw expected; }
                catch (Exception failure) { throw new AssertionError(failure); }
            }, "closed loader cannot reload");
            for (boolean initFailure : List.of(true, false)) {
                failInitialize = initFailure; failShutdown = !initFailure;
                var failed = new ModLoader(logger, hub);
                try { failed.load(List.of(manifest()), game, ui, data); }
                catch (IllegalArgumentException expected) { if (!initFailure) throw expected; }
                if (initFailure) {
                    ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "partial initialization actions revoked");
                    revoked();
                }
                failed.close(); ui.invoke("context:legacy"); ui.invoke("context:scoped"); check(calls == 3, "failure cleanup actions revoked");
                check(failed.fileStates().get("context-fixture.jar") == ModState.FAILED, "failure state survives cleanup");
                revoked();
            }
            var multiple = new ModLoader(logger);
            multiple.load(List.of(
                    new ModManifest(Path.of("first.jar"), "first", "First", "1", First.class.getName(), List.of(), Set.of("ui.register")),
                    new ModManifest(Path.of("second.jar"), "second", "Second", "1", Second.class.getName(), List.of("first"), Set.of("ui.register"))), game, ui, data);
            ui.invoke("first:shared"); ui.invoke("second:shared"); check(calls == 5, "two owners can use same local action id");
            multiple.close(); ui.invoke("first:shared"); ui.invoke("second:shared");
            check(calls == 5, "two owners cleaned despite one shutdown failure");
            check(stopped.equals(List.of("second", "first")), "reverse shutdown continues after failure");
            check(multiple.fileStates().get("first.jar") == ModState.STOPPED && multiple.fileStates().get("second.jar") == ModState.FAILED, "independent terminal states");
        }
        try (var bannerUi = new ModUi(new ClassLoader() { }, logger); var bannerLoader = new ModLoader(logger)) {
            var bannerManifest = new ModManifest(Path.of("build/banner-fixture.jar"), "banner.fixture", "Banner", "1",
                    ClubBannerFeature.class.getName(), List.of(), Set.of("ui.register"), List.of(), List.of(), Set.of("ui.banner.club"));
            bannerLoader.load(List.of(bannerManifest), game, bannerUi, data);
            boolean duplicate = false;
            try { bannerUi.registerClubBanner("banner.fixture:logo", (club, scope) -> Optional.empty()); }
            catch (IllegalArgumentException expected) { duplicate = true; }
            check(duplicate, "loader owns and registers declared banner capability");
            bannerLoader.close();
            AutoCloseable afterClose = bannerUi.registerClubBanner("banner.fixture:logo", (club, scope) -> Optional.empty());
            afterClose.close();
            rejected(() -> retainedBannerContext.registerClubBanner("late", (club, scope) -> Optional.empty()), "closed banner mod cannot register again");
        }
        var sponsorCalls = new java.util.concurrent.atomic.AtomicInteger();
        SponsorAccess fakeSponsor = new SponsorAccess() {
            public CareerDataAccess.Scope bindCareer() { sponsorCalls.incrementAndGet(); return null; }
            public Quote quote(CareerDataAccess.Scope scope, UUID id, int clubId, String club, String name, int credit, String stadium) {
                sponsorCalls.incrementAndGet(); return null;
            }
            public Result activate(Quote quote) { sponsorCalls.incrementAndGet(); return null; }
            public State state(CareerDataAccess.Scope scope, UUID id) { sponsorCalls.incrementAndGet(); return State.NOT_ACTIVATED; }
            public EndQuote quoteEnd(CareerDataAccess.Scope scope, UUID id) { sponsorCalls.incrementAndGet(); return null; }
            public Result end(EndQuote quote) { sponsorCalls.incrementAndGet(); return null; }
            public Result publish(CareerDataAccess.Scope scope, UUID id, int clubId, String club, String name, int credit, String stadium, String message) {
                check(clubId == 12 && club.equals("Clube") && name.equals("Lubrax") && credit == 1234 && stadium.equals("Arena") && message.equals("Texto editado"),
                        "publish facade preserves exact club, amount, naming and custom text");
                sponsorCalls.incrementAndGet(); return new Result(State.PENDING_RECEIPT, "Published");
            }
            public Result cancelPending(CareerDataAccess.Scope scope, UUID id) { sponsorCalls.incrementAndGet(); return new Result(State.ENDED, "Cancelled"); }
        };
        for (int variant = 0; variant < 4; variant++) {
            try (var sponsorUi = new ModUi(new ClassLoader() { }, logger);
                 var sponsorLoader = new ModLoader(logger).sponsors(fakeSponsor)) {
                sponsorLoader.load(List.of(new ModManifest(Path.of("sponsor-fixture.jar"),
                        variant == 1 ? "wrong.owner" : SponsorActivationStore.OWNER, "Sponsor", "1", SponsorFeature.class.getName(), List.of(),
                        variant == 0 ? Set.of() : Set.of("sponsor.activate"), List.of(), List.of(),
                        variant == 2 ? Set.of() : Set.of("game.sponsor.activate"))), game, sponsorUi, data);
                if (variant < 3) {
                    boolean denied = false;
                    try { sponsorContext.sponsors(); } catch (SecurityException expected) { denied = true; }
                    check(denied, "sponsor service rejects missing permission, wrong owner or missing capability: " + variant);
                } else {
                    var facade = sponsorContext.sponsors();
                    facade.bindCareer(); facade.quote(null, null, 0, "", "", 1, ""); facade.activate(null); facade.state(null, null);
                    check(sponsorCalls.get() == 4, "authorized sponsor facade delegates narrow operations");
                    facade.quoteEnd(null, null); facade.end(null);
                    check(sponsorCalls.get() == 6, "authorized ending facade delegates quote and confirmation");
                    check(facade.publish(null, null, 12, "Clube", "Lubrax", 1234, "Arena", "Texto editado").state() == SponsorAccess.State.PENDING_RECEIPT,
                            "actual loader facade delegates publish instead of API default unsupported method");
                    check(facade.cancelPending(null, null).state() == SponsorAccess.State.ENDED && sponsorCalls.get() == 8,
                            "actual loader facade delegates pending cancellation");
                    sponsorLoader.close();
                    rejected(() -> { try { facade.activate(null); } catch (java.io.IOException failure) { throw new AssertionError(failure); } },
                            "retained sponsor facade cannot activate after shutdown");
                    rejected(() -> sponsorContext.sponsors(), "sponsor capability acquisition revoked after shutdown");
                    rejected(() -> { try { facade.end(null); } catch (java.io.IOException failure) { throw new AssertionError(failure); } }, "ending facade revoked after shutdown");
                    rejected(() -> { try { facade.publish(null, null, 12, "Clube", "Lubrax", 1234, "Arena", "Texto editado"); }
                        catch (java.io.IOException failure) { throw new AssertionError(failure); } }, "publish facade revoked after shutdown");
                    rejected(() -> { try { facade.cancelPending(null, null); }
                        catch (java.io.IOException failure) { throw new AssertionError(failure); } }, "pending cancel facade revoked after shutdown");
                    check(sponsorCalls.get() == 8, "revoked sponsor calls never reach provider");
                }
            }
        }
        GameAccess referenceGame = new GameAccess() {
            public Snapshot snapshot() { return new Snapshot(false, List.of()); }
            public ScopedPlayersWithReferencesPage scopedPlayersWithReferences(ClubRef club, int offset, int limit) {
                playerReferenceProviderCalls++;
                return null;
            }
        };
        try (var referenceUi = new ModUi(new ClassLoader() { }, logger); var referenceLoader = new ModLoader(logger)) {
            referenceLoader.load(List.of(new ModManifest(Path.of("player-reference.jar"), "player.references", "References", "1",
                    PlayerReferenceFeature.class.getName(), List.of(), Set.of("game.read"), List.of(), List.of(), Set.of("query.player.references"))),
                    referenceGame, referenceUi, data);
            check(playerReferenceModCalls == 1 && playerReferenceProviderCalls == 1, "declared reference capability delegates to scoped provider");
        }
        System.out.println("PASS: " + checks + " mod context/resource lifecycle checks (synthetic; no game).");
    }
}
