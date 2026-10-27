package br.vbrasfoot;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.*;

/** Qualified native player-screen integration. All writes remain behind the session coordinator. */
final class NativeLoanUi implements AutoCloseable {
    private static final String PROPERTY = "vbrasfoot.native-loan.binding";
    private final LegacyRuntime runtime;
    private final CareerBridge bridge;
    private final CareerAssociationCoordinator coordinator;
    private final NativeLoanAssociation associations;
    private final Logger logger;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Set<JButton> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final javax.swing.Timer timer;
    private boolean closed;
    private record Selection(JPanel panel, Object player, Object origin, Object destination, CareerBridge.LoanReferences refs) { }
    NativeLoanUi(LegacyRuntime runtime, CareerBridge bridge, CareerAssociationCoordinator coordinator, Path metadata, Path preference, Logger logger) throws Exception {
        this.runtime = runtime; this.bridge = bridge; this.coordinator = coordinator; this.logger = logger;
        associations = new NativeLoanAssociation(metadata, preference, coordinator);
        fingerprint("a/iA.class", "0a1c38453bccc1965e981819e792e68c78d7ecb45f1504a9044e6eccdcc187af");
        fingerprint("a/iC.class", "668840c6623ec1e810880762256e00297b456179a785d885ebb2ed2d7df59e1a");
        timer = new javax.swing.Timer(250, event -> install()); timer.start();
    }
    private void fingerprint(String name, String expected) throws Exception {
        var resource = runtime.findResource(name); if (resource == null) throw new IllegalStateException("Tela de jogadores indisponivel");
        try (var input = resource.openStream()) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024 || !expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw new IllegalStateException("Tela nativa de jogadores mudou; integracao recusada");
        }
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var value = owner.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(owner);
    }
    private void install() {
        if (closed) return;
        int[] remaining = {16384};
        try { for (Window window : Window.getWindows()) if (window.isDisplayable() && window.isShowing()) scan(window, remaining, 0); }
        catch (Exception failure) { logger.warning("native-loan UI inspection refused: " + failure.getMessage()); }
    }
    private void scan(Container root, int[] remaining, int depth) throws Exception {
        if (--remaining[0] < 0 || depth > 64) throw new IllegalStateException("Limite de inspecao da interface");
        if (root instanceof JPanel panel && panel.isShowing() && panel.getClass().getName().equals("a.iA") && panel.getClass().getClassLoader() == runtime) {
            JButton button = (JButton) field(panel, "MJ");
            if (button.getClientProperty(PROPERTY) == null) {
                var listeners = button.getActionListeners();
                if (listeners.length != 1 || !listeners[0].getClass().getName().equals("a.iC") || listeners[0].getClass().getClassLoader() != runtime)
                    throw new IllegalStateException("Callback nativa inesperada; nao acumular handlers");
                var owner = new WeakReference<>(panel);
                var binding = new NativeLoanButtonBinding(button, () -> !closed && !busy.get() && selectable(owner.get()), () -> request(owner.get()));
                button.putClientProperty(PROPERTY, binding); installed.add(button);
                logger.info("native-loan: callback nativa substituida no botao da tela de jogadores; nenhum efeito executado");
            }
            ((NativeLoanButtonBinding) button.getClientProperty(PROPERTY)).update();
        }
        for (Component component : root.getComponents()) if (component instanceof Container child) scan(child, remaining, depth + 1);
    }
    private boolean selectable(JPanel panel) {
        try { return panel != null && panel.isShowing() && field(panel, "yK") != null && field(panel, "ul") != null; }
        catch (Exception invalid) { return false; }
    }
    private Selection capture(JPanel panel) throws Exception {
        if (!selectable(panel)) throw new IllegalStateException("Abra o clube e selecione um jogador");
        Object player = field(panel, "yK"), destination = field(panel, "ul"), origin = field(panel, "uk");
        if (CareerBridge.call(player, "fg") != origin) throw new IllegalStateException("O jogador nao pertence mais ao clube exibido; atualize a tela");
        return new Selection(panel, player, origin, destination, bridge.loanReferences(player, origin, destination));
    }
    private boolean current(Selection selection) {
        try { return !closed && selection.panel().isShowing() && field(selection.panel(), "yK") == selection.player()
                    && field(selection.panel(), "uk") == selection.origin()
                    && field(selection.panel(), "ul") == selection.destination() && CareerBridge.call(selection.player(), "fg") == selection.origin(); }
        catch (Exception changed) { return false; }
    }
    private void request(JPanel panel) {
        if (!busy.compareAndSet(false, true)) return;
        final Selection selected;
        try { selected = capture(panel); }
        catch (Exception failure) {
            busy.set(false); logger.log(Level.WARNING, "native-loan capture refused", failure);
            message(panel, NativeLoanPresentation.refused(), JOptionPane.WARNING_MESSAGE); return;
        }
        install();
        final JButton progress;
        try { progress = (JButton)field(panel, "MJ"); progress.setText("Avaliando..."); }
        catch (Exception unavailable) {
            busy.set(false); logger.log(Level.WARNING, "native-loan progress unavailable", unavailable); return;
        }
        new SwingWorker<String, Void>() {
            private boolean executionStarted;
            private long phaseStarted = System.nanoTime();
            private void timed(String stage) {
                long now = System.nanoTime();
                logger.info("native-loan timing stage=" + stage + " elapsedMs=" + ((now - phaseStarted) / 1_000_000)); phaseStarted = now;
            }
            @Override protected String doInBackground() throws Exception {
                var adapter = new LegacyLoanAdapter(runtime, () -> current(selected));
                var refs = selected.refs();
                var eligibility = CareerBridge.onEdt(() -> adapter.eligibility(bridge, refs.player(), refs.origin(), refs.destination()));
                timed("eligibility");
                logger.info("native-loan eligibility: " + eligibility.report());
                if (!eligibility.eligible()) return eligibility.userMessage();
                var handle = associations.resolve(panel, adapter);
                timed("association");
                var quote = coordinator.quoteNativeLoan(handle, bridge, adapter, refs.player(), refs.origin(), refs.destination());
                timed("quote");
                logger.info("native-loan quote: " + quote.report());
                if (quote.request().assessment().decision() != LoanClubAssessment.Decision.APPROVE)
                    return NativeLoanPresentation.refused();
                logger.info("native-loan route=" + (quote.request().nativeListed() ? "listed-direct" : "proposal-confirmation"));
                boolean confirm = CareerBridge.onEdt(() -> NativeLoanFlow.proceed(quote.request(), () -> current(selected),
                        () -> NativeLoanDialog.show(panel, quote.request().player().name(), NativeLoanPresentation.confirmationBody(quote.request()), true)));
                if (!confirm) return NativeLoanPresentation.cancelled();
                phaseStarted = System.nanoTime(); // Human confirmation time is not execution latency.
                SwingUtilities.invokeLater(() -> { if (!closed) progress.setText("Executando..."); });
                executionStarted = true;
                var result = coordinator.executeNativeLoan(quote, adapter);
                timed("execution");
                logger.info("native-loan execution: " + result.kind() + " / " + result.diagnostic());
                return NativeLoanPresentation.result(result.kind(), quote.request().fee());
            }
            @Override protected void done() {
                try {
                    String result = get(); logger.info("native-loan result=" + result.replace('\n', ' '));
                    refresh(selected);
                    if (!closed && panel.isDisplayable()) message(panel, playerName(selected), result);
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    if (!(cause instanceof CancellationException) || executionStarted) {
                        refresh(selected);
                        logger.log(Level.WARNING, "native-loan review required", cause);
                        if (!closed && panel.isDisplayable()) message(panel, playerName(selected), executionStarted
                                ? NativeLoanPresentation.uncertain() : NativeLoanPresentation.failure(cause));
                    }
                } finally { busy.set(false); if (!closed) { progress.setText(NativeLoanButtonBinding.LABEL); install(); } }
            }
        }.execute();
    }
    private void refresh(Selection selected) {
        try {
            if (!closed && selected.panel().isShowing() && field(selected.panel(), "uk") == selected.origin()
                    && CareerBridge.call(selected.player(), "fg") == selected.destination()) {
                var refresh = selected.panel().getClass().getDeclaredMethod("sH"); refresh.setAccessible(true);
                refresh.invoke(selected.panel()); // View rebuild and balance label only, no native effect callback.
                selected.panel().revalidate(); selected.panel().repaint();
            }
        } catch (Exception failure) { logger.warning("native-loan: efeito nao pode ser repetido por falha de refresh: " + failure.getMessage()); }
    }
    private static void message(Component parent, String text, int type) {
        message(parent, "", text);
    }
    private static void message(Component parent, String player, String text) {
        NativeLoanDialog.show(parent, player, text, false);
    }
    private static String playerName(Selection selection) {
        try { return String.valueOf(CareerBridge.call(selection.player(), "getNome")); }
        catch (Exception unavailable) { return ""; }
    }
    @Override public void close() {
        if (closed) return; closed = true; timer.stop();
        for (JButton button : List.copyOf(installed)) {
            var binding = button.getClientProperty(PROPERTY);
            if (binding instanceof NativeLoanButtonBinding owned) owned.close();
            button.putClientProperty(PROPERTY, null);
        }
        installed.clear();
    }
}
