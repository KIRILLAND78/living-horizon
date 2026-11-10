package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Entity Texture Features (ETF) makes the state an entity is drawn with, and mounts it, in
 * {@code EntityRenderDispatcher.render}; Entity Model Features (EMF) reads its animation values
 * from it ({@code limb_speed}, {@code is_on_ground}...). Before 1.21.2 the mod draws its figures
 * through the entity's renderer, not the dispatcher, so nothing was mounted: EMF read a still
 * figure in the air, and a resource pack that walks the legs itself kept them still while the
 * game's arms swung. The mod mounts the state around its own draw, as the dispatcher does.
 * Reached by reflection, so that the mod loads without it; any failure turns this off.
 */
public final class EntityTextureFeatures {
    private static @Nullable Class<?> etfEntity;
    private static @Nullable Method forEntity;
    private static @Nullable Method mount;
    private static @Nullable Method unMount;
    private static boolean looked;

    private EntityTextureFeatures() {
    }

    /** Mounts ETF's state for {@code entity}; true when {@link #unmount} must follow. */
    public static boolean mount(Entity entity) {
        if (!looked) find();
        Method make = forEntity, push = mount;
        Class<?> type = etfEntity;
        if (make == null || push == null || type == null || !type.isInstance(entity)) return false;
        try {
            push.invoke(null, make.invoke(null, entity));
            return true;
        } catch (Throwable error) {
            fail(error);
            return false;
        }
    }

    /** Takes off the state {@link #mount} put on. */
    public static void unmount() {
        Method pop = unMount;
        if (pop == null) return;
        try {
            pop.invoke(null);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("entity_texture_features")) return;
        try {
            etfEntity = Class.forName("traben.entity_texture_features.utils.ETFEntity");
            Class<?> state = Class.forName("traben.entity_texture_features.features.state.ETFEntityRenderState");
            Class<?> stack = Class.forName("traben.entity_texture_features.features.state.ETFState");
            forEntity = state.getMethod("forEntity", etfEntity);
            mount = stack.getMethod("mount", state);
            unMount = stack.getMethod("unMount");
        } catch (ReflectiveOperationException | LinkageError error) {
            fail(error);
        }
    }

    private static void fail(Throwable error) {
        LivingHorizonClient.LOGGER.warn("Entity Texture Features found, but not the version this mod knows", error);
        forEntity = null;
        mount = null;
        unMount = null;
    }
}
