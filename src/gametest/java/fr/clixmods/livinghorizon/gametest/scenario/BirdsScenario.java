package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.ambient.Ambience.Species;
import fr.clixmods.livinghorizon.ambient.BirdFigure;
import fr.clixmods.livinghorizon.ambient.PosedBirds;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.Locale;
import java.util.Random;

/**
 * The birds, posed by hand: each species up close, perched on a log (the duck on water)
 * and flying, then all of them side by side at their true sizes, a sky full of them, and
 * a gull's wing beat in three steps. Everything in 3D, then the lineup and the sky again
 * in 2D to compare. Last, the 3D birds as impostors: every species far away, perched and
 * flying, as models then as pictures, the sky again as pictures, and the baked atlas.
 */
public final class BirdsScenario implements Scenario {
    /** Each species and a wingspan of its own, in blocks, as the mod gives it. */
    private static final Object[][] SPECIES = {
            {Species.GENERIC, 0.8f}, {Species.GOOSE, 1.8f}, {Species.RAPTOR, 2.2f}, {Species.GULL, 1.2f},
            {Species.PIGEON, 0.9f}, {Species.ROBIN, 0.8f}, {Species.TIT, 0.6f}, {Species.DUCK, 0.9f},
    };

    private TestSingleplayerContext world;
    private int ground;

    @Override
    public String name() {
        return "birds";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // No bird of its own, nothing but the posed ones.
        Scene.configure(context, config -> {
            config.skyBirds = true;
            config.birdDensity = 0;
        });
        try (TestSingleplayerContext flat = Scene.flatWorld(context)) {
            world = flat;
            ground = context.computeOnClient(minecraft -> minecraft.player.getBlockY());
            Scene.configure(context, config -> config.birdStyle = "3d");
            for (Object[] species : SPECIES) portrait(context, (Species) species[0], (float) species[1]);
            wings(context);
            for (String style : new String[]{"3d", "2d"}) {
                Scene.configure(context, config -> config.birdStyle = style);
                lineup(context, style);
                sky(context, style);
            }
            impostors(context);
            PosedBirds.clear();
        }
    }

    /** Back to bare grass around the player, and no bird. */
    private void clear() {
        PosedBirds.clear();
        Scene.command(world, "fill -12 " + ground + " -3 12 " + (ground + 6) + " 14 air");
        Scene.command(world, "fill -12 " + (ground - 1) + " -3 12 " + (ground - 1) + " 14 grass_block");
    }

    private void look(float pitch) {
        Scene.command(world, "tp @a 0.5 " + ground + " 0.5 0 " + pitch);
    }

    private void portrait(ClientGameTestContext context, Species species, float span) {
        clear();
        double distance = 0.9 + span * 0.85, aside = 0.3 + span * 0.4;
        int logX = (int) Math.floor(0.5 + aside), logZ = (int) Math.floor(0.5 + distance);
        if (species == Species.DUCK) {
            Scene.command(world, "fill " + (logX - 2) + " " + (ground - 1) + " " + (logZ - 1) + " " + (logX + 1)
                    + " " + (ground - 1) + " " + (logZ + 2) + " water");
            PosedBirds.add(species, span, logX + 0.5, ground - 0.12, logZ + 0.5, -130f, true, 0f, 0f);
        } else {
            Scene.command(world, "setblock " + logX + " " + ground + " " + logZ + " oak_log");
            PosedBirds.add(species, span, logX + 0.5, ground + 1, logZ + 0.5, -130f, true, 0f, 0f);
        }
        // Flying towards the camera, wings spread.
        PosedBirds.add(species, span, 0.5 - aside, ground + 1.05 + span * 0.1, 0.3 + distance, -158f, false, 16f, -8f);
        look(18f);
        Scene.screenshot(context, this, "3d-" + species.name().toLowerCase(Locale.ROOT));
    }

    /** A gull with its wings up, level and down: the beat turns each wing and bends its tip. */
    private void wings(ClientGameTestContext context) {
        clear();
        float[] beat = {43f, 5f, -33f};
        for (int i = 0; i < beat.length; i++) {
            PosedBirds.add(Species.GULL, 1.2f, 2.3 - i * 1.8, ground + 1.2, 3.5, 180f, false, beat[i], 0f);
        }
        look(15f);
        Scene.screenshot(context, this, "3d-wings");
    }

    private void lineup(ClientGameTestContext context, String style) {
        clear();
        double x = 4.6;
        for (Object[] entry : SPECIES) {
            Species species = (Species) entry[0];
            float span = (float) entry[1];
            x -= span * 0.3 + 0.2;
            if (species == Species.DUCK) {
                int block = (int) Math.floor(x);
                Scene.command(world, "fill " + (block - 1) + " " + (ground - 1) + " 3 " + (block + 1) + " " + (ground - 1) + " 4 water");
                PosedBirds.add(species, span, x, ground - 0.12, 4.1, -150f, true, 0f, 0f);
            } else {
                PosedBirds.add(species, span, x, ground, 4.1, -150f, true, 0f, 0f);
            }
            x -= span * 0.3 + 0.2;
        }
        look(22f);
        Scene.screenshot(context, this, style + "-lineup");
    }

