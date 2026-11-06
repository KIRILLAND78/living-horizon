package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Entity Model Features (EMF: Fresh Animations and its player extension) plays an entity's
 * animations less often the farther it stands, once in {@code distance / 20} frames by default
 * (its *Animation LOD*), keeping the last pose in between. Up close that is one frame in two or
 * three; the figures this mod draws stand hundreds of blocks away, one frame in 25 at 500
 * blocks: they slid along with their limbs still, then snapped to a new pose (issue #331).
 * {@code EmfLodMixin} asks {@link #animatesFigure} and plays them every frame, as up close.
 * Reached by reflection, so that the mod loads without it; any failure turns this off.
 */
public final class EntityModelFeatures {
    /** Where EMF says which entity it animates: from 3.3, then from 2.x to 3.2. */
    private static final String[][] CURRENT = {
            {"traben.entity_model_features.models.animation.state.EMFState", "emfEntity"},
            {"traben.entity_model_features.models.animation.EMFAnimationEntityContext", "getEMFEntity"},
    };

    /** The entities extracted for a figure this frame and the one before: render thread only. */
    private static Set<Entity> drawn = newSet(), before = newSet();
    private static @Nullable MethodHandle current;
    private static boolean looked, present;
    /** How many times EMF was told to play a figure: the {@code emf} game test reads it. */
    private static long played;

    private EntityModelFeatures() {
    }

    /** A new frame: the figures of two frames ago are forgotten. */
    public static void beginFrame() {
        if (drawn.isEmpty() && before.isEmpty()) return;
        Set<Entity> old = before;
        before = drawn;
        drawn = old;
        drawn.clear();
    }

    /** This entity is about to be extracted for a figure, or for an impostor's picture. */
    public static void drawn(Entity entity) {
        if (!looked) find();
        if (present) drawn.add(entity);
    }

    /**
     * Whether EMF is animating one of the mod's figures: then it plays it this frame rather
     * than keep its last pose. Iris's shadow pass keeps EMF's own choice, as the game's
     * entities do.
     */
    public static boolean animatesFigure() {
        MethodHandle handle = current;
        if (handle == null || (drawn.isEmpty() && before.isEmpty())) return false;
        try {
            Object entity = handle.invokeExact();
            if (!(entity instanceof Entity e) || !(drawn.contains(e) || before.contains(e))) return false;
        } catch (Throwable error) {
            LivingHorizonClient.LOGGER.warn("Entity Model Features found, but not the version this mod knows", error);
            current = null;
            return false;
        }
        if (IrisLink.shadowPass()) return false;
        played++;
        return true;
    }

    public static long played() {
        return played;
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("entity_model_features")) return;
        for (String[] where : CURRENT) {
            try {
                Class<?> owner = Class.forName(where[0]);
                current = MethodHandles.publicLookup().unreflect(owner.getMethod(where[1]))
                        .asType(MethodType.methodType(Object.class));
                present = true;
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Another version of EMF: the next place.
            }
        }
        LivingHorizonClient.LOGGER.warn("Entity Model Features found, but not the version this mod knows:"
                + " distant figures keep its animation level of detail");
    }

    private static Set<Entity> newSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
