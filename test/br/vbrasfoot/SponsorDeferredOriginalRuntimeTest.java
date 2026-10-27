package br.vbrasfoot;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Real original panel/credit/save on a private marked fixture. Decoder markers and human confirmation are ARTIFICIAL. */
public final class SponsorDeferredOriginalRuntimeTest {
    private static int checks;
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); checks++; }
    private static Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static JButton receive(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && "sponsor.central.receive".equals(button.getName())) return button;
            if (component instanceof Container child) { JButton found = receive(child); if (found != null) return found; }
        }
        return null;
    }
    private static void layout(Container root) { root.doLayout(); for (Component c : root.getComponents()) if (c instanceof Container child) layout(child); }
    private static void load(LegacyRuntime runtime, Path path) throws Exception {
        HookProbe.beforeOriginalIo("LOAD");
        HookProbe.enterLoad("input", false); HookProbe.loadSource(path.toString());
        var trace = HookProbe.traceLoadInput(Files.newInputStream(path)); trace.readAllBytes(); trace.close();
        boolean loaded = CareerBridge.onEdt(() -> (boolean) Class.forName("c.a", false, runtime).getMethod("c", String.class, boolean.class).invoke(null, "input", false));
        var adapter = new LegacySponsorAdapter(runtime); var roots = CareerBridge.onEdt(adapter.finance::roots);
        HookProbe.rootAssigned(); HookProbe.rootAssigned(); HookProbe.decodedLoad(roots.primary(), roots.secondary());
        HookProbe.loadInitializationPathFinished(); HookProbe.exitLoad(loaded);
        HookProbe.afterOriginalIo();
        check(loaded, "original decoder loads disposable input");
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]).toRealPath(), home = Path.of(args[1]).toAbsolutePath();
        if (!Files.isRegularFile(game.resolve(".sponsor-test-fixture")) || !game.equals(Path.of("").toAbsolutePath().toRealPath())
                || Files.exists(home)) throw new IllegalArgumentException("Fresh marked fixture/cwd/home required; never personal saves");
        Files.createDirectories(home);
        var oldLoader = Thread.currentThread().getContextClassLoader();
        try (var runtime = LegacyRuntime.prepare(Profile.verify(game), home.resolve("runtime"), false)) {
            Thread.currentThread().setContextClassLoader(runtime);
            Class.forName("best.M", true, runtime).getConstructor(boolean.class).newInstance(true);
            JFrame frame = CareerBridge.onEdt(() -> {
                var window = new JFrame("Disposable message fixture"); window.pack();
                Class.forName("best.n", true, runtime).getMethod("a", JFrame.class).invoke(null, window); return window;
            });
            try {
                var adapter = new LegacySponsorAdapter(runtime);
                SponsorOfferStore.Offer published;
                CareerSaveAssociations.Handle handle;
                FinanceDelta.Snapshot expected;
                try (var gate = new CareerCommandGate(); var interlock = new CareerIoInterlock(gate);
                     var lifecycle = HookProbe.attachLifecycle(new CareerLifecycle()); var attachedIo = HookProbe.attachIoInterlock(interlock)) {
                    load(runtime, game.resolve("sav/input.s22"));
                    Object human = CareerBridge.onEdt(() -> ((List<?>)CareerBridge.call(adapter.finance.roots().primary(), "aN")).get(0));
                    String club = CareerBridge.onEdt(() -> (String)CareerBridge.call(human, "getNome"));
                    var before = CareerBridge.onEdt(() -> adapter.finance.snapshot(human));
                    String stadium = CareerBridge.onEdt(() -> adapter.stadium(human));
                    String sponsoredStadium = "Arena Lubrax " + UUID.randomUUID().toString().substring(0, 6);
                    Path metadata = home.resolve("data/.career-state");
                    var observer = new SaveFileObserver(); var associations = new CareerSaveAssociations(metadata.resolve("associations"), game.resolve("sav"));
                    var association = associations.create(observer.observe(game.resolve("sav"), "input.s22"), CareerSaveAssociations.Confirmation.CREATE_NEW_CAREER);
                    handle = association.handle();
                    var coordinator = new CareerAssociationCoordinator(game.resolve("sav"), associations, new CareerSidecarStore(metadata.resolve("journals")),
                            new CareerSidecarStore(metadata.resolve("finance-requests")), new CareerSidecarStore(metadata.resolve("finance-execution-witnesses")),
                            new CareerSidecarStore(metadata.resolve("loan-requests")), new CareerSidecarStore(metadata.resolve("loan-continuation")), observer,
                            new LoadedSaveVerifier(observer, adapter.finance::roots, Duration.ofSeconds(5)), List.of(SponsorActivationStore.OWNER, LoanManagerPanel.OWNER))
                            .operational(new CareerOperationalContext(gate));
                    var service = new SponsorService(coordinator, runtime);
                    var provisionalScope = service.bindCareer(); UUID unsaved = UUID.randomUUID();
                    service.publish(provisionalScope, unsaved, -1, club, "Unsaved", 123, "", "Mensagem nao salva");
                    check(service.inbox().stream().anyMatch(e -> e.offer().sponsor().equals(unsaved)), "unsaved native message exists in current memory only");
                    coordinator.clearCareerDataSelection(); load(runtime, game.resolve("sav/input.s22"));
                    var freshScope = service.bindCareer();
                    check(service.inbox().isEmpty(), "reload without saving does not resurrect messages from durable sidecars");
                    try { service.quote(freshScope, unsaved, -1, club, "Unsaved", 123, ""); throw new AssertionError("Unsaved absent message received"); }
                    catch (IOException denied) { checks++; }
                    service.cancelPending(freshScope, unsaved);
                    check(service.inbox().isEmpty(), "cancelling orphan preserves audit but never inserts absent message");
                    var scope = service.bindCareer(); UUID id = UUID.randomUUID();
                    UUID cancelled = UUID.randomUUID();
                    service.publish(scope, cancelled, -1, club, "Cancelled", 100, "", "Nao receber");
                    var stale = service.quote(scope, cancelled, -1, club, "Cancelled", 100, "");
                    service.cancelPending(scope, cancelled);
                    try { service.activate(stale); throw new AssertionError("Cancelled quoted offer received"); }
                    catch (IOException refused) { checks++; }
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(human)).equals(before), "cancellation invalidates an already-issued receipt quote without touching money");
                    var publishedResult = service.publish(scope, id, -1, club, "Lubrax", 1_000_000, sponsoredStadium, "Ola {clube}!\nA {patrocinador} oferece {valor}.\nReceba quando desejar. <b>Texto literal</b>");
                    check(publishedResult.state() == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT, "service activation only publishes pending message");
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(human)).equals(before) && CareerBridge.onEdt(() -> adapter.stadium(human)).equals(stadium), "activation changes neither real money nor real stadium");
                    check(CareerBridge.onEdt(() -> new NativeSponsorSaveMessage(runtime).present(
                            new SponsorOfferStore.Offer(id, -1, club, "Lubrax", 1_000_000, sponsoredStadium, "placeholder", "01/01/2022", false))),
                            "publish inserts native message into live coach, not just private central projection");
                    var offers = new SponsorOfferStore(new CareerSidecarStore(metadata.resolve("sponsor-offers")), handle);
                    published = offers.read().entries().stream().filter(e -> e.sponsor().equals(id)).findFirst().orElseThrow();
                    CareerBridge.onEdt(() -> { Class.forName("c.a", false, runtime).getMethod("ae", String.class).invoke(null, "input"); return null; });
                    Files.copy(game.resolve("sav/input.s22"), game.resolve("sav/pending.s22"));
                    Files.writeString(home.resolve("expected-balances.txt"), "pending=" + before.balance() + "\npaid="
                            + FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, 1_000_000).balance());
                    coordinator.clearCareerDataSelection();
                    load(runtime, game.resolve("sav/input.s22"));
                    var observation = observer.observe(game.resolve("sav"), "input.s22");
                    associations.acceptRevision(handle, associations.inspect(handle).token(), observation, CareerSaveAssociations.Confirmation.ACCEPT_CHANGED_REVISION);
                    var reloadedScope = service.bindCareer();
                    Object reloadedHuman = CareerBridge.onEdt(() -> ((List<?>)CareerBridge.call(adapter.finance.roots().primary(), "aN")).get(0));
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(reloadedHuman)).equals(before), "original save/reload before receipt preserves unpaid balance");
                    check(offers.read().entries().stream().filter(e -> !e.cancelled()).toList().equals(List.of(published)) && service.state(reloadedScope, id) == br.vbrasfoot.api.SponsorAccess.State.PENDING_RECEIPT, "pending sidecar and exact message survive original save/reload");
                    var message = service.inbox().stream().filter(e -> e.offer().sponsor().equals(id)).findFirst().orElseThrow();
                    var attached = new CountDownLatch(1); var nativeCount = new AtomicInteger();
                    try (var integration = new NativeSponsorMessages(runtime, service, java.util.logging.Logger.getLogger("native-sponsor-fixture"))) {
                        CareerBridge.onEdt(() -> {
                            Object coach = CareerBridge.call(reloadedHuman, "ka");
                            var saved = (ArrayList<?>)CareerBridge.call(coach, "lQ");
                            JPanel actual = (JPanel)Class.forName("a.bn", true, runtime).getConstructor(JDialog.class, ArrayList.class, Class.forName("best.ah", false, runtime)).newInstance(null, saved, reloadedHuman);
                            JTable actualTable = (JTable)field(actual, "Ad"); nativeCount.set(actualTable.getRowCount());
                            actualTable.getModel().addTableModelListener(e -> { if (actualTable.getRowCount() == nativeCount.get()) attached.countDown(); });
                            frame.setContentPane(actual); frame.pack(); frame.setVisible(true); return null;
                        });
                        check(attached.await(15, TimeUnit.SECONDS), "production timer discovers real native central and loads durable messages through worker");
                        CareerBridge.onEdt(() -> { frame.setVisible(false); return null; });
                    }
                    var receipt = new CompletableFuture<br.vbrasfoot.api.SponsorAccess.Result>(); var callbacks = new AtomicInteger();
                    AtomicReference<SponsorMessagePanelBinding> bindingRef = new AtomicReference<>();
                    AtomicReference<JPanel> panelRef = new AtomicReference<>(); AtomicReference<JTable> tableRef = new AtomicReference<>();
                    AtomicReference<List<?>> savedMessages = new AtomicReference<>(); AtomicReference<List<?>> originalContents = new AtomicReference<>();
                    CareerBridge.onEdt(() -> {
                        Object coach = CareerBridge.call(reloadedHuman, "ka");
                        List<?> saved = (List<?>)CareerBridge.call(coach, "lQ");
                        savedMessages.set(saved); originalContents.set(saved == null ? List.of() : new ArrayList<>(saved));
                        ArrayList<Object> input = new ArrayList<>(originalContents.get());
                        if (input.isEmpty()) input.add(Class.forName("components.as", false, runtime).getConstructor().newInstance());
                        JPanel panel = (JPanel)Class.forName("a.bn", true, runtime).getConstructor(JDialog.class, ArrayList.class, Class.forName("best.ah", false, runtime)).newInstance(null, input, reloadedHuman);
                        var table = (JTable)field(panel, "Ad");
                        var nativeListener = Arrays.stream(((DefaultListSelectionModel)table.getSelectionModel()).getListSelectionListeners())
                                .filter(l -> l.getClass().getName().equals("a.br")).findFirst().orElseThrow();
                        @SuppressWarnings("unchecked") List<Object> rows = (List<Object>)field(panel, "oh");
                        var binding = new SponsorMessagePanelBinding(panel, table, (JPanel)field(panel, "vd"), rows,
                                Class.forName("components.as", false, runtime), nativeListener, selected -> {
                            callbacks.incrementAndGet(); bindingRef.get().busy(true);
                            CompletableFuture.runAsync(() -> {
                                try {
                                    var quote = service.quote(selected.scope(), id, -1, club, "Lubrax", 1_000_000, sponsoredStadium);
                                    receipt.complete(service.activate(quote)); // Test-only substitute for explicit human confirmation.
                                } catch (Throwable failure) { receipt.completeExceptionally(failure); }
                            });
                        });
                        bindingRef.set(binding); panelRef.set(panel); tableRef.set(table);
                        binding.install(List.of(message), ignored -> {
                            try { return Class.forName("components.as", false, runtime).getConstructor().newInstance(); }
                            catch (Exception bad) { throw new RuntimeException(bad); }
                        });
                        check(table.getRowCount() == input.size() && binding.selected() == message, "real native table reuses saved sponsorship without duplicate row");
                        int nativeIndex = -1;
                        for (int i = 0; i < rows.size(); i++) if (!NativeSponsorSaveMessage.matches(rows.get(i), id)) { nativeIndex = i; break; }
                        table.setRowSelectionInterval(nativeIndex, nativeIndex);
                        check(binding.selected() == null, "original selection callback still handles native messages");
                        for (int i = 0; i < rows.size(); i++) if (NativeSponsorSaveMessage.matches(rows.get(i), id)) table.setRowSelectionInterval(i, i);
                        panel.setSize(900, 440); layout(panel);
                        var image = new java.awt.image.BufferedImage(900, 440, java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                        javax.imageio.ImageIO.write(image, "PNG", home.resolve("native-message-preview.png").toFile());
                        JButton button = receive(panel);
                        check(button != null && button.isEnabled() && button.getText().contains("1.000.000,00"), "receive button appears inside original central with exact amount");
                        button.doClick(); button.doClick();
                        check(callbacks.get() == 1, "busy button rejects repeated click");
                        return null;
                    });
                    var result = receipt.get(30, TimeUnit.SECONDS);
                    check(result.state() == br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY, "native-panel click routes to original protected coordinator credit");
                    expected = FinanceDelta.predict(before, FinanceDelta.Direction.CREDIT_RAW_6, 1_000_000);
                    check(CareerBridge.onEdt(() -> adapter.finance.snapshot(reloadedHuman)).equals(expected), "only receipt credits exact real money once");
                    check(CareerBridge.onEdt(() -> adapter.stadium(reloadedHuman)).equals(sponsoredStadium), "stadium naming deferred until receipt");
                    var paid = service.inbox().stream().filter(e -> e.offer().sponsor().equals(id)).findFirst().orElseThrow();
                    CareerBridge.onEdt(() -> {
                        bindingRef.get().install(List.of(paid), ignored -> { try { return Class.forName("components.as", false, runtime).getConstructor().newInstance(); } catch (Exception bad) { throw new RuntimeException(bad); } });
                        check(!receive(panelRef.get()).isEnabled() && receive(panelRef.get()).getText().equals("Patrocinio recebido"), "paid native message cannot be clicked again");
                        check(savedMessages.get() == null || savedMessages.get().equals(originalContents.get()), "native saved message collection never gains synthetic messages");
                        bindingRef.get().close();
                        check(tableRef.get().getRowCount() == originalContents.get().size() || originalContents.get().isEmpty() && tableRef.get().getRowCount() == 1, "closing binding removes projection and restores original table");
                        Class.forName("c.a", false, runtime).getMethod("ae", String.class).invoke(null, "input"); return null;
                    });
                    try { service.quote(reloadedScope, id, -1, club, "Lubrax", 1_000_000, sponsoredStadium); throw new AssertionError("duplicate receipt"); }
                    catch (IOException expectedRefusal) { checks++; }
                }
                // Simulated process restart: original decoder and new store instance, no in-memory gate authority retained.
                check(CareerBridge.onEdt(() -> (boolean)Class.forName("c.a", false, runtime).getMethod("c", String.class, boolean.class).invoke(null, "input", false)), "paid save loads through original decoder after session closes");
                Object finalHuman = CareerBridge.onEdt(() -> ((List<?>)CareerBridge.call(adapter.finance.roots().primary(), "aN")).get(0));
                check(CareerBridge.onEdt(() -> adapter.finance.snapshot(finalHuman)).equals(expected), "receipt persists through original save/reload");
                var ledger = new SponsorActivationStore(new CareerSidecarStore(home.resolve("data/.career-state/sponsor-activations")), handle);
                var old = ledger.read(); var entry = old.entries().get(0);
                try { ledger.claim(entry.sponsor(), entry.termsDigest(), entry.request(), old.token()); throw new AssertionError("reopened replay"); }
                catch (IOException expectedRefusal) { checks++; }
                check(new SponsorOfferStore(new CareerSidecarStore(home.resolve("data/.career-state/sponsor-offers")), handle).read().entries().stream().filter(e -> !e.cancelled()).toList().equals(List.of(published)), "paid message remains historical; financial ledger prevents replay, not message deletion");
                check(CareerBridge.onEdt(() -> {
                    Object coach = CareerBridge.call(finalHuman, "ka");
                    var messages = (List<?>)CareerBridge.call(coach, "lQ");
                    return messages.stream().filter(row -> NativeSponsorSaveMessage.matches(row, published.sponsor())).count() == 1;
                }), "paid native message survives original save/reload with same UUID and no duplicate");
            } finally { CareerBridge.onEdt(() -> { frame.dispose(); return null; }); }
        } finally { Thread.currentThread().setContextClassLoader(oldLoader); }
        System.out.println("PASS: " + checks + " real native central/deferred credit/save checks. Artificial decoder markers and confirmation; disposable fixture only.");
    }
}
