package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
//? if >=1.20.5 {
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.component.FireworkExplosion;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
*///?}

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 21: on a few real dates - New Year's Eve and Day, the 14th of July, the 17th of
 * May (the game's birthday) - once in the night, fireworks go up over every village around
 * for the best part of a minute, in the colours of the day. Only on this screen.
 *
 * <p>Its dates are its own: the {@link EggCalendar} does not pace it ({@link #paced}). The
 * villages are all those {@link GhostVillager#villages} knows, each its ground read in the
 * loaded chunks or the far world ({@link Columns}), each sending up rockets of its own. The rockets' trails, their bursts and their booms are the
 * game's own: its firework particles and sounds, as a rocket of the game makes them.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Fireworks implements Egg {
    /** Ticks rockets go up for; the last of them, a quicker volley; ticks after it everything has fallen. */
    private static final int SHOW = 1000, FINALE = 140, AFTER = 200;
    /** How far off the villages may be, in blocks across; blocks in front of the player when called without one. */
    private static final double MAX = 400, AHEAD = 150;
    /** Blocks from the village's middle rockets go up from; marks nearer than which are one village. */
    private static final double SPREAD = 10, SAME_VILLAGE = 32;
    /** A rocket's speed as it leaves, in blocks per tick, and the ticks it climbs: at least and at most. */
    private static final double SPEED_MIN = 1.6, SPEED_MAX = 2.2;
    private static final int CLIMB_MIN = 30, CLIMB_MAX = 40;
    /** Blocks over the ground the bursts reach at most, for the far plane. */
    private static final double HIGH = 80;
    /** Ticks a search may take. */
    private static final int SEARCH = 200;

    /** The colours of each date, and of any other day for a show called. */
    private enum Palette {
        NEW_YEAR(0xFFC850, 0xE6E6FF, 0xFF5050, 0x50C8FF, 0xC878FF),
        JULY_14(0xED2939, 0xFFFFFF, 0x3C6EFF),
        BIRTHDAY(0x5AC83C, 0x46E6E6, 0xFFD24A, 0xFFFFFF),
        ANY();

        final int[] colours;

        Palette(int... colours) {
            this.colours = colours;
        }

        /** The palette of {@code date}: null on any day without fireworks. */
        static @Nullable Palette of(LocalDate date) {
            int month = date.getMonthValue(), day = date.getDayOfMonth();
            if ((month == 12 && day == 31) || (month == 1 && day == 1)) return NEW_YEAR;
            if (month == 7 && day == 14) return JULY_14;
            if (month == 5 && day == 17) return BIRTHDAY;
            return null;
        }
    }

    /** A rocket on its way up. */
    private record Rocket(int launched, double x, double y, double z, double driftX, double driftZ, double speed, int climb) {
        /** Where it is {@code t} ticks after it left: slower and slower. */
        Vec3 at(double t) {
            return new Vec3(x + driftX * t, y + speed * t * (1 - 0.35 * t / climb), z + driftZ * t);
        }
    }

    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private final List<Rocket> rockets = new ArrayList<>();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    /** The first village, nearest the player; all of them, each its ground, and the tick it sends its next rocket. */
    private double x, y, z;
    private final List<Vec3> sites = new ArrayList<>();
    private int[] nextAt = new int[0];
    private Palette palette = Palette.ANY;
    /** The night it came last, counted as the calendar counts days: once a night. */
    private int played = Integer.MIN_VALUE;
    private final EggCalendar calendar;
    private int age, idle, searchAge, bursts;

    Fireworks(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 21;
    }

    /** Its dates are its own. */
    @Override
    public boolean paced() {
        return false;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The middle of the village it goes up over, on the ground, while it is there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** For the game tests: the villages it goes up over. */
    public int villages() {
        return active ? sites.size() : 0;
    }

    /** For the game tests: rockets burst so far in this show. */
    public int bursts() {
        return active ? bursts : 0;
    }

    /**
     * Looks for the villages it goes up over and starts once their ground is read: by chance, on
     * its dates, over every village around; or because the player asked ({@code called}: at
     * once, whatever the date and the hour, over every village around, or else in front of
     * them; called again, it starts over). False if the easter eggs are off or, by chance, no
     * village is around.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        List<Vec3> villages = new ArrayList<>();
        for (Vec3 mark : GhostVillager.villages(current, self, far)) {
            if (Math.hypot(mark.x - self.getX(), mark.z - self.getZ()) > far) continue;
            // A bell and the villagers round it are one village.
            boolean known = false;
            for (Vec3 other : villages) known |= Math.hypot(other.x - mark.x, other.z - mark.z) < SAME_VILLAGE;
            if (!known) villages.add(mark);
        }
        if (villages.isEmpty()) {
            if (!called) return false;
            double ahead = Math.max(16, Math.min(AHEAD, far - 16)), yaw = Math.toRadians(self.getYRot());
            villages.add(new Vec3(self.getX() - Math.sin(yaw) * ahead, self.getY(), self.getZ() + Math.cos(yaw) * ahead));
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        double feet = self.getY();
        double[] xs = new double[villages.size()], zs = new double[villages.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = villages.get(i).x;
            zs[i] = villages.get(i).z;
        }
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            searching = false;
            List<Vec3> found = new ArrayList<>();
            for (Columns.Column ground : columns) {
                // Called, the ground of a spot nothing is known of is the player's.
                if (ground.known() || called) found.add(new Vec3(ground.x(), ground.known() ? ground.y() : feet, ground.z()));
            }
            if (found.isEmpty()) return;
            begin(found);
            EggLog.came(21, x, y, z, self, called);
        }, game);
        return true;
    }

    private void begin(List<Vec3> villages) {
        Palette today = Palette.of(LocalDate.now());
        palette = today != null ? today : Palette.ANY;
        if (!called) played = calendar.days();
        sites.clear();
        sites.addAll(villages);
        x = villages.get(0).x;
        y = villages.get(0).y;
        z = villages.get(0).z;
        nextAt = new int[sites.size()];
        // Not all on the same tick: each village starts a moment after the last.
        for (int i = 0; i < nextAt.length; i++) nextAt[i] = 10 + random.nextInt(30);
        rockets.clear();
        age = bursts = 0;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // On its dates only, at night, soon after dusk most often: once that night.
            if (quiet && !current.isBrightOutside() && ++idle > 600 && random.nextInt(2400) == 0) {
                idle = 0;
                if (played != calendar.days() && Palette.of(LocalDate.now()) != null) summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        boolean drawn = false;
        for (Vec3 site : sites) drawn |= Columns.drawn(current, self, site.x, site.z);
        if (!drawn || age > SHOW + AFTER) {
            clear();
            return;
        }
        // Rockets from each village until the show ends, faster at its end; none once dawn has come, unless called.
        boolean going = age < SHOW && (called || !current.isBrightOutside());
        for (int s = 0; going && s < sites.size(); s++) {
            if (age < nextAt[s]) continue;
            int salvo = random.nextInt(6) == 0 ? 2 + random.nextInt(2) : 1;
            for (int i = 0; i < salvo; i++) launch(current, sites.get(s), i * 3);
            nextAt[s] = age + (age > SHOW - FINALE ? 3 + random.nextInt(4) : 10 + random.nextInt(22));
        }
        rockets.removeIf(rocket -> {
            int t = age - rocket.launched();
            if (t < 0) return false;
            if (t < rocket.climb()) {
                // The trail a rocket of the game leaves.
                Vec3 at = rocket.at(t);
                current.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, at.x, at.y - 0.3, at.z,
                        random.nextGaussian() * 0.05, -rocket.speed() * 0.5, random.nextGaussian() * 0.05);
                return false;
            }
            burst(current, rocket.at(rocket.climb()));
            return true;
        });
        if (!going && rockets.isEmpty() && age > SHOW) clear();
    }

    /** A rocket from somewhere round {@code village}, {@code later} ticks from now, with the game's sound of one. */
    private void launch(ClientLevel current, Vec3 village, int later) {
        double angle = random.nextDouble() * Math.PI * 2, off = Math.sqrt(random.nextDouble()) * SPREAD;
        Rocket rocket = new Rocket(age + later, village.x + Math.cos(angle) * off, village.y + 1, village.z + Math.sin(angle) * off,
                random.nextGaussian() * 0.03, random.nextGaussian() * 0.03,
                SPEED_MIN + random.nextDouble() * (SPEED_MAX - SPEED_MIN), CLIMB_MIN + random.nextInt(CLIMB_MAX - CLIMB_MIN + 1));
        rockets.add(rocket);
        current.playLocalSound(rocket.x(), rocket.y(), rocket.z(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT,
                3f, 1f, true);
    }

    /** A burst of the game's own at {@code at}, sparks and boom, in the colours of the day. */
    private void burst(ClientLevel current, Vec3 at) {
        int[] colours = palette.colours;
        int first = colours.length == 0 ? hue() : colours[random.nextInt(colours.length)];
        int fade = random.nextInt(3) == 0 ? (colours.length == 0 ? hue() : colours[random.nextInt(colours.length)]) : first;
        // Small or large balls, stars and bursts; on the game's birthday, a creeper now and then.
        int[] shapes = palette == Palette.BIRTHDAY ? new int[]{0, 1, 2, 3, 4} : new int[]{0, 1, 1, 2, 4};
        int shape = shapes[random.nextInt(shapes.length)];
        boolean trail = random.nextBoolean(), twinkle = random.nextInt(3) == 0;
        bursts++;
        //? if >=26.3 {
        /*FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.values()[shape], IntList.of(first),
                IntList.of(fade), trail, twinkle);
        current.createFireworks(at.x, at.y, at.z, 0, 0, 0, List.of(explosion), true);
        *///?} elif >=1.20.5 {
        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.values()[shape], IntList.of(first),
                IntList.of(fade), trail, twinkle);
        current.createFireworks(at.x, at.y, at.z, 0, 0, 0, List.of(explosion));
        //?} else {
        /*CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) shape);
        explosion.putIntArray("Colors", new int[]{first});
        explosion.putIntArray("FadeColors", new int[]{fade});
        explosion.putBoolean("Trail", trail);
        explosion.putBoolean("Flicker", twinkle);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag fireworks = new CompoundTag();
        fireworks.put("Explosions", explosions);
        current.createFireworks(at.x, at.y, at.z, 0, 0, 0, fireworks);
        *///?}
    }

    /** A bright colour of any hue, for a show on another day. */
    private int hue() {
        return Mth.hsvToRgb(random.nextFloat(), 0.75f + random.nextFloat() * 0.25f, 1f) & 0xFFFFFF;
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        rockets.clear();
        sites.clear();
        level = null;
    }

    /** The game draws the rockets and bursts; the far plane past them, and the beam, called. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double farthest = 0;
        for (Vec3 site : sites) farthest = Math.max(farthest, Math.sqrt(camera.distanceToSqr(site.x, site.y, site.z)));
        GhostRenderer.noteDistance(farthest + HIGH);
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }
}
