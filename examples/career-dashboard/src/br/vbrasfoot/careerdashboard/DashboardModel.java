package br.vbrasfoot.careerdashboard;

import br.vbrasfoot.api.GameAccess;
import br.vbrasfoot.api.GameQueryException;
import java.util.Objects;

/** Holds only the current public DTO page and a scalar copy of the last manual read. */
public final class DashboardModel {
    public static final int PAGE_SIZE = 100;
    public record Reading(String clubName, int year, int month, int day, int dayIndex,
                          String sourceTimeZoneId, String displayTimeZoneId,
                          String displayedDate, long balance) { }
    private final GameAccess game;
    private GameAccess.ScopedClubPage page;
    private Reading reading;

    public DashboardModel(GameAccess game) { this.game = Objects.requireNonNull(game); }
    public GameAccess.ScopedClubPage page() { return page; }
    public Reading reading() { return reading; }
    public void clearReading() { reading = null; }
    public void clear() { page = null; reading = null; }

    public GameAccess.ScopedClubPage clubs(String filter, int offset) {
        try {
            GameAccess.validatePage(offset, PAGE_SIZE);
            var next = game.scopedClubs(filter, offset, PAGE_SIZE);
            if (page != null) requireScope(page.scope(), next.scope());
            for (var club : next.items()) requireScope(next.scope(), club.reference().scope());
            reading = null;
            page = next.loaded() ? next : null;
            return next;
        } catch (RuntimeException failure) { clear(); throw failure; }
    }

    public Reading read(int selectedIndex) {
        try {
            if (page == null || !page.loaded())
                throw new GameQueryException(GameQueryException.Reason.NO_CAREER, "Atualize a lista de clubes");
            var selected = page.items().get(selectedIndex);
            var calendar = game.scopedCalendar();
            requireScope(page.scope(), calendar.scope());
            var balance = game.scopedClubBalance(selected.reference());
            requireScope(page.scope(), balance.scope());
            requireScope(calendar.scope(), balance.scope());
            if (!selected.reference().equals(balance.club()))
                throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Clube da resposta mudou");
            var date = calendar.date();
            reading = new Reading(selected.club().name(), date.year(), date.month(), date.day(),
                    calendar.dayIndex(), date.sourceTimeZoneId(), date.displayTimeZoneId(),
                    date.displayedDate(), balance.balance());
            return reading;
        } catch (RuntimeException failure) { clear(); throw failure; }
    }

    private static void requireScope(GameAccess.CareerScope expected, GameAccess.CareerScope actual) {
        if (!expected.equals(actual))
            throw new GameQueryException(GameQueryException.Reason.STATE_CHANGED, "Escopo de consulta mudou; atualize");
    }
}
