package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lodistance's world ({@link LodistanceLink}): for each column it keeps up to three runs of
 * solid blocks, top and bottom, the block at the top of each, and where water stands over them,
 * in regions of 512 by 512 blocks. Only the regions it holds in memory are read, never one
 * loaded from its files for this: those are the ones around the player, where it draws.
 *
 * <p>Its arrays are read without its locks: a column read while Lodistance changes it is a
 * column a moment old, which the next read puts right. Nothing of it is ever written. It has no
 * biomes, only colours: the biome is left empty.
 */
final class LodistanceSource implements LodSource {
    private static final int REGION = 512, RUNS = 3;

    private volatile boolean broken;
    private volatile @Nullable String failure;
    /** Lodistance's block palette, id to state, until the next {@link #clear()}: its ids are its world's. */
    private volatile ConcurrentHashMap<Integer, BlockState> blocks = new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger(), found = new AtomicInteger();

    @Override
    public String name() {
        return "Lodistance";
    }

    @Override
    public boolean loaded() {
        return Platform.isModLoaded("lodistance");
    }

    @Override
    public boolean available() {
        if (broken) return false;
        if (!LodistanceLink.linked()) {
            failure = "not the version this mod knows";
            return false;
        }
        return true;
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    @Override
    public void clear() {
        blocks = new ConcurrentHashMap<>();
    }

    @Override
    public @Nullable Object open() {
        return LodistanceLink.world();
    }

    /** One column of a region held in memory; null where Lodistance knows nothing of it. */
    private record Column(int runs, short[] top, short[] bottom, short[] block, int at, short water) {
        int highest() {
            return runs > 0 ? top[at] : Integer.MIN_VALUE;
        }
    }

    private @Nullable Column read(Object world, int x, int z) throws Throwable {
        reads.incrementAndGet();
        long key = (long) Math.floorDiv(x, REGION) << 32 | (Math.floorDiv(z, REGION) & 0xFFFFFFFFL);
        Object slot = LodistanceLink.slots(world).get(key);
        if (slot == null) return null;
        Object region = LodistanceLink.slotData.get(slot);
        if (region == null) return null;
        int lx = Math.floorMod(x, REGION), lz = Math.floorMod(z, REGION);
        boolean[] known = (boolean[]) LodistanceLink.chunkKnown.get(region);
        if (!known[(lz >> 4) * 32 + (lx >> 4)]) return null;
        int col = lz * REGION + lx;
        byte runs = ((byte[]) LodistanceLink.count.get(region))[col];
        short water = ((short[]) LodistanceLink.waterTop.get(region))[col];
        if (runs <= 0 && water == LodistanceLink.empty) return null;
        found.incrementAndGet();
        return new Column(Math.min(runs, RUNS), (short[]) LodistanceLink.top.get(region),
                (short[]) LodistanceLink.bottom.get(region), (short[]) LodistanceLink.block.get(region), col * RUNS, water);
    }

    /** The block of a palette id, or stone when its name is not a block here. */
    private BlockState block(Object world, int id) {
        ConcurrentHashMap<Integer, BlockState> known = blocks;
        BlockState state = known.get(id);
        if (state != null) return state;
        state = Blocks.STONE.defaultBlockState();
        try {
            Object name = LodistanceLink.paletteName.invoke(LodistanceLink.palette.get(world), id);
            Identifier parsed = name == null ? null : Identifier.tryParse(String.valueOf(name));
            if (parsed != null) {
                //? if >=1.21.2 {
                Block found = BuiltInRegistries.BLOCK.getValue(parsed);
                //?} else {
                /*Block found = BuiltInRegistries.BLOCK.get(parsed);
                *///?}
                if (found != Blocks.AIR) state = found.defaultBlockState();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not a block this game knows: stone, which walks and hides the same.
        }
        known.put(id, state);
        return state;
    }

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable {
        Column column = read(world, x, z);
        if (column == null) return null;
        // Its tops are the block above the run: the highest block is one below.
        int ground = column.runs > 0 ? column.highest() - 1 : Integer.MIN_VALUE;
        boolean water = column.water != LodistanceLink.empty && column.water - 1 > ground;
        if (water) return new LodWorld.Surface(column.water - 1, "", true, Blocks.WATER.defaultBlockState());
        return new LodWorld.Surface(ground, "", false, block(world, column.block[column.at]));
    }

    @Override
    public BlockState @Nullable [] slice(Object world, int x, int z, int bottom, int height) throws Throwable {
        Column column = read(world, x, z);
        if (column == null) return null;
        BlockState air = Blocks.AIR.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        BlockState[] slice = new BlockState[height];
        java.util.Arrays.fill(slice, air);
        if (column.water != LodistanceLink.empty) {
            int from = column.runs > 0 ? column.highest() : bottom;
            for (int y = Math.max(from, bottom); y < Math.min(column.water, bottom + height); y++) {
                slice[y - bottom] = Blocks.WATER.defaultBlockState();
            }
        }
        for (int k = 0; k < column.runs; k++) {
            int i = column.at + k, runTop = column.top[i], runBottom = column.bottom[i];
            if (runTop == LodistanceLink.empty) break;
            BlockState first = block(world, column.block[i]);
            for (int y = Math.max(runBottom, bottom); y < Math.min(runTop, bottom + height); y++) {
                slice[y - bottom] = y == runTop - 1 ? first : stone;
            }
        }
        return slice;
    }

    /**
     * Walks the ray a block at a time near the eye and in longer steps farther away, and stops
     * inside one of a column's runs of blocks: the overhangs and caves it keeps count.
     */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                           double from, double to) throws Throwable {
        long last = Long.MIN_VALUE;
        Column column = null;
        for (double t = from; t < to; t += Math.clamp(t / 48.0, 1.0, 8.0)) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (key != last) {
                last = key;
                column = read(world, x, z);
            }
            if (column == null) continue;
            for (int k = 0; k < column.runs; k++) {
                int i = column.at + k;
                if (column.top[i] == LodistanceLink.empty) break;
                if (y < column.top[i] && y >= column.bottom[i]) return t;
            }
        }
        return Double.NaN;
    }

    /** Its distance, as far as its fog is still thin: a little way past where it starts. */
    @Override
    public double clearDistance() {
        double drawn = LodistanceLink.drawnDistance();
        double start = Math.clamp(LodistanceLink.fogStart(), 0.0, 1.0);
        return drawn * Math.max(0.1, Math.min(0.9, start + (1 - start) * 0.2));
    }

    @Override
    public double drawnDistance() {
        return LodistanceLink.drawnDistance();
    }

    @Override
    public String details() {
        return reads.get() + " columns read, " + found.get() + " with blocks";
    }

    @Override
    public void fail(Throwable e) {
        if (broken) return;
        broken = true;
        failure = String.valueOf(e);
        LivingHorizonClient.LOGGER.warn("Reading Lodistance's world failed, turned off for this session", e);
    }
}
