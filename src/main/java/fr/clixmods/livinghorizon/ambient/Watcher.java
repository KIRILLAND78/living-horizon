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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 11: now and then, while the player looks far off through a spyglass, a figure
 * appears on a far hilltop close to where they look, a spyglass to its own eye, looking back
 * at them. It never moves: it scopes them a few seconds, lowers its spyglass a while, and raises
 * it again, as long as they look at it. Once they stop, lowering their spyglass or turning it
 * away from it, it is gone; gone too if they come near. Only on this screen.
 *
 * <p>The figure is a player of no one, in a default skin of the game's, any but Steve's
 * ({@link #anyone}), holding a spyglass. It scopes as a player does, in the game's own
 * pose and with the spyglass at its eye: its use flag is set in its synched data, from which
 * the client takes the item in its hand as the one in use, as it does for a player the server
 * says is using one. Its spot is read in the loaded chunks or the far world ({@link Columns}),
 * within the spyglass's narrow view, in sight ({@link Sight}), the highest found.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Watcher implements Egg {
    private static final int NUMBER = 11;
    /** Default skins the game has: it gives an id the one at {@code floorMod(id.hashCode(), SKINS)}. */
    private static final int SKINS = 18;
    /** How far off it may stand, in blocks across; it goes if the player comes within {@link #NEAR}. */
    private static final double MIN = 100, MAX = 350, NEAR = 48;
    /** Blocks off the point the player looks at must be, at least, for the spyglass to look far. */
    private static final double FAR_LOOK = 80;
    /** The spyglass's zoom: the field of view it leaves, out of the game's. */
    private static final double SPYGLASS = 0.1;
    /** How much of the spyglass's view a spot may stand in, from its middle; degrees to each side when called without one. */
    private static final double IN_SCOPE = 0.6, CALLED_CONE = 12;
    /** Ticks of scoping far before it may come; ticks it waits, called, for the player to scope. */
    private static final int SESSION = 60, ALONE = 400;
    /** Ticks it scopes, at least and more by chance; ticks it holds its spyglass lowered, the same. */
    private static final int UP_MIN = 60, UP_MORE = 100, DOWN_MIN = 30, DOWN_MORE = 70;
    /** Ticks out of the player's spyglass, once seen in it, before it is gone: a glance off its edge is not looking away. */
    private static final int UNSEEN = 5;
    /** Ticks it stays at most; ticks a search may take. */
    private static final int LINGER = 2400, SEARCH = 400;
    /** Spots tried. */
    private static final int SPOTS = 64;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private @Nullable RemotePlayer figure;
    /** For the game tests: whether the player scopes, in their place; null reads the player. */
    private @Nullable Boolean pretend;
    private boolean searching, called, scoped, rolled, seen, raised;
    /** Ticks since it came; out of the player's spyglass since seen in it; left before it raises or lowers its own; this session of scoping far. */
    private int age, unseen, phase, unscoped, far, idle, searchAge;

    Watcher(EggCalendar calendar) {
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

    /** For the game tests: it holds its spyglass to its eye. */
    public boolean raised() {
        return figure != null && raised;
    }

    /**
     * For the game tests, which cannot always hold a spyglass to the player's eye: whether the
     * player scopes, read in their place; null reads the player again.
     */
    public void scoping(@Nullable Boolean scoping) {
        pretend = scoping;
    }

    private boolean scoping(LocalPlayer self) {
        Boolean instead = pretend;
        return instead != null ? instead : self.isScoping();
    }

    /**
     * Looks for a far spot close to where the player looks, high where it can, in sight, and
     * stands there once it is read: by chance while they scope, inside the spyglass's view; or
     * because they asked ({@code called}: in front of them, scoping or not; called again, it
     * starts over). False if the easter eggs are off.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double reach = Math.min(MAX, Sight.reach());
        if (reach <= MIN) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        boolean scope = scoping(self);
        double high = scopeHigh(), wide = Math.atan(Math.tan(high) * Statue.aspect());
        double cone = scope ? wide * IN_SCOPE : Math.toRadians(CALLED_CONE);
        double look = Math.toRadians(self.getYRot());
        double[] xs = new double[SPOTS], zs = new double[SPOTS];
        for (int i = 0; i < SPOTS; i++) {
            double bearing = look + (random.nextDouble() * 2 - 1) * cone;
            double distance = MIN + random.nextDouble() * (reach - MIN);
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
            // Dry ground; seen through the spyglass, inside its view up and down as well.
            List<Columns.Column> dry = new ArrayList<>();
            for (Columns.Column column : columns) {
                if (!column.dry()) continue;
                Vec3 head = new Vec3(column.x(), column.y() + self.getEyeHeight(), column.z());
                if (!scope || Statue.inView(self, head, high * IN_SCOPE, wide * IN_SCOPE)) dry.add(column);
            }
            double[] hx = new double[dry.size()], hy = new double[dry.size()], hz = new double[dry.size()];
            for (int i = 0; i < dry.size(); i++) {
                hx[i] = dry.get(i).x();
                hy[i] = dry.get(i).y() + 1.5;
                hz[i] = dry.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                // The highest of those in sight: a hilltop where there is one.
                Columns.Column spot = null;
                for (int i = 0; i < seen.length; i++) {
                    Columns.Column one = dry.get(i);
                    if (seen[i] && (spot == null || one.y() > spot.y() || one.y() == spot.y() && random.nextInt(3) == 0)) {
                        spot = one;
                    }
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

    private void begin(ClientLevel current, LocalPlayer self, Columns.Column spot) {
        if (!called) calendar.shown(NUMBER);
        RemotePlayer made = Puppets.player(current, new GameProfile(anyone(random), ""));
        made.setPos(spot.x(), spot.y(), spot.z());
        made.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
        scope(made, true);
        face(made, self, true);
        made.yBodyRotO = made.yBodyRot;
        made.yHeadRotO = made.yHeadRot;
        made.setOldPosAndRot();
        figure = made;
        age = unseen = unscoped = 0;
        phase = UP_MIN + random.nextInt(UP_MORE);
        raised = true;
        scoped = seen = false;
        EggLog.came(NUMBER, spot.x(), spot.y(), spot.z(), self, called);
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) {
            far = 0;
            rolled = false;
            return;
        }
        boolean scoping = scoping(self);
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        RemotePlayer him = figure;
        if (him == null) {
            idle++;
            // Each time the player scopes far for a while, a chance, once in that time.
            if (!scoping) {
                far = 0;
                rolled = false;
                return;
            }
            if (lookingFar(current, self)) far++;
            if (far > SESSION && !rolled && quiet && idle > 6000) {
                rolled = true;
                if (random.nextInt(4) == 0) {
                    idle = 0;
                    summon(current, self, false);
                }
            }
            return;
        }
        age++;
        him.setOldPosAndRot();
        him.yBodyRotO = him.yBodyRot;
        him.yHeadRotO = him.yHeadRot;
        double across = Math.hypot(self.getX() - him.getX(), self.getZ() - him.getZ());
        if (across < NEAR || age > LINGER || !Columns.drawn(current, self, him.getX(), him.getZ())) {
            clear();
            return;
        }
        // Seen through a spyglass, it goes once the spyglass is lowered; called, it waits a while to be.
        if (scoping) {
            scoped = true;
        } else if (scoped || ++unscoped > ALONE) {
            clear();
            return;
        }
        // Seen through the spyglass, it is gone once the spyglass turns away from it.
        if (scoping) {
            Vec3 head = him.getEyePosition();
            double high = scopeHigh(), wide = Math.atan(Math.tan(high) * Statue.aspect());
            if (Statue.inView(self, head, high, wide) && inSight(current, self, head)) {
                seen = true;
                unseen = 0;
            } else if (seen && ++unseen > UNSEEN) {
                clear();
                return;
            }
        }
        // Where it stands, turned to the player: scoping them a while, then not, then again.
        if (--phase <= 0) {
            raised = !raised;
            scope(him, raised);
            phase = raised ? UP_MIN + random.nextInt(UP_MORE) : DOWN_MIN + random.nextInt(DOWN_MORE);
        }
        face(him, self, raised);
        Puppets.walk(him, 0f, 0.4f, 1f);
        him.tickCount++;
    }

    /** Holding the spyglass to its eye, or not: the use flag, from which the client takes the item in use. */
    private static void scope(RemotePlayer him, boolean up) {
        him.getEntityData().set(LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) (up ? 1 : 0));
    }

    /** Turned to the player; its head raised or lowered to their eye with {@code eyeToEye}, level otherwise. */
    private static void face(RemotePlayer him, LocalPlayer self, boolean eyeToEye) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(self.getX() - him.getX()), self.getZ() - him.getZ()));
        turn(him, yaw);
        double dy = self.getEyeY() - him.getEyeY(), across = Math.hypot(self.getX() - him.getX(), self.getZ() - him.getZ());
        him.setXRot(eyeToEye ? (float) -Math.toDegrees(Math.atan2(dy, across)) : 0f);
    }

    private static void turn(RemotePlayer him, float yaw) {
        him.setYRot(yaw);
        him.yBodyRot = yaw;
        him.yHeadRot = yaw;
    }

    /** Radians up and down from the middle of the spyglass's view. */
    private static double scopeHigh() {
        return Math.toRadians(Minecraft.getInstance().options.fov().get() * SPYGLASS / 2);
    }

    /**
     * The point the player looks at is far: nothing loaded within {@link #FAR_LOOK} blocks
     * along their view, out to the terrain or the horizon; never the sky above it.
     */
    private static boolean lookingFar(ClientLevel level, LocalPlayer self) {
        if (self.getXRot() < -20f) return false;
        Vec3 eye = self.getEyePosition();
        Vec3 end = eye.add(self.getViewVector(1f).scale(Math.max(FAR_LOOK + 1, Sight.reach())));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(eye) > FAR_LOOK;
    }

    /** Nothing loaded between the player's eye and its head; where it stands past them, as the far world said. */
    private static boolean inSight(ClientLevel level, LocalPlayer self, Vec3 head) {
        if (!level.hasChunk(Mth.floor(head.x) >> 4, Mth.floor(head.z) >> 4)) return true;
        return Ghosts.inSight(level, self, self.getEyePosition(), head.x, head.y, head.z);
    }

    private void clear() {
        generation.incrementAndGet();
        searching = false;
        figure = null;
        level = null;
    }

    /** The figure is the game's to draw; called, the beam over it. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        RemotePlayer him = figure;
        if (him == null || !called) return;
        CalledBeam.submit(pose, camera, collector, Mth.lerp(partial, him.xo, him.getX()),
                Mth.lerp(partial, him.yo, him.getY()), Mth.lerp(partial, him.zo, him.getZ()));
    }

    /**
     * An id whose default skin is any of the game's but Steve's, which easter egg 4 wears: the
     * hash of {@code new UUID(0, n)} is {@code n}, so {@code n} is the skin.
     */
    static UUID anyone(RandomSource random) {
        int skin = random.nextInt(SKINS - 1);
        if (skin >= Herobrine.STEVE.getLeastSignificantBits()) skin++;
        return new UUID(0L, skin);
    }
}
