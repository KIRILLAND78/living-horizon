package fr.clixmods.livinghorizon.track;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** When a player who logged off was last seen, written the way the player's language writes dates. */
public final class LastSeen {
    private LastSeen() {
    }

    /**
     * The date and time, in the game's language ({@code "fr_fr"}) and the computer's time
     * zone; null when unknown (0).
     */
    public static @Nullable String format(long millis, String language, ZoneId zone) {
        return format(millis, language, zone, FormatStyle.MEDIUM);
    }

    /** The same, the date written {@code style} ({@link FormatStyle#SHORT}: in figures). */
    public static @Nullable String format(long millis, String language, ZoneId zone, FormatStyle style) {
        if (millis <= 0) return null;
        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(style, FormatStyle.SHORT)
                .withLocale(locale(language)).withZone(zone);
        return format.format(Instant.ofEpochMilli(millis));
    }

    /** The game's language code as a locale: {@code "fr_fr"} is French as written in France. */
    static Locale locale(String language) {
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }
}
