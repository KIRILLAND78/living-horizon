package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.UUIDUtil;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * What the mod costs, to compare before and after a change: a flat world with a crowd far
 * away (a few hundred remembered mobs, test players who logged off, birds), the same camera
 * path every time, and the mod's switches turned off one at a time. Prints a table.
 *
 * <p>The game renders on the CPU here (llvmpipe): GPU work shows as CPU time, so the frame
 * times compare two runs on this machine, not what a graphics card does. What it compares
 * is the mod's own time per tick and per frame, and what it allocates.
 */
public final class PerfScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "chicken"};
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    /** Where the player watches from: the field of animals lies 124 to 476 blocks south. */
    private static final int SIDE = -300;
    private static final int CROWD = 40;
    /** Ticks each phase is left to settle, then measured: one frame per tick in a game test. */
    private static final int SETTLE = 60, FRAMES = 200;
    /** The mod's steps inside a frame, reported one by one: where a change in the frame time comes from. */
    private static final Profile.Section[] STEPS = {Profile.Section.EXTRACT, Profile.Section.DEPTH_BEFORE,
            Profile.Section.DEPTH_AFTER, Profile.Section.OCCLUSION_RUN, Profile.Section.OCCLUSION_COLLECT,
            Profile.Section.SUBMIT_IMPOSTORS, Profile.Section.SUBMIT_BIRDS, Profile.Section.BAKE};
    private static final String[] STEP_NAMES = {"preparing the figures", "far depth before the entities",
            "far depth after them", "drawing the hidden-mob boxes", "reading their answers", "submitting the impostors",
            "submitting the birds", "baking pictures"};
    /** Settings that only act on Voxy's or Distant Horizons' terrain, neither of which runs here. */
    private static final java.util.Set<String> FAR_WORLD =
            java.util.Set.of("optLazyDepth", "optParallelRead", "optColumnCache", "optDhFade", "optVoxyShadows", "optVoxyFog", "optDhFog");

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // Birds on; the easter eggs stay off, they come by chance.
        Scene.configure(context, config -> config.skyBirds = true);
        FarConfig base = context.computeOnClient(minecraft -> copy(FarConfig.get(), new FarConfig()));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A field of animals around the start, 16 blocks apart, as far as the loaded chunks go.
            int i = 0;
            for (int x = -176; x <= 176; x += 16) {
                for (int z = -176; z <= 176; z += 16) {
                    Scene.command(world, "summon " + MOBS[i % MOBS.length] + " " + x + " " + GROUND + " " + z
                            + " {NoAI:1b,Rotation:[" + (i * 45 % 360) + "f,0f]}");
                    i++;
                }
            }
            context.waitTicks(100);
            // Away from it: most of the field is remembered copies now, the server no longer sends them.
            Scene.command(world, "tp @a 0 ~ " + SIDE + " 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 32));
            context.waitTicks(100);
            context.runOnClient(PerfScenario::crowd);

            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            int live = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().live().size());
            int resting = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().spots().size());
            if (shown < 200) throw new AssertionError("perf: only " + shown + " remembered copies, the scene is wrong");
            if (resting < CROWD) throw new AssertionError("perf: only " + resting + " of " + CROWD + " players logged off");

            // Once round unmeasured, with the defaults: impostors baked from every side, the JIT warm.
            measure(context, base, PerfScenario::defaults, SETTLE);

            // The defaults are measured again before each setting and once after the last: each
            // setting is compared with the two around it, so that a slow drift of the whole
            // game cancels out, and how much they differ is the noise.
            Row off = measure(context, base, PerfScenario::off, SETTLE);
            List<Row> defaults = new ArrayList<>();
            List<Row> settings = new ArrayList<>();
            for (Field field : FarConfig.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class
                        || !field.getName().startsWith("opt") && !field.getName().equals("impostors")) {
                    continue;
                }
                // Turning the mod back on rebuilds its figures: longer to settle.
                defaults.add(measure(context, base, PerfScenario::defaults, defaults.isEmpty() ? 200 : SETTLE));
                boolean value = get(field, base);
                Row row = measure(context, base, config -> set(field, config, !value), SETTLE);
                row.setting = field.getName();
                row.name = context.computeOnClient(minecraft -> I18n.get("livinghorizon.options." + field.getName()));
                row.defaultOn = value;
                settings.add(row);
            }
            defaults.add(measure(context, base, PerfScenario::defaults, SETTLE));
            Row offAgain = measure(context, base, PerfScenario::off, SETTLE);
            report(off, offAgain, defaults, settings, String.format(Locale.ROOT,
                    "%d remembered mobs, %d mobs still sent by the server, %d players who logged off, birds",
                    shown, live, resting));

            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
        }
    }

    private static void defaults(FarConfig config) {
    }

    /** The mod off, as its toggle key leaves it, with what it draws on its own off as well. */
    private static void off(FarConfig config) {
        config.enabled = false;
        config.distantMobs = false;
        config.distantEntities = false;
        config.offlinePlayers = false;
        config.skyBirds = false;
    }

    /**
     * Test players who logged off on a ring 120 to 260 blocks round the player, inside and
     * past the render distance, each at the same place every run. Their skins are looked up
     * once, during the unmeasured lap.
     */
    private static void crowd(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        String dimension = SharedPositions.dimensionId(minecraft.level.dimension());
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int n = 0; n < CROWD; n++) {
            String name = "Perf" + n;
            double angle = n * golden, distance = 120 + (n * 37 % 141);
            resting.fake(name, new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), dimension,
                    Math.cos(angle) * distance, GROUND, SIDE + Math.sin(angle) * distance, n * 47 % 360 - 180f,
                    10, null, null);
        }
    }

    /**
     * Settles with the settings changed, then turns the camera once round, one step a frame,
     * timing each frame and reading the mod's own times for it.
     */
    private static Row measure(ClientGameTestContext context, FarConfig base, Consumer<FarConfig> change, int settle) {
        context.runOnClient(minecraft -> {
            change.accept(copy(base, FarConfig.get()));
            look(minecraft, 0);
        });
        System.gc();
        context.waitTicks(settle);
        long thread = context.computeOnClient(minecraft -> Thread.currentThread().threadId());
        Row row = new Row();
        long allocatedBefore = allocated(thread);
        for (int frame = 0; frame < FRAMES; frame++) {
            long started = System.nanoTime();
            context.waitTick();
            row.frame.add(System.nanoTime() - started);
            int next = frame + 1;
            long[] mod = context.computeOnClient(minecraft -> {
                long[] times = {Stats.lastTickNanos(), Stats.lastExtractNanos()};
                look(minecraft, next);
                return times;
            });
            row.tick.add(mod[0]);
            row.extract.add(mod[1]);
            row.own.add(mod[0] + mod[1]);
        }
        row.allocated = (allocated(thread) - allocatedBefore) / FRAMES;
        row.drawn = context.computeOnClient(minecraft -> Stats.drawn());
        // Each step's smoothed average, as the debug panel shows it, at the end of the lap.
        row.steps = context.computeOnClient(minecraft -> {
            double[] steps = new double[STEPS.length];
            for (int i = 0; i < STEPS.length; i++) {
                steps[i] = STEPS[i].cpu().seen() ? STEPS[i].cpu().average() / 1_000_000.0 : 0;
            }
            return steps;
        });
        return row;
    }

    /** The camera path: once round, from facing the field (south), level. */
    private static void look(Minecraft minecraft, int frame) {
        if (minecraft.player == null) return;
        float yaw = 360f * frame / FRAMES;
        minecraft.player.setYRot(yaw);
        minecraft.player.yRotO = yaw;
        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setXRot(0);
        minecraft.player.xRotO = 0;
    }

    /** Bytes the render thread has allocated so far; -1 where the JVM does not tell. */
    private static long allocated(long thread) {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            return bean.getThreadAllocatedBytes(thread);
        }
        return -1;
    }

    /**
     * Two parts, in words: what the mod costs against the game without it, then what each
     * setting does, changed alone against the defaults measured around it. A difference
     * smaller than the defaults' own spread is reported as none.
     */
    private static void report(Row off, Row offAgain, List<Row> defaults, List<Row> settings, String scene) {
        double without = (off.frame.average() + offAgain.frame.average()) / 2;
        Row with = new Row();
        double frameNoise = without / 100, codeNoise = 0.02;
        for (int i = 0; i < defaults.size(); i++) {
            Row row = defaults.get(i);
            with.frame.addAll(row.frame);
            with.tick.addAll(row.tick);
            with.extract.addAll(row.extract);
            with.own.addAll(row.own);
            with.allocated += row.allocated / defaults.size();
            with.drawn = row.drawn;
            for (int step = 0; step < STEPS.length; step++) with.steps[step] += row.steps[step] / defaults.size();
            if (i == 0) continue;
            Row before = defaults.get(i - 1);
            frameNoise = Math.max(frameNoise, Math.abs(row.frame.average() - before.frame.average()));
            codeNoise = Math.max(codeNoise, Math.abs(row.own.average() - before.own.average()));
        }
        double cost = with.frame.average() - without;

        line("");
        line("======== What the mod costs ========");
        line("Scene: " + scene + ".");
        line("Each measure: " + FRAMES + " frames, the camera turning once round. llvmpipe draws on the CPU:");
        line("compare two runs on this machine, never with a graphics card.");
        line("");
        line(String.format(Locale.ROOT, "Without the mod .......... %6.1f ms per frame (%.1f FPS)", without, 1000 / without));
        line(String.format(Locale.ROOT, "With the mod (defaults) .. %6.1f ms per frame (%.1f FPS): %+.1f ms (%+.0f%%)",
                with.frame.average(), 1000 / with.frame.average(), cost, cost / without * 100));
        line(String.format(Locale.ROOT, "  the mod's own code ..... %6.2f ms per frame on average, %.1f ms at worst",
                with.own.average(), with.own.max()));
        line(String.format(Locale.ROOT, "     (its tick %.2f ms, worst %.1f; preparing the figures %.2f ms, worst %.1f)",
                with.tick.average(), with.tick.max(), with.extract.average(), with.extract.max()));
        line(String.format(Locale.ROOT, "  memory ................. %+6.0f KB allocated per frame",
                (with.allocated - (off.allocated + offAgain.allocated) / 2.0) / 1024));
        line(String.format(Locale.ROOT, "  figures drawn .......... %6d", with.drawn));
        StringBuilder steps = new StringBuilder("  steps inside a frame ... ");
        for (int step = 0; step < STEPS.length; step++) {
            if (step > 0) steps.append(", ");
            steps.append(String.format(Locale.ROOT, "%s %.2f ms", STEP_NAMES[step], with.steps[step]));
        }
        line(steps.toString());
        line(String.format(Locale.ROOT, "Noise: the defaults, measured %d times, differ by up to %.1f ms per frame and %.2f ms",
                defaults.size(), frameNoise, codeNoise));
        line("of the mod's code. Smaller differences below are not results.");

        line("");
        line("======== What each setting does (changed alone, against the defaults) ========");
        line("+ means slower, or more memory, once the setting is changed from its default.");
        line(String.format(Locale.ROOT, "%-24s %-10s %9s %12s %9s   %s", "Setting", "Changed to", "Frame", "Mod's code", "Memory", "Verdict"));
        List<String[]> lines = new ArrayList<>();
        boolean starred = false;
        for (int i = 0; i < settings.size(); i++) {
            Row row = settings.get(i), before = defaults.get(i), after = defaults.get(i + 1);
            double frame = row.frame.average() - (before.frame.average() + after.frame.average()) / 2;
            double code = row.own.average() - (before.own.average() + after.own.average()) / 2;
            double memory = (row.allocated - (before.allocated + after.allocated) / 2.0) / 1024;
            boolean far = FAR_WORLD.contains(row.setting);
            starred |= far;
            String sort = String.format(Locale.ROOT, "%012.4f", 1000 - frame);
            lines.add(new String[]{sort, String.format(Locale.ROOT, "%-24s %-10s %+6.1f ms %+9.2f ms %+6.0f KB   %s",
                    row.name + (far ? " *" : ""), row.defaultOn ? "off" : "on", frame, code, memory,
                    verdict(row.defaultOn, frame, code, frameNoise, codeNoise))});
        }
        lines.sort(java.util.Comparator.comparing(entry -> entry[0]));
        for (String[] entry : lines) line(entry[1]);
        if (starred) line("* Acts on Voxy's or Distant Horizons' terrain, which cannot run here: no difference expected.");
    }

    /**
     * The verdict on a setting, from the frame time when it moved past the noise, else from
     * the mod's own code: {@code frame} and {@code code} are how much slower each got once the
     * setting was changed from its default.
     */
    private static String verdict(boolean defaultOn, double frame, double code, double frameNoise, double codeNoise) {
        double change;
        String what;
        if (Math.abs(frame) > frameNoise) {
            change = frame;
            what = " per frame";
        } else if (Math.abs(code) > codeNoise) {
            change = code;
            what = " of the mod's code";
        } else {
            return "no difference";
        }
        String amount = String.format(Locale.ROOT, "%.2f ms", Math.abs(change)) + what;
        if (change > 0) return defaultOn ? "keep it on: saves " + amount : "keep it off: on costs " + amount;
        return defaultOn ? "faster off: on costs " + amount : "faster on: saves " + amount;
    }

    private static void line(String text) {
        Scene.log("perf " + text);
    }

    /** Copies every setting of {@code from} into {@code to}, field by field; {@code to}. */
    private static FarConfig copy(FarConfig from, FarConfig to) {
        for (Field field : FarConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(from);
                // The lists are the mod's own: never shared between two configs.
                if (value instanceof List<?> list) value = new ArrayList<>(list);
                field.set(to, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return to;
    }

    private static boolean get(Field field, FarConfig config) {
        try {
            return field.getBoolean(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field field, FarConfig config, boolean value) {
        try {
            field.setBoolean(config, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Row {
        /** The setting changed, its name in the game, and its default. */
        String setting, name;
        boolean defaultOn;
        /** The whole frame; the mod's tick, its extraction, and the two together. */
        final Times frame = new Times(), tick = new Times(), extract = new Times(), own = new Times();
        long allocated;
        int drawn;
        /** Milliseconds per frame of each of {@link #STEPS}, smoothed. */
        double[] steps = new double[STEPS.length];
    }

    /** Nanoseconds, given in; read back in milliseconds. */
    private static final class Times {
        private long total, max;
        private int count;

        void addAll(Times other) {
            total += other.total;
            max = Math.max(max, other.max);
            count += other.count;
        }

        void add(long nanos) {
            total += nanos;
            max = Math.max(max, nanos);
            count++;
        }

        double average() {
            return count == 0 ? 0 : total / 1e6 / count;
        }

        double max() {
            return max / 1e6;
        }
    }
}
