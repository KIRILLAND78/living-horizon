package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Players who logged off, far away, are pictures like players online: test players 150
 * blocks off, past the impostor distance. One on foot gets a picture of its own seated
 * ({@code offlineLife} off) and standing (on its loop, by day and still at night); one on a horse,
 * one in a boat and one in a minecart a picture each of both together. The impostor page is shown last, to see every posture.
 */
public final class RestingImpostorsScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NAME = "Far", RIDER = "FarRider", ROWER = "FarRower", CARTER = "FarCarter";

    @Override
    public String name() {
        return "restingimpostors";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = false;
            config.impostorPlayers = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // One on foot, one on a horse, both 150 blocks south of the camera.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 150.5 180 0");
            context.waitTicks(20);
            command(context, "livinghorizon debug offline test spawn " + NAME);
            Scene.command(world, "summon horse 6.5 " + GROUND + " 150.5 {Tame:1b,NoAI:1b,"
                    + "SaddleItem:{id:\"minecraft:saddle\",count:1},equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 148 0 0");
            context.waitTicks(10);
            Scene.command(world, "ride @p mount @e[type=horse,limit=1,sort=nearest]");
            context.waitTicks(20);
            command(context, "livinghorizon debug offline test spawn " + RIDER);
            context.waitTicks(5);
            Scene.command(world, "ride @p dismount");
            Scene.command(world, "tp @e[type=horse] ~ -200 ~");
            Scene.command(world, "kill @e[type=horse]");
            // One in a boat with a chest on a pond, one in a minecart: vehicles, not mobs.
            Scene.command(world, "fill -16 " + (GROUND - 2) + " 146 -8 " + (GROUND - 1) + " 156 water");
            vehicle(context, world, ROWER, "oak_chest_boat", -12.5, GROUND - 0.5, 151.5);
            vehicle(context, world, CARTER, "minecart", 12.5, GROUND, 151.5);
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(40);

            // Seated on the ground: a seated picture.
            String seated = pictured(context, NAME, RestingPuppet.Look.SIT, ImpostorKey.SEATED);
            if (seated != null) throw new AssertionError("restingimpostors seated: " + seated);
            Scene.screenshot(context, this, "seated");

            // Living, away from the camera: its loop, standing between its pauses.
            Scene.configure(context, config -> config.offlineLife = true);
            String standing = pictured(context, NAME, RestingPuppet.Look.STAND, ImpostorKey.STANDING);
            if (standing != null) throw new AssertionError("restingimpostors standing: " + standing);
            Scene.screenshot(context, this, "standing");

            // At night, with no bed: still on its feet, never lying on the ground.
            Scene.command(world, "time set midnight");
            context.waitTicks(20);
            String awake = pictured(context, NAME, RestingPuppet.Look.STAND, ImpostorKey.STANDING);
            if (awake != null) throw new AssertionError("restingimpostors night: " + awake);

            // The riders and what carries them: one picture each, of both together.
            for (String rider : new String[]{RIDER, ROWER, CARTER}) {
                String riding = ridden(context, rider);
                if (riding != null) throw new AssertionError("restingimpostors " + rider + ": " + riding);
            }
            Scene.screenshot(context, this, "night");
            Scene.screenshotAtlas(context, this);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /** Rides a new {@code type} at x, y, z, leaves test player {@code name} on it, and takes the real one away. */
    private static void vehicle(ClientGameTestContext context, TestSingleplayerContext world, String name, String type,
                                double x, double y, double z) {
        Scene.command(world, "summon " + type + " " + x + " " + y + " " + z);
        Scene.command(world, "tp @a " + x + " " + (GROUND + 1) + " " + (z - 3) + " 0 0");
        context.waitTicks(10);
        Scene.command(world, "ride @p mount @e[type=" + type + ",limit=1,sort=nearest]");
        context.waitTicks(20);
        command(context, "livinghorizon debug offline test spawn " + name);
        context.waitTicks(5);
        Scene.command(world, "ride @p dismount");
        Scene.command(world, "kill @e[type=" + type + "]");
    }

    /** Waits for the picture of a test player and what it rides; null once there, else why not. */
    private static String ridden(ClientGameTestContext context, String name) {
        String why = null;
        for (int i = 0; i < 30; i++) {
            why = context.computeOnClient(minecraft -> {
                RestingPuppet rider = puppet(minecraft, name);
                Entity mount = rider.mount();
                if (mount == null) return "on nothing";
                float facing = mount instanceof LivingEntity horse ? horse.yBodyRot : mount.getYRot();
                Vec3 seat = rider.position().subtract(mount.position()).yRot((float) Math.toRadians(facing));
                ImpostorKey key = ImpostorKey.riding(rider, mount, seat, Mth.wrapDegrees(rider.yBodyRot - facing));
                return ImpostorAtlas.sheet(key) != null ? null : "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
            });
            if (why == null) break;
            context.waitTicks(10);
        }
        Scene.log("restingimpostors " + name + ": " + (why == null ? "pictured" : why));
        return why;
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    /** Waits for a test player's picture in this look; null once there, else why not. */
    private static String pictured(ClientGameTestContext context, String name, RestingPuppet.Look look, int posture) {
        String why = null;
        for (int i = 0; i < 60; i++) {
            why = context.computeOnClient(minecraft -> {
                RestingPuppet puppet = puppet(minecraft, name);
                if (puppet.look() != look) return "look " + puppet.look();
                ImpostorKey key = ImpostorKey.of(puppet);
                if (ImpostorKey.posture(key) != posture) return "posture " + ImpostorKey.posture(key);
                return ImpostorAtlas.sheet(key) != null ? null : "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
            });
            if (why == null) break;
            context.waitTicks(5);
        }
        Scene.log("restingimpostors " + name + " " + look + ": " + (why == null ? "pictured" : why));
        return why;
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingimpostors: no test player");
    }
}
