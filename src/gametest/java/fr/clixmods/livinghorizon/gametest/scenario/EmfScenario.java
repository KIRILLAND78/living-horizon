package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.EntityModelFeatures;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Figures drawn far away play Entity Model Features' animations every frame (issue #331).
 *
 * <p>EMF plays an entity's animations once in {@code distance / 20} frames and keeps the last
 * pose in between: hundreds of blocks away the figures slid along with their limbs still, then
 * snapped. A pack of its own gives the cow a nodding head (EMF only animates what a pack
 * declares), and cows past the game's entity distance must be played by EMF. Run with
 * {@code --with entity-model-features --with entitytexturefeatures}; without EMF it does nothing.
 */
public final class EmfScenario implements Scenario {
    private static final String PACK = "lh-emf-test";

    @Override
    public String name() {
        return "emf";
    }

    @Override
    public void run(ClientGameTestContext context) {
        boolean loaded = FabricLoader.getInstance().isModLoaded("entity_model_features");
        Scene.log("emf loaded " + loaded);
        if (!loaded) return;
        context.runOnClient(minecraft -> {
            writePack(minecraft.getResourcePackDirectory().resolve(PACK));
            minecraft.getResourcePackRepository().reload();
            if (!minecraft.getResourcePackRepository().addPack("file/" + PACK)) {
                throw new AssertionError("The test pack was not found");
            }
            minecraft.options.updateResourcePacks(minecraft.getResourcePackRepository());
            // The game stops drawing a cow at about 35 blocks: past it the mod draws them.
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.fov().set(30);
        });
        context.waitFor(minecraft -> minecraft.getOverlay() == null);
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, new String[]{"cow"}, 5, 90, 6);
            context.waitTicks(40);
            long before = context.computeOnClient(minecraft -> EntityModelFeatures.played());
            context.waitTicks(60);
            long played = context.computeOnClient(minecraft -> EntityModelFeatures.played()) - before;
            Scene.log("emf figures played instead of skipped in 60 ticks: " + played);
            Scene.screenshot(context, this, "cows");
            if (played == 0) {
                throw new AssertionError("EMF never asked to skip a far cow's animations: the hook or the pack is not in place");
            }
        }
    }

    /** A cow nodding its head: EMF's own format, written here rather than taken from a pack. */
    private static void writePack(Path root) {
        try {
            Path cem = root.resolve("assets/minecraft/optifine/cem");
            Files.createDirectories(cem);
            Files.writeString(root.resolve("pack.mcmeta"),
                    "{\"pack\":{\"description\":\"Living Horizon EMF test\",\"pack_format\":1}}");
            Files.writeString(cem.resolve("cow.jem"), """
                    {
                      "textureSize": [64, 32],
                      "models": [
                        {
                          "part": "head",
                          "id": "head",
                          "attach": true,
                          "animations": [
                            {"head.rx": "sin(age * 0.4) * 0.6"}
                          ]
                        }
                      ]
                    }
                    """);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
