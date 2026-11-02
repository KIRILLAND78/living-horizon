package fr.clixmods.livinghorizon.ambient;

import net.minecraft.util.RandomSource;

/**
 * When one of the daily easter eggs (3, 4 and 5) comes: once a day from its own first day on,
 * at a moment of the day drawn at each dawn, by day or by night, alongside the other easter
 * eggs; until it is seen, then not for one to three days. Its days are kept by the
 * {@link EggCalendar}.
 */
final class Daily {
    /** Ticks into the day before it comes: from half a minute to eighteen, by day or by night. */
    private static final int EARLIEST = 600, SPREAD = 21000;
    /** Ticks before it tries again, when nowhere in sight was found. */
    private static final int RETRY = 200;

    private final EggCalendar calendar;
    private final int number;
    private final RandomSource random = RandomSource.create();
    /** Ticks into the day before it comes, counted for the day {@link #day}; -1 until drawn. */
    private int wait = -1, day = -1;

    Daily(EggCalendar calendar, int number) {
        this.calendar = calendar;
        this.number = number;
    }

    /** Called each tick while it is not there: true when it is time to come (again in a while if it finds nowhere). */
    boolean due() {
        if (wait < 0 || day != calendar.days()) {
            wait = EARLIEST + random.nextInt(SPREAD);
            day = calendar.days();
        }
        if (!calendar.dailyDue(number) || --wait > 0) return false;
        wait = RETRY;
        return true;
    }

    /** For the game tests: it comes in a second, if today is one of its days. */
    void soon() {
        wait = 20;
        day = calendar.days();
    }

    /** It came today. */
    void came() {
        calendar.dailyCame(number);
    }

    /** It was seen: back in one to three days. */
    void seen() {
        calendar.dailySeen(number);
    }
}
