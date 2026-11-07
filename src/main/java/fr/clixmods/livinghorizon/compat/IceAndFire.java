package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Ice and Fire's flying mobs: its dragons, amphitheres and hippogryphs, which all share its
 * {@code IFlyingMount}. Seen from below as they fly over, or from above by a player riding one,
 * they are pictured from several heights, as a phantom or an aircraft is. Reached by name, so
 * that the mod loads without Ice and Fire.
 */
public final class IceAndFire {
    private static final String FLYING_MOUNT = "com.github.alexthe666.iceandfire.entity.util.IFlyingMount";

    private static @Nullable Class<?> flyingMount;
    private static boolean looked;

    private IceAndFire() {
    }

    /** Whether the entity is one of Ice and Fire's flying mobs. */
    public static boolean flies(Entity entity) {
        if (!looked) find();
        Class<?> type = flyingMount;
        return type != null && type.isInstance(entity);
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("iceandfire")) return;
        flyingMount = Links.optionalClass(FLYING_MOUNT);
        if (flyingMount == null) {
            LivingHorizonClient.LOGGER.warn("Ice and Fire found, but not the version this mod knows: its dragons are"
                    + " pictured from the side only");
        }
    }
}
