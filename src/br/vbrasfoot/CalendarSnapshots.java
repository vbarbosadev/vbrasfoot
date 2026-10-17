package br.vbrasfoot;

import br.vbrasfoot.api.GameAccess.CalendarDate;
import br.vbrasfoot.api.GameQueryException;
import java.text.SimpleDateFormat;
import java.util.*;

/** Calendar.get/getTime never run on the live graph; normalization affects the clone only. */
final class CalendarSnapshots {
    static CalendarDate copy(Calendar original) {
        if (original == null) throw unsupported();
        try {
            Calendar copy = (Calendar) original.clone();
            if (!(copy instanceof GregorianCalendar) || !copy.getCalendarType().equals("gregory") || copy.get(Calendar.ERA) != GregorianCalendar.AD) throw unsupported();
            int year = copy.get(Calendar.YEAR), month = copy.get(Calendar.MONTH) + 1, day = copy.get(Calendar.DAY_OF_MONTH);
            TimeZone source = copy.getTimeZone(), display = TimeZone.getDefault();
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy"); format.setTimeZone(display);
            return new CalendarDate(year, month, day, source.getID(), display.getID(), format.format(copy.getTime()));
        }
        catch (java.time.DateTimeException | IllegalArgumentException invalid) { throw unsupported(); }
    }
    private static GameQueryException unsupported() { return new GameQueryException(GameQueryException.Reason.UNSUPPORTED_DATA, "Calendario ausente ou nao representavel como Gregorian AD; nao converter por suposicao"); }
}
