package fr.clixmods.livinghorizon.debug;

// The F3 screen lists entries a mod can add from 1.21.9; before, this one is not shown.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The F3 screen's line for the mod's own entities: how many polygons the distant players
 * and mobs cost, and how many of them are impostors. The game's own entities are not in it.
 */
public final class FakeEntitiesDebugEntry implements DebugScreenEntry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "fake_entities");

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk,
                        @Nullable LevelChunk serverChunk) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant()) return;
        displayer.addLine(I18n.get("livinghorizon.debug.f3.polygons", PolygonStats.polygons(), PolygonStats.triangles(),
                PolygonStats.models(), PolygonStats.modelPolygons(), PolygonStats.impostors()));
        if (config.anyImpostors()) {
            displayer.addLine(I18n.get(ImpostorAtlas.failed() ? "livinghorizon.debug.f3.impostorsFailed"
                    : "livinghorizon.debug.f3.impostors", ImpostorAtlas.sheets(), ImpostorAtlas.waiting()));
        }
    }

    @Override
    public DebugEntryCategory category() {
        return DebugEntryCategory.RENDERER;
    }
}
//?}
