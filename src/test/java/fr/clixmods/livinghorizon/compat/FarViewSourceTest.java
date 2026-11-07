package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Far View's height grid, as {@link FarViewSource} reads it: a copy made by hand, no Far View. */
class FarViewSourceTest {
    private static final int N = FarViewSource.REGION * FarViewSource.REGION;
    private final FarViewSource source = new FarViewSource();

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    private static int id(Block block) {
        return BuiltInRegistries.BLOCK.getId(block);
    }

    /** One region, every column at {@code height} of {@code block}, nothing under trees. */
    private static FarViewSource.Region flat(int height, Block block) {
        short[] heights = new short[N], ground = new short[N], canopy = new short[N];
        int[] blocks = new int[N], groundBlocks = new int[N];
        Arrays.fill(heights, (short) height);
        Arrays.fill(ground, (short) height);
        Arrays.fill(canopy, (short) height);
        Arrays.fill(blocks, id(block));
        Arrays.fill(groundBlocks, id(block));
        return new FarViewSource.Region(heights, blocks, ground, groundBlocks, canopy);
    }

    private static int index(int x, int z) {
        return Math.floorMod(z, FarViewSource.REGION) * FarViewSource.REGION + Math.floorMod(x, FarViewSource.REGION);
    }

    private static ConcurrentHashMap<Long, FarViewSource.Region> world(int rx, int rz, FarViewSource.Region region) {
        ConcurrentHashMap<Long, FarViewSource.Region> world = new ConcurrentHashMap<>();
        world.put(FarViewSource.key(rx, rz), region);
        return world;
    }

    @Test
    void readsTheColumnOfItsRegionOnBothSidesOfZero() {
        FarViewSource.Region region = flat(70, Blocks.GRASS_BLOCK);
        region.heights()[index(-1, -130)] = 90;
        region.blocks()[index(-1, -130)] = id(Blocks.WATER);
        // Column (-1, -130) is the last x and the second-to-last z row of region (-1, -2).
        LodWorld.Surface surface = source.column(world(-1, -2, region), -1, -130);
        assertNotNull(surface);
        assertEquals(90, surface.y());
        assertTrue(surface.water());
        LodWorld.Surface next = source.column(world(-1, -2, region), -2, -130);
        assertNotNull(next);
        assertEquals(70, next.y());
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), next.block());
        assertFalse(next.water());
    }

    @Test
    void nothingWhereItHasNotSeenTheColumnOrHasNoRegion() {
        FarViewSource.Region region = flat(70, Blocks.GRASS_BLOCK);
        region.heights()[index(5, 6)] = FarViewSource.EMPTY;
        assertNull(source.column(world(0, 0, region), 5, 6));
        assertNull(source.slice(world(0, 0, region), 5, 6, 60, 20));
        assertNull(source.column(world(0, 0, region), 200, 6));
    }

    @Test
    void groundUnderTreesWithLeavesAboveAndATrunkWhereOneStands() {
        FarViewSource.Region region = flat(64, Blocks.GRASS_BLOCK);
        // Leaves from 70 to 74 over grass at 64; one column with an oak trunk.
        for (int x : new int[]{3, 4}) {
            int i = index(x, 3);
            region.heights()[i] = 74;
            region.blocks()[i] = id(Blocks.OAK_LEAVES);
            region.ground()[i] = 64;
            region.canopy()[i] = 70;
        }
        region.groundBlocks()[index(3, 3)] = id(Blocks.GRASS_BLOCK);
        region.groundBlocks()[index(4, 3)] = id(Blocks.OAK_LOG);
        BlockState[] open = source.slice(world(0, 0, region), 3, 3, 60, 20);
        assertNotNull(open);
        assertEquals(Blocks.STONE.defaultBlockState(), open[63 - 60]);
        assertEquals(Blocks.GRASS_BLOCK.defaultBlockState(), open[64 - 60]);
        assertTrue(open[65 - 60].isAir(), "room to walk under the leaves");
        assertTrue(open[69 - 60].isAir());
        assertEquals(Blocks.OAK_LEAVES.defaultBlockState(), open[70 - 60]);
        assertEquals(Blocks.OAK_LEAVES.defaultBlockState(), open[74 - 60]);
        assertTrue(open[75 - 60].isAir());
        BlockState[] trunk = source.slice(world(0, 0, region), 4, 3, 60, 20);
        assertNotNull(trunk);
        assertEquals(Blocks.DIRT.defaultBlockState(), trunk[64 - 60], "the ground under a trunk is not known");
        assertEquals(Blocks.OAK_LOG.defaultBlockState(), trunk[65 - 60]);
        assertEquals(Blocks.OAK_LEAVES.defaultBlockState(), trunk[72 - 60]);
    }

    @Test
    void theTopBlockAloneElsewhere() {
        BlockState[] slice = source.slice(world(0, 0, flat(64, Blocks.SAND)), 10, 10, 62, 4);
        assertNotNull(slice);
        assertEquals(Blocks.STONE.defaultBlockState(), slice[0]);
        assertEquals(Blocks.STONE.defaultBlockState(), slice[1]);
        assertEquals(Blocks.SAND.defaultBlockState(), slice[2]);
        assertTrue(slice[3].isAir());
    }

    @Test
    void aHillStopsTheEyeAndPlainGroundDoesNot() {
        FarViewSource.Region region = flat(64, Blocks.GRASS_BLOCK);
        // A wall across x = 60, up to 80.
        for (int z = 0; z < FarViewSource.REGION; z++) region.heights()[index(60, z)] = 80;
        ConcurrentHashMap<Long, FarViewSource.Region> world = world(0, 0, region);
        double hit = source.firstHit(world, 10.5, 70.5, 50.5, 1, 0, 0, 0.5, 100);
        assertEquals(50, hit, 1.0);
        assertTrue(Double.isNaN(source.firstHit(world, 10.5, 90.5, 50.5, 1, 0, 0, 0.5, 100)), "over the wall");
        assertTrue(Double.isNaN(source.firstHit(world, 10.5, 70.5, 50.5, 1, 0, 0, 0.5, 40)), "short of the wall");
    }
}
