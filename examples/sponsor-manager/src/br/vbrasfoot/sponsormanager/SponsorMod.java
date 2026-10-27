package br.vbrasfoot.sponsormanager;

import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;
import br.vbrasfoot.api.ModPanel;
import br.vbrasfoot.api.UiTheme;

public final class SponsorMod implements Mod {
    private AutoCloseable panelRegistration, bannerRegistration;
    @Override public void initialize(ModContext context) {
        SponsorBanner banner = new SponsorBanner(context.careerData());
        bannerRegistration = context.registerClubBanner("sponsor-logo", banner::banner);
        panelRegistration = context.registerPanel("sponsors", "Patrocinios (protótipo)", theme -> new SponsorPanel(context, theme, banner));
    }
    @Override public void shutdown() throws Exception {
        if (panelRegistration != null) { panelRegistration.close(); panelRegistration = null; }
        if (bannerRegistration != null) { bannerRegistration.close(); bannerRegistration = null; }
    }
}
