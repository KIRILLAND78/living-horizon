package fr.clixmods.livinghorizon.track;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Where a figure of a player can be, read from the blocks the client has: room for its
 * body, something under its feet, the floor it falls onto, the first free place above it.
 * Positions are those of its feet, at the middle of its 0.6 block wide body.
 *
 * <p>The blocks come from a {@link Source}: the level itself on the game's thread
 * ({@link #live}), or a {@link BlockCopy} of it on the threads that search the ground.
 */
final class Footing {
    /** A player's height, standing. */
    static final double STANDING = 1.8;

    private Footing() {
    }

    /** The blocks read: the level, or a copy of part of it. */
    interface Source {
        BlockState state(BlockPos at);

        FluidState fluid(BlockPos at);

        /** Whether the blocks there are known: a loaded chunk, or inside the copy. */
        boolean loaded(BlockPos at);

        VoxelShape collision(BlockState state, BlockPos at);

        /** The height of the fluid in its block, from 0 to 1; a full block under more of it. */
        double fluidHeight(FluidState fluid, BlockPos at);

        /** The lowest block known, and the one above the highest. */
        int minY();

        int top();
    }

    /** The level itself, read on the game's thread. */
    static Source live(ClientLevel level) {
        return new Live(level);
    }

    private record Live(ClientLevel level) implements Source {
        @Override
        public BlockState state(BlockPos at) {
            return level.getBlockState(at);
        }

        @Override
        public FluidState fluid(BlockPos at) {
            return level.getFluidState(at);
        }

        @Override
        public boolean loaded(BlockPos at) {
            return Footing.loaded(level, at);
        }

        @Override
        public VoxelShape collision(BlockState state, BlockPos at) {
            return state.getCollisionShape(level, at);
        }

        @Override
        public double fluidHeight(FluidState fluid, BlockPos at) {
            return fluid.getHeight(level, at);
        }

        @Override
        public int minY() {
            return level.getMinY();
        }

        @Override
        public int top() {
            return level.getMinY() + level.getHeight();
        }
    }

    /**
     * Whether the client holds the blocks of this column. Never {@code level.hasChunk}: the
     * client's level answers yes for every chunk, loaded or not, and an unloaded one reads as air.
     */
    static boolean loaded(ClientLevel level, double x, double z) {
        return level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    /** {@link #loaded(ClientLevel, double, double)} for the column of a block. */
    static boolean loaded(ClientLevel level, BlockPos at) {
        return level.getChunkSource().hasChunk(at.getX() >> 4, at.getZ() >> 4);
    }

    /** Half a player's width. */
    static final double HALF = 0.3;

    private static AABB body(double x, double y, double z, double half, double height) {
        return new AABB(x - half, y + 0.05, z - half, x + half, y + height, z + half);
    }

    /** Room for it there: no block in its body. */
    static boolean fits(Source level, double x, double y, double z, double height) {
        return fits(level, x, y, z, HALF, height);
    }

    /** Room there for a body {@code half} a width across each way: a mount and its rider. */
    static boolean fits(Source level, double x, double y, double z, double half, double height) {
        return free(level, body(x, y, z, half, height));
    }

    /** Something to rest on: a block right under it, or water or lava to float on. */
    static boolean supported(Source level, double x, double y, double z) {
        return supported(level, x, y, z, HALF);
    }

    /** Nothing in the way between where it is and where it goes, straight up or down. */
    static boolean clear(Source level, double x, double y, double target, double z, double height) {
        return free(level, body(x, Math.min(y, target), z, HALF, height + Math.abs(target - y)));
    }

    /**
     * No block in the box, doors and fence gates left out: a figure walks through them, shut
     * or not, as it cannot open them. Blocks alone, read straight: this runs thousands of times
     * when a figure looks for its way.
     */
    static boolean free(Source level, AABB box) {
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.state(at);
            if (state.isAir() || passable(state)) continue;
            VoxelShape shape = level.collision(state, at);
            if (shape.isEmpty()) continue;
            int bx = at.getX(), by = at.getY(), bz = at.getZ();
            // Most blocks are whole: no list of boxes to make for them.
            if (shape == Shapes.block()) {
                if (box.intersects(bx, by, bz, bx + 1, by + 1, bz + 1)) return false;
                continue;
            }
            for (AABB part : shape.toAabbs()) {
                if (box.intersects(part.minX + bx, part.minY + by, part.minZ + bz,
                        part.maxX + bx, part.maxY + by, part.maxZ + bz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Doors and fence gates: walked through. */
    static boolean passable(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * The top of the highest block under its feet, between {@code bottom} and {@code y};
     * NaN if there is none.
     */
    static double ground(Source level, double x, double y, double z, double bottom) {
        return ground(level, x, y, z, bottom, HALF);
    }

    /** {@link #ground(Source, double, double, double, double)} under a body this wide. */
    static double ground(Source level, double x, double y, double z, double bottom, double half) {
        AABB column = new AABB(x - half, bottom, z - half, x + half, y, z + half);
        double top = Double.NaN;
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(column.minX, column.minY, column.minZ),
                BlockPos.containing(column.maxX, column.maxY, column.maxZ))) {
            BlockState state = level.state(at);
            if (state.isAir() || passable(state)) continue;
            VoxelShape shape = level.collision(state, at);
            if (shape.isEmpty()) continue;
            int bx = at.getX(), by = at.getY(), bz = at.getZ();
            if (shape == Shapes.block()) {
                if (column.intersects(bx, by, bz, bx + 1, by + 1, bz + 1)) top = higher(top, by + 1, y);
                continue;
            }
            for (AABB part : shape.toAabbs()) {
                if (column.intersects(part.minX + bx, part.minY + by, part.minZ + bz,
                        part.maxX + bx, part.maxY + by, part.maxZ + bz)) {
                    top = higher(top, part.maxY + by, y);
                }
            }
        }
        return top;
    }

    /** The higher of {@code top} and {@code max}, if {@code max} is not above its feet at {@code y}. */
    private static double higher(double top, double max, double y) {
        return max <= y + 1e-4 && !(max <= top) ? max : top;
    }

    /** The first thing under it to land on, a block or the surface of water; where it is if there is none. */
    static double floorBelow(Source level, double x, double y, double z) {
        return floorBelow(level, x, y, z, HALF);
    }

    /** {@link #floorBelow(Source, double, double, double)} under a body this wide. */
    static double floorBelow(Source level, double x, double y, double z, double half) {
        double floor = ground(level, x, y, z, level.minY(), half);
        double lowest = Double.isNaN(floor) ? level.minY() : floor;
        for (int by = BlockPos.containing(x, y, z).getY(); by >= Math.floor(lowest); by--) {
            BlockPos at = BlockPos.containing(x, by, z);
            FluidState fluid = level.fluid(at);
            if (fluid.isEmpty()) continue;
            double surface = by + level.fluidHeight(fluid, at);
            if (surface <= y + 1e-4) return Double.isNaN(floor) ? surface : Math.max(floor, surface);
        }
        return Double.isNaN(floor) ? y : floor;
    }

    /** The first place above where it fits, standing on what is there; where it is if there is none. */
    static double freeAbove(Source level, double x, double y, double z, double height) {
        return freeAbove(level, x, y, z, HALF, height);
    }

    /** {@link #freeAbove(Source, double, double, double, double)} for a body this wide. */
    static double freeAbove(Source level, double x, double y, double z, double half, double height) {
        int top = level.top();
        for (int by = (int) Math.floor(y) + 1; by < top; by++) {
            if (!fits(level, x, by, z, half, height)) continue;
            double landed = ground(level, x, by, z, by - 1, half);
            return !Double.isNaN(landed) && fits(level, x, landed, z, half, height) ? landed : by;
        }
        return y;
    }

    /** Something under a body this wide: a block right under it, or water or lava to float on. */
    static boolean supported(Source level, double x, double y, double z, double half) {
        if (!free(level, new AABB(x - half, y - 0.1, z - half, x + half, y + 0.01, z + half))) return true;
        return !level.fluid(BlockPos.containing(x, y + 0.05, z)).isEmpty()
                || !level.fluid(BlockPos.containing(x, y - 0.1, z)).isEmpty();
    }

    /**
     * The height a player stands at with its feet in block ({@code x}, {@code y}, {@code z}),
     * in the middle of it; NaN where a player would not stand: no floor, no room, water,
     * lava or fire.
     */
    static double stand(Source level, int x, int y, int z) {
        return stand(level, x, y, z, HALF, STANDING);
    }

    /** {@link #stand(Source, int, int, int)} for a body this wide and tall: a mount and its rider. */
    static double stand(Source level, int x, int y, int z, double half, double height) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.loaded(feet) || !level.fluid(feet).isEmpty() || burns(level.state(feet))) {
            return Double.NaN;
        }
        double cx = x + 0.5, cz = z + 0.5;
        double top = ground(level, cx, y + 0.6, cz, y - 0.5, half);
        if (Double.isNaN(top) || top < y - 1e-3) return Double.NaN;
        if (burns(level.state(feet.below())) && top <= y + 1e-3) return Double.NaN;
        return fits(level, cx, top, cz, half, height) ? top : Double.NaN;
    }

    /**
     * The height a boat floats at in block ({@code x}, {@code y}, {@code z}): the surface of
     * the water there, with room above it for the boat and its rider; NaN where there is none.
     */
    static double afloat(Source level, int x, int y, int z, double half, double height) {
        BlockPos at = new BlockPos(x, y, z);
        if (!level.loaded(at)) return Double.NaN;
        FluidState fluid = level.fluid(at);
        if (!fluid.is(FluidTags.WATER) || !level.fluid(at.above()).isEmpty()) return Double.NaN;
        double surface = y + level.fluidHeight(fluid, at);
        return fits(level, x + 0.5, surface, z + 0.5, half, height) ? surface : Double.NaN;
    }

    /** Fire, a lit campfire, magma: nobody stands there on purpose. */
    static boolean burns(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** In lava or fire, some part of a body this high. */
    static boolean inFire(Source level, double x, double y, double z, double height) {
        for (int by = (int) Math.floor(y); by <= Math.floor(y + height); by++) {
            BlockPos at = BlockPos.containing(x, by, z);
            if (level.fluid(at).is(FluidTags.LAVA) || burns(level.state(at))) return true;
        }
        return false;
    }

    /**
     * Its feet in water: wading, swimming, or floating on the surface with nothing under it.
     * Where a player who logged off is never left: see {@link #shore}.
     */
    static boolean wet(Source level, double x, double y, double z) {
        if (level.fluid(BlockPos.containing(x, y + 0.05, z)).is(FluidTags.WATER)) return true;
        return level.fluid(BlockPos.containing(x, y - 0.1, z)).is(FluidTags.WATER)
                && free(level, new AABB(x - 0.3, y - 0.1, z - 0.3, x + 0.3, y + 0.01, z + 0.3));
    }

    /** The top of the water it is in: the surface a boat floats on. */
    static double surface(Source level, double x, double y, double z) {
        int top = level.top();
        BlockPos.MutableBlockPos at = BlockPos.containing(x, y + 0.05, z).mutable();
        // Floating on it: the water is just under its feet.
        if (!level.fluid(at).is(FluidTags.WATER)) at.move(0, -1, 0);
        double surface = y;
        for (; at.getY() < top; at.move(0, 1, 0)) {
            FluidState fluid = level.fluid(at);
            if (!fluid.is(FluidTags.WATER)) break;
            surface = at.getY() + level.fluidHeight(fluid, at);
        }
        return surface;
    }

    /** Whether every block within {@code radius} blocks across is known to the client. */
    static boolean loadedAround(Source level, double x, double z, int radius) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!level.loaded(new BlockPos(bx + dx, 0, bz + dz))) return false;
            }
        }
        return true;
    }

    /**
     * The nearest place out of the water to stand on, within {@code radius} blocks across of
     * where it is: a bank or a beach, from the surface up to three blocks above it, never a
     * pocket of air under the water. Null when there is none.
     */
    static @Nullable Vec3 shore(Source level, double x, double y, double z, int radius) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int water = (int) Math.floor(surface(level, x, y, z) - 1e-3);
        Vec3 best = null;
        double bestCost = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int across = dx * dx + dz * dz;
                if (across > radius * radius || across >= bestCost) continue;
                for (int by = water; by <= water + 3; by++) {
                    double height = stand(level, bx + dx, by, bz + dz);
                    if (Double.isNaN(height)) continue;
                    // Across first; a climb weighs a little, to break ties between banks.
                    double cost = across + (by - water) * 0.1;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new Vec3(bx + dx + 0.5, height, bz + dz + 0.5);
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** Its head under water. */
    static boolean underWater(Source level, double x, double eyes, double z) {
        BlockPos at = BlockPos.containing(x, eyes, z);
        FluidState fluid = level.fluid(at);
        return fluid.is(FluidTags.WATER) && eyes < at.getY() + level.fluidHeight(fluid, at);
    }
}
