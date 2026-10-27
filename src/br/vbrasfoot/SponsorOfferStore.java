package br.vbrasfoot;

import java.io.*;
import java.util.*;
import static br.vbrasfoot.CareerSidecarStore.*;

/** Durable unpaid messages only. Financial truth remains exclusively in SponsorActivationStore. */
final class SponsorOfferStore {
    private static final String OWNER = "br.vbrasfoot.sponsor-offers";
    record Offer(UUID sponsor, int clubId, String club, String name, int credit, String stadium,
                 String message, String date, boolean cancelled) {
        Offer {
            Objects.requireNonNull(sponsor);
            if (clubId < -1 || credit <= 0 || !text(club, 120) || !text(name, 100) || stadium == null
                    || stadium.length() > 120 || !text(message, 2000) || !text(date, 40))
                throw new IllegalArgumentException("Proposta de patrocinio invalida");
        }
        private static boolean text(String s, int max) { return s != null && !s.isBlank() && s.length() <= max; }
        Offer cancel() { return new Offer(sponsor, clubId, club, name, credit, stadium, message, date, true); }
        String body() {
            var values = Map.of("patrocinador", name, "clube", club, "valor",
                    java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(credit));
            var matcher = java.util.regex.Pattern.compile("\\{(patrocinador|clube|valor)\\}").matcher(message);
            return matcher.replaceAll(m -> java.util.regex.Matcher.quoteReplacement(values.get(m.group(1))));
        }
    }
    record Snapshot(String token, List<Offer> entries) { Snapshot { entries = List.copyOf(entries); } }
    private final CareerSidecarStore disk;
    private final Key key;
    SponsorOfferStore(CareerSidecarStore disk, CareerSaveAssociations.Handle handle) {
        this.disk = disk; key = new Key(handle.career(), handle.binding(), OWNER);
    }
    Snapshot read() throws IOException {
        var inventory = disk.inventoryBinding(key.career(), key.binding());
        if (inventory.tokens().keySet().stream().anyMatch(owner -> !owner.equals(OWNER)))
            throw new IOException("Namespace inesperado nas mensagens; preserve o historico");
        var value = disk.read(key, SponsorActivationStore.ANCHOR);
        if (value.value().isEmpty()) {
            if (!inventory.tokens().isEmpty()) throw new IOException("Historico de mensagens incompleto; nao recriar");
            return new Snapshot(EMPTY, List.of());
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(value.value().orElseThrow().payload()))) {
            if (value.value().orElseThrow().schema() != 1 || !in.readUTF().equals("vbrasfoot.sponsor-offers/1")) throw new IOException("Formato de mensagens desconhecido");
            int count = in.readInt(); if (count < 1 || count > 300) throw new IOException("Limite de mensagens");
            var entries = new ArrayList<Offer>(); var ids = new HashSet<UUID>();
            for (int i = 0; i < count; i++) {
                var offer = new Offer(UUID.fromString(in.readUTF()), in.readInt(), in.readUTF(), in.readUTF(), in.readInt(),
                        in.readUTF(), in.readUTF(), in.readUTF(), in.readBoolean());
                if (!ids.add(offer.sponsor())) throw new IOException("Mensagem duplicada");
                entries.add(offer);
            }
            if (in.available() != 0) throw new IOException("Dados extras nas mensagens");
            return new Snapshot(value.token(), entries);
        } catch (IllegalArgumentException bad) { throw new IOException("Mensagem invalida; preserve os dados", bad); }
    }
    Snapshot publish(Offer offer, String expected) throws IOException {
        Snapshot before = read();
        if (!before.token().equals(expected) || offer.cancelled() || before.entries().size() >= 300
                || before.entries().stream().anyMatch(e -> e.sponsor().equals(offer.sponsor())))
            throw new IOException("Mensagem ja publicada ou historico mudou; nao repetir");
        var next = new ArrayList<>(before.entries()); next.add(offer); return write(expected, next);
    }
    Snapshot cancel(UUID sponsor, String expected) throws IOException {
        Snapshot before = read();
        if (!before.token().equals(expected) || before.entries().stream().noneMatch(e -> e.sponsor().equals(sponsor) && !e.cancelled()))
            throw new IOException("Mensagem ausente/cancelada ou historico mudou");
        return write(expected, before.entries().stream().map(e -> e.sponsor().equals(sponsor) ? e.cancel() : e).toList());
    }
    private Snapshot write(String expected, List<Offer> entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF("vbrasfoot.sponsor-offers/1"); out.writeInt(entries.size());
            for (var e : entries) {
                out.writeUTF(e.sponsor().toString()); out.writeInt(e.clubId()); out.writeUTF(e.club()); out.writeUTF(e.name());
                out.writeInt(e.credit()); out.writeUTF(e.stadium()); out.writeUTF(e.message()); out.writeUTF(e.date()); out.writeBoolean(e.cancelled());
            }
        }
        var value = disk.write(key, SponsorActivationStore.ANCHOR, 1, expected, bytes.toByteArray());
        return new Snapshot(value.token(), entries);
    }
}
