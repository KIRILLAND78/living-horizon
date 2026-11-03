package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
//? if >=1.20.5
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A player who logged off, drawn where they are. Their skin comes from a remembered profile,
 * not from the tab list. A picture on this client only: nothing is sent to the server, it
 * blocks nobody and nothing can hurt it.
 *
 * <p>With {@link FarConfig#offlineLife} on, it lives its life within
 * {@link FarConfig#offlineRoam} blocks of the spot where the player logged off - a village -
 * as long as the blocks there are loaded: it strolls, looks around, sits down, stands in
 * front of a chest, a crafting table or a villager's workstation; at night it walks to a bed
 * it can reach, or stays on its feet: never lying on the ground. Doors and fence gates it
 * walks through. Monsters in sight, it draws a sword and raises its shield, watches them and
 * keeps out of their way, never fights; safe indoors, behind walls and shut doors, it does
 * not mind them. A creeper blowing up in sight throws it back, shield up, and it walks back.
 * Hit, it raises its shield. At the water's edge it may fish for a while, a line cast, a
 * bobber bobbing and fish biting now and then; never two who are close by at the same time.
 * In lava or fire it shows fire resistance, under water water breathing. Where the blocks
 * are not loaded it only stands or sits where it was, standing at night.
 *
 * <p>In the evening it goes to stand in front of a campfire or a furnace it can reach, each
 * on a spot of its own.
 *
 * <p>Two of them close by may walk up to each other and chat a while, face to face, nodding
 * as they talk; never a third, and never another pair close by.
 *
 * <p>Look at it and it looks back at you; facing you, it crouches when you crouch. Walk into
 * it and it is shoved aside, as players push each other; it never pushes back, being a
 * picture on this client only.
 *
 * <p>Off, it sits on its spot. Either way it keeps to the ground: break what it rests on and
 * it falls; bury it and it reappears on top. Sitting, its own spot wins again once it has
 * room and something under it, since that is where the player will come back.
 *
 * <p>All of this only near the camera, within {@link #NEAR_IN} blocks: farther, no life of
 * its own any more, nothing read nor decided, it plays one of the animations distant mobs
 * play, in a loop around where its life left it, never farther from its spot than it roams
 * ({@link RestingLoop}): a walk with pauses, seated in some of them by day, in the bed it
 * already lies in at night.
 *
 * <p>A player who logged off riding something stays on it ({@link RestingMount}): with
 * {@link FarConfig#offlineLife} on, the horse walks, the boat rows and the happy ghast drifts
 * about the spot, the rider looking around; off, both wait on the spot. No bed, no chest
 * then, and no sword: hit, the rider raises a shield.
 *
 * <p>Never left in water: a player who logged off swimming or wading is on the nearest shore
 * within {@link #SHORE} blocks of their spot, living their life or playing their loop from
 * there, or, with no shore that near, in a boat on the surface, rowed as a mount is.
 */
public final class RestingPuppet extends RemotePlayer {
    /** How it is drawn. */
    public enum Look { STAND, SIT, BED, RIDE }

    private enum Activity { IDLE, SIT, WALK, FACE, WARM, FISH, TALK, BED, GUARD, FLY }

    /**
     * What all of them share this tick: the beds kept, where they fish, every one of them (for
     * a chat, a place by the fire), whether it is night, and whether the evening has come.
     */
    record Around(Set<BlockPos> beds, List<Vec3> fishers, Collection<RestingPuppet> crowd, boolean night,
                  boolean evening) {
    }

    /** Blocks per tick², the drag and the most it falls in one tick: the game's own. */
    private static final double GRAVITY = 0.08, DRAG = 0.98, TERMINAL = 3.92;
    /** Blocks per tick when it climbs back through open air. */
    private static final double RISE = 0.3;
    /** Ticks it stays on its feet after falling, before sitting back down. */
    private static final int STAND_TICKS = 15;
    /** Ticks it stands at night with no bed, looking around, before looking for one again. */
    private static final int AWAKE = 200;
    /** Blocks it is shoved aside in a tick at most, out of the way of whoever walks into it. */
    private static final double SHOVE = 0.25;
    /** Blocks between two who fish: one on a stretch of shore, not the whole crowd. */
    static final double FISHERS_APART = 16;
    /** Blocks of open water out from the edge, the bobber at the last one. */
    private static final int CAST = 3;
    /** Ticks it keeps looking at you once you look away. */
    private static final int GAZE = 30;
    /** Degrees: how far it turns its head from its body, and how near facing you counts as facing you. */
    private static final float NECK = 75, FACING = 30;
    /**
     * Blocks: how far it looks for someone to chat with, how far apart two pairs keep, and how
     * far from each other the two stand.
     */
    private static final double TALK_REACH = 24, TALKERS_APART = 16, TALK_GAP = 2;
    /** Ticks the one called waits for the other to come, and a chat lasts at least. */
    private static final int TALK_WAIT = 600, CHAT = 400;
    /** Ticks each speaks before the other answers. */
    private static final int TURN_TO_SPEAK = 60;
    /** Ticks a fish takes to come to the bobber, and the bobber stays under once it bites. */
    private static final int WAKE = 40, DIP = 12;
    /** The game lays a sleeper this high above the block of its bed. */
    private static final double BED_HEIGHT = 0.6875;
    /** Blocks per tick: a stroll, and getting out of a monster's way. */
    private static final double STROLL = 0.11, HURRY = 0.2;
    /** Blocks around it, and spots read at most, when it finds its way back after a blast. */
    private static final int REACH = 10, BUDGET = 700;
    /** Blocks below and above its spot copied when it looks around: a village on a hillside. */
    private static final int COPY_BELOW = 32, COPY_ABOVE = 48;
    /** Ticks before it looks around again when it could not. */
    private static final int RETRY = 40;
    /** Ticks before it reads again what is around: blocks change. */
    private static final int RESURVEY = 600;
    /** Ticks the shield stays up after a hit. */
    private static final int SHIELD_TICKS = 30;
    /** Blocks: monsters this close put it on guard; closer than {@link #TOO_CLOSE}, it steps away. */
    private static final double DANGER = 10, TOO_CLOSE = 3.5;
    /** Ticks without a monster before it puts its sword away. */
    private static final int CALM_TICKS = 80;
    /** Blocks: a creeper blowing up this close throws it. */
    private static final double BLAST = 7;
    /**
     * Blocks across from the camera within which it lives its life, and past which it plays
     * its loop again: apart, so that it does not switch to and fro on the edge.
     */
    static final double NEAR_IN = 48, NEAR_OUT = 64;
    /** Blocks across: how far from the water it looks for a shore before taking a boat. */
    private static final int SHORE = 10;
    /** Ticks between two looks for a shore, and between two looks at the water under its boat. */
    private static final int SHORE_WAIT = 20;
    /** The surface of still water open to the sky, above the bottom of its block. */
    private static final double WATER_TOP = 8 / 9.0;
    /** Colours of the fire resistance and water breathing potions' swirls. */
    private static final int FIRE_RESISTANCE = 0xFF9900, WATER_BREATHING = 0x98DAC0;

    /** What a player stops to look at: a chest, a crafting table, a villager's workstation. */
    private static final Set<Block> INTERESTING = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
            Blocks.COMPOSTER, Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.CAULDRON,
            Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
            Blocks.JUKEBOX, Blocks.BELL);

    /** Where it warms itself in the evening: a fire, a furnace. */
    private static final Set<Block> HEARTHS = Set.of(
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
    /** In the evening, the chance in a hundred that it goes to a fire rather than its usual day. */
    private static final int WARM_CHANCE = 60;

    private final RestingPlayers.Spot spot;
    private final PlayerInfo info;
    private final Random random;
    private final Footing.Source blocks;
    private final @Nullable RestingMount mount;
    /** The boat it was given, its spot being in water far from any shore. */
    private @Nullable RestingMount boat;
    /** What it rides now: its own mount, or that boat. */
    private @Nullable RestingMount carried;
    /** Its spot being in water: the nearest shore, where it rests instead. */
    private @Nullable Vec3 shore;
    private int shoreWait, dryWait;
    /** Where it was found to rest, from the blocks: kept with its spot ({@link #restFound}). */
    private RestingPlayers.@Nullable Rest rest;
    private boolean restFound;
    /** Its shore or boat guessed from the far world, to be found again from the blocks once loaded. */
    private boolean guessed;
    /** The far world was asked what is under its spot; its answer, once there. */
    private boolean askedFar;
    private LodWorld.@Nullable Surface farSurface;

    private boolean placed;
    private boolean lively;
    private boolean riding;
    private boolean near = true;
    /** Playing its loop, away from the camera. */
    private boolean far;
    private @Nullable RestingLoop loop;
    private double fall;
    private int standing;

    private Activity activity = Activity.IDLE;
    private int timer;
    private @Nullable List<Walkway.Step> path;
    private int pathIndex;
    private Activity then = Activity.IDLE;
    private @Nullable Vec3 focus;
    private @Nullable BlockPos bed;
    private @Nullable Vec3 bedExit;
    private double vx, vy, vz;
    private @Nullable Vec3 anchor;
    private int shieldTicks;
    private boolean armed, raised;
    private int calm, scan, dodge;
    private @Nullable Entity threat;
    private final List<Creeper> fuses = new ArrayList<>();
    private int roam = 50;
    /** Where it can walk around its spot, as last read, and the copy of the blocks it was read from. */
    private Walkway.@Nullable Survey known;
    private @Nullable BlockCopy area;
    /** A new look around: the blocks being copied, a few sections a tick, then read off the game's thread. */
    private BlockCopy.@Nullable Builder copying;
    private @Nullable CompletableFuture<Area> reading;
    private int surveyAge, retry;
    private List<Sight> sights = List.of(), sleeps = List.of(), hearths = List.of();
    /** The spot in front of a fire it stands on, or walks to: one each. */
    private @Nullable BlockPos warmAt;
    private List<Shore> shores = List.of();
    /** The water's edge it fishes from, or walks to for that. */
    private @Nullable Shore fishingFrom;
    /** Its bobber while the line is out, drawn with it; ticks to the next bite, or before casting again. */
    private @Nullable FishingHook hook;
    private int bite;
    /** Who it chats with, or walks up to for that; whether it went to them, rather than waited. */
    private @Nullable RestingPuppet partner;
    private boolean caller;
    /** Your eyes while you look at it, and for {@link #GAZE} ticks after; whether you crouch. */
    private @Nullable Vec3 gazer;
    private int gazed;
    /** You looked at it since its last tick: it crouches with you only then, never from memory. */
    private boolean gazerCrouching, watched, crouched;
    /** The way to a spot, searched off the game's thread in {@link #area}; empty when there is none. */
    private @Nullable CompletableFuture<Optional<List<Walkway.Step>>> route;
    private float headYaw, headPitch, headYawGoal, headPitchGoal;
    private int glance;
    /** What it held in each hand when its player logged off, given back once the sword and shield are put away. */
    private ItemStack held = ItemStack.EMPTY, heldOff = ItemStack.EMPTY;

    RestingPuppet(ClientLevel level, RestingPlayers.Spot spot, GameProfile profile) {
        super(level, profile);
        this.spot = spot;
        this.info = new PlayerInfo(profile, false);
        this.random = new Random(spot.name().hashCode() * 31L + System.nanoTime());
        this.blocks = Footing.live(level);
        // Each looks for monsters on a tick of its own, not all of them on the same one.
        this.scan = Math.floorMod(spot.name().hashCode(), 5);
        setSilent(true);
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        this.mount = RestingMount.of(level, spot, random);
        // Where it was found to rest before: there at once, near or far, loaded or not.
        rest = spot.rest();
        if (rest != null && rest.boat()) boat = RestingMount.boat(level, spot, rest.y(), random);
        else if (rest != null) shore = rest.at();
        Vec3 at = place();
        snap(at.x, at.y, at.z);
        setYRot(spot.yaw());
        yBodyRot = yBodyRotO = spot.yaw();
        yHeadRot = yHeadRotO = spot.yaw();
        timer = 40 + random.nextInt(80);
        // Dressed as they were when they logged off; the sword and shield it draws are its own.
        Gear.apply(this, spot.gear(), level.registryAccess());
        held = getItemBySlot(EquipmentSlot.MAINHAND).copy();
        heldOff = getItemBySlot(EquipmentSlot.OFFHAND).copy();
    }

    @Override
    protected @Nullable PlayerInfo getPlayerInfo() {
        return info;
    }

    /** Its name, marked offline: what its name tag says. */
    @Override
    public Component getDisplayName() {
        return Component.empty().append(getName()).append(" ")
                .append(Component.translatable("livinghorizon.offline.tag").withStyle(ChatFormatting.GRAY));
    }

    /** How it is drawn this tick. */
    public Look look() {
        if (riding) return Look.RIDE;
        if (!lively) return standing > 0 ? Look.STAND : Look.SIT;
        if (raised && activity != Activity.BED) return Look.STAND;
        return switch (activity) {
            case BED -> Look.BED;
            case SIT -> Look.SIT;
            default -> Look.STAND;
        };
    }

    /** In a bed. The bed is its sleeping position, so the game's renderer lays it along the bed. */
    public boolean inBed() {
        return activity == Activity.BED && bed != null;
    }

    /** What it rides, to draw under it; null when on foot. */
    public @Nullable Entity mount() {
        return riding && carried != null ? carried.entity : null;
    }

    /** Standing in front of a fire or a furnace, warming itself. */
    public boolean warming() {
        return activity == Activity.WARM && warmAt != null;
    }

    /** Shield up. */
    public boolean blocking() {
        return raised;
    }

    /** What it does and what it knows of around it, for the debug tools. */
    public String state() {
        if (riding && carried != null) return carried.state();
        if (gazed > 0 && gazer != null) return "looking at you at " + gazer + (crouched ? ", crouched" : "");
        if (far && loop != null) return "away: " + loop.state();
        Walkway.Survey map = known;
        return activity.name().toLowerCase(java.util.Locale.ROOT)
                + (route != null ? " (finding its way)" : "")
                + ", area " + (map == null ? "unread" : map.spots().size() + " spots")
                + (copying != null || reading != null ? " (reading)" : "")
                + ", " + sleeps.size() + " beds, " + sights.size() + " things to look at, " + hearths.size()
                + " places by a fire, " + shores.size() + " places to fish";
    }

    /** Thrown through the air by a blast. */
    public boolean thrown() {
        return activity == Activity.FLY;
    }

    /** Sword drawn, watching monsters. */
    public boolean guarding() {
        return activity == Activity.GUARD;
    }

    /** The bed it lies in or walks to: one sleeper per bed. */
    @Nullable BlockPos bed() {
        return bed;
    }

    /** Hit by the player: it raises its shield towards them, standing. */
    void strike(Vec3 from) {
        if (!lively || activity == Activity.FLY) return;
        if (riding) {
            // In the saddle: the shield up, the head turned to them.
            shieldTicks = SHIELD_TICKS;
            faceAt(from, 0);
            return;
        }
        if (activity == Activity.BED || activity == Activity.SIT || activity == Activity.FISH || activity == Activity.TALK) {
            // Out of bed: the bed is free again, the next look for one finds it.
            leaveBedIfIn();
            bed = null;
            start(Activity.IDLE, SHIELD_TICKS + 20);
        }
        shieldTicks = SHIELD_TICKS;
        faceAt(from, 180);
    }

    // --- Once per tick -------------------------------------------------------------------

    /**
     * Lives one tick.
     *
     * @param beds  the beds puppets lie in or walk to: one sleeper per bed
     * @param night whether it is time to sleep
     * @param work  sections of blocks all puppets may still copy this tick, shared: looking around costs
     */
    /**
     * Whether it lives its life here, so close to the camera at {@code eye}; past
     * {@link #NEAR_OUT}, it plays its loop until the camera comes within {@link #NEAR_IN}.
     */
    boolean near(Vec3 eye) {
        double across = Math.hypot(getX() - eye.x, getZ() - eye.z);
        near = near ? across < NEAR_OUT : across < NEAR_IN;
        return near;
    }

    /** Playing its loop, away from the camera, rather than living its life. */
    public boolean away() {
        return riding && carried != null ? carried.away() : far;
    }

    void tick(ClientLevel level, FarConfig config, Around around, int[] work, @Nullable Entity you) {
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        setOldPosAndRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        tickCount++;
        if (standing > 0) standing--;
        if (gazed > 0) gazed--;
        // Crouched with you only while it lives its life on foot.
        if (crouched && (gazed <= 0 || riding || !lively || !near)) crouch(false);

        roam = config.offlineRoam;
        if (lively != config.offlineLife) {
            lively = config.offlineLife;
            calmDown(beds);
            if (!lively) snap(place().x, place().y, place().z);
            placed = false;
            if (mount != null) mount.reset();
            if (boat != null) boat.reset();
        }
        if (carried == null || carried == boat) keepDry(level);
        RestingMount carrier = mount != null && config.showVehicles ? mount : boat;
        if (carrier != carried) {
            // On or off its mount, by the switch that shows mounts, or into or out of its
            // boat: back on its spot either way.
            carried = carrier;
            riding = carrier != null;
            calmDown(beds);
            placed = false;
            if (carrier != null) carrier.reset();
            Vec3 at = place();
            snap(at.x, at.y, at.z);
        }
        if (riding && carried != null) {
            ride(level, carried, config, work);
            return;
        }
        if (!lively) {
            Puppets.walk(this, 0f, 1f, 1f);
            sit(level);
            return;
        }

        if (!near) {
            away(level, beds, night);
            return;
        }
        if (far) {
            // Back near the camera: its life again, from where its loop left it.
            far = false;
            placed = false;
            start(Activity.IDLE, 20 + random.nextInt(40));
        }

        if (!Footing.loaded(level, blockPosition())) {
            // Nothing known of the blocks here: it only stands or sits where it is.
            placed = false;
            afar(night);
            return;
        }
        boolean first = !placed;
        placed = true;
        if (first) settle(level, true);

        if (shieldTicks > 0) shieldTicks--;
        if (tickCount % 5 == 0) swirls(level);
        survey(level, work);
        double before = getX(), beforeZ = getZ();
        if (activity != Activity.FLY) shove(level, you);

        if (activity == Activity.FLY) {
            fly(level);
        } else {
            watch(level, beds);
            // Thrown just now by a blast: nothing else this tick.
            if (activity == Activity.GUARD) guard(level);
            else if (activity != Activity.FLY) live(level, around);
            if (activity != Activity.WALK && activity != Activity.BED && activity != Activity.FLY && path == null) {
                settle(level, false);
            }
        }
        if (activity != Activity.FLY && activity != Activity.GUARD) respond();
        boolean wary = activity == Activity.GUARD || activity == Activity.FLY;
        arm(wary, wary || shieldTicks > 0);
        double moved = Math.hypot(getX() - before, getZ() - beforeZ);
        Puppets.walk(this, (float) Math.min(moved * 4.0, 1.0), 0.4f, 1f);
        setYRot(yBodyRot);
    }

    /**
     * Walked into by you, or by what you ride: it is shoved out of your way, getting up if it
     * sat, never into a wall. Never out of its bed.
     */
    private void shove(ClientLevel level, @Nullable Entity you) {
        if (you == null || you.isSpectator() || activity == Activity.BED) return;
        Entity body = you.getRootVehicle();
        if (!getBoundingBox().intersects(body.getBoundingBox())) return;
        double dx = getX() - body.getX(), dz = getZ() - body.getZ();
        double d = Math.hypot(dx, dz);
        if (d < 1e-4) {
            // Right on it: straight ahead of whoever walks in.
            double yaw = Math.toRadians(body.getYRot());
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            d = 1;
        }
        double step = Math.min(SHOVE, Math.max(0.05, (getBbWidth() + body.getBbWidth()) / 2 - d));
        double x = getX() + dx / d * step, z = getZ() + dz / d * step;
        if (!Footing.fits(blocks, x, getY(), z, Footing.STANDING)) return;
        setPos(x, getY(), z);
        if (activity == Activity.SIT) start(Activity.IDLE, 40 + random.nextInt(40));
    }

    // --- In the saddle ----------------------------------------------------------------------

    /** Carried by its mount, looking around; the shield up a moment after a hit. */
    private void ride(ClientLevel level, RestingMount ridden, FarConfig config, int[] work) {
        ridden.tick(level, lively, near, config.offlineRoam, work);
        Vec3 seat = ridden.rider();
        setPos(seat.x, seat.y, seat.z);
        yBodyRot = ridden.riderYaw();
        if (lively) {
            if (shieldTicks > 0) shieldTicks--;
            arm(false, shieldTicks > 0);
            if (shieldTicks > 0) {
                yHeadRot = yBodyRot + headYaw;
            } else if (gazed > 0 && gazer != null) {
                // Looked at from the saddle: it looks back, its head only.
                lookAt(gazer, false);
            } else {
                glance(false);
            }
        } else {
            arm(false, false);
            headYaw = approach(headYaw, 0, 6);
            headPitch = approach(headPitch, 0, 4);
            yHeadRot = yBodyRot + headYaw;
            setXRot(headPitch);
        }
        Puppets.walk(this, 0f, 1f, 1f);
        setYRot(yBodyRot);
    }

    // --- Away from the camera -----------------------------------------------------------------

    /**
     * Its loop: a walk around where it stands with pauses, seated in some of them by day; at
     * night on its feet, or in the bed it already lies in until the morning. It never walks
     * back to its spot: the loop starts wherever its life left it.
     */
    private void away(ClientLevel level, Set<BlockPos> beds, boolean night) {
        if (!far) {
            far = true;
            if (!(night && activity == Activity.BED)) calmDown(beds);
            loop = null;
        }
        if (activity == Activity.BED) {
            if (night) {
                Puppets.walk(this, 0f, 1f, 1f);
                return;
            }
            // Up in the morning: its loop starts beside the bed.
            leaveBed(beds);
            loop = null;
        }
        RestingLoop playing = loop(level);
        double moved = playing.tick((int) level.getGameTime(), false);
        if (getX() != playing.x || getY() != playing.y || getZ() != playing.z) setPos(playing.x, playing.y, playing.z);
        yBodyRot = playing.body;
        activity = playing.pausing && !night ? Activity.SIT : Activity.IDLE;
        glance(false);
        Puppets.walk(this, (float) Math.min(moved * 4.0, 1.0), 0.4f, 1f);
        setYRot(yBodyRot);
    }

    /**
     * Its loop around where it stands, the ground under it read once, never farther from its
     * spot than it roams ({@link FarConfig#offlineRoam}): its spot is the middle of where it may go.
     */
    private RestingLoop loop(ClientLevel level) {
        if (loop == null) {
            long seed = spot.name().toLowerCase(java.util.Locale.ROOT).hashCode() * 0x9E3779B97F4A7C15L;
            double x = getX(), y = getY(), z = getZ(), cx = spot.x(), cz = spot.z(), radius = roam;
            // Where no animation fits, a cramped one; where not even that, it turns on its spot.
            loop = new RestingLoop(seed, x, y, z, yBodyRot, MobAnimations.WALK, true,
                    () -> MobPaths.plan(level, x, y, z, 0.6, seed)
                            .thenApply(choice -> MobPaths.orCramped(MobPaths.within(choice, cx, cz, radius, 0.6, seed), 0.6, seed)));
        }
        return loop;
    }

    // --- Out of the water -------------------------------------------------------------------

    /**
     * Where it rests, decided from the blocks once those around its spot are all there, again
     * when that changes; looked at once a second. Its spot in the air: the ground under it.
     * In water, or over it (water is what it falls onto): the nearest shore within
     * {@link #SHORE} blocks, or a boat on the surface when there is none, left once the water
     * at its spot is gone. Before the blocks are there, a guess from the far world
     * ({@link #guess}). What it finds is kept with its spot ({@link #restFound}).
     */
    private void keepDry(ClientLevel level) {
        if (dryWait > 0) {
            dryWait--;
            return;
        }
        dryWait = SHORE_WAIT;
        if (!Footing.loaded(level, home())) {
            if (rest == null) guess(level);
            return;
        }
        double x = spot.x(), y = landing(level), z = spot.z();
        if (!Footing.wet(blocks, x, y, z)) {
            boat = null;
            guessed = false;
            // In the air: on the ground under it. On its spot: nowhere else.
            Vec3 ground = y < spot.y() - 1e-3 ? new Vec3(x, y, z) : null;
            if (!Objects.equals(shore, ground)) forgetShore(ground);
            found(ground == null ? null : new RestingPlayers.Rest(x, y, z, false));
            return;
        }
        if (boat != null && !guessed) return;
        Vec3 known = shore;
        if (known != null && !guessed && !Double.isNaN(Footing.stand(blocks, (int) Math.floor(known.x),
                (int) Math.floor(known.y + 0.01), (int) Math.floor(known.z)))) {
            return;
        }
        if (!Footing.loadedAround(blocks, x, z, SHORE)) return;
        guessed = false;
        Vec3 land = Footing.shore(blocks, x, y, z, SHORE);
        if (land != null) {
            boat = null;
            forgetShore(land);
            found(new RestingPlayers.Rest(land.x, land.y, land.z, false));
            return;
        }
        double surface = Footing.surface(blocks, x, y, z);
        forgetShore(null);
        boat = RestingMount.boat(level, spot, surface, random);
        found(boat == null ? null : new RestingPlayers.Rest(x, surface, z, true));
    }

    /** Where it was found to rest, to be kept with its spot. */
    private void found(RestingPlayers.@Nullable Rest now) {
        if (Objects.equals(rest, now)) return;
        rest = now;
        restFound = true;
    }

    /** Whether it found where it rests since last asked: {@link #rest()}, to keep with its spot. */
    boolean restFound() {
        boolean found = restFound;
        restFound = false;
        return found;
    }

    /** Where it rests, when it is not its spot. */
    RestingPlayers.@Nullable Rest rest() {
        return rest;
    }

    /**
     * Its spot far away, its blocks not loaded and where it rests not found yet: what the far
     * world (Voxy, Distant Horizons) holds under it, asked once. A spot high above the ground
     * - a player who logged off flying - rests on it; a spot on water or over it rests in a
     * boat. A guess only: found again from the blocks once they are loaded.
     */
    private void guess(ClientLevel level) {
        LodWorld.Surface under = farSurface;
        if (under != null) {
            farSurface = null;
            double top = under.y() + 1;
            if (under.water() && top < spot.y() + 4) {
                // On the water or above it, not deep under it: afloat, as it would be once the blocks are there.
                boat = RestingMount.boat(level, spot, under.y() + WATER_TOP, random);
                guessed = boat != null;
            } else if (!under.water() && top < spot.y() - 1) {
                forgetShore(new Vec3(spot.x(), top, spot.z()));
                guessed = true;
                if (!riding) {
                    snap(spot.x(), top, spot.z());
                    loop = null;
                }
            }
            return;
        }
        if (askedFar || boat != null || shore != null) return;
        askedFar = true;
        LodWorld.surface(Mth.floor(spot.x()), Mth.floor(spot.z()))
                .thenAcceptAsync(found -> farSurface = found.orElse(null), Minecraft.getInstance());
    }

    /** Where it rests while not riding: its spot, or the ground or shore it rests on instead. */
    private Vec3 place() {
        Vec3 land = shore;
        return land != null ? land : new Vec3(spot.x(), spot.y(), spot.z());
    }

    /** Where its spot comes to rest: the spot, or what is under it when it is in the air, water included. */
    private double landing(ClientLevel level) {
        double x = spot.x(), y = spot.y(), z = spot.z();
        if (Footing.wet(blocks, x, y, z) || Footing.supported(blocks, x, y, z)) return y;
        return Footing.floorBelow(blocks, x, y, z);
    }

    /**
     * A new place to rest. Away from the camera and left in water, it is moved there, its loop
     * drawn again around it; anywhere else its loop goes on where it is.
     */
    private void forgetShore(@Nullable Vec3 land) {
        shore = land;
        if (!far || riding || !Footing.wet(blocks, getX(), getY(), getZ())) return;
        Vec3 at = place();
        snap(at.x, at.y, at.z);
        loop = null;
    }

    /**
     * Living, near the camera, and in water (its spot was, or it was thrown in): out onto the
     * nearest shore within {@link #SHORE} blocks of where it is, or its spot's.
     */
    private void ashore(ClientLevel level) {
        if (!Footing.wet(blocks, getX(), getY(), getZ())) return;
        if (shoreWait > 0) {
            shoreWait--;
            return;
        }
        shoreWait = SHORE_WAIT;
        if (!Footing.loadedAround(blocks, getX(), getZ(), SHORE)) return;
        Vec3 land = Footing.shore(blocks, getX(), getY(), getZ(), SHORE);
        if (land == null) land = shore;
        if (land == null) return;
        bed = null;
        calmDownQuietly();
        fall = 0;
        snap(land.x, land.y, land.z);
    }

    // --- A life ---------------------------------------------------------------------------

    private void live(ClientLevel level, Around around) {
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        if (route != null) routing(level, beds);
        switch (activity) {
            case WALK -> {
                if (step(level, STROLL, false)) arrive(level, beds);
                return;
            }
            case BED -> {
                BlockPos head = bed;
                if (!night || head == null || !(level.getBlockState(head).getBlock() instanceof BedBlock)) {
                    leaveBed(beds);
                    start(Activity.IDLE, 40);
                }
                return;
            }
            case FACE, WARM -> {
                if (focus != null && gazed <= 0) faceAt(focus, 6);
            }
            case FISH -> {
                if (night) start(Activity.IDLE, 20);
                else fish(level);
            }
            case TALK -> {
                if (night) start(Activity.IDLE, 20);
                else talk();
            }
            default -> {
            }
        }
        if (gazed <= 0 && activity != Activity.TALK) {
            glance(activity == Activity.FACE || activity == Activity.WARM || activity == Activity.FISH);
        }
        if (--timer <= 0) decide(level, around);
    }

    /** What it does next. */
    private void decide(ClientLevel level, Around around) {
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        if (route != null) return;
        Walkway.Survey map = known;
        if (night) {
            if (map == null) {
                // Still reading what is around: a moment more before giving up on a bed.
                start(Activity.IDLE, 20);
                return;
            }
            if (towardsBed(level, beds)) return;
            // No bed it can reach: on its feet, looking around, until one is free.
            start(Activity.IDLE, AWAKE + random.nextInt(AWAKE));
            return;
        }
        if (around.evening() && activity != Activity.WARM && random.nextInt(100) < WARM_CHANCE
                && towardsHearth(around.crowd())) {
            return;
        }
        int roll = random.nextInt(100);
        if (roll < 30 || map == null) {
            start(Activity.IDLE, 60 + random.nextInt(100));
        } else if (roll < 45) {
            start(Activity.SIT, 200 + random.nextInt(300));
        } else if (roll < 55 && towardsWater(around.fishers())) {
            return;
        } else if (roll < 63 && towardsChat(map, around.crowd())) {
            return;
        } else if (!(roll < 72 && towardsSomething()) && !stroll(map)) {
            start(Activity.IDLE, 60 + random.nextInt(60));
        }
    }

    private void start(Activity next, int ticks) {
        reelIn();
        if (next != Activity.TALK) hangUp();
        if (next != Activity.WARM) warmAt = null;
        if (next != Activity.FISH) fishingFrom = null;
        activity = next;
        timer = ticks;
        path = null;
        route = null;
        if (next != Activity.FACE && next != Activity.WARM) focus = null;
    }

    /** A stroll to somewhere it can reach around its spot. */
    private boolean stroll(Walkway.Survey map) {
        BlockPos at = feet();
        List<Walkway.Step> spots = map.spots();
        for (int tries = 0; tries < 12 && !spots.isEmpty(); tries++) {
            Walkway.Step step = spots.get(random.nextInt(spots.size()));
            if (step.distanceSqr(at.getX(), at.getZ()) < 9) continue;
            return routeTo(step, Activity.IDLE);
        }
        return false;
    }

    /** To stand in front of a chest, a crafting table or a workstation it can reach. */
    private boolean towardsSomething() {
        if (sights.isEmpty()) return false;
        Sight sight = sights.get(random.nextInt(sights.size()));
        if (!routeTo(sight.front(), Activity.FACE)) return false;
        // Set once on its way: starting the way clears what it looked at before.
        focus = Vec3.atCenterOf(sight.block());
        return true;
    }

    /**
     * To a fire or a furnace it can reach, one of the nearest, on a spot in front of it that
     * nobody else stands on or walks to.
     */
    private boolean towardsHearth(Collection<RestingPuppet> crowd) {
        if (hearths.isEmpty()) return false;
        BlockPos at = feet();
        List<Sight> nearest = new ArrayList<>(hearths);
        nearest.sort(java.util.Comparator.comparingDouble(sight -> sight.front().distanceSqr(at.getX(), at.getZ())));
        List<Sight> free = new ArrayList<>();
        for (Sight sight : nearest) {
            BlockPos front = new BlockPos(sight.front().x(), sight.front().y(), sight.front().z());
            boolean taken = false;
            for (RestingPuppet other : crowd) {
                if (other != this && front.equals(other.warmAt)) taken = true;
            }
            if (!taken) free.add(sight);
            if (free.size() == 3) break;
        }
        if (free.isEmpty()) return false;
        Sight sight = free.get(random.nextInt(free.size()));
        if (!routeTo(sight.front(), Activity.WARM)) return false;
        focus = Vec3.atCenterOf(sight.block());
        warmAt = new BlockPos(sight.front().x(), sight.front().y(), sight.front().z());
        return true;
    }

    /** To the nearest free bed it can reach, which it keeps for itself on the way. */
    private boolean towardsBed(ClientLevel level, Set<BlockPos> beds) {
        BlockPos at = feet();
        Sight best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Sight sight : sleeps) {
            BlockPos head = sight.block();
            if (beds.contains(head) || !freeBed(level.getBlockState(head))) continue;
            double distance = head.distSqr(at);
            if (distance < bestDistance) {
                best = sight;
                bestDistance = distance;
            }
        }
        if (best == null) return false;
        bed = best.block();
        beds.add(best.block());
        return routeTo(best.front(), Activity.BED);
    }

    /**
     * Starts looking for the way to a spot, off the game's thread, in the blocks it last read
     * around; it stays as it is meanwhile, then walks.
     */
    private boolean routeTo(Walkway.Step target, Activity after) {
        BlockCopy copy = area;
        if (copy == null) return false;
        BlockPos at = feet();
        BlockPos home = home();
        int radius = roam + 4, budget = budget(roam);
        start(activity == Activity.SIT || activity == Activity.FISH || activity == Activity.TALK
                || activity == Activity.WARM || activity == Activity.FACE ? Activity.IDLE : activity, 400);
        route = GroundWork.run(() -> Optional.ofNullable(Walkway.way((x, y, z) -> Footing.stand(copy, x, y, z),
                at.getX(), at.getY(), at.getZ(), target, home.getX(), home.getZ(), radius, budget)));
        then = after;
        return true;
    }

    /** Once the way is found, off it goes. */
    private void routing(ClientLevel level, Set<BlockPos> beds) {
        CompletableFuture<Optional<List<Walkway.Step>>> searching = route;
        if (searching == null || !searching.isDone()) return;
        route = null;
        List<Walkway.Step> way = searching.isCompletedExceptionally() ? null : searching.join().orElse(null);
        if (way == null) {
            // No way there after all: the bed it kept is free again.
            if (then == Activity.BED) leaveBed(beds);
            start(Activity.IDLE, 40 + random.nextInt(60));
        } else if (way.isEmpty()) {
            arrive(level, beds);
        } else {
            path = way;
            pathIndex = 0;
            activity = Activity.WALK;
            timer = 0;
        }
    }

    // --- What is around -----------------------------------------------------------------------

    /** A block worth going to, and the spot to stand on in front of it. */
    private record Sight(BlockPos block, Walkway.Step front) {
    }

    /** What it read around its spot: where it can walk, from the copy of the blocks it read, and what is there to go to. */
    private record Area(BlockCopy blocks, Walkway.@Nullable Survey map, List<Sight> sights, List<Sight> sleeps,
                        List<Sight> hearths, List<Shore> shores) {
    }

    /**
     * Reads where it can walk around its spot and what is there to go to: beds, chests,
     * workstations. The blocks around are copied a few sections a tick, then read off the
     * game's thread. Read again now and then, as blocks change.
     */
    private void survey(ClientLevel level, int[] work) {
        surveyAge++;
        boolean due = known == null ? surveyAge >= retry : surveyAge > RESURVEY || lost();
        if (copying == null && reading == null && route == null && due) {
            BlockPos at = feet();
            // Nowhere to stand here: nothing to read, until it stands again.
            if (Double.isNaN(Footing.stand(blocks, at.getX(), at.getY(), at.getZ()))
                    && Double.isNaN(Footing.stand(blocks, at.getX(), at.getY() + 1, at.getZ()))
                    && Double.isNaN(Footing.stand(blocks, at.getX(), at.getY() - 1, at.getZ()))) {
                return;
            }
            BlockPos home = home();
            int reach = roam + 4;
            copying = BlockCopy.of(level, home.getX() - reach, home.getY() - COPY_BELOW, home.getZ() - reach,
                    home.getX() + reach, home.getY() + COPY_ABOVE, home.getZ() + reach);
            surveyAge = 0;
        }
        BlockCopy.Builder builder = copying;
        if (builder != null) {
            work[0] -= builder.copy(level, Math.max(0, work[0]));
            if (!builder.done()) return;
            copying = null;
            BlockCopy copy = builder.build();
            BlockPos at = feet();
            BlockPos home = home();
            int radius = roam, budget = budget(roam);
            reading = GroundWork.run(() -> read(copy, at, home, radius, budget));
            return;
        }
        CompletableFuture<Area> pending = reading;
        if (pending == null || !pending.isDone()) return;
        reading = null;
        Area read = pending.isCompletedExceptionally() ? null : pending.join();
        if (read == null || read.map() == null) {
            // It could not stand where it was read from: again a little later.
            retry = RETRY;
            return;
        }
        known = read.map();
        area = read.blocks();
        sights = read.sights();
        sleeps = read.sleeps();
        shores = read.shores();
        hearths = read.hearths();
        // Idle for want of knowing better (no bed known at night): think again with what it now knows.
        if (activity == Activity.IDLE) timer = Math.min(timer, 20);
    }

    /** Off the game's thread: everywhere it can walk from {@code at}, and the beds, things and water beside. */
    private static Area read(BlockCopy copy, BlockPos at, BlockPos home, int radius, int budget) {
        Walkway.Survey map = Walkway.around((x, y, z) -> Footing.stand(copy, x, y, z), at.getX(), at.getY(), at.getZ(),
                home.getX(), home.getZ(), radius, budget);
        if (map == null) return new Area(copy, null, List.of(), List.of(), List.of(), List.of());
        Map<BlockPos, Sight> sights = new LinkedHashMap<>(), sleeps = new LinkedHashMap<>(), hearths = new LinkedHashMap<>();
        Map<BlockPos, Shore> shores = new LinkedHashMap<>();
        for (Walkway.Step spot : map.spots()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = new BlockPos(spot.x() + side.getStepX(), spot.y(), spot.z() + side.getStepZ());
                BlockState state = copy.state(next);
                // Every spot beside a fire: several of them may stand round it.
                if (HEARTHS.contains(state.getBlock())) {
                    hearths.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Sight(next, spot));
                }
                if (INTERESTING.contains(state.getBlock())) {
                    sights.putIfAbsent(next, new Sight(next, spot));
                } else if (state.getBlock() instanceof BedBlock) {
                    BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? next
                            : next.relative(state.getValue(BedBlock.FACING));
                    sleeps.putIfAbsent(head, new Sight(head, spot));
                }
                Vec3 water = openWater(copy, spot, side);
                if (water != null) shores.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Shore(spot, water));
            }
        }
        return new Area(copy, map, new ArrayList<>(sights.values()), new ArrayList<>(sleeps.values()),
                new ArrayList<>(hearths.values()), new ArrayList<>(shores.values()));
    }

    /**
     * Off the area it knows - fallen, thrown, or its floor broken - while standing still: what
     * it read no longer says where it can go from here. Not more often than every two seconds.
     */
    private boolean lost() {
        Walkway.Survey map = known;
        if (map == null || surveyAge < 40) return false;
        if (activity == Activity.WALK || activity == Activity.BED || activity == Activity.FLY) return false;
        BlockPos at = feet();
        return map.reached(at.getX(), at.getY(), at.getZ()) == null && map.reached(at.getX(), at.getY() + 1, at.getZ()) == null
                && map.reached(at.getX(), at.getY() - 1, at.getZ()) == null;
    }

    /** Spots read at most around its spot: the area of the circle, and as much again for floors. */
    private static int budget(int radius) {
        return (int) Math.min(60_000, Math.PI * radius * radius * 1.6 + 200);
    }

    private BlockPos home() {
        return BlockPos.containing(spot.x(), spot.y(), spot.z());
    }

    /** Where a walk was going, and what it was for. */
    private void arrive(ClientLevel level, Set<BlockPos> beds) {
        path = null;
        switch (then) {
            case FACE -> start(Activity.FACE, 100 + random.nextInt(140));
            case WARM -> start(Activity.WARM, 300 + random.nextInt(300));
            case TALK -> {
                start(Activity.TALK, CHAT + random.nextInt(CHAT));
                // Both talk as long, the one who waited from now on too.
                RestingPuppet other = partner;
                if (other != null && other.partner == this) other.timer = timer;
            }
            case FISH -> {
                start(Activity.FISH, 900 + random.nextInt(900));
                bite = 10 + random.nextInt(30);
            }
            case BED -> {
                BlockPos head = bed;
                if (head != null && freeBed(level.getBlockState(head))) {
                    bedExit = position();
                    setSleepingPos(head);
                    snap(head.getX() + 0.5, head.getY() + BED_HEIGHT, head.getZ() + 0.5);
                    activity = Activity.BED;
                } else {
                    leaveBed(beds);
                    start(Activity.IDLE, 40);
                }
            }
            case GUARD -> activity = Activity.GUARD;
            default -> start(Activity.IDLE, 40 + random.nextInt(80));
        }
    }

    /**
     * One tick along the way: a stroll, or backing off from a monster while facing it.
     * True once there; a spot that can no longer be stood on ends the walk where it is.
     */
    private boolean step(ClientLevel level, double speed, boolean backing) {
        List<Walkway.Step> way = path;
        if (way == null || pathIndex >= way.size()) return true;
        Walkway.Step next = way.get(pathIndex);
        double tx = next.x() + 0.5, tz = next.z() + 0.5, ty = next.height();
        double dx = tx - getX(), dz = tz - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= speed) {
            if (Double.isNaN(Footing.stand(blocks, next.x(), next.y(), next.z()))) {
                // Built on or dug out since it was read: read again what is around.
                path = null;
                surveyAge = Math.max(surveyAge, RESURVEY);
                return true;
            }
            setPos(tx, ty, tz);
            pathIndex++;
        } else {
            double y = getY();
            // Up a step at once, as with a jump; down only once over the edge.
            if (ty > y) y = Math.min(ty, y + 0.3);
            else if (ty < y && distance < 0.45) y = Math.max(ty, y - 0.4);
            setPos(getX() + dx / distance * speed, y, getZ() + dz / distance * speed);
        }
        if (!backing && distance > 1e-3) turnTo(yawTowards(dx, dz), 20);
        if (gazed <= 0) {
            // Looked at, its head is turned to you instead (respond).
            headYaw = approach(headYaw, 0, 10);
            headPitch = approach(headPitch, 5, 5);
        }
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
        return pathIndex >= way.size();
    }

    // --- You -------------------------------------------------------------------------------

    /** You look at it, from {@code eye}, crouching or not: it looks back for a while. */
    void lookedAt(Vec3 eye, boolean crouching) {
        gazer = eye;
        gazed = GAZE;
        gazerCrouching = crouching;
        watched = true;
    }

    /** Looking back at you while you look at it, crouched when you crouch and it faces you. */
    public boolean crouched() {
        return crouched;
    }

    /**
     * Looked at: it looks back at you. Standing, it turns to you and, facing you, crouches when
     * you crouch; walking, seated or fishing, it turns its head only. Asleep, on guard or
     * thrown, never.
     */
    private void respond() {
        Vec3 eye = gazer;
        boolean crouch = false;
        if (gazed > 0 && eye != null) {
            boolean standing = activity == Activity.IDLE || activity == Activity.FACE || activity == Activity.WARM;
            if (standing || activity == Activity.WALK || activity == Activity.SIT || activity == Activity.FISH) {
                lookAt(eye, standing);
                crouch = watched && gazerCrouching && standing && facing(eye);
            }
        }
        watched = false;
        if (crouch != crouched) crouch(crouch);
    }

    private void crouch(boolean down) {
        crouched = down;
        setPose(down ? Pose.CROUCHING : Pose.STANDING);
    }

    /** Its head to {@code target}, as far as its neck turns; its body too when {@code body}. */
    private void lookAt(Vec3 target, boolean body) {
        double dx = target.x - getX(), dz = target.z - getZ();
        double dy = target.y - getEyeY();
        float yaw = yawTowards(dx, dz);
        if (body) turnTo(yaw, 10);
        headYaw = approach(headYaw, Math.max(-NECK, Math.min(NECK, wrap(yaw - yBodyRot))), 15);
        headPitch = approach(headPitch, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), 10);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    /** Its body turned to {@code target}, near enough. */
    private boolean facing(Vec3 target) {
        return Math.abs(wrap(yawTowards(target.x - getX(), target.z - getZ()) - yBodyRot)) < FACING;
    }

    // --- A chat ------------------------------------------------------------------------------

    /** Who it chats with, or walks up to or waits for, for that; null when nobody. */
    public @Nullable RestingPuppet partner() {
        return partner;
    }

    /** Chatting now: face to face with the other, both there. */
    public boolean chatting() {
        RestingPuppet other = partner;
        return activity == Activity.TALK && other != null && other.partner == this && other.activity == Activity.TALK;
    }

    /** Free to be called over for a chat: living its life near the camera, on foot, doing nothing much. */
    private boolean free() {
        return lively && near && !far && !riding && partner == null && route == null && path == null
                && (activity == Activity.IDLE || activity == Activity.SIT || activity == Activity.FACE
                || activity == Activity.WARM);
    }

    /**
     * To someone free within {@link #TALK_REACH} blocks, to chat: it walks to a spot
     * {@link #TALK_GAP} blocks from them, who waits, turned to it. Never when another pair
     * chats within {@link #TALKERS_APART} blocks of them.
     */
    private boolean towardsChat(Walkway.Survey map, Collection<RestingPuppet> crowd) {
        List<RestingPuppet> others = new ArrayList<>(crowd);
        if (others.size() < 2) return false;
        int first = random.nextInt(others.size());
        BlockPos at = feet();
        for (int i = 0; i < others.size(); i++) {
            RestingPuppet other = others.get((first + i) % others.size());
            if (other == this || !other.free() || other.distanceToSqr(this) > TALK_REACH * TALK_REACH) continue;
            if (chatNear(crowd, other)) continue;
            BlockPos them = other.feet();
            Walkway.Step best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Walkway.Step step : map.spots()) {
                if (Math.abs(step.y() - them.getY()) > 1) continue;
                double apart = Math.hypot(step.x() - them.getX(), step.z() - them.getZ());
                if (apart < TALK_GAP - 0.5 || apart > TALK_GAP + 0.6) continue;
                double distance = step.distanceSqr(at.getX(), at.getZ());
                if (distance < bestDistance) {
                    best = step;
                    bestDistance = distance;
                }
            }
            if (best == null || !routeTo(best, Activity.TALK)) continue;
            partner = other;
            caller = true;
            other.calledBy(this);
            return true;
        }
        return false;
    }

    /** Whether another pair chats, or is about to, within {@link #TALKERS_APART} blocks of {@code them}. */
    private boolean chatNear(Collection<RestingPuppet> crowd, RestingPuppet them) {
        for (RestingPuppet other : crowd) {
            if (other == this || other == them || other.partner == null) continue;
            if (other.distanceToSqr(them) < TALKERS_APART * TALKERS_APART) return true;
        }
        return false;
    }

    /** Called over for a chat: it stops what it does and waits, turned to the one coming. */
    private void calledBy(RestingPuppet other) {
        start(Activity.TALK, TALK_WAIT);
        partner = other;
        caller = false;
    }

    /** The chat over, or never started: the other is free again, and goes back to its life. */
    private void hangUp() {
        RestingPuppet other = partner;
        partner = null;
        if (other == null || other.partner != this) return;
        other.partner = null;
        if (other.activity == Activity.TALK) other.start(Activity.IDLE, 40 + other.random.nextInt(60));
    }

    /**
     * A tick of a chat: turned to the other, its head nodding while it speaks, still while it
     * listens; each speaks in turn. Waiting for the other to come, it watches them come.
     */
    private void talk() {
        RestingPuppet other = partner;
        if (other == null || other.partner != this || other.far) {
            start(Activity.IDLE, 20 + random.nextInt(40));
            return;
        }
        boolean both = other.activity == Activity.TALK;
        if (both && other.distanceToSqr(this) > (TALK_GAP + 2) * (TALK_GAP + 2)) {
            // Pushed apart or carried off: the chat is over.
            start(Activity.IDLE, 20 + random.nextInt(40));
            return;
        }
        lookAt(other.getEyePosition(), true);
        if (!both) return;
        boolean speaking = (tickCount / TURN_TO_SPEAK) % 2 == (caller ? 0 : 1);
        float nod = speaking ? (float) Math.sin(tickCount * 0.5) * 7f : (tickCount % 90 < 12 ? 6f : 0f);
        float sway = speaking ? (float) Math.sin(tickCount * 0.17) * 6f : 0f;
        yHeadRot += sway;
        setXRot(headPitch + nod);
    }

    // --- Fishing ---------------------------------------------------------------------------

    /** Where it stands at the water's edge, and the spot on the water its bobber floats on. */
    private record Shore(Walkway.Step front, Vec3 water) {
    }

    /** The edge it fishes from, or walks to for that: one who fishes on a stretch of shore. */
    @Nullable Vec3 fishing() {
        Shore at = fishingFrom;
        return at == null ? null : Vec3.atBottomCenterOf(new BlockPos(at.front().x(), at.front().y(), at.front().z()));
    }

    /** Its bobber, while its line is out; drawn with it. */
    public @Nullable FishingHook hook() {
        return hook;
    }

    /**
     * The water a player standing on {@code spot} could fish in towards {@code side}: open
     * water just below its feet for {@link #CAST} blocks out, nothing over it. Where the
     * bobber floats, or null.
     */
    private static @Nullable Vec3 openWater(BlockCopy copy, Walkway.Step spot, Direction side) {
        for (int k = 1; k <= CAST; k++) {
            BlockPos surface = new BlockPos(spot.x() + side.getStepX() * k, spot.y() - 1, spot.z() + side.getStepZ() * k);
            if (!copy.state(surface).getFluidState().is(FluidTags.WATER) || !copy.state(surface.above()).isAir()) return null;
        }
        return new Vec3(spot.x() + side.getStepX() * CAST + 0.5, spot.y() - 0.1, spot.z() + side.getStepZ() * CAST + 0.5);
    }

    /** To the water's edge to fish, where nobody else fishes within {@link #FISHERS_APART} blocks. */
    private boolean towardsWater(List<Vec3> fishers) {
        if (shores.isEmpty()) return false;
        Shore shore = shores.get(random.nextInt(shores.size()));
        Vec3 at = Vec3.atBottomCenterOf(new BlockPos(shore.front().x(), shore.front().y(), shore.front().z()));
        for (Vec3 other : fishers) {
            if (other.distanceToSqr(at) < FISHERS_APART * FISHERS_APART) return false;
        }
        if (!routeTo(shore.front(), Activity.FISH)) return false;
        fishingFrom = shore;
        fishers.add(at);
        return true;
    }

    /**
     * A tick of fishing, facing its bobber: a cast with a splash, the bobber bobbing, a fish
     * coming in a wake, a bite that pulls the bobber under, the line reeled in, cast again.
     * Nothing is caught: none of it reaches the world.
     */
    private void fish(ClientLevel level) {
        Shore at = fishingFrom;
        if (at == null) {
            start(Activity.IDLE, 40);
            return;
        }
        Vec3 water = at.water();
        if (gazed <= 0) faceAt(water, 6);
        FishingHook bobber = hook;
        if (bobber == null) {
            if (--bite > 0) return;
            // Cast: a rod in the hand, the bobber on the water with a splash.
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            bobber = Puppets.numbered(new FishingHook(EntityType.FISHING_BOBBER, level));
            bobber.setOwner(this);
            bobber.setPos(water.x, water.y, water.z);
            bobber.setOldPosAndRot();
            hook = bobber;
            splash(level, water, 6);
            bite = 100 + random.nextInt(400);
            return;
        }
        bite--;
        double sink = bite <= 0 && bite > -DIP ? 0.25 : 0;
        bobber.setOldPosAndRot();
        bobber.setPos(water.x, water.y + Math.sin(tickCount * 0.15) * 0.03 - sink, water.z);
        if (bite > 0 && bite <= WAKE) {
            // A fish coming: its wake on the water, nearer every tick.
            double u = bite / (double) WAKE, angle = (spot.name().hashCode() & 7) * Math.PI / 4;
            level.addParticle(ParticleTypes.FISHING, water.x + Math.cos(angle) * u * 3, water.y + 0.1,
                    water.z + Math.sin(angle) * u * 3, 0, 0, 0);
        } else if (bite == 0) {
            splash(level, water, 10);
        } else if (bite <= -DIP) {
            // Reeled in: the line back, a moment before the next cast.
            reelIn();
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            bite = 30 + random.nextInt(60);
        }
    }

    private void splash(ClientLevel level, Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.SPLASH, at.x + (random.nextDouble() - 0.5) * 0.4, at.y + 0.1,
                    at.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
        }
        level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, 0, 0.05, 0);
    }

    /** The line out of the water, and the rod put away: what it held back in its hand. */
    private void reelIn() {
        if (hook != null) {
            hook = null;
            fishing = null;
        }
        if (activity == Activity.FISH && !armed) setItemSlot(EquipmentSlot.MAINHAND, held.copy());
    }

    // --- Monsters ---------------------------------------------------------------------------

    /** Monsters around, and creepers about to blow up. */
    private void watch(ClientLevel level, Set<BlockPos> beds) {
        for (int i = fuses.size() - 1; i >= 0; i--) {
            Creeper creeper = fuses.get(i);
            if (creeper.isRemoved()) {
                // Gone while about to blow up: it blew up (the client sees it die of its own blast).
                if (creeper.getSwelling(1f) > 0.6f && seen(level, creeper.position().add(0, 1, 0))) {
                    thrown(creeper.position(), beds);
                }
                fuses.remove(i);
            } else if (creeper.distanceToSqr(this) > BLAST * BLAST * 4) {
                fuses.remove(i);
            }
        }
        if (activity == Activity.FLY || ++scan % 5 != 0) return;
        // Nearest first, and a look only where it matters: until it sees one, and at a creeper
        // about to blow up beside it. A look is a ray through the blocks; the distance is free.
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DANGER),
                e -> e instanceof Enemy && e.isAlive() && !e.isSpectator() && e.distanceToSqr(this) < DANGER * DANGER);
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        Entity nearest = null;
        for (LivingEntity entity : around) {
            double distance = entity.distanceToSqr(this);
            if (nearest != null && distance >= BLAST * BLAST) break;
            boolean fuse = entity instanceof Creeper creeper && creeper.getSwelling(1f) > 0 && !fuses.contains(creeper)
                    && distance < BLAST * BLAST;
            if (nearest != null && !fuse || !seen(level, entity.getEyePosition())) continue;
            if (fuse) fuses.add((Creeper) entity);
            if (nearest == null) nearest = entity;
        }
        threat = nearest;
        if (nearest != null) {
            calm = CALM_TICKS;
            if (activity != Activity.GUARD) {
                leaveBed(beds);
                start(Activity.GUARD, 0);
            }
        } else if (activity == Activity.GUARD && (calm -= 5) <= 0) {
            start(Activity.IDLE, 60);
        }
    }

    /**
     * In plain sight: nothing solid between its eyes and there - no wall, shut door or window.
     * A monster it cannot see cannot reach it either: safe indoors, it stays calm.
     */
    private boolean seen(ClientLevel level, Vec3 target) {
        return level.clip(new ClipContext(getEyePosition(), target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Sword drawn and shield up, it watches the nearest monster and keeps out of its way. */
    private void guard(ClientLevel level) {
        Entity watched = threat;
        if (path != null) {
            boolean there = step(level, HURRY, watched != null);
            if (watched != null) faceAt(watched.getEyePosition(), 25);
            if (there) path = null;
            return;
        }
        if (watched == null) return;
        faceAt(watched.getEyePosition(), 25);
        double distance = Math.sqrt(watched.distanceToSqr(this));
        if (--dodge > 0) return;
        if (distance < TOO_CLOSE) {
            away(level, watched, 4, TOO_CLOSE + 1.5);
            dodge = 20;
        } else if (random.nextInt(60) == 0) {
            // A step aside now and then, round the monster rather than towards it.
            away(level, watched, 2, distance - 0.5);
            dodge = 40;
        }
    }

    /** A spot within {@code within} blocks, at least {@code keep} from the monster; the farthest. */
    private void away(ClientLevel level, Entity from, int within, double keep) {
        BlockPos at = feet();
        Walkway.Survey map = Walkway.around((x, y, z) -> Footing.stand(blocks, x, y, z), at.getX(), at.getY(), at.getZ(),
                at.getX(), at.getZ(), within, 120);
        if (map == null) return;
        Walkway.Step best = null;
        double bestDistance = keep * keep;
        for (Walkway.Step step : Walkway.shuffled(map.spots(), random)) {
            double dx = step.x() + 0.5 - from.getX(), dz = step.z() + 0.5 - from.getZ();
            double distance = dx * dx + dz * dz;
            if (distance >= bestDistance) {
                best = step;
                bestDistance = distance;
            }
        }
        if (best == null) return;
        path = map.pathTo(best);
        pathIndex = 0;
        if (path.isEmpty()) path = null;
    }

    /** Thrown back by a creeper blowing up, shield up, before walking back. */
    private void thrown(Vec3 blast, Set<BlockPos> beds) {
        double dx = getX() - blast.x, dz = getZ() - blast.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > BLAST) return;
        double strength = 0.5 + (1 - distance / BLAST) * 1.2;
        double nx = distance < 1e-3 ? 1 : dx / distance, nz = distance < 1e-3 ? 0 : dz / distance;
        leaveBed(beds);
        anchor = position();
        vx = nx * strength;
        vz = nz * strength;
        vy = 0.35 + strength * 0.3;
        faceAt(blast, 180);
        start(Activity.FLY, 0);
    }

    /** In the air, after a blast: it lands, then walks back to where it stood. */
    private void fly(ClientLevel level) {
        vy = (vy - GRAVITY) * DRAG;
        vx *= 0.91;
        vz *= 0.91;
        double x = getX(), y = getY(), z = getZ();
        if (Footing.fits(blocks, x + vx, y, z, Footing.STANDING)) x += vx;
        else vx = 0;
        if (Footing.fits(blocks, x, y, z + vz, Footing.STANDING)) z += vz;
        else vz = 0;
        boolean landed = false;
        if (vy > 0) {
            if (Footing.fits(blocks, x, y + vy, z, Footing.STANDING)) y += vy;
            else vy = 0;
        } else {
            double floor = Footing.floorBelow(blocks, x, y, z);
            if (y + vy <= floor) {
                y = floor;
                landed = true;
            } else {
                y += vy;
            }
        }
        setPos(x, y, z);
        if (!landed) return;
        vx = vy = vz = 0;
        Vec3 back = anchor;
        anchor = null;
        start(Activity.GUARD, 0);
        // On guard long enough to walk back, monsters or not.
        calm = CALM_TICKS;
        if (back == null) return;
        BlockPos at = feet();
        Walkway.Survey map = Walkway.around((bx, by, bz) -> Footing.stand(blocks, bx, by, bz), at.getX(), at.getY(),
                at.getZ(), at.getX(), at.getZ(), REACH, BUDGET);
        BlockPos target = BlockPos.containing(back.x, back.y + 0.01, back.z);
        Walkway.Step home = map == null ? null : map.reached(target.getX(), target.getY(), target.getZ());
        if (home != null) {
            path = map.pathTo(home);
            pathIndex = 0;
            if (path.isEmpty()) path = null;
        }
    }

    /** Back to its quiet self: sword away, shield down, out of any bed. */
    private void calmDown(Set<BlockPos> beds) {
        leaveBed(beds);
        start(Activity.IDLE, 60);
        shieldTicks = 0;
        threat = null;
        fuses.clear();
        vx = vy = vz = 0;
        anchor = null;
        arm(false, false);
    }

    /** What it holds: a sword drawn, a shield up. Changed only when it changes. */
    private void arm(boolean sword, boolean shield) {
        if (sword != armed) {
            armed = sword;
            setItemSlot(EquipmentSlot.MAINHAND, sword ? new ItemStack(Items.IRON_SWORD) : held.copy());
        }
        if (shield != raised) {
            raised = shield;
            if (shield) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            // Using the item in the off hand: the game draws the shield raised.
            getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) (shield ? 3 : 0));
            if (!shield) setItemSlot(EquipmentSlot.OFFHAND, heldOff.copy());
        }
    }

    // --- Beds -----------------------------------------------------------------------------

    private void leaveBedIfIn() {
        if (activity != Activity.BED) return;
        clearSleepingPos();
        Vec3 exit = bedExit;
        if (exit != null) snap(exit.x, exit.y, exit.z);
        bedExit = null;
    }

    private void leaveBed(Set<BlockPos> beds) {
        leaveBedIfIn();
        if (bed != null) beds.remove(bed);
        bed = null;
        clearSleepingPos();
    }

    /** The head of a bed nobody really sleeps in. */
    private static boolean freeBed(BlockState state) {
        return state.getBlock() instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }

    // --- Afar -------------------------------------------------------------------------------

    /** Where the blocks are not loaded: standing or sitting where it is, standing at night. */
    private void afar(boolean night) {
        Puppets.walk(this, 0f, 1f, 1f);
        if (activity == Activity.BED) return;
        if (activity == Activity.WALK || activity == Activity.GUARD || activity == Activity.FLY
                || activity == Activity.FACE || activity == Activity.WARM || activity == Activity.FISH
                || activity == Activity.TALK) {
            calmDownQuietly();
        }
        if (night) {
            if (activity != Activity.IDLE) start(Activity.IDLE, AWAKE);
            glance(false);
            return;
        }
        if (--timer <= 0) {
            start(activity == Activity.SIT || random.nextBoolean() ? Activity.IDLE : Activity.SIT, 200 + random.nextInt(400));
        }
        glance(false);
    }

    private void calmDownQuietly() {
        start(Activity.IDLE, 60);
        shieldTicks = 0;
        threat = null;
        fuses.clear();
        arm(false, false);
    }

    // --- Looking around ---------------------------------------------------------------------

    /** Its head turning now and then: around, or at what it stands in front of. */
    private void glance(boolean focused) {
        if (!focused && --glance <= 0) {
            headYawGoal = (random.nextFloat() - 0.5f) * 140f;
            headPitchGoal = (random.nextFloat() - 0.3f) * 40f;
            glance = 30 + random.nextInt(70);
        }
        if (!focused) {
            headYaw = approach(headYaw, headYawGoal, 6);
            headPitch = approach(headPitch, headPitchGoal, 4);
        }
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    /** Turns towards a point: the body as far as {@code turn} degrees, the head the rest. */
    private void faceAt(Vec3 target, float turn) {
        double dx = target.x - getX(), dz = target.z - getZ();
        double dy = target.y - getEyeY();
        turnTo(yawTowards(dx, dz), turn);
        headYaw = approach(headYaw, wrap(yawTowards(dx, dz) - yBodyRot), 15);
        headPitch = approach(headPitch, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), 10);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    private void turnTo(float yaw, float max) {
        yBodyRot = yBodyRot + Math.max(-max, Math.min(max, wrap(yaw - yBodyRot)));
    }

    private static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float approach(float from, float to, float max) {
        return from + Math.max(-max, Math.min(max, to - from));
    }

    // --- Fire and water ---------------------------------------------------------------------

    /** The swirls of a potion keeping it safe: fire resistance in lava or fire, water breathing under water. */
    private void swirls(ClientLevel level) {
        int colour;
        if (Footing.inFire(blocks, getX(), getY(), getZ(), Footing.STANDING)) colour = FIRE_RESISTANCE;
        else if (Footing.underWater(blocks, getX(), getEyeY(), getZ())) colour = WATER_BREATHING;
        else return;
        for (int i = 0; i < 2; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 0.6;
            double y = getY() + random.nextDouble() * 1.8;
            double z = getZ() + (random.nextDouble() - 0.5) * 0.6;
            //? if >=1.20.5 {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | colour), x, y, z, 0, 0, 0);
            //?} else
            /*level.addParticle(ParticleTypes.ENTITY_EFFECT, x, y, z, (colour >> 16 & 255) / 255.0, (colour >> 8 & 255) / 255.0, (colour & 255) / 255.0);*/
        }
    }

    // --- Ground -----------------------------------------------------------------------------

    /** The block its feet are in. */
    private BlockPos feet() {
        return BlockPos.containing(getX(), getY() + 0.01, getZ());
    }

    /**
     * Keeps it on the ground where it is: it falls when nothing is under it any more, and
     * reappears on top when buried. Placed at once the first time.
     */
    private void settle(ClientLevel level, boolean now) {
        double height = switch (look()) {
            case SIT -> 1.2;
            case BED -> 0.5;
            default -> Footing.STANDING;
        };
        double x = getX(), y = getY(), z = getZ();
        double target;
        if (!Footing.fits(blocks, x, y, z, height)) target = Footing.freeAbove(blocks, x, y, z, height);
        else if (!Footing.supported(blocks, x, y, z)) target = Footing.floorBelow(blocks, x, y, z);
        else target = y;
        move(level, x, z, y, target, height, now);
        if (getY() == target) ashore(level);
    }

    /**
     * Sitting on its spot, or the shore near it when the spot is in water, which it goes back
     * to once it has room and something under it again.
     */
    private void sit(ClientLevel level) {
        if (!Footing.loaded(level, spot.x(), spot.z())) {
            placed = false;
            return;
        }
        boolean first = !placed;
        placed = true;
        // Its spot in water: on the shore instead.
        Vec3 land = shore;
        double x = land != null ? land.x : spot.x(), home = land != null ? land.y : spot.y();
        double z = land != null ? land.z : spot.z(), height = 1.2;
        double y = getY();
        if (x != getX() || z != getZ()) {
            fall = 0;
            snap(x, home, z);
            return;
        }
        double target;
        if (Footing.fits(blocks, x, home, z, height) && Footing.supported(blocks, x, home, z)) {
            target = home;
        } else if (!Footing.fits(blocks, x, y, z, height)) {
            target = Footing.freeAbove(blocks, x, y, z, height);
        } else if (!Footing.supported(blocks, x, y, z)) {
            target = Footing.floorBelow(blocks, x, y, z);
        } else {
            target = y;
        }
        move(level, x, z, y, target, height, first);
    }

    private void move(ClientLevel level, double x, double z, double y, double target, double height, boolean now) {
        if (target == y) {
            fall = 0;
            return;
        }
        if (now || !Footing.clear(blocks, x, y, target, z, height)) {
            // Nothing to see on the way: through blocks, it simply reappears.
            fall = 0;
            snap(x, target, z);
            return;
        }
        Motion step = Motion.step(y, fall, target);
        fall = step.fall();
        if (step.y() != y) {
            setPos(x, step.y(), z);
            standing = STAND_TICKS;
        }
    }

    /** One tick of movement towards where it rests: it falls like anyone, and climbs steadily. */
    record Motion(double y, double fall) {
        static Motion step(double y, double fall, double target) {
            if (Math.abs(target - y) < 1e-4) return new Motion(target, 0);
            if (target > y) return new Motion(Math.min(target, y + RISE), 0);
            double speed = Math.min(TERMINAL, (fall + GRAVITY) * DRAG);
            double next = y - speed;
            return next <= target ? new Motion(target, 0) : new Motion(next, speed);
        }
    }

    private void snap(double x, double y, double z) {
        setPos(x, y, z);
        setOldPosAndRot();
    }
}
