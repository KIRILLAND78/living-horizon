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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 3: from the second or third day the player lives in a world, once a day, at any
 * moment of it, by day or by night, a pale glowing figure walks a few steps somewhere in sight,
 * anywhere around the player up to a few hundred blocks off, then stops, turns to look at them,
 * and is gone - at once if the player comes near. Once it has been looked at it does not come
 * back for one to three days; until then it comes back every day ({@link Daily}). Only on this
 * screen.
 *
 * <p>Its way is read in the loaded chunks or the far world ({@link Columns}), on dry ground
 * all along, and must start in sight ({@link Sight}). The figure is drawn of glowing,
 * see-through boxes ({@link BoxFigure}), as the boat of {@link GhostBoat} is.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Ghosts {
    /** A figure of a player's proportions, pale. */
    static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            0.75f, 0.25f, 0.75f, 0.5f, 0.25f, 0.72f, 0.25f, 0.5f, 0f, 0xB9CDF5, 0xB9CDF5);
    /** How much of what is behind a ghost it hides, out of 255. */
    static final int ALPHA = 65;
    /** Blocks per tick it walks; how close the player may come before it vanishes. */
    private static final double PACE = 0.045, NEAR = 10;
    /** How far off it may walk, in blocks across. */
    private static final double MIN = 22, MAX = 320;
    /** Ways tried; stretches each way is read in. */
    private static final int WAYS = 40, ALONG = 4;
    /** Ticks it stands looking at the player before it vanishes; ticks a search may take. */
    private static final int STARE = 50, SEARCH = 400;

    private final Daily daily;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, seen;
    private double x, y, z, xo, yo, zo, dirX, dirZ, floor, walked, length;
    /** The ground along its way, where it is not loaded: {@link #ALONG} stretches. */
    private double[] heights = new double[0];
    private float yaw, yawO, phase;
    private int age, walk, searchAge;

    Ghosts(EggCalendar calendar) {
        this.daily = new Daily(calendar, 3);
    }

    /** For the game tests: it comes in a second, if today is one of its days. */
    public void soon() {
        daily.soon();
    }

    /** On its way or under way. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it stands, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for a way in sight anywhere around the player, and walks it once it is read: on
     * its day, or because the player asked ({@code called}: in front of them; called again, it
     * starts over). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(3);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        int steps = 160 + random.nextInt(100);
        double way = steps * PACE;
        double[] xs = new double[WAYS * (ALONG + 1)], zs = new double[xs.length], dirs = new double[WAYS * 2];
        double[] distances = new double[WAYS];
        for (int w = 0; w < WAYS; w++) {
            double bearing = called ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            distances[w] = distance;
            double sx = self.getX() - Math.sin(bearing) * distance, sz = self.getZ() + Math.cos(bearing) * distance;
            double heading = random.nextDouble() * Math.PI * 2;
            dirs[2 * w] = -Math.sin(heading);
            dirs[2 * w + 1] = Math.cos(heading);
            for (int k = 0; k <= ALONG; k++) {
                xs[w * (ALONG + 1) + k] = sx + dirs[2 * w] * way * k / ALONG;
                zs[w * (ALONG + 1) + k] = sz + dirs[2 * w + 1] * way * k / ALONG;
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            // Dry all along, no step too high to float over.
            List<Integer> dry = new ArrayList<>();
            for (int w = 0; w < WAYS; w++) {
                boolean ok = true;
                for (int k = 0; k <= ALONG && ok; k++) {
                    Columns.Column column = columns[w * (ALONG + 1) + k];
                    ok = column.dry() && (k == 0 || Math.abs(column.y() - columns[w * (ALONG + 1) + k - 1].y()) <= 3);
                }
                if (ok) dry.add(w);
            }
            double[] hx = new double[dry.size()], hy = new double[dry.size()], hz = new double[dry.size()];
            for (int i = 0; i < dry.size(); i++) {
                Columns.Column start = columns[dry.get(i) * (ALONG + 1)];
                hx[i] = start.x();
                hy[i] = start.y() + 1.6;
                hz[i] = start.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Integer> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(dry.get(i));
                Integer w = Sight.any(clear, random);
                if (w == null) {
                    if (called) EggLog.nowhere(3);
                    return;
                }
                double[] ys = new double[ALONG + 1];
                for (int k = 0; k <= ALONG; k++) ys[k] = columns[w * (ALONG + 1) + k].y();
                begin(columns[w * (ALONG + 1)], dirs[2 * w], dirs[2 * w + 1], ys, way, steps);
                EggLog.came(3, x, floor, z, self, called);
            }, game);
        }, game);
        return true;
    }

    private void begin(Columns.Column start, double dx, double dz, double[] ys, double way, int steps) {
        if (!called) daily.came();
        x = xo = start.x();
        z = zo = start.z();
        heights = ys;
        floor = ys[0];
        y = yo = floor + 0.2;
        dirX = dx;
        dirZ = dz;
        yaw = yawO = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        length = way;
        walk = steps;
        walked = 0;
        age = 0;
        phase = random.nextFloat() * 6.28f;
        seen = false;
        active = true;
    }

    public void tick(ClientLevel current, LocalPlayer self, boolean enabled) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!active) {
            if (daily.due()) summon(current, self, false);
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        yawO = yaw;
        if (Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        if (age <= walk) {
            double nx = x + dirX * PACE, nz = z + dirZ * PACE, ground = ground(current, nx, nz, walked + PACE);
            if (Double.isNaN(ground)) {
                // Water ahead: it stops there.
                walk = age;
            } else {
                x = nx;
                z = nz;
                walked += PACE;
                floor += (ground - floor) * 0.15;
            }
        } else {
            // Turning, slowly, to the player.
            float toPlayer = (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
            yaw = Mth.approachDegrees(yaw, toPlayer, 9f);
        }
        // Not quite touching the ground.
        y = floor + 0.2 + 0.08 * Math.sin(age * 0.08 + phase);
        if (!seen && !called && Herobrine.lookedAt(current, self, new Vec3(x, y + 1.5, z))) {
            // Looked at: not back for days.
            seen = true;
            daily.seen();
        }
        if (age > walk + STARE) clear();
    }

    /**
     * The ground to float over, {@code along} blocks into its way: the loaded column's, NaN
     * where it is water; where it is not loaded, the one read before it started.
     */
    private double ground(ClientLevel level, double x, double z, double along) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (Columns.loaded(level, x, z)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            return level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty() ? top : Double.NaN;
        }
        double f = Mth.clamp(along / length, 0, 1) * ALONG;
        int i = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - i, heights[i], heights[i + 1]);
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // A slow stride while it walks.
        float swing = age <= walk ? (float) Math.sin((walked + PACE * partial) * 4.0) * 25f : 0f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        BoxFigure.draw(pose, collector, SHAPE, swing, 0f, 0f, Ufo.BRIGHT, Ufo.GLOW, ALPHA, false);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /** Nothing the player cannot see through between their eye and this point. */
    static boolean inSight(ClientLevel level, LocalPlayer self, Vec3 eye, double x, double y, double z) {
        return level.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self))
                .getType() == HitResult.Type.MISS;
    }
}
