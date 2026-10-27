package br.vbrasfoot.sponsormanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic metadata transitions only; no game/save mutation. */
public final class SponsorModel {
    private SponsorModel() { }
    public static List<SponsorContract> forProfile(List<SponsorContract> all, String profile) {
        Objects.requireNonNull(all); Objects.requireNonNull(profile);
        return all.stream().filter(item -> item.profile().equals(profile)).toList();
    }
    public static List<SponsorContract> forClub(List<SponsorContract> all, int clubId, String clubName) {
        Objects.requireNonNull(all); Objects.requireNonNull(clubName);
        return all.stream().filter(item -> item.clubId() == clubId && item.clubName().equals(clubName)).toList();
    }
    public static List<SponsorContract> add(List<SponsorContract> all, SponsorContract added) throws IOException {
        Objects.requireNonNull(all); Objects.requireNonNull(added);
        if (all.size() >= SponsorStore.MAX_CONTRACTS) throw new IOException("Limite de configuracoes atingido");
        if (all.stream().anyMatch(item -> item.id().equals(added.id()))) throw new IOException("ID de contrato duplicado");
        if (!added.ended() && added.namingRights() && all.stream().anyMatch(item -> !item.ended() && item.namingRights()
                && item.clubId() == added.clubId() && item.clubName().equals(added.clubName())))
            throw new IOException("Ja existe uma configuracao de naming rights nao arquivada para esse clube");
        var next = new ArrayList<>(all); next.add(added); return List.copyOf(next);
    }
    public static List<SponsorContract> end(List<SponsorContract> all, UUID id, String profile) throws IOException {
        Objects.requireNonNull(all); Objects.requireNonNull(id); Objects.requireNonNull(profile);
        var next = new ArrayList<SponsorContract>(all.size()); boolean found = false;
        for (var item : all) {
            if (item.id().equals(id) && item.profile().equals(profile)) {
                if (item.ended()) throw new IOException("Configuracao ja arquivada");
                next.add(item.withEnded(true)); found = true;
            } else next.add(item);
        }
        if (!found) throw new IOException("Configuracao nao encontrada neste perfil");
        return List.copyOf(next);
    }
}
