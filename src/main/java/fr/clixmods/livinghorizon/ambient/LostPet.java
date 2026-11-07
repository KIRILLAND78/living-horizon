package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 16: some evening, a dog or a cat the player lost - one of their own, which this
 * client remembers somewhere and has not seen for a long while - sits far off on the ground,
 * in sight, and looks at them. Looked at a moment, or once they come nearer, it gets up and
 * runs off, and is gone as soon as something hides it; gone too at dawn, or after a few
 * minutes. Only on this screen: a memory of it, while the pet itself stays wherever it is
 * remembered.
 *
 * <p>The pet is a tamed wolf or cat among the remembered mobs ({@link MobMemory}) whose saved
 * data names the player as its owner. Its copy is made from that data, as the remembered
 * mobs' copies are, so it keeps its coat and collar, and it is drawn by the game's renderer
 * ({@link #figures}). Its spot is read in the loaded chunks or the far world ({@link Columns}):
 * dry, flat ground, in sight ({@link Sight}). A player who never had a pet never sees it
 * by chance; called, it is a wolf with a red collar.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class LostPet implements Egg {
    /** The kinds of pets that sit where they are told. */
    private static final Set<String> PETS = Set.of("minecraft:wolf", "minecraft:cat");
    private static final String WOLF = "minecraft:wolf";
    /** Milliseconds a pet must have gone unseen to be lost: a day of the game. */
    private static final long LOST = 20 * 60 * 1000L;
    /** How far off it may come, in blocks across; blocks across under which it is gone. */
    private static final double MIN = 60, MAX = 200, NEAR = 12;
    /** Blocks across within which it runs off; blocks per tick it runs. */
    private static final double FLEE_NEAR = 48, RUN = 0.3;
    /** Ticks looked at before it runs off; ticks it runs at most. */
    private static final int SEEN = 40, FLEE_LIFE = 120;
    /** Degrees a tick its head follows the player, and its body when it shuffles round; degrees its head turns from its body, at most. */
    private static final float HEAD_TURN = 8f, BODY_TURN = 3f, NECK = 50f;
    /** Spots tried; ticks a search may take; ticks it stays at most (three minutes). */
    private static final int SPOTS = 40, SEARCH = 400, LIFE = 3600;
    /** Idle ticks before it may come by chance, and its odds per tick of night then: about once in an hour of it. */
    private static final int IDLE = 6000, CHANCE = 72000;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private @Nullable LivingEntity figure;
    private double x, y, z;
    private int age, idle, searchAge;
    /** Ticks looked at; ticks since it ran off, -1 until then; the way it runs. */
    private int seen, fleeing = -1;
    private double fleeX, fleeZ;

    LostPet(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 16;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** The pet, sitting where it came, while it is there. */
    @Override
    public List<? extends Entity> figures() {
        LivingEntity pet = figure;
        return active && pet != null ? List.of(pet) : List.of();
    }

    /**
     * Makes a copy of one of the player's lost pets and looks for a spot in sight far off for it:
     * by chance, a pet unseen for a day of the game; called ({@code called}), any of theirs, or a
     * wolf with a red collar when they have none. False if the easter eggs are off, or by chance
     * when they have no lost pet.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(16);
            return false;
        }
        LivingEntity pet = pet(current, self, called);
        if (pet == null) {
            if (called) EggLog.nowhere(16);
            return false;
        }
        // Each spot, a block off it on four sides: flat ground to sit on.
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int per = around.length;
        double[] xs = new double[SPOTS * per], zs = new double[xs.length];
        for (int s = 0; s < SPOTS; s++) {
            double angle = random.nextDouble() * Math.PI * 2, across = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() + Math.cos(angle) * across) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.sin(angle) * across) + 0.5;
            for (int k = 0; k < per; k++) {
                xs[s * per + k] = cx + around[k][0];
                zs[s * per + k] = cz + around[k][1];
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Minecraft game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Columns.Column> flat = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[s * per];
                boolean ok = spot.dry();
                for (int k = 1; k < per && ok; k++) {
                    Columns.Column column = columns[s * per + k];
                    ok = column.dry() && Math.abs(column.y() - spot.y()) <= 1;
                }
                if (ok) flat.add(spot);
            }
            double[] hx = new double[flat.size()], hy = new double[flat.size()], hz = new double[flat.size()];
            for (int i = 0; i < flat.size(); i++) {
                hx[i] = flat.get(i).x();
                hy[i] = flat.get(i).y() + 0.6;
                hz[i] = flat.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Columns.Column> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(flat.get(i));
                Columns.Column spot = Sight.any(clear, random);
                if (spot == null) {
                    if (called) EggLog.nowhere(16);
                    return;
                }
                begin(pet, spot, self);
            }, game);
        }, game);
        return true;
    }

    /**
     * A sitting copy of one of the player's pets the client remembers: by chance, one unseen for
     * {@link #LOST}; called, one of those first, then any of theirs, then a wolf with a red
     * collar. Null when there is none to be had.
     */
    private @Nullable LivingEntity pet(ClientLevel level, LocalPlayer self, boolean called) {
        UUID player = self.getUUID();
        List<CompoundTag> lost = new ArrayList<>(), theirs = new ArrayList<>();
        List<String> lostTypes = new ArrayList<>(), theirTypes = new ArrayList<>();
        long now = System.currentTimeMillis();
        String dimension = level.dimension().identifier().toString();
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered(dimension)) {
            if (!PETS.contains(mob.type())) continue;
            CompoundTag tag = ownedBy(mob.nbt(), player);
            if (tag == null) continue;
            boolean gone = now - mob.seenAt() > LOST;
            (gone ? lost : theirs).add(tag);
            (gone ? lostTypes : theirTypes).add(mob.type());
        }
        if (!lost.isEmpty()) {
            int i = random.nextInt(lost.size());
            return copy(level, lostTypes.get(i), lost.get(i));
        }
        if (!called) return null;
        if (!theirs.isEmpty()) {
            int i = random.nextInt(theirs.size());
            return copy(level, theirTypes.get(i), theirs.get(i));
        }
        // None of theirs: a dog, tamed, a red collar on.
        CompoundTag dog = new CompoundTag();
        dog.putByte("CollarColor", (byte) DyeColor.RED.getId());
        dog.putIntArray("Owner", UUIDUtil.uuidToIntArray(player));
        return copy(level, WOLF, dog);
    }

    /**
     * A pet's saved data, if it names {@code player} as its owner: the game saves the owner as
     * four numbers under {@code Owner}. Null for anyone else's, or a stray.
     */
    private static @Nullable CompoundTag ownedBy(@Nullable String nbt, UUID player) {
        if (nbt == null || !nbt.contains("Owner")) return null;
        try {
            CompoundTag tag = TagParser.parseCompoundFully(nbt);
            if (!(tag.get("Owner") instanceof IntArrayTag owner) || owner.getAsIntArray().length != 4) return null;
            return player.equals(UUIDUtil.uuidFromIntArray(owner.getAsIntArray())) ? tag : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** A copy of the pet from its saved data, with no name and an id of its own, sitting. */
    private static @Nullable LivingEntity copy(ClientLevel level, String type, CompoundTag tag) {
        EntityType<?> kind = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(type)).orElse(null);
        if (kind == null || !(Puppets.create(kind, level) instanceof TamableAnimal pet)) return null;
        try {
            EntityNbt.load(pet, level, tag);
        } catch (RuntimeException e) {
            return null;
        }
        pet.setUUID(UUID.randomUUID());
        pet.setCustomName(null);
        pet.hurtTime = 0;
        pet.hurtDuration = 0;
        pet.deathTime = 0;
        pet.setOrderedToSit(true);
        pet.setInSittingPose(true);
        return pet;
    }

    private void begin(LivingEntity pet, Columns.Column spot, LocalPlayer self) {
        if (!called) calendar.shown(16);
        seen = 0;
        fleeing = -1;
        x = spot.x();
        y = spot.y();
        z = spot.z();
        float yaw = toward(self, x, z);
        pet.setPos(x, y, z);
        pet.setYRot(yaw);
        pet.yBodyRot = pet.yBodyRotO = pet.yHeadRot = pet.yHeadRotO = yaw;
        pet.setOldPosAndRot();
        figure = pet;
        age = 0;
        active = true;
        EggLog.came(16, x, y, z, self, called);
    }

    /** The yaw that faces the player from {@code (x, z)}. */
    private static float toward(LocalPlayer self, double x, double z) {
        return (float) (Mth.atan2(self.getZ() - z, self.getX() - x) * Mth.RAD_TO_DEG) - 90f;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, at dusk or at night, and only for a player who lost a pet.
            if (++idle > IDLE && quiet && !current.isBrightOutside() && random.nextInt(CHANCE) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        LivingEntity pet = figure;
        double across = Math.hypot(self.getX() - x, self.getZ() - z);
        if (pet == null || ++age > LIFE || across < NEAR || !Columns.drawn(current, self, x, z)
                || (!called && current.isBrightOutside())) {
            clear();
            return;
        }
        // Looked at a moment, or the player coming nearer: it gets up and runs off.
        if (fleeing < 0 && (across < FLEE_NEAR || (Herobrine.lookedAt(current, self, new Vec3(x, y + 0.6, z)) && ++seen >= SEEN))) {
            fleeing = 0;
            double away = Math.atan2(x - self.getX(), z - self.getZ()) + (random.nextDouble() - 0.5) * 1.0;
            fleeX = Math.sin(away);
            fleeZ = Math.cos(away);
            if (pet instanceof TamableAnimal tame) {
                tame.setOrderedToSit(false);
                tame.setInSittingPose(false);
            }
        }
        if (fleeing >= 0) {
            flee(current, self, pet);
            return;
        }
        // Sitting where it is, its eyes on the player; turning round on the spot when they walk round it.
        pet.setOldPosAndRot();
        pet.yBodyRotO = pet.yBodyRot;
        pet.yHeadRotO = pet.yHeadRot;
        float to = toward(self, x, z);
        float body = pet.yBodyRot;
        if (Math.abs(Mth.wrapDegrees(to - body)) > NECK) body = Mth.approachDegrees(body, to, BODY_TURN);
        float head = Mth.approachDegrees(pet.yHeadRot, to, HEAD_TURN);
        float neck = Mth.wrapDegrees(head - body);
        if (Math.abs(neck) > NECK) head = body + Math.copySign(NECK, neck);
        pet.setPos(x, y, z);
        pet.setYRot(body);
        pet.yBodyRot = body;
        pet.yHeadRot = head;
        Puppets.walk(pet, 0f, 0.4f, pet.isBaby() ? 3f : 1f);
        pet.tickCount++;
    }

    /** Running off, away from the player along the ground; gone once hidden, or after a while. */
    private void flee(ClientLevel current, LocalPlayer self, LivingEntity pet) {
        pet.setOldPosAndRot();
        pet.yBodyRotO = pet.yBodyRot;
        pet.yHeadRotO = pet.yHeadRot;
        float yaw = (float) Math.toDegrees(Math.atan2(-fleeX, fleeZ));
        x += fleeX * RUN;
        z += fleeZ * RUN;
        if (Columns.loaded(current, x, z)) {
            y = current.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        }
        pet.setPos(x, y, z);
        pet.setYRot(yaw);
        pet.yBodyRot = yaw;
        pet.yHeadRot = yaw;
        Puppets.walk(pet, 1f, 0.4f, pet.isBaby() ? 3f : 1f);
        pet.tickCount++;
        boolean hidden = Columns.loaded(current, x, z)
                && !Ghosts.inSight(current, self, self.getEyePosition(), x, y + 0.6, z);
        if (++fleeing > FLEE_LIFE || (fleeing > 10 && hidden)) clear();
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
        figure = null;
    }

    /** Nothing of its own to draw: the pet is the game's own. The beam over it, when called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (active && called) CalledBeam.submit(pose, camera, collector, x, y + 1, z);
    }
}
