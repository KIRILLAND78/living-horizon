package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.compat.DrawnGround.Answer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mob over the void is hidden only on a "no terrain" answer: never a blink while one is awaited. */
class DrawnGroundTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void aDrawnMobStaysDrawnWhileItsChunkIsAsked() {
        DrawnGround.Decisions decisions = new DrawnGround.Decisions();
        assertTrue(decisions.decide("cow", Answer.TERRAIN, 0));
        // Walked out of the game's chunks: the far world has not answered yet.
        for (int frame = 1; frame <= 10; frame++) assertTrue(decisions.decide("cow", Answer.UNKNOWN, frame));
        assertFalse(decisions.decide("cow", Answer.VOID, 11));
    }

    @Test
    void aHiddenMobStaysHiddenWhileItsChunkIsAsked() {
        DrawnGround.Decisions decisions = new DrawnGround.Decisions();
        assertFalse(decisions.decide("cow", Answer.UNKNOWN, 0));
        assertFalse(decisions.decide("cow", Answer.VOID, 1));
        assertFalse(decisions.decide("cow", Answer.UNKNOWN, 2));
        assertTrue(decisions.decide("cow", Answer.TERRAIN, 3));
    }

    @Test
    void theLastAnswerOutlivesOneTurnOfTheMemory() {
        DrawnGround.Decisions decisions = new DrawnGround.Decisions();
        assertTrue(decisions.decide("cow", Answer.TERRAIN, 0));
        decisions.decide("sheep", Answer.TERRAIN, DrawnGround.Decisions.FORGET_NANOS + SECOND);
        assertTrue(decisions.decide("cow", Answer.UNKNOWN, DrawnGround.Decisions.FORGET_NANOS + 2 * SECOND));
    }

    @Test
    void anotherWorldStartsHidden() {
        DrawnGround.Decisions decisions = new DrawnGround.Decisions();
        assertTrue(decisions.decide("cow", Answer.TERRAIN, 0));
        decisions.clear();
        assertFalse(decisions.decide("cow", Answer.UNKNOWN, 1));
    }
}
