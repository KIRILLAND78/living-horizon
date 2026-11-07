package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.ServerConfig;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.SeedDraws;
import fr.clixmods.livinghorizon.track.SeedMobs;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A world of the usual terrain, with a seed of its own: the server foresees the animals of the
 * chunks it has not generated yet and shares them; the client draws them, and hides them once
 * its setting is off. Then a square of those chunks is generated: the animals the game put there
 * are compared with those foreseen (how many stand within 2 blocks of one of the same kind, the
 * world frozen so that none wandered off), and the server stops sharing the foreseen ones there.
 */
public final class SeedMobsScenario implements Scenario {
    /** How far from the herd the square generated reaches, in chunks: 11 by 11. */
    private static final int SIDE = 5;

    @Override
    public String name() {
        return "seedmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(4));
        Scene.configure(context, config -> {
            config.seedMobs = true;
            config.onlyOverTerrain = false;
            config.hideOccludedMobs = false;
        });
        // Not the consistent settings: they make a flat world, which has no first animals.
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("living horizon"))
                .create()) {
            context.waitTicks(100);
            //? if >=1.21.11 {
            Scene.command(world, "gamerule advance_time false");
            //?} else {
            /*Scene.command(world, "gamerule doDaylightCycle false");
            *///?}
            Scene.command(world, "time set noon");
            Scene.command(world, "weather clear");
            // Two full shares: the first starts the pass, the next sends what it found.
            context.waitTicks(260);

            List<SharedPositions.MobReport> foreseen = new ArrayList<>();
            List<SharedPositions.MobReport> loaded = new ArrayList<>();
            context.runOnClient(minecraft -> {
                SharedPositions shared = FarPlayerTracker.get().shared();
                for (UUID id : shared.mobs()) {
                    SharedPositions.MobReport mob = shared.readMob(id);
                    if (mob != null && SeedDraws.foreseen(id)) foreseen.add(mob);
                }
                if (foreseen.isEmpty()) throw new AssertionError("the server foresees no animal around");
                // A chunk loaded since the last share: never drawn, and no longer shared at the next.
                for (SharedPositions.MobReport mob : foreseen) {
                    if (minecraft.level.getChunkSource().hasChunk((int) Math.floor(mob.x()) >> 4, (int) Math.floor(mob.z()) >> 4)) loaded.add(mob);
                }
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (SeedDraws.foreseen(mob.id()) && minecraft.level.getChunkSource().hasChunk((int) Math.floor(mob.x()) >> 4,
                            (int) Math.floor(mob.z()) >> 4)) {
                        throw new AssertionError("a foreseen mob drawn in a chunk already there: " + mob.type());
                    }
                }
                long drawn = FarPlayerTracker.get().mobs().shown().stream().filter(m -> SeedDraws.foreseen(m.id())).count();
                if (drawn == 0) {
                    throw new AssertionError("no foreseen mob among those drawn: remembered " + FarPlayerTracker.get().mobs().size()
                            + ", drawn " + FarPlayerTracker.get().mobs().shown().size() + ", player at " + minecraft.player.position()
                            + ", first foreseen at " + foreseen.get(0).x() + ", " + foreseen.get(0).z() + " " + MobKinds.type(foreseen.get(0).kind()));
                }
                Scene.log("seedmobs foreseen=" + foreseen.size() + " drawn=" + drawn + " in chunks loaded since=" + loaded.size());
            });
            // Past the render distance the game's fog hides them without Voxy or Distant Horizons, and
            // their boxes too (the lines are fogged): the pink marks of the frame are counted instead,
            // the camera turned to the nearest herd.
            float[] look = context.computeOnClient(minecraft -> {
                SharedPositions.MobReport herd = foreseen.stream()
                        .min(java.util.Comparator.comparingDouble(mob -> Math.hypot(mob.x() - minecraft.player.getX(),
                                mob.z() - minecraft.player.getZ())))
                        .orElseThrow();
                double dx = herd.x() - minecraft.player.getX(), dz = herd.z() - minecraft.player.getZ();
                double dy = herd.y() - minecraft.player.getEyeY();
                return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)),
                        (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
            });
            Scene.command(world, "tp @p ~ ~ ~ " + look[0] + " " + look[1]);
            Scene.configure(context, config -> config.debugBoxes = config.debugLabels = true);
            context.waitTicks(20);
            Scene.screenshot(context, this, "foreseen");
            int[] marks = context.computeOnClient(minecraft -> new int[]{DebugMarks.count(DebugMarks.Mark.FORESEEN),
                    DebugMarks.count(DebugMarks.Mark.FAKE)});
            Scene.log("seedmobs in view: " + marks[0] + " foreseen (pink), " + marks[1] + " other copies");
            if (marks[0] == 0) throw new AssertionError("no foreseen herd drawn in front of the camera");
            Scene.configure(context, config -> config.debugBoxes = config.debugLabels = false);

            // The client's setting off: none drawn, though the server still shares them.
            Scene.configure(context, config -> config.seedMobs = false);
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (SeedDraws.foreseen(mob.id())) throw new AssertionError("a foreseen mob drawn with the setting off");
                }
                Scene.log("seedmobs setting off: none drawn");
            });
            Scene.configure(context, config -> config.seedMobs = true);

            // This client foresees them too, by itself, as far as mobs are drawn.
            context.waitTicks(100);
            int auto = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().size());
            if (auto == 0) throw new AssertionError("the client foresaw nothing by itself");
            Scene.log("seedmobs foreseen by the client itself: " + auto);

            // Mob Render Distance raised: where the player looks fills in within seconds, far out.
            Scene.configure(context, config -> {
                config.mobMaxDistance = 2000;
                config.maxDistantMobs = fr.clixmods.livinghorizon.FarConfig.UNLIMITED_MOBS;
            });
            for (int second = 1; second <= 5; second++) {
                context.waitTicks(20);
                int at = second;
                Scene.log("seedmobs after " + at + " s: " + context.computeOnClient(minecraft -> {
                    var memory = FarPlayerTracker.get().mobs();
                    var self = minecraft.player;
                    long remembered = memory.remembered(minecraft.level.dimension().identifier().toString()).stream()
                            .filter(mob -> SeedDraws.foreseen(mob.id()) && Math.hypot(mob.x() - self.getX(), mob.z() - self.getZ()) > 640).count();
                    long shown = memory.shown().stream()
                            .filter(mob -> SeedDraws.foreseen(mob.id()) && Math.hypot(mob.x() - self.getX(), mob.z() - self.getZ()) > 640).count();
                    return "index " + memory.foresight().size() + ", past 640 blocks remembered " + remembered + ", shown " + shown
                            + " of " + memory.shown().size();
                }));
            }
            int[] filled = context.computeOnClient(minecraft -> {
                var self = minecraft.player;
                var sight = self.getViewVector(1f);
                int ahead = 0, behind = 0;
                for (var mob : FarPlayerTracker.get().mobs().shown()) {
                    double dx = mob.x() - self.getX(), dz = mob.z() - self.getZ(), length = Math.hypot(dx, dz);
                    if (!SeedDraws.foreseen(mob.id()) || length < 640) continue;
                    if ((dx * sight.x + dz * sight.z) / (length * Math.hypot(sight.x, sight.z)) > 0.5) ahead++;
                    else behind++;
                }
                return new int[]{ahead, behind};
            });
            if (filled[0] == 0) throw new AssertionError("nothing foreseen past 640 blocks where the player looks, 5 s after");
            Scene.log("seedmobs Mob Render Distance 2000: past 640 blocks, " + filled[0] + " ahead and " + filled[1] + " elsewhere after 5 s");
            Scene.configure(context, config -> {
                config.mobMaxDistance = new fr.clixmods.livinghorizon.FarConfig().mobMaxDistance;
                config.maxDistantMobs = new fr.clixmods.livinghorizon.FarConfig().maxDistantMobs;
            });
            int beforeScope = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().size());

            // The game's generator built again from the seed, as a client does when a server shares it: the same animals.
            context.runOnClient(minecraft -> {
                ServerLevel level = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
                SeedDraws.Source server = SeedDraws.Source.of(level);
                String[] keys = SeedDraws.vanillaKeys(level);
                if (server == null || keys == null) throw new AssertionError("the overworld is not the game's own generator");
                SeedDraws.Source built = SeedDraws.vanilla("minecraft:overworld", level.getSeed(), keys[0], keys[1],
                        minecraft.level, minecraft.level.dimensionType(), minecraft.level.registryAccess());
                if (built == null) throw new AssertionError("could not build the generator from " + keys[0] + ", " + keys[1]);
                // Where the server foresaw some, and the chunks round them.
                it.unimi.dsi.fastutil.longs.LongOpenHashSet around = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
                for (SharedPositions.MobReport mob : foreseen) {
                    int cx = (int) Math.floor(mob.x()) >> 4, cz = (int) Math.floor(mob.z()) >> 4;
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) around.add(SeedDraws.chunk(cx + dx, cz + dz));
                }
                long[] chunks = around.toLongArray();
                var expected = SeedMobs.draw(server, chunks, null);
                var drawn = SeedMobs.draw(built, chunks, null);
                if (expected.isEmpty()) throw new AssertionError("the server's generator draws nothing where it foresaw animals");
                if (!expected.equals(drawn)) {
                    throw new AssertionError("the generator built from the seed draws other animals: " + drawn.size()
                            + " chunks with some, the server's " + expected.size());
                }
                Scene.log("seedmobs the generator built from the seed draws the server's animals: " + expected.size() + " of "
                        + chunks.length + " chunks with some");
            });

            // Through a spyglass: where it looks, however far, is foreseen too.
            Scene.command(world, "item replace entity @p weapon.mainhand with minecraft:spyglass");
            context.waitTicks(5);
            context.getInput().holdKey(options -> options.keyUse);
            // How soon one shows where it looks, past where it foresees by itself: the spyglass's alone.
            int soon = -1;
            for (int tick = 0; tick < 100; tick++) {
                context.waitTicks(1);
                boolean seen = context.computeOnClient(minecraft -> {
                    var memory = FarPlayerTracker.get().mobs();
                    double far = fr.clixmods.livinghorizon.FarConfig.get().mobMaxDistance + 64.0;
                    return memory.shown().stream().anyMatch(mob -> SeedDraws.foreseen(mob.id()) && mob.puppet() != null
                            && memory.foresight().inScope(mob.x(), mob.y() + 0.5, mob.z())
                            && Math.hypot(mob.x() - minecraft.player.getX(), mob.z() - minecraft.player.getZ()) > far);
                });
                if (seen) {
                    soon = tick + 1;
                    break;
                }
            }
            context.waitTicks(Math.max(0, 100 - Math.max(soon, 0)));
            boolean scoping = context.computeOnClient(minecraft -> minecraft.player.isScoping());
            int looked = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().size());
            context.getInput().releaseKey(options -> options.keyUse);
            if (!scoping) throw new AssertionError("the player does not look through the spyglass");
            if (looked <= beforeScope) throw new AssertionError("nothing more foreseen through the spyglass: " + looked);
            if (soon < 0) throw new AssertionError("nothing drawn where the spyglass looks within 100 ticks");
            Scene.log("seedmobs through the spyglass: " + looked + ", the first drawn where it looks after " + soon + " ticks");

            // By hand, farther.
            context.runOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().start(minecraft, 64));
            for (int i = 0; i < 90 && context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().scanning()); i++) {
                context.waitTicks(20);
            }
            int scanned = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().foresight().size());
            if (scanned <= looked) throw new AssertionError("the guess found nothing more: " + scanned);
            Scene.log("seedmobs after a guess within 64 chunks: " + scanned);
            // Kept in a file of their own, a few bytes each.
            context.waitTicks(20);
            Path kept = Platform.configDir().resolve("livinghorizon").resolve("foreseen");
            long bytes;
            try (var files = Files.walk(kept)) {
                bytes = files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".bin"))
                        .mapToLong(file -> file.toFile().length()).sum();
            } catch (java.io.IOException e) {
                throw new AssertionError("no file of foreseen mobs in " + kept, e);
            }
            if (bytes == 0) throw new AssertionError("no foreseen mob saved in " + kept);
            Scene.log("seedmobs saved: " + bytes + " bytes for " + scanned + " mobs");

            // The owner shares the seed: the client hears it.
            Path settings = Platform.configDir().resolve(ServerConfig.FILE);
            share(settings, true);
            context.waitTicks(160);
            context.runOnClient(minecraft -> {
                PositionsMessage.Seed seed = FarPlayerTracker.get().shared().seed();
                if (seed == null || seed.dimensions().stream().noneMatch(ground -> ground.dimension().equals("minecraft:overworld"))) {
                    throw new AssertionError("the seed is not shared: " + seed);
                }
                Scene.log("seedmobs seed shared for " + seed.dimensions());
            });
            share(settings, false);

            // A square of the chunks generated, as a player walking there would: the game's own animals.
            // Around a foreseen herd past the render distance, so that the square has some.
            double[] at = context.computeOnClient(minecraft -> {
                SharedPositions.MobReport herd = foreseen.stream()
                        .min(java.util.Comparator.comparingDouble(mob -> Math.abs(Math.hypot(mob.x() - minecraft.player.getX(),
                                mob.z() - minecraft.player.getZ()) - 250)))
                        .orElseThrow();
                return new double[]{herd.x(), herd.z()};
            });
            int cx0 = (int) Math.floor(at[0]) >> 4, cz0 = (int) Math.floor(at[1]) >> 4;
            int west = cx0 - SIDE, east = cx0 + SIDE, north = cz0 - SIDE, south = cz0 + SIDE;
            List<SharedPositions.MobReport> inSquare = new ArrayList<>();
            for (SharedPositions.MobReport mob : foreseen) {
                int cx = (int) Math.floor(mob.x()) >> 4, cz = (int) Math.floor(mob.z()) >> 4;
                if (cx >= west && cx <= east && cz >= north && cz <= south) inSquare.add(mob);
            }
            // Frozen, so that the animals stand where the game put them, not where they wandered since.
            Scene.command(world, "tick freeze");
            Scene.command(world, "forceload add " + west * 16 + " " + north * 16 + " " + (east * 16 + 15) + " " + (south * 16 + 15));
            context.waitTicks(400);
            int[] near = {0};
            List<String> missed = new ArrayList<>();
            context.runOnClient(minecraft -> {
                ServerLevel level = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
                for (SharedPositions.MobReport mob : inSquare) {
                    String type = MobKinds.type(mob.kind());
                    double best = Double.MAX_VALUE;
                    for (Entity entity : level.getAllEntities()) {
                        if (!EntityType.getKey(entity.getType()).toString().equals(type)) continue;
                        best = Math.min(best, Math.hypot(entity.getX() - mob.x(), entity.getZ() - mob.z()));
                    }
                    if (best <= 2) near[0]++;
                    else missed.add(type + " at " + (int) mob.x() + ", " + (int) mob.y() + ", " + (int) mob.z() + " (nearest " + (int) best + ")");
                }
                Scene.log("seedmobs square: " + near[0] + " of " + inSquare.size() + " foreseen stand within 2 blocks of the game's"
                        + (missed.isEmpty() ? "" : ", missed " + missed));
            });
            Scene.command(world, "tick unfreeze");
            // Shared again after the next full share: the foreseen ones of those chunks are gone.
            context.waitTicks(140);
            List<SharedPositions.MobReport> generated = new ArrayList<>(inSquare);
            generated.addAll(loaded);
            context.runOnClient(minecraft -> {
                SharedPositions shared = FarPlayerTracker.get().shared();
                for (SharedPositions.MobReport mob : generated) {
                    if (shared.readMob(mob.id()) != null) {
                        throw new AssertionError("still foreseen in a generated chunk: " + MobKinds.type(mob.kind()));
                    }
                }
                Scene.log("seedmobs generated chunks: foreseen ones no longer shared");
            });
            // About half stand where the game puts them; the others are herds the game turned down for
            // what the bare terrain lacks (grass, a tree in the way). Draws gone wrong would leave none.
            if (inSquare.isEmpty() || near[0] * 3 < inSquare.size()) {
                throw new AssertionError("fewer than a third of the foreseen mobs stand where the game put them: "
                        + near[0] + " of " + inSquare.size());
            }
            Scene.command(world, "forceload remove all");
        }
    }

    /** The server's shareSeed setting, written in its file as an owner would. */
    private static void share(Path settings, boolean on) {
        try {
            String text = Files.readString(settings).replace("\"shareSeed\": " + !on, "\"shareSeed\": " + on);
            Files.writeString(settings, text);
            Files.setLastModifiedTime(settings, FileTime.fromMillis(System.currentTimeMillis() + (on ? 5000 : 10000)));
        } catch (IOException e) {
            throw new AssertionError("could not change " + settings, e);
        }
    }
}
