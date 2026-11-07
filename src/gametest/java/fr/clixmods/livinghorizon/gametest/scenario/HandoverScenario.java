package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.ServerConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A server that shares nothing, as one without the mod, and a client whose "Entity Distance"
 * is half the server's: the game stops drawing a cow long before the server stops sending
 * it. The player walks away from a line of cows and back, and at every step each cow is drawn
 * by someone: the game, the mod for the real cow, or its copy. None is left out on the way.
 *
 * <p>Then the server sends less far, as a LAN host does when it lowers its own "Entity
 * Distance": the cows it stops sending are drawn as copies, not hidden as too close.
 */
public final class HandoverScenario implements Scenario {
    /** The cows, along the line of sight, this many blocks ahead of where the player starts. */
    private static final int FIRST = 30, STEP = 10, COWS = 12;
    /** How far back the player walks, and how fast: four blocks every two ticks. */
    private static final int BACK = 260, STRIDE = 4, PACE = 2;

    @Override
    public String name() {
        return "handover";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Path settings = Platform.configDir().resolve(ServerConfig.FILE);
        String before = read(settings);
        write(settings, "{\"sharePlayers\": false, \"shareOfflinePlayers\": false, \"shareMobs\": false}");
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(16);
            minecraft.options.entityDistanceScaling().set(0.5);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
            config.debugHud = true;
        });
        List<String> gaps = new ArrayList<>();
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer()) {
            server.runOnServer(minecraft -> minecraft.getPlayerList().setViewDistance(16));
            server.runCommand("forceload add -16 -16 16 " + (FIRST + STEP * COWS));
            for (int i = 0; i < COWS; i++) {
                server.runCommand("summon cow 0 -60 " + (FIRST + i * STEP) + " {NoAI:1b,Rotation:[90f,0f]}");
            }
            try (var connection = server.connect()) {
                server.runCommand("tp @a 0 -60 0 0 0");
                context.waitTicks(200);
                step(context, 0, "start", gaps);
                for (int z = -STRIDE; z >= -BACK; z -= STRIDE) walk(context, server, z, "away", gaps);
                context.waitTicks(100);
                step(context, -BACK, "far", gaps);
                for (int z = -BACK + STRIDE; z <= 0; z += STRIDE) walk(context, server, z, "back", gaps);
                context.waitTicks(100);
                step(context, 0, "end", gaps);
                // The host lowers its "Entity Distance": its server stops sending the cows past
                // 75 blocks, all at once, as the packet that removes them does. Their copies show.
                context.runOnClient(minecraft -> {
                    List<Entity> far = new ArrayList<>();
                    for (Entity entity : minecraft.level.entitiesForRendering()) {
                        if (entity.getType() == EntityType.COW && entity.getZ() > 75) far.add(entity);
                    }
                    if (far.isEmpty()) throw new AssertionError("no cow sent past 75 blocks");
                    for (Entity cow : far) minecraft.level.removeEntity(cow.getId(), Entity.RemovalReason.DISCARDED);
                    Scene.log("handover " + far.size() + " cows no longer sent, reach "
                            + FarPlayerTracker.get().mobs().reach());
                });
                // Long enough for a copy taken for gone to be forgotten (five checks a second apart).
                context.waitTicks(160);
                context.runOnClient(minecraft -> Scene.log("handover reach now " + FarPlayerTracker.get().mobs().reach()));
                step(context, 0, "lowered", gaps);
            }
        } finally {
            if (before == null) {
                try {
                    Files.deleteIfExists(settings);
                } catch (IOException ignored) {
                }
            } else {
                write(settings, before);
            }
        }
        if (!gaps.isEmpty()) throw new AssertionError("cows drawn by nobody: " + String.join("; ", gaps));
    }

    private static void walk(ClientGameTestContext context, TestDedicatedServerContext server, int z, String way,
                             List<String> gaps) {
        server.runCommand("tp @a 0 -60 " + z + " 0 0");
        context.waitTicks(PACE);
        step(context, z, way, gaps);
    }

    /** What drew each cow in the last frame; a step where fewer than all are drawn is a gap. */
    private static void step(ClientGameTestContext context, int z, String way, List<String> gaps) {
        context.runOnClient(minecraft -> {
            int game = DebugMarks.count(Mark.GAME), live = DebugMarks.count(Mark.LIVE), copy = DebugMarks.count(Mark.FAKE);
            StringBuilder others = new StringBuilder();
            for (Mark mark : Mark.values()) {
                if (mark == Mark.GAME || mark == Mark.LIVE || mark == Mark.FAKE || DebugMarks.count(mark) == 0) continue;
                others.append(' ').append(mark.name().toLowerCase()).append('=').append(DebugMarks.count(mark));
            }
            int drawn = game + live + copy;
            String line = way + " z=" + z + " game=" + game + " live=" + live + " copy=" + copy + others;
            Scene.log("handover " + line);
            if (drawn < COWS) gaps.add(line);
        });
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new AssertionError("could not write " + file, e);
        }
    }
}
