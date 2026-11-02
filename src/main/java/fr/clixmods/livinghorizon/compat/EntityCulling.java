package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Entity Culling (tr7zw) gives an entity a shorter tick while the game does not draw it: it
 * holds every entity out of view until the game's own draw says otherwise. That tick moves a
 * mob's head without its angle of the tick before, so a mob the mod draws in the game's
 * place had its head swing between an old angle and the new one every tick (issue #184).
 * The mod says so for the entities it draws, as the game's draw does. Reached by reflection,
 * so that the mod loads without it; any failure turns this off.
 */
public final class EntityCulling {
    private static @Nullable Class<?> cullable;
    private static @Nullable MethodHandle setOutOfCamera;
    private static boolean looked;

    private EntityCulling() {
    }

    /** The entity is drawn this frame: Entity Culling ticks it in full at the next tick. */
    public static void drawn(Entity entity) {
        if (!looked) find();
        MethodHandle handle = setOutOfCamera;
        Class<?> type = cullable;
        if (handle == null || type == null || !type.isInstance(entity)) return;
        try {
            handle.invoke(entity, false);
        } catch (Throwable error) {
            LivingHorizonClient.LOGGER.warn("Entity Culling found, but not the version this mod knows", error);
            setOutOfCamera = null;
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("entityculling")) return;
        try {
            cullable = Class.forName("dev.tr7zw.entityculling.access.Cullable");
            setOutOfCamera = MethodHandles.publicLookup().findVirtual(cullable, "setOutOfCamera",
                    MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("Entity Culling found, but not the version this mod knows", error);
        }
    }
}
