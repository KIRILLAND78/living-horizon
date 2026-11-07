package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 8: now and then, at night, in a village around the player, a pale, see-through
 * villager fades in somewhere among the houses and wanders as a villager does: a few steps one
 * way, a pause to look about, a few steps another, never far from where it came. Looked at a
 * moment, it stops, turns to the player, stares, and fades away; it fades too after a while,
 * at dawn, and at once if the player comes near. Only on this screen.
 *
 * <p>A village is where a bell stands in the loaded chunks, or where a villager is, loaded or
 * among the remembered mobs farther off. Where it comes and each way it walks are read in the
 * loaded chunks or the far world ({@link Columns}): on the ground - a street, a garden, a
 * field - never on a roof; it comes in sight of the player where it can ({@link Sight}). The
 * figure is drawn of glowing, see-through boxes, as the ghosts of {@link Ghosts} are.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class GhostVillager {
    /** Robe and head, pale; eyes, dark. */
    private static final int ROBE = 0xC4D6F2, HEAD = 0xD4E2F8, EYES = 0x26324A;
    /** How much of what is behind it hides, out of 255, at most. */
    private static final int ALPHA = 70;
    /** Blocks per tick it walks, a villager's stroll; blocks across under which it goes at once. */
    private static final double PACE = 0.045, NEAR = 20;
    /** How far off it may come by chance, and called, in blocks across; at most. */
    private static final double MIN = 40, CALLED_MIN = 24, MAX = 400;
    /** Blocks from what marks the village it may come; it wanders no farther from where it came. */
    private static final double AROUND = 32, HOME = 24;
    /** Blocks one walk goes, at least and at most; stretches its ground is read in. */
    private static final double HOP_MIN = 3, HOP_MAX = 10;
    private static final int ALONG = 4;
    /** Villages looked at, at most; spots tried for where it comes. */
    private static final int VILLAGES = 24, SPOTS = 64;
    /** Ticks it pauses between walks, at least and at most more. */
    private static final int PAUSE_MIN = 40, PAUSE_MORE = 100;
    /** Ticks looked at before it notices; it stares then; it fades in or out; it wanders at most. */
    private static final int NOTICE = 30, STARE = 60, FADE = 50, LIFE = 2400;
    /** Ticks a search may take. */
    private static final int SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches and the walks read: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, walking, choosing;
    private double x, y, z, xo, yo, zo, floor;
    /** Where it came: it wanders round it. */
    private double homeX, homeZ;
    /** The walk under way: its heading, how far it goes and has gone, its ground where not loaded. */
    private double dirX, dirZ, length, walked;
    private final double[] heights = new double[ALONG + 1];
    private float yaw, yawO, head, headO, look, phase;
    /** Ticks since it came; till its next walk; looked at; since it noticed; fading away, -1 until then. */
    private int age, pause, seen, staring = -1, leaving = -1, idle, searchAge;

    GhostVillager(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it stands, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for a village around the player and somewhere on the ground in it, and comes there
     * once it is read: by chance, or because the player asked ({@code called}: the nearest
     * villages, nearer the player, and out of sight when nowhere in sight; called again, it
     * starts over). False if the easter eggs are off or no village is known around.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double near = called ? CALLED_MIN : MIN, far = Math.min(MAX, Sight.reach());
        List<Vec3> villages = far <= near ? List.of() : villages(current, self, far);
        if (villages.isEmpty()) {
            if (called) EggLog.nowhere(8);
            return false;
        }
        double[] xs = new double[SPOTS], zs = new double[SPOTS];
        for (int s = 0; s < SPOTS; s++) {
            // Called, the nearest villages first: the one in front of the player, most likely.
            Vec3 village = villages.get(called ? random.nextInt(Math.min(3, villages.size())) : random.nextInt(villages.size()));
            double angle = random.nextDouble() * Math.PI * 2, off = Math.sqrt(random.nextDouble()) * AROUND;
            xs[s] = Mth.floor(village.x + Math.cos(angle) * off) + 0.5;
            zs[s] = Mth.floor(village.z + Math.sin(angle) * off) + 0.5;
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
            List<Columns.Column> ground = new ArrayList<>();
            for (Columns.Column spot : columns) {
                double across = Math.hypot(spot.x() - self.getX(), spot.z() - self.getZ());
                if (across >= near && across <= far && street(spot)) ground.add(spot);
            }
            double[] hx = new double[ground.size()], hy = new double[ground.size()], hz = new double[ground.size()];
            for (int i = 0; i < ground.size(); i++) {
                hx[i] = ground.get(i).x();
                hy[i] = ground.get(i).y() + 1.8;
                hz[i] = ground.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Columns.Column> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(ground.get(i));
                // Called and nowhere in sight: behind the houses, it wanders and will show.
                Columns.Column spot = Sight.any(clear.isEmpty() && called ? ground : clear, random);
                if (spot == null) {
                    if (called) EggLog.nowhere(8);
                    return;
                }
                begin(spot);
                EggLog.came(8, x, floor, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /**
     * Where villages stand around the player, within {@code far} blocks across: each bell in the
     * loaded chunks, and each villager, loaded or remembered. The nearest first,
     * {@link #VILLAGES} at most.
     */
    static List<Vec3> villages(ClientLevel level, LocalPlayer self, double far) {
        List<Vec3> found = new ArrayList<>();
        double reach = far + AROUND;
        int radius = Mth.ceil(Math.min(reach, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16) / 16);
        int pcx = Mth.floor(self.getX()) >> 4, pcz = Mth.floor(self.getZ()) >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(entity instanceof BellBlockEntity)) continue;
                    BlockPos at = entity.getBlockPos();
                    found.add(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
                }
            }
        }
        // Villagers sleep in their houses at night: where they are is the village.
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getType() == EntityType.VILLAGER) found.add(entity.position());
        }
        String dimension = level.dimension().identifier().toString();
        String villager = EntityType.getKey(EntityType.VILLAGER).toString();
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered(dimension)) {
            if (villager.equals(mob.type())) found.add(new Vec3(mob.x(), mob.y(), mob.z()));
        }
        found.removeIf(at -> Math.hypot(at.x - self.getX(), at.z - self.getZ()) > reach);
        found.sort((a, b) -> Double.compare(a.distanceToSqr(self.position()), b.distanceToSqr(self.position())));
        return found.size() > VILLAGES ? new ArrayList<>(found.subList(0, VILLAGES)) : found;
    }

    /** Known ground to walk on, out of the water: a street, a garden, a field, never a roof. */
    private static boolean street(Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        // A far world that does not say: taken as ground.
        return block == null || block.is(BlockTags.DIRT) || block.is(Blocks.DIRT_PATH) || block.is(BlockTags.SAND)
                || block.is(Blocks.GRAVEL) || block.is(BlockTags.SNOW) || block.is(Blocks.FARMLAND);
    }

    private void begin(Columns.Column spot) {
        if (!called) calendar.shown(8);
        x = xo = homeX = spot.x();
        z = zo = homeZ = spot.z();
        floor = spot.y();
        y = yo = floor + 0.1;
        yaw = yawO = random.nextFloat() * 360f;
        head = headO = 0f;
        look = 0f;
        age = seen = 0;
        staring = leaving = -1;
        walking = choosing = false;
        pause = PAUSE_MIN + random.nextInt(PAUSE_MORE);
        phase = random.nextFloat() * 6.28f;
        active = true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in forty minutes of it, where a village is around.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(48000) == 0) {
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
        headO = head;
        if (Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        // Looked at a moment once it shows: it stops, turns to the player, and stares.
        if (staring < 0 && leaving < 0 && age > FADE && Herobrine.lookedAt(current, self, new Vec3(x, y + 1.6, z))
                && ++seen >= NOTICE) {
            staring = 0;
            walking = false;
        }
        if (staring >= 0) {
            float toPlayer = (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
            yaw = Mth.approachDegrees(yaw, toPlayer, 6f);
            head = Mth.approachDegrees(head, 0f, 6f);
            if (++staring > STARE && leaving < 0) leaving = 0;
        } else if (walking) {
            walk(current);
        } else if (!choosing && --pause <= 0) {
            choose(current);
        } else {
            // Looking about while it stands, as a villager does.
            if (random.nextInt(60) == 0) look = (random.nextFloat() - 0.5f) * 120f;
            head = Mth.approachDegrees(head, look, 4f);
        }
        // Its time is up, or dawn has come: it fades.
        if (leaving < 0 && (age > LIFE || (!called && current.isBrightOutside()))) leaving = 0;
        if (leaving >= 0 && ++leaving > FADE) {
            clear();
            return;
        }
        // Not quite touching the ground.
        y = floor + 0.1 + 0.05 * Math.sin(age * 0.06 + phase);
    }

    /** Picks somewhere near to walk to, and walks there once its ground is read. */
    private void choose(ClientLevel current) {
        double angle = random.nextDouble() * Math.PI * 2, hop = HOP_MIN + random.nextDouble() * (HOP_MAX - HOP_MIN);
        // Back towards where it came when it would wander off.
        if (Math.hypot(x - Math.sin(angle) * hop - homeX, z + Math.cos(angle) * hop - homeZ) > HOME) {
            angle = Math.atan2(-(homeX - x), homeZ - z);
        }
        double dx = -Math.sin(angle), dz = Math.cos(angle), from = floor;
        double[] xs = new double[ALONG], zs = new double[ALONG];
        for (int k = 1; k <= ALONG; k++) {
            xs[k - 1] = x + dx * hop * k / ALONG;
            zs[k - 1] = z + dz * hop * k / ALONG;
        }
        int mine = generation.get();
        choosing = true;
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            choosing = false;
            pause = PAUSE_MIN / 2 + random.nextInt(PAUSE_MORE);
            double before = from;
            for (Columns.Column column : columns) {
                // Ground all along, no step it could not take: else it stays a while more.
                if (!street(column) || Math.abs(column.y() - before) > 1) return;
                before = column.y();
            }
            heights[0] = from;
            for (int k = 0; k < ALONG; k++) heights[k + 1] = columns[k].y();
            dirX = dx;
            dirZ = dz;
            length = hop;
            walked = 0;
            walking = true;
        }, Minecraft.getInstance());
    }

    /** A step along the walk under way, turning to it first; ended where the ground stops it. */
    private void walk(ClientLevel current) {
        float heading = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        if (Math.abs(Mth.wrapDegrees(heading - yaw)) > 10f) {
            yaw = Mth.approachDegrees(yaw, heading, 12f);
            return;
        }
        yaw = heading;
        head = Mth.approachDegrees(head, 0f, 6f);
        double nx = x + dirX * PACE, nz = z + dirZ * PACE, ground = ground(current, nx, nz, walked + PACE);
        if (Double.isNaN(ground) || walked >= length) {
            walking = false;
            return;
        }
        x = nx;
        z = nz;
        walked += PACE;
        floor += (ground - floor) * 0.2;
    }

    /**
     * The ground to walk on, {@code along} blocks into its walk: the loaded column's, NaN where
     * it is water or a step up; where it is not loaded, the one read before it set off.
     */
    private double ground(ClientLevel level, double x, double z, double along) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (Columns.loaded(level, x, z)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            boolean dry = level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty();
            return dry && Math.abs(top - floor) <= 1.2 ? top : Double.NaN;
        }
        double f = Mth.clamp(along / length, 0, 1) * ALONG;
        int i = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - i, heights[i], heights[i + 1]);
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = walking = choosing = false;
        level = null;
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        // Fading in as it comes, out as it goes.
        float shown = Mth.clamp((age + partial) / FADE, 0f, 1f);
        if (leaving >= 0) shown = Math.min(shown, Mth.clamp(1f - (leaving + partial) / FADE, 0f, 1f));
        int alpha = Math.round(ALPHA * shown);
        if (alpha <= 0) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // A slow shuffle while it walks, the feet under the robe.
        float stride = walking ? (float) Math.sin((walked + PACE * partial) * 5.0) * 0.09f : 0f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        collector.draw(pose, Ufo.GLOW, (at, out) -> body(at, out, alpha, stride));
        // The head, turned on its own as it looks about.
        pose.pushPose();
        pose.translate(0f, 1.5f, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, headO, head)));
        collector.draw(pose, Ufo.GLOW, (at, out) -> head(at, out, alpha));
        pose.popPose();
        // The arms folded in front, as a villager holds them.
        pose.translate(0f, 1.3125f, 0.11f);
        pose.mulPose(Axis.XP.rotationDegrees(-43f));
        collector.draw(pose, Ufo.GLOW, (at, out) -> arms(at, out, alpha));
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /** Feet and the long robe; facing +z, origin at its feet. */
    private static void body(PoseStack.Pose at, VertexConsumer out, int alpha, float stride) {
        int light = Ufo.BRIGHT;
        box(at, out, -0.24f, 0f, -0.1f + stride, -0.01f, 0.36f, 0.12f + stride, ROBE, alpha, light);
        box(at, out, 0.01f, 0f, -0.1f - stride, 0.24f, 0.36f, 0.12f - stride, ROBE, alpha, light);
        box(at, out, -0.25f, 0.36f, -0.1875f, 0.25f, 1.5f, 0.1875f, ROBE, alpha, light);
    }

    /** The head from the neck up, its big nose, two dark eyes. */
    private static void head(PoseStack.Pose at, VertexConsumer out, int alpha) {
        int light = Ufo.BRIGHT;
        box(at, out, -0.25f, 0f, -0.25f, 0.25f, 0.625f, 0.25f, HEAD, alpha, light);
        box(at, out, -0.0625f, 0f, 0.25f, 0.0625f, 0.25f, 0.375f, HEAD, alpha, light);
        int eyes = Math.min(255, alpha * 3);
        box(at, out, -0.1875f, 0.28f, 0.25f, -0.0625f, 0.35f, 0.26f, EYES, eyes, light);
        box(at, out, 0.0625f, 0.28f, 0.25f, 0.1875f, 0.35f, 0.26f, EYES, eyes, light);
    }

    /** The folded arms, around their joint at the shoulders: hands across, an arm down each side. */
    private static void arms(PoseStack.Pose at, VertexConsumer out, int alpha) {
        int light = Ufo.BRIGHT;
        box(at, out, -0.25f, -0.375f, -0.125f, 0.25f, -0.125f, 0.125f, ROBE, alpha, light);
        box(at, out, -0.5f, -0.375f, -0.125f, -0.25f, 0.125f, 0.125f, ROBE, alpha, light);
        box(at, out, 0.25f, -0.375f, -0.125f, 0.5f, 0.125f, 0.125f, ROBE, alpha, light);
    }

    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int alpha, int light) {
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, alpha, light);
    }
}
