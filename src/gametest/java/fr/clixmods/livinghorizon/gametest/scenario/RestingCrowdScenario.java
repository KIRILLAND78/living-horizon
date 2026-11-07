package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A crowd of players who logged off on rafts with a chest, more than the 128 sheets the players
 * once had room for, on a lake 150 blocks off, past the impostor distance: every one of them is
 * a picture, rider and raft together, none a model. Their puppets are made a few a frame, and
 * every one is there within a few seconds.
 */
public final class RestingCrowdScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final int CROWD = 150;

    @Override
    public String name() {
        return "restingcrowd";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "fill -24 " + (GROUND - 2) + " 126 24 " + (GROUND - 1) + " 174 water");
            Scene.command(world, "summon bamboo_chest_raft 0.5 " + (GROUND - 0.5) + " 150.5");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 147 0 0");
            context.waitTicks(20);
            Scene.command(world, "ride @p mount @e[type=bamboo_chest_raft,limit=1,sort=nearest]");
            context.waitTicks(20);
            // In two waves: a crowd is a hundred at most.
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd 100 22"));
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd " + (CROWD - 100) + " 22"));
            context.waitTicks(5);
            Scene.command(world, "ride @p dismount");
            Scene.command(world, "kill @e[type=bamboo_chest_raft]");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 5");

            // Made a few a frame, not all in one: every puppet is there within a few seconds.
            int waited = 0;
            long missing = -1;
            for (; waited < 400 && missing != 0; waited += 10) {
                context.waitTicks(10);
                missing = context.computeOnClient(minecraft -> {
                    RestingPlayers resting = FarPlayerTracker.get().resting();
                    return resting.shown().stream().filter(spot -> resting.made(spot) == null).count();
                });
            }
            Scene.log("restingcrowd: " + (missing == 0 ? "every puppet made" : missing + " puppets never made")
                    + " within " + waited + " ticks");
            if (missing != 0) throw new AssertionError("restingcrowd: " + missing + " puppets never made");
            context.waitTicks(Math.max(0, 60 - waited));

            List<String> models = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                models = context.computeOnClient(minecraft -> {
                    List<String> left = new ArrayList<>();
                    RestingPlayers resting = FarPlayerTracker.get().resting();
                    for (RestingPlayers.Spot spot : resting.spots()) {
                        if (!resting.isFake(spot) || minecraft.level == null) continue;
                        RestingPuppet puppet = resting.puppet(minecraft.level, spot);
                        if (!GhostRenderer.pictured(puppet)) {
                            left.add(spot.name() + " (" + puppet.look() + ", mount " + puppet.mount() + ", "
                                    + puppet.state() + ")");
                        }
                    }
                    return left;
                });
                if (models.isEmpty()) break;
                context.waitTicks(10);
            }
            Scene.log("restingcrowd: " + models.size() + " of " + CROWD + " still models " + models);
            Scene.screenshot(context, this, "crowd");
            if (!models.isEmpty()) throw new AssertionError("restingcrowd: still models: " + models);

            // "Max Offline": only the nearest are drawn, the others still remembered.
            context.runOnClient(minecraft -> FarConfig.get().maxOfflinePlayers = 40);
            context.waitTicks(5);
            String limited = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                List<RestingPlayers.Spot> shown = resting.shown();
                if (shown.size() != 40) return "shown " + shown.size() + " of 40";
                double eyeX = minecraft.player.getX(), eyeZ = minecraft.player.getZ();
                double farthestShown = 0;
                for (RestingPlayers.Spot spot : shown) {
                    farthestShown = Math.max(farthestShown, Math.hypot(spot.x() - eyeX, spot.z() - eyeZ));
                }
                for (RestingPlayers.Spot spot : resting.spots()) {
                    if (shown.contains(spot)) continue;
                    double distance = Math.hypot(spot.x() - eyeX, spot.z() - eyeZ);
                    if (distance < farthestShown - 1e-6) return spot.name() + " left out at " + distance + " blocks";
                }
                return resting.spots().size() >= CROWD ? null : "only " + resting.spots().size() + " remembered";
            });
            Scene.log("restingcrowd: limited to 40, " + (limited == null ? "the nearest" : limited));
            Scene.screenshot(context, this, "limited");
            context.runOnClient(minecraft -> FarConfig.get().maxOfflinePlayers = 256);
            if (limited != null) throw new AssertionError("restingcrowd: " + limited);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        }
    }
}
