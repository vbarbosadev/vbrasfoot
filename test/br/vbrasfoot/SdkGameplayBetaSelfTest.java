package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;

public final class SdkGameplayBetaSelfTest {
    private static int checks;
    private static ModContext context;
    public static final class Feature implements Mod { public void initialize(ModContext c) { context = c; } }
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); checks++; }
    private interface Action { void run() throws Exception; }
    private static void denied(Action action) throws Exception {
        try { action.run(); } catch (IOException | IllegalStateException | SecurityException expected) { checks++; return; }
        throw new AssertionError("Expected refusal");
    }
    public static void main(String[] args) throws Exception {
        var period = new TransferWindows.Period(LocalDate.of(2027,1,1), LocalDate.of(2027,1,31));
        var rules = new TransferWindows.Rules(true, List.of(period));
        check(rules.allows(period.start()) && rules.allows(period.end()), "window endpoints inclusive");
        check(!rules.allows(period.start().minusDays(1)) && !rules.allows(period.end().plusDays(1)), "outside dates denied without clock editing");
        check(!new TransferWindows.Rules(true, List.of()).allows(LocalDate.now()), "enabled empty policy denies all");
        check(new TransferWindows.Rules(false, List.of()).allows(LocalDate.now()), "disabled policy preserves behavior");
        boolean overlap = false; try { new TransferWindows.Rules(true, List.of(period,period)); } catch (IllegalArgumentException e) { overlap = true; }
        check(overlap, "overlapping windows rejected");
        Path root = Files.createTempDirectory(Path.of(System.getenv("LOCALAPPDATA"), "Temp", "opencode"), "sdk-gameplay-beta-").toRealPath();
        Path saves = Files.createDirectory(root.resolve("sav")), save = Files.write(saves.resolve("owned.s22"), new byte[]{1,2,3});
        var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(root.resolve("associations"));
        var handle = associations.create(observer.observe(saves,"owned.s22"), CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER).handle();
        Object primary = new Object(), secondary = new Object();
        var coordinator = new CareerAssociationCoordinator(saves, associations, new CareerSidecarStore(root.resolve("journals")),
                new CareerSidecarStore(root.resolve("requests")), new CareerSidecarStore(root.resolve("executions")), observer,
                new LoadedSaveVerifier(observer, () -> new CareerCommandDispatcher.Roots(primary,secondary), Duration.ofSeconds(2)), List.of());
        try (var attached = HookProbe.attachLifecycle(new CareerLifecycle())) {
            HookProbe.enterLoad("owned",false); HookProbe.loadSource(save.toString());
            var in = HookProbe.traceLoadInput(Files.newInputStream(save)); in.readAllBytes(); in.close();
            HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(primary,secondary); HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true);
            coordinator.selectCareerData(coordinator.review(Optional.of(handle))); var scope = coordinator.currentCareerDataScope();
            var disk = new CareerSidecarStore(root.resolve("windows")); var windows = new TransferWindowService(disk,coordinator,null);
            var author = windows.forMod("third.party"); var initial = author.current(scope);
            check(!initial.rules().enabled(), "missing per-career policy is unrestricted");
            var written = author.configure(scope,initial.revision(),rules);
            check(new TransferWindowService(disk,coordinator,null).forMod("another.mod").current(scope).equals(written), "policy roundtrips across service restart and records author");
            denied(() -> author.configure(scope,initial.revision(),new TransferWindows.Rules(false,List.of())));
            var closed = windows.forMod("another.mod").configure(scope,written.revision(),new TransferWindows.Rules(true,List.of()));
            check(closed.configuredBy().equals("another.mod"), "explicit current revision permits reviewed cross-mod change");
            denied(() -> windows.checkLoan(handle));
            var calls = new AtomicInteger(); var activeCapture = new AtomicReference<java.util.function.BooleanSupplier>();
            var quote = new LoanAccess.Quote(UUID.randomUUID(),"Player","Origin","Human",0,366,true);
            var logger = Logger.getLogger("sdk-gameplay-test"); logger.setUseParentHandlers(false);
            try (var ui = new ModUi(new ClassLoader(){},logger); var loader = new ModLoader(logger).gameplay((owner,active) -> {
                check(owner.equals("third.party"), "public loan service accepts third-party ID, no canonical-owner exception"); activeCapture.set(active);
                return new LoanAccess() {
                    public CareerDataAccess.Scope bindCareer() { calls.incrementAndGet(); return scope; }
                    public Quote quote(CareerDataAccess.Scope s, GameAccess.PlayerRef p, GameAccess.ClubRef o, GameAccess.ClubRef d) { calls.incrementAndGet(); return quote; }
                    public Result accept(Quote q) { calls.incrementAndGet(); return new Result(State.APPLIED_IN_MEMORY,"Owned fake effect"); }
                };
            },windows)) {
                var manifest = new ModManifest(Path.of("synthetic.jar"),"third.party","SDK Test","1",Feature.class.getName(),List.of(),
                        Set.of("game.read","loan.request","transfer.window.configure"),List.of(),List.of(),Set.of("game.loan.request","transfer.windows"));
                loader.load(List.of(manifest), () -> new GameAccess.Snapshot(false,List.of()), ui,root.resolve("data"));
                var loanFacade = context.loans();
                check(loanFacade.bindCareer().equals(scope) && loanFacade.quote(scope,null,null,null).equals(quote)
                        && loanFacade.accept(quote).state() == LoanAccess.State.APPLIED_IN_MEMORY && calls.get() == 3, "all public loan facade operations reach provider");
                var windowFacade = context.transferWindows();
                check(windowFacade.current(scope).equals(closed), "public window facade reaches provider");
                loader.close();
                check(!activeCapture.get().getAsBoolean(), "in-flight effect guard revoked when mod closes");
                denied(() -> loanFacade.accept(quote)); denied(() -> windowFacade.configure(scope,closed.revision(),rules));
                check(calls.get() == 3, "closed facade never reaches effect provider");
            }
            try (var ui = new ModUi(new ClassLoader(){},logger); var loader = new ModLoader(logger)) {
                loader.load(List.of(new ModManifest(Path.of("denied.jar"),"unprivileged","Denied","1",Feature.class.getName(),List.of(),Set.of())),
                        () -> new GameAccess.Snapshot(false,List.of()),ui,root.resolve("denied"));
                denied(() -> context.loans()); denied(() -> context.transferWindows());
            }
            var options = Main.Options.parse(new String[]{"--developer-mods","--probe-career-hooks"});
            Files.writeString(root.resolve("distribution-profile.txt"),"vbrasfoot-combined-v1");
            Main.applyDistributionProfile(options,root); check(options.developer && !options.combined, "author launcher overrides feature-only marker explicitly");
            Files.createDirectory(root.resolve("mods"));
            SponsorsOnlyProfileSelfTest.jar(root.resolve("mods/third-party.jar"),"third.party");
            check(Main.selectedManifests(options,root).get(0).id().equals("third.party"), "author profile selects third-party plugins instead of canonical sponsor restriction");
            denied(() -> ModLoader.requireObservationMode(List.of(new ModManifest(Path.of("hooks.jar"),"hooks","Hooks","1",Feature.class.getName(),List.of(),Set.of("loan.request"),List.of(),List.of(),Set.of("game.loan.request"))),false));
            check(Arrays.equals(Files.readAllBytes(save),new byte[]{1,2,3}) && coordinator.operationalSnapshot().status() == CareerOperationalContext.Status.NOT_BOUND,
                    "policy metadata never edits save or grants command authority");
        }
        System.out.println("PASS: "+checks+" SDK public-facade/window persistence/date/permission checks; synthetic, no original transfer performed. "+root);
    }
}
