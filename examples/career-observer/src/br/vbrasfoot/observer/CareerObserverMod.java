package br.vbrasfoot.observer;

import br.vbrasfoot.api.CareerObservations;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;

/** Public SDK consumer only; no gameplay, file paths, persistence or EDT calls. */
public final class CareerObserverMod implements Mod {
    private CareerObservations.Subscription subscription;
    @Override public void initialize(ModContext context) {
        subscription = context.careerObservations().subscribe(update -> context.logger().info(
                "career-observer kind=" + update.kind() + " revision=" + update.state().revision()
                + " generation=" + update.state().generation() + " phase=" + update.state().phase()
                + " reads=" + update.state().reads() + " primaryWrites=" + update.state().primaryWrites()));
    }
    @Override public void shutdown() { if (subscription != null) subscription.close(); }
}
