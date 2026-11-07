package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 10: now and then, by day or by night, a statue - a player carved in stone, in
 * marble or in mossy stone, one after the other - stands still on a far hill or open ground in
 * sight of the player, facing them. It never moves while it is on their screen; but each time
 * they look away and back, it has: a few blocks closer, crouched, its head or its body another
 * way. Once it stands near, the next time they look away it is gone for
 * good; gone as well after ten minutes, or if they walk up to it. Only on this screen.
 *
 * <p>Its spots are read in the loaded chunks or the far world ({@link Columns}), dry ground in
 * sight ({@link Sight}). It is on screen when it is inside the player's view, the field of
 * view of the options and a margin around it: a move is read once it has left it, and made
 * only while it, and the spot it goes to, are both off the screen. The statue is a player of
 * no one in one of the mod's statue skins ({@link SkinnedPlayer}), drawn by the game.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Statue implements Egg {
    private static final int NUMBER = 10;
    /** Stone, its darker patches, the hollows of its eyes. */
    /** How far off it may first stand, in blocks across. */
    private static final double MIN = 60, MAX = 250;
    /** Blocks across: the next look away takes it within {@link #CLOSE}; walking within {@link #NEAR} does at once. */
    private static final double CLOSE = 24, NEAR = 16;
    /** Blocks it comes closer at each look away, at least and at most; how far up or down it may step. */
    private static final double STEP_MIN = 6, STEP_MAX = 12, CLIMB = 8;
    /** Degrees around the field of view still counted on screen: the edge of the screen, fov effects. */
    private static final double MARGIN = 8;
    /** Ticks it must have been looked at before a look away moves it; ticks it stays at most; ticks a search may take. */
    private static final int SEEN = 10, LIFE = 12000, SEARCH = 400;
    /** Blocks from its feet to the middle of its head, where it is seen from. */
    private static final double HEAD = 1.7;
    /** Spots tried when it comes, and when it moves. */
    private static final int SPOTS = 48, MOVES = 6;

    /**
     * How it stands: its head bowed (positive, in degrees) or raised, its body turned away from
     * the player (degrees) with its head still on them, crouched or not.
     */
    private record Stance(float bow, float turn, boolean crouch) {
    }

    private static final Stance[] STANCES = {
            new Stance(0f, 0f, false),
            new Stance(35f, 0f, false),
            new Stance(-25f, 0f, false),
            new Stance(0f, 50f, false),
            new Stance(20f, 0f, true),
            new Stance(0f, 40f, true),
            new Stance(10f, 35f, false),
    };
    /** The statue skins, made for it, worn one after the other; the one the next statue wears. */
    private static final String[] LOOKS = {"statue/stone", "statue/marble", "statue/mossy"};
    private static int nextLook;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches and the moves: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, shown;
    private double x, y, z;
    /** Where it goes at the next chance it has off the screen; null until a move is read. */
    private @Nullable Vec3 next;
    /** Facing the player from where it stands, in the game's degrees; its turn to one side or the other. */
    private float yaw, side = 1f;
    private int pose, age, seen, moves, idle, searchAge;
    private @Nullable SkinnedPlayer figure;

    Statue(EggCalendar calendar) {
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

    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** For the game tests: the times it has moved since it came. */
    public int moves() {
        return moves;
    }

    /**
     * Looks for a far spot in sight, on high ground where it can, and stands there once it is
     * read: by chance, off the screen; or because the player asked ({@code called}: in front of
     * them; called again, it starts over). False if the easter eggs are off.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        double[] xs = new double[SPOTS], zs = new double[SPOTS];
        for (int i = 0; i < SPOTS; i++) {
            double bearing = called ? look + (random.nextDouble() * 2 - 1) * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            xs[i] = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            zs[i] = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
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
            List<Columns.Column> dry = new ArrayList<>();
            for (Columns.Column column : columns) {
                // By chance it is simply there the first time the player turns to it, never
                // appearing before their eyes.
                if (column.dry() && (called || !onScreen(self, new Vec3(column.x(), column.y() + HEAD, column.z())))) {
                    dry.add(column);
                }
            }
            double[][] heads = heads(dry);
            Sight.of(current, self, eye, heads[0], heads[1], heads[2]).thenAcceptAsync(sight -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Columns.Column> clear = new ArrayList<>();
                for (int i = 0; i < sight.length; i++) if (sight[i]) clear.add(dry.get(i));
                // On a hill where it can: the highest of a few spots taken at random.
                Columns.Column spot = null;
                for (int k = 0; k < 3; k++) {
                    Columns.Column one = Sight.any(clear, random);
                    if (one != null && (spot == null || one.y() > spot.y())) spot = one;
                }
                if (spot == null) {
                    if (called) EggLog.nowhere(NUMBER);
                    return;
                }
                begin(current, self, spot);
            }, game);
        }, game);
        return true;
    }

    /** Where its head would be on each of these spots: their x, y and z. */
    private static double[][] heads(List<Columns.Column> spots) {
        double[][] out = new double[3][spots.size()];
        for (int i = 0; i < spots.size(); i++) {
            Columns.Column spot = spots.get(i);
            out[0][i] = spot.x();
            out[1][i] = spot.y() + HEAD;
            out[2][i] = spot.z();
        }
        return out;
    }

    private void begin(ClientLevel current, LocalPlayer self, Columns.Column spot) {
        if (!called) calendar.shown(NUMBER);
        x = spot.x();
        y = spot.y();
        z = spot.z();
        face(self);
        pose = 0;
        figure = Puppets.numbered(new SkinnedPlayer(current, LOOKS[nextLook++ % LOOKS.length]));
        place();
        age = seen = moves = 0;
        shown = false;
        next = null;
        active = true;
        EggLog.came(NUMBER, x, y, z, self, called);
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!active) {
            // Rare, day or night: about once in an hour and a half.
            if (++idle > 6000 && quiet && random.nextInt(108000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        age++;
        double across = Math.hypot(self.getX() - x, self.getZ() - z);
        if (across < NEAR || age > LIFE || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        Vec3 head = new Vec3(x, y + HEAD, z);
        boolean now = onScreen(self, head);
        if (now) {
            if (inSight(current, self, head)) seen++;
        } else if (shown && seen >= SEEN) {
            // Looked at, then away: near, it is gone for good; otherwise it moves while unseen.
            if (across < CLOSE) {
                clear();
                return;
            }
            seen = 0;
            plan(current, self);
        }
        shown = now;
        Vec3 to = next;
        if (!now && to != null && !onScreen(self, to.add(0, HEAD, 0))) move(current, self, to);
    }

    /**
     * Reads where it goes next: a few blocks along the ground towards the player, a little to
     * one side or the other, dry and in sight; where none is, it stays and only changes its pose.
     */
    private void plan(ClientLevel current, LocalPlayer self) {
        double dx = self.getX() - x, dz = self.getZ() - z, across = Math.hypot(dx, dz);
        double ux = dx / across, uz = dz / across;
        double[] xs = new double[MOVES], zs = new double[MOVES];
        for (int i = 0; i < MOVES; i++) {
            // Never nearer than where walking up to it takes it.
            double step = Math.min(STEP_MIN + random.nextDouble() * (STEP_MAX - STEP_MIN), across - NEAR - 2);
            double aside = random.nextGaussian() * 2;
            xs[i] = Mth.floor(x + ux * Math.max(0, step) - uz * aside) + 0.5;
            zs[i] = Mth.floor(z + uz * Math.max(0, step) + ux * aside) + 0.5;
        }
        int mine = generation.incrementAndGet();
        next = null;
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        double from = y;
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Columns.Column> dry = new ArrayList<>();
            for (Columns.Column column : columns) {
                if (column.dry() && Math.abs(column.y() - from) <= CLIMB) dry.add(column);
            }
            double[][] heads = heads(dry);
            Sight.of(current, self, eye, heads[0], heads[1], heads[2]).thenAcceptAsync(sight -> {
                if (generation.get() != mine) return;
                for (int i = 0; i < sight.length; i++) {
                    if (sight[i]) {
                        Columns.Column spot = dry.get(i);
                        next = new Vec3(spot.x(), spot.y(), spot.z());
                        return;
                    }
                }
                next = new Vec3(x, y, z);
            }, game);
        }, game);
    }

    /** Off the screen, it is somewhere else: facing the player again, in another pose. */
    private void move(ClientLevel current, LocalPlayer self, Vec3 to) {
        next = null;
        x = to.x;
        y = to.y;
        z = to.z;
        face(self);
        int other = 1 + random.nextInt(STANCES.length - 1);
        pose = other == pose ? 1 + other % (STANCES.length - 1) : other;
        side = random.nextBoolean() ? 1f : -1f;
        place();
        moves++;
    }

    /** The statue where it stands now, in its stance, at once: it moves only when no one sees it. */
    private void place() {
        SkinnedPlayer statue = figure;
        if (statue == null) return;
        Stance stance = STANCES[pose];
        float body = yaw + stance.turn() * side;
        statue.setPos(x, y, z);
        statue.setYRot(body);
        statue.yBodyRot = body;
        statue.yHeadRot = yaw;
        statue.setXRot(stance.bow());
        statue.setShiftKeyDown(stance.crouch());
        statue.setPose(stance.crouch() ? Pose.CROUCHING : Pose.STANDING);
        statue.setOldPosAndRot();
        statue.yBodyRotO = statue.yBodyRot;
        statue.yHeadRotO = statue.yHeadRot;
    }

    private void face(LocalPlayer self) {
        yaw = (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
    }

    /** Nothing loaded between the player's eye and its head; where it stands past them, as the far world said. */
    private static boolean inSight(ClientLevel level, LocalPlayer self, Vec3 head) {
        if (!level.hasChunk(Mth.floor(head.x) >> 4, Mth.floor(head.z) >> 4)) return true;
        return Ghosts.inSight(level, self, self.getEyePosition(), head.x, head.y, head.z);
    }

    /** Inside the player's view, the field of view of the options and {@link #MARGIN} around it. */
    static boolean onScreen(LocalPlayer self, Vec3 point) {
        double fov = Minecraft.getInstance().options.fov().get();
        double half = Math.toRadians(fov / 2);
        double across = Math.atan(Math.tan(half) * aspect());
        return inView(self, point, half + Math.toRadians(MARGIN), across + Math.toRadians(MARGIN));
    }

    /**
     * Whether a point stands inside a view from the player's eye in the direction they look:
     * {@code high} radians up and down from its middle, {@code wide} radians to each side.
     */
    static boolean inView(LocalPlayer self, Vec3 point, double high, double wide) {
        Vec3 forward = self.getViewVector(1f);
        double yaw = Math.toRadians(self.getYRot());
        // To the side, level, and up from the view: the axes of the picture on screen.
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = forward.cross(right).normalize();
        Vec3 to = point.subtract(self.getEyePosition());
        double depth = to.dot(forward);
        if (depth <= 0) return false;
        return Math.abs(to.dot(right)) <= depth * Math.tan(Math.min(wide, 1.5))
                && Math.abs(to.dot(up)) <= depth * Math.tan(Math.min(high, 1.5));
    }

    /** The window's width over its height. */
    static double aspect() {
        var window = Minecraft.getInstance().getWindow();
        return window.getHeight() <= 0 ? 16.0 / 9.0 : (double) window.getWidth() / window.getHeight();
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        next = null;
        figure = null;
        level = null;
    }

    @Override
    public List<? extends Entity> figures() {
        SkinnedPlayer statue = figure;
        return active && statue != null ? List.of(statue) : List.of();
    }

    /** The game draws the statue; only the beam over it, called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        GhostRenderer.noteDistance(Math.sqrt(camera.distanceToSqr(x, y, z)) + 5);
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }
}
