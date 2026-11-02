package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One other player, followed whether or not the server still sends them.
 *
 * <p>While the server sends the entity, it is the truth and the game draws it. Once it
 * stops, what remains is a memory - how they looked, what they rode - and the position
 * a server with the mod shares ({@link SharedPositions}), when there is one. The memory
 * becomes two puppets (the player and their mount), copies that were never added to the world, which this class moves and animates every
 * tick towards where the real one is.
 */
public final class FarPlayer {
    public enum Source {
        /** The server sends the entity: its position is exact. */
        LIVE,
        /** A server with the mod shares the position: exact, at any distance. */
        SHARED,
        /** Nothing is known any more. */
        LOST
    }

    private static final double DT = 0.05;

    final UUID id;
    Source source = Source.LOST;

    /** The live entity, only while {@link Source#LIVE}. */
    @Nullable AbstractClientPlayer live;

    // The memory of the last time the entity was sent.
    private @Nullable AbstractClientPlayer lastSeen;
    private @Nullable Entity lastVehicle;
    /** The player's position relative to their mount, in the mount's own frame. */
    private Vec3 riderOffset = Vec3.ZERO;
    private float riderYaw;
    /** The way the mount faced when last placed. */
    private float mountYaw;
    /** The mount's own id, so that the remembered mobs leave it to this player; null when not known. */
    private @Nullable UUID mountId;
    /** Ticks before looking again for a mount under a rider whose mount was never seen. */
    private int adoptIn;
    /** The mount drawn is a remembered mob taken from under them: its seat is a guess. */
    private boolean mountGuessed;
    /** The mount drawn is the server's copy: its seat is the server's. */
    private boolean mountShared;
    private double knownY = Double.NaN;
    /** Where they look, when the server says it. */
    private float knownYaw = Float.NaN;
    /** Whether they ride something, when the server says it. */
    private boolean riding = true;
    /** Flying with an elytra, as the server says. */
    private boolean gliding;
    /** Where they look up or down while gliding, as the server says, and as drawn. */
    private float knownPitch, pitch;

    /**
     * Not to be drawn until something new is heard: disappeared too close to have walked
     * out of range (died, left through a portal), or in another dimension.
     */
    boolean vanished;
    int ticksSinceInfo;

    /** Where the shared position says they are; only meaningful while {@link #targeted}. */
    private double targetX, targetZ;
    private boolean targeted;

    // What is drawn, smoothed out of the estimate.
    private boolean placed;
    private double x, y, z;
    private double velX, velZ;
    /** How fast they went lately: the plane they log off in flies on at that pace. */
    private final Pace going = new Pace();
    /** Whether the server's last copy of their mount was off the ground; null when it said nothing. */
    private @Nullable Boolean sharedAloft;
    private PositionsMessage.@Nullable Mount aloftOf;
    private float yaw;

    @Nullable RemotePlayer puppet;
    @Nullable Entity mountPuppet;

    /** Their profile as the tab list last gave it: name and skin, kept for when they log off. */
    @Nullable GameProfile profile;
    /** The tab list entry the puppet takes its skin from: a new one (a new skin) makes a new puppet. */
    private @Nullable PlayerInfo shownInfo;
    /** The gear the server last said they wear, as put on the puppet; null when none was put on. */
    private @Nullable List<String> shownGear;
    /** The id ({@link SharedPositions#dimensionId}) of the dimension x, y, z belong to; null when none. */
    private @Nullable String knownDimension;

