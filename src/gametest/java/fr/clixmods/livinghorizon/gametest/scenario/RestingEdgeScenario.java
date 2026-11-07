package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Players who logged off, near you: one shoved off the edge of a tower falls to the ground
 * instead of standing in the air on the rim; and a crowd of them left alone never jumps a few
 * blocks aside, nor is made again where its player logged off, while you stand among them.
 */
public final class RestingEdgeScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    /** The tower: x and z from 0 to 4, its top this many blocks over the ground. */
    private static final int TOWER = 8;
    private static final String NAME = "Edge";

    @Override
    public String name() {
        return "restingedge";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            edge(context, world);
            crowd(context, world);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /** Spawned in the middle of the tower's top, then walked into towards its east edge, off it. */
    private void edge(ClientGameTestContext context, TestSingleplayerContext world) {
        int top = GROUND + TOWER;
        Scene.command(world, "fill 0 " + GROUND + " 0 4 " + (top - 1) + " 4 stone");
        Scene.command(world, "tp @a 3.5 " + top + " 2.5 -90 0");
        context.waitTicks(10);
        command(context, "livinghorizon debug offline test spawn " + NAME);
        Scene.command(world, "tp @a 1.5 " + top + " 2.5 -90 0");
        context.waitTicks(60);
        // You walk into it from the west, right behind it, stopping half a step short of the rim, as anyone walking up to it does.
        for (int i = 0; i < 80; i++) {
            Vec3 at = position(context, NAME);
            if (at == null) throw new AssertionError("restingedge: no test player");
            double you = Math.min(at.x - 0.4, 4.65);
            Scene.command(world, "tp @a " + you + " " + top + " " + at.z + " -90 0");
            context.waitTicks(1);
            if (i % 5 == 0) {
                Vec3 self = context.computeOnClient(minecraft -> minecraft.player.position());
                Scene.log("restingedge push " + i + ": it at " + at + ", you at " + self + ", " + state(context, NAME));
            }
        }
        Scene.log("restingedge pushed to: " + position(context, NAME) + ", " + state(context, NAME));
        Scene.command(world, "tp @a 1 " + top + " 2.5 -90 30");
        context.waitTicks(60);
        Vec3 at = position(context, NAME);
        Scene.log("restingedge shoved off the edge: at " + at + ", the top at " + top);
        Scene.screenshot(context, this, "edge");
        if (at == null || at.y > top - 0.5) throw new AssertionError("restingedge: still up on the tower, at " + at);
    }

    /** Ten of them around you, watched for twenty seconds: none jumps, none is made again. */
    private void crowd(ClientGameTestContext context, TestSingleplayerContext world) {
        command(context, "livinghorizon debug offline test clear");
        Scene.command(world, "fill 0 " + GROUND + " 0 4 " + (GROUND + TOWER) + " 4 air");
        Scene.command(world, "tp @a 40.5 " + GROUND + " 40.5 0 20");
        context.waitTicks(20);
        command(context, "livinghorizon debug offline test spawn Crowd 0 10 8");
        context.waitTicks(20);
        Map<String, Vec3> last = new HashMap<>();
        Map<String, RestingPuppet> made = new HashMap<>();
        int jumps = 0, remade = 0;
        for (int tick = 0; tick < 400; tick++) {
            Map<String, Object[]> now = context.computeOnClient(minecraft -> {
                Map<String, Object[]> seen = new HashMap<>();
                RestingPlayers resting = FarPlayerTracker.get().resting();
                for (RestingPlayers.Spot spot : resting.spots()) {
                    RestingPuppet puppet = resting.made(spot);
                    if (puppet != null) seen.put(spot.name(), new Object[]{puppet, puppet.position(), puppet.state()});
                }
                return seen;
            });
            for (Map.Entry<String, Object[]> each : now.entrySet()) {
                RestingPuppet puppet = (RestingPuppet) each.getValue()[0];
                Vec3 at = (Vec3) each.getValue()[1];
                RestingPuppet before = made.put(each.getKey(), puppet);
                Vec3 was = last.put(each.getKey(), at);
                if (before != null && before != puppet) {
                    remade++;
                    Scene.log("restingedge " + each.getKey() + " made again at tick " + tick + ": " + was + " -> " + at);
                }
                if (was != null && Math.hypot(at.x - was.x, at.z - was.z) > 1.0) {
                    jumps++;
                    Scene.log("restingedge " + each.getKey() + " jumped at tick " + tick + ": " + was + " -> " + at
                            + " (" + each.getValue()[2] + ")");
                }
            }
            context.waitTicks(1);
        }
        Scene.log("restingedge crowd: " + jumps + " jumps, " + remade + " made again, " + made.size() + " watched");
        if (made.isEmpty()) throw new AssertionError("restingedge: no crowd");
        if (jumps > 0 || remade > 0) {
            throw new AssertionError("restingedge: " + jumps + " jumps, " + remade + " made again");
        }
    }

    private static @Nullable Vec3 position(ClientGameTestContext context, String name) {
        return context.computeOnClient(minecraft -> {
            RestingPlayers resting = FarPlayerTracker.get().resting();
            for (RestingPlayers.Spot spot : resting.spots()) {
                if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot).position();
            }
            return null;
        });
    }

    private static String state(ClientGameTestContext context, String name) {
        return context.computeOnClient(minecraft -> {
            RestingPlayers resting = FarPlayerTracker.get().resting();
            for (RestingPlayers.Spot spot : resting.spots()) {
                if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot).state();
            }
            return "none";
        });
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }
}
