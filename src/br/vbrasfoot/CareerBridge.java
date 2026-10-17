package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

final class CareerBridge implements GameAccess {
    // ClassValue associates public getter caches with the defining class, without a global strong classloader map.
    private static final ClassValue<java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method>> getters = new ClassValue<>() {
        @Override protected java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Method> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };
    private final ClassLoader runtime;
    private final QueryScopes scopes = new QueryScopes(4096);
    CareerBridge(ClassLoader runtime) { this.runtime = runtime; }

    private record ReadState(Object primary, Object secondary, List<?> clubs, List<?> humans, CareerScope scope) {
        boolean loaded() { return primary != null && secondary != null && !clubs.isEmpty() && !humans.isEmpty(); }
    }
    private ReadState readState() throws Exception {
        Class<?> type = Class.forName("c.a", false, runtime);
        Object primary = type.getField("SR").get(null), aux = type.getField("afQ").get(null);
        List<?> clubs = primary == null ? null : (List<?>) call(primary, "P");
        List<?> humans = primary == null ? null : (List<?>) call(primary, "aN");
        CareerScope scope = scopes.observe(primary, aux, clubs, humans);
        return new ReadState(primary, aux, clubs == null ? List.of() : clubs, humans == null ? List.of() : humans, scope);
    }
    private void unchanged(ReadState before) throws Exception {
        if (!readState().scope().equals(before.scope())) throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Raizes/listas mudaram durante leitura; consulte novamente");
    }
    static <T> T scopedRead(Callable<T> action) {
        return onEdt(() -> {
            try { return action.call(); }
            catch (IndexOutOfBoundsException | java.util.ConcurrentModificationException changed) {
                throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Lista mudou durante leitura; consulte novamente");
            }
        });
    }
    @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
        GameAccess.validatePage(offset, limit);
        String query = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT);
        return scopedRead(() -> {
            ReadState state = readState();
            if (!state.loaded()) return new ScopedClubPage(false, state.scope(), List.of(), offset, 0);
            int count = state.clubs().size(), total = 0;
            List<ClubView> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Object club = state.clubs().get(i);
                String name = (String) call(club, "getNome");
                if (!name.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                if (total++ >= offset && result.size() < limit) {
                    ClubSummary summary = new ClubSummary(i, number(club, "gD"), name,
                            state.humans().stream().anyMatch(h -> h == club), ((List<?>) call(club, "kc")).size());
                    result.add(new ClubView(scopes.issue(state.scope(), i, club), summary));
                }
            }
            unchanged(state);
            if (state.clubs().size() != count) throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Lista de clubes mudou durante consulta");
            return new ScopedClubPage(true, state.scope(), result, offset, total);
        });
    }
    @Override public ScopedPlayerPage scopedPlayers(ClubRef reference, int offset, int limit) {
        GameAccess.validatePage(offset, limit);
        java.util.Objects.requireNonNull(reference, "club reference");
        return scopedRead(() -> {
            ReadState state = readState();
            scopes.requireScope(reference.scope());
            if (!state.loaded()) throw new GameQueryException(GameQueryException.Reason.NO_CAREER, "Nenhuma carreira ativa identificada pelo bridge");
            int index = scopes.resolve(reference, state.clubs());
            Object club = state.clubs().get(index);
            List<?> players = (List<?>) call(club, "kc");
            int count = players.size();
            List<Player> result = new ArrayList<>();
            for (int i = offset; i < count && result.size() < limit; i++) {
                Object p = players.get(i);
                result.add(new Player(number(p, "gD"), (String) call(p, "getNome"), number(p, "getIdade"),
                        number(p, "getPosicao"), number(p, "fi"), number(p, "fk")));
            }
            unchanged(state);
            scopes.resolve(reference, state.clubs());
            if (call(club, "kc") != players || players.size() != count) throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Lista de jogadores mudou durante leitura");
            return new ScopedPlayerPage(state.scope(), result, offset, count);
        });
    }

    private List<?> activeClubs() throws Exception {
        Object game = Class.forName("c.a", false, runtime).getField("SR").get(null);
        if (game == null || Class.forName("c.a", false, runtime).getField("afQ").get(null) == null) return List.of();
        List<?> humans = (List<?>) call(game, "aN");
        return humans == null || humans.isEmpty() ? List.of() : (List<?>) call(game, "P");
    }
    @Override public ScopedCalendar scopedCalendar() {
        return scopedRead(() -> {
            ReadState state = readState();
            if (!state.loaded()) throw new GameQueryException(GameQueryException.Reason.NO_CAREER, "Nenhuma carreira identificada para calendario");
            int index = number(state.primary(), "J");
            Object original = call(state.primary(), "bb");
            if (!(original instanceof java.util.Calendar calendar)) throw new GameQueryException(GameQueryException.Reason.UNSUPPORTED_DATA, "Calendario original indisponivel");
            CalendarDate date = CalendarSnapshots.copy(calendar);
            unchanged(state);
            if (call(state.primary(), "bb") != original || number(state.primary(), "J") != index) throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Calendario mudou durante consulta");
            return new ScopedCalendar(state.scope(), index, date);
        });
    }
    @Override public ScopedClubBalance scopedClubBalance(ClubRef reference) {
        java.util.Objects.requireNonNull(reference, "club reference");
        return scopedRead(() -> {
            ReadState state = readState(); scopes.requireScope(reference.scope());
            if (!state.loaded()) throw new GameQueryException(GameQueryException.Reason.NO_CAREER, "Nenhuma carreira identificada para saldo");
            int index = scopes.resolve(reference, state.clubs());
            Object club = state.clubs().get(index);
            long balance = ((Number) call(club, "kb")).longValue();
            unchanged(state); scopes.resolve(reference, state.clubs());
            return new ScopedClubBalance(state.scope(), reference, balance);
        });
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
        Class<?> type = target.getClass();
        var cache = getters.get(type);
        java.lang.reflect.Method method = cache.get(name);
        if (method == null) {
            method = type.getMethod(name);
            java.lang.reflect.Method existing = cache.putIfAbsent(name, method);
            if (existing != null) method = existing;
        }
        try { return method.invoke(target); }
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
        } catch (RuntimeException failure) { throw failure; }
        catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Nao foi possivel consultar a carreira", failure.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta interrompida", interrupted);
        } catch (Exception failure) { throw new IllegalStateException("Nao foi possivel consultar a carreira", failure); }
    }
}
