package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;

/**
 * Outlines seen through terrain, one switch per kind of figure, in the colours of the debug
 * boxes: what the game draws, what the mod draws and how, and where it leaves a mob out.
 * Made to compare screenshots with and without an optimisation.
 */
public final class OutlinesScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public OutlinesScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "outlines"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "outlines.drawn"));
        list.addSmall(
                bool("outlineGameMobs", c.outlineGameMobs, v -> c.outlineGameMobs = v),
                bool("outlineLiveMobs", c.outlineLiveMobs, v -> c.outlineLiveMobs = v),
                bool("outlineCopies", c.outlineCopies, v -> c.outlineCopies = v),
                bool("outlineForeseen", c.outlineForeseen, v -> c.outlineForeseen = v),
                bool("outlinePlayers", c.outlinePlayers, v -> c.outlinePlayers = v),
                bool("outlineImpostors", c.outlineImpostors, v -> c.outlineImpostors = v));

        header(Component.translatable(KEY + "outlines.leftOut"));
        list.addSmall(bool("outlineLeftOut", c.outlineLeftOut, v -> c.outlineLeftOut = v));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
