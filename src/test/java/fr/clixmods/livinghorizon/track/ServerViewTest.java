package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Entities come no farther than the server's view distance, whatever the render distance says. */
class ServerViewTest {
    @Test
    void cutToTheServersViewDistance() {
        // Bobby: a render distance of 32 on a server that sends 6.
        assertEquals(6, ServerView.chunks(32, 6));
    }

    @Test
    void theRenderDistanceWhenSmaller() {
        assertEquals(8, ServerView.chunks(8, 12));
    }

    @Test
    void theRenderDistanceBeforeTheServerSays() {
        assertEquals(12, ServerView.chunks(12, 0));
    }
}
