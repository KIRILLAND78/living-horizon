package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.GameBoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an impostor key is made of: the values counted in its hash, and the restless ones it
 * learns to leave out. A key that changes while the look does not bakes the figure over and over.
 */
class ImpostorKeyTest {
    private static final String COW = "minecraft:cow";
    private static final long MINUTE = 60_000;

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void numbersAndFlagsAreBits() {
        assertEquals(0L, ImpostorKey.bits(null));
        assertEquals(1L, ImpostorKey.bits(true));
        assertEquals(0L, ImpostorKey.bits(false));
        assertEquals(0xFFL, ImpostorKey.bits((byte) -1));
        assertEquals(0xFFFFFFFFL, ImpostorKey.bits(-1));
        assertNull(ImpostorKey.bits("a name"));
        assertNull(ImpostorKey.bits(2.5f));
    }

    @Test
    void theOrderOfTheValuesDoesNotCount() {
        Map<Integer, Object> one = new LinkedHashMap<>(), other = new LinkedHashMap<>();
        one.put(3, 7);
        one.put(17, "variant");
        other.put(17, "variant");
        other.put(3, 7);
        assertEquals(ImpostorKey.hash(17, one, Map.of()), ImpostorKey.hash(17, other, Map.of()));
    }

    @Test
    void sizesAFewPercentApartShareAStep() {
        assertEquals(ImpostorKey.size(1.2f), ImpostorKey.size(1.22f));
        // An Ice and Fire dragon's box from hatchling to adult: no two ages a quarter apart share one.
        assertNotEquals(ImpostorKey.size(1.2f), ImpostorKey.size(1.5f));
        assertNotEquals(ImpostorKey.size(1.2f), ImpostorKey.size(8.4f));
        assertEquals(ImpostorKey.size(0f), ImpostorKey.size(-1f));
    }

    @Test
    void aValueAtZeroCountsAsUnset() {
        assertEquals(ImpostorKey.hash(17, Map.of(), Map.of()), ImpostorKey.hash(17, Map.of(5, 0), Map.of()));
        assertEquals(ImpostorKey.hash(17, Map.of(), Map.of()), ImpostorKey.hash(17, Map.of(5, false), Map.of()));
        assertNotEquals(ImpostorKey.hash(17, Map.of(), Map.of()), ImpostorKey.hash(17, Map.of(5, 1), Map.of()));
    }

    @Test
    void restlessBitsAreLeftOut() {
        Map<Integer, Long> restless = Map.of(5, 0b100L);
        // Only the restless bit differs: the same key.
        assertEquals(ImpostorKey.hash(17, Map.of(5, 0b001), restless), ImpostorKey.hash(17, Map.of(5, 0b101), restless));
        // Nothing but the restless bit set: as if unset.
        assertEquals(ImpostorKey.hash(17, Map.of(), restless), ImpostorKey.hash(17, Map.of(5, 0b100), restless));
        // Another bit still counts.
        assertNotEquals(ImpostorKey.hash(17, Map.of(5, 0b001), restless), ImpostorKey.hash(17, Map.of(5, 0b011), restless));
    }

    @Test
    void aRestlessValueThatIsNotANumberIsLeftOutWhole() {
        Map<Integer, Long> restless = Map.of(9, -1L);
        assertEquals(ImpostorKey.hash(17, Map.of(9, "one"), restless), ImpostorKey.hash(17, Map.of(9, "two"), restless));
        assertNotEquals(ImpostorKey.hash(17, Map.of(9, "one"), Map.of()), ImpostorKey.hash(17, Map.of(9, "two"), Map.of()));
    }

    @Test
    void oneChangeTeachesNothing() {
        Map<String, Map<Integer, Long>> restless = new HashMap<>();
        Map<Integer, ImpostorKey.Change> changes = new HashMap<>();
        assertFalse(ImpostorKey.learn(restless, COW, Map.of(5, 1), Map.of(5, 3), changes, 0));
        assertTrue(restless.isEmpty());
        assertEquals(0b010L, changes.get(5).bits());
    }

    @Test
    void aValueChangingTwiceWithinAMinuteIsRestless() {
        Map<String, Map<Integer, Long>> restless = new HashMap<>();
        Map<Integer, ImpostorKey.Change> changes = new HashMap<>();
        ImpostorKey.learn(restless, COW, Map.of(5, 0b001), Map.of(5, 0b011), changes, 0);
        assertTrue(ImpostorKey.learn(restless, COW, Map.of(5, 0b011), Map.of(5, 0b111), changes, MINUTE - 1));
        // The bits that changed both times: those flags, and no others.
        assertEquals(0b110L, restless.get(COW).get(5));
        // The same again teaches nothing new.
        assertFalse(ImpostorKey.learn(restless, COW, Map.of(5, 0b111), Map.of(5, 0b011), changes, MINUTE));
    }

    @Test
    void twoChangesFarApartAreNot() {
        Map<String, Map<Integer, Long>> restless = new HashMap<>();
        Map<Integer, ImpostorKey.Change> changes = new HashMap<>();
        ImpostorKey.learn(restless, COW, Map.of(5, 1), Map.of(5, 2), changes, 0);
        assertFalse(ImpostorKey.learn(restless, COW, Map.of(5, 2), Map.of(5, 3), changes, MINUTE));
        assertTrue(restless.isEmpty());
    }

    @Test
    void aValueThatComesAndGoesIsANumberGoingToZero() {
        Map<String, Map<Integer, Long>> restless = new HashMap<>();
        Map<Integer, ImpostorKey.Change> changes = new HashMap<>();
        ImpostorKey.learn(restless, COW, Map.of(), Map.of(5, 4), changes, 0);
        ImpostorKey.learn(restless, COW, Map.of(5, 4), Map.of(), changes, 1000);
        assertEquals(4L, restless.get(COW).get(5));
    }

    @Test
    void aChangingValueThatIsNotANumberIsRestlessWhole() {
        Map<String, Map<Integer, Long>> restless = new HashMap<>();
        Map<Integer, ImpostorKey.Change> changes = new HashMap<>();
        ImpostorKey.learn(restless, COW, Map.of(9, "a"), Map.of(9, "b"), changes, 0);
        ImpostorKey.learn(restless, COW, Map.of(9, "b"), Map.of(9, "c"), changes, 1000);
        assertEquals(-1L, restless.get(COW).get(9));
    }

    @Test
    void thePostureIsPartOfTheKey() {
        ImpostorKey standing = new ImpostorKey(ImpostorKey.PLAYER, 42);
        assertEquals(ImpostorKey.STANDING, standing.posture());
        assertNotEquals(standing, new ImpostorKey(ImpostorKey.PLAYER, 42, ImpostorKey.SEATED));
        assertNotEquals(new ImpostorKey(ImpostorKey.PLAYER, 42, ImpostorKey.LYING),
                new ImpostorKey(ImpostorKey.PLAYER, 42, ImpostorKey.SEATED));
        assertEquals(standing, new ImpostorKey(ImpostorKey.PLAYER, 42, ImpostorKey.STANDING));
    }
}
