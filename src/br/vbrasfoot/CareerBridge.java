package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

final class CareerBridge implements GameAccess {
    private final ClassLoader runtime;
    CareerBridge(ClassLoader runtime) { this.runtime = runtime; }

    @Override public Snapshot snapshot() {
        return onEdt(() -> {
            Class<?> state = Class.forName("c.a", false, runtime);
            Object game = state.getField("SR").get(null);
            Object secondary = state.getField("afQ").get(null);
            if (game == null || secondary == null) return new Snapshot(false, List.of());
            List<?> clubs = (List<?>) call(game, "P");
            List<?> humans = (List<?>) call(game, "aN");
            // SR is also allocated for an empty new-game screen. Non-null alone is not a loaded career.
            if (clubs == null || clubs.isEmpty() || humans == null || humans.isEmpty()) {
                return new Snapshot(false, List.of());
            }
            List<Club> result = new ArrayList<>();
            for (Object club : clubs) {
                List<Player> players = new ArrayList<>();
                for (Object player : (List<?>) call(club, "kc")) {
                    players.add(new Player(number(player, "gD"), (String) call(player, "getNome"),
                            number(player, "getIdade"), number(player, "getPosicao"),
                            number(player, "fi"), number(player, "fk")));
                }
                boolean human = humans.stream().anyMatch(item -> item == club);
                result.add(new Club(number(club, "gD"), (String) call(club, "getNome"), human, players));
            }
            return new Snapshot(true, result);
        });
    }

    static Object call(Object target, String name) throws Exception {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (InvocationTargetException failure) {
            throw new IllegalStateException("Falha no acesso legado " + name, failure.getCause());
        }
    }
    private static int number(Object target, String name) throws Exception { return ((Number) call(target, name)).intValue(); }

    static <T> T onEdt(Callable<T> action) {
        try {
            if (SwingUtilities.isEventDispatchThread()) return action.call();
            FutureTask<T> task = new FutureTask<>(action);
            SwingUtilities.invokeAndWait(task);
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta interrompida", interrupted);
        } catch (Exception failure) { throw new IllegalStateException("Nao foi possivel consultar a carreira", failure); }
    }
}
