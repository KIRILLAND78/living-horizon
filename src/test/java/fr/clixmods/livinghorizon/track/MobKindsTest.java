package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers of {@link MobKinds} are saved in the servers' files and sent to clients of other
 * versions: a kind is only ever added at the end. Add a new one at the end of {@link #NUMBERED}
 * too; never change what is already there.
 */
class MobKindsTest {
    /** Kind 1 first, as released. */
    private static final List<String> NUMBERED = List.of(
            "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama", "minecraft:camel",
            "minecraft:cow", "minecraft:mooshroom", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:goat", "minecraft:cat", "minecraft:wolf", "minecraft:fox",
            "minecraft:parrot", "minecraft:panda", "minecraft:polar_bear", "minecraft:turtle", "minecraft:axolotl",
            "minecraft:frog", "minecraft:armadillo", "minecraft:sniffer", "minecraft:bee", "minecraft:ocelot",
            "minecraft:happy_ghast", "minecraft:strider", "minecraft:villager", "minecraft:iron_golem",
            "minecraft:snow_golem", "minecraft:allay",
            "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat", "minecraft:jungle_boat",
            "minecraft:acacia_boat", "minecraft:cherry_boat", "minecraft:dark_oak_boat", "minecraft:pale_oak_boat",
            "minecraft:mangrove_boat", "minecraft:bamboo_raft",
            "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat", "minecraft:birch_chest_boat",
            "minecraft:jungle_chest_boat", "minecraft:acacia_chest_boat", "minecraft:cherry_chest_boat",
            "minecraft:dark_oak_chest_boat", "minecraft:pale_oak_chest_boat", "minecraft:mangrove_chest_boat",
            "minecraft:bamboo_chest_raft");

    @Test
    void neverRenumbers() {
        assertTrue(MobKinds.TYPES.size() >= NUMBERED.size(), "a kind was removed");
        for (int i = 0; i < NUMBERED.size(); i++) {
            assertEquals(NUMBERED.get(i), MobKinds.type(i + 1), "kind " + (i + 1) + " changed");
        }
        assertEquals(NUMBERED, MobKinds.TYPES, "a kind was added: add it at the end of NUMBERED too");
    }

    @Test
    void numbersFromOne() {
        assertNull(MobKinds.type(0));
        assertNull(MobKinds.type(MobKinds.TYPES.size() + 1));
    }

    @Test
    void numbersATypeTheWayBack() {
        for (int kind = 1; kind <= MobKinds.TYPES.size(); kind++) assertEquals(kind, MobKinds.kind(MobKinds.type(kind)));
        assertEquals(0, MobKinds.kind("minecraft:zombie"));
    }
}
