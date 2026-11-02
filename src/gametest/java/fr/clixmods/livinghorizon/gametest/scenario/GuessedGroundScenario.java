package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.DrawnGround;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.SeedDraws;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Guessed mobs over Distant Horizons' terrain (`--with distanthorizons`, 26.3): with <i>Only Over
 * Terrain</i> on, a guessed mob standing where Distant Horizons holds terrain must not be judged
 * over the void. Reads, for a sample of the guessed mobs chosen, whether Distant Horizons has their
 * column (through its API) and what the mod decided, and fails when terrain is there and the mod
 * says void.
 */
public final class GuessedGroundScenario implements Scenario {
    @Override
    public String name() {
        return "guessedground";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded("distanthorizons")) {
            Scene.log("guessedground skipped: Distant Horizons is not installed (run.sh guessedground --with distanthorizons)");
            return;
        }
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(4));
        Scene.configure(context, config -> {
            config.seedMobs = true;
            config.onlyOverTerrain = true;
            config.hideOccludedMobs = false;
            config.mobMaxDistance = 512;
            config.debugBoxes = true;
        });
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("living horizon"))
                .create()) {
            Scene.command(world, "gamerule advance_time false");
            Scene.command(world, "time set noon");
            Scene.command(world, "weather clear");
            int[] last = null;
            for (int round = 0; round < 18; round++) {
                context.waitTicks(200);
                int[] counts = count(context, (round + 1) * 10);
                last = counts;
                if (counts[1] > 20 && round >= 5) break;
            }
            Scene.screenshot(context, this, "guessed");
            if (last != null && last[2] > Math.max(2, last[1] / 20)) {
                throw new AssertionError("guessedground: " + last[2] + " guessed mobs judged over the void where Distant Horizons has terrain, of "
                        + last[1]);
            }
        } finally {
            Scene.configure(context, config -> config.debugBoxes = false);
        }
    }

    /** Guessed mobs whose column is read through Distant Horizons' API, a quarter of a second each. */
    private static final int SAMPLE = 40;
    private static final long REFERENCE_NANOS = 30_000_000_000L;
    private static boolean failureLogged;

    /**
     * {sampled, with terrain in Distant Horizons, void though terrain, void without, unknown,
     * terrain} for a sample of the guessed mobs chosen now: the mod's answer asked of DrawnGround
     * as the frame does, then Distant Horizons read directly on this thread, outside the mod's
     * readers, whose queue the guessed mobs' own ground reads fill.
     */
    private static int[] count(ClientGameTestContext context, int seconds) {
        List<double[]> spots = context.computeOnClient(minecraft -> {
            List<double[]> found = new ArrayList<>();
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                if (SeedDraws.foreseen(mob.id())) found.add(new double[]{mob.x(), mob.z()});
            }
            List<double[]> sample = new ArrayList<>();
            int n = Math.min(SAMPLE, found.size());
            for (int i = 0; i < n; i++) sample.add(found.get(i * found.size() / n));
            return sample;
        });
        String[] answers = context.computeOnClient(minecraft -> {
            String[] out = new String[spots.size()];
            for (int i = 0; i < out.length; i++) out[i] = answer(minecraft, spots.get(i)[0], spots.get(i)[1]);
            return out;
        });
        int[] answered = new int[3];
        for (String answer : answers) answered[answer.equals("VOID") ? 0 : answer.equals("UNKNOWN") ? 1 : 2]++;
        Scene.log("guessedground after " + seconds + " s: the mod says, of " + answers.length + " guessed mobs sampled: void "
                + answered[0] + ", unknown " + answered[1] + ", terrain " + answered[2] + "; marks void "
                + context.computeOnClient(minecraft -> DebugMarks.count(DebugMarks.Mark.VOID)) + "; "
                + context.computeOnClient(minecraft -> DrawnGround.status() + "; " + LodWorld.status() + " " + LodWorld.details()
                + " drawn to " + (int) LodWorld.drawnDistance()));
        int[] counts = new int[6];
        long started = System.nanoTime();
        for (int i = 0; i < spots.size() && System.nanoTime() - started < REFERENCE_NANOS; i++) {
            Boolean terrain = column((int) Math.floor(spots.get(i)[0]), (int) Math.floor(spots.get(i)[1]));
            if (terrain == null) continue;
            counts[0]++;
            if (terrain) counts[1]++;
            switch (answers[i]) {
                case "VOID" -> counts[terrain ? 2 : 3]++;
                case "UNKNOWN" -> counts[4]++;
                default -> counts[5]++;
            }
        }
        Scene.log("guessedground after " + seconds + " s: read directly " + counts[0] + " in " + (System.nanoTime() - started) / 1_000_000
                + " ms, Distant Horizons has their column " + counts[1] + ", judged void with terrain there " + counts[2]
                + ", void without terrain " + counts[3] + ", unknown " + counts[4] + ", terrain " + counts[5]);
        return counts;
    }

    /** Whether Distant Horizons holds blocks at this column, through its API; null when it did not answer. */
    private static Boolean column(int x, int z) {
        try {
            Method source = LodWorld.class.getDeclaredMethod("source");
            source.setAccessible(true);
            Object lod = source.invoke(null);
            if (lod == null) return null;
            Class<?> type = Class.forName("fr.clixmods.livinghorizon.compat.LodSource");
            Method open = LodWorld.class.getDeclaredMethod("open", type);
            open.setAccessible(true);
            Object world = open.invoke(null, lod);
            if (world == null) return null;
            Method column = type.getDeclaredMethod("column", Object.class, int.class, int.class);
            column.setAccessible(true);
            return column.invoke(lod, world, x, z) != null;
        } catch (ReflectiveOperationException e) {
            // Unanswered, or no world: not counted, and the first reason logged.
            if (!failureLogged) {
                failureLogged = true;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                Scene.log("guessedground: a direct read of Distant Horizons gave no answer: " + cause);
            }
            return null;
        }
    }

    private static String answer(Minecraft minecraft, double x, double z) {
        try {
            Class<?> ground = Class.forName("fr.clixmods.livinghorizon.compat.DrawnGround");
            Method answer = ground.getDeclaredMethod("answer", net.minecraft.client.multiplayer.ClientLevel.class, Vec3.class,
                    double.class, double.class);
            answer.setAccessible(true);
            return String.valueOf(answer.invoke(null, minecraft.level, minecraft.player.getEyePosition(), x, z));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("DrawnGround.answer: " + e, e);
        }
    }
}
