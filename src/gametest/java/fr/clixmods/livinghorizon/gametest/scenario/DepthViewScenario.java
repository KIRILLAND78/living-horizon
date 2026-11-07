package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The depth view, bottom right: the game's depth copied and shaded, near white to far black,
 * the sky in blue. A view all white is a copy the driver never gave a size to. Every OpenGL
 * program of the far terrain's depth is compiled too, and the shaders of 26.3's GPU path read
 * as GLSL: Voxy and Distant Horizons cannot run here, and their programs would go untried.
 * The fourth picture, the whole world drawn (what Ecstatic adds after the entities), is copied
 * at the end of the world: it must show the same sky and ground as the first.
 */
public final class DepthViewScenario implements Scenario {
    @Override
    public String name() {
        return "depthview";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.debugDepthView = 1);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 8, 0);
            context.runOnClient(minecraft -> minecraft.player.setXRot(-5));
            Path game = Scene.screenshotFrame(context, this, "game");
            Scene.configure(context, config -> config.debugDepthView = 4);
            context.waitTicks(5);
            Path whole = Scene.screenshotFrame(context, this, "whole");
            Scene.configure(context, config -> config.debugDepthView = 1);
            int[] first = view(game), last = view(whole);
            Scene.log("depth view, game: " + first[0] + " sky and " + first[1] + " ground pixels of " + first[2]
                    + "; whole world: " + last[0] + " and " + last[1]);
            if (last[0] * 20 < last[2] || last[1] * 20 < last[2]) {
                throw new AssertionError("the whole world's depth view is not drawn: " + last[0] + " sky, "
                        + last[1] + " ground pixels of " + last[2]);
            }
            context.runOnClient(minecraft -> {
                FarDepth.compileAll();
                // 26.3's own, through the game's shader loader and its #include.
                //? if >=26.3
                /*fr.clixmods.livinghorizon.compat.FarDepthGpu.compileAll();*/
                Scene.log("depth " + FarDepth.state());
                // A step out of order is a hook gone missing: the far terrain's depth goes wrong.
                if (FarDepth.outOfOrder() != null) throw new AssertionError("steps out of order: " + FarDepth.outOfOrder());
            });
        }
    }

    /**
     * The depth view's corner of a screenshot (two fifths of the screen, 8 pixels in from the
     * bottom right): {@code {sky, ground, all}}, the view's sky dark blue, its ground grey.
     */
    private static int[] view(Path shot) {
        BufferedImage image;
        try {
            image = ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("cannot read " + shot, e);
        }
        int w = image.getWidth() * 2 / 5, h = image.getHeight() * 2 / 5;
        int x0 = image.getWidth() - w - 8, y0 = image.getHeight() - h - 8;
        int sky = 0, ground = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int rgb = image.getRGB(x, y), r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                if (Math.abs(r - 20) < 6 && Math.abs(g - 31) < 6 && Math.abs(b - 82) < 6) sky++;
                else if (Math.abs(r - g) < 4 && Math.abs(g - b) < 4) ground++;
            }
        }
        return new int[]{sky, ground, w * h};
    }
}
