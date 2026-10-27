package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Real game adapter/coordinator/facade; manual decoder markers and confirmation are ARTIFICIAL. Private marked fixture only. */
public final class PublicLoanOriginalRuntimeTest {
    private static ModContext context;
    private static int checks;
    public static final class Feature implements Mod { public void initialize(ModContext c) { context = c; } }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private static List<?> list(Object object, String method) throws Exception { return (List<?>)CareerBridge.call(object,method); }
    private static long number(Object object,String method) throws Exception { return ((Number)CareerBridge.call(object,method)).longValue(); }
    private interface Action { void run() throws Exception; }
    private static void denied(Action action) throws Exception { try { action.run(); } catch (java.io.IOException | IllegalStateException expected) { checks++; return; } throw new AssertionError("Expected refusal"); }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".sponsor-test-fixture")) || !game.equals(Path.of("").toAbsolutePath().toRealPath()) || Files.exists(home))
            throw new IllegalArgumentException("Fresh private marked game/home required");
        String inputHash = Profile.digest(game.resolve("sav/input.s22"));
        var previous = Thread.currentThread().getContextClassLoader();
        try (var runtime = LegacyRuntime.prepare(Profile.verify(game),home.resolve("runtime"),false)) {
            Thread.currentThread().setContextClassLoader(runtime);
            Class.forName("best.M",true,runtime).getConstructor(boolean.class).newInstance(true);
            JFrame frame = CareerBridge.onEdt(() -> { var f = new JFrame("Private SDK loan test"); f.pack(); Class.forName("best.n",true,runtime).getMethod("a",JFrame.class).invoke(null,f); return f; });
            try {
                var adapter = new LegacyLoanAdapter(runtime); var bridge = new CareerBridge(runtime);
                String movedName; long movedId, balance, clock; int trackers;
                try (var gate = new CareerCommandGate(); var interlock = new CareerIoInterlock(gate);
                     var lifecycle = HookProbe.attachLifecycle(new CareerLifecycle()); var io = HookProbe.attachIoInterlock(interlock)) {
                    HookProbe.beforeOriginalIo("LOAD"); HookProbe.enterLoad("input",false); HookProbe.loadSource(game.resolve("sav/input.s22").toString());
                    var trace = HookProbe.traceLoadInput(Files.newInputStream(game.resolve("sav/input.s22"))); trace.readAllBytes(); trace.close();
                    check(CareerBridge.onEdt(() -> (boolean)Class.forName("c.a",false,runtime).getMethod("c",String.class,boolean.class).invoke(null,"input",false)), "original loader accepts seed");
                    var roots = CareerBridge.onEdt(adapter::roots);
                    HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(roots.primary(),roots.secondary()); HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(true); HookProbe.afterOriginalIo();
                    var observer = new SaveFileObserver(); Path metadata = home.resolve("data/.career-state");
                    var associations = new CareerSaveAssociations(metadata.resolve("associations"),game.resolve("sav"));
                    var handle = associations.create(observer.observe(game.resolve("sav"),"input.s22"),CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER).handle();
                    var coordinator = new CareerAssociationCoordinator(game.resolve("sav"),associations,new CareerSidecarStore(metadata.resolve("journals")),
                            new CareerSidecarStore(metadata.resolve("finance-requests")),new CareerSidecarStore(metadata.resolve("witnesses")),
                            new CareerSidecarStore(metadata.resolve("loan-requests")),new CareerSidecarStore(metadata.resolve("loan-continuation")),observer,
                            new LoadedSaveVerifier(observer,adapter::roots,Duration.ofSeconds(5)),List.of(LoanManagerPanel.OWNER,"third.party"))
                            .operational(new CareerOperationalContext(gate));
                    var windows = new TransferWindowService(new CareerSidecarStore(metadata.resolve("transfer-windows")),coordinator,bridge); coordinator.transferWindows(windows);
                    var logger = java.util.logging.Logger.getLogger("public-loan-original"); logger.setUseParentHandlers(false);
                    try (var ui = new ModUi(runtime,logger); var loader = new ModLoader(logger).gameplay((owner,active) -> new LoanService(coordinator,runtime,bridge,active),windows)) {
                        loader.load(List.of(new ModManifest(Path.of("public-loan-fixture.jar"),"third.party","Third Party","1",Feature.class.getName(),List.of(),
                                Set.of("game.read","loan.request","transfer.window.configure"),List.of(),List.of(),Set.of("game.loan.request","transfer.windows"))),bridge,ui,home.resolve("mod-data"));
                        var loans = context.loans(); var scope = loans.bindCareer(); var window = context.transferWindows();
                        Object target = CareerBridge.onEdt(() -> list(roots.primary(),"aN").get(0));
                        balance = CareerBridge.onEdt(() -> number(target,"kb")); clock = CareerBridge.onEdt(() -> number(roots.primary(),"bc")); trackers = CareerBridge.onEdt(() -> list(roots.primary(),"bt").size());
                        check(trackers < 3, "private fixture has room below loan cap");
                        var refs = CareerBridge.onEdt(() -> {
                            var counts = new HashMap<String,Integer>();
                            for(Object c:list(roots.primary(),"P")) counts.merge(CareerBridge.call(c,"gD")+"/"+CareerBridge.call(c,"getNome"),1,Integer::sum);
                            for(Object club:list(roots.primary(),"P")) if(club!=target && counts.get(CareerBridge.call(club,"gD")+"/"+CareerBridge.call(club,"getNome"))==1)
                                for(Object player:list(club,"kc")) if(Boolean.TRUE.equals(CareerBridge.call(player,"fz"))) {
                                    var r = bridge.loanReferences(player,club,target);
                                    if(adapter.eligibility(bridge,r.player(),r.origin(),r.destination()).eligible()) return r;
                                }
                            throw new AssertionError("No naturally eligible listed player");
                        });
                        var date = bridge.scopedCalendar().date(); var today = LocalDate.of(date.year(),date.month(),date.day());
                        var old = window.current(scope);
                        var closed = window.configure(scope,old.revision(),new TransferWindows.Rules(true,List.of(new TransferWindows.Period(today.plusDays(1),today.plusDays(10)))));
                        denied(() -> loans.quote(scope,refs.player(),refs.origin(),refs.destination()));
                        check(CareerBridge.onEdt(() -> list(roots.primary(),"bt").size())==trackers, "closed dated window blocks quote without tracker effect");
                        var open = window.configure(scope,closed.revision(),new TransferWindows.Rules(true,List.of(new TransferWindows.Period(today,today))));
                        var stale = loans.quote(scope,refs.player(),refs.origin(),refs.destination());
                        closed = window.configure(scope,open.revision(),new TransferWindows.Rules(true,List.of()));
                        denied(() -> loans.accept(stale));
                        check(CareerBridge.onEdt(() -> list(roots.primary(),"bt").size())==trackers, "policy change between quote and accept blocks transfer");
                        window.configure(scope,closed.revision(),new TransferWindows.Rules(true,List.of(new TransferWindows.Period(today,today))));
                        var quote = loans.quote(scope,refs.player(),refs.origin(),refs.destination());
                        check(quote.listed() && quote.fee()==0, "public DTO exposes original listed zero-fee route");
                        movedName = quote.player(); movedId = CareerBridge.onEdt(() -> number(bridge.loanSelection(refs.player(),refs.origin(),refs.destination()).player(),"gD"));
                        check(loans.accept(quote).state()==LoanAccess.State.APPLIED_IN_MEMORY, "third-party facade executes actual protected coordinator transfer");
                        denied(() -> loans.accept(quote));
                        check(CareerBridge.onEdt(() -> list(roots.primary(),"bt").size())==trackers+1, "one tracker, no duplicate on reused token");
                        check(CareerBridge.onEdt(() -> number(target,"kb"))==balance && CareerBridge.onEdt(() -> number(roots.primary(),"bc"))==clock, "window/transfer preserve original balance and clock");
                        CareerBridge.onEdt(() -> { Class.forName("c.a",false,runtime).getMethod("ae",String.class).invoke(null,"output"); return null; });
                    }
                }
                check(CareerBridge.onEdt(() -> (boolean)Class.forName("c.a",false,runtime).getMethod("c",String.class,boolean.class).invoke(null,"output",false)), "original decoder reopens output");
                var after = CareerBridge.onEdt(adapter::roots);
                final String name = movedName; final long id = movedId;
                check(CareerBridge.onEdt(() -> list(list(after.primary(),"aN").get(0),"kc").stream().filter(p -> { try { return number(p,"gD")==id && CareerBridge.call(p,"getNome").equals(name); } catch(Exception e) { throw new RuntimeException(e); } }).count())==1,
                        "loan player persists uniquely through original save/reload");
                check(Profile.digest(game.resolve("sav/input.s22")).equals(inputHash), "baseline file untouched");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { Thread.currentThread().setContextClassLoader(previous); }
        System.out.println("PASS: "+checks+" public loan/window checks against original runtime/save. Artificial decoder markers/confirmation, disposable fixture only.");
    }
}
