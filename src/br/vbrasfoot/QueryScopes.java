package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.*;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.GameQueryException.Reason;
import java.lang.ref.WeakReference;
import java.util.*;

/** EDT-confined bounded reference registry. Weak references never retain previous career graphs. */
final class QueryScopes {
    private final String session = UUID.randomUUID().toString();
    private final int capacity;
    private long generation;
    private WeakReference<Object> game = new WeakReference<>(null), secondary = new WeakReference<>(null),
            clubs = new WeakReference<>(null), humans = new WeakReference<>(null);
    private final LinkedHashMap<String, Entry> references = new LinkedHashMap<>(16, .75f, true);
    private record Entry(int index, WeakReference<Object> club, long access) { }
    private final LinkedHashMap<String, PlayerEntry> playerReferences = new LinkedHashMap<>(16, .75f, true);
    private record PlayerEntry(int clubIndex, int playerIndex, WeakReference<Object> club, WeakReference<Object> player, long access) { }
    private long accessSequence;
    QueryScopes(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    CareerScope observe(Object primary, Object aux, Object clubList, Object humanList) {
        if (generation == 0 || game.get() != primary || secondary.get() != aux || clubs.get() != clubList || humans.get() != humanList) {
            generation++;
            game = new WeakReference<>(primary); secondary = new WeakReference<>(aux);
            clubs = new WeakReference<>(clubList); humans = new WeakReference<>(humanList);
            references.clear(); playerReferences.clear();
        }
        return new CareerScope(session, generation);
    }
    ClubRef issue(CareerScope scope, int index, Object club) {
        requireScope(scope);
        String token = UUID.randomUUID().toString();
        references.put(token, new Entry(index, new WeakReference<>(club), ++accessSequence));
        trim();
        return new ClubRef(scope, token);
    }
    PlayerRef issuePlayer(CareerScope scope, int clubIndex, int playerIndex, Object club, Object player) {
        requireScope(scope);
        if (clubIndex < 0 || playerIndex < 0 || club == null || player == null) throw new IllegalArgumentException("Invalid player locator");
        String token = UUID.randomUUID().toString();
        playerReferences.put(token, new PlayerEntry(clubIndex, playerIndex, new WeakReference<>(club), new WeakReference<>(player), ++accessSequence));
        trim();
        return new PlayerRef(scope, token);
    }
    void requireScope(CareerScope scope) {
        if (!new CareerScope(session, generation).equals(scope)) throw new GameQueryException(Reason.STALE_SCOPE, "Carreira/escopo mudou; atualize a pagina de clubes");
    }
    int resolve(ClubRef reference, List<?> current) {
        Objects.requireNonNull(reference);
        requireScope(reference.scope());
        Entry entry = references.get(reference.token());
        if (entry == null || entry.club().get() == null) throw new GameQueryException(Reason.EXPIRED_REFERENCE, "Referencia expirada; atualize a pagina de clubes");
        entry = new Entry(entry.index(), entry.club(), ++accessSequence);
        references.put(reference.token(), entry);
        if (entry.index() >= current.size() || current.get(entry.index()) != entry.club().get()) {
            references.remove(reference.token());
            throw new GameQueryException(Reason.CLUB_MOVED, "Clube movido/substituido na lista; atualize a pagina");
        }
        return entry.index();
    }
    int resolvePlayer(PlayerRef reference, int clubIndex, List<?> currentClubs, List<?> currentPlayers) {
        Objects.requireNonNull(reference);
        requireScope(reference.scope());
        PlayerEntry entry = playerReferences.get(reference.token());
        if (entry == null || entry.club().get() == null || entry.player().get() == null)
            throw new GameQueryException(Reason.EXPIRED_REFERENCE, "Referencia de jogador expirada; atualize a pagina de jogadores");
        entry = new PlayerEntry(entry.clubIndex(), entry.playerIndex(), entry.club(), entry.player(), ++accessSequence);
        playerReferences.put(reference.token(), entry);
        if (entry.clubIndex() != clubIndex || clubIndex >= currentClubs.size() || currentClubs.get(clubIndex) != entry.club().get()
                || entry.playerIndex() >= currentPlayers.size() || currentPlayers.get(entry.playerIndex()) != entry.player().get()) {
            playerReferences.remove(reference.token());
            throw new GameQueryException(Reason.PLAYER_MOVED, "Jogador mudou de clube/posicao ou foi substituido; atualize a pagina");
        }
        return entry.playerIndex();
    }
    private void trim() {
        while (references.size() + playerReferences.size() > capacity) {
            if (references.isEmpty()) playerReferences.remove(playerReferences.keySet().iterator().next());
            else if (playerReferences.isEmpty()) references.remove(references.keySet().iterator().next());
            else {
                var oldestClub = references.entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().access())).orElseThrow();
                var oldestPlayer = playerReferences.entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().access())).orElseThrow();
                if (oldestClub.getValue().access() < oldestPlayer.getValue().access()) references.remove(oldestClub.getKey());
                else playerReferences.remove(oldestPlayer.getKey());
            }
        }
    }
    int retained() { return references.size() + playerReferences.size(); }
}
