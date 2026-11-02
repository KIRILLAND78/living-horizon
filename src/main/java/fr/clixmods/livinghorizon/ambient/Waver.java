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
 * Easter egg 5: from the second or third day the player lives in a world, once a day, at any
 * moment of it, by day or by night, a dark figure stands on the highest summit in sight,
 * anywhere around the player up to a few hundred blocks off, against the sky, turns to them and
 * waves - and is gone the moment they look at it. Once looked at it does not come back for one
 * to three days; until then it comes back every day ({@link Daily}). Only on this screen.
 *
 * <p>The summit is the highest of rings of columns around the player, read in the loaded
 * chunks or the far world ({@link Columns}), in sight ({@link Sight}); on its day it must
 * rise well above the player and the sea. The figure is drawn of boxes ({@link BoxFigure}).
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Waver {
    private static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            0.75f, 0.25f, 0.75f, 0.5f, 0.25f, 0.72f, 0.25f, 0.5f, 0f, 0x16161A, 0x16161A);
    /**
     * Ticks it stands still before it waves, looked at or not; ticks it waves at most, until
     * it is looked at, on its day and when called; ticks a search may take.
     */
    private static final int STAND = 30, PATIENCE = 600, CALLED_PATIENCE = 1800, SEARCH = 400;
    /** How far off it may stand, in blocks across. */
    private static final double MIN = 60, MAX = 400;
    /** Blocks a summit found on its day rises above the player's feet, and above the sea. */
    private static final double ABOVE_PLAYER = 12, ABOVE_SEA = 20;
    /** Blocks across: it goes if the player comes this close. */
    private static final double NEAR = 24;

    private final Daily daily;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private boolean searching, active, called;
    private double x, y, z;
    private float yaw, yawO;
    private int age, searchAge;

    Waver(EggCalendar calendar) {
        this.daily = new Daily(calendar, 5);
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
     * Looks for the summit in sight, and stands there once it is read: on its day anywhere
     * around, or because the player asked ({@code called}: the highest point in front of
     * them, however low; called again, it starts over). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(5);
            return false;
        }
        int bearings = called ? 11 : 32, rings = 10;
        double look = Math.toRadians(self.getYRot());
        double[] xs = new double[bearings * rings], zs = new double[bearings * rings];
        for (int b = 0, i = 0; b < bearings; b++) {
            double bearing = called ? look + (b - (bearings - 1) / 2.0) * 0.09 : (b + random.nextDouble()) * Math.PI * 2 / bearings;
            for (int r = 0; r < rings; r++, i++) {
                double distance = MIN + (r + random.nextDouble()) * (far - MIN) / rings;
                xs[i] = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
                zs[i] = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            }
        }
        this.called = called;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        double feetY = self.getY();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(level, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Columns.Column> standing = new ArrayList<>();
            for (Columns.Column column : columns) {
                if (!column.dry()) continue;
                if (!called && (column.y() < feetY + ABOVE_PLAYER || column.y() < level.getSeaLevel() + ABOVE_SEA)) continue;
                standing.add(column);
            }
            double[] hx = new double[standing.size()], hy = new double[standing.size()], hz = new double[standing.size()];
            for (int i = 0; i < standing.size(); i++) {
                hx[i] = standing.get(i).x();
                hy[i] = standing.get(i).y() + 1.6;
                hz[i] = standing.get(i).z();
            }
            Sight.of(level, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                // Any of the highest in sight: a summit against the sky.
                List<Columns.Column> seenTops = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) seenTops.add(standing.get(i));
                seenTops.sort((a, b) -> Double.compare(b.y(), a.y()));
                Columns.Column top = Sight.any(seenTops.subList(0, (seenTops.size() + 3) / 4), random);
                if (top != null) begin(top, self);
                else if (called) EggLog.nowhere(5);
            }, game);
        }, game);
        return true;
    }

    private void begin(Columns.Column top, LocalPlayer self) {
        EggLog.came(5, top.x(), top.y(), top.z(), self, called);
        if (!called) daily.came();
        x = top.x();
        y = top.y();
        z = top.z();
        yaw = yawO = facing(self);
        age = 0;
        active = true;
    }

    private void clear() {
        generation.incrementAndGet();
        active = searching = false;
    }

    public void tick(ClientLevel level, LocalPlayer self, boolean enabled) {
        if (!enabled) {
            if (busy()) clear();
            return;
        }
        if (!active && !searching) {
            // Where there are mountains: tried again in a while where there are none.
            if (daily.due()) summon(level, self, false);
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        yawO = yaw;
        yaw = facing(self);
        // Looked at once it stands there a moment: gone at once.
        boolean looked = age > STAND && Herobrine.lookedAt(level, self, new Vec3(x, y + 1.2, z));
        if (looked && !called) daily.seen();
        if (looked || age > (called ? CALLED_PATIENCE : PATIENCE) || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR
                || !Columns.drawn(level, self, x, z)) {
            clear();
        }
    }

    private float facing(LocalPlayer self) {
        return (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = x - camera.x, py = y - camera.y, pz = z - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        // Past the far plane: closer and smaller in proportion, the same on screen.
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // The right arm comes up and waves until the figure is gone.
        float t = age + partial, raise = 0f;
        if (t > STAND) {
            float up = Math.min(1f, (t - STAND) / 8f);
            raise = -(150f + 22f * (float) Math.sin((t - STAND) * 0.55)) * up;
        }
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        BoxFigure.draw(pose, collector, SHAPE, 0f, raise, 0f, Ufo.LIT);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }
}
