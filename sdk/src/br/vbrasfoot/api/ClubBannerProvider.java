package br.vbrasfoot.api;

import java.util.Optional;

/** Fast, read-only provider called on Swing's EDT only while the club screen is visible. */
@FunctionalInterface
public interface ClubBannerProvider {
    Optional<ClubBanner> banner(GameAccess.ClubSummary currentHumanClub, GameAccess.CareerScope currentScope);
}
