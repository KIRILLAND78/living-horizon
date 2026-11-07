package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The fog on the world's entities follows Voxy's, which stops growing past a distance of its
 * own, and nothing else changes. Voxy cannot run here: a limit stands in for it, at the camera,
 * so that the mobs 100 blocks away lose the little fog they had. Between the two screenshots
 * the mobs' pixels must come away from the fog colour, and every other pixel stay as it was:
 * the terrain and the sky keep the game's fog. With *Voxy Fog Fix* off, the mobs get the game's
 * fog back. From 1.21.6, where the overworld has a fog of its own past the render distance.
 */
public final class FarFogScenario implements Scenario {
    private static final String[] MOBS = {"cow", "horse", "villager", "wolf", "pig", "sheep"};
    /** The overworld's fog colour, #c0d8ff. */
    private static final int[] FOG = {0xC0, 0xD8, 0xFF};
    /** Pixels the mobs cover at 100 blocks, zoomed in: a few hundred. */
    private static final int MIN_CHANGED = 60;

    @Override
    public String name() {
        return "farfog";
    }

    @Override
    public void run(ClientGameTestContext context) {
        //? if >=1.21.6 {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(12);
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 1, 100, 3);
            context.waitTicks(100);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Path game = still(context, "game");
            context.runOnClient(minecraft -> FarFog.limitForTests(0));
            Path limited = Scene.screenshot(context, this, "limited");
            compare("models", game, limited);
            // Voxy Fog Fix off: the game's fog again, everywhere.
            Scene.configure(context, config -> config.optVoxyFog = false);
            Path off = Scene.screenshot(context, this, "off");
            int[] moved = differences(game, off);
            Scene.log("farfog switched off: pixels changed=" + (moved[0] + moved[2]));
            if (moved[0] + moved[2] > 0) {
                throw new AssertionError("Voxy Fog Fix off still changed " + (moved[0] + moved[2]) + " pixels");
            }

            // The same mobs as flat pictures: drawn with the world's entities, they get the fog too.
            context.runOnClient(minecraft -> FarFog.limitForTests(Float.NaN));
            Scene.configure(context, config -> {
                config.optVoxyFog = true;
                config.impostors = true;
                config.impostorDistance = 32;
            });
            context.waitTicks(100);
            Path pictures = still(context, "pictures");
            context.runOnClient(minecraft -> FarFog.limitForTests(0));
            compare("pictures", pictures, Scene.screenshot(context, this, "pictures-limited"));
            Scene.log("farfog status: " + context.computeOnClient(minecraft -> FarFog.status()));
        } finally {
            context.runOnClient(minecraft -> {
                FarFog.limitForTests(Float.NaN);
                fr.clixmods.livinghorizon.FarConfig config = fr.clixmods.livinghorizon.FarConfig.get();
                config.optVoxyFog = true;
                config.impostorDistance = new fr.clixmods.livinghorizon.FarConfig().impostorDistance;
                minecraft.options.fov().set(70);
                minecraft.options.entityDistanceScaling().set(1.0);
                minecraft.options.cloudStatus().set(CloudStatus.FANCY);
            });
        }
        //?} else {
        /*Scene.log("farfog skipped: before 1.21.6 the overworld has no fog past the render distance");
        *///?}
    }

    /**
     * A picture once the view holds still: from 1.21.11 the sky and the fog ease into their
     * colours after a world opens, and pictures are baked a few frames after they are asked for.
     */
    private Path still(ClientGameTestContext context, String name) {
        Path view = Scene.screenshot(context, this, name);
        for (int tries = 0; tries < 15; tries++) {
            context.waitTicks(20);
            Path again = Scene.screenshot(context, this, name);
            int[] moved = differences(view, again);
            view = again;
            if (moved[0] + moved[2] == 0) break;
            Scene.log("farfog view still changing: " + (moved[0] + moved[2]) + " pixels");
        }
        return view;
    }

    /** The mobs' pixels nearer the fog colour in the first, every other pixel the same in both. */
    static void compare(String what, Path game, Path limited) {
        int[] counts = differences(game, limited);
        int changed = counts[0], clearer = counts[1], elsewhere = counts[2];
        Scene.log("farfog " + what + ": pixels changed on the mobs=" + changed + " farther from the fog=" + clearer
                + " changed elsewhere=" + elsewhere);
        if (changed < MIN_CHANGED) throw new AssertionError("The mobs' fog did not change: " + changed + " pixels");
        if (clearer < changed * 9 / 10) {
            throw new AssertionError("The mobs did not come away from the fog: " + clearer + " of " + changed + " pixels");
        }
        if (elsewhere > 0) throw new AssertionError("The fog changed away from the mobs: " + elsewhere + " pixels");
    }

    /**
     * Pixels that differ between two screenshots: on the mobs, those of them farther from the
     * fog colour in the second, and everywhere else.
     */
    static int[] differences(Path first, Path second) {
        try {
            BufferedImage a = ImageIO.read(first.toFile()), b = ImageIO.read(second.toFile());
            int changed = 0, clearer = 0, elsewhere = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    int p = a.getRGB(x, y), q = b.getRGB(x, y);
                    int difference = 0;
                    for (int shift = 0; shift <= 16; shift += 8) {
                        difference = Math.max(difference, Math.abs((p >> shift & 0xFF) - (q >> shift & 0xFF)));
                    }
                    if (difference <= 1) continue;
                    // The mobs stand on the horizon, near the middle of the screen.
                    boolean onMobs = Math.abs(y - a.getHeight() / 2) < a.getHeight() / 6
                            && Math.abs(x - a.getWidth() / 2) < a.getWidth() / 3;
                    if (!onMobs) {
                        elsewhere++;
                        continue;
                    }
                    changed++;
                    if (fromFog(q) > fromFog(p)) clearer++;
                }
            }
            return new int[]{changed, clearer, elsewhere};
        } catch (IOException e) {
            throw new AssertionError("Reading the screenshots back failed", e);
        }
    }

    /** How far a colour is from the fog's. */
    private static int fromFog(int rgb) {
        return Math.abs((rgb >> 16 & 0xFF) - FOG[0]) + Math.abs((rgb >> 8 & 0xFF) - FOG[1]) + Math.abs((rgb & 0xFF) - FOG[2]);
    }
}
