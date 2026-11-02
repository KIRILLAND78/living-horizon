package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;

/**
 * A figure seated or lying down, the same whether it is drawn as a model ({@link GhostRenderer})
 * or baked into a picture ({@code ImpostorAtlas}): players who logged off sitting or lying where
 * they were, and riders.
 */
public final class Posture {
    /** Blocks a figure in the riding pose is lowered by to sit on the ground: the seat's height. */
    public static final double SEAT_DROP = 0.6;
    /** Blocks a figure lying on the ground is raised by: the game lays a sleeper a little low. */
    public static final double LIE_LIFT = 0.13;
    /**
     * Blocks a figure lying down is raised by in its picture: the game lays a sleeper across its
     * feet, half of it below them, out of the tile. Lowered back by as much where it is drawn.
     */
    public static final double BAKED_LIFT = 0.3;

    private Posture() {
    }

    /** The riding pose, legs out in front: on a mount, or on the ground. False for a figure without one. */
    public static boolean seat(EntityRenderState state) {
        if (!(state instanceof HumanoidRenderState humanoid)) return false;
        humanoid.isPassenger = true;
        return true;
    }

    /**
     * Asleep: in a bed, laid along it by the game; otherwise along its body rotation, as the
     * game lays a sleeper with no bed. False for a figure that cannot lie down.
     */
    public static boolean lie(EntityRenderState state, boolean inBed) {
        if (!(state instanceof LivingEntityRenderState living)) return false;
        living.pose = Pose.SLEEPING;
        if (!inBed) living.bedOrientation = null;
        return true;
    }

    /** A figure in the posture its picture is baked in ({@link ImpostorKey#posture()}). */
    public static void baked(EntityRenderState state, int posture) {
        if (posture == ImpostorKey.SEATED) seat(state);
        else if (posture == ImpostorKey.LYING) lie(state, false);
    }

    /** Blocks a figure is raised by in a picture baked in {@code posture}. */
    public static double bakedLift(int posture) {
        return posture == ImpostorKey.LYING ? BAKED_LIFT : 0;
    }
}
