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

    private List<?> activeClubs() throws Exception {
        Object game = Class.forName("c.a", false, runtime).getField("SR").get(null);
        if (game == null || Class.forName("c.a", false, runtime).getField("afQ").get(null) == null) return List.of();
        List<?> humans = (List<?>) call(game, "aN");
        return humans == null || humans.isEmpty() ? List.of() : (List<?>) call(game, "P");
    }

    @Override public ClubPage clubs(String filter, int offset, int limit) {
        GameAccess.validatePage(offset, limit);
        String query = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT);
        return onEdt(() -> {
            List<?> clubs = activeClubs();
            List<ClubSummary> result = new ArrayList<>();
            int total = 0;
            Object game = Class.forName("c.a", false, runtime).getField("SR").get(null);
            List<?> humans = clubs.isEmpty() ? List.of() : (List<?>) call(game, "aN");
            int index = -1;
            for (Object club : clubs) {
                index++;
                String name = (String) call(club, "getNome");
                if (!name.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                if (total++ >= offset && result.size() < limit) {
                    result.add(new ClubSummary(index, number(club, "gD"), name, humans.stream().anyMatch(h -> h == club), ((List<?>) call(club, "kc")).size()));
                }
            }
            return new ClubPage(!clubs.isEmpty(), result, offset, total);
        });
    }

    @Override public PlayerPage players(int clubId, int offset, int limit) {
        GameAccess.validatePage(offset, limit);
        return onEdt(() -> {
            List<?> clubs = activeClubs();
            int found = -1;
            for (int i = 0; i < clubs.size(); i++) {
                if (number(clubs.get(i), "gD") != clubId) continue;
                if (found >= 0) throw new IllegalArgumentException("ID de clube ambiguo; use playersAt com index da pagina atual");
                found = i;
            }
            return found < 0 ? new PlayerPage(!clubs.isEmpty(), List.of(), offset, 0) : playersAt(found, offset, limit);
        });
    }

    @Override public PlayerPage playersAt(int clubIndex, int offset, int limit) {
        GameAccess.validatePage(offset, limit);
        return onEdt(() -> {
            List<?> clubs = activeClubs();
            if (clubIndex < 0 || clubIndex >= clubs.size()) throw new IllegalArgumentException("Indice de clube fora da lista atual");
            Object club = clubs.get(clubIndex);
                List<?> players = (List<?>) call(club, "kc");
                List<Player> result = new ArrayList<>();
                for (int i = offset; i < players.size() && result.size() < limit; i++) {
                    Object p = players.get(i);
                    result.add(new Player(number(p, "gD"), (String) call(p, "getNome"), number(p, "getIdade"),
                            number(p, "getPosicao"), number(p, "fi"), number(p, "fk")));
                }
                return new PlayerPage(true, result, offset, players.size());
        });
    }

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
