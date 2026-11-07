package fr.clixmods.livinghorizon.track;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Vehicles, other mods' included: an aircraft, a car, a tank. They are not alive, so the list
 * of mobs leaves them out, and no list of ids can know them all. What tells one is what its
 * class does that a plain entity does not, the way the game itself treats a boat:
 *
 * <ul>
 * <li>{@link Kind#STEERED}: it answers who steers it ({@code getControllingPassenger}), which
 *     the game asks every tick to know whose keys move it. A boat, Immersive Aircraft's planes.
 *     Shown far away from the start, like a boat.</li>
 * <li>{@link Kind#SEATED}: it only places its riders ({@code positionRider}). A minecart,
 *     Superb Warfare's vehicles, but also Create's seats and moving contraptions, whose copies
 *     could cost a lot: listed among the mobs once met, and only shown once chosen.</li>
 * </ul>
 *
 * <p>Both methods are found on {@link Entity} by their shape, not their name, which is not
 * the same under every loader. The answer is kept per class: asking costs a lookup.
 */
public final class Vehicles {
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    /** What an entity is as a vehicle. */
    public enum Kind {
        /** Not a vehicle: alive, or carrying riders the way any entity can. */
        NONE,
        /** Steered by its rider. */
        STEERED,
        /** Places its riders, steered or not. */
        SEATED
    }

    /** Entity's {@code getControllingPassenger()}: the one method of Entity that takes nothing and gives a living entity. */
    private static final @Nullable Method STEERING = find("getControllingPassenger",
            method -> method.getParameterCount() == 0 && method.getReturnType() == LivingEntity.class);
    /** Entity's {@code positionRider(Entity, MoveFunction)}: the one that takes where to move a rider. */
    private static final @Nullable Method SEATING = find("positionRider",
            method -> method.getParameterCount() == 2 && method.getParameterTypes()[0] == Entity.class
                    && method.getParameterTypes()[1] == Entity.MoveFunction.class);

    private static final ClassValue<Kind> KINDS = new ClassValue<>() {
        @Override
        protected Kind computeValue(Class<?> type) {
            return kindOf(type);
        }
    };

    private Vehicles() {
    }

    /** What this entity is as a vehicle. Any thread. */
    public static Kind of(Entity entity) {
        return KINDS.get(entity.getClass());
    }

    /** Whether this entity is a vehicle of either kind. */
    public static boolean is(Entity entity) {
        return of(entity) != Kind.NONE;
    }

    /** What entities of this class are as vehicles. */
    public static Kind kindOf(Class<?> type) {
        if (!Entity.class.isAssignableFrom(type) || LivingEntity.class.isAssignableFrom(type)) return Kind.NONE;
        if (overrides(type, STEERING)) return Kind.STEERED;
        return overrides(type, SEATING) ? Kind.SEATED : Kind.NONE;
    }

    /** Whether a class below Entity declares this method of Entity's again. */
    private static boolean overrides(Class<?> type, @Nullable Method method) {
        if (method == null) return false;
        for (Class<?> at = type; at != null && at != Entity.class; at = at.getSuperclass()) {
            try {
                Method declared = at.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (!declared.isBridge() && !Modifier.isAbstract(declared.getModifiers())) return true;
            } catch (NoSuchMethodException e) {
                // Not this class: its parent, then.
            } catch (LinkageError e) {
                // A class that cannot be read (a client class on a server): no vehicle.
                return false;
            }
        }
        return false;
    }

    /**
     * The one method of Entity of this shape. Another mod may add one of the same shape: then
     * the one a boat declares again is the game's. None, or still more than one: that kind of
     * vehicle is never found, said once in the log.
     */
    private static @Nullable Method find(String what, Predicate<Method> shape) {
        List<Method> found = new ArrayList<>();
        for (Method method : Entity.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())
                    || method.isSynthetic() || method.isBridge()) continue;
            if (shape.test(method)) found.add(method);
        }
        if (found.size() > 1) found.removeIf(method -> !overrides(boat(), method));
        if (found.size() == 1) return found.get(0);
        LOGGER.warn("Vehicles of other mods are not drawn far away: Entity has {} methods like {}", found.size(), what);
        return null;
    }

    /** The class every boat comes from, which declares both methods again. */
    private static Class<?> boat() {
        //? if >=1.21.2 {
        return net.minecraft.world.entity.vehicle.boat.AbstractBoat.class;
        //?} else
        /*return net.minecraft.world.entity.vehicle.Boat.class;*/
    }
}
