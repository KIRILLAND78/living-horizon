package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Vehicles are told by what their class does, other mods' included, without their names. */
class VehiclesTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    /** Another mod's aircraft: steered by its pilot, as a boat is. */
    abstract static class Plane extends Entity {
        Plane(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        public LivingEntity getControllingPassenger() {
            return null;
        }
    }

    /** One model of that aircraft, which declares nothing of its own. */
    abstract static class Biplane extends Plane {
        Biplane(EntityType<?> type, Level level) {
            super(type, level);
        }
    }

    /** Another mod's tank, or a seat: places its riders, steered by nobody the game knows. */
    abstract static class Tank extends Entity {
        Tank(EntityType<?> type, Level level) {
            super(type, level);
        }

        @Override
        protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        }
    }

    @Test
    void boatsAreSteered() {
        //? if >=1.21.2 {
        assertEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class));
        //?} else
        /*assertEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(net.minecraft.world.entity.vehicle.Boat.class));*/
    }

    /** Nobody steers a minecart: never shown far away until chosen. */
    @Test
    void minecartsAreNotSteered() {
        //? if >=1.21.11 {
        assertNotEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(net.minecraft.world.entity.vehicle.minecart.Minecart.class));
        //?} else
        /*assertNotEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(net.minecraft.world.entity.vehicle.Minecart.class));*/
    }

    @Test
    void otherModsVehiclesAreFound() {
        assertEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(Plane.class));
        assertEquals(Vehicles.Kind.STEERED, Vehicles.kindOf(Biplane.class));
        assertEquals(Vehicles.Kind.SEATED, Vehicles.kindOf(Tank.class));
    }

    @Test
    void theRestIsNoVehicle() {
        assertEquals(Vehicles.Kind.NONE, Vehicles.kindOf(ItemEntity.class));
        assertEquals(Vehicles.Kind.NONE, Vehicles.kindOf(ItemFrame.class));
        // Alive: a mob, or a stand, never a vehicle, even the mounts that are steered.
        assertEquals(Vehicles.Kind.NONE, Vehicles.kindOf(ArmorStand.class));
        assertEquals(Vehicles.Kind.NONE, Vehicles.kindOf(net.minecraft.world.entity.Mob.class));
        assertEquals(Vehicles.Kind.NONE, Vehicles.kindOf(String.class));
    }
}
