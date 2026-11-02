package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 19: now and then, at night, far off on a hilltop or on open dry ground, a
 * campfire burns, its smoke rising. No one is ever seen by it. As the player comes near it is
 * put out, and for a minute only the cold campfire shows where it burned.
 *
 * <p>Its place is read around the player, in the loaded chunks or the far world
 * ({@link Columns}): dry, flat enough, under the open sky, in sight ({@link Sight}), and the
 * highest of those found, so that it stands on a hill where there is one. The fire and its
 * smoke are the game's own ({@link GameFire}), as easter egg 9's ({@link Sect}).
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Campfire implements Egg {
    /** How far off it may burn, in blocks across; under which it is out. */
    private static final double MIN = 100, MAX = 350, NEAR = 48;
    /** Highest spots within this many blocks of the highest are all as good. */
    private static final double TOP = 0.5;
    /** Spots tried; of them, ahead of the player when called. */
    private static final int SPOTS = 160, AHEAD = 120;
    /** Half the angle ahead the spots are tried in when called, in radians. */
    private static final double SECTOR = Math.toRadians(30);
    /** Ticks it burns at most; it stays cold; a search may take. */
    private static final int LIFE = 9600, ASH_LIFE = 1200, SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, burning, ash, called;
    private double x, y, z;
    private @Nullable GameFire fire;
    private int age, ashAge, idle, searchAge;

    Campfire(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 19;
    }

    @Override
    public boolean busy() {
        return searching || burning || ash;
    }

    @Override
    public @Nullable Vec3 position() {
        return burning || ash ? new Vec3(x, y, z) : null;
    }

    /** The fire is burning. For the game tests. */
    public boolean burning() {
        return burning;
    }

    /** Only the cold campfire is left. For the game tests. */
    public boolean ash() {
        return ash;
    }

    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(19);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        // Each spot, then two blocks off it on two sides: flat enough for a fire.
        double[] xs = new double[SPOTS * 3], zs = new double[xs.length];
        for (int s = 0; s < SPOTS; s++) {
            double bearing = called && s < AHEAD ? look + (random.nextDouble() * 2 - 1) * SECTOR
                    : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            xs[3 * s] = cx;
            zs[3 * s] = cz;
            xs[3 * s + 1] = cx + 2;
            zs[3 * s + 1] = cz;
            xs[3 * s + 2] = cx;
            zs[3 * s + 2] = cz + 2;
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
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[3 * s];
                if (!ground(current, spot)) continue;
                boolean flat = true;
                for (int k = 1; k < 3 && flat; k++) {
                    Columns.Column side = columns[3 * s + k];
                    flat = side.dry() && Math.abs(side.y() - spot.y()) <= 1;
                }
                if (flat) open.add(spot);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                hx[i] = open.get(i).x();
                hy[i] = open.get(i).y() + 1.2;
                hz[i] = open.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                // The highest in sight: a hilltop where there is one.
                double highest = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < seen.length; i++) if (seen[i]) highest = Math.max(highest, open.get(i).y());
                List<Columns.Column> tops = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) {
                    if (seen[i] && open.get(i).y() >= highest - TOP) tops.add(open.get(i));
                }
                Columns.Column chosen = Sight.any(tops, random);
                if (chosen == null) {
                    if (called) EggLog.nowhere(19);
                    return;
                }
                begin(current, chosen);
                EggLog.came(19, x, y, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /**
     * Open dry ground: out of the water, under no leaves, not on them nor on ice; under the
     * open sky where it is loaded (the loaded column gives the ground under the trees).
     */
    private static boolean ground(ClientLevel level, Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        if (block != null && (block.is(BlockTags.LEAVES) || block.is(BlockTags.ICE))) return false;
        return !Columns.loaded(level, column.x(), column.z())
                || level.canSeeSky(BlockPos.containing(column.x(), column.y(), column.z()));
    }

    private void begin(ClientLevel current, Columns.Column spot) {
        if (!called) calendar.shown(19);
        x = spot.x();
        y = spot.y();
        z = spot.z();
        fire = GameFire.at(current, x, y, z);
        age = ashAge = 0;
        ash = false;
        burning = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!burning && !ash) {
            // Rare, and only at night: about once in forty minutes of it.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(48000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (!Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        if (burning) {
            age++;
            if (fire != null) fire.tick(random);
            // Out as the player comes, at the end of its time, and at dawn if it came by chance.
            boolean dawn = !called && current.isBrightOutside();
            if (Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || age > LIFE || dawn) {
                burning = false;
                ash = true;
                ashAge = 0;
                if (fire != null) fire.lit(false);
            }
        } else if (++ashAge > ASH_LIFE) {
            clear();
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = burning = ash = false;
        fire = null;
        level = null;
    }

    @Override
    public List<? extends Entity> figures() {
        GameFire shown = fire;
        return shown != null && (burning || ash) ? shown.figures() : List.of();
    }

    /** The game draws the campfire and its smoke; only the beam over it, called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!burning && !ash) return;
        // Its smoke rises high over it: the far plane past the top of it.
        GhostRenderer.noteDistance(Math.sqrt(camera.distanceToSqr(x, y, z)) + 24);
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }
}
