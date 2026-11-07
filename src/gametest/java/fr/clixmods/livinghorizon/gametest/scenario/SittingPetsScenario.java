package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pets told to sit stay sitting far away: two tamed wolves and two tamed cats, one of each
 * sitting, left 200 blocks behind. The client never learns that a pet was told to sit (the
 * game saves that order from a field only the server has): without the mod's help their
 * copies stand up and stroll like any other animal.
 *
 * <p>The simulation distance is the smallest, so that the server does not move them and the
 * copies play their own animation. Then the sitting wolf is stood up on the server: its copy
 * stands up too.
 */
public final class SittingPetsScenario implements Scenario {
    /** The pets, by the id they are summoned with: kind, where across, sitting. */
    private record Pet(String type, double x, boolean sitting, int n) {
        UUID id() {
            return new UUID(7L << 32 | 7L, 7L << 32 | n);
        }

        String nbt() {
            return "{NoAI:1b,UUID:[I;7,7,7," + n + "],Owner:[I;1,2,3,4],Sitting:" + (sitting ? 1 : 0)
                    + "b,Rotation:[180f,0f]}";
        }
    }

    private static final Pet[] PETS = {
            new Pet("wolf", -9, true, 1),
            new Pet("cat", -3, true, 2),
            new Pet("wolf", 3, false, 3),
            new Pet("cat", 9, false, 4),
    };

    @Override
    public String name() {
        return "sittingpets";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            for (Pet pet : PETS) {
                Scene.command(world, "execute at @p run summon " + pet.type() + " ~" + pet.x() + " ~ ~40 " + pet.nbt());
            }
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);

            Map<UUID, double[]> first = copies(context, "first");
            for (Pet pet : PETS) {
                double[] copy = first.get(pet.id());
                if (copy == null) throw new AssertionError("The " + describe(pet) + " has no copy far away");
                if ((copy[3] == 1) != pet.sitting()) {
                    throw new AssertionError("The " + describe(pet) + "'s copy is " + (copy[3] == 1 ? "sitting" : "standing"));
                }
            }
            context.waitTicks(200);
            Map<UUID, double[]> later = copies(context, "later");
            for (Pet pet : PETS) {
                double[] a = first.get(pet.id()), b = later.get(pet.id());
                if (b == null) throw new AssertionError("The " + describe(pet) + "'s copy is gone");
                double moved = Math.hypot(b[0] - a[0], b[2] - a[2]);
                double fromSpot = Math.hypot(b[0] - b[4], b[2] - b[5]);
                Scene.log("sittingpets " + describe(pet) + " moved " + moved + ", " + fromSpot + " from its spot");
                if (pet.sitting() && (moved > 0.05 || fromSpot > 0.05)) {
                    throw new AssertionError("The " + describe(pet) + "'s copy walked " + moved + " blocks");
                }
            }

            // Nearer for the pictures: 90 blocks, still past the 80 a wolf is sent at.
            Scene.command(world, "tp @a 0 ~ -50 0 0");
            context.waitTicks(40);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "models");
            Scene.configure(context, config -> {
                config.impostors = true;
                config.impostorDistance = 100;
                config.impostorPixels = 0;
            });
            context.waitTicks(60);
            Scene.screenshot(context, this, "impostors");

            // Stood up on the server while far away: the copy stands up as well.
            Scene.command(world, "data merge entity " + PETS[0].id() + " {Sitting:0b}");
            context.waitTicks(40);
            double[] stood = copies(context, "stood").get(PETS[0].id());
            if (stood == null || stood[3] == 1) {
                throw new AssertionError("The wolf stood up on the server and its copy " + (stood == null ? "is gone" : "still sits"));
            }
            Scene.screenshot(context, this, "stood");
        }
    }

    private static String describe(Pet pet) {
        return (pet.sitting() ? "sitting " : "standing ") + pet.type();
    }

    /** Each pet's copy: where it is, 1 when sitting, and where it is remembered. */
    private static Map<UUID, double[]> copies(ClientGameTestContext context, String when) {
        return context.computeOnClient(minecraft -> {
            Map<UUID, double[]> found = new HashMap<>();
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                if (!(puppet instanceof TamableAnimal pet)) continue;
                found.put(mob.id(), new double[]{puppet.getX(), puppet.getY(), puppet.getZ(),
                        pet.isInSittingPose() ? 1 : 0, mob.x(), mob.z()});
                Scene.log("sittingpets " + when + " " + mob.type() + " " + mob.id() + " at " + puppet.position()
                        + (pet.isInSittingPose() ? " sitting" : " standing"));
            }
            return found;
        });
    }
}
