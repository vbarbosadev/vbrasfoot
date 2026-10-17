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
    private record Entry(int index, WeakReference<Object> club) { }
    QueryScopes(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    CareerScope observe(Object primary, Object aux, Object clubList, Object humanList) {
        if (generation == 0 || game.get() != primary || secondary.get() != aux || clubs.get() != clubList || humans.get() != humanList) {
            generation++;
            game = new WeakReference<>(primary); secondary = new WeakReference<>(aux);
            clubs = new WeakReference<>(clubList); humans = new WeakReference<>(humanList);
            references.clear();
        }
        return new CareerScope(session, generation);
    }
    ClubRef issue(CareerScope scope, int index, Object club) {
        requireScope(scope);
        String token = UUID.randomUUID().toString();
        references.put(token, new Entry(index, new WeakReference<>(club)));
        while (references.size() > capacity) references.remove(references.keySet().iterator().next());
        return new ClubRef(scope, token);
    }
    void requireScope(CareerScope scope) {
        if (!new CareerScope(session, generation).equals(scope)) throw new GameQueryException(Reason.STALE_SCOPE, "Carreira/escopo mudou; atualize a pagina de clubes");
    }
    int resolve(ClubRef reference, List<?> current) {
        Objects.requireNonNull(reference);
        requireScope(reference.scope());
        Entry entry = references.get(reference.token());
        if (entry == null || entry.club().get() == null) throw new GameQueryException(Reason.EXPIRED_REFERENCE, "Referencia expirada; atualize a pagina de clubes");
        if (entry.index() >= current.size() || current.get(entry.index()) != entry.club().get()) {
            references.remove(reference.token());
            throw new GameQueryException(Reason.CLUB_MOVED, "Clube movido/substituido na lista; atualize a pagina");
        }
        return entry.index();
    }
    int retained() { return references.size(); }
}
