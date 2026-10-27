package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.SponsorAccess;
import java.util.UUID;

public final class SponsorActivationNoticeTest {
    public static void main(String[] args) {
        var quote = new SponsorAccess.Quote(UUID.randomUUID(), UUID.randomUUID(), "Flamengo", "REDBULL", 3_000_000,
                1_000_000, 4_000_000, "Original", "Arena");
        String success = SponsorActivationNotice.message(quote,
                new SponsorAccess.Result(SponsorAccess.State.APPLIED_IN_MEMORY, "Resultado confirmado."));
        if (!success.contains("REDBULL") || !success.contains("3.000.000,00") || !success.contains("R$")
                || !success.contains("Flamengo") || !success.contains("Salve pelo jogo") || !success.contains("Resultado confirmado."))
            throw new AssertionError("Confirmed notice must identify quoted sponsor/amount and require saving: " + success);
        for (var state : SponsorAccess.State.values()) {
            if (state == SponsorAccess.State.APPLIED_IN_MEMORY) continue;
            String message = SponsorActivationNotice.message(quote, new SponsorAccess.Result(state, "Sem confirmacao de credito."));
            if (!message.equals("Sem confirmacao de credito.")) throw new AssertionError("False success for " + state);
        }
        System.out.println("PASS: 5 sponsor credit notice checks; presentation only, no payments.");
    }
}
