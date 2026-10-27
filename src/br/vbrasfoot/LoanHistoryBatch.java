package br.vbrasfoot;

import java.util.*;

/** One bounded traversal, scoped to one EDT validation; no retained graph/index cache. */
final class LoanHistoryBatch {
    interface Access {
        List<?> clubs() throws Exception;
        List<?> roster(Object club, boolean juniors) throws Exception;
        int id(Object player) throws Exception;
        String name(Object player) throws Exception;
        Object club(Object player) throws Exception;
        List<?> trackers() throws Exception;
        Object trackedPlayer(Object tracker) throws Exception;
        Object origin(Object tracker) throws Exception;
        long due(Object tracker) throws Exception;
    }
    record Expected(int id, String name, Object club, Object origin, long due, boolean retired, boolean nativeReturn) {
        Expected(int id, String name, Object club, Object origin, long due, boolean retired) { this(id, name, club, origin, due, retired, false); }
    }
    private static final class Found {
        int occurrences, trackers;
        Object player;
        boolean returned;
        boolean valid = true;
    }
    static List<Expected> invalid(List<Expected> expected, Access access) throws Exception {
        if (expected.size() > 64) throw new IllegalStateException("Historical player quota");
        if (expected.isEmpty()) return List.of();
        var targets = new HashMap<Integer, Expected>(); var found = new HashMap<Integer, Found>();
        for (var item : expected) {
            if (targets.put(item.id(), item) != null) throw new IllegalStateException("Ambiguous historical player id");
            found.put(item.id(), new Found());
        }
        int inspected = 0;
        for (Object club : access.clubs()) for (boolean juniors : List.of(false, true)) {
            var roster = access.roster(club, juniors);
            if (roster.size() > 500) throw new IllegalStateException("Historical roster quota");
            for (Object player : roster) {
                if (++inspected > 1000000) throw new IllegalStateException("Historical graph quota");
                int id = access.id(player); var item = targets.get(id); if (item == null) continue;
                var row = found.get(id); row.occurrences++; row.player = player;
                row.returned = !item.retired() && item.nativeReturn() && club == item.origin();
                if (juniors || (item.nativeReturn() ? !row.returned : club != item.club()) || access.club(player) != club || !item.name().equals(access.name(player))) row.valid = false;
            }
        }
        var byIdentity = new IdentityHashMap<Object, Expected>();
        for (var item : expected) {
            var row = found.get(item.id()); if (row.occurrences == 1) byIdentity.put(row.player, item);
        }
        var trackers = access.trackers(); if (trackers.size() > 10000) throw new IllegalStateException("Historical tracker quota");
        for (Object tracker : trackers) {
            var item = byIdentity.get(access.trackedPlayer(tracker)); if (item == null) continue;
            var row = found.get(item.id()); row.trackers++;
            if (item.retired() || row.returned || access.origin(tracker) != item.origin() || access.due(tracker) != item.due()) row.valid = false;
        }
        return expected.stream().filter(item -> {
            var row = found.get(item.id()); return !row.valid || row.occurrences != 1 || row.trackers != (item.retired() || row.returned ? 0 : 1);
        }).toList();
    }
}
