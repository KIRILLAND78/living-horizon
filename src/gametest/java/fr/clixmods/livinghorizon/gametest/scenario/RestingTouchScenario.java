package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * A player who logged off, near the camera, and you: walk into it and it is shoved out of your
 * way, while you are never pushed back.
 */
public final class RestingTouchScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NAME = "Touched";

    @Override
    public String name() {
        return "restingtouch";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(10);
            command(context, "livinghorizon debug offline test spawn " + NAME);
            Scene.command(world, "tp @a 0.5 " + GROUND + " -4.5 0 0");
            context.waitTicks(40);
            push(context, world);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /** You step right into it: it is shoved aside, you stay where you are. */
    private void push(ClientGameTestContext context, TestSingleplayerContext world) {
        Vec3 at = read(context, RestingPuppet::position);
        double yx = at.x + 0.2, yz = at.z;
        Scene.command(world, "tp @a " + yx + " " + GROUND + " " + yz + " 90 0");
        context.waitTicks(10);
        Vec3 now = read(context, RestingPuppet::position);
        Vec3 you = context.computeOnClient(minecraft -> minecraft.player.position());
        double apart = Math.hypot(now.x - you.x, now.z - you.z);
        Scene.log("restingtouch pushed: from " + at + " to " + now + ", you at " + you + ", " + apart + " apart");
        if (apart < 0.5) throw new AssertionError("restingtouch: not shoved aside, " + apart + " blocks from you");
        if (Math.hypot(you.x - yx, you.z - yz) > 0.05) throw new AssertionError("restingtouch: you were pushed to " + you);
        Scene.screenshot(context, this, "pushed");
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    private static RestingPuppet puppet(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(NAME) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingtouch: no test player");
    }

    private static <T> T read(ClientGameTestContext context, Function<RestingPuppet, T> read) {
        return context.computeOnClient(minecraft -> read.apply(puppet(minecraft)));
    }
}
