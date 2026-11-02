package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

/**
 * Players who logged off on what they rode: a horse, a boat, and from 1.21.6 a happy ghast.
 * Each time the camera rides it, the debug command leaves a test player on a copy of it, and
 * the real one goes. Seated on the spot first ({@code offlineLife} off), then about it: the
 * mount goes somewhere and the rider stays on it, never jumping ahead; the boat keeps to the
 * water. With mounts hidden the rider is on foot, and back on it once they show again. Last,
 * a player this client never saw, who logged off on a horse a server with the mod shares.
 */
public final class RestingMountScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "restingmount";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A saddled horse, the saddle as each game version keeps it.
            mounted(context, world, "Rider", "horse", 0.5, GROUND, 8.5,
                    "{Tame:1b,NoAI:1b,SaddleItem:{id:\"minecraft:saddle\",count:1},"
                            + "equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 15");
            context.waitTicks(20);
            String still = rider(context, "Rider", p -> p.look() == RestingPuppet.Look.RIDE && p.mount() != null
                    ? null : "look " + p.look() + ", mount " + p.mount());
            if (still != null) throw new AssertionError("restingmount horse: not seated on it, " + still);
            Scene.screenshot(context, this, "horse-still");

            Scene.configure(context, config -> config.offlineLife = true);
            roams(context, "Rider", 0.5, 8.5, 600, Double.NaN);
            lookAt(context, world, "Rider");
            Scene.screenshot(context, this, "horse-roaming");

            // Mounts hidden: on foot. Shown again: back on it.
            Scene.configure(context, config -> config.showVehicles = false);
            context.waitTicks(10);
            String foot = rider(context, "Rider", p -> p.look() != RestingPuppet.Look.RIDE && p.mount() == null
                    ? null : "look " + p.look());
            if (foot != null) throw new AssertionError("restingmount: still riding with mounts hidden, " + foot);
            Scene.configure(context, config -> config.showVehicles = true);
            context.waitTicks(10);
            String back = rider(context, "Rider", p -> p.look() == RestingPuppet.Look.RIDE ? null : "look " + p.look());
            if (back != null) throw new AssertionError("restingmount: not back on the horse, " + back);

            // A boat on a pond: it rows about, on the water.
            Scene.command(world, "fill -10 " + (GROUND - 3) + " 20 10 " + (GROUND - 1) + " 40 water");
            context.waitTicks(20);
            // The boat turned a quarter from where the camera looks: the rower sits facing the boat, not the camera.
            mounted(context, world, "Rower", "oak_boat", 0.5, GROUND - 0.1, 30.5, "{Rotation:[90f,0f]}");
            String facing = rider(context, "Rower", p -> {
                float off = net.minecraft.util.Mth.wrapDegrees(p.yBodyRot - p.mount().getYRot());
                return Math.abs(off) < 20 ? null : "body " + p.yBodyRot + ", boat " + p.mount().getYRot();
            });
            if (facing != null) throw new AssertionError("restingmount: the rower does not face the boat's way, " + facing);
            Scene.command(world, "tp @a 0.5 " + GROUND + " 12 0 15");
            roams(context, "Rower", 0.5, 30.5, 600, GROUND - 1);
            lookAt(context, world, "Rower");
            Scene.screenshot(context, this, "boat");

            // A happy ghast, where the game has them: it drifts through the air.
            boolean ghasts = context.computeOnClient(minecraft -> BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                    .anyMatch(key -> key.getPath().equals("happy_ghast")));
            if (ghasts) {
                // A crowd of them placed while flying: in the air, at the camera's height, not on the ground.
                mounted(context, world, "Flyer", "happy_ghast", -20.5, GROUND + 8, 10.5,
                        "{NoAI:1b,equipment:{body:{id:\"minecraft:white_harness\",count:1}}}",
                        "livinghorizon debug offline test crowd 4 12");
                String grounded = context.computeOnClient(minecraft -> {
                    RestingPlayers resting = FarPlayerTracker.get().resting();
                    for (RestingPlayers.Spot spot : resting.spots()) {
                        if (spot.name().startsWith("Sleeper") && spot.y() < GROUND + 8) {
                            return spot.name() + " at y=" + spot.y() + (spot.mount() == null ? ", on nothing" : "");
                        }
                    }
                    return resting.spots().stream().filter(spot -> spot.name().startsWith("Sleeper")).count() == 4
                            ? null : "not 4 of them";
                });
                Scene.log("restingmount crowd on happy ghasts: " + (grounded == null ? "in the air" : grounded));
                if (grounded != null) throw new AssertionError("restingmount crowd on happy ghasts: " + grounded);
                Scene.command(world, "tp @a -20.5 " + GROUND + " -10 0 -10");
                roams(context, "Flyer", -20.5, 10.5, 800, Double.NaN);
                lookAt(context, world, "Flyer", GROUND + 14);
                Scene.screenshot(context, this, "happy-ghast");
            }
            clientCommand(context, "livinghorizon debug offline test clear");

            // Known from the server alone: it shares the horse they took away with them.
            PositionsMessage.Player faraway = new PositionsMessage.Player("Faraway", 12.5, GROUND, -12.5, 0f, "minecraft:overworld", false, true,
                    null, new PositionsMessage.Mount("minecraft:horse", "{Tame:1b,Variant:513}", 0, 0.85, -0.2, 0f, 0f));
            for (int i = 0; i < 6; i++) {
                context.runOnClient(minecraft -> {
                    FarPlayerTracker.get().receive(PositionsMessage.players(List.of()));
                    PositionsMessage.offline(List.of(faraway)).forEach(FarPlayerTracker.get()::receive);
                });
                context.waitTicks(10);
            }
            String shared = rider(context, "Faraway", p -> p.mount() != null
                    && "minecraft:horse".equals(BuiltInRegistries.ENTITY_TYPE.getKey(p.mount().getType()).toString())
                    ? null : "mount " + p.mount());
            if (shared != null) throw new AssertionError("restingmount: the server's horse is not under them, " + shared);
            Scene.log("restingmount Faraway on the server's horse");
        }
    }

    /**
     * Rides a new {@code type} at x, y, z, leaves test player {@code name} on it, then gets
     * off and takes the real one away, as the server does when a player logs off on it.
     */
    private static void mounted(ClientGameTestContext context, TestSingleplayerContext world, String name, String type,
                                double x, double y, double z, String nbt) {
        mounted(context, world, name, type, x, y, z, nbt, null);
    }

    /** The same, typing {@code also} too while it rides. */
    private static void mounted(ClientGameTestContext context, TestSingleplayerContext world, String name, String type,
                                double x, double y, double z, String nbt, String also) {
        Scene.command(world, "tp @a " + x + " " + y + " " + (z - 4) + " 0 0");
        Scene.command(world, "summon " + type + " " + x + " " + y + " " + z + " " + nbt);
        context.waitTicks(10);
        Scene.command(world, "ride @p mount @e[type=" + type + ",limit=1,sort=nearest]");
        context.waitTicks(20);
        boolean riding = context.computeOnClient(minecraft -> minecraft.player.getVehicle() != null);
        if (!riding) throw new AssertionError("restingmount: the camera could not ride the " + type);
        clientCommand(context, "livinghorizon debug offline test spawn " + name);
        if (also != null) clientCommand(context, also);
        context.waitTicks(5);
        Scene.command(world, "ride @p dismount");
        Scene.command(world, "tp @e[type=" + type + "] ~ -200 ~");
        Scene.command(world, "kill @e[type=" + type + "]");
        context.waitTicks(10);
        String kept = rider(context, name, p -> p.mount() != null ? null : "no mount");
        if (kept != null) throw new AssertionError("restingmount " + type + ": " + kept);
        Scene.log("restingmount " + name + " on a " + type);
    }

    /**
     * Fails the run unless the mount gets at least 3 blocks from ({@code x}, {@code z}) within
     * {@code ticks}, the rider on it all the way, never more than a ride's worth in 10 ticks,
     * and, for a boat, at the water's surface over {@code water}.
     */
    private static void roams(ClientGameTestContext context, String name, double x, double z, int ticks, double water) {
        Vec3 last = rider(context, name, p -> p.mount().position());
        double farthest = 0;
        for (int i = 0; i < ticks / 10; i++) {
            context.waitTicks(10);
            Vec3 now = rider(context, name, p -> p.mount().position());
            double across = Math.hypot(now.x - last.x, now.z - last.z);
            if (across > 2) throw new AssertionError("restingmount " + name + ": moved " + across + " blocks in 10 ticks");
            String seated = rider(context, name, p -> {
                Entity mount = p.mount();
                if (p.look() != RestingPuppet.Look.RIDE || mount == null) return "off its mount";
                return p.position().distanceTo(mount.position()) < 6 ? null : "rider " + p.position() + " far from " + mount.position();
            });
            if (seated != null) throw new AssertionError("restingmount " + name + ": " + seated);
            if (!Double.isNaN(water) && (now.y < water || now.y > water + 1.2)) {
                throw new AssertionError("restingmount " + name + ": off the water, y=" + now.y);
            }
            farthest = Math.max(farthest, Math.hypot(now.x - x, now.z - z));
            last = now;
            if (i % 10 == 0) Scene.log("restingmount " + name + " " + now + " " + rider(context, name, RestingPuppet::state));
        }
        Scene.log("restingmount " + name + ": " + farthest + " blocks from where it was left");
        if (farthest < 3) throw new AssertionError("restingmount " + name + ": stayed within " + farthest + " blocks");
    }

    /** Stands the camera 12 blocks from the test player, on the ground, facing them, for the screenshot. */
    private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, String name) {
        lookAt(context, world, name, GROUND);
    }

    /** The same, standing at {@code floor}: on a glass block placed there when it is above the ground. */
    private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, String name, int floor) {
        Vec3 at = rider(context, name, p -> p.position());
        Vec3 from = context.computeOnClient(minecraft -> minecraft.player.position());
        double dx = at.x - from.x, dz = at.z - from.z;
        double across = Math.max(1e-3, Math.hypot(dx, dz));
        double x = at.x - dx / across * 12, z = at.z - dz / across * 12, eye = floor + 1.62;
        float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - x), at.z - z));
        float pitch = (float) -Math.toDegrees(Math.atan2(at.y + 1 - eye, 12));
        if (floor > GROUND) Scene.command(world, "setblock " + (int) Math.floor(x) + " " + (floor - 1) + " " + (int) Math.floor(z) + " glass");
        Scene.command(world, "tp @a " + x + " " + floor + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(20);
    }

    private static void clientCommand(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingmount: no test player " + name);
    }

    private static <T> T rider(ClientGameTestContext context, String name, Function<RestingPuppet, T> read) {
        return context.computeOnClient(minecraft -> read.apply(puppet(minecraft, name)));
    }
}
