package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.SponsorAccess;
import java.text.NumberFormat;
import java.util.Locale;

/** Presentation only: a confirmed result never starts another financial operation. */
final class SponsorActivationNotice {
    private SponsorActivationNotice() { }

    static String message(SponsorAccess.Quote quote, SponsorAccess.Result result) {
        if (result.state() != SponsorAccess.State.APPLIED_IN_MEMORY) return result.message();
        String amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(quote.credit());
        return "Patrocínio ativado!\n\n" + quote.sponsorName() + " creditou " + amount
                + " na conta do clube " + quote.club() + ".\n\n"
                + "Salve pelo jogo para gravar o crédito.\n\n" + result.message();
    }
}
