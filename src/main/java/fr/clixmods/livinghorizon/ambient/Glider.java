package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 12: now and then, by day, a player with an elytra glides across the far sky,
 * from one side of the player's view to the other, some twenty to sixty blocks over the far
 * terrain, slowly coming down; then dives steeply behind the highest ground past the end of
 * its way and is gone. Half a minute or so in all.
 *
 * <p>Its way is read under it, in the loaded chunks or the far world ({@link Columns}), and in
 * sight for part of it at least ({@link Sight}). The dive ends under the ground behind the
 * highest of a few columns past the end of the crossing, so that the hill, or the ground there,
 * hides the glider as it goes. The figure is a player of no one ({@link Puppets#player}), in the
 * default skin the game picks from its id, wearing an elytra and told it glides the way
 * {@code FarPlayer.glide} tells a far player's copy: the game's own renderer then draws it lying
 * along its flight, its wings spread.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Glider implements Egg {
    private static final int NUMBER = 12;
    /** Blocks out the middle of its crossing is, at most and at least; under which it has nowhere to fly. */
    private static final double MIN = 200, MAX = 400, LEAST = 80;
    /** Half its crossing, as a share of how far out it flies: some fifty degrees of the view in all. */
    private static final double HALF = 0.45;
    /** Blocks over the highest ground of its way it starts at, at least, and more by chance. */
    private static final double OVER = 22, OVER_MORE = 36;
    /** Blocks it comes down along its crossing, at least and more by chance. */
    private static final double DROP = 8, DROP_MORE = 10;
    /** Blocks past the end of its crossing the high ground it dives behind may stand, at least and more. */
    private static final double OUT = 20, OUT_MORE = 30;
    /** Blocks behind that high ground, away from the player, where the dive ends. */
    private static final double BEHIND = 12;
    /** Most degrees the dive turns away from straight out. */
    private static final double TURN = 50;
    /** Columns read along the crossing; places tried to dive behind; points looked at along the way. */
    private static final int ALONG = 7, HILLS = 8, LOOKED = 5;
    /** Blocks per tick along the crossing, about; ticks it takes, at least and at most; ticks of the dive. */
    private static final double PACE = 0.55;
    private static final int CROSS_MIN = 360, CROSS_MAX = 640, DIVE_MIN = 80, DIVE_MORE = 40;
    /** Blocks across under which it goes at once; ticks a search may take. */
    private static final double NEAR = 24;
    private static final int SEARCH = 400;
    /** Degrees it looks down at most, diving, and up at most. */
    private static final float STEEPEST = 75f, HIGHEST = -20f;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private @Nullable RemotePlayer figure;
    private boolean searching, called;
    /** Where the crossing starts and ends; the dive's bend and where it ends. */
    private double sx, sy, sz, ex, ey, ez, qx, qz, fx, fy, fz;
    private int cross, dive, age, idle, searchAge;

    Glider(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return NUMBER;
    }

    @Override
    public boolean busy() {
        return searching || figure != null;
    }

    @Override
    public @Nullable Vec3 position() {
        RemotePlayer him = figure;
        return him == null ? null : him.position();
    }

    @Override
    public List<? extends Entity> figures() {
        RemotePlayer him = figure;
        return him == null ? List.of() : List.of(him);
    }

    /**
     * Picks a crossing out in front of the player, from one side of their view to the other,
     * reads the ground under it and past its end, and comes once it is read: by chance, or
     * because the player asked ({@code called}: straight across their view, at any hour).
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        double far = Sight.reach();
        // Out where it crosses; its ends and the dive past them stay where the terrain is drawn.
        double out = MIN + random.nextDouble() * (MAX - MIN);
        out = Math.min(out, (far - OUT - OUT_MORE - BEHIND - 8) / Math.hypot(1, HALF));
        if (out < LEAST) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        // Across where the player looks; by chance, somewhere near it.
        double heading = Math.toRadians(self.getYRot()) + (called ? 0 : (random.nextDouble() - 0.5) * Math.toRadians(70));
        double lookX = -Math.sin(heading), lookZ = Math.cos(heading);
        double side = random.nextBoolean() ? 1 : -1;
        double acrossX = -lookZ * side, acrossZ = lookX * side;
        double cx = self.getX() + lookX * out, cz = self.getZ() + lookZ * out, half = out * HALF;
        double startX = cx - acrossX * half, startZ = cz - acrossZ * half;
        double endX = cx + acrossX * half, endZ = cz + acrossZ * half;
        // The crossing's ground, then each high place to dive behind and the ground behind it.
        double[] xs = new double[ALONG + HILLS * 2], zs = new double[xs.length];
        for (int k = 0; k < ALONG; k++) {
            double f = k / (double) (ALONG - 1);
            xs[k] = Mth.lerp(f, startX, endX);
            zs[k] = Mth.lerp(f, startZ, endZ);
        }
        double endAcross = Math.hypot(endX - self.getX(), endZ - self.getZ());
        double radialX = (endX - self.getX()) / endAcross, radialZ = (endZ - self.getZ()) / endAcross;
        for (int h = 0; h < HILLS; h++) {
            // Turning out of the crossing, away from the player.
            double turn = Math.toRadians(random.nextDouble() * TURN), reach = OUT + random.nextDouble() * OUT_MORE;
            double dx = radialX * Math.cos(turn) + acrossX * Math.sin(turn), dz = radialZ * Math.cos(turn) + acrossZ * Math.sin(turn);
            double hx = endX + dx * reach, hz = endZ + dz * reach;
            double hAcross = Math.hypot(hx - self.getX(), hz - self.getZ());
            xs[ALONG + 2 * h] = hx;
            zs[ALONG + 2 * h] = hz;
            xs[ALONG + 2 * h + 1] = hx + (hx - self.getX()) / hAcross * BEHIND;
            zs[ALONG + 2 * h + 1] = hz + (hz - self.getZ()) / hAcross * BEHIND;
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
            // Known ground all along the crossing: nothing to fly over is a hole in the view.
            double top = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < ALONG; k++) {
                if (!columns[k].known()) {
                    give(called);
                    return;
                }
                top = Math.max(top, columns[k].y());
            }
            // The highest place past the end, with known ground behind it.
            int hill = -1;
            for (int h = 0; h < HILLS; h++) {
                Columns.Column high = columns[ALONG + 2 * h], back = columns[ALONG + 2 * h + 1];
                if (!high.known() || !back.known()) continue;
                if (hill < 0 || high.y() > columns[ALONG + 2 * hill].y()) hill = h;
            }
            if (hill < 0) {
                give(called);
                return;
            }
            double startY = top + OVER + random.nextDouble() * OVER_MORE;
            double endY = Math.max(top + OVER * 0.6, startY - DROP - random.nextDouble() * DROP_MORE);
            Columns.Column high = columns[ALONG + 2 * hill], back = columns[ALONG + 2 * hill + 1];
            // Under the hill's top and under the ground behind it: hidden, whichever hides it first.
            double diveY = Math.min(high.y() - 8, back.y() - 4);
            double[] hx = new double[LOOKED], hy = new double[LOOKED], hz = new double[LOOKED];
            for (int k = 0; k < LOOKED; k++) {
                double f = (k + 0.5) / LOOKED;
                hx[k] = Mth.lerp(f, startX, endX);
                hy[k] = Mth.lerp(f, startY, endY);
                hz[k] = Mth.lerp(f, startZ, endZ);
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                int clear = 0;
                for (boolean one : seen) if (one) clear++;
                if (clear < 2) {
                    give(called);
                    return;
                }
                searching = false;
                begin(current, startX, startY, startZ, endX, endY, endZ, back.x(), diveY, back.z());
                EggLog.came(NUMBER, sx, sy, sz, self, called);
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

    private void begin(ClientLevel current, double x0, double y0, double z0, double x1, double y1, double z1,
                       double x2, double y2, double z2) {
        if (!called) calendar.shown(NUMBER);
        sx = x0;
        sy = y0;
        sz = z0;
        ex = x1;
        ey = y1;
        ez = z1;
        fx = x2;
        fy = y2;
        fz = z2;
        double length = Math.hypot(ex - sx, ez - sz);
        cross = Mth.clamp((int) (length / PACE), CROSS_MIN, CROSS_MAX);
        dive = DIVE_MIN + random.nextInt(DIVE_MORE + 1);
        // The dive's bend: straight on at the crossing's pace, so that the way turns without a kink.
        double ahead = length / cross * dive / 2;
        qx = ex + (ex - sx) / length * ahead;
        qz = ez + (ez - sz) / length * ahead;
        age = 0;
        RemotePlayer made = Puppets.player(current, new GameProfile(new UUID(random.nextLong(), random.nextLong()), ""));
        made.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        made.startFallFlying();
        // Already lying along its flight as it comes into view.
        made.fallFlyTicks = 20;
        figure = made;
        place(made, true);
    }

    /** Where it is {@code t} ticks after it came: along the crossing, then down the dive. */
    private Vec3 at(double t) {
        if (t <= cross) {
            double f = t / cross;
            return new Vec3(Mth.lerp(f, sx, ex), Mth.lerp(f, sy, ey), Mth.lerp(f, sz, ez));
        }
        double since = Math.min(t - cross, dive), u = since / dive, v = 1 - u;
        double x = v * v * ex + 2 * u * v * qx + u * u * fx;
        double z = v * v * ez + 2 * u * v * qz + u * u * fz;
        // Coming down as it did, then ever steeper.
        double fall = (ey - sy) / cross;
        double y = ey + fall * since + (fy - ey - fall * dive) * u * u * u;
        return new Vec3(x, y, z);
    }

    /** Puts the figure where it is now, along its flight, gliding. */
    private void place(RemotePlayer him, boolean first) {
        Vec3 now = at(age), next = at(age + 1);
        double vx = next.x - now.x, vy = next.y - now.y, vz = next.z - now.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(vy, Math.hypot(vx, vz))), HIGHEST, STEEPEST);
        him.setOldPosAndRot();
        him.yBodyRotO = him.yBodyRot;
        him.yHeadRotO = him.yHeadRot;
        him.setPos(now.x, now.y, now.z);
        him.setYRot(yaw);
        him.setXRot(pitch);
        him.yBodyRot = yaw;
        him.yHeadRot = yaw;
        him.setDeltaMovement(vx, vy, vz);
        // What the player's tick does for a glider, which a puppet never runs, as FarPlayer.glide does.
        if (!him.isFallFlying()) him.startFallFlying();
        him.fallFlyTicks = Math.min(him.fallFlyTicks + 1, 200);
        // Before 1.21.2 the wings spread as the model is drawn.
        //? if >=1.21.2
        him.elytraAnimationState.tick();
        him.tickCount++;
        if (first) {
            // Nothing to come from: it shows where it is, already turned along its way.
            him.setOldPosAndRot();
            him.yBodyRotO = yaw;
            him.yHeadRotO = yaw;
        }
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only by day: about once in an hour of daylight.
            if (++idle > 6000 && quiet && current.isBrightOutside() && random.nextInt(72000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        RemotePlayer him = figure;
        if (him == null) return;
        age++;
        place(him, false);
        if (age > cross + dive || Math.hypot(self.getX() - him.getX(), self.getZ() - him.getZ()) < NEAR
                || !Columns.drawn(current, self, him.getX(), him.getZ()) || (!called && !current.isBrightOutside())) {
            clear();
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = false;
        figure = null;
        level = null;
    }

    /** The game draws the figure itself; only the beam over it, called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        RemotePlayer him = figure;
        if (him == null || !called) return;
        CalledBeam.submit(pose, camera, collector, Mth.lerp(partial, him.xo, him.getX()),
                Mth.lerp(partial, him.yo, him.getY()), Mth.lerp(partial, him.zo, him.getZ()));
    }
}
