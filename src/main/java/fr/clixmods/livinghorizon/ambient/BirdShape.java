package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 18: now and then, on a clear day, a flock crosses the sky far off, from one side
 * of the player's view to the other, as the geese do - but in the shape of a Creeper's face
 * instead of a V: a bird on each dark pixel of it, its eyes, its nose and its mouth. Only on
 * this screen.
 *
 * <p>The face stands upright in the plane the flock flies in, across the player's line of
 * sight, its pixels some one degree of the view apart, so that it reads the same however far
 * it flies. Its way is read in the loaded chunks or the far world ({@link Columns}), high
 * enough over the ground, and in sight ({@link Sight}). Each bird faces the way it flies,
 * flapping; they come and go fading at the edges of the way. The birds are the 3D models of
 * the flocks ({@link AmbientRenderer#model}, {@link BirdModels}), drawn by this class.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class BirdShape implements Egg {
    private static final int NUMBER = 18;
    /** The face, top row first: X a bird, . the sky. */
    private static final String[] FACE = {
            "........",
            "........",
            ".XX..XX.",
            ".XX..XX.",
            "...XX...",
            "..XXXX..",
            "..XXXX..",
            "..X..X..",
    };
    private static final int GRID = 8;
    /** Blocks out it flies, at least and at most; under which it has nowhere to fly. */
    private static final double MIN = 150, MAX = 300, LEAST = 60;
    /** Blocks between two pixels of the face per block out: about a degree of the view. */
    private static final double SPACING = 0.0175;
    /** A bird's wingspan, as a share of the spacing. */
    private static final double SPAN = 0.9;
    /** How far up it flies, as a share of how far out: some ten degrees over the eye. */
    private static final double RISE = 0.16;
    /** Blocks between the bottom of the face and the ground under it, at least. */
    private static final double CLEAR = 12;
    /** Half the way across, as a share of how far out: from some forty degrees to one side to as far to the other. */
    private static final double HALF_WAY = 0.85;
    /** Blocks per tick it flies; ticks it takes to show or to fade at either end. */
    private static final double PACE = 0.6;
    private static final int FADE = 40;
    /** Points of its way read; ticks a search may take. */
    private static final int ALONG = 7, SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private final List<Ambience.Flyer> birds = new ArrayList<>();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    /** Where the face's middle starts and the way it flies, in blocks per tick; its height; a pixel's size. */
    private double startX, startZ, height, dirX, dirZ, spacing;
    /** Per bird: its place in the face, along the way and up; its own little wobble. */
    private double[] along = new double[0], up = new double[0];
    private float[] wobble = new float[0];
    private float heading;
    /** Ticks since it came; ticks it takes to cross. */
    private int age, length, idle, searchAge;

    BirdShape(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return NUMBER;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The face's middle, while the flock is there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(startX + dirX * age, height, startZ + dirZ * age) : null;
    }

    /** Birds in the flock, while it is there. For the game tests. */
    public int birds() {
        return active ? birds.size() : 0;
    }

    /**
     * Picks a way across the sky out in front of the player, reads the ground under it and
     * whether it is in sight, and flies it once it is read: by chance, or because the player
     * asked ({@code called}: straight across their view, at any hour, whatever the weather).
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        double out = Math.min(MIN + random.nextDouble() * (MAX - MIN), Sight.reach() * 0.8);
        if (out < LEAST) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        // Across where the player looks; by chance, somewhere near it.
        double look = Math.toRadians(self.getYRot()) + (called ? 0 : (random.nextDouble() - 0.5) * Math.toRadians(100));
        double lookX = -Math.sin(look), lookZ = Math.cos(look);
        double side = random.nextBoolean() ? 1 : -1, acrossX = -lookZ * side, acrossZ = lookX * side;
        double middleX = self.getX() + lookX * out, middleZ = self.getZ() + lookZ * out, half = out * HALF_WAY;
        double fromX = middleX - acrossX * half, fromZ = middleZ - acrossZ * half;
        double[] xs = new double[ALONG], zs = new double[ALONG];
        for (int k = 0; k < ALONG; k++) {
            double f = k / (double) (ALONG - 1);
            xs[k] = fromX + acrossX * 2 * half * f;
            zs[k] = fromZ + acrossZ * 2 * half * f;
        }
        double step = out * SPACING;
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            double ground = Double.NEGATIVE_INFINITY;
            for (Columns.Column column : columns) if (column.known()) ground = Math.max(ground, column.y());
            if (ground == Double.NEGATIVE_INFINITY) {
                give(called);
                return;
            }
            // The face's middle: high enough over all of its way.
            double middleY = Math.max(eye.y + out * RISE, ground + step * GRID / 2 + CLEAR);
            double[] hy = new double[ALONG];
            java.util.Arrays.fill(hy, middleY);
            Sight.of(current, self, eye, xs, hy, zs).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                int clear = 0;
                for (boolean one : seen) if (one) clear++;
                // In sight for most of its way.
                if (clear * 2 <= ALONG) {
                    give(called);
                    return;
                }
                searching = false;
                begin(fromX, middleY, fromZ, acrossX, acrossZ, 2 * half, step);
                EggLog.came(NUMBER, middleX, middleY, middleZ, self, called);
            }, game);
        }, game);
        return true;
    }

    /** Nowhere to fly: the search ends. */
    private void give(boolean called) {
        searching = false;
        level = null;
        if (called) EggLog.nowhere(NUMBER);
    }

    private void begin(double x, double y, double z, double acrossX, double acrossZ, double way, double step) {
        if (!called) calendar.shown(NUMBER);
        startX = x;
        startZ = z;
        height = y;
        dirX = acrossX * PACE;
        dirZ = acrossZ * PACE;
        heading = (float) Math.toDegrees(Math.atan2(-acrossX, acrossZ));
        spacing = step;
        length = (int) Math.ceil(way / PACE);
        // A bird on each dark pixel of the face: its columns along the way, its rows up.
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) if (FACE[row].charAt(col) == 'X') cells.add(new int[]{row, col});
        }
        int count = cells.size();
        along = new double[count];
        up = new double[count];
        wobble = new float[count];
        birds.clear();
        for (int i = 0; i < count; i++) {
            int[] cell = cells.get(i);
            // The face's left, seen by the player, at the back of the flock when it flies to their right.
            along[i] = (cell[1] - (GRID - 1) / 2.0) * step;
            up[i] = ((GRID - 1) / 2.0 - cell[0]) * step;
            wobble[i] = random.nextFloat() * 6.28f;
            Ambience.Flyer bird = new Ambience.Flyer(Ambience.Kind.SILHOUETTE, Ambience.Species.GENERIC,
                    (float) (step * SPAN * (0.92 + random.nextDouble() * 0.16)), 40, null, 0.8f + random.nextFloat() * 0.3f);
            bird.beat = random.nextFloat() * 6.28f;
            bird.place(x + acrossX * along[i], y + up[i], z + acrossZ * along[i]);
            bird.yaw = bird.yawO = heading;
            birds.add(bird);
        }
        age = 0;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only on a clear day: about once in an hour of it.
            if (++idle > 6000 && quiet && current.isBrightOutside() && !current.isRaining() && random.nextInt(72000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (++age > length) {
            clear();
            return;
        }
        double cx = startX + dirX * age, cz = startZ + dirZ * age, unit = Math.hypot(dirX, dirZ);
        double ax = dirX / unit, az = dirZ / unit;
        // Showing at one end of the way, fading at the other.
        float shown = Math.min(1f, Math.min(age, length - age) / (float) FADE);
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            // Each keeps its place in the face, rising and falling a little on its own.
            double bob = Math.sin(age * 0.08 + wobble[i]) * spacing * 0.12;
            bird.moveTo(cx + ax * along[i], height + up[i] + bob, cz + az * along[i], heading, 0f);
            bird.scale = shown;
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        birds.clear();
        level = null;
    }

    /** Each bird as the flocks' 3D model, pulled in past the far plane like the flocks, a little smaller in proportion. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            if (bird.scale <= 0f) continue;
            double px = Mth.lerp(partial, bird.xo, bird.x) - camera.x;
            double py = Mth.lerp(partial, bird.yo, bird.y) - camera.y;
            double pz = Mth.lerp(partial, bird.zo, bird.z) - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            GhostRenderer.noteDistance(distance + bird.span);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            float wing = Mth.lerp(partial, bird.wingO, bird.wing) * Mth.DEG_TO_RAD;
            float tone = 0.9f + 0.2f * ((i * 37) & 15) / 15f;
            pose.pushPose();
            pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
            // The model's own yaw is left at nought: turned here.
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, bird.yawO, bird.yaw)));
            AmbientRenderer.model(pose, collector, BirdModels.of(Ambience.Species.GENERIC), bird.span * bird.scale * pull,
                    false, 0f, 0f, wing, tone, Ufo.LIT);
            pose.popPose();
        }
        Vec3 middle = position();
        if (called && middle != null) CalledBeam.submit(pose, camera, collector, middle.x, middle.y + spacing * GRID / 2, middle.z);
    }
}
