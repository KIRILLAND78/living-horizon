package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Ambient Birds (Cmetanochkaa) adds birds that this client alone sees: they perch round the
 * player and fly off in flocks. Far away the mod draws a copy of one, made from its saved data,
 * which keeps none of its flight: it would be drawn perched, and invisible, since a bird fades
 * in over its first ticks. The copy is told it flies past, as Ambient Birds tells its flocks,
 * so that it is drawn wings out, and made old enough to be seen. Reached by reflection, so
 * that the mod loads without it; any failure turns this off.
 */
public final class AmbientBirds {
    /** Ticks a bird of Ambient Birds takes to fade in, counted from when it was made. */
    private static final int FADE_IN = 30;

    private static @Nullable Class<?> bird;
    private static @Nullable MethodHandle markFlyby;
    private static boolean looked;

    private AmbientBirds() {
    }

    /** Makes the copy of a bird of Ambient Birds fly, facing the way it faces, and fully seen. */
    public static void flying(Entity copy) {
        if (!looked) find();
        MethodHandle handle = markFlyby;
        Class<?> type = bird;
        if (handle == null || type == null || !type.isInstance(copy)) return;
        try {
            double yaw = Math.toRadians(copy.getYRot());
            handle.invoke(copy, new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)));
            copy.tickCount = Math.max(copy.tickCount, FADE_IN);
        } catch (Throwable error) {
            LivingHorizonClient.LOGGER.warn("Ambient Birds found, but not the version this mod knows", error);
            markFlyby = null;
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("ambientbirds")) return;
        try {
            bird = Class.forName("com.shareitt.ambientbirds.entity.BirdEntity");
            markFlyby = MethodHandles.publicLookup().findVirtual(bird, "markFlyby",
                    MethodType.methodType(void.class, Vec3.class));
        } catch (ReflectiveOperationException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("Ambient Birds found, but not the version this mod knows", error);
        }
    }
}
