package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Far View's terrain ({@link FarViewLink}): a height grid of what the client has seen, the top
 * block of each column and, under trees, the ground and where the leaves start. No biome, no
 * caves: enough for the ground a herd walks on, the hills that hide it, and whether there is
 * terrain under a mob at all.
 *
 * <p>Far View keeps its regions in a plain map that the game's thread fills. Reader threads never
 * touch it: every second, the game's thread copies the regions it holds into a map of our own
 * ({@link #tick()}), the region's arrays and not their content, which Far View writes in place.
 * A column read as Far View writes it may come back old or new, never wrong.
 */
final class FarViewSource implements LodSource {
    static final int REGION = 128;
    /** A column Far View has not seen. */
    static final short EMPTY = Short.MIN_VALUE;
    private static final int TICKS = 20;

    /** One region of the height grid, as Far View held it at the last copy. */
    record Region(short[] heights, int @Nullable [] blocks, short @Nullable [] ground,
                          int @Nullable [] groundBlocks, short @Nullable [] canopy) {
    }

    /** Replaced, never emptied, on another world: a read still running keeps the old one. */
    private volatile ConcurrentHashMap<Long, Region> regions = new ConcurrentHashMap<>();
    private volatile boolean drawing;
    private volatile double drawn, clear;
    private int clock;

    private volatile boolean broken;
    private volatile @Nullable String failure;
    /** After a failed read, nothing is read until then. */
    private volatile long pausedUntil;

    /** Columns asked for, and those with terrain. */
    private final AtomicInteger reads = new AtomicInteger(), found = new AtomicInteger();

    @Override
    public String name() {
        return "Far View";
    }

    @Override
    public boolean loaded() {
        return FarViewLink.installed();
    }

    @Override
    public boolean available() {
        if (broken || System.nanoTime() < pausedUntil) return false;
        if (!FarViewLink.linked()) {
            broken = true;
            failure = "Far View found, but not the version this mod knows";
            return false;
        }
        return true;
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    /**
     * On the game's thread, every tick: once a second, what Far View draws and how far, and a copy
     * of the regions it holds. A region is copied again only when Far View gave it new arrays.
     */
    @Override
    public void tick() {
        if (++clock % TICKS != 1 || !available()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean now = FarViewLink.drawing(minecraft);
        drawn = now ? FarViewLink.drawnDistance(minecraft) : 0;
        clear = now ? FarViewLink.clearDistance(minecraft) : 0;
        drawing = now;
        ConcurrentHashMap<Long, Region> held = regions;
        if (!now) {
            if (!held.isEmpty()) regions = new ConcurrentHashMap<>();
            return;
        }
        int[] count = {0};
        try {
            FarViewLink.regions((rx, rz, heights, blocks, ground, groundBlocks, canopy) -> {
                count[0]++;
                long key = key(rx, rz);
                Region known = held.get(key);
                if (known == null || known.heights != heights || known.blocks != blocks || known.ground != ground
                        || known.groundBlocks != groundBlocks || known.canopy != canopy) {
                    held.put(key, new Region(heights, blocks, ground, groundBlocks, canopy));
                }
            });
            // Far View let some go (far behind the player, or another dimension).
            if (held.size() > count[0]) {
                LongOpenHashSet live = new LongOpenHashSet(count[0]);
                FarViewLink.regions((rx, rz, heights, blocks, ground, groundBlocks, canopy) -> live.add(key(rx, rz)));
                held.keySet().removeIf(key -> !live.contains(key.longValue()));
            }
        } catch (Throwable e) {
            fail(e);
        }
    }

    @Override
    public void clear() {
        regions = new ConcurrentHashMap<>();
    }

    @Override
    public @Nullable Object open() {
        ConcurrentHashMap<Long, Region> held = regions;
        return drawing && !held.isEmpty() ? held : null;
    }

    @Override
    public double drawnDistance() {
        return drawn;
    }

    @Override
    public double clearDistance() {
        return clear;
    }

    @Override
    public String details() {
        return regions.size() + " regions of its grid held, " + reads.get() + " columns read, " + found.get() + " with terrain";
    }

    @Override
    public void fail(Throwable e) {
        pausedUntil = System.nanoTime() + 20_000_000_000L;
        failure = "Far View did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Reading Far View's terrain paused for 20 seconds: it did not answer as expected", e);
    }

    /** The region holding a column, and the column's place in it; null where Far View has none. */
    private static @Nullable Region region(Object world, int x, int z) {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, Region> held = (ConcurrentHashMap<Long, Region>) world;
        return held.get(key(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION)));
    }

    private static int index(int x, int z) {
        return Math.floorMod(z, REGION) * REGION + Math.floorMod(x, REGION);
    }

    /** A block from its id in the game's registry; null where there is none. */
    private static @Nullable BlockState block(int @Nullable [] ids, int i) {
        if (ids == null) return null;
        Block block = BuiltInRegistries.BLOCK.byId(ids[i]);
        return block == null || block == Blocks.AIR ? null : block.defaultBlockState();
    }

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) {
        reads.incrementAndGet();
        Region region = region(world, x, z);
        if (region == null) return null;
        int i = index(x, z);
        short top = region.heights[i];
        if (top == EMPTY) return null;
        found.incrementAndGet();
        BlockState block = block(region.blocks, i);
        boolean water = block != null && block.getFluidState().getType().isSame(Fluids.WATER);
        return new LodWorld.Surface(top, "", water, block);
    }

    /**
     * The column's blocks. Under trees Far View knows the ground and where the leaves start: the
     * leaves from there up, air under them (a log where its trunk stands), the ground under that,
     * so that a herd walks under the trees rather than on them. Elsewhere the top block alone.
     */
    @Override
    public BlockState @Nullable [] slice(Object world, int x, int z, int bottom, int height) {
        Region region = region(world, x, z);
        if (region == null) return null;
        int i = index(x, z);
        short top = region.heights[i];
        if (top == EMPTY) return null;
        BlockState topBlock = block(region.blocks, i);
        if (topBlock == null) topBlock = Blocks.STONE.defaultBlockState();
        int ground = region.ground == null ? EMPTY : region.ground[i];
        int canopy = region.canopy == null ? EMPTY : region.canopy[i];
        boolean underTrees = ground != EMPTY && canopy != EMPTY && ground < top && canopy > ground && canopy <= top;
        BlockState trunk = underTrees ? block(region.groundBlocks, i) : null;
        // Far View puts a trunk's log there, found by its tag. Any pillar block is taken for one:
        // the tags are only bound once a world's data is in.
        if (trunk != null && !trunk.is(BlockTags.LOGS) && !(trunk.getBlock() instanceof RotatedPillarBlock)) trunk = null;
        BlockState groundBlock = underTrees && trunk == null ? block(region.groundBlocks, i) : null;
        if (groundBlock == null) groundBlock = Blocks.DIRT.defaultBlockState();
        BlockState[] slice = new BlockState[height];
        for (int k = 0; k < height; k++) {
            int y = bottom + k;
            BlockState state;
            if (y > top) state = Blocks.AIR.defaultBlockState();
            else if (!underTrees) state = y == top ? topBlock : Blocks.STONE.defaultBlockState();
            else if (y >= canopy) state = topBlock;
            else if (y > ground) state = trunk != null ? trunk : Blocks.AIR.defaultBlockState();
            else state = y == ground ? groundBlock : Blocks.STONE.defaultBlockState();
            slice[k] = state;
        }
        return slice;
    }

    /**
     * Walks the ray a block at a time near the eye and in longer steps farther away, and stops
     * where it goes under the top of a column (a tree's leaves included). A column Far View has
     * not seen is walked through.
     */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                           double from, double to) {
        long column = Long.MIN_VALUE;
        int height = Integer.MIN_VALUE;
        for (double t = from; t < to; t += Math.max(1.0, Math.min(8.0, t / 48.0))) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (key != column) {
                column = key;
                Region region = region(world, x, z);
                short top = region == null ? EMPTY : region.heights[index(x, z)];
                height = top == EMPTY ? Integer.MIN_VALUE : top;
            }
            if (y <= height) return t;
        }
        return Double.NaN;
    }

    static long key(int rx, int rz) {
        return (long) rx << 32 | (rz & 0xFFFFFFFFL);
    }
}