    FarPlayer(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }
    public Source source() { return source; }
    public boolean vanished() { return vanished; }
    public @Nullable AbstractClientPlayer live() { return live; }
    public @Nullable RemotePlayer puppet() { return puppet; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public boolean placed() { return placed; }
    public float yaw() { return yaw; }
    @Nullable String knownDimension() { return knownDimension; }

    /** The mount to draw under the puppet, if any. */
    public @Nullable Entity mountPuppet() {
        return riding ? mountPuppet : null;
    }

    /** The mount itself, while the server sends it, or the last one seen; null when they ride nothing. */
    @Nullable Entity mount() {
        return riding ? lastVehicle : null;
    }

    /**
     * What they ride, to keep when they log off: the mount as the game saves it, the seat and
     * the way both face. Null when they ride nothing, or share the mount with another player,
     * whom the server leaves on it.
     */
    RestingPlayers.@Nullable Mount mountToRest() {
        Entity vehicle = mount();
        // Never seen up close: the copy drawn under them, from what the server said.
        if (vehicle == null) vehicle = mountPuppet();
        if (vehicle == null) return null;
        for (Entity passenger : vehicle.getIndirectPassengers()) {
            if (passenger instanceof Player other && !other.getUUID().equals(id)) return null;
        }
        // Off the ground or not: what the game sees of it while sent, else the server's last word.
        Boolean aloft = source == Source.LIVE ? null : sharedAloft;
        return RestingPlayers.Mount.of(vehicle, riderOffset, mountYaw, riderYaw, going.fastest(), aloft);
    }

    /** What they wear and hold, to keep when they log off: as last seen, or as the server last said. */
    @Nullable List<String> gearToRest() {
        LivingEntity worn = live != null ? live : puppet != null ? puppet : lastSeen;
        return worn == null ? null : Gear.of(worn, worn.level().registryAccess());
    }

    /** The copy of their mount, drawn while they ride it; null when there is none. */
    @Nullable Entity mountCopy() {
        return mountPuppet;
    }

    /**
     * The id of the mount this player rides, while it is known: its copy is drawn under this
     * player's, and none of its own. Already while the player is sent, so that a mount left
     * behind with them is never drawn on its own for a tick.
     */
    @Nullable UUID carriedMount() {
        return source != Source.LOST && riding ? mountId : null;
    }

    /** How sure the position is, in blocks. */
    public double uncertainty() {
        return source == Source.LIVE || source == Source.SHARED ? 0.1 : Double.NaN;
    }

    /** Whether the puppets should be drawn this frame. */
    public boolean showsPuppet(FarConfig config) {
        return source != Source.LIVE && source != Source.LOST && placed && !vanished && puppet != null;
    }

    // --- While the server sends the entity ---------------------------------------

    void observeLive(AbstractClientPlayer player, String dimension) {
        knownDimension = dimension;
        double px = x, pz = z;
        boolean wasPlaced = placed;
        source = Source.LIVE;
        live = player;
        lastSeen = player;
        vanished = false;
        ticksSinceInfo = 0;
        knownY = player.getY();

        Entity root = player.getRootVehicle();
        if (root != player) {
            lastVehicle = root;
            mountId = root.getUUID();
            riderOffset = player.position().subtract(root.position()).yRot((float) Math.toRadians(root.getYRot()));
            // The body, not the head: a rider looking aside still sits facing the way of their mount.
            riderYaw = player.yBodyRot - root.getYRot();
            mountYaw = root.getYRot();
        } else {
            lastVehicle = null;
            mountId = null;
        }
        riding = root != player;

        x = player.getX();
        y = player.getY();
        z = player.getZ();
        placed = true;
        if (wasPlaced) {
            velX = lerp(0.3, velX, x - px);
            velZ = lerp(0.3, velZ, z - pz);
            going.note(velX, velZ);
        }
        yaw = player.getYRot();
        puppet = null;
        mountPuppet = null;
        mountGuessed = mountShared = false;
        shownInfo = null;
        shownGear = null;
    }

    // --- Once it stops ------------------------------------------------------------

    void observeRemote(ClientLevel level, Player self, PlayerInfo info, SharedPositions.@Nullable Report shared,
                       @Nullable UUID vehicle, PositionsMessage.@Nullable Mount sharedMount,
                       PositionsMessage.@Nullable Look look, String dimension, double vanishRadius, FarConfig config,
                       MobMemory mobs) {
        if (source == Source.LIVE) leaveLive(level, self, vanishRadius);
        if (puppet == null) freshPuppet(level, info);
        // A new tab list entry: a new skin. The puppet keeps the one it was made with, so a new one.
        if (shownInfo != null && shownInfo != info) newSkin(level, info);
        shownInfo = info;
        // What the server says they wear now, put on once each time it changes.
        if (look != null && puppet != null && !look.gear().equals(shownGear)) {
            Gear.apply(puppet, look.gear(), level.registryAccess());
            shownGear = look.gear();
        }

        // Read once each time the server sends a new copy: the game saves it as text.
        if (sharedMount != aloftOf) {
            aloftOf = sharedMount;
            String nbt = sharedMount == null ? "" : sharedMount.nbt();
            sharedAloft = nbt.contains("OnGround:0b") ? Boolean.TRUE : nbt.contains("OnGround:1b") ? Boolean.FALSE : null;
        }
        Source heard = shared == null ? null : share(shared, dimension);
        if (heard != null && riding) {
            // Got onto another mount out of sight: the one drawn is no longer theirs.
            if (vehicle != null && !vehicle.equals(mountId)) {
                mountPuppet = null;
                mountGuessed = mountShared = false;
                mountId = null;
                adoptIn = 0;
            }
            // The server's copy of the mount, with the seat it measured, before a remembered mob
            // taken from under them, whose seat is only guessed from two positions of different ages.
            if (sharedMount != null && (mountPuppet == null || mountGuessed)) {
                mountFrom(level, sharedMount, vehicle);
            } else if (mountPuppet == null && vehicle != null && --adoptIn <= 0) {
                adopt(level, shared, vehicle, mobs);
            }
            // The seat as the server measures it now, every few seconds.
            if (mountShared && sharedMount != null) {
                riderOffset = new Vec3(sharedMount.seatX(), sharedMount.seatY(), sharedMount.seatZ());
                riderYaw = sharedMount.riderYaw();
            }
        }

        if (heard != null) {
            source = heard;
            vanished = false;
            ticksSinceInfo = 0;
        } else {
            if (source != Source.LOST) ticksSinceInfo = 0;
            source = Source.LOST;
            ticksSinceInfo++;
        }

        if (source != Source.LOST && targeted) {
            follow(self);
            knownDimension = dimension;
        }
        animate(config);
    }

    private void leaveLive(ClientLevel level, Player self, double vanishRadius) {
        live = null;
        source = Source.LOST;
        // Players leave the server's range by walking out of it. One that disappears
        // well inside it was removed instead: dead, through a portal, logged off.
        vanished = Math.hypot(x - self.getX(), z - self.getZ()) < vanishRadius;
        if (vanished) {
            targeted = false;
            placed = false;
        } else {
            targetX = x;
            targetZ = z;
            targeted = true;
        }
        if (lastSeen != null) {
            puppet = copyPlayer(level, lastSeen);
            // Gliding as they left, the body stays tilted as it was; the server says from here on.
            pitch = lastSeen.getXRot();
            mountPuppet = lastVehicle == null ? null : copyEntity(level, lastVehicle);
            mountGuessed = mountShared = false;
            if (mountPuppet != null) {
                mountPuppet.setYRot(lastVehicle.getYRot());
            }
        }
    }

    /** Takes the shared position's word. Null when they are in another dimension. */
    private @Nullable Source share(SharedPositions.Report report, String dimension) {
        if (!report.dimension().equals(dimension)) {
            knownDimension = null;
            vanished = true;
            placed = false;
            targeted = false;
            return null;
        }
        targetX = report.x();
        targetZ = report.z();
        targeted = true;
        knownY = report.y();
        knownYaw = report.yaw();
        riding = report.riding();
        gliding = report.gliding();
        knownPitch = report.pitch();
        return Source.SHARED;
    }

    /**
     * A rider whose mount was never seen up close (they rode before this client met them): the
     * mob the server says they ride becomes their mount, rather than a player sitting on air
     * beside a copy of it. Waits for the server to have shared that mob.
     */
    private void adopt(ClientLevel level, SharedPositions.Report report, UUID vehicle, MobMemory mobs) {
        adoptIn = 20;
        MobMemory.Remembered under = mobs.known(vehicle);
        Entity mount = under == null ? null : mobs.copy(under, level);
        if (mount == null) return;
        mountPuppet = mount;
        mountId = under.id();
        mountGuessed = true;
        mountShared = false;
        // On its back, as high above it as the two were last published.
        riderOffset = new Vec3(0, Math.min(Math.max(0, report.y() - under.y()), mount.getBbHeight() + 0.5), 0);
        riderYaw = 0;
    }

    /** Their mount, from the copy the server saved: the rider sits where they sat on it. */
    private void mountFrom(ClientLevel level, PositionsMessage.Mount shared, @Nullable UUID vehicle) {
        Entity mount = RestingMount.copy(level, RestingPlayers.Mount.of(shared));
        if (mount == null) return;
        // A server of an older version names only the mobs it shares, never a plane: the mount
        // known so far stays theirs while the server's copy is of its kind, so that its
        // remembered copy is not drawn beside them again.
        boolean same = mountPuppet != null && mountPuppet.getType() == mount.getType();
        mountPuppet = mount;
        if (vehicle != null || !same) mountId = vehicle;
        mountGuessed = false;
        mountShared = true;
        riderOffset = new Vec3(shared.seatX(), shared.seatY(), shared.seatZ());
        riderYaw = shared.riderYaw();
    }

    /** A new puppet in the skin of a new tab list entry, wearing what the old one wore. */
    private void newSkin(ClientLevel level, PlayerInfo info) {
        RemotePlayer old = puppet;
        puppet = Puppets.player(level, info.getProfile());
        if (old != null) copyState(old, puppet);
    }

    /** Moves what is drawn towards the estimate, smoothly unless the jump is a teleport. */
    private void follow(Player self) {
        double tx = targetX, tz = targetZ;
        double ty = Double.isNaN(knownY) ? self.getY() : knownY;
        double px = x, pz = z;
        if (!placed || Math.hypot(tx - x, tz - z) > 64) {
            x = tx;
            y = ty;
            z = tz;
            placed = true;
            velX = velZ = 0;
            return;
        }
        // The positions come five times a second.
        double k = 1 - Math.exp(-DT / 0.2);
        x += (tx - x) * k;
        y += (ty - y) * k;
        z += (tz - z) * k;
        velX = lerp(0.2, velX, x - px);
        velZ = lerp(0.2, velZ, z - pz);
        going.note(velX, velZ);
    }

    /**
     * The fastest a player went over the last two seconds, in blocks a tick: the last positions
     * of one logging off come to a stop before they leave, though their plane was flying.
     */
    static final class Pace {
        static final int KEPT = 40;
        private double x, z;
        private int ago;

        /** One tick at this pace. */
        void note(double vx, double vz) {
            if (++ago > KEPT || Math.hypot(vx, vz) >= Math.hypot(x, z)) {
                x = vx;
                z = vz;
                ago = 0;
            }
        }

        Vec3 fastest() {
            return new Vec3(x, 0, z);
        }
    }

    // --- Puppets ---------------------------------------------------------------------

    private void animate(FarConfig config) {
        if (puppet == null) return;
        double speed = Math.hypot(velX, velZ); // blocks per tick
        boolean moving = speed > 0.03 && source != Source.LOST;
        if (moving) {
            yaw = (float) Math.toDegrees(Math.atan2(-velX, velZ));
        } else if (!Float.isNaN(knownYaw)) {
            yaw = knownYaw;
        }
        float head = Float.isNaN(knownYaw) ? yaw : knownYaw;
        // Gliding, the legs hang still however fast they go.
        float walk = moving && !gliding ? (float) Math.min(speed * 4.0, 1.0) : 0f;
        glide(puppet);

        Entity mount = config.showVehicles ? mountPuppet() : null;
        if (mount != null) {
            mountYaw = yaw;
            place(mount, x, y, z, yaw, yaw, walk, 0f);
            Vec3 seat = riderOffset.yRot((float) -Math.toRadians(yaw));
            mount.setPos(x - seat.x, y - seat.y, z - seat.z);
            place(puppet, x, y, z, yaw + riderYaw, head, 0f, 0f);
        } else {
            place(puppet, x, y, z, yaw, head, walk, gliding ? pitch : 0f);
        }
    }

    /**
     * Flying with an elytra, as the server says: the body tilts along the flight over half a
     * second and the wings spread, the way the game does it in the player's tick, which a
     * puppet never runs. The pitch, which tilts the body up or down, follows the server's.
     */
    private void glide(RemotePlayer puppet) {
        if (gliding) {
            if (!puppet.isFallFlying()) puppet.startFallFlying();
            // Lying along the flight after ten ticks; counted no further.
            puppet.fallFlyTicks = Math.min(puppet.fallFlyTicks + 1, 200);
            pitch += (knownPitch - pitch) * (float) (1 - Math.exp(-DT / 0.2));
        } else {
            if (puppet.isFallFlying()) puppet.stopFallFlying();
            puppet.fallFlyTicks = 0;
            pitch = knownPitch = 0f;
        }
        // A copy made mid-flight takes the glider's pose, and its small box: the copies keep a standing one.
        if (puppet.getPose() == Pose.FALL_FLYING) puppet.setPose(Pose.STANDING);
        // Before 1.21.2 the wings spread as the model is drawn.
        //? if >=1.21.2
        puppet.elytraAnimationState.tick();
    }

    private static void place(Entity entity, double px, double py, double pz, float body, float head, float walk,
                              float pitch) {
        entity.setOldPosAndRot();
        entity.setPos(px, py, pz);
        entity.setYRot(head);
        entity.setXRot(pitch);
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRot = body;
            living.yHeadRot = head;
            Puppets.walk(living, walk, 0.4f, living.isBaby() ? 3f : 1f);
        }
        entity.tickCount++;
    }

