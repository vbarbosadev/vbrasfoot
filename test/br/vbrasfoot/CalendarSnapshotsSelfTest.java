package br.vbrasfoot;

import br.vbrasfoot.api.*;
import java.util.*;

public final class CalendarSnapshotsSelfTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static final class CountingCalendar extends GregorianCalendar {
        int reads;
        CountingCalendar(TimeZone zone) { super(zone); }
        @Override public int get(int field) { reads++; return super.get(field); }
        @Override public long getTimeInMillis() { reads++; return super.getTimeInMillis(); }
    }
    public static void main(String[] args) {
        TimeZone previous = TimeZone.getDefault(); Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC")); Locale.setDefault(Locale.Category.FORMAT, Locale.ROOT);
            CountingCalendar original = new CountingCalendar(TimeZone.getTimeZone("GMT+14:00"));
            original.clear(); original.set(2024, Calendar.FEBRUARY, 29, 0, 0); original.reads = 0;
            var date = CalendarSnapshots.copy(original);
            check(original.reads == 0, "live Calendar getters not called");
            check(date.year() == 2024 && date.month() == 2 && date.day() == 29, "copied leap day and public month");
            check(date.sourceTimeZoneId().equals("GMT+14:00") && date.displayTimeZoneId().equals("UTC") && date.displayedDate().equals("28/02/2024"), "source vs UI timezone explicit");
            original.set(2030, Calendar.DECEMBER, 31); check(date.year() == 2024, "DTO detached from later mutation");
            original.clear(); original.set(2023, 13, 1); original.reads = 0;
            var normalized = CalendarSnapshots.copy(original);
            check(normalized.year() == 2024 && normalized.month() == 2 && original.reads == 0, "lenient normalization only on clone");
            GregorianCalendar bc = new GregorianCalendar(); bc.clear(); bc.set(Calendar.ERA, GregorianCalendar.BC); bc.set(Calendar.YEAR, 1);
            boolean rejected = false; try { CalendarSnapshots.copy(bc); } catch (GameQueryException failure) { rejected = failure.reason() == GameQueryException.Reason.UNSUPPORTED_DATA; }
            check(rejected, "BC not converted silently");
            rejected = false; try { CalendarSnapshots.copy(null); } catch (GameQueryException failure) { rejected = failure.reason() == GameQueryException.Reason.UNSUPPORTED_DATA; }
            check(rejected, "missing calendar typed");
            GregorianCalendar invalid = new GregorianCalendar(); invalid.clear(); invalid.setLenient(false); invalid.set(2023, Calendar.FEBRUARY, 30);
            rejected = false; try { CalendarSnapshots.copy(invalid); } catch (GameQueryException failure) { rejected = failure.reason() == GameQueryException.Reason.UNSUPPORTED_DATA; }
            check(rejected, "invalid nonlenient date typed");
            var scope = new GameAccess.CareerScope("owned", 1); var club = new GameAccess.ClubRef(scope, "opaque");
            for (long balance : new long[] {Long.MIN_VALUE, -1, 0, Long.MAX_VALUE}) check(new GameAccess.ScopedClubBalance(scope, club, balance).balance() == balance, "balance preserves full long");
            rejected = false; try { new GameAccess.ScopedClubBalance(new GameAccess.CareerScope("other", 1), club, 0); } catch (IllegalArgumentException failure) { rejected = true; }
            check(rejected, "balance DTO scope invariant");
            GameAccess legacy = () -> new GameAccess.Snapshot(false, List.of());
            rejected = false; try { legacy.scopedCalendar(); } catch (UnsupportedOperationException failure) { rejected = true; } check(rejected, "old implementations refuse calendar");
            rejected = false; try { legacy.scopedClubBalance(club); } catch (UnsupportedOperationException failure) { rejected = true; } check(rejected, "old implementations refuse balance");
        } finally { TimeZone.setDefault(previous); Locale.setDefault(Locale.Category.FORMAT, locale); }
        System.out.println("PASS: " + checks + " calendar/balance snapshot checks; synthetic, no game.");
    }
}
