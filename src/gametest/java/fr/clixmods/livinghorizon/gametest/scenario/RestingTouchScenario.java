package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Players who logged off, near the camera, and you: walk into one and it is shoved out of your
 * way, while you are never pushed back; look at it and it looks back, and crouches when you do. Three along a pond: one of them goes fishing, never two
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
            gaze(context, world);
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

    /**
     * You look at it from 10 blocks: it pays no attention. From 5: it turns to you, then
     * crouches when you crouch, and stands up with you.
     */
    private void gaze(ClientGameTestContext context, TestSingleplayerContext world) {
        Vec3[] aimed = {null};
        for (int i = 0; i < 10; i++) {
            aim(context, world, aimed, 10);
            // The first two seconds, it may still be looking at you from when it was pushed.
            if (i >= 4 && read(context, RestingPuppet::state).startsWith("looking at you")) {
                throw new AssertionError("restingtouch: looked back from 10 blocks away");
            }
        }
        aimed[0] = null;
        boolean facing = false;
        for (int i = 0; i < 60 && !facing; i++) {
            aim(context, world, aimed, 5);
            facing = Math.abs(Mth.wrapDegrees(read(context, p -> p.yHeadRot) - 180f)) < 20;
        }
        Scene.log("restingtouch looked at: " + read(context, RestingPuppet::state));
        Scene.screenshot(context, this, "looked-at");
        if (!facing) throw new AssertionError("restingtouch: never looked back, head at " + read(context, p -> p.yHeadRot));

        context.getInput().holdKey(options -> options.keyShift);
        boolean down = false;
        for (int i = 0; i < 60 && !down; i++) {
            aim(context, world, aimed, 5);
            down = read(context, RestingPuppet::crouched);
        }
        Scene.log("restingtouch you crouch: " + read(context, RestingPuppet::state));
        Scene.screenshot(context, this, "crouched");
        context.getInput().releaseKey(options -> options.keyShift);
        if (!down) throw new AssertionError("restingtouch: never crouched with you");
        context.waitTicks(10);
        if (read(context, RestingPuppet::crouched)) throw new AssertionError("restingtouch: still crouched once you stood up");
    }

    /**
     * Wherever it goes, you keep it under your crosshair, {@code blocks} north of it, facing
     * south a little down, at its chest; then 10 ticks.
     */
    private void aim(ClientGameTestContext context, TestSingleplayerContext world, Vec3[] aimed, double blocks) {
        Vec3 at = read(context, RestingPuppet::position);
        if (aimed[0] == null || aimed[0].distanceTo(at) > 0.3) {
            float pitch = (float) Math.toDegrees(Math.atan2(0.5, blocks));
            Scene.command(world, "tp @a " + at.x + " " + GROUND + " " + (at.z - blocks) + " 0 " + pitch);
            aimed[0] = at;
        }
        context.waitTicks(10);
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
            if (i % 50 == 0) {
                Scene.log("restingtouch anglers " + i * 10 + ": " + context.computeOnClient(minecraft -> {
                    StringBuilder line = new StringBuilder();
                    RestingPlayers resting = FarPlayerTracker.get().resting();
                    for (RestingPlayers.Spot spot : resting.spots()) {
                        RestingPuppet puppet = resting.puppet(minecraft.level, spot);
                        line.append(spot.name()).append(' ').append(puppet.blockPosition().toShortString()).append(' ')
                                .append(puppet.state()).append("; ");
                    }
                    return line.toString();
                }));
            }
            if (fishing > 1) throw new AssertionError("restingtouch: " + fishing + " fishing side by side");
            if (fishing == 1 && most == 0) {
                Scene.log("restingtouch fishing after " + i * 10 + " ticks");
                beside(context, world);
                context.waitTicks(20);
                Scene.screenshot(context, this, "fishing");
                // Watched a while longer: still never two.
                until = Math.min(until, i + 60);
            }
            most = Math.max(most, fishing);
        }
        if (most == 0) throw new AssertionError("restingtouch: nobody went fishing");
    }

    /** You, a few blocks to the side of the one fishing, looking at it and its line. */
    private static void beside(ClientGameTestContext context, TestSingleplayerContext world) {
        Vec3[] fisher = context.computeOnClient(minecraft -> {
            RestingPlayers resting = FarPlayerTracker.get().resting();
            for (RestingPlayers.Spot spot : resting.spots()) {
                RestingPuppet puppet = resting.puppet(minecraft.level, spot);
                if (puppet.hook() != null) return new Vec3[]{puppet.position(), puppet.hook().position()};
            }
            return null;
        });
        if (fisher == null) return;
        Vec3 from = fisher[0], to = fisher[1];
        Vec3 along = to.subtract(from).multiply(1, 0, 1).normalize();
        Vec3 you = from.add(-along.z * 4, 0, along.x * 4).subtract(along);
        Vec3 middle = from.add(to).scale(0.5).add(0, 0.8, 0);
        double dx = middle.x - you.x, dz = middle.z - you.z, dy = middle.y - (you.y + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        Scene.command(world, "tp @a " + you.x + " " + from.y + " " + you.z + " " + yaw + " " + pitch);
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
