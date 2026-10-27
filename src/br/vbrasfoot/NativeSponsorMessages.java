package br.vbrasfoot;

import java.awt.*;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;

/** Fingerprinted native message-centre integration, backed by loader sidecars and the existing credit pipeline. */
final class NativeSponsorMessages implements AutoCloseable {
    private final LegacyRuntime runtime;
    private final SponsorService service;
    private final Logger logger;
    private final javax.swing.Timer timer;
    private final Map<JPanel, SponsorMessagePanelBinding> bindings = new WeakHashMap<>();
    private boolean closed;
    NativeSponsorMessages(LegacyRuntime runtime, SponsorService service, Logger logger) throws Exception {
        this.runtime = runtime; this.service = service; this.logger = logger;
        fingerprint("a/bn.class", "b318e93c33d1b96142a47a1786dadeac24ad5966b55a8587e6a47955c313341f");
        fingerprint("a/br.class", "5ffd746dceb9cf83993c0c2ae56d9bd6a63fe80b353a180abed17a5d6813a65e");
        fingerprint("b/u.class", "8b7ad05ec334327878ab17322bb6dd926b430791421bb21ff3df090a044a11a5");
        fingerprint("components/as.class", "ae14042f7dd3053cec2bc7f7f0741ffe96f4cee1a327679da279f7373e215597");
        timer = new javax.swing.Timer(250, e -> scan()); timer.start();
    }
    private void fingerprint(String path, String digest) throws Exception {
        try (var in = runtime.findResource(path).openStream()) {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(digest))
                throw new IllegalStateException("Central de Mensagens mudou; integracao recusada");
        }
    }
    private static Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private void scan() {
        if (closed) return;
        try {
            int[] budget = {16384};
            for (Window window : Window.getWindows()) if (window.isShowing()) visit(window, budget, 0);
            for (var entry : new ArrayList<>(bindings.entrySet())) if (!entry.getKey().isDisplayable()) {
                entry.getValue().close(); bindings.remove(entry.getKey());
            }
        } catch (Exception error) { logger.log(Level.WARNING, "sponsor-messages inspection refused", error); }
    }
    private void visit(Container container, int[] budget, int depth) throws Exception {
        if (--budget[0] < 0 || depth > 64) throw new IllegalStateException("Limite de inspecao da central");
        if (container instanceof JPanel panel && panel.getClass().getName().equals("a.bn")
                && panel.getClass().getClassLoader() == runtime && !bindings.containsKey(panel)) attach(panel);
        for (Component c : container.getComponents()) if (c instanceof Container child) visit(child, budget, depth + 1);
    }
    @SuppressWarnings("unchecked")
    private void attach(JPanel panel) throws Exception {
        JTable table = (JTable)field(panel, "Ad");
        if (!table.getModel().getClass().getName().equals("b.u") || table.getModel().getClass().getClassLoader() != runtime)
            throw new IllegalStateException("Modelo nativo inesperado");
        var listeners = Arrays.stream(((DefaultListSelectionModel)table.getSelectionModel()).getListSelectionListeners())
                .filter(l -> l.getClass().getName().equals("a.br") && l.getClass().getClassLoader() == runtime).toList();
        if (listeners.size() != 1) throw new IllegalStateException("Callback da central inesperada");
        SponsorMessagePanelBinding[] holder = new SponsorMessagePanelBinding[1];
        var binding = new SponsorMessagePanelBinding(panel, table, (JPanel)field(panel, "vd"),
                (List<Object>)field(panel, "oh"), Class.forName("components.as", false, runtime), listeners.get(0),
                message -> receive(panel, holder[0], message));
        holder[0] = binding; bindings.put(panel, binding); refresh(panel, binding);
        logger.info("sponsor-messages: native central attached; saved sponsorship rows reused, no duplicate projection");
    }
    private void refresh(JPanel panel, SponsorMessagePanelBinding binding) {
        new SwingWorker<List<SponsorService.Message>, Void>() {
            protected List<SponsorService.Message> doInBackground() throws Exception { return service.inbox(); }
            protected void done() {
                try {
                    var messages = get();
                    if (closed || !panel.isDisplayable()) return;
                    binding.install(messages.stream().filter(service::current).toList(), message -> {
                        try { return Class.forName("components.as", false, runtime).getConstructor().newInstance(); }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                    });
                } catch (Exception failure) { binding.uncertain(); logger.log(Level.WARNING, "sponsor-messages refresh refused", failure); }
            }
        }.execute();
    }
    private void receive(JPanel panel, SponsorMessagePanelBinding binding, SponsorService.Message message) {
        binding.busy(true);
        new SwingWorker<br.vbrasfoot.api.SponsorAccess.Result, Void>() {
            protected br.vbrasfoot.api.SponsorAccess.Result doInBackground() throws Exception {
                var offer = message.offer();
                if (!CareerBridge.onEdt(() -> binding.current(message))) throw new IllegalStateException("Mensagem mudou; nenhum pagamento solicitado");
                var quote = service.quote(message.scope(), offer.sponsor(), offer.clubId(), offer.club(), offer.name(), offer.credit(), offer.stadium());
                boolean confirmed = CareerBridge.onEdt(() -> binding.current(message) && br.vbrasfoot.api.SponsorDialogs.showConfirmDialog(panel,
                        "Receber " + offer.credit() + " de " + offer.name() + "?\nSaldo: " + quote.balanceBefore() + " -> " + quote.balanceAfter()
                        + "\nEstadio: " + quote.stadiumBefore() + " -> " + quote.stadiumAfter()
                        + "\n\nRecebimento unico. Salve pelo jogo depois. Nao repetir se houver erro ou fechar sem salvar.",
                        "Confirmar recebimento", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION);
                if (!confirmed) return null;
                if (!CareerBridge.onEdt(() -> binding.current(message))) throw new IllegalStateException("Selecao mudou durante confirmacao");
                return service.activate(quote);
            }
            protected void done() {
                try {
                    var result = get();
                    if (!closed && panel.isDisplayable()) {
                        if (result != null) br.vbrasfoot.api.SponsorDialogs.showMessageDialog(panel, result.message(), "Recebimento de patrocinio",
                                result.state() == br.vbrasfoot.api.SponsorAccess.State.APPLIED_IN_MEMORY ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                        if (result == null) binding.busy(false);
                        refresh(panel, binding);
                    }
                } catch (Exception failure) {
                    binding.uncertain(); logger.log(Level.WARNING, "sponsor-messages receive refused; no automatic retry", failure);
                    if (!closed && panel.isDisplayable()) br.vbrasfoot.api.SponsorDialogs.showMessageDialog(panel,
                            "Operacao exige revisao. Nao repetir automaticamente.\n" + (failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage()),
                            "Recebimento exige revisao", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }
    public void close() {
        closed = true; timer.stop();
        CareerBridge.onEdt(() -> { for (var binding : bindings.values()) binding.close(); bindings.clear(); return null; });
    }
}
