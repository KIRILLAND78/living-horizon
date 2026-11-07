package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.IrisNormals;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Impostors lit by a shader pack that reads normal maps: a row of mobs 50 blocks away, past the game's entity distance and close
 * enough for the pack's shadows, as models, as impostors baked the plain way, then baked with
 * their normal maps ({@code impostorShading}), at three times of the day. Turns on Photon's
 * <em>Normal Mapping</em> first, when the pack is Photon.
 *
 * <p>Needs a graphics card for Photon: run it on a real machine with Sodium and Iris in
 * {@code -Plh.mods} and the pack in {@code -Plh.shaderpack}.
 */
public final class ImpostorShadingScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "villager", "horse", "pig", "slime"};

    @Override
    public String name() {
        return "impostorshading";
    }

    @Override
    public void run(ClientGameTestContext context) {
        photon = normalMapping(context);
        // Half the entity distance: the game stops drawing the mobs, the mod draws them.
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = false;
            config.impostorShading = false;
            config.impostorDistance = 16;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 1, 50, 4.0);
            context.waitTicks(60);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            for (String time : new String[]{"noon", "500", "11500"}) {
                Scene.command(world, "time set " + time);
                shot(context, "models", time, false, false);
                shot(context, "plain", time, true, false);
                shot(context, "shaded", time, true, true);
            }
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        }
        Scene.configure(context, config -> config.impostorShading = true);
        if (photon && context.computeOnClient(minecraft -> IrisNormals.given()) == 0) {
            throw new AssertionError("Photon reads normal maps, but Iris was never handed the impostors'");
        }
    }

    /** Whether the pack is Photon, its Normal Mapping turned on by {@link #normalMapping}. */
    private boolean photon;

    private void shot(ClientGameTestContext context, String name, String time, boolean impostors, boolean shading) {
        Scene.configure(context, config -> {
            config.impostors = impostors;
            config.impostorShading = shading;
        });
        if (impostors) {
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                        ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                    }
                }
            });
            // A switch of the setting throws the pictures away on the next frame: baked again after.
            context.waitTicks(5);
            context.waitFor(minecraft -> ImpostorAtlas.relief() == shading && ImpostorAtlas.sheets() >= MOBS.length
                    || ImpostorAtlas.failed(), 2000);
        }
        // Shader packs blend a few frames together: some time to settle.
        context.waitTicks(30);
        Scene.screenshot(context, this, time + "-" + name);
        context.runOnClient(minecraft -> Scene.log("impostorshading " + time + "-" + name + " relief="
                + ImpostorAtlas.relief() + " impostors=" + PolygonStats.impostors() + " models=" + PolygonStats.models()
                + " sheets=" + ImpostorAtlas.sheets() + " normal maps asked/given=" + IrisNormals.counts()));
    }

    /** Photon reads normal maps only with its Normal Mapping on: turned on, the pack reloaded. */
    private static boolean normalMapping(ClientGameTestContext context) {
        String folder = System.getProperty("livinghorizon.shaderpack", "");
        if (folder.isBlank() || !Path.of(folder).getFileName().toString().toLowerCase().contains("photon")) return false;
        context.runOnClient(minecraft -> {
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                Path packs = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
                Files.writeString(packs.resolve(Path.of(folder).getFileName() + ".txt"), "NORMAL_MAPPING=true\n");
                iris.getMethod("reload").invoke(null);
                Scene.log("impostorshading Photon's Normal Mapping on");
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new IllegalStateException("Could not turn Photon's Normal Mapping on", e);
            }
        });
        context.waitTicks(60);
        return true;
    }
}
