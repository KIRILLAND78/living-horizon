package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A mob's sheet saved on disk reads back the same, when and where it was baked included, a file of
 * the format before still reads, and a damaged one never asks for more than a sheet.
 */
class ImpostorDiskTest {
    /** The levels of a sheet of scale 1, each pixel its own number. */
    private static int[][] sheet() {
        int[][] levels = new int[ImpostorAtlas.MIPS][];
        for (int level = 0; level < levels.length; level++) {
            int tile = ImpostorViews.TILE >> level;
            levels[level] = new int[tile * ImpostorViews.VIEWS * tile];
            int shift = level;
            Arrays.setAll(levels[level], i -> i * 31 + shift);
        }
        return levels;
    }

    private static final long BAKED_AT = 1_790_000_000_000L;
    private static final String WORLD = "New World";

    private static byte[] encoded(double worldSize, int scale, int[][] levels) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            ImpostorDisk.encode(out, worldSize, scale, levels, BAKED_AT, WORLD);
        }
        return bytes.toByteArray();
    }

    /** A sheet as format 2 wrote it, without when or where it was baked. */
    private static byte[] encodedBefore(double worldSize, int scale, int[][] levels) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x4C484953);
            out.writeInt(2);
            out.writeDouble(worldSize);
            out.writeInt(scale);
            out.writeInt(levels.length);
            for (int[] level : levels) {
                out.writeInt(level.length);
                for (int pixel : level) out.writeInt(pixel);
            }
        }
        return bytes.toByteArray();
    }

    private static ImpostorDisk.Stored decoded(byte[] data) throws IOException {
        return ImpostorDisk.decode(new DataInputStream(new ByteArrayInputStream(data)));
    }

    @Test
    void roundTrips() throws IOException {
        int[][] levels = sheet();
        ImpostorDisk.Stored stored = decoded(encoded(2.5, ImpostorDisk.SCALE, levels));
        assertNotNull(stored);
        assertEquals(2.5, stored.worldSize());
        assertEquals(levels.length, stored.levels().length);
        for (int level = 0; level < levels.length; level++) assertArrayEquals(levels[level], stored.levels()[level]);
        assertEquals(BAKED_AT, stored.bakedAt());
        assertEquals(WORLD, stored.world());
    }

    @Test
    void readsTheFormatBefore() throws IOException {
        int[][] levels = sheet();
        ImpostorDisk.Stored stored = decoded(encodedBefore(2.5, 1, levels));
        assertNotNull(stored);
        assertEquals(2.5, stored.worldSize());
        for (int level = 0; level < levels.length; level++) assertArrayEquals(levels[level], stored.levels()[level]);
        assertEquals(0, stored.bakedAt());
        assertEquals("", stored.world());
    }

    @Test
    void ignoresAnotherFormat() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        data[7]++;
        assertNull(decoded(data));
    }

    @Test
    void refusesALengthItCannotHave() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        // The first level's length, after the magic, the format, the date, the world, the size,
        // the scale and the count.
        int at = 4 + 4 + 8 + 2 + WORLD.length() + 8 + 4 + 4;
        data[at] = 0x7F;
        assertThrows(IOException.class, () -> decoded(data));
    }

    @Test
    void refusesAScaleOrCountItCannotHave() throws IOException {
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE - 1, sheet())));
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE + 1, sheet())));
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE, Arrays.copyOf(sheet(), ImpostorAtlas.MIPS - 1))));
    }

    @Test
    void stopsAtTheEndOfACutFile() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        assertThrows(EOFException.class, () -> decoded(Arrays.copyOf(data, data.length / 2)));
    }

    /** A fingerprint's folder with one sheet in it, chosen {@code ago} milliseconds ago. */
    private static Path folder(Path base, String name, long ago) throws IOException {
        Path folder = base.resolve(name);
        Files.createDirectories(folder.resolve("minecraft+cow"));
        Files.write(folder.resolve("minecraft+cow").resolve("1.bin"), new byte[]{1});
        assertTrue(folder.toFile().setLastModified(System.currentTimeMillis() - ago));
        return folder;
    }

    @Test
    void keepsTheFoldersChosenLast(@TempDir Path base) throws IOException {
        Path current = folder(base, "current", 0);
        Path last = folder(base, "last", 1000);
        Path before = folder(base, "before", 2000);
        Path oldest = folder(base, "oldest", 3000);
        ImpostorDisk.tidy(base, current, ImpostorDisk.FOLDERS, 10);
        assertTrue(Files.isDirectory(current));
        assertTrue(Files.isDirectory(last));
        assertTrue(Files.isDirectory(before));
        assertFalse(Files.exists(oldest));
    }

    @Test
    void keepsTheSheetsMetLast(@TempDir Path base) throws IOException {
        Path current = base.resolve("current").resolve("minecraft+cow");
        Files.createDirectories(current);
        for (int sheet = 0; sheet < 5; sheet++) {
            Path file = current.resolve(sheet + ".bin");
            Files.write(file, new byte[]{1});
            assertTrue(file.toFile().setLastModified(System.currentTimeMillis() - (5 - sheet) * 1000L));
        }
        ImpostorDisk.tidy(base, base.resolve("current"), ImpostorDisk.FOLDERS, 3);
        assertFalse(Files.exists(current.resolve("0.bin")));
        assertFalse(Files.exists(current.resolve("1.bin")));
        for (int sheet = 2; sheet < 5; sheet++) assertTrue(Files.exists(current.resolve(sheet + ".bin")));
    }
}
