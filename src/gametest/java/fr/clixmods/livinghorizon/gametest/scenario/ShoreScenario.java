package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/**
 * Players who logged off in water are never left standing in it. One logs off swimming in a
 * pond: they sit on its bank, then, living, stay on dry land. One logs off in the middle of a
 * lake, more than 10 blocks from any shore: they get a boat, rowed about the lake while they
 * live, waiting on their spot while they sit; the lake drained, the boat is gone. One placed
 * in the air above the lake lands on its shore. Seen from 100 blocks away, where they play
 * their loop, they stay in the boat and on the shore.
 */
public final class ShoreScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "shore";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A pond five blocks across and two deep, someone swimming in its middle.
            Scene.command(world, "fill 4 " + (GROUND - 2) + " 8 8 " + (GROUND - 1) + " 12 water");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 0.5 0 20");
            context.waitTicks(20);
            fake(context, "LH_Swimmer", 6.5, GROUND - 1.2, 10.5);
            context.waitTicks(40);
            check(context, "pond seated", "LH_Swimmer", false, 4);
            Scene.screenshot(context, this, "pond-seated");

            Scene.configure(context, config -> config.offlineLife = true);
            context.waitTicks(60);
            check(context, "pond living", "LH_Swimmer", false, 12);
            Scene.screenshot(context, this, "pond-living");
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());

            // A lake 26 blocks across: no shore within 10 blocks of its middle.
            Scene.command(world, "fill -6 " + (GROUND - 2) + " 20 19 " + (GROUND - 1) + " 45 water");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 16.5 0 15");
            context.waitTicks(20);
            fake(context, "LH_Rower", 6.5, GROUND - 1.2, 32.5);
            context.waitTicks(40);
            check(context, "lake living", "LH_Rower", true, 12);
            // Placed in the air above the lake, near its edge: what they land on is water, so the shore.
            fake(context, "LH_Jumper", -3.5, GROUND + 5, 32.5);
            context.waitTicks(60);
            check(context, "jumper", "LH_Jumper", false, 10);
            Scene.screenshot(context, this, "lake-living");

            // Away from them, past where they live their life: they play their loop, still in
            // the boat and still out of the water.
            Scene.command(world, "tp @a 6.5 " + GROUND + " 132.5 180 10");
            for (int i = 0; i < 10; i++) {
                context.waitTicks(20);
                check(context, "lake far " + i, "LH_Rower", true, 12);
                check(context, "jumper far " + i, "LH_Jumper", false, 24);
            }
            Scene.screenshot(context, this, "lake-far");
            // Zoomed in on them, as models (within the pictures' distance) and as pictures (past it).
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            for (int d : new int[]{90, 160}) {
                Scene.command(world, "tp @a 6.5 " + (GROUND + 2) + " " + (32.5 + d) + " 180 3");
                context.waitTicks(60);
                context.runOnClient(minecraft -> {
                    RestingPuppet rower = puppet(minecraft, "LH_Rower");
                    Scene.log("shore far zoom " + d + ": look " + rower.look() + ", mount " + rower.mount() + ", away "
                            + rower.away() + ", pictured " + fr.clixmods.livinghorizon.render.GhostRenderer.pictured(rower));
                });
                Scene.screenshot(context, this, "lake-zoom-" + d);
            }
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            // From above, figures drawn bigger than they are: the water round the boat stays.
            Scene.configure(context, config -> config.minApparentPixels = 120);
            Scene.command(world, "gamemode spectator @a");
            Scene.command(world, "tp @a 6.5 " + (GROUND + 30) + " -20.5 0 35");
            context.waitTicks(40);
            Scene.configure(context, config -> config.offlinePlayers = false);
            context.waitTicks(10);
            Scene.screenshot(context, this, "lake-above-without");
            Scene.configure(context, config -> config.offlinePlayers = true);
            context.waitTicks(10);
            Scene.screenshot(context, this, "lake-bigger");
            Scene.configure(context, config -> config.minApparentPixels = 0);
            Scene.command(world, "gamemode creative @a");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 16.5 0 15");
            context.waitTicks(40);
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
            fake(context, "LH_Rower", 6.5, GROUND - 1.2, 32.5);
            context.waitTicks(40);

            Scene.configure(context, config -> config.offlineLife = false);
            context.waitTicks(40);
            check(context, "lake seated", "LH_Rower", true, 0.6);
            Scene.screenshot(context, this, "lake-seated");

            // Drained: out of the boat, on the ground.
            Scene.command(world, "fill -6 " + (GROUND - 2) + " 20 19 " + (GROUND - 1) + " 45 air");
            context.waitTicks(60);
            check(context, "lake drained", "LH_Rower", false, 0.5);
        }
    }

    private static void fake(ClientGameTestContext context, String name, double x, double y, double z) {
        context.runOnClient(minecraft -> FarPlayerTracker.get().resting().fake(name, minecraft.player.getGameProfile(),
                SharedPositions.dimensionId(minecraft.level.dimension()), x, y, z, 180f, 0, null, null));
    }

    /**
     * Fails the run unless the player is in a boat (or on dry land, out of the water), within
     * {@code within} blocks across of where they logged off.
     */
    private static void check(ClientGameTestContext context, String step, String name, boolean boat, double within) {
        String wrong = context.computeOnClient(minecraft -> {
            RestingPuppet puppet = puppet(minecraft, name);
            RestingPlayers.Spot spot = spot(name);
            double off = Math.hypot(puppet.getX() - spot.x(), puppet.getZ() - spot.z());
            boolean wet = minecraft.level.getFluidState(BlockPos.containing(puppet.getX(), puppet.getY() + 0.05,
                    puppet.getZ())).is(FluidTags.WATER);
            boolean inBoat = puppet.mount() != null && puppet.look() == RestingPuppet.Look.RIDE;
            Scene.log("shore " + step + ": " + puppet.position() + ", " + off + " blocks off, boat " + inBoat
                    + ", wet " + wet + ", look " + puppet.look() + ", " + puppet.state());
            if (boat != inBoat) return "boat " + inBoat;
            if (!boat && wet) return "in the water";
            if (off > within) return off + " blocks from their spot";
            return null;
        });
        if (wrong != null) throw new AssertionError("shore " + step + ": " + wrong);
    }

    private static RestingPlayers.Spot spot(String name) {
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (spot.name().equals(name)) return spot;
        }
        throw new AssertionError("shore: no " + name);
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        return FarPlayerTracker.get().resting().puppet(minecraft.level, spot(name));
    }
}
