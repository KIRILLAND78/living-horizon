package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.render.impostor.ImpostorFigure;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One sheet of a 3D bird's impostor pictures: a species, perched or flying with its wings
 * at one of {@link #BEATS}, seen from one of a few heights. A bird in the sky is seen from
 * below far more often than from the side, so each height has a sheet of its own; and the
 * wings keep beating from far, from one sheet to the next.
 *
 * <p>Sizes are in wingspans: the picture is scaled by the bird's own when drawn.
 *
 * @param beat   which of {@link #BEATS}; always 0 perched
 * @param height which of {@link #FLYING} or {@link #PERCHED}
 */
public record BirdFigure(Ambience.Species species, boolean perched, int beat, int height) implements ImpostorFigure {
    /** Wing angles baked, in degrees: up, level, down. */
    static final float[] BEATS = {40f, 5f, -30f};

    /** Heights a flying bird is seen from, in degrees: from well below, from below, from above. */
    static final float[] FLYING = {-60f, -20f, 30f};

    /** Heights a perched bird is seen from: from below (a pigeon on a roof), level, from above. */
    static final float[] PERCHED = {-25f, 0f, 35f};

    /** What every bird key's type starts with, followed by the species. */
    public static final String KEY = "livinghorizon:bird/";

    /** The sheet showing a bird as it is now, seen from {@code elevation} degrees. */
    static BirdFigure of(Ambience.Species species, boolean perched, float wingDegrees, double elevation) {
        int beat = perched ? 0 : nearest(BEATS, wingDegrees);
        return new BirdFigure(species, perched, beat, nearest(perched ? PERCHED : FLYING, elevation));
    }

    /** The key of {@link #of}'s sheet, without making the figure: asked for every bird every frame. */
    static ImpostorKey keyOf(Ambience.Species species, boolean perched, float wingDegrees, double elevation) {
        int beat = perched ? 0 : nearest(BEATS, wingDegrees);
        return key(species, perched, beat, nearest(perched ? PERCHED : FLYING, elevation));
    }

    /** Every key made so far, by species, pose, beat and height. */
    private static final ImpostorKey[] KEYS = new ImpostorKey[Ambience.Species.values().length * 2 * BEATS.length * 3];

    private static ImpostorKey key(Ambience.Species species, boolean perched, int beat, int height) {
        int index = ((species.ordinal() * 2 + (perched ? 1 : 0)) * BEATS.length + beat) * 3 + height;
        ImpostorKey key = KEYS[index];
        if (key == null) {
            KEYS[index] = key = new ImpostorKey(KEY + species.name().toLowerCase(Locale.ROOT),
                    (perched ? 100 : 0) + beat * 10 + height);
        }
        return key;
    }

    /** Every sheet of a species in one pose, to bake them together. */
    public static List<BirdFigure> all(Ambience.Species species, boolean perched) {
        List<BirdFigure> all = new ArrayList<>();
        for (int beat = 0; beat < (perched ? 1 : BEATS.length); beat++) {
            for (int height = 0; height < (perched ? PERCHED : FLYING).length; height++) {
                all.add(new BirdFigure(species, perched, beat, height));
            }
        }
        return all;
    }

    private static int nearest(float[] values, double value) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - value) < Math.abs(values[best] - value)) best = i;
        }
        return best;
    }

    public ImpostorKey key() {
        return key(species, perched, beat, height);
    }

    /**
     * Within 0.6 wingspan of its middle flying, of its feet perched, whichever way it is seen,
     * inside the strip {@code ImpostorRenderer} leaves out round each tile.
     */
    @Override
    public double worldSize() {
        return perched ? 1.1 : 1.35;
    }

    /** Flying, the middle of the bird in the middle of the picture; perched, the feet low in it. */
    @Override
    public float feet() {
        return perched ? 0.75f : 0.5f;
    }

    @Override
    public float elevation() {
        return (perched ? PERCHED : FLYING)[height];
    }

    @Override
    public void draw(PoseStack pose, Sink sink, float yaw) {
        AmbientRenderer.model(pose, sink, BirdModels.of(species), 1f, perched, yaw, 0f, BEATS[beat] * Mth.DEG_TO_RAD,
                1f, FULL_BRIGHT);
    }
}
