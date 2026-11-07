package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 15: now and then, a herd far off - three cows or more, sheep, pigs, horses, goats,
 * llamas grazing together - stops all at once, and every one of them turns its head, then its
 * body, towards the player, and keeps staring. Once the player has seen them do it, they stare
 * on ten seconds and go back to their lives; seen through a spyglass, they go back as soon as
 * it is lowered. Never seen, they give up after a while, or when the player comes near.
 *
 * <p>The herd is among the mobs this client remembers and draws far away ({@link MobMemory}):
 * their copies stand still while held, and {@link MobMemory}'s tick asks {@link #turn} before
 * playing their loop, which goes on from there afterwards. The real mobs the server sends are
 * never touched. A held copy is drawn as a model, never as a flat picture, which would not
 * show its head turned apart from its body.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Herd implements Egg {
    /** The kinds that live in herds. */
    private static final Set<String> KINDS = Set.of("minecraft:cow", "minecraft:mooshroom", "minecraft:sheep",
            "minecraft:pig", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:goat",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:camel");
    /** How far off the herd may be, in blocks across; how near each other its animals stand, and how much higher. */
    private static final double MIN = 60, MAX = 250, TOGETHER = 16, ABOVE = 8;
    /** Blocks across under which it is over: the player came to see. */
    private static final double NEAR = 40;
    /** Animals that make a herd, at least; held at once, at most. */
    private static final int LEAST = 3, MOST = 12;
    /** Degrees a tick a head turns, and a body once it follows; degrees a head turns from its body, at most. */
    private static final float HEAD_TURN = 15f, BODY_TURN = 9f, NECK = 50f;
    /** Ticks the heads turn alone before the bodies follow. */
    private static final int BODY_AFTER = 8;
    /** Ticks they hold it unseen, at least and at most; ticks they stare on once seen. */
    private static final int HOLD_MIN = 600, HOLD_MAX = 800, STARE = 200;
    /** Ticks a search may take; idle ticks before it may come by chance, and its odds per tick then. */
    private static final int SEARCH = 400, IDLE = 6000, CHANCE = 48000;

    /**
     * The copies held now, read by {@link MobMemory}'s tick on the game's thread, and the
     * player's eye they turn to: one herd at a time, so kept here rather than on the egg.
     */
    private static final Set<Entity> HELD = Collections.newSetFromMap(new IdentityHashMap<>());
    private static double eyeX, eyeY, eyeZ;
    /** The heads have turned alone long enough: the bodies follow. */
    private static boolean bodies;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private List<MobMemory.Remembered> held = List.of();
    /** The middle of the herd. */
    private double x, y, z;
    private int age, hold, idle, searchAge;
    /** The tick they were first seen at, -1 until then; whether through a spyglass. */
    private int seenAt = -1;
    private boolean seenScoping;
    /** For the game tests: stands in for the player's spyglass while set. */
    private @Nullable Boolean scopingStandIn;

    Herd(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 15;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The middle of the herd, while it is held. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** The copies held now, for the game tests. */
    public List<LivingEntity> held() {
        List<LivingEntity> now = new ArrayList<>();
        for (Entity entity : HELD) if (entity instanceof LivingEntity living) now.add(living);
        return now;
    }

    /** Held by this easter egg: drawn as a model, never as a picture. */
    public static boolean holds(Entity puppet) {
        return !HELD.isEmpty() && HELD.contains(puppet);
    }

    /**
     * One tick of a remembered mob's copy, if it is held: it stands where it is and turns to the
     * player, head first, then body. False, and nothing done, for any other.
     */
    public static boolean turn(LivingEntity puppet) {
        if (!holds(puppet)) return false;
        puppet.setOldPosAndRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        double dx = eyeX - puppet.getX(), dz = eyeZ - puppet.getZ();
        float to = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float body = bodies ? Mth.approachDegrees(puppet.yBodyRot, to, BODY_TURN) : puppet.yBodyRot;
        float head = Mth.approachDegrees(puppet.yHeadRot, to, HEAD_TURN);
        // A neck only turns so far: the rest waits for the body.
        float neck = Mth.wrapDegrees(head - body);
        if (Math.abs(neck) > NECK) head = body + Math.copySign(NECK, neck);
        float pitch = (float) -(Mth.atan2(eyeY - puppet.getEyeY(), Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
        puppet.setYRot(body);
        puppet.yBodyRot = body;
        puppet.yHeadRot = head;
        // Its head up from the grass, towards the player.
        puppet.setXRot(Mth.lerp(0.3f, puppet.getXRot(), pitch));
        // Legs coming to rest.
        Puppets.walk(puppet, 0f, 0.4f, puppet.isBaby() ? 3f : 1f);
        puppet.tickCount++;
        return true;
    }

    /**
     * Looks for a herd among the remembered mobs drawn far away, in sight, and holds it once
     * the sight is read: by chance, or because the player asked ({@code called}: the nearest
     * herd, in sight if one is, any otherwise). False if the easter eggs are off or no herd is
     * drawn around.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog, nor than mobs are drawn.
        double far = Math.min(MAX, Sight.reach());
        int limit = FarConfig.get().mobMaxDistance;
        if (limit > 0) far = Math.min(far, limit);
        MobMemory memory = FarPlayerTracker.get().mobs();
        List<MobMemory.Remembered> candidates = new ArrayList<>();
        for (MobMemory.Remembered mob : memory.shown()) {
            if (!KINDS.contains(mob.type()) || memory.handingOver(mob)) continue;
            if (!(mob.puppet() instanceof LivingEntity puppet) || puppet.isPassenger() || puppet.isVehicle()) continue;
            double across = Math.hypot(puppet.getX() - self.getX(), puppet.getZ() - self.getZ());
            if (across >= MIN && across <= far) candidates.add(mob);
        }
        if (candidates.size() < LEAST) {
            if (called) EggLog.nowhere(15);
            return false;
        }
        // Their heads, in sight of the player's eye.
        double[] xs = new double[candidates.size()], ys = new double[xs.length], zs = new double[xs.length];
        for (int i = 0; i < xs.length; i++) {
            Entity puppet = candidates.get(i).puppet();
            xs[i] = puppet.getX();
            ys[i] = puppet.getY() + puppet.getBbHeight() * 0.8;
            zs[i] = puppet.getZ();
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Sight.of(current, self, self.getEyePosition(), xs, ys, zs).thenAcceptAsync(seen -> {
            if (generation.get() != mine) return;
            searching = false;
            List<MobMemory.Remembered> group = group(candidates, seen, self);
            if (group == null && called) group = group(candidates, null, self);
            if (group == null) {
                if (called) EggLog.nowhere(15);
                return;
            }
            begin(group, self);
        }, Minecraft.getInstance());
        return true;
    }

    /**
     * The nearest herd: animals of one kind, {@link #LEAST} at least, standing within
     * {@link #TOGETHER} blocks of the first of them, each in sight ({@code seen}; null: any).
     * The nearest of its first {@link #MOST}. Null if there is none.
     */
    private static @Nullable List<MobMemory.Remembered> group(List<MobMemory.Remembered> candidates, boolean @Nullable [] seen,
                                                               LocalPlayer self) {
        List<MobMemory.Remembered> pool = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if ((seen == null || (i < seen.length && seen[i])) && candidates.get(i).puppet() != null) pool.add(candidates.get(i));
        }
        pool.sort(Comparator.comparingDouble(mob -> mob.puppet().distanceToSqr(self)));
        for (MobMemory.Remembered first : pool) {
            Entity lead = first.puppet();
            List<MobMemory.Remembered> group = new ArrayList<>();
            for (MobMemory.Remembered mob : pool) {
                Entity puppet = mob.puppet();
                if (!mob.type().equals(first.type()) || puppet == null) continue;
                if (Math.hypot(puppet.getX() - lead.getX(), puppet.getZ() - lead.getZ()) <= TOGETHER
                        && Math.abs(puppet.getY() - lead.getY()) <= ABOVE) {
                    group.add(mob);
                }
            }
            if (group.size() < LEAST) continue;
            group.sort(Comparator.comparingDouble(mob -> mob.puppet().distanceToSqr(lead)));
            return group.size() > MOST ? new ArrayList<>(group.subList(0, MOST)) : group;
        }
        return null;
    }

    private void begin(List<MobMemory.Remembered> group, LocalPlayer self) {
        held = List.copyOf(group);
        refresh();
        if (HELD.size() < LEAST) {
            clear();
            if (called) EggLog.nowhere(15);
            return;
        }
        if (!called) calendar.shown(15);
        look(self);
        bodies = false;
        age = 0;
        hold = HOLD_MIN + random.nextInt(HOLD_MAX - HOLD_MIN + 1);
        seenAt = -1;
        seenScoping = false;
        active = true;
        EggLog.came(15, x, y, z, self, called);
    }

    /** The copies of the herd as they are now (a copy is made again when it leaves the list drawn), and their middle. */
    private void refresh() {
        HELD.clear();
        double sx = 0, sy = 0, sz = 0;
        for (MobMemory.Remembered mob : held) {
            Entity puppet = mob.puppet();
            if (puppet == null) continue;
            HELD.add(puppet);
            sx += puppet.getX();
            sy += puppet.getY();
            sz += puppet.getZ();
        }
        int count = HELD.size();
        if (count == 0) return;
        x = sx / count;
        y = sy / count;
        z = sz / count;
    }

    private static void look(LocalPlayer self) {
        Vec3 eye = self.getEyePosition();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare: about once in forty minutes, where a herd is drawn around.
            if (++idle > IDLE && quiet && random.nextInt(CHANCE) == 0) {
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
        refresh();
        if (HELD.isEmpty()) {
            clear();
            return;
        }
        look(self);
        bodies = age > BODY_AFTER;
        boolean scoping = scopingStandIn != null ? scopingStandIn : self.isScoping();
        if (Herobrine.lookedAt(current, self, new Vec3(x, y + 1, z))) {
            if (seenAt < 0) seenAt = age;
            seenScoping |= scoping;
        }
        // Seen, they stare on a while, or until the spyglass they were seen through is lowered;
        // never seen, until their time is up; and never once the player comes to see.
        boolean stared = seenAt >= 0 && (age - seenAt > STARE || (seenScoping && !scoping));
        if (stared || (seenAt < 0 && age > hold) || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR) {
            clear();
        }
    }

    /**
     * For the game tests, which cannot always hold a spyglass up: true or false stands in for
     * whether the player looks through one; null reads the player again.
     */
    public void standIn(@Nullable Boolean scoping) {
        scopingStandIn = scoping;
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
        held = List.of();
        HELD.clear();
    }

    /** Nothing of its own to draw: the herd is the game's own mobs. The beam over it, when called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (active && called) CalledBeam.submit(pose, camera, collector, x, y + 2, z);
    }
}
