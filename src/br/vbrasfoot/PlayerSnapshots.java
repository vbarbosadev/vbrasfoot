package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import java.util.Optional;

/** Only getters corroborated by the original player details UI; no legacy objects escape. */
final class PlayerSnapshots {
    private PlayerSnapshots() { }
    static PlayerDetails copy(Object source, boolean individualSkills) throws Exception {
        Player player = new Player(number(source, "gD"), (String) CareerBridge.call(source, "getNome"),
                number(source, "getIdade"), number(source, "getPosicao"), number(source, "fi"), number(source, "fk"));
        Optional<IndividualSkills> skills = individualSkills ? Optional.of(new IndividualSkills(
                number(source, "gK"), number(source, "gN"), number(source, "gM"), number(source, "gO"),
                number(source, "gP"), number(source, "gJ"), number(source, "gL"))) : Optional.empty();
        return new PlayerDetails(player, skills);
    }
    private static int number(Object source, String getter) throws Exception {
        return ((Number) CareerBridge.call(source, getter)).intValue();
    }
}
