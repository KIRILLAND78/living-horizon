package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.GameBoot;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The impostor sheets of the 3D birds: which one shows a bird, and whether it fits its tiles. */
class BirdFigureTest {
    private static final double INSET = ImpostorRenderer.INSET;

    @Test
    void picksTheNearestBeatAndHeight() {
        BirdFigure up = BirdFigure.of(Ambience.Species.GULL, false, 43f, -80);
        assertEquals(0, up.beat());
        assertEquals(-60f, up.elevation());
        BirdFigure down = BirdFigure.of(Ambience.Species.GULL, false, -33f, 50);
        assertEquals(2, down.beat());
        assertEquals(30f, down.elevation());
        BirdFigure perched = BirdFigure.of(Ambience.Species.ROBIN, true, 40f, 2);
        assertEquals(0, perched.beat());
        assertEquals(0f, perched.elevation());
    }

    @Test
    void everySheetOfAPoseIsBakedTogether() {
        assertEquals(BirdFigure.BEATS.length * BirdFigure.FLYING.length, BirdFigure.all(Ambience.Species.GOOSE, false).size());
        assertEquals(BirdFigure.PERCHED.length, BirdFigure.all(Ambience.Species.GOOSE, true).size());
        Set<BirdFigure> distinct = new HashSet<>(BirdFigure.all(Ambience.Species.GOOSE, false));
        distinct.addAll(BirdFigure.all(Ambience.Species.GOOSE, true));
        assertEquals(BirdFigure.BEATS.length * BirdFigure.FLYING.length + BirdFigure.PERCHED.length, distinct.size());
    }

    /**
     * The keys come from a table made once, and stay what they were: the pictures kept on disk
     * from an earlier game are found by them.
     */
    @Test
    void keysAreMadeOnceAndKeepTheirValues() {
        GameBoot.start();
        for (Ambience.Species species : Ambience.Species.values()) {
            for (boolean perched : new boolean[]{false, true}) {
                for (BirdFigure figure : BirdFigure.all(species, perched)) {
                    BirdFigure same = new BirdFigure(species, perched, figure.beat(), figure.height());
                    assertSame(figure.key(), same.key());
                    assertEquals(new ImpostorKey("livinghorizon:bird/" + species.name().toLowerCase(java.util.Locale.ROOT),
                            (perched ? 100 : 0) + figure.beat() * 10 + figure.height()), figure.key());
                }
            }
        }
    }

    /**
     * Seen from any side, a bird must stay inside the part of its tile that is drawn (the
     * renderer leaves a thin strip out round it): within half of that from its middle flying,
     * at every beat; perched, within the room above its feet, and about half of it across.
     */
    @Test
    void everyBirdFitsItsTiles() {
        for (Ambience.Species species : Ambience.Species.values()) {
            BirdModels.Model model = BirdModels.of(species);
            BirdFigure flying = new BirdFigure(species, false, 0, 0);
            for (float beat : BirdFigure.BEATS) {
                double radius = radius(model, false, beat * Math.PI / 180) / model.span();
                assertTrue(radius <= flying.worldSize() * (0.5 - INSET), species + " flying reaches " + radius);
            }
            BirdFigure perched = new BirdFigure(species, true, 0, 0);
            double radius = radius(model, true, 0) / model.span();
            assertTrue(radius <= perched.worldSize() * (perched.feet() - INSET), species + " perched reaches " + radius);
            assertTrue(radius <= perched.worldSize() * (0.5 - INSET) * 1.25, species + " perched is " + radius + " wide");
        }
    }

    /** The farthest corner from the origin: the middle flying, the feet perched. */
    private static double radius(BirdModels.Model model, boolean perched, double wing) {
        double farthest = 0;
        for (BirdModels.Cube cube : model.cubes()) {
            BirdModels.Part part = cube.part();
            boolean shown = part == BirdModels.Part.BODY
                    || (perched ? part == BirdModels.Part.PERCH
                    : part == BirdModels.Part.FLY || part == BirdModels.Part.WING || part == BirdModels.Part.TIP);
            if (!shown) continue;
            for (float x : new float[]{cube.x0(), cube.x1()}) {
                for (float y : new float[]{cube.y0(), cube.y1()}) {
                    for (float z : new float[]{cube.z0(), cube.z1()}) {
                        double[] p = {x, y, z};
                        if (part == BirdModels.Part.TIP) p = turn(p, model.tipX(), wing * 0.5);
                        if (part == BirdModels.Part.WING || part == BirdModels.Part.TIP) p = turn(p, model.hingeX(), wing);
                        double lifted = perched ? p[1] + model.lift() : p[1];
                        farthest = Math.max(farthest, Math.sqrt(p[0] * p[0] + lifted * lifted + p[2] * p[2]));
                    }
                }
            }
        }
        return farthest;
    }

    /** A point turned about the line along Z through {@code x}, as the renderer's hinge does. */
    private static double[] turn(double[] p, double x, double radians) {
        double dx = p[0] - x, cos = Math.cos(radians), sin = Math.sin(radians);
        return new double[]{x + dx * cos - p[1] * sin, dx * sin + p[1] * cos, p[2]};
    }
}
