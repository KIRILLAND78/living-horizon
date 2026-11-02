package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarViewLink;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a distant mob stands behind terrain, for {@link FarConfig#hideOccludedMobs}.
 *
 * <p>The GPU answers ({@link OcclusionQueries}): a mob is hidden when not one pixel of its
 * box passes the depth test against the terrain drawn in front of it. Without a recent
 * answer, the mob is shown.
 *
 * <p>With {@link FarConfig#optOcclusionQueries} off, once the queries failed, or with Vulkan, the far terrain's world
 * answers instead. A frame only reads the last answer. Twice a second, the mobs the frames
 * asked about are tested in one go on the reader threads: a line from the eye to the middle
 * of the mob and one to its top. Hidden means both meet a block before the mob.
 */
public final class Occlusion {
    /** Ticks between two tests. */
    private static final int EVERY = 10;

    private static final Map<Object, Boolean> HIDDEN = new ConcurrentHashMap<>();
    /**
     * Asked about: where, how tall, and the round of tests it was last asked in. An entry is
     * written over while its mob is asked about, and dropped at the first test it misses.
     */
    private static final Map<Object, double[]> ASKED = new HashMap<>();
    /** The round of tests the frames ask for now. */
    private static int round;
    private static volatile boolean busy;
    private static int clock;

    private Occlusion() {
    }

    /**
     * With Vulkan, once Distant Horizons' depth is merged: the depth test hides each mob pixel by
     * pixel, and the far terrain's world - coarse, twice a second - would only get it wrong.
     */
    private static boolean byPixels() {
        return !DepthFar.openGl() && FarDepth.mergedLastFrame() && !byDepth();
    }

    /** Whether the depth answers rather than the far terrain's world. */
    private static boolean byDepth() {
        // Through the game's device where the far terrain goes that way (Vulkan, or Distant
        // Horizons on 26.3), else OpenGL's queries; without either, the far terrain's world.
        if (!FarConfig.get().optOcclusionQueries || !OcclusionQueries.usable()) return false;
        // Far View's Seamless option squeezes its terrain into the back of the depth, with a
        // projection of its own: a mob's depth cannot be compared with it.
        if (FarViewLink.squeezesDepth()) return false;
        return FarDepth.gpuPath() ? OcclusionQueries.gpuReady() : DepthFar.openGl();
    }

    /**
     * The last answer for this mob, standing at its feet, {@code (dx, dy, dz)} from the camera,
     * drawn {@code scale} times its size; false until there is one.
     */
    static boolean hidden(Object key, Entity entity, double dx, double dy, double dz, double scale) {
        // Asked about, so the far terrain's depth must be in the picture this frame.
        FarDepth.needed();
        if (byPixels()) return false;
        // The GPU's answer is the better one: what the player sees, no world to read.
        // Never mixed with the world's answer, which disagrees often enough to make mobs blink.
        if (byDepth()) {
            return OcclusionQueries.hidden(key, dx, dy, dz,
                    Math.max(entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.35) * scale,
                    entity.getBbHeight() * scale);
        }
        double[] at = ASKED.get(key);
        if (at == null) ASKED.put(key, at = new double[5]);
        at[0] = entity.getX();
        at[1] = entity.getY();
        at[2] = entity.getZ();
        at[3] = entity.getBbHeight();
        at[4] = round;
        return HIDDEN.getOrDefault(key, false);
    }

    /** Every client tick, from where the camera is. */
    public static void tick(Vec3 eye) {
        if (!FarConfig.get().hideOccludedMobs || byDepth() || byPixels()) {
            ASKED.clear();
            HIDDEN.clear();
            return;
        }
        if (++clock % EVERY != 0 || busy || ASKED.isEmpty()) return;
        // Only the mobs asked about since the last test; the others are forgotten.
        ASKED.values().removeIf(at -> at[4] != round);
        if (ASKED.isEmpty()) return;
        round++;
        List<Object> keys = new ArrayList<>(ASKED.keySet());
        int n = keys.size();
        double[] xs = new double[n * 2], ys = new double[n * 2], zs = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double[] at = ASKED.get(keys.get(i));
            xs[2 * i] = xs[2 * i + 1] = at[0];
            zs[2 * i] = zs[2 * i + 1] = at[2];
            ys[2 * i] = at[1] + at[3] * 0.5;
            ys[2 * i + 1] = at[1] + at[3] * 0.95;
        }
        busy = true;
        LodWorld.blocked(eye, xs, ys, zs, 1.0).whenComplete((blocked, error) -> {
            try {
                if (blocked != null) {
                    for (int i = 0; i < n; i++) HIDDEN.put(keys.get(i), blocked[2 * i] && blocked[2 * i + 1]);
                    HIDDEN.keySet().retainAll(new HashSet<>(keys));
                } else {
                    // No answer (no world, a failed read): better a mob too many than one hidden for good.
                    HIDDEN.clear();
                }
            } finally {
                busy = false;
            }
        });
    }

    /** How many of the mobs tested last are hidden, for the debug panel. */
    public static int hiddenCount() {
        if (byDepth()) return OcclusionQueries.culled();
        int hidden = 0;
        for (boolean value : HIDDEN.values()) if (value) hidden++;
        return hidden;
    }
}
