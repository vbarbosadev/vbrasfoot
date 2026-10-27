package br.vbrasfoot.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/** Experimental per-career LOAN-only window policy. Does not change the clock, purchases, free agents or AI transfers. */
public interface TransferWindows {
    record Period(LocalDate start, LocalDate end) {
        public Period { Objects.requireNonNull(start); Objects.requireNonNull(end); if (end.isBefore(start)) throw new IllegalArgumentException("Reversed window"); }
    }
    record Rules(boolean enabled, List<Period> periods) {
        public Rules {
            periods = List.copyOf(periods);
            if (periods.size() > 32) throw new IllegalArgumentException("At most 32 periods");
            var sorted = periods.stream().sorted(Comparator.comparing(Period::start)).toList();
            for (int i = 1; i < sorted.size(); i++) if (!sorted.get(i).start().isAfter(sorted.get(i-1).end()))
                throw new IllegalArgumentException("Overlapping windows");
        }
        public boolean allows(LocalDate date) {
            Objects.requireNonNull(date);
            return !enabled || periods.stream().anyMatch(p -> !date.isBefore(p.start()) && !date.isAfter(p.end()));
        }
    }
    record Snapshot(String revision, String configuredBy, Rules rules) { }
    /** Worker-only. Missing policy is unrestricted. Rules live in loader sidecars, not the game save. */
    Snapshot current(CareerDataAccess.Scope career) throws IOException;
    /** Compare-and-swap; changing another mod's policy requires its latest revision, never silently overwrites stale state. */
    Snapshot configure(CareerDataAccess.Scope career, String expectedRevision, Rules rules) throws IOException;
}
