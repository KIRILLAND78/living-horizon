package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.Puppets;
import fr.clixmods.livinghorizon.render.DepthFar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Easter egg 1: now and then, a flying saucer comes down over a cow this client remembers,
 * lifts up a second cow grazing beside it in a beam of light, and is gone. The remembered
 * cow stays where it was; the second one, the saucer and the beam exist on this screen
 * only - no cow anywhere was harmed.
 *
 * <p>The saucer is a few boxes of colour, Minecraft style, with lights blinking round the
 * rim; the beam is a translucent glowing column; the cow taken is a twin of the remembered
 * one ({@link MobMemory#twin}), a puppet like the distant mobs.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Ufo {
    static final Identifier SHEET = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/misc/birds.png");
    static final RenderType SOLID = RenderTypes.entityCutout(SHEET);
    static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(SHEET);
    /** A white texel of the bird sheet: every box is that texel, tinted. */
    private static final float U = 7.5f / 64f, V = 1.5f / 32f;
    static final int BRIGHT = LightTexture.FULL_BRIGHT, LIT = LightTexture.pack(0, 15);
    private static final String COW = "minecraft:cow";

    /** Ticks of the show: arrive, beam on, lift, beam off, wait, then away. */
    private static final int ARRIVED = 240, BEAM = 300, LIFTED = 480, TAKEN = 500, GONE = 520;
    /**
     * Away, slowly to the horizon: blocks per tick it gains, up to its speed; blocks it climbs
     * for each it goes out. It is gone once a pixel or so on screen, or after five minutes.
     */
    private static final double AWAY_GAIN = 0.02, AWAY_SPEED = 4, AWAY_RISE = 0.3;
    private static final int AWAY_LONGEST = 6000;
    /** Blocks across the saucer, its rim; screen pixels under which it is too small to see. */
    private static final double WIDTH = 14, TINY = 1.0;
    /** Blocks from the ground to the saucer's middle while it hovers. */
    private static final double HOVER = 16;
    /** How far the remembered cow may be, in blocks across: when called, and by chance. */
    private static final double CALLED_MIN = 24, CALLED_MAX = 400, CHANCE_MIN = 80, CHANCE_MAX = 400;
    /** Ahead of the player: within about 45 degrees of where they look. */
    private static final double AHEAD = 0.7;
    /** Spots beside the cow tried before standing the twin at the cow's own height. */
    private static final int TRIES = 8;

    /** What calling the show found. */
    public enum Call {
        /** A cow ahead of the player: it comes in sight. */
        AHEAD,
        /** A cow elsewhere, or a show already on its way. */
        AROUND,
        /** No cow remembered in reach, or the easter eggs are off. */
        NOTHING
    }

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    private final AtomicReference<double @Nullable []> foundGround = new AtomicReference<>();
    private int tries;
    private boolean active, searching, called;
    private int age;
    /** Where the remembered cow stood when the show was called: the twin goes beside it. */
    private double anchorX, anchorY, anchorZ;
    private @Nullable LivingEntity twin;
    private double groundX, groundY, groundZ;
    private double fromX, fromY, fromZ, awayX, awayZ;
    /** Blocks it has gone on its way off, and how fast it goes. */
    private double out, awaySpeed;
    private double x, y, z, xo, yo, zo;
    private float spin, spinO, tilt, tiltO, beam, beamO, heading;
    private @Nullable LivingEntity cow;
    private boolean cowShown;
    private int idle;

    Ufo(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return active || searching;
    }

    /** The cow taken, while it is still to be seen. */
    public @Nullable LivingEntity cow() {
        return active && cowShown ? cow : null;
    }

    /**
     * Starts the show over a cow this client remembers, an adult one drawn far away, ahead of
     * the player where there is one, anywhere around otherwise: by chance, or because they
     * asked ({@code called}: nearer).
     */
    public Call summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return Call.NOTHING;
        if (active || searching) return Call.AROUND;
        MobMemory memory = FarPlayerTracker.get().mobs();
        int limit = FarConfig.get().mobMaxDistance;
        double close = called ? CALLED_MIN : CHANCE_MIN, far = called ? CALLED_MAX : CHANCE_MAX;
        if (limit > 0) far = Math.min(far, limit);
        // No farther than the terrain is drawn and clear of fog.
        far = Math.min(far, Sight.reach());
        double yaw = Math.toRadians(self.getYRot()), lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
        List<MobMemory.Remembered> ahead = new ArrayList<>(), around = new ArrayList<>();
        for (MobMemory.Remembered mob : memory.shown()) {
            if (!COW.equals(mob.type()) || memory.handingOver(mob)) continue;
            if (!(mob.puppet() instanceof LivingEntity puppet) || puppet.isBaby()) continue;
            double dx = puppet.getX() - self.getX(), dz = puppet.getZ() - self.getZ(), across = Math.hypot(dx, dz);
            if (across < close || across > far) continue;
            ((dx * lookX + dz * lookZ) / across > AHEAD ? ahead : around).add(mob);
        }
        Call call = !ahead.isEmpty() ? Call.AHEAD : !around.isEmpty() ? Call.AROUND : Call.NOTHING;
        if (call == Call.NOTHING) {
            if (called) EggLog.nowhere(1);
            return call;
        }
        List<MobMemory.Remembered> from = call == Call.AHEAD ? ahead : around;
        MobMemory.Remembered chosen = Sight.any(from, random);
        if (chosen == null) return Call.NOTHING;
        Entity remembered = chosen.puppet();
        LivingEntity made = memory.twin(chosen, level);
        if (remembered == null || made == null) return Call.NOTHING;
        twin = made;
        this.called = called;
        anchorX = remembered.getX();
        anchorY = remembered.getY();
        anchorZ = remembered.getZ();
        searching = true;
        tries = 0;
        // A lookup of an earlier show, answered late, is not this one's.
        foundGround.set(null);
        look(level);
        return call;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet) {
        if (!enabled) {
            active = searching = false;
            twin = null;
            return;
        }
        if (!active && !searching) {
            // Rare: about once in half an hour of night, an hour and a half of day.
            int chance = level.isBrightOutside() ? 108000 : 36000;
            if (++idle > 1200 && quiet && random.nextInt(chance) == 0) {
                idle = 0;
                summon(level, self, false);
            }
            return;
        }
        if (searching) {
            double[] ground = foundGround.getAndSet(null);
            if (ground == null) return;
            // Beside the cow and about as high: not in water, not up or down a cliff.
            if (Double.isNaN(ground[1]) || Math.abs(ground[1] - anchorY) > 1.5) {
                if (++tries < TRIES) {
                    look(level);
                    return;
                }
                ground[1] = anchorY;
            }
            begin(self, ground[0], ground[1], ground[2]);
            return;
        }
        step(self);
    }

    /** Picks a spot beside the remembered cow and finds its ground, here or in the far world. */
    private void look(ClientLevel level) {
        double angle = random.nextDouble() * Math.PI * 2, gap = 2.2 + random.nextDouble();
        double sx = anchorX + Math.sin(angle) * gap, sz = anchorZ + Math.cos(angle) * gap;
        int gx = Mth.floor(sx), gz = Mth.floor(sz);
        if (Columns.loaded(level, sx, sz)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            boolean wet = !level.getFluidState(new BlockPos(gx, top - 1, gz)).isEmpty();
            foundGround.set(new double[]{sx, wet ? Double.NaN : top, sz});
        } else {
            int bottom = level.getMinY();
            LodWorld.surface(gx, gz).handle((surface, error) -> {
                boolean ground = surface != null && surface.isPresent() && !surface.get().water() && surface.get().y() > bottom;
                foundGround.set(new double[]{sx, ground ? surface.get().y() + 1 : Double.NaN, sz});
                return null;
            });
        }
    }

    private void begin(LocalPlayer self, double gx, double gy, double gz) {
        EggLog.came(1, gx, gy, gz, self, called);
        LivingEntity taken = twin;
        twin = null;
        searching = false;
        if (taken == null) return;
        if (!called) calendar.shown(1);
        cow = taken;
        groundX = gx;
        groundY = gy;
        groundZ = gz;
        float yaw = random.nextFloat() * 360f;
        cow.setPos(gx, gy, gz);
        cow.setOldPosAndRot();
        cow.setYRot(yaw);
        cow.yBodyRot = cow.yBodyRotO = cow.yHeadRot = cow.yHeadRotO = yaw;
        cowShown = true;
        // In from the far side, high up; out the way it came, onwards.
        double away = Math.atan2(gx - self.getX(), gz - self.getZ()) + random.nextGaussian() * 0.5;
        fromX = gx + Math.sin(away) * 420;
        fromZ = gz + Math.cos(away) * 420;
        fromY = gy + 90;
        double leave = away + Math.PI * 0.5 * (random.nextBoolean() ? 1 : -1);
        awayX = Math.sin(leave);
        awayZ = Math.cos(leave);
        x = xo = fromX;
        y = yo = fromY;
        z = zo = fromZ;
        heading = (float) Math.toDegrees(Math.atan2(-(gx - fromX), gz - fromZ));
        age = 0;
        out = awaySpeed = 0;
        beam = beamO = tilt = tiltO = 0f;
        active = true;
    }

    private void step(LocalPlayer self) {
        age++;
        xo = x;
        yo = y;
        zo = z;
        spinO = spin;
        tiltO = tilt;
        beamO = beam;
        spin += 7f;
        double hoverY = groundY + HOVER + 0.4 * Math.sin(age * 0.15);
        if (age <= ARRIVED) {
            // Gliding in, slowing down over the cow.
            double t = age / (double) ARRIVED, ease = 1 - Math.pow(1 - t, 3);
            x = Mth.lerp(ease, fromX, groundX);
            y = Mth.lerp(ease, fromY, hoverY);
            z = Mth.lerp(ease, fromZ, groundZ);
            tilt = (float) (14 * (1 - ease));
        } else if (age <= GONE) {
            x = groundX;
            y = hoverY;
            z = groundZ;
            tilt = 0f;
            if (age > ARRIVED && age <= BEAM) beam = (age - ARRIVED) / (float) (BEAM - ARRIVED);
            if (age > LIFTED) beam = Math.max(0f, 1f - (age - LIFTED) / (float) (TAKEN - LIFTED));
        } else {
            // Off: slowly faster, climbing a little, to the horizon, until it is a speck.
            int k = age - GONE;
            heading = (float) Math.toDegrees(Math.atan2(-awayX, awayZ));
            awaySpeed = Math.min(AWAY_SPEED, awaySpeed + AWAY_GAIN);
            out += awaySpeed;
            x = groundX + awayX * out;
            z = groundZ + awayZ * out;
            y = groundY + HOVER + out * AWAY_RISE;
            tilt = Math.min(25f, k * 0.15f);
            if (tiny(self.getEyePosition().distanceTo(new Vec3(x, y, z))) || k > AWAY_LONGEST) active = false;
        }

        if (cow != null) {
            cow.setOldPosAndRot();
            cow.yBodyRotO = cow.yBodyRot;
            cow.yHeadRotO = cow.yHeadRot;
            double cowY = groundY;
            if (age > BEAM && age <= LIFTED) {
                double t = (age - BEAM) / (double) (LIFTED - BEAM);
                double ease = t * t * (3 - 2 * t);
                cowY = Mth.lerp(ease, groundY, groundY + HOVER - 2.8);
                // Turning slowly as it goes up.
                cow.yBodyRot += 2.5f;
                cow.yHeadRot = cow.yBodyRot;
                cow.setYRot(cow.yBodyRot);
            } else if (age > LIFTED) {
                cowY = groundY + HOVER - 2.8;
            }
            cow.setPos(groundX, cowY, groundZ);
            Puppets.walk(cow, 0f, 0.4f, 1f);
            cow.tickCount++;
            cowShown = age <= LIFTED + 4;
        }
    }

    /** A saucer this far off is a pixel or so on screen, as this frame is projected. */
    static boolean tiny(double distance) {
        return WIDTH / distance * DepthFar.pixelsPerRadian(Minecraft.getInstance()) < TINY;
    }

    // --- Drawing --------------------------------------------------------------------------

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 20);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        float spinNow = Mth.lerp(partial, spinO, spin), tiltNow = Mth.lerp(partial, tiltO, tilt);
        float beamNow = Mth.lerp(partial, beamO, beam);
        int blink = (age / 5) % 3;

        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);

        if (beamNow > 0.01f) {
            float drop = (float) (Mth.lerp(partial, yo, y) - groundY);
            int alpha = (int) (110 * beamNow);
            collector.draw(pose, GLOW, (at, out) -> beam(at, out, drop, alpha));
        }

        saucer(pose, collector, heading, tiltNow, spinNow, blink);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /** The saucer at the pose's origin: heading where it goes, tilted forwards, spinning. */
    static void saucer(PoseStack pose, Sink collector, float heading, float tilt, float spin, int blink) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-heading));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        collector.draw(pose, SOLID, (at, out) -> saucer(at, out, blink));
        pose.popPose();
    }

    /** The saucer: a wide rim, a hull above and below, a glass dome, lights around. */
    private static void saucer(PoseStack.Pose at, VertexConsumer out, int blink) {
        box(at, out, -4, -1.6f, -4, 4, -0.6f, 4, 105, 108, 118, LIT);       // underside
        box(at, out, -7, -0.6f, -7, 7, 0.4f, 7, 196, 200, 208, LIT);        // rim
        box(at, out, -5, 0.4f, -5, 5, 1.4f, 5, 168, 172, 182, LIT);         // upper hull
        box(at, out, -2.5f, 1.4f, -2.5f, 2.5f, 3.6f, 2.5f, 130, 215, 235, LIT); // dome
        box(at, out, -2, -2.1f, -2, 2, -1.6f, 2, 150, 255, 180, BRIGHT);   // the hatch, glowing
        int[][] colours = {{255, 70, 70}, {255, 220, 70}, {90, 255, 120}};
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cx = (float) (Math.cos(a) * 7.2), cz = (float) (Math.sin(a) * 7.2);
            int[] c = colours[(i + blink) % 3];
            box(at, out, cx - 0.45f, -0.45f, cz - 0.45f, cx + 0.45f, 0.35f, cz + 0.45f, c[0], c[1], c[2], BRIGHT);
        }
    }

    /** The beam: a glowing column widening from the hatch to the ground. */
    private static void beam(PoseStack.Pose at, VertexConsumer out, float drop, int alpha) {
        float top = 2f, bottom = 3.6f, y0 = -2.1f, y1 = -drop;
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            vertex(at, out, a[0] * top, y0, a[1] * top, 170, 255, 190, alpha, BRIGHT, 0, 1, 0);
            vertex(at, out, b[0] * top, y0, b[1] * top, 170, 255, 190, alpha, BRIGHT, 0, 1, 0);
            vertex(at, out, b[0] * bottom, y1, b[1] * bottom, 170, 255, 190, alpha / 3, BRIGHT, 0, 1, 0);
            vertex(at, out, a[0] * bottom, y1, a[1] * bottom, 170, 255, 190, alpha / 3, BRIGHT, 0, 1, 0);
        }
    }

    /** An axis-aligned box, every face wound to face outwards. */
    static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
                    int r, int g, int b, int light) {
        box(at, out, x0, y0, z0, x1, y1, z1, r, g, b, 255, light);
    }

    /** The same, see-through as much as {@code a} says, for a translucent render type. */
    static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
                    int r, int g, int b, int a, int light) {
        face(at, out, r, g, b, a, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(at, out, r, g, b, a, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(at, out, r, g, b, a, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(at, out, r, g, b, a, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(at, out, r, g, b, a, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(at, out, r, g, b, a, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** One face, its corners put in the order that makes it face {@code (nx, ny, nz)}. */
    static void face(PoseStack.Pose at, VertexConsumer out, int r, int g, int b, int a, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;
        if (wx * nx + wy * ny + wz * nz >= 0) {
            vertex(at, out, ax, ay, az, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, bx, by, bz, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, cx, cy, cz, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, dx, dy, dz, r, g, b, a, light, nx, ny, nz);
        } else {
            vertex(at, out, ax, ay, az, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, dx, dy, dz, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, cx, cy, cz, r, g, b, a, light, nx, ny, nz);
            vertex(at, out, bx, by, bz, r, g, b, a, light, nx, ny, nz);
        }
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, float x, float y, float z,
                               int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        out.addVertex(at, x, y, z)
                .setColor(r, g, b, a)
                .setUv(U, V)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, nx, ny, nz);
    }
}
