package fr.clixmods.livinghorizon.track;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * How a remembered mob that flies keeps flying far away, where a walker plays one of the
 * {@link MobAnimations}: a phantom circles above the spot where it was last seen, a ghast
 * drifts about it. Like an animation, nothing is decided while it plays: where it is at a
 * tick comes from its seed and the clock alone, so each one flies its own way, and the
 * same way every time. No ground is read: what is around it in the air is not known.
 *
 * <p>Seen from below far more often than from the side, these mobs are pictured from
 * several heights when they are impostors ({@link #pictured}).
 */
public enum MobFlight {
    /** A phantom: round and round, rising and dipping, now wider, now tighter. */
    CIRCLE,
    /** A ghast: slow loops through the air about its spot, never quite the same twice. */
    DRIFT;

    /** Blocks a phantom's circle is above where it was last seen: down low, it had come for a player. */
    static final double CIRCLE_RISE = 5;
    /** Blocks a tick a phantom flies round its circle. */
    static final double CIRCLE_PACE = 0.4;
    /** Blocks from its spot a ghast drifts at most, along the ground, and up or down. */
    static final double DRIFT_REACH = 10, DRIFT_RISE = 3;

    /** The flight of a remembered mob's type, null for one that walks or swims. */
    public static @Nullable MobFlight of(String type) {
        return switch (type) {
            case "minecraft:phantom" -> CIRCLE;
            case "minecraft:ghast", "minecraft:happy_ghast" -> DRIFT;
            default -> null;
        };
    }

    /**
     * Whether an entity is pictured from several heights as an impostor: one of the flyers
     * above, seen from below as often as level with it.
     */
    public static boolean pictured(Entity entity) {
        EntityType<?> type = entity.getType();
        return of(EntityType.getKey(type).toString()) != null;
    }

    /**
     * Where it is at tick {@code t} of its flight, from the spot it was last seen at, written
     * into {@code out}: east, up, south. Moves smoothly, never faster than a phantom flies.
     */
    void at(long seed, double t, double[] out) {
        switch (this) {
            case CIRCLE -> {
                // Its own circle: 7 to 13 blocks across the middle, either way round.
                double radius = 7 + Math.floorMod(seed >> 3, 7L);
                double turn = ((seed >> 9 & 1) == 0 ? 1 : -1) * CIRCLE_PACE / radius;
                double phase = Math.floorMod(seed >> 13, 628L) / 100.0;
                // Wider and tighter, higher and lower, each slowly and at a pace of its own.
                double r = radius + 2.5 * Math.sin(t * 0.011 + phase);
                double angle = t * turn + phase;
                out[0] = r * Math.cos(angle);
                out[1] = CIRCLE_RISE + 2 * Math.sin(t * 0.023 + phase * 1.7);
                out[2] = r * Math.sin(angle);
            }
            case DRIFT -> {
                // Loops whose two paces never line up: it does not go round the same way twice.
                double period = 900 + Math.floorMod(seed >> 3, 400L);
                double a = 2 * Math.PI / period, b = a * 0.73, c = a * 1.37;
                double p = Math.floorMod(seed >> 13, 628L) / 100.0, q = Math.floorMod(seed >> 23, 628L) / 100.0;
                out[0] = DRIFT_REACH * Math.sin(t * a + p);
                out[1] = DRIFT_RISE * Math.sin(t * c + p + q);
                out[2] = DRIFT_REACH * Math.sin(t * b + q);
            }
        }
    }
}
