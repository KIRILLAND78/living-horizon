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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 17: now and then, day or night, far out at sea, a huge dark back rises out of
 * the water, a ridge of small fins along it, glides on a little and sinks; a moment later a
 * giant tail rises a little farther on, hangs dripping, and slaps down on the water with a
 * splash; then everything is gone. Gone at once if the player comes near. Only on this screen.
 *
 * <p>Its water is read in the loaded chunks or the far world ({@link Columns}): in an ocean
 * biome, water all round it and along its way, in sight ({@link Sight}). It is drawn of solid
 * boxes in the light where it swims ({@link BoxFigure#light}), dark at night; whatever of it is
 * under the surface is cut off, so that it rises out of the water and sinks into it.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class SeaCreature implements Egg {
    private static final int NUMBER = 17;
    /** Its skin, the sheen along its top, its fins and flukes, the foam and the spray. */
    private static final int SKIN = 0x34404C, SHEEN = 0x4A5866, FIN = 0x2A333D, FOAM = 0xE6EEF2;
    /** Blocks per tick it swims on, back up or under; blocks across under which it is gone. */
    private static final double SPEED = 0.08, NEAR = 60;
    /** How far off it may come, in blocks across. */
    private static final double MIN = 100, MAX = 350;
    /** Blocks off its spot where open water is read, on four sides; along its way, ahead. */
    private static final int AROUND = 12, AHEAD = 16, FARTHER = 30;
    /** Spots tried; columns read for each; ticks a search may take. */
    private static final int SPOTS = 84, PER = 7, SEARCH = 400;
    /** Its back: up by RISE, sinking from GLIDE, under at SUNK. */
    private static final int RISE = 60, GLIDE = 140, SUNK = 200;
    /** Its tail: rising from TAIL, hanging from HANG, slapping down from SLAP, down at SLAPPED. */
    private static final int TAIL = 260, HANG = 310, SLAP = 350, SLAPPED = 362;
    /** The splash lasts until SPLASHED; all of it is over at END. */
    private static final int SPLASHED = 420, END = 440;
    /** Blocks the tail stands out of the water, at most. */
    private static final float TAIL_HEIGHT = 5f;
    /** Drops of the splash; how fast they fall, blocks per tick each tick. */
    private static final int DROPS = 22;
    private static final float GRAVITY = 0.035f;
    /** Under the surface, what is drawn is cut off a little below it, to meet the water. */
    private static final float WATERLINE = -0.15f;
    /** About once in an hour near an ocean. */
    private static final int CHANCE = 72000;
    /** Blocks around the player where an ocean counts as near. */
    private static final double SHORE = 96;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    /** Where it came up, which way it swims, the top of the water there. */
    private double x0, z0, dirX, dirZ, surface;
    private float yaw;
    private int age, light, idle, searchAge;
    /** The splash's drops: where each starts across the tail, how it flies, how big it is. */
    private float[] drops = new float[0];

    SeaCreature(EggCalendar calendar) {
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
        if (!active) return null;
        double along = along(age);
        return new Vec3(x0 + dirX * along, surface, z0 + dirZ * along);
    }

    /** Ticks since it came, -1 while it is not there. For the game tests. */
    public int age() {
        return active ? age : -1;
    }

    /** Blocks it has swum at {@code t} ticks: on until its tail comes up, which stays where it rose. */
    private static double along(float t) {
        return SPEED * Math.min(t, TAIL);
    }

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
        // Each spot, four around it and two along its way: water all round and all along.
        double[] xs = new double[SPOTS * PER], zs = new double[xs.length], dirs = new double[SPOTS * 2];
        int[][] around = {{0, 0}, {AROUND, 0}, {-AROUND, 0}, {0, AROUND}, {0, -AROUND}};
        for (int s = 0; s < SPOTS; s++) {
            double bearing = called ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            double heading = random.nextDouble() * Math.PI * 2;
            double dx = -Math.sin(heading), dz = Math.cos(heading);
            dirs[2 * s] = dx;
            dirs[2 * s + 1] = dz;
            int i = s * PER;
            for (int[] d : around) {
                xs[i] = cx + d[0];
                zs[i++] = cz + d[1];
            }
            xs[i] = cx + dx * AHEAD;
            zs[i++] = cz + dz * AHEAD;
            xs[i] = cx + dx * FARTHER;
            zs[i] = cz + dz * FARTHER;
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
            List<Integer> open = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column middle = columns[s * PER];
                double across = Math.hypot(middle.x() - self.getX(), middle.z() - self.getZ());
                boolean ok = across >= MIN && across <= far && ocean(middle.biome());
                for (int k = 0; k < PER && ok; k++) ok = columns[s * PER + k].known() && columns[s * PER + k].water();
                if (ok) open.add(s);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                Columns.Column middle = columns[open.get(i) * PER];
                hx[i] = middle.x();
                hy[i] = middle.y() + 1.0;
                hz[i] = middle.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Integer> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(open.get(i));
                Integer s = Sight.any(clear, random);
                if (s == null) {
                    if (called) EggLog.nowhere(NUMBER);
                    return;
                }
                begin(columns[s * PER], dirs[2 * s], dirs[2 * s + 1]);
                EggLog.came(NUMBER, x0, surface, z0, self, called);
            }, game);
        }, game);
        return true;
    }

    /** The sea, by the name of its biome. */
    private static boolean ocean(String biome) {
        return biome.toLowerCase(Locale.ROOT).contains("ocean");
    }

    private void begin(Columns.Column spot, double dx, double dz) {
        if (!called) calendar.shown(NUMBER);
        x0 = spot.x();
        z0 = spot.z();
        // The top of the water block, a little under the column's top.
        surface = spot.y() - 0.12;
        dirX = dx;
        dirZ = dz;
        yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        // Where the tail lies once down, from its root across to its flukes' ends.
        drops = new float[DROPS * 7];
        for (int i = 0; i < DROPS; i++) {
            int k = i * 7;
            drops[k] = (random.nextFloat() - 0.5f) * 11f;
            drops[k + 1] = 1f + random.nextFloat() * 6.5f;
            drops[k + 2] = (random.nextFloat() - 0.5f) * 0.16f;
            drops[k + 3] = 0.3f + random.nextFloat() * 0.35f;
            drops[k + 4] = (random.nextFloat() - 0.5f) * 0.16f;
            // Bigger near the middle, where most of the water is thrown.
            drops[k + 5] = 0.3f + random.nextFloat() * (Math.abs(drops[k]) < 3 ? 0.9f : 0.5f);
            drops[k + 6] = random.nextInt(6);
        }
        age = 0;
        light = Ufo.LIT;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, by day or night, with an ocean near.
            if (++idle > 6000 && quiet && random.nextInt(CHANCE) == 0 && nearOcean(current, self)) {
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
        double along = along(age), x = x0 + dirX * along, z = z0 + dirZ * along;
        if (age > END || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        light = BoxFigure.light(current, x, surface, z);
    }

    /** An ocean where the player stands, or a little way off on one side. */
    private static boolean nearOcean(ClientLevel level, LocalPlayer self) {
        double[][] around = {{0, 0}, {SHORE, 0}, {-SHORE, 0}, {0, SHORE}, {0, -SHORE}};
        for (double[] d : around) {
            BlockPos at = BlockPos.containing(self.getX() + d[0], self.getY(), self.getZ() + d[1]);
            String biome = level.getBiome(at).unwrapKey().map(key -> key.identifier().toString()).orElse("");
            if (ocean(biome)) return true;
        }
        return false;
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        float t = age + partial;
        double along = along(t);
        double px = x0 + dirX * along - camera.x, py = surface - camera.y, pz = z0 + dirZ * along - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 30);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        int lit = light;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (t < SUNK) collector.draw(pose, Ufo.SOLID, (at, out) -> back(at, out, t, lit));
        if (t >= TAIL && t < SLAPPED) tail(pose, collector, t - TAIL, lit);
        if (t >= SLAPPED && t < SPLASHED) {
            float[] thrown = drops;
            collector.draw(pose, Ufo.SOLID, (at, out) -> splash(at, out, thrown, t - SLAPPED, lit));
        }
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, surface, pz + camera.z);
    }

    /** 0 to 1, easing in and out. */
    private static float smooth(float f) {
        f = Mth.clamp(f, 0f, 1f);
        return f * f * (3 - 2 * f);
    }

    /**
     * Its back at {@code t} ticks, +z ahead, the surface at 0: segments along it, round in
     * section, under a crest that rolls from its head to its end as it dives, the fins along
     * its top, foam where it breaks the water. Whatever is under the water is cut off.
     */
    private static void back(PoseStack.Pose at, VertexConsumer out, float t, int light) {
        float lift = t < RISE ? Mth.lerp(smooth(t / RISE), -3.5f, 1.6f)
                : t < GLIDE ? 1.6f + 0.12f * Mth.sin((t - RISE) * 0.08f)
                : Mth.lerp(smooth((t - GLIDE) / (SUNK - GLIDE)), 1.6f, -4.5f);
        float crest = 8f - 16f * t / SUNK;
        for (int i = 0; i < 13; i++) {
            float z = -12f + 2f * i;
            float r = 2.6f * (float) Math.sqrt(Math.max(0.05, 1 - (z / 14f) * (z / 14f)));
            float top = lift - 0.018f * (z - crest) * (z - crest);
            if (top <= WATERLINE) continue;
            float bottom = top - 1.6f * r;
            cut(at, out, -r, bottom, z - 1f, r, top, z + 1f, SKIN, light);
            cut(at, out, -r * 0.5f, top - 0.01f, z - 1f, r * 0.5f, top + 0.08f, z + 1f, SHEEN, light);
            // A ridge of small fins along the top, towards its end.
            if (z >= -10 && z <= 4) cut(at, out, -0.12f, top, z - 0.35f, 0.12f, top + 0.45f, z + 0.35f, FIN, light);
            if (i == 3) {
                // The fin on its back, swept back.
                cut(at, out, -0.2f, top, z - 1f, 0.2f, top + 1.0f, z + 0.8f, FIN, light);
                cut(at, out, -0.15f, top + 1.0f, z - 0.9f, 0.15f, top + 1.5f, z, FIN, light);
            }
            // Foam round it where it breaks the surface.
            if (bottom < 0.1f) cut(at, out, -r - 0.35f, -0.12f, z - 1.1f, r + 0.35f, 0.06f, z + 1.1f, FOAM, light);
        }
    }

    /**
     * Its tail, {@code t} ticks after it starts to rise, standing out of the water at the pose's
     * origin, its flukes spread across: rising, hanging dripping, then falling flat ahead.
     */
    private static void tail(PoseStack pose, Sink collector, float t, int light) {
        float span = HANG - TAIL;
        float raised = t < span ? Mth.lerp(smooth(t / span), -TAIL_HEIGHT - 2.5f, 0f)
                : 0.08f * Mth.sin(t * 0.15f);
        // Slapping down: faster and faster, on the waterline, ahead.
        float fall = t < SLAP - TAIL ? 0f : Mth.clamp((t - (SLAP - TAIL)) / (SLAPPED - SLAP), 0f, 1f);
        float base = TAIL_HEIGHT + raised;
        pose.pushPose();
        if (fall > 0) pose.mulPose(Axis.XP.rotationDegrees(90f * fall * fall));
        collector.draw(pose, Ufo.SOLID, (at, out) -> {
            cut(at, out, -0.7f, WATERLINE, -0.6f, 0.7f, base + 0.6f, 0.6f, SKIN, light);
            for (int side = -1; side <= 1; side += 2) {
                // Each fluke in three steps, rising out to its tip.
                lobe(at, out, side, 0.3f, 2.0f, base, base + 1.5f, light);
                lobe(at, out, side, 2.0f, 3.8f, base + 0.4f, base + 2.0f, light);
                lobe(at, out, side, 3.8f, 5.4f, base + 0.9f, base + 2.5f, light);
            }
            // Water running off its edge while it hangs.
            if (t > span - 10 && fall == 0) {
                for (int k = 0; k < 6; k++) {
                    float dx = (k % 2 == 0 ? 1 : -1) * (1.2f + 0.8f * (k / 2));
                    float drop = (t * 1.0f + k * 7f) % 18f;
                    float y = base + 0.4f * (k / 2) - drop * drop * 0.03f;
                    cut(at, out, dx - 0.08f, y - 0.25f, -0.08f, dx + 0.08f, y, 0.08f, FOAM, light);
                }
            }
        });
        pose.popPose();
    }

    /** One step of a fluke, on {@code side} of the tail, from {@code x0} to {@code x1} out from it. */
    private static void lobe(PoseStack.Pose at, VertexConsumer out, int side, float x0, float x1, float y0, float y1, int light) {
        float a = side * x0, b = side * x1;
        cut(at, out, Math.min(a, b), y0, -0.22f, Math.max(a, b), y1, 0.22f, FIN, light);
    }

    /** The splash, {@code t} ticks after the tail came down: drops thrown up and falling, a ring of foam. */
    private static void splash(PoseStack.Pose at, VertexConsumer out, float[] drops, float t, int light) {
        if (t < 30) {
            float spread = 6f + t * 0.06f;
            cut(at, out, -spread, -0.12f, -0.6f, spread, 0.04f, 8.2f + t * 0.06f, FOAM, light);
        }
        for (int i = 0; i < drops.length / 7; i++) {
            int k = i * 7;
            // Thrown a tick or so apart, not all at once.
            float own = t - drops[k + 6];
            if (own < 0) continue;
            float dx = drops[k] + drops[k + 2] * own, dz = drops[k + 1] + drops[k + 4] * own;
            float y = drops[k + 3] * own - 0.5f * GRAVITY * own * own;
            float half = drops[k + 5] / 2;
            cut(at, out, dx - half, y - half, dz - half, dx + half, y + half, dz + half, FOAM, light);
        }
    }

    /** A box of it, cut off a little under the surface; nothing where all of it is under. */
    private static void cut(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int light) {
        float from = Math.max(y0, WATERLINE);
        if (y1 <= from) return;
        Ufo.box(at, out, x0, from, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, light);
    }
}
