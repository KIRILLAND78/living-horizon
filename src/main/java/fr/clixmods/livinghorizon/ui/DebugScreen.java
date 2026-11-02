package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.choice;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.unit;

/**
 * Tools to see what the mod does: boxes and labels on every distant mob, outlines by kind, a panel of
 * counts and costs, a view of the depth buffer with and without Voxy's terrain - and
 * every optimisation on its own switch, to measure what it saves on the panel.
 */
public final class DebugScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public DebugScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "debug.title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "debug.view"));
        list.addSmall(bool("debugHud", c.debugHud, v -> c.debugHud = v));
        list.addBig(choice("debugDepthView", List.of("0", "1", "2", "3", "4"), String.valueOf(c.debugDepthView),
                v -> c.debugDepthView = Integer.parseInt(v)));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "boxes.open"), b -> minecraft.setScreen(new BoxesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "boxes.open.tooltip"))).build(),
                Button.builder(Component.translatable(KEY + "outlines.open"), b -> minecraft.setScreen(new OutlinesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "outlines.open.tooltip"))).build());
        list.addSmall(
                Button.builder(Component.translatable(KEY + "stats.open"), b -> minecraft.setScreen(new StatsScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "stats.open.tooltip"))).build(), null);

        header(Component.translatable(KEY + "debug.rendering"));
        list.addSmall(
                bool("extendFarPlane", c.extendFarPlane, v -> c.extendFarPlane = v),
                unit("minApparentPixels", "pixels", 0, 32, (int) c.minApparentPixels, v -> c.minApparentPixels = v),
                bool("renderTrackedVehiclesFar", c.renderTrackedVehiclesFar, v -> c.renderTrackedVehiclesFar = v));

        header(Component.translatable(KEY + "debug.optimisations"));
        list.addSmall(
                bool("optViewCulling", c.optViewCulling, v -> c.optViewCulling = v),
                bool("optTinyCulling", c.optTinyCulling, v -> c.optTinyCulling = v),
                bool("depthOcclusion", c.depthOcclusion, v -> c.depthOcclusion = v),
                bool("optLazyDepth", c.optLazyDepth, v -> c.optLazyDepth = v),
                bool("optParallelRead", c.optParallelRead, v -> c.optParallelRead = v),
                bool("optColumnCache", c.optColumnCache, v -> c.optColumnCache = v),
                bool("hideOccludedMobs", c.hideOccludedMobs, v -> c.hideOccludedMobs = v),
                bool("onlyOverTerrain", c.onlyOverTerrain, v -> c.onlyOverTerrain = v),
                bool("optOcclusionQueries", c.optOcclusionQueries, v -> c.optOcclusionQueries = v),
                bool("optDhFade", c.optDhFade, v -> c.optDhFade = v),
                bool("optVoxyShadows", c.optVoxyShadows, v -> c.optVoxyShadows = v),
                bool("optVoxyFog", c.optVoxyFog, v -> c.optVoxyFog = v),
                bool("optDhFog", c.optDhFog, v -> c.optDhFog = v),
                bool("optBackgroundBuild", c.optBackgroundBuild, v -> c.optBackgroundBuild = v),
                bool("optStillTiny", c.optStillTiny, v -> c.optStillTiny = v),
                bool("optFreezeHidden", c.optFreezeHidden, v -> c.optFreezeHidden = v));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
