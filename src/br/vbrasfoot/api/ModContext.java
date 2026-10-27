package br.vbrasfoot.api;

import java.util.logging.Logger;

/**
 * Session-owned services. The loader rejects service operations after initialization
 * failure or completion of shutdown. Global data remains usable during normal shutdown;
 * new UI actions and observation subscriptions do not. This is not a sandbox.
 */
public interface ModContext {
    GameAccess game();
    /** Experimental loan commands, loader 0.16.0+, loan.request and game.loan.request; requires hooks. */
    default LoanAccess loans() { throw new UnsupportedOperationException("Emprestimos publicos exigem loader 0.16.0 e hooks"); }
    /** Experimental LOAN-only windows, loader 0.16.0+, transfer.windows; configure also needs transfer.window.configure. */
    default TransferWindows transferWindows() { throw new UnsupportedOperationException("Janelas exigem loader 0.16.0 e hooks"); }
    Logger logger();
    /** Experimental opt-in observations, requires events.career.read and loader 0.4.0+. */
    default CareerObservations careerObservations() {
        throw new UnsupportedOperationException("Observacoes exigem loader 0.4.0+ e --probe-career-hooks");
    }
    /** Qualified existing-load/save sequence events; loader0.8.0+, events.career.read and required events.career.io. Opt-in, not durable. */
    default CareerIoEvents careerIoEvents() {
        throw new UnsupportedOperationException("Eventos de I/O exigem loader 0.8.0+ e --probe-career-hooks");
    }
    /** Actions run on Swing's event dispatch thread; loader removes them on failure/shutdown. */
    void registerAction(String id, String label, Runnable action);
    /**
     * Registration owned by this mod. Close on any thread to revoke dispatch and
     * schedule visual removal on the EDT. In-flight callbacks may still complete.
     */
    default AutoCloseable registerScopedAction(String id, String label, Runnable action) {
        throw new UnsupportedOperationException("Loader anterior a 0.2.0: use minLoaderVersion=0.2.0");
    }
    /** Experimental embedded overlay in the career window; no original-screen injection. Requires ui.register and ui.panel.embedded. */
    default AutoCloseable registerPanel(String id, String label, java.util.function.Function<UiTheme, ModPanel> factory) {
        throw new UnsupportedOperationException("Painel integrado exige loader 0.6.0");
    }
    /** Read-only image strip in the original club toolbar; requires ui.register and ui.banner.club. Provider is called on the EDT. */
    default AutoCloseable registerClubBanner(String id, ClubBannerProvider provider) {
        throw new UnsupportedOperationException("Banners na tela do clube exigem loader 0.10.0");
    }
    /** Per-save mod-owned sidecar data; requires data.read, data.write and career.data.scoped. Not a game-save API. */
    default CareerDataAccess careerData() {
        throw new UnsupportedOperationException("Dados de mod vinculados a carreira exigem loader 0.11.0 e selecao explicita");
    }
    /** Narrow experimental gameplay service; requires sponsor.activate and game.sponsor.activate, only for the sponsor mod. */
    default SponsorAccess sponsors() {
        throw new UnsupportedOperationException("Ativacao real de patrocinio exige loader 0.12.0 e --probe-career-hooks");
    }
    byte[] readData(String file) throws java.io.IOException;
    void writeData(String file, byte[] bytes) throws java.io.IOException;
}
