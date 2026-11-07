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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 6: now and then, day or night, far off in a forest anywhere around the player,
 * as far as the horizon, a big hairy figure walks across their line of sight, turns its head
 * to look at them once, walks on, and is gone as soon as something stands between them - a
 * trunk, a rock - or the player comes near. Only on this screen.
 *
 * <p>Its way is read in the loaded chunks or the far world ({@link Columns}): in a forest, on
 * ground, never on the leaves the far world shows over its trees, so out there it walks where
 * the ground shows (a clearing, the edge of the wood). Part of its way must be in sight
 * ({@link Sight}): in a forest too thick to see into (a jungle), a way all under the leaves
 * would never be seen. It starts out of sight where it can, and once it has been in sight a
 * while, the first obstacle that hides it is where it goes. The figure is drawn of boxes
 * ({@link BigfootModel}), in the light where it walks.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Bigfoot {
    /** Blocks per tick it walks; blocks across under which it goes. */
    private static final double PACE = 0.075, NEAR = 18;
    /**
     * How far off it walks, in blocks across: from {@link #MIN} to {@link #MAX}; nearer, from
     * {@link #CLOSE}, only where the trees hide everything farther.
     */
    private static final double MIN = 80, MAX = 400, CLOSE = 30;
    /** Ways tried; stretches each way is read in; points of it in sight, at least. */
    private static final int WAYS = 64, ALONG = 6, IN_SIGHT = 2;
    /** Ticks it keeps its head turned to the player. */
    private static final int LOOK = 40;
    /** Ticks in sight before an obstacle may take it; ticks hidden that do; ticks it walks on, at most, to find one. */
    private static final int SEEN = 40, HIDDEN = 4, LONGER = 400;
    /** Ticks a search may take. */
    private static final int SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z, xo, yo, zo, dirX, dirZ, walked, length;
    /** Its way, where it is not loaded: the ground at each point read, and whether it is in sight there. */
    private double[] heights = new double[0];
    private boolean[] inSight = new boolean[0];
    private float yaw, headYaw, headYawO;
    private int age, steps, lookAt, light, idle, seen, hidden, searchAge;
    /** For the previews: standing still, in a stride of its own. */
    private boolean showcase;
    private float showSwing;

    Bigfoot(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /**
     * For the previews in the game tests: stands still at {@code (x, y, z)} facing {@code yaw},
     * its legs {@code swing} degrees apart, until cleared.
     */
    public void showcase(ClientLevel current, double x, double y, double z, float yaw, float swing) {
        clear();
        this.x = xo = x;
        this.y = yo = y;
        this.z = zo = z;
        this.yaw = yaw;
        headYaw = headYawO = 0f;
        showSwing = swing;
        showcase = true;
        level = current;
        light = BoxFigure.light(current, x, y, z);
        active = true;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it walks, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for a way across the line of sight, in a forest anywhere around the player, and
     * walks it once it is read: by chance, or because they asked ({@code called}: in front of
     * them where it can; called again, it starts over). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double reach = Sight.reach();
        if (reach <= CLOSE) {
            if (called) EggLog.nowhere(6);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        int walk = 220 + random.nextInt(100);
        double way = walk * PACE;
        double[] xs = new double[WAYS * (ALONG + 1)], zs = new double[xs.length], dirs = new double[WAYS * 2];
        double[] distances = new double[WAYS];
        for (int w = 0; w < WAYS; w++) {
            double bearing = called && w % 2 == 0 ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            // Most far off; a few nearer, for where the trees hide everything farther.
            boolean far = w < WAYS * 3 / 4 && reach > MIN;
            double distance = far ? MIN + random.nextDouble() * (Math.min(MAX, reach) - MIN)
                    : CLOSE + random.nextDouble() * (Math.min(MIN, reach) - CLOSE);
            distances[w] = distance;
            // Across the line of sight, from a few blocks before the spot.
            double side = random.nextBoolean() ? 1 : -1;
            double ax = Math.cos(bearing) * side, az = Math.sin(bearing) * side;
            double sx = self.getX() - Math.sin(bearing) * distance - ax * 8;
            double sz = self.getZ() + Math.cos(bearing) * distance - az * 8;
            dirs[2 * w] = ax;
            dirs[2 * w + 1] = az;
            for (int k = 0; k <= ALONG; k++) {
                xs[w * (ALONG + 1) + k] = sx + ax * way * k / ALONG;
                zs[w * (ALONG + 1) + k] = sz + az * way * k / ALONG;
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
            // On ground all along, in a forest most of the way, no step too high to walk.
            List<Integer> forest = new ArrayList<>();
            for (int w = 0; w < WAYS; w++) {
                boolean ok = true;
                int trees = 0;
                for (int k = 0; k <= ALONG && ok; k++) {
                    Columns.Column column = columns[w * (ALONG + 1) + k];
                    ok = column.dry() && (k == 0 || Math.abs(column.y() - columns[w * (ALONG + 1) + k - 1].y()) <= 4);
                    if (forest(column.biome())) trees++;
                }
                if (ok && trees * 2 > ALONG) forest.add(w);
            }
            int points = forest.size() * (ALONG + 1);
            double[] hx = new double[points], hy = new double[points], hz = new double[points];
            for (int i = 0; i < forest.size(); i++) {
                for (int k = 0; k <= ALONG; k++) {
                    Columns.Column column = columns[forest.get(i) * (ALONG + 1) + k];
                    hx[i * (ALONG + 1) + k] = column.x();
                    hy[i * (ALONG + 1) + k] = column.y() + 2.3;
                    hz[i * (ALONG + 1) + k] = column.z();
                }
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(sight -> {
                if (generation.get() != mine) return;
                searching = false;
                // Seen somewhere along: far before near, out of sight at the start before not,
                // then any of those.
                List<Integer> ways = new ArrayList<>();
                int bestRank = -1;
                for (int i = 0; i < forest.size(); i++) {
                    int along = 0;
                    for (int k = 1; k <= ALONG; k++) if (sight[i * (ALONG + 1) + k]) along++;
                    if (along < IN_SIGHT) continue;
                    int rank = (distances[forest.get(i)] >= MIN ? 2 : 0) + (sight[i * (ALONG + 1)] ? 0 : 1);
                    if (rank > bestRank) ways.clear();
                    if (rank >= bestRank) {
                        ways.add(i);
                        bestRank = rank;
                    }
                }
                Integer chosen = Sight.any(ways, random);
                if (chosen == null) {
                    if (called) EggLog.nowhere(6);
                    return;
                }
                int best = chosen, w = forest.get(best);
                double[] ys = new double[ALONG + 1];
                boolean[] seenAt = new boolean[ALONG + 1];
                for (int k = 0; k <= ALONG; k++) {
                    ys[k] = columns[w * (ALONG + 1) + k].y();
                    seenAt[k] = sight[best * (ALONG + 1) + k];
                }
                begin(current, xs[w * (ALONG + 1)], zs[w * (ALONG + 1)], dirs[2 * w], dirs[2 * w + 1], ys, seenAt, way, walk);
                EggLog.came(6, x, y, z, self, called);
            }, game);
        }, game);
        return true;
    }

    private void begin(ClientLevel current, double sx, double sz, double ax, double az,
                       double[] ys, boolean[] seenAt, double way, int walk) {
        if (!called) calendar.shown(6);
        dirX = ax;
        dirZ = az;
        x = xo = sx;
        z = zo = sz;
        heights = ys;
        inSight = seenAt;
        double ground = ground(current, x, z);
        y = yo = Double.isNaN(ground) ? ys[0] : ground;
        yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        headYaw = headYawO = 0f;
        length = way;
        steps = walk;
        lookAt = steps * 2 / 5;
        walked = 0;
        age = 0;
        seen = hidden = 0;
        light = BoxFigure.light(current, x, y, z);
        active = true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (showcase) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!active) {
            // Rare, day or night: about once in an hour and a half, where a forest stands around.
            if (++idle > 6000 && quiet && random.nextInt(108000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        headYawO = headYaw;
        x += dirX * PACE;
        z += dirZ * PACE;
        walked += PACE;
        double ground = ground(current, x, z);
        if (Double.isNaN(ground)) ground = alongWay();
        y += (ground - y) * 0.3;
        // Once on its way, it turns its head to the player, then looks ahead again.
        float toPlayer = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z)) - yaw);
        float wanted = age > lookAt && age < lookAt + LOOK ? Mth.clamp(toPlayer, -100f, 100f) : 0f;
        headYaw += Mth.clamp(wanted - headYaw, -8f, 8f);
        light = BoxFigure.light(current, x, y, z);
        // Seen for a while, it goes behind the first thing that hides it; past its walk, as soon
        // as something does, or anyway a little later.
        if (visible(current, self)) {
            seen++;
            hidden = 0;
        } else {
            hidden++;
        }
        boolean gone = hidden >= HIDDEN && (seen >= SEEN || age > steps);
        if (gone || age > steps + LONGER || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR
                || !Columns.drawn(current, self, x, z)) {
            clear();
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = showcase = false;
        level = null;
    }

    /** The ground read along its way, where it is now: between two points, linearly. */
    private double alongWay() {
        double f = Mth.clamp(walked / length, 0, 1) * ALONG;
        int i = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - i, heights[i], heights[i + 1]);
    }

    /**
     * In sight of the player: its head or its chest through the loaded blocks; where it is not
     * loaded, as the far world said of the nearest point of its way.
     */
    private boolean visible(ClientLevel level, LocalPlayer self) {
        if (!Columns.loaded(level, x, z)) {
            return inSight[(int) Math.round(Mth.clamp(walked / length, 0, 1) * ALONG)];
        }
        Vec3 eye = self.getEyePosition();
        return Ghosts.inSight(level, self, eye, x, y + 2.3, z) || Ghosts.inSight(level, self, eye, x, y + 1.4, z);
    }

    /** A forest, by the name of its biome: forest, taiga, jungle, woods, groves, pale gardens. */
    private static boolean forest(String biome) {
        String id = biome.toLowerCase(Locale.ROOT);
        return id.contains("forest") || id.contains("taiga") || id.contains("jungle") || id.contains("wood")
                || id.contains("grove") || id.contains("pale_garden");
    }

    /** The ground of a loaded column, under its trees: below the trunks and bamboo the heightmap stands on. NaN where not loaded. */
    private static double ground(ClientLevel level, double x, double z) {
        if (!Columns.loaded(level, x, z)) return Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(
                bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
        for (int i = 0; i < 32; i++) {
            BlockState state = level.getBlockState(at);
            if (!state.is(BlockTags.LOGS) && !state.is(Blocks.BAMBOO)) break;
            at.move(0, -1, 0);
        }
        return at.getY() + 1;
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
        // A long, slow stride.
        float swing = (float) Math.sin((walked + PACE * partial) * 2.2) * 28f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Yaw turns the other way round from the pose's rotations, the body's as the head's.
        float stride = showcase ? showSwing : swing, turned = -Mth.lerp(partial, headYawO, headYaw);
        BigfootModel.draw(pose, collector, stride, turned, light);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }
}
