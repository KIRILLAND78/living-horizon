package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mod's JSON files are written whole, keep the version of their layout, and are never thrown away. */
class JsonStoreTest {
    @TempDir
    Path folder;

    @Test
    void writesAndReadsBackAVersionedList() throws IOException {
        Path file = folder.resolve("sub").resolve("list.json");
        JsonStore.write(file, writer -> JsonStore.writeVersioned(writer, new Gson(), 3, "items", List.of("a", "b")));
        assertFalse(Files.exists(folder.resolve("sub").resolve("list.json.tmp")));
        JsonElement root = JsonStore.read(file);
        assertNotNull(root);
        JsonStore.Versioned read = JsonStore.unwrap(root, "items");
        assertNotNull(read);
        assertEquals(3, read.version());
        assertEquals(List.of("a", "b"), new Gson().fromJson(read.data(), List.class));
    }

    @Test
    void readsABareListAsVersionZero() {
        JsonStore.Versioned read = JsonStore.unwrap(JsonStore.parse(new StringReader("[1, 2]")), "items");
        assertNotNull(read);
        assertEquals(0, read.version());
        assertEquals(2, read.data().getAsJsonArray().size());
        JsonStore.Versioned empty = JsonStore.unwrap(JsonStore.parse(new StringReader("")), "items");
        assertNotNull(empty);
        assertEquals(0, empty.data().getAsJsonArray().size());
    }

    @Test
    void refusesAnotherShape() {
        assertNull(JsonStore.unwrap(JsonStore.parse(new StringReader("{\"other\": []}")), "items"));
        assertNull(JsonStore.unwrap(JsonStore.parse(new StringReader("{\"items\": [], \"version\": \"x\"}")), "items"));
        assertNull(JsonStore.unwrap(JsonStore.parse(new StringReader("12")), "items"));
    }

    @Test
    void keepsAFileThatDoesNotParse() throws IOException {
        Path file = folder.resolve("broken.json");
        Files.writeString(file, "{\"enabled\": tru");
        assertNull(JsonStore.read(file));
        assertFalse(Files.exists(file));
        assertEquals("{\"enabled\": tru", Files.readString(folder.resolve("broken.json.bad")));
    }

    @Test
    void readsNothingWhenThereIsNoFile() {
        assertNull(JsonStore.read(folder.resolve("missing.json")));
        assertFalse(Files.exists(folder.resolve("missing.json.bad")));
    }

    @Test
    void leavesTheOldFileWhenAWriteFails() throws IOException {
        Path file = folder.resolve("kept.json");
        Files.writeString(file, "[\"old\"]");
        assertThrows(IOException.class, () -> JsonStore.write(file, writer -> {
            writer.write("[\"half");
            throw new IOException("the game stopped");
        }));
        assertEquals("[\"old\"]", Files.readString(file));
    }

    @Test
    void keepsACopyOfANewerFile() throws IOException {
        Path file = folder.resolve("newer.json");
        Files.writeString(file, "{\"version\": 9, \"items\": []}");
        JsonStore.keepNewer(file, 9);
        assertTrue(Files.exists(file));
        assertEquals(Files.readString(file), Files.readString(folder.resolve("newer.json.v9")));
    }

    @Test
    void writesTheVersionFirst() throws IOException {
        StringWriter out = new StringWriter();
        JsonStore.writeVersioned(out, new Gson(), 1, "items", List.of());
        assertEquals("{\"version\":1,\"items\":[]}", out.toString());
    }
}
