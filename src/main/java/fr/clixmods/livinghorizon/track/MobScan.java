package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.EntityStorage;
//? if >=1.20.5
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The mobs of the chunks around, read from the world itself instead of met on the way.
 *
 * <p>Only in a single player world: the integrated server runs in this game, and its
 * world is right here. The chunks it has loaded give their mobs as they are now; the
 * others are read from the world's save, the way the game reads them when a chunk loads -
 * without loading it, nor generating anything. A chunk never visited has nothing saved.
 *
 * <p>On another server there is nothing to read: a client only receives the mobs near it,
 * and a server with the mod shares them from farther ({@link PositionPublisher}).
 */
public final class MobScan {
    /** One mob found, as the game saves it, ready to be remembered. */
    public record Found(UUID id, String type, double x, double y, double z, float yaw, CompoundTag nbt, boolean named) {
    }

    /** What a scan found, and the chunks it covered: a remembered mob in those that was not found is gone. */
    public record Result(String dimension, int centerX, int centerZ, int radius, int chunksRead, List<Found> mobs) {
        public boolean covers(double x, double z) {
            int dx = ((int) Math.floor(x) >> 4) - centerX, dz = ((int) Math.floor(z) >> 4) - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    public static final int MIN_RADIUS = 4;
    /** The top of the settings slider; the command takes any radius. */
    public static final int MAX_RADIUS = 1024;

    private static boolean running;

    private MobScan() {
    }

    /**
     * Scans and remembers what it finds, saying so in the chat: the settings button and
     * {@code /livinghorizon scan}. One scan at a time.
     */
    public static void start(Minecraft client, int radius) {
        if (running) {
            tell(client, Component.translatable("livinghorizon.scan.running"));
            return;
        }
        CompletableFuture<Result> scan = scan(client, radius);
        if (scan == null) {
            tell(client, Component.translatable("livinghorizon.scan.unavailable"));
            return;
        }
        running = true;
        tell(client, Component.translatable("livinghorizon.scan.started", Math.max(radius, MIN_RADIUS)));
        scan.whenComplete((result, error) -> client.execute(() -> {
            running = false;
            if (error != null || result == null) {
                LivingHorizonClient.LOGGER.warn("Could not scan the chunks around", error);
                tell(client, Component.translatable("livinghorizon.scan.failed"));
                return;
            }
            // Left the world, or another dimension, while it was reading: what it found is not here.
            if (client.level == null || !client.level.dimension().identifier().toString().equals(result.dimension())) return;
            int found = FarPlayerTracker.get().mobs().adopt(result);
            tell(client, Component.translatable("livinghorizon.scan.done", found, result.radius()));
        }));
    }

    private static void tell(Minecraft client, Component message) {
        //? if >=26.1 {
        /*if (client.player != null) client.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        if (client.player != null) client.player.displayClientMessage(message, false);
        //?}
    }

    /** Whether there is a world here to read: a single player one. */
    public static boolean available(Minecraft client) {
        return client.getSingleplayerServer() != null && client.level != null && client.player != null;
    }

    /**
     * Reads every mob within {@code radius} chunks of the player, in this dimension. Kinds
     * and names are filtered as for the mobs met on the way. Null outside single player.
     * The future completes off the game's thread.
     */
    public static @Nullable CompletableFuture<Result> scan(Minecraft client, int radius) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.level == null || client.player == null) return null;
        ResourceKey<Level> key = client.level.dimension();
        ChunkPos center = client.player.chunkPosition();
        int r = Math.max(radius, MIN_RADIUS);
        FarConfig config = FarConfig.get();
        Filter filter = new Filter(Set.copyOf(config.mobTypes), Set.copyOf(config.hiddenMobs), config.rememberNamedMobs);
        String dimension = key.identifier().toString();

        // The world belongs to the server's thread: everything it holds is read there.
        return server.submit(() -> {
            ServerLevel level = server.getLevel(key);
            if (level == null) return CompletableFuture.completedFuture(new Result(dimension, x(center), z(center), r, 0, List.of()));
            List<Found> found = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            // Chunks with their mobs in the world: as they are now, not as last saved.
            for (Entity entity : level.getAllEntities()) {
                int dx = x(entity.chunkPosition()) - x(center), dz = z(entity.chunkPosition()) - z(center);
                if (dx * dx + dz * dz > r * r || entity.isRemoved()) continue;
                add(entity, level, filter, found, seen);
            }
            // The others, from the save, without loading them.
            Saves storage = Saves.of(level);
            List<CompletableFuture<Void>> reads = new ArrayList<>();
            int[] chunks = {0};
            if (storage != null) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dz * dz > r * r) continue;
                        ChunkPos pos = new ChunkPos(x(center) + dx, z(center) + dz);
                        if (level.areEntitiesLoaded(pack(pos))) continue;
                        chunks[0]++;
                        reads.add(storage.read(pos)
                                .thenAcceptAsync(saved -> saved.ifPresent(tag -> load(storage, tag, level, filter, found, seen)), server)
                                .exceptionally(error -> null));
                    }
                }
            }
            return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenApply(
                    done -> new Result(dimension, x(center), z(center), r, chunks[0], found));
        }).thenCompose(future -> future);
    }

    /** Read on the server's thread: a copy of the lists, never the config itself. */
    private record Filter(Set<String> types, Set<String> hidden, boolean named) {
        boolean wants(Entity entity) {
            if (!MobMemory.rememberable(entity)) return false;
            String type = EntityType.getKey(entity.getType()).toString();
            if (hidden.contains(type) || hidden.contains(FarConfig.wholeMod(type))) return false;
            if (named && entity.hasCustomName()) return true;
            return types.contains(type);
        }
    }

    /** The mobs of one saved chunk, brought up to this version as the game does, never added to the world. */
    private static void load(Saves storage, CompoundTag saved, ServerLevel level, Filter filter,
                             List<Found> found, Set<UUID> seen) {
        try {
            CompoundTag chunk = storage.upgrade(saved);
            EntityNbt.chunkEntities(chunk, level).forEach(root -> {
                add(root, level, filter, found, seen);
                for (Entity passenger : root.getIndirectPassengers()) add(passenger, level, filter, found, seen);
            });
        } catch (RuntimeException e) {
            LivingHorizonClient.LOGGER.debug("Could not read the mobs of a saved chunk", e);
        }
    }

    private static void add(Entity entity, ServerLevel level, Filter filter, List<Found> found, Set<UUID> seen) {
        if (!filter.wants(entity) || !seen.add(entity.getUUID())) return;
        try {
            found.add(new Found(entity.getUUID(), EntityType.getKey(entity.getType()).toString(),
                    entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), EntityNbt.save(entity, level),
                    entity.hasCustomName()));
        } catch (RuntimeException e) {
            seen.remove(entity.getUUID());
        }
    }

    /** Where the server keeps the mobs of the chunks it has not loaded: the entity storage's own file before 1.20.5. */
    private record Saves(
            //? if >=1.20.5 {
            SimpleRegionStorage storage
            //?} else
            /*EntityStorage storage*/
    ) {
        static @Nullable Saves of(ServerLevel level) {
            EntityPersistentStorage<Entity> storage = level.entityManager.permanentStorage;
            if (!(storage instanceof EntityStorage entities)) return null;
            //? if >=1.20.5 {
            return new Saves(entities.simpleRegionStorage);
            //?} else
            /*return new Saves(entities);*/
        }

        CompletableFuture<Optional<CompoundTag>> read(ChunkPos pos) {
            //? if >=1.20.5 {
            return storage.read(pos);
            //?} else
            /*return storage.worker.loadAsync(pos);*/
        }

        /** Brought up to this version as the game does. */
        CompoundTag upgrade(CompoundTag saved) {
            //? if >=1.20.5 {
            return storage.upgradeChunkTag(saved, -1);
            //?} else
            /*return storage.upgradeChunkTag(saved);*/
        }
    }

    // ChunkPos became a record in 26.1: its fields are read through accessors.
    private static int x(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.x();
        *///?} else {
        return pos.x;
        //?}
    }

    private static int z(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.z();
        *///?} else {
        return pos.z;
        //?}
    }

    private static long pack(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }
}
