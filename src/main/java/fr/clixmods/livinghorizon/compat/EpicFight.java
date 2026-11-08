package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * Epic Fight animates a player from its tick: its patch joins on {@code EntityJoinLevelEvent} and
 * its animator moves on {@code EntityTickEvent}, neither of which fires for an offline player's
 * puppet, which is never added to the level and lives by a tick of its own. Its model was drawn,
 * frozen in its first pose. Around that tick this calls Epic Fight's hooks as the level would.
 *
 * <p>Ticking is not enough to look alive. Epic Fight tells a remote player walks from its patch's
 * {@code dx}/{@code dz}, which only a server packet sets, so they are set from how far the puppet
 * moved. And a puppet sits, sleeps or rides only as it is drawn, with no seat, bed or vehicle Epic
 * Fight could read, so its motion is set from {@link RestingPuppet#look()}.
 *
 * <p>Reached by reflection, so that the mod loads without it; checked against Epic Fight 21.17
 * (NeoForge 1.21.1). Any failure, or an older version without these hooks, turns this off.
 */
public final class EpicFight {
    private static boolean looked;
    private static boolean working;

    private static Method getPatch;
    private static Class<?> clientPlayerPatch;
    private static Method uninitialized;
    private static Method eventListener;
    private static Method registerEvent;
    private static Object motionHook;
    private static Object permanent;
    private static Class<?> subscription;
    private static Field dx;
    private static Field dz;
    private static Method onJoinLevel;
    private static Method preTick;
    private static Method postTick;
    private static Object sit;
    private static Object sleep;
    private static Object mount;

    private EpicFight() {
    }

    /** At the head of the puppet's tick, before it moves or keeps its old position. */
    public static void beforeTick(RestingPuppet puppet) {
        if (!looked) find();
        if (!working) return;
        try {
            Object patch = ((Optional<?>) getPatch.invoke(null, puppet, clientPlayerPatch)).orElse(null);
            if (patch != null) {
                if ((boolean) uninitialized.invoke(patch)) listen(puppet, patch);
                // How far it moved last tick: Epic Fight walks it from that.
                dx.setDouble(patch, 0.0);
                dz.setDouble(patch, Math.hypot(puppet.getX() - puppet.xOld, puppet.getZ() - puppet.zOld));
            }
            // Joins once: the hook does nothing for a patch already joined.
            onJoinLevel.invoke(null, puppet, puppet.level(), false);
            preTick.invoke(null, puppet);
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** At every end of the puppet's tick. */
    public static void afterTick(RestingPuppet puppet) {
        if (!working) return;
        try {
            postTick.invoke(null, puppet);
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** Sets the puppet's motion from how it is drawn, unless Epic Fight plays an action. */
    private static void listen(RestingPuppet puppet, Object patch) throws ReflectiveOperationException {
        Object listener = Proxy.newProxyInstance(subscription.getClassLoader(), new Class<?>[]{subscription},
                (self, method, args) -> switch (method.getName()) {
                    case "fire" -> {
                        Object event = args[0];
                        Object motion = motion(puppet);
                        if (working && motion != null && !(boolean) event.getClass().getMethod("inaction").invoke(event)) {
                            Method set = null;
                            for (Method candidate : event.getClass().getMethods()) {
                                if (candidate.getName().equals("setMotion")) set = candidate;
                            }
                            if (set != null) set.invoke(event, motion);
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> "Living Horizon offline player motion";
                    default -> null;
                });
        registerEvent.invoke(eventListener.invoke(patch), motionHook, listener, permanent);
    }

    /** The motion its look stands for, or null when Epic Fight's own reading holds. */
    private static @Nullable Object motion(RestingPuppet puppet) {
        return switch (puppet.look()) {
            case SIT -> sit;
            case BED -> sleep;
            case RIDE -> puppet.mount() instanceof PlayerRideableJumping ? mount : sit;
            case STAND -> null;
        };
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("epicfight")) return;
        try {
            Class<?> capabilities = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
            getPatch = capabilities.getMethod("getUnparameterizedEntityPatch", Entity.class, Class.class);
            clientPlayerPatch = Class.forName(
                    "yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch");
            uninitialized = clientPlayerPatch.getMethod("uninitialized");
            eventListener = clientPlayerPatch.getMethod("getEventListener");
            Class<?> hook = Class.forName("yesman.epicfight.api.event.EventHook");
            subscription = Class.forName("yesman.epicfight.api.event.subscription.DefaultEventSubscription");
            Class<?> identifier = Class.forName("yesman.epicfight.api.event.IdentifierProvider");
            registerEvent = eventListener.getReturnType().getMethod("registerEvent", hook, subscription, identifier);
            motionHook = Class.forName("yesman.epicfight.api.client.event.EpicFightClientEventHooks$Entity")
                    .getField("MODIFY_PLAYER_LIVING_MOTION_BASE").get(null);
            permanent = identifier.getMethod("permanent").invoke(null);
            dx = clientPlayerPatch.getField("dx");
            dz = clientPlayerPatch.getField("dz");
            Class<?> hooks = Class.forName("yesman.epicfight.api.event.impl.VanillaEntityEventHooks");
            onJoinLevel = hooks.getMethod("onJoinLevel", Entity.class, Level.class, boolean.class);
            preTick = hooks.getMethod("preTick", Entity.class);
            postTick = hooks.getMethod("postTick", Entity.class);
            Class<?> motions = Class.forName("yesman.epicfight.api.animation.LivingMotions");
            sit = motions.getField("SIT").get(null);
            sleep = motions.getField("SLEEP").get(null);
            mount = motions.getField("MOUNT").get(null);
            working = true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            LivingHorizonClient.LOGGER.warn("Epic Fight found, but not the version this mod knows:"
                    + " offline players are not animated by it", error);
        }
    }

    private static void fail(Throwable error) {
        if (!working) return;
        working = false;
        LivingHorizonClient.LOGGER.warn("Epic Fight found, but not the version this mod knows:"
                + " offline players are not animated by it", error);
    }
}