    /** A player never seen up close: their skin from the tab list, every skin layer on. */
    private void freshPuppet(ClientLevel level, PlayerInfo info) {
        puppet = Puppets.player(level, info.getProfile());
        mountPuppet = null;
        mountGuessed = mountShared = false;
        mountId = null;
        shownGear = null;
    }

    private static RemotePlayer copyPlayer(ClientLevel level, AbstractClientPlayer source) {
        RemotePlayer copy = Puppets.numbered(new RemotePlayer(level, source.getGameProfile()));
        copyState(source, copy);
        return copy;
    }

    private static @Nullable Entity copyEntity(ClientLevel level, Entity source) {
        Entity copy = Puppets.create(source.getType(), level);
        if (copy == null) return null;
        copyState(source, copy);
        return copy;
    }

    /**
     * What the server told this client about the entity, given to the copy the same way
     * the network gives it: synched data (variants, saddles, skin layers, pose) and
     * equipment.
     */
    private static void copyState(Entity source, Entity copy) {
        try {
            List<SynchedEntityData.DataValue<?>> values = source.getEntityData().getNonDefaultValues();
            if (values != null) copy.getEntityData().assignValues(values);
        } catch (RuntimeException ignored) {
            // A modded entity with unusual data: the copy keeps its defaults.
        }
        if (source instanceof LivingEntity from && copy instanceof LivingEntity to) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                to.setItemSlot(slot, from.getItemBySlot(slot).copy());
            }
            // How long it has been gliding, which tilts its body: a glider copied mid-flight stays lying along it.
            to.fallFlyTicks = from.getFallFlyingTicks();
        }
        copy.setPos(source.getX(), source.getY(), source.getZ());
        copy.setOldPosAndRot();
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
