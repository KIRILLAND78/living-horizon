package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * Lycanites Mobs (Forge 1.20.1) does not draw the opaque parts of its OBJ models when its
 * renderer is called: it queues them and draws the queue at Forge's "after entities" stage,
 * with the matrices in use then. A figure the mod draws went wrong twice (issue #225): its
 * picture was baked empty, the queued parts landing in the world later, at the place the bake
 * had put them; and a far model was drawn once the far terrain's depth was taken back, so it
 * showed through it. The mod draws the queue itself right after its own figures, where the
 * picture or the depth they need is still in place. Reached by reflection; any failure turns
 * this off.
 */
public final class LycaniteMobs {
    private static final String[] BATCHERS = {
            "com.lycanitesmobs.client.renderer.util.VBOBatcher",
            "com.lycanitesmobs.client.renderer.util.IrisStaticVboBatcher",
    };

    /** Each batcher's {@code endDeferredBatches}, bound to its instance. */
    private static List<MethodHandle> flushes = List.of();
    private static boolean looked;

    private LycaniteMobs() {
    }

    /** Draws what Lycanites Mobs queued for later, now, with the target and matrices in use. */
    public static void drawQueued() {
        if (!looked) find();
        if (flushes.isEmpty()) return;
        try {
            for (MethodHandle flush : flushes) flush.invoke();
        } catch (Throwable error) {
            LivingHorizonClient.LOGGER.warn("Lycanites Mobs found, but not the version this mod knows", error);
            flushes = List.of();
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("lycanitesmobs")) return;
        List<MethodHandle> found = new ArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String name : BATCHERS) {
            try {
                Class<?> batcher = Class.forName(name);
                Object instance = lookup.findStatic(batcher, "getInstance", MethodType.methodType(batcher)).invoke();
                found.add(lookup.findVirtual(batcher, "endDeferredBatches", MethodType.methodType(void.class))
                        .bindTo(instance));
            } catch (Throwable error) {
                // Older versions have no Iris batcher; one without any queue has nothing to draw.
                LivingHorizonClient.LOGGER.debug("Lycanites Mobs: no {}", name, error);
            }
        }
        flushes = List.copyOf(found);
    }
}
