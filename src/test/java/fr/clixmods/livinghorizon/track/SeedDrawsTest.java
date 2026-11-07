package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The draws of a chunk's first animals: the ids, the packing, and the game's generator built from a seed. */
class SeedDrawsTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void tellsAForeseenMobByItsIdAlone() {
        // The game gives its mobs random ids, version 4; those foreseen are name-based, version 3.
        for (int i = 0; i < 100; i++) assertFalse(SeedDraws.foreseen(UUID.randomUUID()));
        assertTrue(SeedDraws.foreseen(UUID.nameUUIDFromBytes("a cow".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void buildsTheGamesGeneratorFromASeedAndDrawsTheSameAnimalsEveryTime() {
        // What a client does with the seed a server shares: the game's overworld, built again.
        DimensionType overworld = SeedDraws.vanillaLookup().lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        SeedDraws.Source source = SeedDraws.vanilla("minecraft:overworld", 4242L, "minecraft:overworld",
                "minecraft:overworld", height, overworld, null);
        assertNotNull(source);
        assertNull(SeedDraws.vanilla("minecraft:overworld", 4242L, "somemod:islands", "minecraft:overworld", height, overworld, null));
        long[] chunks = new long[256];
        for (int i = 0; i < chunks.length; i++) chunks[i] = SeedDraws.chunk(i % 16 - 8, i / 16 - 8);
        Long2ObjectMap<List<PositionsMessage.Mob>> first = SeedMobs.draw(source, chunks, null);
        Long2ObjectMap<List<PositionsMessage.Mob>> again = SeedMobs.draw(source, chunks, null);
        assertFalse(first.isEmpty(), "no animal in 256 chunks of an overworld");
        assertEquals(first, again);
        for (List<PositionsMessage.Mob> mobs : first.values()) {
            for (PositionsMessage.Mob mob : mobs) {
                assertTrue(SeedDraws.foreseen(mob.id()));
                assertTrue(MobKinds.type(mob.kind()) != null);
                assertEquals("minecraft:overworld", mob.dimension());
            }
        }
    }

    @Test
    void packsAChunkAsTheGameDoes() {
        int[][] chunks = {{0, 0}, {1, -1}, {-30000, 29999}, {123456, -654321}};
        for (int[] chunk : chunks) {
            long packed = SeedDraws.chunk(chunk[0], chunk[1]);
            //? if >=26.1 {
            /*assertEquals(new ChunkPos(chunk[0], chunk[1]).pack(), packed);
            *///?} else
            assertEquals(ChunkPos.asLong(chunk[0], chunk[1]), packed);
            assertEquals(chunk[0], SeedDraws.chunkX(packed));
            assertEquals(chunk[1], SeedDraws.chunkZ(packed));
        }
    }
}
