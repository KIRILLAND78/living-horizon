package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The remembered mobs' file, written off the game's thread as the world closes and read off it
 * as the world opens again: every mob remembered comes back. The file holds the mobs' saved data
 * as it is (no {@code =} for each {@code =}), without what never shows (a villager's memories).
 */
public final class MobFileScenario implements Scenario {
    private static final String[] MOBS = {"cow", "villager", "sheep", "villager"};

    @Override
    public String name() {
        return "mobfile";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        TestWorldSave save;
        Set<UUID> remembered = new HashSet<>();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            save = world.getWorldSave();
            for (int i = 0; i < MOBS.length; i++) Scene.summon(world, MOBS[i], i * 4 - 6, 150, 0);
            context.waitTicks(60);
            remembered.addAll(context.computeOnClient(minecraft -> {
                // What the server's packet does when it stops sending a mob: each is remembered.
                List<Entity> mobs = new ArrayList<>();
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.COW || entity.getType() == EntityType.VILLAGER
                            || entity.getType() == EntityType.SHEEP) mobs.add(entity);
                }
                Set<UUID> ids = new HashSet<>();
                for (Entity mob : mobs) {
                    ids.add(mob.getUUID());
                    minecraft.level.removeEntity(mob.getId(), Entity.RemovalReason.DISCARDED);
                }
                return ids;
            }));
            if (remembered.size() != MOBS.length) throw new AssertionError("mobfile: " + remembered.size() + " mobs sent");
            // Far enough that they are not sent again when the world opens.
            Scene.command(world, "tp @a 0 ~ -300");
            context.waitTicks(40);
        }

        String text = file();
        for (UUID id : remembered) {
            if (!text.contains(id.toString())) throw new AssertionError("mobfile: " + id + " not in the file");
        }
        if (text.contains("\\u003d") || text.contains("\\u0027")) throw new AssertionError("mobfile: escaped text in the file");
        if (text.contains("Brain")) throw new AssertionError("mobfile: a mob's memories in the file");
        Scene.log("mobfile: " + remembered.size() + " mobs written, " + text.length() + " characters, nothing escaped");

        try (TestSingleplayerContext again = save.open()) {
            String missing = null;
            int waited = 0;
            while (waited < 200) {
                context.waitTicks(10);
                waited += 10;
                missing = context.computeOnClient(minecraft -> {
                    String dimension = minecraft.level.dimension().identifier().toString();
                    Set<UUID> back = new HashSet<>();
                    for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered(dimension)) back.add(mob.id());
                    Set<UUID> lost = new HashSet<>(remembered);
                    lost.removeAll(back);
                    return lost.isEmpty() ? null : lost + " not read back";
                });
                if (missing == null) break;
            }
            Scene.log("mobfile: " + (missing == null ? "every mob read back" : missing) + " within " + waited + " ticks");
            if (missing != null) throw new AssertionError("mobfile: " + missing);
        }
    }

    /** The newest file of remembered mobs: this world's. */
    private static String file() {
        Path folder = Platform.configDir().resolve("livinghorizon").resolve("mobs");
        try (Stream<Path> files = Files.list(folder)) {
            Path newest = files.filter(path -> path.toString().endsWith(".json"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("mobfile: no file in " + folder));
            return Files.readString(newest);
        } catch (IOException e) {
            throw new AssertionError("mobfile: cannot read " + folder, e);
        }
    }
}
