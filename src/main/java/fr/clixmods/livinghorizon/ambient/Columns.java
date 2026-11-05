package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The top of many columns at once, for the easter eggs that look for a place: read here
 * where the game draws its chunks, from the far world (Voxy, Distant Horizons) off the game's
 * thread past them, and only where something is drawn ({@link #drawn}).
 */
final class Columns {
    /**
     * One column: the height to stand at, whether that is water, its biome; NaN where
     * nothing is known. {@code canopy}: the far world's top is leaves, the ground under them
     * unknown (a loaded column gives the ground under its trees). {@code block}: the block on
     * top, null where nothing says.
     */
    record Column(double x, double z, double y, boolean water, String biome, boolean canopy, @Nullable BlockState block) {
        boolean known() {
            return !Double.isNaN(y);
        }

        /** Known ground, out of the water and under no leaves: somewhere to walk. */
        boolean dry() {
            return known() && !water && !canopy;
        }
    }

    private Columns() {
    }

    /**
     * Whether the ground of this column is drawn, seen from the player: by the game inside the
     * circle of chunks it draws, a little in from its edge, where the chunk is loaded (it keeps
     * a square of them loaded, drawing only the circle, so a loaded chunk in a corner shows
     * nothing); by the far world past that circle, as far as it draws and its fog leaves clear.
     * Inside the circle the far world draws nothing: a chunk not loaded there is a hole,
     * whatever the far world knows of it. Called on the game's thread.
     */
    static boolean drawn(ClientLevel level, LocalPlayer self, double x, double z) {
        double across = Math.hypot(x - self.getX(), z - self.getZ());
        double edge = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        if (across <= edge - 16) return loaded(level, x, z);
        return across >= edge + 16 && across <= Sight.farClear();
    }

    /**
     * Whether this column is loaded here, and holds something: a chunk at the edge of the view
     * can be empty to the bottom of the world while it comes or goes, its ground read as the
     * bottom of the world.
     */
    static boolean loaded(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        return level.hasChunk(bx >> 4, bz >> 4)
                && level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) > level.getMinY();
    }

    /** Entry {@code i} is the column at {@code (xs[i], zs[i])}; called on the game's thread. */
    static CompletableFuture<Column[]> read(ClientLevel level, double[] xs, double[] zs) {
        Column[] found = new Column[xs.length];
        List<Integer> far = new ArrayList<>();
        LocalPlayer self = Minecraft.getInstance().player;
        double edge = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
        for (int i = 0; i < xs.length; i++) {
            int bx = Mth.floor(xs[i]), bz = Mth.floor(zs[i]);
            if (self == null || !drawn(level, self, xs[i], zs[i])) {
                found[i] = new Column(xs[i], zs[i], Double.NaN, false, "", false, null);
                continue;
            }
            if (Math.hypot(xs[i] - self.getX(), zs[i] - self.getZ()) > edge) {
                // Past the game's circle: what the far world draws there.
                far.add(i);
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockPos under = new BlockPos(bx, top - 1, bz);
            String biome = level.getBiome(under).unwrapKey().map(key -> key.identifier().toString()).orElse("");
            found[i] = new Column(xs[i], zs[i], top, !level.getFluidState(under).isEmpty(), biome, false,
                    level.getBlockState(under));
        }
        if (far.isEmpty()) return CompletableFuture.completedFuture(found);
        int bottom = level.getMinY();
        int[] fx = new int[far.size()], fz = new int[far.size()];
        for (int k = 0; k < far.size(); k++) {
            fx[k] = Mth.floor(xs[far.get(k)]);
            fz[k] = Mth.floor(zs[far.get(k)]);
        }
        return LodWorld.surfaces(fx, fz).handle((surfaces, error) -> {
            // A far world that fails leaves those columns unknown, never the search hanging; so
            // does one with nothing above the bottom of the world.
            for (int k = 0; k < far.size(); k++) {
                int i = far.get(k);
                LodWorld.Surface surface = surfaces == null ? null : surfaces[k];
                found[i] = surface == null || surface.y() <= bottom ? new Column(xs[i], zs[i], Double.NaN, false, "", false, null)
                        : new Column(xs[i], zs[i], surface.y() + 1, surface.water(), surface.biome(),
                        surface.block() != null && surface.block().is(BlockTags.LEAVES), surface.block());
            }
            return found;
        });
    }
}
