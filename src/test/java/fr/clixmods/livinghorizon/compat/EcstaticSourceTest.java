package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a column sits in Ecstatic's grid, and what it draws there. */
class EcstaticSourceTest {
    @Test
    void aColumnFallsInTheSampleWhoseCellHoldsIt() {
        assertArrayEquals(new int[]{0, 0, 0, 0}, EcstaticSource.cell(0, 0, 512, 2));
        assertArrayEquals(new int[]{0, 0, 255, 0}, EcstaticSource.cell(511, 1, 512, 2));
        // West and north of 0: the region before, counted from its own corner.
        assertArrayEquals(new int[]{-1, -1, 255, 240}, EcstaticSource.cell(-1, -32, 512, 2));
        assertArrayEquals(new int[]{-1, 2, 14, 0}, EcstaticSource.cell(-33, 1025, 512, 32));
    }

    @Test
    void groundUnderTheSeaIsWater() {
        LodWorld.Surface land = EcstaticSource.surface(70, "minecraft:plains");
        assertEquals(69, land.y());
        assertFalse(land.water());
        assertEquals("minecraft:plains", land.biome());

        LodWorld.Surface sea = EcstaticSource.surface(40, "minecraft:ocean");
        assertEquals(EcstaticSource.WATER_TOP - 1, sea.y());
        assertTrue(sea.water());

        // Ground up to the water's height: dry, as Ecstatic draws it.
        LodWorld.Surface shore = EcstaticSource.surface(EcstaticSource.WATER_TOP, "minecraft:beach");
        assertEquals(EcstaticSource.WATER_TOP - 1, shore.y());
        assertFalse(shore.water());
    }
}
