package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.compat.Links;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Distant mobs behind Lodistance's terrain (Fabric 26.3, its jar in {@code -Plh.mods}; with
 * {@code --sodium} it draws into the game's depth, without Sodium into a texture of its own). A
 * stone wall and a row of cows behind it; the player walks away until the wall is Lodistance's.
 * With <i>Hide Behind Far Terrain</i> on, every cow is behind the wall and hidden; off, as
 * Lodistance's depth alone leaves them, they show through it. Its world is read too: the wall's
 * height over the ground, and the wall straight ahead; and the cows' fog is Lodistance's. Without
 * Lodistance it says so.
 */
public final class LodistanceScenario implements Scenario {
    private static final String[] MOBS = {"cow"};
    private static final int COUNT = 12;

    @Override
    public String name() {
        return "lodistance";
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("reading Lodistance's world failed", e);
        }
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded("lodistance")) {
            Scene.log("lodistance skipped: Lodistance is not installed (its jar in -Plh.mods)");
            return;
        }
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(8);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.onlyOverTerrain = false;
            config.depthOcclusion = true;
            config.hideOccludedMobs = true;
            config.optOcclusionQueries = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // The wall 60 blocks ahead, the cows 20 blocks behind it.
            Scene.command(world, "execute at @p run fill ~-40 ~-1 ~60 ~40 ~24 ~62 stone");
            Scene.row(world, MOBS, COUNT, 80, 5.0);
            context.waitTicks(60);
            // Lodistance took these chunks as they loaded, before the wall: it reads them again
            // from the saved world.
            Scene.command(world, "save-all flush");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("lodistance reimport"));
            context.waitTicks(200);
            // Walk away: the wall is Lodistance's past the render distance, the cows the mod's copies.
            Scene.command(world, "tp @a 0 ~ -200 0 0");
            context.waitTicks(400);
            for (Links.Report link : context.computeOnClient(minecraft -> Links.report())) {
                Scene.log("lodistance link " + link.mod() + ": " + link.status());
            }
            Scene.log("lodistance source: " + context.computeOnClient(minecraft -> LodWorld.status() + " " + LodWorld.details()));
            // Its world, read directly: the top of the wall, the ground behind it, the wall ahead.
            int wallTop = await(context.computeOnClient(minecraft -> LodWorld.surface(0, 61)))
                    .map(LodWorld.Surface::y).orElse(Integer.MIN_VALUE);
            int fieldTop = await(context.computeOnClient(minecraft -> LodWorld.surface(0, 100)))
                    .map(LodWorld.Surface::y).orElse(Integer.MIN_VALUE);
            double ahead = await(context.computeOnClient(minecraft ->
                    LodWorld.probe(minecraft.player.getEyePosition(), new Vec3(0, 0, 1), 1024)));
            Scene.log("lodistance ground: wall top " + wallTop + ", field " + fieldTop + ", wall ahead at " + ahead
                    + "; " + context.computeOnClient(minecraft -> LodWorld.details()));
            if (wallTop - fieldTop != 25) throw new AssertionError("Lodistance's wall " + wallTop + " over ground " + fieldTop + ", not 25 blocks");
            if (!(Math.abs(ahead - 260) < 3)) throw new AssertionError("Lodistance's wall seen " + ahead + " blocks ahead, not 260");

            // Without Sodium on 26.3, Lodistance draws nothing when the game takes its indirect chunk
            // path (its hook misses the camera): nothing to hide behind then.
            String drawing = context.computeOnClient(minecraft -> Links.report().stream()
                    .filter(link -> link.mod().equals("Lodistance")).findFirst().map(Links.Report::status).orElse(""));
            if (!drawing.endsWith(", drawing")) {
                Scene.log("lodistance depth not checked: Lodistance draws nothing here (" + drawing + ")");
                return;
            }
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            Scene.log("lodistance remembered shown=" + shown);
            if (shown < COUNT) throw new AssertionError("Only " + shown + " of " + COUNT + " cows remembered");

            context.waitTicks(40);
            int merged = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            Scene.log("lodistance merged: " + merged + " hidden; depth " + context.computeOnClient(minecraft -> FarDepth.state()));
            Scene.screenshot(context, this, "merged");
            Scene.configure(context, config -> config.debugDepthView = 1);
            Scene.screenshot(context, this, "merged-depth");
            Scene.configure(context, config -> config.debugDepthView = 0);

            Scene.configure(context, config -> config.depthOcclusion = false);
            context.waitTicks(40);
            int alone = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            Scene.log("lodistance Lodistance's depth alone: " + alone + " hidden");
            Scene.configure(context, config -> config.hideOccludedMobs = false);
            context.waitTicks(20);
            Scene.screenshot(context, this, "alone-drawn");
            Scene.configure(context, config -> config.depthOcclusion = true);
            context.waitTicks(20);
            Scene.screenshot(context, this, "merged-drawn");

            // The cows get Lodistance's fog, without the game's atmospheric fog its terrain does not have.
            String fog = context.computeOnClient(minecraft -> FarFog.status());
            Scene.log("lodistance fog: " + fog);
            if (merged != COUNT) throw new AssertionError("Behind Lodistance's wall " + merged + " cows hidden, not " + COUNT);
            if (!fog.startsWith("Lodistance's")) throw new AssertionError("The cows' fog is not Lodistance's: " + fog);
        } finally {
            Scene.configure(context, config -> {
                config.depthOcclusion = true;
                config.debugDepthView = 0;
            });
            context.runOnClient(minecraft -> {
                minecraft.options.entityDistanceScaling().set(1.0);
                minecraft.options.renderDistance().set(12);
            });
        }
    }
}
