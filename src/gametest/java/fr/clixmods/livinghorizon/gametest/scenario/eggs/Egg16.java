package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.LostPet;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Easter egg 16, at night. Called with no pet of the player's remembered: a wolf with a red
 * collar, sitting far off and facing them, seen from 50 blocks, running off once they are within 30.
 * Then a wolf of theirs with a yellow collar, met and left 170 blocks behind: called again, the
 * copy that sits far off is that wolf.
 */
public final class Egg16 {
    /** The game's numbers for a red and a yellow collar. */
    private static final String RED = "14", YELLOW = "4";

    private Egg16() {
    }

    private static LostPet egg() {
        return FarPlayerTracker.get().ambience().lostPet();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 16 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set midnight");
        context.waitTicks(5);

        // No pet of theirs known: the red collar.
        Vec3 at = call(context, "ee16");
        double start = context.computeOnClient(minecraft -> Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ()));
        Scene.log("eggs2 ee16 at " + at + ", " + (int) start + " blocks");
        if (start < 55) throw new AssertionError("ee16 at " + (int) start + " blocks, too near");
        pet(context, RED, "ee16");
        context.waitTicks(20);
        facing(context, "ee16");

        // Seen from 50 blocks: still there.
        Vec3 near = context.computeOnClient(minecraft -> toward(minecraft.player.position(), at, 50 / Math.hypot(
                at.x - minecraft.player.getX(), at.z - minecraft.player.getZ())));
        Scene.command(world, "tp @a " + near.x + " ~ " + near.z);
        context.waitTicks(10);
        if (!context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee16 gone 50 blocks away");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y + 0.5, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(10);
        facing(context, "ee16 from 50 blocks");
        Scene.screenshot(context, scenario, "ee16-wolf");

        // Within 30 blocks: it gets up and runs off, and is gone a few seconds later.
        Vec3 close = context.computeOnClient(minecraft -> toward(minecraft.player.position(), at, 30 / Math.hypot(
                at.x - minecraft.player.getX(), at.z - minecraft.player.getZ())));
        Scene.command(world, "tp @a " + close.x + " ~ " + close.z);
        context.waitTicks(20);
        double off = context.computeOnClient(minecraft -> {
            List<? extends Entity> figures = egg().figures();
            if (figures.isEmpty() || !(figures.get(0) instanceof TamableAnimal pet)) throw new AssertionError("ee16 gone at once");
            if (pet.isInSittingPose()) throw new AssertionError("ee16 still sitting with the player 30 blocks away");
            return Math.hypot(pet.getX() - minecraft.player.getX(), pet.getZ() - minecraft.player.getZ());
        });
        Scene.log("eggs2 ee16 running off, " + (int) off + " blocks away");
        if (off < 33) throw new AssertionError("ee16 not running off: " + off + " blocks away");
        Scene.screenshot(context, scenario, "ee16-running");
        context.waitTicks(130);
        if (context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee16 still there after running off");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));

        // A wolf of theirs, a yellow collar on, met then left behind.
        Scene.command(world, "tp @a " + 16 * 1000 + " -60 0 0 0");
        context.waitTicks(40);
        String owner = context.computeOnClient(minecraft -> {
            int[] id = UUIDUtil.uuidToIntArray(minecraft.player.getUUID());
            return id[0] + "," + id[1] + "," + id[2] + "," + id[3];
        });
        Scene.command(world, "execute at @p run summon minecraft:wolf ~ ~ ~40 {NoAI:1b,Owner:[I;" + owner
                + "],CollarColor:4b,Sitting:1b}");
        context.waitTicks(100);
        Scene.command(world, "tp @a " + 16 * 1000 + " -60 -130 0 0");
        context.waitTicks(300);
        boolean remembered = context.computeOnClient(minecraft -> {
            String wolf = EntityType.getKey(EntityType.WOLF).toString();
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered(minecraft.level.dimension().identifier().toString())) {
                if (wolf.equals(mob.type())) return true;
            }
            return false;
        });
        if (!remembered) throw new AssertionError("ee16: the player's wolf is not remembered");
        Vec3 again = call(context, "ee16 with a pet");
        Scene.log("eggs2 ee16 with a pet at " + again);
        pet(context, YELLOW, "ee16 with a pet");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, again.x, again.y + 0.5, again.z);
            minecraft.options.fov().set(30);
        });
        Scene.screenshot(context, scenario, "ee16-own-wolf");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** Called: where it sits, once found, on the ground the game draws. */
    private static Vec3 call(ClientGameTestContext context, String egg) {
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError(egg + " did not start");
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) {
                drawn(context, at, egg);
                return at;
            }
            context.waitTick();
        }
        throw new AssertionError(egg + " found no place");
    }

    /** One figure: a tamed wolf, sitting, with a collar of this colour. */
    private static void pet(ClientGameTestContext context, String collar, String egg) {
        context.runOnClient(minecraft -> {
            List<? extends Entity> figures = egg().figures();
            if (figures.size() != 1) throw new AssertionError(egg + " shows " + figures.size() + " figures");
            Entity figure = figures.get(0);
            if (figure.getType() != EntityType.WOLF) throw new AssertionError(egg + " is a " + figure.getType());
            if (!(figure instanceof TamableAnimal pet) || !pet.isTame()) throw new AssertionError(egg + " is not tamed");
            if (!pet.isInSittingPose()) throw new AssertionError(egg + " is not sitting");
            Tag saved = EntityNbt.save(figure, minecraft.level).get("CollarColor");
            String colour = saved == null ? "none" : saved.toString().replaceAll("[^0-9]", "");
            Scene.log(egg + " collar " + colour);
            if (!collar.equals(colour)) throw new AssertionError(egg + " wears collar " + colour + ", not " + collar);
        });
    }

    /** Its head turned to the player, within a few degrees. */
    private static void facing(ClientGameTestContext context, String egg) {
        context.runOnClient(minecraft -> {
            LocalPlayer self = minecraft.player;
            List<? extends Entity> figures = egg().figures();
            if (figures.isEmpty() || !(figures.get(0) instanceof TamableAnimal pet)) throw new AssertionError(egg + " is gone");
            float to = (float) (Mth.atan2(self.getZ() - pet.getZ(), self.getX() - pet.getX()) * Mth.RAD_TO_DEG) - 90f;
            float off = Math.abs(Mth.wrapDegrees(pet.yHeadRot - to));
            Scene.log("eggs2 " + egg + " head " + (int) pet.yHeadRot + ", to the player " + (int) to);
            if (off > 10) throw new AssertionError(egg + " looks " + (int) off + " degrees off the player");
        });
    }

    /** The point {@code share} of the way from {@code to} back to {@code from}, across. */
    private static Vec3 toward(Vec3 from, Vec3 to, double share) {
        return new Vec3(to.x + (from.x - to.x) * share, from.y, to.z + (from.z - to.z) * share);
    }

    /** Standing where the game draws the ground, on it: no far world here to draw farther. */
    private static void drawn(ClientGameTestContext context, Vec3 at, String egg) {
        context.runOnClient(minecraft -> {
            double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
            double edge = minecraft.options.getEffectiveRenderDistance() * 16;
            if (across > edge - 16) throw new AssertionError(egg + " at " + (int) across + " blocks, past the "
                    + (int) edge + " drawn");
            int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            if (at.y <= minecraft.level.getMinY() || Math.abs(at.y - ground) > 3) {
                throw new AssertionError(egg + " at height " + at.y + ", the ground there at " + ground);
            }
        });
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
