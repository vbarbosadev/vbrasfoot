package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.ClubBanner;
import br.vbrasfoot.api.CareerDataAccess;
import br.vbrasfoot.api.GameAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** In-memory read-only projection of the explicitly featured logo. */
final class SponsorBanner {
    private record State(List<SponsorContract> contracts, Optional<UUID> featured, Optional<GameAccess.CareerScope> scope,
                         Optional<CareerDataAccess.Scope> dataScope) {
        State { contracts = List.copyOf(contracts); }
    }
    private final CareerDataAccess careerData;
    SponsorBanner(CareerDataAccess careerData) { this.careerData = careerData; }
    private volatile State state = new State(List.of(), Optional.empty(), Optional.empty(), Optional.empty());

    synchronized void replace(List<SponsorContract> contracts, Optional<UUID> featured, Optional<GameAccess.CareerScope> scope,
                              Optional<CareerDataAccess.Scope> dataScope) {
        state = new State(contracts, featured, scope, dataScope);
    }
    synchronized void updateContracts(List<SponsorContract> contracts) {
        state = new State(contracts, state.featured(), state.scope(), state.dataScope());
    }
    synchronized void featured(Optional<UUID> featured) {
        state = new State(state.contracts(), featured, state.scope(), state.dataScope());
    }
    synchronized void clear() { state = new State(List.of(), Optional.empty(), Optional.empty(), Optional.empty()); }
    Optional<UUID> featured() { return state.featured(); }

    Optional<ClubBanner> banner(GameAccess.ClubSummary club, GameAccess.CareerScope scope) {
        if (club == null || scope == null) return Optional.empty();
        State snapshot = state;
        if (snapshot.featured().isEmpty() || snapshot.scope().filter(scope::equals).isEmpty()
                || snapshot.dataScope().filter(careerData::isSelected).isEmpty()) return Optional.empty();
        return snapshot.contracts().stream()
                .filter(item -> item.id().equals(snapshot.featured().orElseThrow()) && !item.ended())
                 .filter(item -> item.clubId() == club.id() && item.clubName().equals(club.name()))
                 .filter(item -> item.logoPng().length > 0)
                .findFirst()
                .map(item -> new ClubBanner(item.clubId(), item.clubName(), "Prévia: " + item.sponsorName(), item.logoPng(), scope));
    }
}
