package br.vbrasfoot;

import java.util.*;
import javax.swing.SwingUtilities;

/** Writes ONLY existing native message fields. No new class/field is introduced into Kryo saves. */
final class NativeSponsorSaveMessage {
    private static final String PREFIX = "vbrasfoot:sponsor-message:1:";
    private final LegacyRuntime runtime;
    private final LegacySponsorAdapter adapter;
    NativeSponsorSaveMessage(LegacyRuntime runtime) throws Exception {
        this.runtime = runtime; adapter = new LegacySponsorAdapter(runtime);
        try (var in = runtime.findResource("components/as.class").openStream()) {
            if (!HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))
                    .equals("ae14042f7dd3053cec2bc7f7f0741ffe96f4cee1a327679da279f7373e215597")) throw new IllegalStateException("Formato nativo de mensagem mudou");
        }
    }
    static String marker(UUID id) { return PREFIX + id; }
    static boolean matches(Object row, UUID id) {
        try { return row != null && row.getClass().getName().equals("components.as") && marker(id).equals(CareerBridge.call(row, "vw")); }
        catch (Exception invalid) { return false; }
    }
    @SuppressWarnings("unchecked")
    private List<Object> rows(SponsorOfferStore.Offer offer) throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Mensagem exige EDT");
        Object club = adapter.finance.humanClub(offer.clubId(), offer.club());
        Object coach = CareerBridge.call(club, "ka");
        if (coach == null) throw new IllegalStateException("Treinador da carreira indisponivel");
        Object value = CareerBridge.call(coach, "lQ");
        if (!(value instanceof ArrayList<?> list) || list.size() > 10000) throw new IllegalStateException("Lista nativa indisponivel/acima do limite");
        return (List<Object>)list;
    }
    boolean present(SponsorOfferStore.Offer offer) throws Exception {
        return rows(offer).stream().filter(row -> matches(row, offer.sponsor())).count() == 1;
    }
    Object rootsPrimary() throws Exception { return adapter.finance.roots().primary(); }
    Object rootsSecondary() throws Exception { return adapter.finance.roots().secondary(); }
    void update(SponsorOfferStore.Offer offer, br.vbrasfoot.api.SponsorAccess.State state, boolean create) throws Exception {
        var rows = rows(offer);
        var found = rows.stream().filter(row -> matches(row, offer.sponsor())).toList();
        if (found.size() > 1) throw new IllegalStateException("Mensagem nativa duplicada; preserve o save");
        if (found.isEmpty() && !create) return; // Never resurrect an unsaved/deleted message from a sidecar.
        Object row = found.isEmpty() ? Class.forName("components.as", false, runtime).getConstructor().newInstance() : found.get(0);
        String status = switch (state) {
            case PENDING_RECEIPT -> "Valor disponivel. Abra o jogo pelo mod para receber este patrocinio.";
            case APPLIED_IN_MEMORY -> "Patrocinio recebido. Este registro nao autoriza receber novamente.";
            case ENDED -> "Patrocinio encerrado/cancelado. Recebimento bloqueado.";
            default -> "Recebimento exige revisao. Nao repita o pagamento.";
        };
        // Native type 9 is a harmless informational 'Agradecimento' subject prefix. -1 omits template text.
        set(row, "RU", 9); set(row, "RV", -1); set(row, "RW", " — Patrocinio: " + offer.name());
        set(row, "RX", escape(offer.body()) + "<br><br>" + escape(status)); set(row, "data", offer.date());
        set(row, "Sb", marker(offer.sponsor())); set(row, "RZ", false); set(row, "Sc", -1); set(row, "oq", -1);
        if (found.isEmpty()) rows.add(row);
    }
    private static void set(Object row, String name, Object value) throws Exception {
        var field = row.getClass().getDeclaredField(name); field.setAccessible(true); field.set(row, value);
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\r\n", "\n").replace("\n", "<br>");
    }
}
