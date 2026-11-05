package fr.clixmods.livinghorizon.compat;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A mod that keeps the world past the render distance, read through {@link LodWorld}:
 * Voxy, Distant Horizons, Lodistance, Far View or Ecstatic. None is required, and none is
 * linked against: each source reaches its mod at runtime, so the mod loads with any of them or none.
 *
 * <p>Every method but the first three and {@link #tick()} runs on a reader thread, never on
 * the game's.
 */
interface LodSource {
    String name();

    /** The mod is installed. */
    boolean loaded();

    /** The mod is installed and its classes are the ones this source knows. */
    boolean available();

    /** Why this source turned itself off, or null. */
    @Nullable String failure();

    /**
     * Another world: whatever readers hold of the old one is let go, and what a read still
     * running gets from it is not kept. Called on the game's thread, with no read running only
     * for a source that {@link #keepsBetweenReads() keeps what it reads}.
     */
    void clear();

    /**
     * Whether readers keep what they read from one read to the next (Voxy's sections), which
     * only {@link #clear()} lets go of, with no read running: {@link LodWorld} waits for the
     * reads to end before it clears. The reads of a source that says no are never waited for.
     */
    default boolean keepsBetweenReads() {
        return false;
    }

    /**
     * No world any more, after {@link #clear()}: reads still waiting on the mod are called off,
     * since it may never answer them. On the game's thread.
     */
    default void cancel() {
    }

    /** Every client tick with a level, on the game's thread, for a source installed. */
    default void tick() {
    }

    /** The world to read on this thread, or null while the mod has none loaded. */
    @Nullable Object open() throws Throwable;

    /**
     * Whether the mod holds terrain anywhere in the chunk of the block {@code (x, z)}, known
     * without reading its blocks; null where it cannot tell that way, and the column is read.
     */
    default @Nullable Boolean holds(Object world, int x, int z) throws Throwable {
        return null;
    }

    /** The highest block of a column and its biome; null where the mod knows nothing. */
    LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable;

    /**
     * The blocks of a column from {@code bottom} up, {@code height} of them, air where the
     * mod has nothing; null where it knows nothing of the column. By default only the top
     * block is known: that block, solid ground under it, air over it.
     */
    default BlockState @Nullable [] slice(Object world, int x, int z, int bottom, int height) throws Throwable {
        LodWorld.Surface surface;
        try {
            surface = column(world, x, z);
        } catch (LodWorld.Unanswered unanswered) {
            // Not answered: nothing known, never a failure that pauses every read.
            return null;
        }
        if (surface == null) return null;
        BlockState top = surface.block() != null ? surface.block()
                : surface.water() ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
        BlockState[] slice = new BlockState[height];
        for (int k = 0; k < height; k++) {
            int y = bottom + k;
            slice[k] = y > surface.y() ? Blocks.AIR.defaultBlockState() : y == surface.y() ? top : Blocks.STONE.defaultBlockState();
        }
        return slice;
    }

    /** Distance to the first opaque block along a unit direction, between two distances; NaN if none. */
    double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                    double from, double to) throws Throwable;

    /**
     * Blocks across to which the mod draws its terrain and its far fog still leaves it clear,
     * read from its settings on the game's thread; 0 when it draws nothing or cannot say.
     */
    default double clearDistance() {
        return 0;
    }

    /**
     * Blocks across to which the mod draws its terrain at all, fog or not, read from its
     * settings on the game's thread; 0 when it draws nothing or cannot say.
     */
    default double drawnDistance() {
        return 0;
    }

    /** What the reads found so far, for {@code /livinghorizon lod}; empty when there is nothing to say. */
    default String details() {
        return "";
    }

    /** The mod did not answer as expected: this source is off for the session. */
    void fail(Throwable e);
}
