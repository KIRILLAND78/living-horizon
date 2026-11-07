package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Foresight;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * What the mod keeps, counted: the remembered mobs by kind (met, shared by the server, guessed
 * from the seed), how many are drawn and built, the guessed mobs kept apart and the memory they
 * take, and the files on disk. Worked out when opened, and again with <i>Refresh</i>.
 */
public final class StatsScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.stats.";

    public StatsScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("livinghorizon.options.stats"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        MobMemory memory = FarPlayerTracker.get().mobs();
        MobMemory.Stats mobs = memory.stats();
        Foresight.Stats guessed = memory.foresight().stats();

        header(Component.translatable(KEY + "memory"));
        line("remembered", mobs.remembered());
        line("kinds", mobs.met(), mobs.shared(), mobs.guessed());
        line("drawn", mobs.chosen(), mobs.built(), mobs.waiting());

        header(Component.translatable(KEY + "guessed"));
        line("index", guessed.mobs(), guessed.chunks());
        line("asked", guessed.asked());
        line("ram", size(guessed.bytes()));

        header(Component.translatable(KEY + "disk"));
        Path all = Platform.configDir().resolve("livinghorizon");
        line("mobs", mobs.file() == null ? size(-1) : size(bytes(mobs.file())), size(bytes(all.resolve("mobs"))));
        line("foreseen", guessed.folder() == null ? size(-1) : size(bytes(guessed.folder())), size(bytes(all.resolve("foreseen"))));
        line("impostors", size(bytes(all.resolve("impostors"))));
        line("resting", size(bytes(all.resolve("resting"))));
        line("total", size(bytes(all)));

        list.addSmall(Button.builder(Component.translatable(KEY + "refresh"),
                button -> minecraft.setScreen(new StatsScreen(lastScreen))).build(), null);
    }

    private void line(String key, Object... values) {
        Object[] shown = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            shown[i] = values[i] instanceof Number number && !(values[i] instanceof Double)
                    ? String.format(Locale.ROOT, "%,d", number.longValue()).replace(',', ' ') : values[i];
        }
        subheader(Component.translatable(KEY + key, shown));
    }

    /** Bytes as a reader says them, in the units of the player's language; "-" for none. */
    private static Component size(long bytes) {
        if (bytes < 0) return Component.literal("-");
        if (bytes < 1024) return Component.translatable(KEY + "bytes", bytes);
        if (bytes < 1024 * 1024) return Component.translatable(KEY + "kilobytes", String.format(Locale.ROOT, "%.1f", bytes / 1024.0));
        return Component.translatable(KEY + "megabytes", String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0)));
    }

    /** What a file or a folder takes on disk; 0 when it is not there. */
    private static long bytes(Path path) {
        try {
            if (!Files.exists(path)) return 0;
            if (!Files.isDirectory(path)) return Files.size(path);
            try (Stream<Path> files = Files.walk(path)) {
                return files.filter(Files::isRegularFile).mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
