package fr.clixmods.livinghorizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void keepsTheLastValueTheAverageAndTheWorstOfFiveSeconds() {
        Profile.Stat stat = new Profile.Stat();
        assertFalse(stat.seen());
        stat.record(100_000_000L, -1, 10 * SECOND);
        stat.record(2_000_000L, -1, 10 * SECOND + 1);
        assertTrue(stat.seen());
        assertEquals(2_000_000L, stat.last());
        assertEquals(100_000_000L, stat.worst(10 * SECOND + 2));
        // The spike is still the worst four seconds later, and gone after five.
        assertEquals(100_000_000L, stat.worst(14 * SECOND));
        assertEquals(0L, stat.worst(15 * SECOND));
        // The average moves slowly: a spike once in a while barely shows in it.
        assertTrue(stat.average() > 2_000_000L && stat.average() < 100_000_000L);
    }

    @Test
    void aSecondSlotIsReusedOnlyAfterItsTimeHasPassed() {
        Profile.Stat stat = new Profile.Stat();
        stat.record(9_000_000L, -1, 3 * SECOND);
        stat.record(1_000_000L, -1, 8 * SECOND);
        assertEquals(1_000_000L, stat.worst(8 * SECOND));
    }

    @Test
    void saysHowLongASectionHasBeenIdle() {
        Profile.Stat stat = new Profile.Stat();
        assertEquals(0, stat.idleSeconds(100 * SECOND));
        stat.record(1, -1, 10 * SECOND);
        assertEquals(0, stat.idleSeconds(10 * SECOND + 1));
        assertEquals(7, stat.idleSeconds(17 * SECOND));
    }

    @Test
    void countsCallsPerFrameAndRemembersBytes() {
        Profile.Stat stat = new Profile.Stat();
        stat.record(1, 4096, SECOND);
        stat.record(1, -1, SECOND);
        stat.record(1, -1, SECOND);
        assertEquals(0, stat.perFrame());
        stat.frame();
        assertEquals(3, stat.perFrame());
        assertEquals(4096, stat.bytes());
        stat.frame();
        assertEquals(0, stat.perFrame());
        assertEquals(3, stat.calls());
    }

    @Test
    void everySectionIsNamedOnceInTheLanguageFiles() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Profile.Section section : Profile.Section.values()) {
            assertTrue(ids.add(section.id()), section.id());
            assertEquals("livinghorizon.profile." + section.id(), section.langKey());
        }
    }

    @Test
    void beginAndEndFeedTheSection() {
        Profile.Section section = Profile.Section.SAVE_MOBS;
        section.cpu().reset();
        long started = Profile.begin(section);
        Profile.end(section, started, 123);
        assertTrue(section.cpu().seen());
        assertEquals(123, section.cpu().bytes());
        section.cpu().reset();
    }
}
