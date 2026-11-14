package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whether a mob or another entity stands underground - in a cave, a mine, a cellar - for
 * {@link FarConfig#hideUndergroundMobs}: nobody far away can see it, so it is not drawn.
 *
 * <p>Underground is no sky light where the mob stands and a solid block somewhere above
 * it, so that a fish deep in the sea (sky light fades in water) and a mob at the bottom
 * of a ravine stay drawn. Read from the chunks loaded here; past them, from the far world
 * (Voxy or Distant Horizons): its top block well above the mob, and neither leaves nor water.
 *
 * <p>Only in a dimension with a sky and no ceiling: in the Nether, everything is under the
 * bedrock roof and nothing would be drawn; the End has no sky light to read.
 */
public final class Underground {
    /** Blocks between a mob's feet and the far world's top block for it to count as under it. */
    private static final int FAR_DEPTH = 6;

    /** The live mobs already checked, with the block they were checked at. */
    private static final Map<Entity, Check> LIVE = new WeakHashMap<>();

    private record Check(long block, boolean underground) {
    }

    private Underground() {
    }

    /** Whether this level has a surface to be under: a sky and no ceiling. */
    public static boolean applies(ClientLevel level) {
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasCeiling();
    }

    /** Whether the setting is on and this level is one it applies to. */
    public static boolean hiding(ClientLevel level, FarConfig config) {
        return config.hideUndergroundMobs && applies(level);
    }

    /** A real mob the server sends: checked again whenever it moves to another block. */
    public static boolean live(ClientLevel level, Entity entity) {
        long block = BlockPos.asLong(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
        Check known = LIVE.get(entity);
        if (known != null && known.block == block) return known.underground;
        Boolean found = at(level, entity.getX(), entity.getY(), entity.getZ());
        if (found == null) return false;
        LIVE.put(entity, new Check(block, found));
        return found;
    }

    /** Forgets the live mobs checked, with the level they were in. */
    public static void clear() {
        LIVE.clear();
    }

    /** Whether a spot is underground, from the chunks loaded here; null where none is. */
    public static @Nullable Boolean at(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        // The level says it holds every chunk (ClientLevel.hasChunk): its chunk source knows.
        if (!Footing.loaded(level, x, z)) return null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, by, bz);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        sky = Math.max(sky, level.getBrightness(LightLayer.SKY, pos.setY(by + 1)));
        if (sky > 0) return false;
        // Sky light also fades to nothing deep under water: there must be a roof too.
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        for (int k = by + 1; k < top; k++) {
            if (level.getBlockState(pos.setY(k)).canOcclude()) return true;
        }
        return false;
    }

    /**
     * From the top block of the far world at the mob's column: null where the far world has
     * nothing there, or where its top block (leaves, water) says nothing of what is below.
     */
    static @Nullable Boolean far(LodWorld.@Nullable Surface surface, double y) {
        if (surface == null) return null;
        BlockState block = surface.block();
        if (surface.water() || block == null || block.getBlock() instanceof LeavesBlock) return null;
        return surface.y() >= Mth.floor(y) + FAR_DEPTH && block.canOcclude();
    }
}
