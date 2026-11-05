package fr.clixmods.livinghorizon.ambient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * When the easter eggs may come by chance, in days of the game this player has lived in a
 * world: each from a day of its own on, never two within a few days of each other; the daily
 * ones ({@link Daily}: 3, 4 and 5) on days of their own, alongside the others. A day is a dawn seen in the overworld,
 * a night slept through included.
 *
 * <p>Kept per server, in {@code config/livinghorizon/calendar/<server>.json}. Calling an
 * easter egg by its command is none of its business.
 */
public final class EggCalendar {
    /**
     * The first day each easter egg may come by chance, by number; the daily ones' (3, 4, 5)
     * are drawn per world, 14 and 20 are no more, and 21 keeps to dates of its own.
     */
    private static final int[] FIRST = {0, 3, 5, 0, 0, 0, 6, 8, 4, 5, 4, 3, 2, 6, 3, 2, 7, 3, 2, 3, 5, 0};
    /** The daily easter eggs, by number. */
    private static final int[] DAILY = {3, 4, 5};

    /** Gson writes every field that is not transient, under names that say nothing of what they count. */
    private static final class State {
        /** Dawns seen. */
        int days;
        /** No easter egg comes by chance before this day: the last one was not long ago. */
        @SerializedName("next")
        int quietUntil;
        /** Each daily one comes every day from this one on, until it is seen; -1 until drawn. */
        @SerializedName("next3")
        int from3 = -1;
        @SerializedName("next4")
        int from4 = -1;
        @SerializedName("next5")
        int from5 = -1;
        /** The day each daily one last came; -1 if never. */
        @SerializedName("last3")
        int came3 = -1;
        @SerializedName("last4")
        int came4 = -1;
        @SerializedName("last5")
        int came5 = -1;

        int from(int number) {
            return number == 3 ? from3 : number == 4 ? from4 : from5;
        }

        void from(int number, int day) {
            if (number == 3) from3 = day;
            else if (number == 4) from4 = day;
            else from5 = day;
        }

        int came(int number) {
            return number == 3 ? came3 : number == 4 ? came4 : came5;
        }

        void came(int number, int day) {
            if (number == 3) came3 = day;
            else if (number == 4) came4 = day;
            else came5 = day;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RandomSource random = RandomSource.create();
    private State state = new State();
    private @Nullable Path file;
    private boolean bright, known;

    /** Days lived in this world so far. */
    public int days() {
        return state.days;
    }

    public void open(String server) {
        close();
        file = Platform.configDir().resolve("livinghorizon").resolve("calendar")
                .resolve(RestingPlayers.safe(server) + ".json");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                State read = GSON.fromJson(reader, State.class);
                if (read != null) state = read;
            } catch (IOException | RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
            }
        }
        boolean drawn = false;
        for (int number : DAILY) {
            if (state.from(number) >= 0) continue;
            // The second or third day.
            state.from(number, 2 + random.nextInt(2));
            drawn = true;
        }
        if (drawn) save();
    }

    public void close() {
        save();
        state = new State();
        file = null;
        known = false;
    }

    /** Counts a dawn when the overworld's sky turns bright. */
    void tick(ClientLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            known = false;
            return;
        }
        boolean now = level.isBrightOutside();
        if (known && now && !bright) {
            state.days++;
            save();
        }
        bright = now;
        known = true;
    }

    /** Whether easter egg {@code number} may come by chance today. */
    boolean allows(int number) {
        return state.days >= FIRST[number] && state.days >= state.quietUntil;
    }

    /** Easter egg {@code number} came by chance: none other for two to four days. */
    void shown(int number) {
        state.quietUntil = state.days + 2 + random.nextInt(3);
        save();
    }

    /** Whether daily easter egg {@code number} is due today: its day has come and it has not come yet. */
    boolean dailyDue(int number) {
        int from = state.from(number);
        return from >= 0 && state.days >= from && state.came(number) != state.days;
    }

    /** Daily easter egg {@code number} came today. */
    void dailyCame(int number) {
        state.came(number, state.days);
        save();
    }

    /** Daily easter egg {@code number} was seen: back in one to three days. */
    void dailySeen(int number) {
        state.from(number, state.days + 1 + random.nextInt(3));
        save();
    }

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
