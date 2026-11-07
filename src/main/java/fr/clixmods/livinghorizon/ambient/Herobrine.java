package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Easter egg 4: from the second or third day the player lives in a world, once a day, at any
 * moment of it, by day or by night, a figure in Steve's skin with white eyes stands somewhere
 * far off around them - up to 400 blocks - and watches them. Once they look him in the eyes he
 * is gone three to five seconds later, and does not come back for one to three days; until then
 * he comes back every day. He goes as well after ten minutes, or if they come near. Only on
 * this screen.
 *
 * <p>The figure is a player of no one, in the default skin the game picks from its id:
 * Steve's for {@link #STEVE}. The white eyes are two small boxes drawn over its face, the
 * size of the eyes of the skin, however far he stands. His
 * spot is read in the loaded chunks or the far world ({@link Columns}), and must be in
 * sight: through the loaded blocks, or past the far world's terrain ({@link LodWorld#blocked}).
 * His days are kept by {@link Daily}; he may come while another easter egg is under way.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Herobrine {
    /**
     * An id the game gives Steve's default skin (wide arms): the skin is the one at
     * {@code floorMod(id.hashCode(), 18)} in its list of default skins, Steve's at 15.
     */
    static final UUID STEVE = new UUID(0L, 15L);
    /** Blocks: he goes if the player comes this close, or this far. */
    private static final double NEAR = 24, LOST = 600;
    /** How far off he may stand, in blocks across; when called, from {@link #CALLED} on. */
    private static final double MIN = 60, MAX = 400, CALLED = 80;
    /** Ticks a search may take before it is given up: a far world that does not answer. */
    private static final int SEARCH = 400;
    /** Ticks he waits, when called, to be seen before he goes anyway. */
    private static final int PATIENCE = 1800;
    /** Ticks he stands there, not looked at, before he goes: about a night. */
    private static final int LINGER = 12000;
    /** Glowing and unshaded, the way the game draws a spider's eyes: the white texel of the bird sheet. */
    private static final RenderType EYES = RenderTypes.eyes(Ufo.SHEET);
    /** A pixel of the player model, drawn at 15/16 of its size, in blocks. */
    private static final float PIXEL = 0.9375f / 16f;
    /** Pixels from the feet to the neck, where the head turns. */
    private static final float NECK = 1.501f * 16f;

    /** Spots found standing, in sight through the loaded blocks; the far ones still to check. */
    private record Search(List<Vec3> clear, List<Vec3> far) {
    }

    private final Daily daily;
    private final RandomSource random = RandomSource.create();
    private final AtomicReference<Columns.Column @Nullable []> found = new AtomicReference<>();
    private final AtomicReference<boolean @Nullable []> farSight = new AtomicReference<>();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable Search search;
    private @Nullable RemotePlayer figure;
    private boolean searching, called;
    private int age, leaveIn, searchAge;

    Herobrine(EggCalendar calendar) {
        this.daily = new Daily(calendar, 4);
    }

    /** For the game tests: he comes in a second, if today is one of his days. */
    public void soon() {
        daily.soon();
    }

    /** On his way or there. */
    public boolean busy() {
        return searching || figure != null;
    }

    /** The figure to draw, while he is there. */
    public @Nullable RemotePlayer figure() {
        return figure;
    }

    /**
     * Looks for a spot in sight, and stands there once it is read: on his day, or because the
     * player asked ({@code called}: more in front of them, anywhere from {@link #CALLED} to
     * {@link #MAX} blocks off; called again, he goes and stands somewhere else). False if the
     * easter eggs are off.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        figure = null;
        // No farther than the terrain is drawn and clear of fog.
        double near = called ? CALLED : MIN, far = Math.min(MAX, Sight.reach());
        if (far <= near) {
            if (called) EggLog.nowhere(4);
            return false;
        }
        int count = 48;
        double yaw = Math.toRadians(self.getYRot());
        double[] xs = new double[count], zs = new double[count];
        for (int i = 0; i < count; i++) {
            // Anywhere around, at any distance alike; in front of the player when called.
            double bearing = called ? yaw + (random.nextDouble() * 2 - 1) * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = near + random.nextDouble() * (far - near);
            xs[i] = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            zs[i] = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
        }
        this.called = called;
        found.set(null);
        farSight.set(null);
        search = null;
        int mine = generation.incrementAndGet();
        Columns.read(level, xs, zs).thenAccept(columns -> {
            if (generation.get() == mine) found.set(columns);
        });
        searching = true;
        searchAge = 0;
        return true;
    }

    public void tick(ClientLevel level, LocalPlayer self, boolean enabled) {
        RemotePlayer him = figure;
        if (!enabled) {
            figure = null;
            searching = false;
            return;
        }
        if (him != null && him.level() != level) figure = him = null;
        // Gone with the ground under him, once it is no longer drawn.
        if (him != null && !Columns.drawn(level, self, him.getX(), him.getZ())) figure = him = null;
        if (searching) {
            if (++searchAge > SEARCH) {
                generation.incrementAndGet();
                searching = false;
                search = null;
                return;
            }
            search(level, self);
            return;
        }
        if (him == null) {
            if (daily.due()) summon(level, self, false);
            return;
        }
        age++;
        him.setOldPosAndRot();
        him.yBodyRotO = him.yBodyRot;
        him.yHeadRotO = him.yHeadRot;
        face(him, self);
        Puppets.walk(him, 0f, 0.4f, 1f);
        him.tickCount++;

        Vec3 head = new Vec3(him.getX(), him.getY() + 1.6, him.getZ());
        double across = Math.hypot(head.x - self.getX(), head.z - self.getZ());
        if (across < NEAR || across > LOST || (!called && age > LINGER)) {
            figure = null;
            return;
        }
        if (leaveIn < 0 && lookedAt(level, self, head)) {
            // Seen: three to five seconds more, and not back for days.
            leaveIn = 60 + random.nextInt(41);
            if (!called) daily.seen();
        }
        if (leaveIn >= 0 ? --leaveIn <= 0 : called && age > PATIENCE) figure = null;
    }

    /** The spots read: the ones near in sight at once, the far ones from the far world, then one of them. */
    private void search(ClientLevel level, LocalPlayer self) {
        Search current = search;
        if (current == null) {
            Columns.Column[] columns = found.getAndSet(null);
            if (columns == null) return;
            Vec3 eye = self.getEyePosition();
            List<Vec3> clear = new ArrayList<>(), far = new ArrayList<>();
            for (Columns.Column column : columns) {
                if (!column.dry()) continue;
                Vec3 head = new Vec3(column.x(), column.y() + 1.6, column.z());
                if (!level.hasChunk(Mth.floor(column.x()) >> 4, Mth.floor(column.z()) >> 4)) {
                    far.add(head);
                } else if (Ghosts.inSight(level, self, eye, head.x, head.y, head.z)
                        && Ghosts.inSight(level, self, eye, head.x, head.y - 1.0, head.z)) {
                    clear.add(head);
                }
            }
            search = current = new Search(clear, far);
            if (!far.isEmpty()) {
                double[] xs = new double[far.size()], ys = new double[far.size()], zs = new double[far.size()];
                for (int i = 0; i < far.size(); i++) {
                    xs[i] = far.get(i).x;
                    ys[i] = far.get(i).y;
                    zs[i] = far.get(i).z;
                }
                int mine = generation.get();
                LodWorld.blocked(eye, xs, ys, zs, 1.0).thenAccept(blocked -> {
                    if (generation.get() == mine) farSight.set(blocked == null ? new boolean[0] : blocked);
                });
                return;
            }
        } else {
            boolean[] blocked = farSight.getAndSet(null);
            if (blocked == null) return;
            for (int i = 0; i < blocked.length && i < current.far().size(); i++) {
                if (!blocked[i]) current.clear().add(current.far().get(i));
            }
        }
        searching = false;
        search = null;
        Vec3 head = Sight.any(current.clear(), random);
        if (head == null) {
            if (called) EggLog.nowhere(4);
            return;
        }
        RemotePlayer made = Puppets.player(level, new GameProfile(STEVE, ""));
        made.setPos(head.x, head.y - 1.6, head.z);
        face(made, self);
        made.yBodyRotO = made.yBodyRot;
        made.yHeadRotO = made.yHeadRot;
        made.setOldPosAndRot();
        figure = made;
        EggLog.came(4, made.getX(), made.getY(), made.getZ(), self, called);
        age = 0;
        leaveIn = -1;
        if (!called) daily.came();
    }

    /**
     * The player looks at this point, in the eyes for him: the middle of their view on it,
     * tighter the farther it stands, and nothing loaded in between.
     */
    static boolean lookedAt(ClientLevel level, LocalPlayer self, Vec3 point) {
        Vec3 eye = self.getEyePosition(), to = point.subtract(eye);
        double cone = Math.max(Math.toRadians(1), Math.atan2(3, to.length()));
        return self.getViewVector(1f).dot(to.normalize()) > Math.cos(cone) && inSight(level, self, eye, point);
    }

    /** Nothing between the player's eye and his head: the loaded blocks where he stands in them. */
    private static boolean inSight(ClientLevel level, LocalPlayer self, Vec3 eye, Vec3 head) {
        if (!level.hasChunk(Mth.floor(head.x) >> 4, Mth.floor(head.z) >> 4)) return true;
        return Ghosts.inSight(level, self, eye, head.x, head.y, head.z);
    }

    /** Turned to the player, his head level. */
    private static void face(RemotePlayer him, LocalPlayer self) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(self.getX() - him.getX()), self.getZ() - him.getZ()));
        him.setYRot(yaw);
        him.setXRot(0f);
        him.yBodyRot = yaw;
        him.yHeadRot = yaw;
    }

    /**
     * His white eyes, over the face the game draws, the size of the skin's own: brought
     * closer and smaller with him past the far plane, the same on screen.
     */
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        RemotePlayer him = figure;
        if (him == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        double px = Mth.lerp(partial, him.xo, him.getX()) - camera.x;
        double py = Mth.lerp(partial, him.yo, him.getY()) - camera.y;
        double pz = Mth.lerp(partial, him.zo, him.getZ()) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        double reach = DepthFar.of(minecraft) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, him.yHeadRotO, him.yHeadRot)));
        collector.draw(pose, EYES, Herobrine::eyes);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /**
     * Both eyes of Steve's face, two pixels each, made white: the fifth row of the face
     * from the top, three and four pixels above the neck, a pixel in from each side.
     */
    private static void eyes(PoseStack.Pose at, VertexConsumer out) {
        float middle = (NECK + 3.5f) * PIXEL, half = 0.5f * PIXEL;
        // Just in front of the face, behind the hat layer half a pixel out.
        float near = 4.05f * PIXEL, far = 4.25f * PIXEL;
        for (int side = -1; side <= 1; side += 2) {
            float centre = side * 2 * PIXEL;
            Ufo.box(at, out, centre - PIXEL, middle - half, near, centre + PIXEL, middle + half, far,
                    255, 255, 255, Ufo.BRIGHT);
        }
    }
}
