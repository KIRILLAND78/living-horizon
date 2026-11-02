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
 * Players who logged off, near the camera, and you: walk into one and it is shoved out of your
 * way, while you are never pushed back. Three along a pond: one of them goes fishing, never two
 * at once, so close together.
 */
public final class RestingTouchScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NAME = "Touched";
    private static final int ANGLERS = 3;

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
            fishing(context, world);
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

    /** A pond with three of them on its edge: in time one fishes, and never two at once. */
    private void fishing(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "fill -10 " + (GROUND - 3) + " 8 10 " + (GROUND - 1) + " 18 water");
        for (int i = 0; i < ANGLERS; i++) {
            Scene.command(world, "tp @a " + (i * 5 - 4.5) + " " + GROUND + " 6.5 0 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Angler" + i);
        }
        Scene.command(world, "tp @a 0.5 " + GROUND + " -6.5 0 20");
        int most = 0, until = 400;
        for (int i = 0; i < until; i++) {
            context.waitTicks(10);
            int fishing = context.computeOnClient(minecraft -> {
                int count = 0;
                RestingPlayers resting = FarPlayerTracker.get().resting();
                for (RestingPlayers.Spot spot : resting.spots()) {
                    if (minecraft.level != null && resting.puppet(minecraft.level, spot).hook() != null) count++;
                }
                return count;
            });
            if (fishing > 1) throw new AssertionError("restingtouch: " + fishing + " fishing side by side");
            if (fishing == 1 && most == 0) {
                Scene.log("restingtouch fishing after " + i * 10 + " ticks");
                context.waitTicks(20);
                Scene.screenshot(context, this, "fishing");
                // Watched a while longer: still never two.
                until = Math.min(until, i + 60);
            }
            most = Math.max(most, fishing);
        }
        if (most == 0) throw new AssertionError("restingtouch: nobody went fishing");
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