    /**
     * Each species 45 blocks away, perched and flying above, four times its size so that it
     * can be told apart, as models then as impostors once every sheet is baked.
     */
    private void impostors(ClientGameTestContext context) {
        if (context.computeOnClient(minecraft -> ImpostorAtlas.failed())) {
            Scene.log("birds: no impostors in this version");
            return;
        }
        clear();
        Scene.configure(context, config -> {
            config.birdStyle = "3d";
            config.impostors = false;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
        });
        float[] beat = {40f, 5f, -30f};
        for (int i = 0; i < SPECIES.length; i++) {
            Species species = (Species) SPECIES[i][0];
            float span = (float) SPECIES[i][1] * 4;
            double x = 14.5 - i * 4;
            PosedBirds.add(species, span, x, ground, 45.5, -150f, true, 0f, 0f);
            PosedBirds.add(species, span, x, ground + 7, 47.5, -160f, false, beat[i % beat.length], 0f);
        }
        look(4f);
        context.runOnClient(minecraft -> minecraft.options.fov().set(40));
        Scene.screenshot(context, this, "3d-far-models");
        Scene.configure(context, config -> config.impostors = true);
        // Every sheet of every species, perched and flying: other scenarios may have left
        // sheets of their own in the atlas, so the birds' are counted.
        int wanted = SPECIES.length * (BirdFigure.all(Species.GULL, false).size() + BirdFigure.all(Species.GULL, true).size());
        context.waitFor(minecraft -> ImpostorAtlas.failed() || birdSheets() >= wanted, 2400);
        context.runOnClient(minecraft -> Scene.log("birds impostors baked=" + birdSheets() + "/" + wanted
                + " waiting=" + ImpostorAtlas.waiting() + " failed=" + ImpostorAtlas.failed()));
        if (context.computeOnClient(minecraft -> ImpostorAtlas.failed())) {
            throw new AssertionError("The bird impostors could not be baked, see the log");
        }
        Scene.screenshot(context, this, "3d-far-impostors");
        context.runOnClient(minecraft ->
                Scene.log("birds impostors drawn=" + PolygonStats.impostors() + " models=" + PolygonStats.models()));
        if (context.computeOnClient(minecraft -> PolygonStats.impostors()) < SPECIES.length * 2) {
            throw new AssertionError("The far birds are not drawn as impostors");
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        // The sky up close, every bird past the distance.
        Scene.configure(context, config -> config.impostorDistance = 8);
        sky(context, "3d-impostors");
        Scene.screenshotAtlas(context, this);
    }

    /** The bird sheets in the atlas. */
    private static int birdSheets() {
        int count = 0;
        for (ImpostorAtlas.Baked baked : ImpostorAtlas.baked()) {
            if (baked.key().type().startsWith(BirdFigure.KEY)) count++;
        }
        return count;
    }

    /** Geese in a V, two buzzards circling, a cloud of starlings, gulls lower down. */
    private void sky(ClientGameTestContext context, String style) {
        clear();
        Random random = new Random(7);
        for (int i = 0; i < 9; i++) {
            int rank = (i + 1) / 2;
            double side = (i % 2 == 0 ? 1 : -1) * rank * 2.0;
            PosedBirds.add(Species.GOOSE, 1.9f, 2.5 - rank * 2.3, ground + 11 + random.nextDouble() * 0.5, 20.5 + side,
                    -90f, false, 5f + 38f * (float) Math.sin(i * 1.3), 0f);
        }
        PosedBirds.add(Species.RAPTOR, 2.4f, -8.5, ground + 15, 26.5, 30f, false, 8f, 18f);
        PosedBirds.add(Species.RAPTOR, 2.2f, -3.5, ground + 17, 32.5, 160f, false, 8f, -18f);
        for (int i = 0; i < 14; i++) {
            PosedBirds.add(Species.GENERIC, 0.8f, 9.5 + random.nextGaussian() * 2.5, ground + 9 + random.nextGaussian() * 1.5,
                    18.5 + random.nextGaussian() * 2.5, -70f, false, 5f + 38f * (float) Math.sin(random.nextDouble() * 6.3), 0f);
        }
        PosedBirds.add(Species.GULL, 1.3f, 3.5, ground + 4.5, 9.5, 100f, false, 20f, -10f);
        PosedBirds.add(Species.GULL, 1.2f, 6.5, ground + 5.5, 12.5, 120f, false, -10f, -6f);
        look(-22f);
        Scene.screenshot(context, this, style + "-sky");
    }
}
