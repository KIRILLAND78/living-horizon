package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Valkyrien Skies keeps the blocks of a ship, and the entities it carries as part of it (its
 * seats, the vehicles and contraptions on it, whoever sits in them), in its shipyard, an area
 * far from the world. Its renderer moves each of those entities onto its ship, from the camera,
 * whatever place it was handed. The mod took the shipyard for where they were: a model came out
 * nowhere and a picture floated in the wrong direction (issue #225). The ship is never drawn far
 * away either, so those entities are left to the game. Reached by reflection; any failure turns
 * this off.
 */
public final class ValkyrienSkies {
    private static @Nullable MethodHandle inShipyard;
    private static boolean looked;

    private ValkyrienSkies() {
    }

    /** Whether Valkyrien Skies carries this entity on a ship, itself or what it rides. */
    public static boolean onShip(Entity entity) {
        if (!looked) find();
        if (inShipyard == null) return false;
        if (inShipyard(entity)) return true;
        Entity vehicle = entity.getVehicle();
        return vehicle != null && inShipyard(vehicle);
    }

    private static boolean inShipyard(Entity entity) {
        MethodHandle handle = inShipyard;
        Level level = entity.level();
        if (handle == null || level == null) return false;
        try {
            return (boolean) handle.invokeExact(level, entity.getX(), entity.getY(), entity.getZ());
        } catch (Throwable error) {
            LivingHorizonClient.LOGGER.warn("Valkyrien Skies found, but not the version this mod knows", error);
            inShipyard = null;
            return false;
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("valkyrienskies")) return;
        try {
            Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            inShipyard = MethodHandles.publicLookup().findStatic(utils, "isBlockInShipyard",
                    MethodType.methodType(boolean.class, Level.class, double.class, double.class, double.class));
        } catch (ReflectiveOperationException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("Valkyrien Skies found, but not the version this mod knows", error);
        }
    }
}
