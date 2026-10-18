package br.vbrasfoot.observer;

import br.vbrasfoot.api.CareerObservations;
import br.vbrasfoot.api.Mod;
import br.vbrasfoot.api.ModContext;

/** Public SDK consumer only; no gameplay, file paths, persistence or EDT calls. */
public final class CareerObserverMod implements Mod {
    private CareerObservations.Subscription subscription;
    private CareerObservations.Subscription ioSubscription;
    @Override public void initialize(ModContext context) {
        subscription = context.careerObservations().subscribe(update -> context.logger().info(
                "career-observer kind=" + update.kind() + " revision=" + update.state().revision()
                + " generation=" + update.state().generation() + " phase=" + update.state().phase()
                 + " reads=" + update.state().reads() + " primaryWrites=" + update.state().primaryWrites()));
        try {
            ioSubscription = context.careerIoEvents().subscribe(update -> context.logger().info(
                    "career-io kind=" + update.kind() + " revision=" + update.state().revision()
                    + " generation=" + update.state().generation() + " phase=" + update.state().phase()
                    + " existingLoads=" + update.state().existingLoads() + " completedSaves=" + update.state().completedSaves()));
        } catch (RuntimeException | Error failure) { subscription.close(); throw failure; }
    }
    @Override public void shutdown() {
        if (subscription != null) subscription.close();
        if (ioSubscription != null) ioSubscription.close();
    }
}
