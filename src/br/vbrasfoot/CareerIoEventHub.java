package br.vbrasfoot;

import br.vbrasfoot.api.CareerIoEvents;
import br.vbrasfoot.api.CareerObservations;

/** Typed stream adapter reusing the tested bounded dispatcher, with an independent immutable state/revision. */
final class CareerIoEventHub implements CareerIoEvents, AutoCloseable {
    private final CareerObservationHub carrier;
    CareerIoEventHub() { this(new CareerSubscriberBudget(32)); }
    CareerIoEventHub(CareerSubscriberBudget budget) { carrier = new CareerObservationHub(budget); }
    private static CareerObservations.State encode(State state) {
        return new CareerObservations.State(state.session(), state.generation(), state.revision(),
                CareerObservations.Phase.valueOf(state.phase().name()), state.existingLoads(), state.completedSaves(), state.healthy());
    }
    private static State decode(CareerObservations.State state) {
        return new State(state.session(), state.generation(), state.revision(), Phase.valueOf(state.phase().name()), state.reads(), state.primaryWrites(), state.healthy());
    }
    void initialize(State state) { carrier.initialize(encode(state)); }
    void publish(Update update) {
        var kind = switch (update.kind()) {
            case CURRENT_STATE -> CareerObservations.Kind.CURRENT_STATE;
            case INVALIDATED -> CareerObservations.Kind.INVALIDATED;
            case EXISTING_LOAD_COMPLETED -> CareerObservations.Kind.READ_COMPLETED;
            case SAVE_SEQUENCE_COMPLETED -> CareerObservations.Kind.PRIMARY_WRITE_COMPLETED;
        };
        carrier.publish(new CareerObservations.Update(kind, encode(update.state()), update.backupInput()));
    }
    void finishState(State state) { carrier.finishState(encode(state)); }
    public State state() { return decode(carrier.state()); }
    public CareerObservations.Subscription subscribe(java.util.function.Consumer<Update> callback) {
        java.util.Objects.requireNonNull(callback);
        return carrier.subscribe(update -> {
            var kind = switch (update.kind()) {
                case CURRENT_STATE -> Kind.CURRENT_STATE;
                case INVALIDATED -> Kind.INVALIDATED;
                case READ_COMPLETED -> Kind.EXISTING_LOAD_COMPLETED;
                case PRIMARY_WRITE_COMPLETED -> Kind.SAVE_SEQUENCE_COMPLETED;
            };
            callback.accept(new Update(kind, decode(update.state()), update.backupInput()));
        });
    }
    public void close() { carrier.close(); }
}
