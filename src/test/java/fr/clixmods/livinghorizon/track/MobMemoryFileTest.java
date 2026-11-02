package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file of the remembered mobs keeps its version, reads the older layout, and is kept aside when damaged. */
class MobMemoryFileTest {
    @TempDir
    Path folder;

    private static MobMemory.Remembered horse() {
        MobMemory.Remembered mob = new MobMemory.Remembered();
        mob.id = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
        mob.type = "minecraft:horse";
        mob.dimension = "minecraft:overworld";
        mob.world = 1234L;
        mob.x = 1.5;
        mob.y = 70;
        mob.z = -2.5;
        mob.yaw = 90f;
        mob.nbt = "{Variant:3}";
        mob.seenAt = 42;
        mob.named = true;
        return mob;
    }

    @Test
    void roundTrips() throws IOException {
        Path file = folder.resolve("server.json");
        StringWriter out = new StringWriter();
        MobMemory.write(List.of(horse()), out);
        Files.writeString(file, out.toString());
        assertTrue(out.toString().startsWith("{\"version\":" + MobMemory.VERSION + ","));
        List<MobMemory.Remembered> read = MobMemory.read(file);
        assertEquals(1, read.size());
        MobMemory.Remembered mob = read.get(0);
        MobMemory.Remembered horse = horse();
        assertEquals(horse.id, mob.id);
        assertEquals(horse.type, mob.type);
        assertEquals(horse.world, mob.world);
        assertEquals(horse.x, mob.x);
        assertEquals(horse.yaw, mob.yaw);
        assertEquals(horse.nbt, mob.nbt);
        assertEquals(horse.seenAt, mob.seenAt);
        assertTrue(mob.named);
        assertTrue(Float.isNaN(mob.head));
    }

    @Test
    void readsTheBareList() throws IOException {
        Path file = folder.resolve("old.json");
        Files.writeString(file, "[{\"id\": \"0f8fad5b-d9cb-469f-a165-70867728950e\", \"type\": \"minecraft:cow\","
                + " \"dimension\": \"minecraft:overworld\", \"world\": 1, \"x\": 1, \"y\": 2, \"z\": 3}, {\"x\": 1}, null]");
        List<MobMemory.Remembered> read = MobMemory.read(file);
        assertEquals(1, read.size());
        assertEquals("minecraft:cow", read.get(0).type);
    }

    @Test
    void keepsADamagedFileAside() throws IOException {
        Path file = folder.resolve("broken.json");
        Files.writeString(file, "{\"version\": 1, \"mobs\": [{\"id\": 12");
        assertEquals(List.of(), MobMemory.read(file));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(folder.resolve("broken.json.bad")));

        Path other = folder.resolve("other.json");
        Files.writeString(other, "{\"players\": []}");
        assertEquals(List.of(), MobMemory.read(other));
        assertTrue(Files.exists(folder.resolve("other.json.bad")));
    }
}
