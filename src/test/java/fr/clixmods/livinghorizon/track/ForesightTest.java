package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file the foreseen mobs are kept in: what is read back, and how small it is. */
class ForesightTest {
    private static final long SEED = 4242L, WORLD = 0x1234L;
    private static final String OVERWORLD = "minecraft:overworld";

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void anIdKeepsItsPlaceAmongTheChunksDraws() {
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < 40; index++) {
            UUID id = SeedDraws.id(SEED, OVERWORLD, 3, -7, index);
            assertTrue(SeedDraws.foreseen(id));
            assertEquals(index, SeedDraws.index(id));
            assertEquals(id, SeedDraws.id(SEED, OVERWORLD, 3, -7, index));
            ids.add(id);
            ids.add(SeedDraws.id(SEED, OVERWORLD, 4, -7, index));
            ids.add(SeedDraws.id(SEED, "minecraft:the_nether", 3, -7, index));
        }
        assertEquals(120, ids.size());
    }

    @Test
    void readsBackWhatItWroteInAFewBytesAMob() throws IOException {
        DimensionType overworld = SeedDraws.vanillaLookup().lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        SeedDraws.Source source = SeedDraws.vanilla(OVERWORLD, SEED, OVERWORLD, OVERWORLD,
                LevelHeightAccessor.create(-64, 384), overworld, null);
        assertNotNull(source);
        long[] chunks = new long[1024];
        for (int i = 0; i < chunks.length; i++) chunks[i] = SeedDraws.chunk(i % 32 - 16, i / 32 - 16);
        Long2ObjectMap<List<PositionsMessage.Mob>> found = SeedMobs.draw(source, chunks, null);
        Long2ObjectOpenHashMap<long[]> index = new Long2ObjectOpenHashMap<>();
        int mobs = 0;
        for (Long2ObjectMap.Entry<List<PositionsMessage.Mob>> entry : found.long2ObjectEntrySet()) {
            index.put(entry.getLongKey(), Foresight.pack(entry.getValue()));
            mobs += entry.getValue().size();
        }
        Foresight.Worked worked = new Foresight.Worked();
        for (long chunk : chunks) worked.mark(chunk);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Foresight.write(out, new Foresight.Saved(SEED, worked, index), WORLD, OVERWORLD);
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length < mobs * 16 + 4096, bytes.length + " bytes for " + mobs + " mobs");
        System.out.println("foresight file: " + bytes.length + " bytes for " + mobs + " mobs in " + found.size()
                + " of " + chunks.length + " chunks");

        Foresight.Saved read = Foresight.read(new ByteArrayInputStream(bytes), WORLD, OVERWORLD);
        assertNotNull(read);
        assertEquals(SEED, read.seed());
        for (long chunk : chunks) assertTrue(read.worked().contains(chunk));
        assertEquals(index.keySet(), read.index().keySet());
        for (Long2ObjectMap.Entry<long[]> entry : index.long2ObjectEntrySet()) {
            long[] was = entry.getValue(), is = read.index().get(entry.getLongKey());
            assertEquals(was.length, is.length);
            for (int at = 0; at < was.length; at += 4) {
                // The id, its height, kind, variant and age exactly; where it stands to a 256th of a block.
                assertArrayEquals(new long[]{was[at], was[at + 1], was[at + 3]}, new long[]{is[at], is[at + 1], is[at + 3]});
                assertEquals(Float.intBitsToFloat((int) (was[at + 2] >>> 32)), Float.intBitsToFloat((int) (is[at + 2] >>> 32)), 1 / 256.0);
                assertEquals(Float.intBitsToFloat((int) was[at + 2]), Float.intBitsToFloat((int) is[at + 2]), 1 / 256.0);
            }
        }
        // Another world's or dimension's file is not read as this one's.
        assertNull(Foresight.read(new ByteArrayInputStream(bytes), WORLD + 1, OVERWORLD));
        assertNull(Foresight.read(new ByteArrayInputStream(bytes), WORLD, "minecraft:the_nether"));
    }
}
