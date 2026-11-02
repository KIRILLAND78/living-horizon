package fr.clixmods.livinghorizon.track;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The players a world saved before the mod was there to see them log off: where each one
 * was, the way they faced and what they wore, from the world's {@code playerdata} folder, and
 * their names from the server's {@code usercache.json}. Read once, when the server first
 * shares, so that a server that had players before the mod shows them as logged off at once.
 *
 * <p>Loaded on a dedicated server: nothing here may reach a class of the client's.
 */
final class WorldPlayers {
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    /** The game mode a player file saves for spectator mode. */
    private static final int SPECTATOR = 3;

    private WorldPlayers() {
    }

    /**
     * Every player the world saved whose name is known, as logged off; those saved in spectator
     * mode left out when {@code leaveOutSpectators}, since the server hides them.
     */
    static List<PositionsMessage.Player> read(MinecraftServer server, boolean leaveOutSpectators) {
        return read(server.getWorldPath(LevelResource.PLAYER_DATA_DIR), server.getWorldPath(LevelResource.ROOT),
                leaveOutSpectators);
    }

    /** The same, from a world's folder and its players' folder, spectators kept. */
    static List<PositionsMessage.Player> read(Path folder, Path world) {
        return read(folder, world, false);
    }

    /** The same, from a world's folder and its players' folder. */
    static List<PositionsMessage.Player> read(Path folder, Path world, boolean leaveOutSpectators) {
        if (!Files.isDirectory(folder)) return List.of();
        Map<UUID, String> names = names(world);
        if (names.isEmpty()) return List.of();
        List<Path> files;
        try (Stream<Path> listed = Files.list(folder)) {
            files = new ArrayList<>(listed.filter(path -> path.getFileName().toString().endsWith(".dat")).toList());
        } catch (IOException e) {
            LOGGER.warn("Could not list the players of this world", e);
            return List.of();
        }
        // Every one of them, those who played last first.
        files.sort(Comparator.comparingLong(WorldPlayers::modified).reversed());
        List<PositionsMessage.Player> players = new ArrayList<>();
        for (Path file : files) {
            String id = file.getFileName().toString();
            String name;
            try {
                name = names.get(UUID.fromString(id.substring(0, id.length() - ".dat".length())));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (name == null) continue;
            try {
                CompoundTag tag = readTag(file);
                if (leaveOutSpectators && number(tag, "playerGameType") == SPECTATOR) continue;
                PositionsMessage.Player player = player(name, tag, modified(file));
                if (player != null) players.add(player);
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Could not read the saved player {}", file, e);
            }
        }
        return players;
    }

    /** A saved player: where they were, the way they faced, what they wore. */
    private static PositionsMessage.@Nullable Player player(String name, CompoundTag tag, long lastSeen) {
        ListTag pos = list(tag, "Pos", Tag.TAG_DOUBLE);
        if (pos.size() < 3) return null;
        ListTag rotation = list(tag, "Rotation", Tag.TAG_FLOAT);
        float yaw = rotation.isEmpty() ? 0 : floatAt(rotation, 0);
        return new PositionsMessage.Player(name, doubleAt(pos, 0), doubleAt(pos, 1), doubleAt(pos, 2), yaw,
                dimension(text(tag, "Dimension")), false, false, null, null,
                new PositionsMessage.Look(null, null, gear(tag)), lastSeen, false);
    }

    /** Hands and armour, in {@link Gear#SLOTS}' order. */
    private static List<String> gear(CompoundTag tag) {
        // Hands and armour by slot: the selected hotbar slot, the off hand, the four armour slots.
        String[] gear = {"", "", "", "", "", ""};
        int selected = number(tag, "SelectedItemSlot");
        ListTag inventory = list(tag, "Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag item = compoundAt(inventory, i);
            if (item == null) continue;
            int slot = slotOf(item);
            int index = slot == selected ? 0 : slot == -106 ? 1 : slot >= 100 && slot <= 103 ? 2 + slot - 100 : -1;
            if (index >= 0) gear[index] = itemText(item);
        }
        // From 1.21.5 the armour and the off hand are saved apart, by name.
        CompoundTag equipment = compound(tag, "equipment");
        if (equipment != null) {
            String[] keys = {null, "offhand", "feet", "legs", "chest", "head"};
            for (int i = 1; i < keys.length; i++) {
                CompoundTag item = compound(equipment, keys[i]);
                if (item != null) gear[i] = itemText(item);
            }
        }
        return List.of(gear);
    }

    /** An item as {@link Gear} keeps it: the saved item, without its inventory slot. */
    private static String itemText(CompoundTag item) {
        CompoundTag copy = item.copy();
        copy.remove("Slot");
        String text = copy.toString();
        return text.length() > Gear.MAX_ITEM ? "" : text;
    }

    /** The dimension's id, as the game saved it; a save without one is in the overworld. */
    private static String dimension(String key) {
        return key.isEmpty() ? "minecraft:overworld" : key;
    }

    /** Names by id, from the server's cache of the profiles it has seen. */
    private static Map<UUID, String> names(Path world) {
        Map<UUID, String> names = new HashMap<>();
        // Beside a dedicated server's world, or in the game's folder above the saves.
        Path parent = world.toAbsolutePath().normalize().getParent();
        List<Path> places = new ArrayList<>();
        if (parent != null) {
            places.add(parent.resolve("usercache.json"));
            if (parent.getParent() != null) places.add(parent.getParent().resolve("usercache.json"));
        }
        places.add(Path.of("usercache.json").toAbsolutePath());
        for (Path place : places) {
            if (!Files.isRegularFile(place)) continue;
            try (Reader reader = Files.newBufferedReader(place)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) continue;
                JsonArray entries = root.getAsJsonArray();
                for (JsonElement entry : entries) {
                    if (!entry.isJsonObject()) continue;
                    JsonObject profile = entry.getAsJsonObject();
                    if (!profile.has("name") || !profile.has("uuid")) continue;
                    try {
                        names.putIfAbsent(UUID.fromString(profile.get("uuid").getAsString()), profile.get("name").getAsString());
                    } catch (IllegalArgumentException ignored) {
                        // Not an id.
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Could not read {}", place, e);
            }
        }
        return names;
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    // --- Saved data, as each game version reads it ---------------------------------------

    private static CompoundTag readTag(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            //? if >=1.20.3 {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            //?} else
            /*return NbtIo.readCompressed(in);*/
        }
    }

    private static ListTag list(CompoundTag tag, String key, int type) {
        //? if >=1.21.5 {
        return tag.getListOrEmpty(key);
        //?} else
        /*return tag.getList(key, type);*/
    }

    private static @Nullable CompoundTag compound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getCompound(key).orElse(null);
        //?} else
        /*return tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;*/
    }

    private static @Nullable CompoundTag compoundAt(ListTag list, int i) {
        //? if >=1.21.5 {
        return list.getCompound(i).orElse(null);
        //?} else
        /*return list.getCompound(i);*/
    }

    private static double doubleAt(ListTag list, int i) {
        //? if >=1.21.5 {
        return list.getDoubleOr(i, 0);
        //?} else
        /*return list.getDouble(i);*/
    }

    private static float floatAt(ListTag list, int i) {
        //? if >=1.21.5 {
        return list.getFloatOr(i, 0);
        //?} else
        /*return list.getFloat(i);*/
    }

    private static String text(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getStringOr(key, "");
        //?} else
        /*return tag.getString(key);*/
    }

    private static int number(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, -1);
        //?} else
        /*return tag.contains(key) ? tag.getInt(key) : -1;*/
    }

    private static int slotOf(CompoundTag item) {
        //? if >=1.21.5 {
        return item.getByteOr("Slot", (byte) -1);
        //?} else
        /*return item.contains("Slot") ? item.getByte("Slot") : -1;*/
    }
}
