package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod's JSON files on the client: written whole or not at all, and never thrown away.
 *
 * <p>A file is written to a temporary one beside it, then moved over it, so a game that stops
 * halfway leaves the old file as it was. One that does not read is moved aside as
 * {@code <name>.bad}, and the log says so, before anything is written in its place. A list is
 * saved with the version of its layout, {@code {"version": 1, "<field>": [...]}}; a bare list,
 * as files were before, reads as version 0.
 */
public final class JsonStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    /** What a file holds, and the version of its layout. */
    public record Versioned(JsonElement data, int version) {
    }

    /** Writes a file's content; may throw. */
    public interface Content {
        void write(Writer writer) throws IOException;
    }

    private JsonStore() {
    }

    /**
     * The file, parsed; null when there is none, and when it does not parse, after it is
     * moved aside as {@code .bad}.
     */
    public static @Nullable JsonElement read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException | RuntimeException e) {
            keepBad(file, e);
            return null;
        }
    }

    /** Parses a whole document; an empty one is JSON null. */
    public static JsonElement parse(Reader reader) {
        return JsonParser.parseReader(reader);
    }

    /**
     * A list saved under {@code field} with its version, or a bare list (version 0); an empty
     * file is an empty list. Null when it is neither: a file of something else.
     */
    public static @Nullable Versioned unwrap(JsonElement root, String field) {
        if (root.isJsonNull()) return new Versioned(new JsonArray(), 0);
        if (root.isJsonArray()) return new Versioned(root, 0);
        if (!root.isJsonObject()) return null;
        JsonObject object = root.getAsJsonObject();
        JsonElement data = object.get(field);
        JsonElement version = object.get("version");
        if (data == null || version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return new Versioned(data, version.getAsInt());
    }

    /** Writes {@code data} under {@code field}, with the version of its layout. */
    public static void writeVersioned(Writer writer, Gson gson, int version, String field, Object data) throws IOException {
        JsonWriter json = gson.newJsonWriter(writer);
        json.beginObject();
        json.name("version").value(version);
        json.name(field);
        gson.toJson(data, data.getClass(), json);
        json.endObject();
        json.flush();
    }

    /**
     * Writes the file whole, or leaves it as it was: into {@code <name>.tmp}, then moved over
     * it. Throws when it could not.
     */
    public static void write(Path file, Content content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path written = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(written, StandardCharsets.UTF_8)) {
            content.write(writer);
        }
        try {
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A file that does not read: moved aside as {@code <name>.bad}, over an older one, and said in the log. */
    public static void keepBad(Path file, Throwable why) {
        Path bad = file.resolveSibling(file.getFileName() + ".bad");
        try {
            Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Living Horizon: could not read {}, kept it as {} and started from an empty one", file,
                    bad.getFileName(), why);
        } catch (IOException e) {
            LOGGER.warn("Living Horizon: could not read {}, nor keep it aside", file, why);
        }
    }

    /**
     * A file written by a newer version of the mod: read as well as this one can, and copied to
     * {@code <name>.v<version>} first, since saving it again leaves out what this one does not know.
     */
    public static void keepNewer(Path file, int version) {
        Path copy = file.resolveSibling(file.getFileName() + ".v" + version);
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Living Horizon: {} was written by a newer version of the mod, kept a copy as {}", file,
                    copy.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Living Horizon: {} was written by a newer version of the mod", file, e);
        }
    }
}
