package br.vbrasfoot.careerdashboard;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import br.vbrasfoot.api.ModContext;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

public final class DashboardModelTest {
    private static final GameAccess.CareerScope S = new GameAccess.CareerScope("query", 1);
    private static final GameAccess.CareerScope T = new GameAccess.CareerScope("query", 2);
    private static int tests;
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void test(String name, Throwing action) throws Exception {
        action.run(); tests++; System.out.println("PASS " + name);
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
    private static GameAccess.ClubRef ref(GameAccess.CareerScope scope, String token) {
        return new GameAccess.ClubRef(scope, token);
    }
    private static void clean(DashboardModel model) { check(model.page() == null && model.reading() == null); }
    private static void failure(Class<? extends RuntimeException> type, GameQueryException.Reason reason,
                                DashboardModel model, Runnable action) {
        try { action.run(); throw new AssertionError("Expected failure"); }
        catch (RuntimeException error) {
            check(type.isInstance(error));
            if (reason != null) check(((GameQueryException) error).reason() == reason);
        }
        clean(model);
    }

    public static void main(String[] args) throws Exception {
        test("same scope scalar date and balance; explicit manual reads only", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f);
            m.clubs("", 0); check(f.calendarCalls == 0 && f.balanceCalls == 0);
            var r = m.read(0);
            check(r.clubName().equals("Clube 0") && r.year() == 2024 && r.month() == 2 && r.day() == 29);
            check(r.displayedDate().equals("29/02/2024 texto SDK") && r.dayIndex() == 7);
            check(r.sourceTimeZoneId().equals("America/Sao_Paulo") && r.displayTimeZoneId().equals("UTC"));
            check(r.balance() == -51 && f.calendarCalls == 1 && f.balanceCalls == 1);
            f.balance = 9; check(m.read(0).balance() == 9); check(f.balanceCalls == 2);
            m.clear(); clean(m);
        });
        for (long value : new long[] {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1})
            test("raw long " + value, () -> {
                Fake f = new Fake(); f.balance = value;
                DashboardModel m = new DashboardModel(f); m.clubs("", 0);
                check(m.read(0).balance() == value);
            });
        for (var date : List.of(new GameAccess.CalendarDate(1, 1, 1, "UTC", "UTC", "custom"),
                new GameAccess.CalendarDate(999999999, 12, 31, "UTC", "Pacific/Kiritimati", "displayed independently")))
            test("date scalar boundary " + date.year(), () -> {
                Fake f = new Fake(); f.date = date;
                DashboardModel m = new DashboardModel(f); m.clubs("", 0); var r = m.read(0);
                check(r.year() == date.year() && r.month() == date.month() && r.day() == date.day());
                check(r.displayedDate().equals(date.displayedDate()));
            });
        test("filter offset and bounded page 100", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f);
            for (int offset : new int[] {0, 100, 200}) {
                var p = m.clubs("filtro", offset);
                check(f.filter.equals("filtro") && f.offset == offset && f.limit == 100);
                check(p.items().size() == Math.min(100, 205 - offset) && p.total() == 205);
            }
        });
        test("negative offset never reaches provider", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f);
            failure(IllegalArgumentException.class, null, m, () -> m.clubs("", -1)); check(f.clubCalls == 0);
        });
        test("empty page no read", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f);
            check(m.clubs("", 300).items().isEmpty());
            failure(IndexOutOfBoundsException.class, null, m, () -> m.read(0)); check(f.calendarCalls == 0);
        });
        test("no career clears previous DTOs", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f); m.clubs("", 0); m.read(0);
            f.loaded = false; check(!m.clubs("", 0).loaded()); clean(m);
            failure(GameQueryException.class, GameQueryException.Reason.NO_CAREER, m, () -> m.read(0));
        });
        test("pagination and explicit refresh discard reading", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f); m.clubs("", 0); m.read(0);
            m.clubs("", 100); check(m.reading() == null); m.read(0); m.clear();
            f.scope = T; f.calendarScope = T; f.balanceScope = T;
            m.clubs("", 0); check(m.read(0).balance() == -51);
        });
        test("selection clearing without queries", () -> {
            Fake f = new Fake(); DashboardModel m = new DashboardModel(f); m.clubs("", 0); m.read(0);
            m.clearReading(); check(m.reading() == null && m.page() != null && f.calendarCalls == 1);
        });
        for (String source : List.of("page", "club", "calendar", "balance", "wrongClub"))
            test("scope or reference mismatch " + source, () -> {
                Fake f = new Fake(); DashboardModel m = new DashboardModel(f); m.clubs("", 0); m.read(0);
                switch (source) {
                    case "page" -> f.scope = T;
                    case "club" -> f.badPageClub = true;
                    case "calendar" -> f.calendarScope = T;
                    case "balance" -> f.balanceScope = T;
                    case "wrongClub" -> f.wrongClub = true;
                }
                failure(GameQueryException.class, GameQueryException.Reason.STATE_CHANGED, m,
                        () -> { if (source.equals("page") || source.equals("club")) m.clubs("", 100); else m.read(0); });
                if (source.equals("calendar")) check(f.balanceCalls == 1); // not attempted after mismatch
            });
        for (var reason : GameQueryException.Reason.values()) for (String operation : List.of("clubs", "calendar", "balance"))
            test("cleanup " + operation + " " + reason, () -> {
                Fake f = new Fake(); DashboardModel m = new DashboardModel(f); m.clubs("", 0); m.read(0);
                f.failure = new GameQueryException(reason, "fake"); f.failAt = operation;
                failure(GameQueryException.class, reason, m,
                        () -> { if (operation.equals("clubs")) m.clubs("", 100); else m.read(0); });
            });
        for (String operation : List.of("calendar", "balance"))
            test("old API default unsupported " + operation, () -> {
                Fake f = new Fake();
                GameAccess old = new GameAccess() {
                    @Override public Snapshot snapshot() { throw new AssertionError("No snapshot fallback"); }
                    @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) { return f.scopedClubs(filter, offset, limit); }
                    @Override public ScopedCalendar scopedCalendar() {
                        return operation.equals("calendar") ? GameAccess.super.scopedCalendar() : f.scopedCalendar();
                    }
                    // scopedClubBalance intentionally retains the unsupported default.
                };
                DashboardModel m = new DashboardModel(old); m.clubs("", 0);
                failure(UnsupportedOperationException.class, null, m, () -> m.read(0));
            });
        test("old scopedClubs default unsupported, no snapshot fallback", () -> {
            DashboardModel m = new DashboardModel(() -> { throw new AssertionError("No snapshot fallback"); });
            failure(UnsupportedOperationException.class, null, m, () -> m.clubs("", 0));
        });
        test("scoped registration, shutdown twice, late callback inert", () -> {
            Context c = new Context(); CareerDashboardMod mod = new CareerDashboardMod(); mod.initialize(c);
            check(c.callback != null && c.gameCalls == 0);
            mod.shutdown(); mod.shutdown(); c.callback.run();
            SwingUtilities.invokeAndWait(c.callback); check(c.closed == 1 && c.gameCalls == 0);
        });
        test("close failure still stops late callback and releases registration", () -> {
            Context c = new Context(); c.failClose = true;
            CareerDashboardMod mod = new CareerDashboardMod(); mod.initialize(c);
            try { mod.shutdown(); throw new AssertionError(); } catch (IllegalStateException expected) { }
            mod.shutdown(); SwingUtilities.invokeAndWait(c.callback); check(c.closed == 1 && c.gameCalls == 0);
        });
        test("shutdown schedules disposal without waiting for blocked EDT or queued callback", () -> {
            Context c = new Context(); CareerDashboardMod mod = new CareerDashboardMod(); mod.initialize(c);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            check(entered.await(5, TimeUnit.SECONDS)); SwingUtilities.invokeLater(c.callback);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread shutdown = new Thread(() -> { try { mod.shutdown(); } catch (Throwable e) { error.set(e); } });
            try { shutdown.start(); shutdown.join(2000); check(!shutdown.isAlive() && error.get() == null); }
            finally { release.countDown(); }
            SwingUtilities.invokeAndWait(() -> {}); check(c.closed == 1 && c.gameCalls == 0);
        });
        System.out.println("Tests passed: " + tests);
    }

    private static final class Fake implements GameAccess {
        CareerScope scope = S, calendarScope = S, balanceScope = S;
        CalendarDate date = new CalendarDate(2024, 2, 29, "America/Sao_Paulo", "UTC", "29/02/2024 texto SDK");
        boolean loaded = true, badPageClub, wrongClub;
        long balance = -51;
        int offset, limit, clubCalls, calendarCalls, balanceCalls;
        String filter, failAt;
        RuntimeException failure;
        void maybeFail(String operation) { if (operation.equals(failAt)) throw failure; }
        @Override public Snapshot snapshot() { throw new AssertionError("Global snapshot forbidden"); }
        @Override public ClubPage clubs(String filter, int offset, int limit) { throw new AssertionError("Unscoped clubs forbidden"); }
        @Override public PlayerPage players(int id, int offset, int limit) { throw new AssertionError("Players forbidden"); }
        @Override public PlayerPage playersAt(int index, int offset, int limit) { throw new AssertionError("Players forbidden"); }
        @Override public ScopedClubPage scopedClubs(String filter, int offset, int limit) {
            clubCalls++; maybeFail("clubs"); check(limit == 100 && offset >= 0);
            this.filter = filter; this.offset = offset; this.limit = limit;
            var items = loaded ? java.util.stream.IntStream.range(offset, Math.min(205, offset + limit))
                    .mapToObj(i -> new ClubView(ref(badPageClub ? T : scope, "opaque-" + i),
                            new ClubSummary(i, 77, "Clube " + i, i == 0, 20))).toList() : List.<ClubView>of();
            return new ScopedClubPage(loaded, scope, items, offset, loaded ? 205 : 0);
        }
        @Override public ScopedCalendar scopedCalendar() {
            calendarCalls++; maybeFail("calendar"); return new ScopedCalendar(calendarScope, 7, date);
        }
        @Override public ScopedClubBalance scopedClubBalance(ClubRef club) {
            balanceCalls++; maybeFail("balance");
            return new ScopedClubBalance(balanceScope, ref(balanceScope, wrongClub ? "other" : club.token()), balance);
        }
    }
    private static final class Context implements ModContext {
        Runnable callback; int closed, gameCalls; boolean failClose;
        @Override public GameAccess game() { gameCalls++; throw new AssertionError("No game access during initialize/stopped callback"); }
        @Override public Logger logger() { throw new AssertionError("No logging needed"); }
        @Override public void registerAction(String id, String label, Runnable action) { throw new AssertionError("Only scoped action"); }
        @Override public AutoCloseable registerScopedAction(String id, String label, Runnable action) {
            check(id.equals("dashboard")); callback = action;
            return () -> { closed++; if (failClose) throw new IllegalStateException("fake close error"); };
        }
        @Override public byte[] readData(String file) { throw new AssertionError("No data reads"); }
        @Override public void writeData(String file, byte[] data) { throw new AssertionError("No save/data writes"); }
    }
}
