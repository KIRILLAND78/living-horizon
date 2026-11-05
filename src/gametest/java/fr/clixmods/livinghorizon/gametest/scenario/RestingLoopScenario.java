package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Players who logged off, away from the camera, play a loop instead of living their life: a
 * test player on foot and one on a horse, the camera 100 blocks off. Each walks the loop
 * around where it was when the camera left, never back to its spot, never farther than the
 * widest animation goes, never jumping, and comes back to its life once the camera is near again.
 */
public final class RestingLoopScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    /** Blocks from its spot the widest animation goes. */
    private static final double LOOP = 7;

    @Override
    public String name() {
        return "restingloop";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(10);
            command(context, "livinghorizon debug offline test spawn Walker");
            Scene.command(world, "summon horse 10.5 " + GROUND + " 0.5 {Tame:1b,NoAI:1b,"
                    + "SaddleItem:{id:\"minecraft:saddle\",count:1},equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
            Scene.command(world, "tp @a 10.5 " + GROUND + " -2 0 0");
            context.waitTicks(10);
            Scene.command(world, "ride @p mount @e[type=horse,limit=1,sort=nearest]");
            context.waitTicks(20);
            command(context, "livinghorizon debug offline test spawn Horseman");
            context.waitTicks(5);
            Scene.command(world, "ride @p dismount");
            Scene.command(world, "tp @e[type=horse] ~ -200 ~");
            Scene.command(world, "kill @e[type=horse]");

            // Near: their life.
            Scene.command(world, "tp @a 5.5 " + GROUND + " -10 0 10");
            context.waitTicks(40);
            for (String name : new String[]{"Walker", "Horseman"}) {
                if (read(context, name, RestingPuppet::away)) throw new AssertionError("restingloop " + name + ": away while near");
            }

            // Away: their loop.
            Scene.command(world, "tp @a 5.5 " + GROUND + " -100 0 10");
            loops(context, "Walker", 0.5, 0.5, false);
            loops(context, "Horseman", 10.5, 0.5, true);
            Scene.screenshot(context, this, "away");

            // Near again: their life again.
            Scene.command(world, "tp @a 5.5 " + GROUND + " -10 0 10");
            context.waitTicks(20);
            for (String name : new String[]{"Walker", "Horseman"}) {
                if (read(context, name, RestingPuppet::away)) throw new AssertionError("restingloop " + name + ": still away when near");
            }
            Scene.log("restingloop back near: living again");
            Scene.screenshot(context, this, "near");
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /**
     * Fails the run unless the test player plays its loop: away, moving, never more than a
     * walk's worth in 10 ticks, and within {@link #LOOP} blocks of where it was when it went away.
     * ({@code x}, {@code z}) is its spot, for the log.
     */
    private static void loops(ClientGameTestContext context, String name, double x, double z, boolean mounted) {
        Function<RestingPuppet, Vec3> where = p -> {
            Entity mount = p.mount();
            return mounted && mount != null ? mount.position() : p.position();
        };
        Vec3 last = read(context, name, where), start = last;
        double travelled = 0, farthest = 0;
        for (int i = 0; i < 60; i++) {
            context.waitTicks(10);
            if (!read(context, name, RestingPuppet::away)) throw new AssertionError("restingloop " + name + ": not away");
            if (mounted && read(context, name, p -> p.mount() == null)) throw new AssertionError("restingloop " + name + ": off its horse");
            Vec3 now = read(context, name, where);
            double across = Math.hypot(now.x - last.x, now.z - last.z);
            if (across > 1.6) throw new AssertionError("restingloop " + name + ": moved " + across + " blocks in 10 ticks");
            travelled += across;
            // Around where it was when you went away, never back to its spot.
            farthest = Math.max(farthest, Math.hypot(now.x - start.x, now.z - start.z));
            last = now;
            if (i % 10 == 0) Scene.log("restingloop " + name + " " + now + " " + read(context, name, RestingPuppet::state));
        }
        Scene.log("restingloop " + name + ": walked " + travelled + " blocks, at most " + farthest
                + " from where it was, " + Math.hypot(last.x - x, last.z - z) + " from its spot");
        if (travelled < 2) throw new AssertionError("restingloop " + name + ": walked only " + travelled + " blocks");
        if (farthest > LOOP) throw new AssertionError("restingloop " + name + ": " + farthest + " blocks from where it was");
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingloop: no test player " + name);
    }

    private static <T> T read(ClientGameTestContext context, String name, Function<RestingPuppet, T> read) {
        return context.computeOnClient(minecraft -> read.apply(puppet(minecraft, name)));
    }
}
