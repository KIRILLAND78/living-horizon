package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 7: some night, out on open water anywhere around the player, a few
 * hundred blocks off at most, a rowing boat drifts, pale and see-through, a ghost sitting in it.
 * Looked at a few seconds, it sinks a little and fades away; it is gone at once when the
 * player comes near. Only on this screen.
 *
 * <p>The water is found in rings of columns all around, loaded or in the far world
 * ({@link Columns}), each with water all round, in sight ({@link Sight}). The boat and the
 * ghost are drawn of glowing, see-through boxes, the ghost as {@link Ghosts} are.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class GhostBoat {
    /** Blocks per tick it drifts; blocks across under which it goes. */
    private static final double DRIFT = 0.012, NEAR = 28;
    /** Ticks it drifts at most; looked at before it goes; it takes to sink and fade. */
    private static final int LIFE = 3600, SEEN = 60, FADE = 80;
    /** How much of what is behind it the boat hides, out of 255. */
    private static final int HULL_ALPHA = 50;
    /** Height of the bench above the water, where the ghost sits. */
    private static final double SEAT = 0.3;
    /** How far off it may drift, in blocks across. */
    private static final double MIN = 50, MAX = 400;
    /** Ticks a search may take. */
    private static final int SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z, xo, yo, zo, surface, dirX, dirZ;
    private float yaw, yawO, turn;
    /** Ticks since it came; looked at; fading away, -1 until then. */
    private int age, seen, leaving = -1, idle, searchAge;

    GhostBoat(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it floats, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for open water in sight, and sets the boat drifting there once it is read: by
     * chance anywhere around, or because the player asked ({@code called}: in front of them;
     * called again, it starts over). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(7);
            return false;
        }
        int spots = 84;
        double look = Math.toRadians(self.getYRot());
        // Each spot and four around it, four blocks away: water all round is open water.
        double[] xs = new double[spots * 5], zs = new double[xs.length];
        int[][] around = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int s = 0, i = 0; s < spots; s++) {
            double bearing = called ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            for (int[] d : around) {
                xs[i] = cx + d[0];
                zs[i++] = cz + d[1];
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
            List<Columns.Column> open = new ArrayList<>();
            for (int i = 0; i < columns.length; i += 5) {
                boolean water = true;
                for (int k = 0; k < 5; k++) water &= columns[i + k].known() && columns[i + k].water();
                if (water) open.add(columns[i]);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                hx[i] = open.get(i).x();
                hy[i] = open.get(i).y() + 0.8;
                hz[i] = open.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Columns.Column> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(open.get(i));
                Columns.Column spot = Sight.any(clear, random);
                if (spot == null) {
                    if (called) EggLog.nowhere(7);
                    return;
                }
                begin(spot);
                EggLog.came(7, x, y, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in an hour and twenty minutes of it, where there is water.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(96000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        yawO = yaw;
        // Drifting, turning slowly, rising and falling on the swell; stopped where the water ends.
        double nx = x + dirX * DRIFT, nz = z + dirZ * DRIFT;
        if (water(current, nx, nz)) {
            x = nx;
            z = nz;
        }
        yaw += turn;
        // Looked at long enough, or its time up: it sinks a little as it fades.
        if (leaving < 0 && Herobrine.lookedAt(current, self, new Vec3(x, y + 1, z)) && ++seen >= SEEN) leaving = 0;
        if (leaving < 0 && age > LIFE) leaving = 0;
        float sinking = leaving < 0 ? 0f : (float) leaving / FADE;
        y = surface + 0.04 * Math.sin(age * 0.05) - 0.5 * sinking;
        if ((leaving >= 0 && ++leaving > FADE) || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)
                || (!called && current.isBrightOutside())) {
            clear();
        }
    }

    private void begin(Columns.Column spot) {
        if (!called) calendar.shown(7);
        x = xo = spot.x();
        z = zo = spot.z();
        // The top of the water block, a little under the column's top.
        surface = y = yo = spot.y() - 0.1;
        double drift = random.nextDouble() * Math.PI * 2;
        dirX = Math.sin(drift);
        dirZ = Math.cos(drift);
        yaw = yawO = random.nextFloat() * 360f;
        turn = (random.nextBoolean() ? 1 : -1) * (0.05f + random.nextFloat() * 0.1f);
        age = seen = 0;
        leaving = -1;
        active = true;
    }

    private void clear() {
        generation.incrementAndGet();
        active = searching = false;
        level = null;
    }

    /** Water on top of this column; true where the chunk is not loaded, as nothing says otherwise. */
    private static boolean water(ClientLevel level, double x, double z) {
        if (!Columns.loaded(level, x, z)) return true;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return !level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty();
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        float shown = leaving < 0 ? 1f : Mth.clamp(1f - (leaving + partial) / FADE, 0f, 1f);
        if (shown <= 0f) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        float rock = (float) Math.sin((age + partial) * 0.07) * 4f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        pose.mulPose(Axis.ZP.rotationDegrees(rock));
        int hull = Math.round(HULL_ALPHA * shown);
        collector.draw(pose, Ufo.GLOW, (at, out) -> hull(at, out, hull));
        // The ghost on the bench, looking out over the water, this way and that.
        pose.translate(0f, (float) SEAT - Ghosts.SHAPE.leg(), 0f);
        float look = 35f * (float) Math.sin((age + partial) * 0.01);
        BoxFigure.draw(pose, collector, Ghosts.SHAPE, 0f, 0f, -look, Ufo.BRIGHT, Ufo.GLOW,
                Math.round(Ghosts.ALPHA * shown), true);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /** A rowing boat, pale and see-through: its floor, its four sides, a bench across. */
    private static void hull(PoseStack.Pose at, VertexConsumer out, int a) {
        int r = 140, g = 158, b = 190, light = Ufo.BRIGHT;
        // Half its width and length, the height of its sides, the thickness of a plank.
        float w = 0.62f, l = 0.88f, h = 0.42f, t = 0.08f;
        Ufo.box(at, out, -w, -0.05f, -l, w, t - 0.05f, l, r, g, b, a, light);
        Ufo.box(at, out, -w, 0, -l, -w + t, h, l, r, g, b, a, light);
        Ufo.box(at, out, w - t, 0, -l, w, h, l, r, g, b, a, light);
        Ufo.box(at, out, -w + t, 0, -l, w - t, h, -l + t, r, g, b, a, light);
        Ufo.box(at, out, -w + t, 0, l - t, w - t, h, l, r, g, b, a, light);
        Ufo.box(at, out, -w + t, (float) SEAT - 0.08f, -0.12f, w - t, (float) SEAT, 0.12f, r, g, b, a, light);
    }
}
