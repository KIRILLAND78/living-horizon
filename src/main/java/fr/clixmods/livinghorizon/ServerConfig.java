package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a server owner lets the server share with the players who have the mod, in
 * {@code config/livinghorizon-server.json}; read by {@code track/PositionPublisher}. The
 * defaults share everything but spectators and invisible players, as the game itself does.
 *
 * <p>The file is written with every setting the first time the server starts, filled in with
 * those a newer version adds, and read again a few seconds after it is changed ({@link Watched}):
 * no restart. In a world played alone or opened to LAN, the host's game reads the same file.
 *
 * <p>Loaded on a dedicated server: nothing here may reach a class of the client's.
 */
public final class ServerConfig {
    /** The file, in the config folder. */
    public static final String FILE = "livinghorizon-server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    /** The farthest {@link #seedMobRange}. */
    public static final int MAX_SEED_MOB_RANGE = 2048;

    /** Online players shared past the server's view distance. */
    public boolean sharePlayers = true;
    /** How far away, in blocks across the ground, a player is shared; 0 for any distance. */
    public int playerRange = 0;
    /** Players only shared with those of their own team (the game's teams, {@code /team}). */
    public boolean teamsOnly = false;
    /** Players in spectator mode never shared. */
    public boolean hideSpectators = true;
    /** Invisible players never shared. */
    public boolean hideInvisible = true;
    /** Players never shared, by name, whatever case. */
    public List<String> hiddenPlayers = new ArrayList<>();
    /** Dimensions nothing is shared in, by id ({@code minecraft:the_nether}). */
    public List<String> hiddenDimensions = new ArrayList<>();
    /** Players who logged off shared where they left. */
    public boolean shareOfflinePlayers = true;
    /** Remembered mobs shared past the server's view distance. */
    public boolean shareMobs = true;
    /**
     * The animals the world puts in the chunks not generated yet, worked out from the seed,
     * shared with the remembered mobs ({@code track/SeedMobs}).
     */
    public boolean seedMobs = true;
    /** How far from a player those animals are worked out and shared, in blocks: 0 to 2048. */
    public int seedMobRange = 512;
    /**
     * The world's seed sent to the players with the mod, for their game to foresee those animals
     * anywhere itself (where they look, where their far terrain reaches, a scan), where the
     * dimension's generator is the game's own. Off by default: the seed tells everything about
     * the world to whoever has it.
     */
    public boolean shareSeed = false;

    /** {@link #hiddenPlayers}, lower case. */
    public Set<String> hiddenNames() {
        Set<String> names = new HashSet<>();
        for (String name : hiddenPlayers) names.add(name.toLowerCase(Locale.ROOT));
        return names;
    }

    /** {@link #hiddenDimensions}, lower case. */
    public Set<String> hiddenDimensionIds() {
        Set<String> ids = new HashSet<>();
        for (String id : hiddenDimensions) ids.add(id.toLowerCase(Locale.ROOT));
        return ids;
    }

    /** Values a hand made out of range, put back in it. */
    private void tidy() {
        if (playerRange < 0) playerRange = 0;
        seedMobRange = Math.max(0, Math.min(seedMobRange, MAX_SEED_MOB_RANGE));
        if (hiddenPlayers == null) hiddenPlayers = new ArrayList<>();
        if (hiddenDimensions == null) hiddenDimensions = new ArrayList<>();
        hiddenPlayers.removeIf(name -> name == null || name.isBlank());
        hiddenDimensions.removeIf(id -> id == null || id.isBlank());
        hiddenPlayers.replaceAll(String::trim);
        hiddenDimensions.replaceAll(String::trim);
    }

    private JsonElement tree() {
        return GSON.toJsonTree(this);
    }

    /**
     * The file and the settings last read from it: read the first time it is asked, and again
     * when it has changed since. A file that does not read at start is kept aside as
     * {@code .bad} and the defaults are written; one that does not read later, while an owner
     * is editing it, leaves the settings as they were and the file alone.
     */
    public static final class Watched {
        private final Path path;
        private ServerConfig current = new ServerConfig();
        private long seen = Long.MIN_VALUE;
        private boolean loaded;

        public Watched(Path path) {
            this.path = path;
        }

        public ServerConfig get() {
            return current;
        }

        /** Reads the file the first time, and again once changed. True when the settings changed. */
        public boolean refresh() {
            long modified = modified();
            if (loaded && modified == seen) return false;
            boolean first = !loaded;
            loaded = true;
            JsonElement before = current.tree();
            ServerConfig read = read(first);
            if (read != null) current = read;
            seen = modified();
            boolean changed = !current.tree().equals(before);
            if (changed && !first) LOGGER.info("Living Horizon: read the server settings again from {}", path);
            return changed;
        }

        private @Nullable ServerConfig read(boolean first) {
            if (!Files.isRegularFile(path)) {
                ServerConfig defaults = new ServerConfig();
                if (write(defaults)) LOGGER.info("Living Horizon: wrote the server settings to {}", path);
                return defaults;
            }
            JsonElement root;
            ServerConfig read;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonStore.parse(reader);
                read = GSON.fromJson(root, ServerConfig.class);
            } catch (IOException | RuntimeException e) {
                if (!first) {
                    LOGGER.warn("Living Horizon: could not read {}, the server settings stay as they were", path, e);
                    return null;
                }
                JsonStore.keepBad(path, e);
                ServerConfig defaults = new ServerConfig();
                write(defaults);
                return defaults;
            }
            if (read == null) read = new ServerConfig();
            read.tidy();
            if (root.isJsonObject()) {
                JsonObject known = read.tree().getAsJsonObject();
                for (String key : root.getAsJsonObject().keySet()) {
                    if (!known.has(key)) LOGGER.warn("Living Horizon: {} has no setting \"{}\", left out", path, key);
                }
            }
            // Settings a newer version added, or values put back in range: written in.
            if (!read.tree().equals(root)) write(read);
            return read;
        }

        private boolean write(ServerConfig config) {
            try {
                JsonStore.write(path, writer -> GSON.toJson(config, writer));
                return true;
            } catch (IOException e) {
                LOGGER.warn("Living Horizon: could not write {}", path, e);
                return false;
            }
        }

        private long modified() {
            try {
                return Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
            } catch (IOException e) {
                return Long.MIN_VALUE;
            }
        }
    }
}
