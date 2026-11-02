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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Where a player who logged off rests is kept, not found again each time. One logs off in the
 * middle of a lake and gets a boat; one logs off gliding 25 blocks above the ground and comes to
 * rest on it. Then the camera goes 600 blocks away, where the blocks of their spots are no longer
 * loaded, and their figures are made again (as when they leave the nearest drawn and come back):
 * still in the boat, still on the ground, not back in the water or the air. Last,
 * {@code /livinghorizon debug offline test glide} places gliders in an elytra, which land too.
 */
public final class RestKeptScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "restkept";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A lake 26 blocks across: no shore within 10 blocks of its middle.
            Scene.command(world, "fill -6 " + (GROUND - 2) + " 20 19 " + (GROUND - 1) + " 45 water");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 6.5 0 15");
            context.waitTicks(40);
            fake(context, "LH_Rower", 6.5, GROUND - 1.2, 32.5);
            fake(context, "LH_Glider", -12.5, GROUND + 25, 12.5);
            context.waitTicks(80);
            check(context, "near", true);
            Scene.screenshot(context, this, "near");

            // Far away, the blocks of their spots gone, their figures made again.
            Scene.command(world, "tp @a 6.5 " + GROUND + " 640.5 180 10");
            boolean loaded = true;
            for (int i = 0; i < 60 && loaded; i++) {
                context.waitTicks(20);
                loaded = context.computeOnClient(minecraft -> minecraft.level.getChunkSource().hasChunk(0, 2)
                        || minecraft.level.getChunkSource().hasChunk(-1, 0));
            }
            context.runOnClient(minecraft -> Scene.log("restkept far: player at " + minecraft.player.position()
                    + ", lake chunk " + minecraft.level.getChunkSource().hasChunk(0, 2) + ", render distance "
                    + minecraft.options.getEffectiveRenderDistance()));
            Scene.log("restkept far: spots loaded " + loaded);
            if (loaded) throw new AssertionError("restkept: the lake is still loaded 600 blocks away");
            remake(context);
            for (int i = 0; i < 5; i++) {
                context.waitTicks(20);
                check(context, "far " + i, false);
            }

            // Back: as they were.
            Scene.command(world, "tp @a 6.5 " + GROUND + " 6.5 0 15");
            context.waitTicks(100);
            remake(context);
            context.waitTicks(20);
            check(context, "back", true);

            // Gliders placed by the command: an elytra on, down on the ground below.
            context.runOnClient(minecraft -> minecraft.player.connection
                    .sendCommand("livinghorizon debug offline test glide 4 6 20"));
            context.waitTicks(80);
            String wrong = context.computeOnClient(minecraft -> {
                int gliders = 0;
                for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
                    if (!spot.name().startsWith("Sleeper")) continue;
                    gliders++;
                    RestingPuppet puppet = FarPlayerTracker.get().resting().puppet(minecraft.level, spot);
                    boolean elytra = puppet.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
                    Scene.log("restkept glider " + spot.name() + ": spot y " + spot.y() + ", at " + puppet.position()
                            + ", elytra " + elytra + ", rest " + spot.rest());
                    if (!elytra) return spot.name() + " has no elytra";
                    if (spot.y() < GROUND + 15) return spot.name() + " placed at " + spot.y();
                    if (puppet.getY() > GROUND + 2) return spot.name() + " still in the air at " + puppet.getY();
                }
                return gliders == 4 ? null : gliders + " gliders";
            });
            if (wrong != null) throw new AssertionError("restkept: " + wrong);
            // Stepped back to look at them, down on the grass.
            Scene.command(world, "tp @a 6.5 " + (GROUND + 1) + " -6.5 0 25");
            context.waitTicks(20);
            Scene.screenshot(context, this, "gliders");
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
        }
    }

    private static void fake(ClientGameTestContext context, String name, double x, double y, double z) {
        context.runOnClient(minecraft -> FarPlayerTracker.get().resting().fake(name, minecraft.player.getGameProfile(),
                SharedPositions.dimensionId(minecraft.level.dimension()), x, y, z, 180f, 0, null, null));
    }

    /** Their figures let go and made again: none drawn for a few ticks, then all of them. */
    private static void remake(ClientGameTestContext context) {
        Scene.configure(context, config -> config.maxOfflinePlayers = 0);
        context.waitTicks(5);
        Scene.configure(context, config -> config.maxOfflinePlayers = 256);
        context.waitTicks(5);
    }

    /** Fails the run unless the rower is in a boat and the glider down on the ground. */
    private static void check(ClientGameTestContext context, String step, boolean near) {
        String wrong = context.computeOnClient(minecraft -> {
            RestingPuppet rower = puppet(minecraft, "LH_Rower");
            RestingPuppet glider = puppet(minecraft, "LH_Glider");
            boolean boat = rower.mount() != null && rower.look() == RestingPuppet.Look.RIDE;
            Scene.log("restkept " + step + ": rower " + rower.position() + " boat " + boat + " (" + rower.state()
                    + "), glider " + glider.position() + " " + glider.look() + " (" + glider.state() + "), rests "
                    + spot("LH_Rower").rest() + " / " + spot("LH_Glider").rest());
            if (!boat) return "the rower has no boat";
            if (spot("LH_Rower").rest() == null || spot("LH_Glider").rest() == null) return "a rest not kept";
            // On the grass, sitting, lying or standing; never back up in the air.
            if (glider.getY() > GROUND + 0.5 || glider.getY() < GROUND - 0.5) return "the glider at " + glider.getY();
            return null;
        });
        if (wrong != null) throw new AssertionError("restkept " + step + ": " + wrong);
    }

    private static RestingPlayers.Spot spot(String name) {
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (spot.name().equals(name)) return spot;
        }
        throw new AssertionError("restkept: no " + name);
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        return FarPlayerTracker.get().resting().puppet(minecraft.level, spot(name));
    }
}
