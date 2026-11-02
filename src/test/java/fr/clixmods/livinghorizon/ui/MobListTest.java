package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.GameBoot;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.Vehicles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list of mobs is not empty, which it once was. */
class MobListTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void listsMobsAndOnlyMobs() {
        List<EntityType<?>> mobs = MobList.all();
        assertTrue(mobs.size() > 60, mobs.size() + " mobs");
        assertTrue(mobs.contains(EntityType.HORSE));
        assertTrue(mobs.contains(EntityType.ZOMBIE));
        //? if >=1.21.6
        assertTrue(mobs.contains(EntityType.HAPPY_GHAST));
        assertTrue(mobs.contains(EntityType.VILLAGER));
        assertFalse(mobs.contains(EntityType.PLAYER));
        assertFalse(mobs.contains(EntityType.ARMOR_STAND));
        //? if >=1.21.9
        assertTrue(mobs.contains(EntityType.MANNEQUIN));
        // One boat type per wood from 1.21.2.
        //? if >=1.21.2 {
        assertTrue(mobs.contains(EntityType.OAK_BOAT));
        //?} else
        /*assertTrue(mobs.contains(EntityType.BOAT));*/
        // The rideable minecart is a vehicle where its class places its rider; the others never.
        //? if >=1.21.11 {
        boolean seats = Vehicles.kindOf(net.minecraft.world.entity.vehicle.minecart.Minecart.class) != Vehicles.Kind.NONE;
        //?} else
        /*boolean seats = Vehicles.kindOf(net.minecraft.world.entity.vehicle.Minecart.class) != Vehicles.Kind.NONE;*/
        assertEquals(seats, mobs.contains(EntityType.MINECART));
        assertFalse(mobs.contains(EntityType.CHEST_MINECART));
        assertFalse(mobs.contains(EntityType.ITEM));
        // The kinds this game version has; the build names those it does not have yet.
        List<String> missing = List.of(System.getProperty("livinghorizon.missingTypes", "").split(","));
        for (String type : MobKinds.TYPES) {
            if (missing.contains(type)) continue;
            assertTrue(mobs.contains(BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(type)).orElseThrow()), type);
        }
    }
}
