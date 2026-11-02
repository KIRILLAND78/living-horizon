package fr.clixmods.livinghorizon.track;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * What a player who logged off rode: a copy of their horse, boat or happy ghast, never added
 * to the level, which carries them about their spot. A horse walks where it has room for
 * itself and its rider, a boat keeps to the water it floats on, a happy ghast drifts through
 * open air; anything else (a minecart, a mount of another mod) stays where it was. Each
 * goes somewhere, waits a while, and goes again, within the same distance of the spot as a
 * player on foot. Where the blocks are not loaded it stays put.
 *
 * <p>That only near the camera, as for a player on foot ({@link RestingPuppet#NEAR_IN}).
 * Farther it plays one of the animations distant mobs play, in a loop around its spot
 * ({@link RestingLoop}): a horse walks the ground read around the spot, a boat the water,
 * a happy ghast the air at its height. Nothing is read nor decided while it plays.
 */
final class RestingMount {
    /**
     * How it gets about. An aircraft its rider left in the air stays there: {@code HOVER} on
     * its spot when it was still, {@code CIRCLE} round and round at its height when it was going,
     * as a plane cannot stop in the air.
     */
    enum Kind { GROUND, WATER, AIR, STILL, HOVER, CIRCLE }

    /** Blocks per tick an aircraft was going at least, as its rider left, to fly in circles rather than hover. */
    static final double CRUISE = 0.15;
    /** Blocks per tick a circling aircraft flies at, whatever it was going at. */
    private static final double CIRCLE_SLOWEST = 0.2, CIRCLE_FASTEST = 1.2;
    /** Blocks across half its circle, per block a tick it flies, and the narrowest and widest. */
    private static final double CIRCLE_RADIUS = 40, CIRCLE_NARROWEST = 16, CIRCLE_WIDEST = 48;

    /** Blocks per tick: a horse's walk, a boat rowed gently, a happy ghast's drift. */
    private static final double WALK = 0.13, ROW = 0.1, DRIFT = 0.06;
    /**
     * The widest it is taken to be when finding its way: a horse is wider than a door, but
     * players ride through gaps one block wide all the same.
     */
    private static final double MAX_HALF = 0.45;
    /** A seated rider's height above the seat. */
    private static final double RIDER = 1.5;
    /** Blocks from its spot a happy ghast drifts at most, and up or down. */
    private static final int AIR_REACH = 24, AIR_RISE = 4;
    /** Spots read at most each time it looks for where to go. */
    private static final int BUDGET = 6000;
    /** Blocks below and above its spot copied when it looks for where to go. */
    private static final int COPY_BELOW = 32, COPY_ABOVE = 48;
    /** Degrees it turns per tick. */
    private static final float TURN = 10f;
    //? if >=1.21.2 {
    private static final EntityType<?> BOAT = EntityType.OAK_BOAT;
    //?} else
    /*private static final EntityType<?> BOAT = EntityType.BOAT;*/
    /** The boat given to a swimmer: how deep it sits in the water, and its rider's feet under its own. */
    private static final double DRAFT = 0.1, SEAT = -0.41;

    final Entity entity;
    /** How it gets about: an aircraft whose save said it stood on the ground turns out to be in the air once the blocks are known. */
    Kind kind;
    /** The rider's feet from the mount's, in the mount's own frame. */
    private final Vec3 seat;
    private final float riderYaw;
    /** Where it stood when its rider logged off. */
    private final Vec3 home;
    private final float homeYaw;
    /** The body that must fit where it goes: its own, the rider's on top. */
    private final double half, height;
    private final Footing.Source blocks;
    private final Random random;
    private final long seed;
    private final ClientLevel level;
    /** Playing its loop, away from the camera. */
    private boolean far;
    private @Nullable RestingLoop loop;
    /** How deep a boat sits in the water, below its surface: as it sat when they logged off. */
    private double draft = Double.NaN;

    private float yaw;
    private int timer;
    private boolean placed;
    private double fall;
    private double moved;
    /** Ticks it has flown its circle, from its spot. */
    private int circling;
    /** Where it can go, being looked for: the blocks around copied a few sections a tick, then read off the game's thread. */
    private BlockCopy.@Nullable Builder copying;
    private @Nullable CompletableFuture<Optional<Walkway.Survey>> survey;
    private @Nullable List<Walkway.Step> path;
    private int pathIndex;
    private @Nullable Vec3 flight;

    private RestingMount(ClientLevel level, Entity entity, RestingPlayers.Mount mount, Vec3 rider, Random random, long seed) {
        this.entity = entity;
        this.level = level;
        this.seed = seed;
        this.kind = kindOf(entity);
        this.seat = new Vec3(mount.seatX(), mount.seatY(), mount.seatZ());
        this.riderYaw = mount.riderYaw();
        this.random = random;
        this.homeYaw = mount.yaw();
        this.home = rider.subtract(seatAt(mount.yaw()));
        double width = entity.getBbWidth() / 2;
        this.half = aloft() ? width : Math.min(width, MAX_HALF);
        this.height = Math.max(entity.getBbHeight(), seat.y + RIDER);
        this.blocks = Footing.live(level);
        reset();
    }

    /** The mount a spot keeps, ready to draw; null when there is none, or its type is gone. */
    static @Nullable RestingMount of(ClientLevel level, RestingPlayers.Spot spot, Random random) {
        RestingPlayers.Mount mount = spot.mount();
        Entity entity = mount == null ? null : copy(level, mount);
        if (entity == null) return null;
        long seed = spot.name().toLowerCase(java.util.Locale.ROOT).hashCode() * 0xBF58476D1CE4E5B9L;
        return new RestingMount(level, entity, mount, new Vec3(spot.x(), spot.y(), spot.z()), random, seed);
    }

    /**
     * Writes into its save how fast it was going, where the game kept less: a vehicle this client
     * only sees moved by the server, or a copy moved by the mod, has no motion of its own; its
     * rider's last pace ({@code going}) stands for it. A plane left in the air flies on in circles
     * at that pace ({@link Kind#CIRCLE}).
     */
    static void keepMotion(Entity vehicle, CompoundTag saved, Vec3 going) {
        double dx = vehicle.getX() - vehicle.xo, dy = vehicle.getY() - vehicle.yo, dz = vehicle.getZ() - vehicle.zo;
        // Its rider's pace, when that was faster: the last positions came to a stop as they left.
        if (going.horizontalDistanceSqr() > dx * dx + dz * dz) {
            dx = going.x;
            dy = 0;
            dz = going.z;
        }
        if (vehicle.getDeltaMovement().horizontalDistanceSqr() >= dx * dx + dz * dz) return;
        ListTag motion = new ListTag();
        motion.add(DoubleTag.valueOf(dx));
        motion.add(DoubleTag.valueOf(dy));
        motion.add(DoubleTag.valueOf(dz));
        saved.put("Motion", motion);
    }

    /** A copy of a kept mount, as it was saved; null when its type is gone or it does not read. */
    static @Nullable Entity copy(ClientLevel level, RestingPlayers.Mount mount) {
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(mount.type())).orElse(null);
            Entity entity = type == null ? null : Puppets.create(type, level);
            if (entity == null) return null;
            CompoundTag tag = TagParser.parseCompoundFully(mount.nbt());
            EntityNbt.load(entity, level, tag);
            applyData(entity, mount.data());
            if (entity instanceof LivingEntity living) {
                // A copy never ticks: a hit taken just before would leave it red for good.
                living.hurtTime = 0;
                living.hurtDuration = 0;
                living.deathTime = 0;
            }
            return entity;
        } catch (Exception e) {
            // Saved by a game or a mod that reads it differently: they rest on foot.
            return null;
        }
    }

    /**
     * A boat for a player who logged off in water with no shore near, on the surface at
     * {@code surface} above their spot, facing their way: rowed about the water as anyone's
     * boat. Null when the game cannot make one.
     */
    static @Nullable RestingMount boat(ClientLevel level, RestingPlayers.Spot spot, double surface, Random random) {
        Entity entity = Puppets.create(BOAT, level);
        if (entity == null) return null;
        RestingPlayers.Mount mount = new RestingPlayers.Mount(EntityType.getKey(BOAT).toString(), "{}", null,
                0, SEAT, 0, spot.yaw(), 0);
        long seed = spot.name().toLowerCase(java.util.Locale.ROOT).hashCode() * 0xBF58476D1CE4E5B9L;
        return new RestingMount(level, entity, mount, new Vec3(spot.x(), surface - DRAFT + SEAT, spot.z()), random, seed);
    }

    // --- What the server told this client of it ------------------------------------------

    /**
     * The flags, numbers and switches the server sent of it (a saddle, a chest, a horse's
     * flags), which its save leaves out on the client before 1.21.5: written {@code id:kind:value}
     * and joined by {@code ;}. The entity's own flags (on fire, glowing) are left out: the copy
     * would keep them for good.
     */
    static @Nullable String dataOf(Entity entity) {
        List<SynchedEntityData.DataValue<?>> values;
        try {
            values = entity.getEntityData().getNonDefaultValues();
        } catch (RuntimeException e) {
            return null;
        }
        if (values == null) return null;
        StringBuilder data = new StringBuilder();
        for (SynchedEntityData.DataValue<?> value : values) {
            if (value.id() == 0) continue;
            String kind;
            if (value.serializer() == EntityDataSerializers.BYTE) kind = "b";
            else if (value.serializer() == EntityDataSerializers.BOOLEAN) kind = "z";
            else if (value.serializer() == EntityDataSerializers.INT) kind = "i";
            else if (value.serializer() == EntityDataSerializers.FLOAT) kind = "f";
            else continue;
            if (!data.isEmpty()) data.append(';');
            data.append(value.id()).append(':').append(kind).append(':').append(value.value());
        }
        return data.isEmpty() ? null : data.toString();
    }

    /** Gives a copy what {@link #dataOf} kept; a value the type no longer has is left out. */
    static void applyData(Entity entity, @Nullable String data) {
        if (data == null || data.isEmpty()) return;
        for (String part : data.split(";")) {
            String[] fields = part.split(":", 3);
            if (fields.length != 3) continue;
            try {
                int id = Integer.parseInt(fields[0]);
                SynchedEntityData.DataValue<?> value = switch (fields[1]) {
                    case "b" -> SynchedEntityData.DataValue.create(
                            new EntityDataAccessor<>(id, EntityDataSerializers.BYTE), Byte.parseByte(fields[2]));
                    case "z" -> SynchedEntityData.DataValue.create(
                            new EntityDataAccessor<>(id, EntityDataSerializers.BOOLEAN), Boolean.parseBoolean(fields[2]));
                    case "i" -> SynchedEntityData.DataValue.create(
                            new EntityDataAccessor<>(id, EntityDataSerializers.INT), Integer.parseInt(fields[2]));
                    case "f" -> SynchedEntityData.DataValue.create(
                            new EntityDataAccessor<>(id, EntityDataSerializers.FLOAT), Float.parseFloat(fields[2]));
                    default -> null;
                };
                if (value != null) entity.getEntityData().assignValues(List.of(value));
            } catch (RuntimeException e) {
                // Another game version numbers them differently: this one stays as saved.
            }
        }
    }

    /** How a mount gets about, from what it is. */
    static Kind kindOf(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            // Boats float; minecarts and the rest stay where they are.
            if (MobMemory.boat(entity)) return Kind.WATER;
            // An aircraft left in the air (another mod's plane, steered): round and round where it
            // was if it was going, hovering there if it was still. A car on the ground stays put.
            if (!entity.onGround() && Vehicles.of(entity) == Vehicles.Kind.STEERED) {
                return entity.getDeltaMovement().horizontalDistance() >= CRUISE ? Kind.CIRCLE : Kind.HOVER;
            }
            return Kind.STILL;
        }
        if (living.isNoGravity() || EntityType.getKey(entity.getType()).getPath().equals("happy_ghast")) return Kind.AIR;
        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS) {
            return Kind.STILL;
        }
        return Kind.GROUND;
    }

    /** Back on its spot, at rest. */
    void reset() {
        stop();
        far = false;
        circling = 0;
        yaw = homeYaw;
        placed = false;
        fall = 0;
        face();
        entity.setPos(home.x, home.y, home.z);
        entity.setOldPosAndRot();
        timer = 60 + random.nextInt(200);
    }

    private void stop() {
        copying = null;
        survey = null;
        path = null;
        flight = null;
    }

    /** Where the rider sits now. */
    Vec3 rider() {
        return entity.position().add(seatAt(yaw));
    }

    /** The way the rider faces now. */
    float riderYaw() {
        return yaw + riderYaw;
    }

    /** Playing its loop, away from the camera, rather than going where it chooses. */
    boolean away() {
        return far;
    }

    /** On its way somewhere. */
    boolean moving() {
        return path != null || flight != null;
    }

    private Vec3 seatAt(float facing) {
        return seat.yRot((float) -Math.toRadians(facing));
    }

    /**
     * One tick: where it goes, or the ground under it.
     *
     * @param lively whether it goes anywhere; otherwise it stays on its spot
     * @param near   close enough to the camera to go where it chooses; farther, it plays its loop
     * @param roam   blocks from its spot it may go
     * @param work   sections of blocks all puppets may still copy this tick, shared
     */
    void tick(ClientLevel level, boolean lively, boolean near, int roam, int[] work) {
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
        }
        entity.tickCount++;
        moved = 0;
        if (kind == Kind.CIRCLE && lively) {
            // In the air, near or far: nothing to read, nothing in its way at its height.
            circle();
            animate();
            return;
        }
        if (lively && !near) {
            playLoop();
            animate();
            return;
        }
        if (far) {
            // Back near the camera: it goes where it chooses again, from where its loop left it.
            far = false;
            placed = false;
            stop();
            timer = 40 + random.nextInt(100);
        }
        if (!Footing.loaded(level, entity.blockPosition())) {
            // Nothing known of the blocks here: it waits where it is.
            placed = false;
            stop();
            animate();
            return;
        }
        boolean first = !placed;
        placed = true;
        if (first && kind == Kind.STILL && !(entity instanceof LivingEntity)
                && Vehicles.of(entity) == Vehicles.Kind.STEERED
                && !Footing.supported(blocks, entity.getX(), entity.getY(), entity.getZ(), half)) {
            // Its save was older than its flight (a copy made while it stood): nothing under it now.
            kind = entity.getDeltaMovement().horizontalDistance() >= CRUISE ? Kind.CIRCLE : Kind.HOVER;
        }
        if (!lively) {
            stop();
            settle(level, first);
            animate();
            return;
        }
        if (first) settle(level, true);
        if (kind == Kind.WATER && Double.isNaN(draft)) draft = measureDraft(level);
        switch (kind) {
            case GROUND, WATER -> roam(level, roam, work);
            case AIR -> drift(level, roam);
            case STILL, HOVER, CIRCLE -> {
            }
        }
        if (!moving()) settle(level, false);
        animate();
    }

    /** In the air for good: a happy ghast, an aircraft left hovering or circling. */
    private boolean aloft() {
        return kind == Kind.AIR || kind == Kind.HOVER || kind == Kind.CIRCLE;
    }

    /**
     * One tick of its circle, at the height it was left at: it flies on from its spot the way it
     * faced, at the pace it was going, and turns left or right round a point beside it.
     */
    private void circle() {
        double speed = Mth.clamp(entity.getDeltaMovement().horizontalDistance(), CIRCLE_SLOWEST, CIRCLE_FASTEST);
        double radius = Mth.clamp(speed * CIRCLE_RADIUS, CIRCLE_NARROWEST, CIRCLE_WIDEST);
        int turn = (seed & 1) == 0 ? 1 : -1;
        double facing = Math.toRadians(homeYaw);
        // Beside its spot, square to the way it faced: the circle starts on the spot, along that way.
        double cx = home.x + Math.cos(facing) * radius * turn, cz = home.z + Math.sin(facing) * radius * turn;
        double start = Math.atan2(home.z - cz, home.x - cx);
        circling++;
        double angle = start - turn * circling * speed / radius;
        double x = cx + Math.cos(angle) * radius, z = cz + Math.sin(angle) * radius;
        // Along the circle: the way it goes now.
        double tx = Math.sin(angle) * turn, tz = -Math.cos(angle) * turn;
        entity.setPos(x, home.y, z);
        yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
        moved = speed;
    }

    // --- Away from the camera -----------------------------------------------------------------

    /** One tick of its loop around its spot. */
    private void playLoop() {
        RestingLoop playing = loop();
        if (!far) {
            far = true;
            stop();
            playing.from(entity.getX(), entity.getY(), entity.getZ(), yaw);
        }
        moved = playing.tick((int) level.getGameTime(), kind == Kind.AIR || kind == Kind.WATER);
        entity.setPos(playing.x, playing.y, playing.z);
        yaw = playing.body;
    }

    /** Its loop: the animation that fits the ground, the water or the air around its spot. */
    private RestingLoop loop() {
        if (loop == null) {
            double pace = kind == Kind.WATER ? ROW : kind == Kind.AIR ? DRIFT : WALK;
            loop = new RestingLoop(seed, home.x, home.y, home.z, homeYaw, pace, this::plan);
        }
        return loop;
    }

    private CompletableFuture<MobPaths.Choice> plan() {
        return switch (kind) {
            case GROUND -> MobPaths.plan(level, home.x, home.y, home.z, entity.getBbWidth(), seed);
            case AIR -> CompletableFuture.completedFuture(MobPaths.free(home.x, home.y, home.z, seed));
            case WATER -> water();
            case STILL, HOVER, CIRCLE -> CompletableFuture.completedFuture(MobPaths.stand(home.x, home.y, home.z));
        };
    }

    /**
     * The water around its spot, read like the ground around a mob ({@link MobPaths}): every
     * column it can float in, from a copy of the blocks this client has, off the game's
     * thread; the others are read again later.
     */
    private CompletableFuture<MobPaths.Choice> water() {
        int bx = Mth.floor(home.x), by = Mth.floor(home.y), bz = Mth.floor(home.z), r = MobPaths.RADIUS;
        boolean complete = true;
        for (int cx = (bx - r) >> 4; cx <= (bx + r) >> 4; cx++) {
            for (int cz = (bz - r) >> 4; cz <= (bz + r) >> 4; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) complete = false;
            }
        }
        BlockCopy copy = BlockCopy.of(level, bx - r, by - 2, bz - r, bx + r, by + 3 + (int) Math.ceil(height), bz + r).copy(level);
        double width = entity.getBbWidth();
        boolean whole = complete;
        return GroundWork.run(() -> {
            double[][] ground = new double[MobPaths.SIZE][MobPaths.SIZE];
            for (int i = 0; i < MobPaths.SIZE; i++) {
                for (int j = 0; j < MobPaths.SIZE; j++) {
                    int cx = bx + i - r, cz = bz + j - r;
                    ground[i][j] = Double.NaN;
                    for (int dy : new int[]{0, -1, 1}) {
                        double surface = Footing.afloat(copy, cx, by + dy, cz, half, height);
                        if (!Double.isNaN(surface)) {
                            ground[i][j] = home.y;
                            break;
                        }
                    }
                }
            }
            return MobPaths.choose(new MobPaths.Ground(ground, home.x, home.y, home.z, whole), width, seed);
        });
    }

    /** Where it can be in these blocks: afloat for a boat, on the ground for the others. */
    private Walkway.Ground ground(Footing.Source source) {
        double sink = draft();
        return kind == Kind.WATER
                ? (x, y, z) -> Footing.afloat(source, x, y, z, half, height) - sink
                : (x, y, z) -> Footing.stand(source, x, y, z, half, height);
    }

    // --- On the ground or the water ---------------------------------------------------------

    /** Somewhere it can reach, then a rest, then somewhere else. */
    private void roam(ClientLevel level, int roam, int[] work) {
        if (path != null) {
            if (step(level)) {
                path = null;
                timer = 100 + random.nextInt(300);
            }
            return;
        }
        BlockCopy.Builder builder = copying;
        if (builder != null) {
            work[0] -= builder.copy(level, Math.max(0, work[0]));
            if (!builder.done()) return;
            copying = null;
            Walkway.Ground ground = ground(builder.build());
            BlockPos at = feet();
            BlockPos center = BlockPos.containing(home);
            int budget = Math.min(BUDGET, (int) (Math.PI * roam * roam * 1.6 + 200));
            survey = GroundWork.run(() -> Optional.ofNullable(Walkway.around(ground, at.getX(), at.getY(), at.getZ(),
                    center.getX(), center.getZ(), roam, budget)));
            return;
        }
        CompletableFuture<Optional<Walkway.Survey>> reading = survey;
        if (reading != null) {
            if (!reading.isDone()) return;
            survey = null;
            Walkway.Survey map = reading.isCompletedExceptionally() ? null : reading.join().orElse(null);
            if (map != null) choose(map);
            else timer = 200 + random.nextInt(200);
            return;
        }
        if (--timer > 0) return;
        BlockPos at = feet();
        Walkway.Ground here = ground(blocks);
        if (Double.isNaN(here.stand(at.getX(), at.getY(), at.getZ())) && Double.isNaN(here.stand(at.getX(), at.getY() + 1, at.getZ()))
                && Double.isNaN(here.stand(at.getX(), at.getY() - 1, at.getZ()))) {
            // Nowhere it can go from here (a boat on land): it stays, and looks again later.
            timer = 200 + random.nextInt(200);
            return;
        }
        BlockPos center = BlockPos.containing(home);
        copying = BlockCopy.of(level, center.getX() - roam - 1, center.getY() - COPY_BELOW, center.getZ() - roam - 1,
                center.getX() + roam + 1, center.getY() + COPY_ABOVE, center.getZ() + roam + 1);
    }

    /** A spot a few blocks away at least, and the way there. */
    private void choose(Walkway.Survey map) {
        BlockPos at = feet();
        List<Walkway.Step> spots = map.spots();
        for (int tries = 0; tries < 12 && !spots.isEmpty(); tries++) {
            Walkway.Step step = spots.get(random.nextInt(spots.size()));
            if (step.distanceSqr(at.getX(), at.getZ()) < 16) continue;
            List<Walkway.Step> way = map.pathTo(step);
            if (way.isEmpty()) continue;
            path = way;
            pathIndex = 0;
            return;
        }
        timer = 100 + random.nextInt(200);
    }

    /** One tick along the way; true once there, or when the way can no longer be taken. */
    private boolean step(ClientLevel level) {
        List<Walkway.Step> way = path;
        if (way == null || pathIndex >= way.size()) return true;
        Walkway.Step next = way.get(pathIndex);
        double speed = kind == Kind.WATER ? ROW : WALK;
        double tx = next.x() + 0.5, tz = next.z() + 0.5, ty = next.height();
        double dx = tx - entity.getX(), dz = tz - entity.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= speed) {
            double there = kind == Kind.WATER
                    ? Footing.afloat(blocks, next.x(), next.y(), next.z(), half, height) - draft()
                    : Footing.stand(blocks, next.x(), next.y(), next.z(), half, height);
            if (Double.isNaN(there)) return true;
            entity.setPos(tx, there, tz);
            pathIndex++;
            moved = distance;
        } else {
            double y = entity.getY();
            // Up a step at once, as with a jump; down only once over the edge.
            if (ty > y) y = Math.min(ty, y + 0.3);
            else if (ty < y && distance < 0.6) y = Math.max(ty, y - 0.4);
            entity.setPos(entity.getX() + dx / distance * speed, y, entity.getZ() + dz / distance * speed);
            moved = speed;
        }
        if (distance > 1e-3) turnTo(yawTowards(dx, dz));
        return pathIndex >= way.size();
    }

    private double draft() {
        return Double.isNaN(draft) ? 0 : draft;
    }

    /** How far below the water's surface the boat sits now; none when it is not on water. */
    private double measureDraft(ClientLevel level) {
        BlockPos at = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        for (BlockPos water : new BlockPos[]{at, at.above()}) {
            double surface = Footing.afloat(blocks, water.getX(), water.getY(), water.getZ(), half, height);
            if (!Double.isNaN(surface)) return Math.clamp(surface - entity.getY(), 0, 0.6);
        }
        return 0;
    }

    // --- In the air -------------------------------------------------------------------------

    /** A happy ghast: straight through open air to somewhere near its spot, then a rest. */
    private void drift(ClientLevel level, int roam) {
        Vec3 target = flight;
        if (target != null) {
            Vec3 at = entity.position();
            Vec3 to = target.subtract(at);
            double distance = to.length();
            Vec3 next = distance <= DRIFT ? target : at.add(to.scale(DRIFT / distance));
            if (!open(level, next)) {
                // Something was built in the way: it stops there.
                flight = null;
                timer = 100 + random.nextInt(200);
                return;
            }
            entity.setPos(next.x, next.y, next.z);
            moved = Math.min(distance, DRIFT);
            if (Math.abs(to.x) + Math.abs(to.z) > 1e-3) turnTo(yawTowards(to.x, to.z));
            if (distance <= DRIFT) {
                flight = null;
                timer = 160 + random.nextInt(400);
            }
            return;
        }
        if (--timer > 0) return;
        flight = flightFrom(level, Math.min(roam, AIR_REACH));
        if (flight == null) timer = 100 + random.nextInt(100);
    }

    /** Somewhere around its spot it can drift to in a straight line; null if no try finds one. */
    private @Nullable Vec3 flightFrom(ClientLevel level, int reach) {
        if (reach < 4) return null;
        Vec3 from = entity.position();
        for (int tries = 0; tries < 8; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 4 + random.nextDouble() * (reach - 4);
            Vec3 target = new Vec3(home.x + Math.cos(angle) * distance,
                    home.y + (random.nextDouble() * 2 - 1) * AIR_RISE, home.z + Math.sin(angle) * distance);
            if (target.distanceToSqr(from) < 16) continue;
            if (clearLine(level, from, target)) return target;
        }
        return null;
    }

    private boolean clearLine(ClientLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = (int) Math.ceil(length);
        for (int i = 1; i <= steps; i++) {
            if (!open(level, from.lerp(to, i / (double) steps))) return false;
        }
        return true;
    }

    /** Room there for it and its rider, in blocks this client has. */
    private boolean open(ClientLevel level, Vec3 at) {
        return Footing.loaded(level, at.x, at.z) && Footing.fits(blocks, at.x, at.y, at.z, half, height);
    }

    // --- Its place --------------------------------------------------------------------------

    /**
     * Keeps it where it can be: on top of what buries it, and, unless it flies, on what is
     * under it, falling like anyone when that is broken. Placed at once the first time.
     */
    private void settle(ClientLevel level, boolean now) {
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        double target;
        if (!Footing.fits(blocks, x, y, z, half, height)) target = Footing.freeAbove(blocks, x, y, z, half, height);
        else if (!aloft() && !Footing.supported(blocks, x, y, z, half)) target = Footing.floorBelow(blocks, x, y, z, half);
        else target = y;
        if (target == y) {
            fall = 0;
            return;
        }
        if (now || target > y) {
            fall = 0;
            entity.setPos(x, target, z);
            if (now) entity.setOldPosAndRot();
            return;
        }
        RestingPuppet.Motion step = RestingPuppet.Motion.step(y, fall, target);
        fall = step.fall();
        entity.setPos(x, step.y(), z);
    }

    private void animate() {
        face();
        if (entity instanceof LivingEntity living) {
            Puppets.walk(living, (float) Math.min(moved * 4.0, 1.0), 0.4f, living.isBaby() ? 3f : 1f);
        }
    }

    private void face() {
        entity.setYRot(yaw);
        entity.setXRot(0f);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yHeadRot = yaw;
        }
    }

    private BlockPos feet() {
        return BlockPos.containing(entity.getX(), entity.getY() + 0.01, entity.getZ());
    }

    private void turnTo(float target) {
        float d = (target - yaw) % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        yaw += Math.max(-TURN, Math.min(TURN, d));
    }

    private static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** What it does, for the debug tools. */
    String state() {
        if (far && loop != null) return kind.name().toLowerCase(java.util.Locale.ROOT) + " mount away: " + loop.state();
        String doing = path != null || flight != null ? "on its way" : survey != null || copying != null ? "looking where to go" : "resting";
        return kind.name().toLowerCase(java.util.Locale.ROOT) + " mount " + doing;
    }
}
