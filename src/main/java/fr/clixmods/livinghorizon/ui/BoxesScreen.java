package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;

/**
 * The debug boxes, seen through terrain, and the labels above them: on or off, and one switch per
 * kind of figure, in the colours of the outlines. Which mobs are real and which are copies, met
 * or only foreseen from the seed, at a glance, without the others in the way.
 */
public final class BoxesScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public BoxesScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "boxes"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "boxes.shown"));
        list.addSmall(
                bool("debugBoxes", c.debugBoxes, v -> c.debugBoxes = v),
                bool("debugLabels", c.debugLabels, v -> c.debugLabels = v));

        header(Component.translatable(KEY + "boxes.kinds"));
        list.addSmall(
                bool("debugGameMobs", c.debugGameMobs, v -> c.debugGameMobs = v),
                bool("boxLiveMobs", c.boxLiveMobs, v -> c.boxLiveMobs = v),
                bool("boxCopies", c.boxCopies, v -> c.boxCopies = v),
                bool("boxForeseen", c.boxForeseen, v -> c.boxForeseen = v),
                bool("boxPlayers", c.boxPlayers, v -> c.boxPlayers = v),
                bool("boxLeftOut", c.boxLeftOut, v -> c.boxLeftOut = v));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
