package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 9: now and then, at night, far off on open ground, six to eight people in white
 * hooded robes, their faces hidden, crouch in a circle round a campfire, one of them standing. Looked at through a
 * spyglass, they all turn their heads, then their bodies, slowly to the player, and keep
 * facing them. Once they have been seen so, as soon as the player lowers the spyglass, they
 * stand up and come, each on a heading of its own, walking - or, some nights, running -
 * towards the player. One hidden behind something once it has come a few seconds is gone;
 * one in sight comes until it is near, and is gone. Never seen through a spyglass, they stay
 * until dawn, and are gone, fire and all, if the player comes near. Only on this screen.
 *
 * <p>Their place is read around the player, in the loaded chunks or the far world
 * ({@link Columns}): dry, flat, open ground in sight ({@link Sight}), and the ground on the
 * way to the player, for where they walk past the loaded chunks. They are players of no one,
 * in a skin made for them - a white hooded robe, no face - drawn by the game as far players are; their
 * fire is a campfire of the game's own with its own smoke ({@link GameFire}).
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Sect implements Egg {
    /** The robes they wear, skins made for them - white, hooded, the face hidden - each its own at random. */
    private static final String[] ROBES = {"sect/veil", "sect/mask", "sect/grin"};
    /** Blocks from the fire to each figure round it. */
    private static final double RING = 2.6;
    /** Figures round the fire, at least; more, at most. */
    private static final int FEW = 6, MORE = 2;
    /** Blocks per tick they walk, or run as a player sprints. */
    private static final double WALK_PACE = 0.16, RUN_PACE = 0.28;
    /** How far off they may be, in blocks across; nearer than which they go, fire and all; a walker goes. */
    private static final double MIN = 80, MAX = 300, NEAR = 60, REACHED = 40;
    /** Spots tried; of them, ahead of the player when called; points read on the way to the player. */
    private static final int SPOTS = 48, AHEAD = 32, ALONG = 6;
    /** Ticks looked at through a spyglass before they notice. */
    private static final int NOTICE = 6;
    /** Ticks a figure walks before one hidden is gone; ticks hidden that do; ticks between looks. */
    private static final int HIDE_AFTER = 140, HIDDEN = 6, LOOK_EVERY = 4;
    /** Ticks they sit at most; walk at most; the fire burns once they are all gone; a search may take. */
    private static final int LIFE = 14000, WALK = 1200, EMBERS = 1200, SEARCH = 400;
    /** Degrees per tick a head turns, a body turns to look, a walker turns to its way. */
    private static final float HEAD_TURN = 5f, BODY_TURN = 3f, WALK_TURN = 6f;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches and the looks: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, noticed, scattering, fireOn, running, showcasing;
    /** For the game tests: stand in for the player's spyglass, and for whether they run, while set. */
    private @Nullable Boolean scopingStandIn, runningStandIn;
    /** The fire; the player's feet when they came, which the ground on the way was read towards. */
    private double fx, fy, fz, toX, toZ;
    private @Nullable GameFire fire;
    /** The ground on the way to the player, {@link #ALONG} stretches, from the fire. */
    private double[] heights = new double[0];
    private int count;
    private RemotePlayer[] people = new RemotePlayer[0];
    private double[] x = new double[0], y = x, z = x;
    /** World yaws of each body and head, in degrees; the heading each takes off the player's. */
    private float[] body = new float[0], head = body, off = body;
    /** Ticks before each turns to the player once noticed, stands up once they scatter; ticks it has walked, been hidden. */
    private int[] turnAt = new int[0], standAt = turnAt, walking = turnAt, hidden = turnAt;
    private boolean[] standing = new boolean[0], gone = standing;
    private int age, lookedAt, sinceNoticed, sinceScatter, emptyFor, idle, searchAge;

    Sect(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 9;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The fire they sit round, while they are there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(fx, fy, fz) : null;
    }

    /** They have been seen through a spyglass. For the game tests. */
    public boolean noticed() {
        return active && noticed;
    }

    /** They run, rather than walk, once they come. For the game tests. */
    public boolean running() {
        return running;
    }

    /** Figures still there. For the game tests. */
    public int left() {
        int n = 0;
        for (int i = 0; active && i < count; i++) if (!gone[i]) n++;
        return n;
    }

    /**
     * The widest angle, in degrees, between where a figure still there faces, head or body,
     * and the way to {@code from}. For the game tests.
     */
    public float turnedAway(Vec3 from) {
        float widest = 0f;
        for (int i = 0; active && i < count; i++) {
            if (gone[i]) continue;
            float to = yawTo(x[i], z[i], from.x, from.z);
            widest = Math.max(widest, Math.max(Math.abs(Mth.wrapDegrees(head[i] - to)), Math.abs(Mth.wrapDegrees(body[i] - to))));
        }
        return widest;
    }

    /** Blocks across from {@code from} to the nearest figure still there; infinite when none. For the game tests. */
    public double nearest(Vec3 from) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; active && i < count; i++) {
            if (!gone[i]) nearest = Math.min(nearest, Math.hypot(x[i] - from.x, z[i] - from.z));
        }
        return nearest;
    }

    /**
     * For the game tests, which cannot always hold a spyglass up: true or false stands in for
     * whether the player looks through one; null reads the player again.
     */
    public void standIn(@Nullable Boolean scoping) {
        scopingStandIn = scoping;
    }

    /**
     * For the previews in the game tests: a row of them at {@code (x, y, z)}, facing -z, one of
     * each of {@code robes} standing, then the same crouched, until cleared.
     */
    public void showcase(ClientLevel current, double x, double y, double z, String[] robes) {
        clear();
        count = robes.length * 2;
        people = new RemotePlayer[count];
        this.x = new double[count];
        this.y = new double[count];
        this.z = new double[count];
        body = new float[count];
        head = new float[count];
        off = new float[count];
        turnAt = new int[count];
        standAt = new int[count];
        walking = new int[count];
        hidden = new int[count];
        standing = new boolean[count];
        gone = new boolean[count];
        for (int i = 0; i < count; i++) {
            this.x[i] = x + (i % robes.length - (robes.length - 1) / 2.0) * 1.6;
            this.y[i] = y;
            this.z[i] = z + (i < robes.length ? 0 : 3);
            body[i] = head[i] = 180f;
            standing[i] = i < robes.length;
            people[i] = Puppets.numbered(new SkinnedPlayer(current, robes[i % robes.length]));
            pose(people[i], standing[i]);
            place(i, false);
        }
        showcasing = true;
        level = current;
        active = true;
    }

    /** For the game tests: true or false chooses whether the next ones run; null leaves it to chance. */
    public void standInRunning(@Nullable Boolean run) {
        runningStandIn = run;
    }

    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(9);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        // Each spot, four points round it where they sit, then the way to the player.
        int per = 5 + ALONG - 1;
        double[] xs = new double[SPOTS * per], zs = new double[xs.length];
        double px = self.getX(), pz = self.getZ();
        for (int s = 0; s < SPOTS; s++) {
            double bearing = called && s < AHEAD ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(px - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(pz + Math.cos(bearing) * distance) + 0.5;
            xs[s * per] = cx;
            zs[s * per] = cz;
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                xs[s * per + 1 + k] = cx + Math.cos(a) * (RING + 0.5);
                zs[s * per + 1 + k] = cz + Math.sin(a) * (RING + 0.5);
            }
            for (int k = 1; k < ALONG; k++) {
                xs[s * per + 4 + k] = Mth.lerp((double) k / ALONG, cx, px);
                zs[s * per + 4 + k] = Mth.lerp((double) k / ALONG, cz, pz);
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        double feet = self.getY();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            // Dry, open and flat where they sit.
            List<Integer> open = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[s * per];
                boolean ok = ground(current, spot);
                for (int k = 1; k <= 4 && ok; k++) {
                    Columns.Column round = columns[s * per + k];
                    ok = round.dry() && Math.abs(round.y() - spot.y()) <= 1;
                }
                if (ok) open.add(s);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                Columns.Column spot = columns[open.get(i) * per];
                hx[i] = spot.x();
                hy[i] = spot.y() + 1.6;
                hz[i] = spot.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Integer> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(open.get(i));
                Integer s = Sight.any(clear, random);
                if (s == null) {
                    if (called) EggLog.nowhere(9);
                    return;
                }
                // The ground on the way, each point unknown taking the one before it.
                double[] ys = new double[ALONG + 1];
                ys[0] = columns[s * per].y();
                for (int k = 1; k < ALONG; k++) {
                    double at = columns[s * per + 4 + k].y();
                    ys[k] = Double.isNaN(at) ? ys[k - 1] : at;
                }
                ys[ALONG] = feet;
                begin(current, columns[s * per], ys, px, pz);
                EggLog.came(9, fx, fy, fz, self, called);
            }, game);
        }, game);
        return true;
    }

    /** Open dry ground: out of the water, not on leaves nor ice, under the sky where it is loaded. */
    private static boolean ground(ClientLevel level, Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        if (block != null && (block.is(BlockTags.LEAVES) || block.is(BlockTags.ICE))) return false;
        return !Columns.loaded(level, column.x(), column.z())
                || level.canSeeSky(BlockPos.containing(column.x(), column.y(), column.z()));
    }

    private void begin(ClientLevel current, Columns.Column spot, double[] ys, double px, double pz) {
        if (!called) calendar.shown(9);
        fx = spot.x();
        fy = spot.y();
        fz = spot.z();
        toX = px;
        toZ = pz;
        heights = ys;
        fire = GameFire.at(current, fx, fy, fz);
        running = runningStandIn != null ? runningStandIn : random.nextBoolean();
        count = FEW + random.nextInt(MORE + 1);
        people = new RemotePlayer[count];
        x = new double[count];
        y = new double[count];
        z = new double[count];
        body = new float[count];
        head = new float[count];
        off = new float[count];
        turnAt = new int[count];
        standAt = new int[count];
        walking = new int[count];
        hidden = new int[count];
        standing = new boolean[count];
        gone = new boolean[count];
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = start + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.25;
            x[i] = fx + Math.cos(a) * RING;
            z[i] = fz + Math.sin(a) * RING;
            double ground = loadedGround(current, x[i], z[i]);
            y[i] = Double.isNaN(ground) ? fy : ground;
            body[i] = head[i] = yawTo(x[i], z[i], fx, fz);
            // The one standing turns first and is up already; the others one after the other.
            turnAt[i] = i == 0 ? 0 : 10 + random.nextInt(30);
            standAt[i] = i == 0 ? 0 : 4 + random.nextInt(30);
            off[i] = (random.nextFloat() * 2 - 1) * 20f;
            standing[i] = i == 0;
            RemotePlayer person = Puppets.numbered(new SkinnedPlayer(current, ROBES[random.nextInt(ROBES.length)]));
            pose(person, standing[i]);
            people[i] = person;
            place(i, false);
            person.setOldPosAndRot();
            person.yBodyRotO = person.yBodyRot;
            person.yHeadRotO = person.yHeadRot;
        }
        age = lookedAt = sinceNoticed = sinceScatter = emptyFor = 0;
        noticed = scattering = false;
        fireOn = true;
        active = true;
    }

    /** Crouched round the fire, or up on its feet. */
    private static void pose(RemotePlayer person, boolean up) {
        person.setShiftKeyDown(!up);
        person.setPose(up ? Pose.STANDING : Pose.CROUCHING);
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (showcasing) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!active) {
            // Rare, and only at night: about once in an hour of it.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(72000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        age++;
        double fromFire = Math.hypot(self.getX() - fx, self.getZ() - fz);
        boolean fireDrawn = Columns.drawn(current, self, fx, fz);
        if (!scattering && (fromFire < NEAR || !fireDrawn)) {
            clear();
            return;
        }
        if (fireOn && (fromFire < NEAR || !fireDrawn)) fireOn = false;
        boolean scoping = scopingStandIn != null ? scopingStandIn : self.isScoping();
        if (!noticed && scoping && seenThroughSpyglass(current, self)) {
            noticed = ++lookedAt >= NOTICE;
        }
        // Seen, then the spyglass lowered: they come.
        if (noticed && !scattering && !scoping) scattering = true;
        // Dawn, or too long a night, sends them all away.
        boolean over = (!called && current.isBrightOutside()) || (!scattering && age > LIFE)
                || (scattering && sinceScatter > WALK);
        if (over) fireOn = fireOn && scattering && !current.isBrightOutside();
        if (noticed) sinceNoticed++;
        if (scattering) sinceScatter++;
        if (scattering && age % LOOK_EVERY == 0) look(current, self);
        int left = 0;
        for (int i = 0; i < count; i++) {
            if (gone[i]) continue;
            boolean moved = false;
            if (scattering) moved = walk(current, self, i);
            else if (noticed) turn(self, i);
            // Near the player, hidden a moment once it has come a while, or the night over: gone.
            boolean near = scattering && Math.hypot(self.getX() - x[i], self.getZ() - z[i]) < REACHED;
            boolean behind = walking[i] >= HIDE_AFTER && hidden[i] >= HIDDEN;
            if (over || near || behind || !Columns.drawn(current, self, x[i], z[i])) {
                gone[i] = true;
                continue;
            }
            place(i, moved);
            left++;
        }
        if (fire != null) {
            // Put out with the game's puff of smoke when they leave it; smoking while it burns.
            fire.lit(fireOn);
            fire.tick(random);
        }
        if (left == 0 && (!fireOn || ++emptyFor > EMBERS)) clear();
    }

    /** The player looks at the fire through a spyglass: the middle of their view on it, and nothing loaded in between. */
    private boolean seenThroughSpyglass(ClientLevel current, LocalPlayer self) {
        Vec3 eye = self.getEyePosition(), to = new Vec3(fx, fy + 1.2, fz).subtract(eye);
        // The spyglass shows a tenth of the view: a little of its middle, or a few blocks round them.
        double cone = Math.max(Math.toRadians(2.5), Math.atan2(4, to.length()));
        if (self.getViewVector(1f).dot(to.normalize()) <= Math.cos(cone)) return false;
        return !Columns.loaded(current, fx, fz) || Ghosts.inSight(current, self, eye, fx, fy + 1.2, fz);
    }

    /** Noticed: the head turns to the player first, then the body after it. */
    private void turn(LocalPlayer self, int i) {
        float to = yawTo(x[i], z[i], self.getX(), self.getZ());
        if (sinceNoticed > turnAt[i]) body[i] = Mth.approachDegrees(body[i], to, BODY_TURN);
        // A head turns no farther than a neck lets it from the body.
        float wanted = body[i] + Mth.clamp(Mth.wrapDegrees(to - body[i]), -80f, 80f);
        head[i] = Mth.approachDegrees(head[i], wanted, HEAD_TURN);
    }

    /** Scattering: up on its feet in turn, then each walks or runs towards the player on a heading of its own, along the ground. */
    private boolean walk(ClientLevel current, LocalPlayer self, int i) {
        if (sinceScatter < standAt[i]) return false;
        if (!standing[i]) {
            standing[i] = true;
            pose(people[i], true);
            return false;
        }
        float to = yawTo(x[i], z[i], self.getX(), self.getZ()) + off[i];
        body[i] = Mth.approachDegrees(body[i], to, WALK_TURN);
        head[i] = body[i];
        double yaw = Math.toRadians(body[i]), pace = running ? RUN_PACE : WALK_PACE;
        x[i] -= Math.sin(yaw) * pace;
        z[i] += Math.cos(yaw) * pace;
        walking[i]++;
        double ground = loadedGround(current, x[i], z[i]);
        if (Double.isNaN(ground)) ground = onTheWay(x[i], z[i]);
        y[i] += (ground - y[i]) * 0.3;
        return true;
    }

    /**
     * Whether those that have come a while are hidden from the player: their head and chest,
     * through the loaded blocks, or past the far world's terrain. Counted in {@link #hidden}.
     */
    private void look(ClientLevel current, LocalPlayer self) {
        List<Integer> asked = new ArrayList<>();
        for (int i = 0; i < count; i++) if (!gone[i] && walking[i] >= HIDE_AFTER - LOOK_EVERY * 2) asked.add(i);
        if (asked.isEmpty()) return;
        double[] xs = new double[asked.size() * 2], ys = new double[xs.length], zs = new double[xs.length];
        for (int k = 0; k < asked.size(); k++) {
            int i = asked.get(k);
            xs[2 * k] = xs[2 * k + 1] = x[i];
            zs[2 * k] = zs[2 * k + 1] = z[i];
            ys[2 * k] = y[i] + 1.6;
            ys[2 * k + 1] = y[i] + 1.0;
        }
        int mine = generation.get();
        Sight.of(current, self, self.getEyePosition(), xs, ys, zs).thenAcceptAsync(seen -> {
            if (generation.get() != mine) return;
            for (int k = 0; k < asked.size(); k++) {
                int i = asked.get(k);
                if (seen[2 * k] || seen[2 * k + 1]) hidden[i] = 0;
                else hidden[i] += LOOK_EVERY;
            }
        }, Minecraft.getInstance());
    }

    /** The puppet where the figure is now, its legs walking when it {@code moved}. */
    private void place(int i, boolean moved) {
        RemotePlayer person = people[i];
        person.setOldPosAndRot();
        person.yBodyRotO = person.yBodyRot;
        person.yHeadRotO = person.yHeadRot;
        person.setPos(x[i], y[i], z[i]);
        person.setYRot(body[i]);
        person.yBodyRot = body[i];
        person.yHeadRot = head[i];
        person.setSprinting(moved && running);
        Puppets.walk(person, moved ? (float) Math.min(1.0, (running ? RUN_PACE : WALK_PACE) * 4) : 0f, 0.4f, 1f);
    }

    /** The top of a loaded column; NaN where it is not loaded. */
    private static double loadedGround(ClientLevel level, double x, double z) {
        if (!Columns.loaded(level, x, z)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** The ground read on the way from the fire to where the player was, at the point nearest to this one. */
    private double onTheWay(double px, double pz) {
        double wx = toX - fx, wz = toZ - fz, length = wx * wx + wz * wz;
        double t = length <= 0 ? 0 : Mth.clamp(((px - fx) * wx + (pz - fz) * wz) / length, 0, 1);
        double f = t * ALONG;
        int k = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - k, heights[k], heights[k + 1]);
    }

    /** The yaw, as the game counts it, that faces from one point to another. */
    private static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = showcasing = false;
        fire = null;
        people = new RemotePlayer[0];
        count = 0;
        level = null;
    }

    @Override
    public List<? extends Entity> figures() {
        if (!active) return List.of();
        List<Entity> shown = new ArrayList<>(count + 1);
        GameFire burning = fire;
        if (burning != null) shown.addAll(burning.figures());
        for (int i = 0; i < count; i++) if (!gone[i]) shown.add(people[i]);
        return shown;
    }

    /** The game draws them and their fire; the far plane past the smoke, and the beam, called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        GhostRenderer.noteDistance(Math.sqrt(camera.distanceToSqr(fx, fy, fz)) + 20);
        if (called) CalledBeam.submit(pose, camera, collector, fx, fy, fz);
    }
}
