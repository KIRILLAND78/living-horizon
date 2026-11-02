package fr.clixmods.livinghorizon.track;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The blocks of a box of the world, copied on the game's thread for another to read: every
 * 16-block section the box touches, its palette and all, which costs about as much as a few
 * dozen block reads. A section of air is not copied at all. What lies outside the box, or in
 * a chunk the client did not have, is unknown: air, nothing to stand on.
 *
 * <p>A copy never changes: what was built or broken after it is not in it. Collision shapes
 * are taken with nothing around ({@link EmptyBlockGetter}), so that a block of another mod
 * that looks at its neighbours for its shape never reads the level from another thread.
 */
final class BlockCopy implements Footing.Source {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** A section of nothing but air: known, and not copied. */
    private static final Object EMPTY = new Object();

    private final int minCx, minSy, minCz, countX, countY, countZ;
    /** The blocks the copy holds, from {@link #minY} to {@link #maxY}. */
    private final int minY, maxY;
    /** Each section: null where unknown, {@link #EMPTY}, or its blocks. */
    private final Object[] sections;

    private BlockCopy(int minCx, int minSy, int minCz, int countX, int countY, int countZ, int minY, int maxY) {
        this.minCx = minCx;
        this.minSy = minSy;
        this.minCz = minCz;
        this.countX = countX;
        this.countY = countY;
        this.countZ = countZ;
        this.minY = minY;
        this.maxY = maxY;
        this.sections = new Object[countX * countY * countZ];
    }

    /**
     * A copy of the box from ({@code minX}, {@code minY}, {@code minZ}) to ({@code maxX},
     * {@code maxY}, {@code maxZ}), blocks included, within the level's height; to be made
     * with {@link Builder#copy}, a few sections at a time if it is large.
     */
    static Builder of(ClientLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int low = Math.max(minY, level.getMinY()), high = Math.min(maxY, level.getMinY() + level.getHeight() - 1);
        if (high < low) high = low;
        int minSy = low >> 4, maxSy = high >> 4;
        BlockCopy copy = new BlockCopy(minX >> 4, minSy, minZ >> 4, (maxX >> 4) - (minX >> 4) + 1, maxSy - minSy + 1,
                (maxZ >> 4) - (minZ >> 4) + 1, low, high);
        return new Builder(copy);
    }

    /** A copy being made. */
    static final class Builder {
        private final BlockCopy copy;
        private int next;

        private Builder(BlockCopy copy) {
            this.copy = copy;
        }

        /** Every section at once. */
        BlockCopy copy(ClientLevel level) {
            copy(level, Integer.MAX_VALUE);
            return copy;
        }

        /**
         * Copies sections until {@code budget} of them with blocks in them are copied; the
         * number copied. Sections of air and chunks the client does not have cost nothing.
         */
        int copy(ClientLevel level, int budget) {
            BlockCopy into = copy;
            int copied = 0;
            int perChunk = into.countY;
            while (next < into.sections.length && copied < budget) {
                int column = next / perChunk;
                int cx = into.minCx + column / into.countZ, cz = into.minCz + column % into.countZ;
                // The client's level says yes for every chunk: its chunk source knows.
                if (!level.getChunkSource().hasChunk(cx, cz)) {
                    // Unknown, the whole column of sections.
                    next = (column + 1) * perChunk;
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] all = chunk.getSections();
                for (; next < (column + 1) * perChunk && copied < budget; next++) {
                    int sy = into.minSy + next % perChunk;
                    int index = chunk.getSectionIndex(sy << 4);
                    if (index < 0 || index >= all.length) continue;
                    LevelChunkSection section = all[index];
                    if (section == null || section.hasOnlyAir()) {
                        into.sections[next] = EMPTY;
                    } else {
                        into.sections[next] = section.getStates().copy();
                        copied++;
                    }
                }
            }
            return copied;
        }

        /** Every section copied, or known to be unknown. */
        boolean done() {
            return next >= copy.sections.length;
        }

        /** The copy, once {@link #done()}. */
        BlockCopy build() {
            return copy;
        }
    }

    private @Nullable Object section(int x, int y, int z) {
        if (y < minY || y > maxY) return null;
        int i = (x >> 4) - minCx, j = (y >> 4) - minSy, k = (z >> 4) - minCz;
        if (i < 0 || k < 0 || i >= countX || k >= countZ) return null;
        return sections[(i * countZ + k) * countY + j];
    }

    /** The block there; air where unknown. */
    @SuppressWarnings("unchecked")
    BlockState state(int x, int y, int z) {
        Object section = section(x, y, z);
        if (section instanceof PalettedContainer<?> states) return ((PalettedContainer<BlockState>) states).get(x & 15, y & 15, z & 15);
        return AIR;
    }

    /** Whether the copy knows the block there: in the box, in a chunk the client had. */
    boolean loaded(int x, int y, int z) {
        return section(x, y, z) != null;
    }

    @Override
    public BlockState state(BlockPos at) {
        return state(at.getX(), at.getY(), at.getZ());
    }

    @Override
    public FluidState fluid(BlockPos at) {
        return state(at).getFluidState();
    }

    @Override
    public boolean loaded(BlockPos at) {
        return loaded(at.getX(), at.getY(), at.getZ());
    }

    @Override
    public VoxelShape collision(BlockState state, BlockPos at) {
        try {
            return state.getCollisionShape(EmptyBlockGetter.INSTANCE, at);
        } catch (RuntimeException e) {
            // A shape that wants the level: a full block, to be safe.
            return Shapes.block();
        }
    }

    /** As the game has it: a full block under more of the same fluid, its own height otherwise. */
    @Override
    public double fluidHeight(FluidState fluid, BlockPos at) {
        if (fluid.isEmpty()) return 0;
        return fluid.getType().isSame(fluid(at.above()).getType()) ? 1 : fluid.getOwnHeight();
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int top() {
        return maxY + 1;
    }
}
