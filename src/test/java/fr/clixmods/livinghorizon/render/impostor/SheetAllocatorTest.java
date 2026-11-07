package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rows are handed out, given way and given back as the settings and the clock say, with no game running. */
class SheetAllocatorTest {
    private record Key(String type, int look) {
    }

    private static final String COW = "minecraft:cow", PIG = "minecraft:pig";

    /** A page texture: its number and height, and whether it was given back. */
    private static final class Page {
        final int number, height;
        boolean released;

        Page(int number, int height) {
            this.number = number;
            this.height = height;
        }
    }

    private long now = 1_000_000L;
    private int mobRows = 1000, playerRows = 1000;
    private final List<Page> opened = new ArrayList<>();
    private final List<Key> dropped = new ArrayList<>();
    private final SheetAllocator<Key, Page> allocator = new SheetAllocator<>(() -> now, Key::type,
            players -> players ? playerRows : mobRows,
            new SheetAllocator.Textures<>() {
                @Override
                public Page open(int number, int width, int height) {
                    assertEquals(ImpostorViews.TILE * ImpostorViews.VIEWS, width);
                    Page page = new Page(number, height);
                    opened.add(page);
                    return page;
                }

                @Override
                public void release(int number, Page texture) {
                    assertEquals(number, texture.number);
                    texture.released = true;
                }
            },
            dropped::add);

    /** Places a sheet for {@code key}: its index. */
    private int place(Key key) {
        int index = allocator.allocate(key);
        if (index >= 0) assertNotNull(allocator.put(key, index, 2.0, ImpostorAtlas.FEET));
        return index;
    }

    @Test
    void eachKindHasPagesOfItsOwn() {
        int cow = place(new Key(COW, 0));
        int pig = place(new Key(PIG, 0));
        assertEquals(0, cow / SheetAllocator.ROWS);
        assertEquals(1, pig / SheetAllocator.ROWS);
        // The cow's second look goes on the cow's page.
        assertEquals(0, place(new Key(COW, 1)) / SheetAllocator.ROWS);
        assertEquals(2, opened.size());
    }

    @Test
    void pagesDoubleUpToTheLargest() {
        Map<Integer, Integer> rowsByPage = new HashMap<>();
        for (int look = 0; look < 4 + 8 + 16 + 32 + 32; look++) {
            int index = place(new Key(COW, look));
            assertTrue(index >= 0);
        }
        for (Page page : opened) rowsByPage.put(page.number, page.height / ImpostorViews.TILE);
        assertEquals(Map.of(0, SheetAllocator.FIRST_ROWS, 1, 8, 2, 16, 3, SheetAllocator.ROWS, 4, SheetAllocator.ROWS),
                rowsByPage);
        assertEquals(SheetAllocator.FIRST_ROWS << SheetAllocator.DOUBLINGS, SheetAllocator.ROWS);
    }

    @Test
    void theOldestSheetGivesWayOnlyOnceUnused() {
        mobRows = SheetAllocator.FIRST_ROWS;
        Key first = new Key(COW, 0);
        for (int look = 0; look < SheetAllocator.FIRST_ROWS; look++) place(new Key(COW, look));
        // Full, and every sheet was used just now: the next one waits.
        assertEquals(-1, allocator.allocate(new Key(COW, 99)));
        assertTrue(dropped.isEmpty());

        now += SheetAllocator.IN_USE + 1;
        // The others drawn again: the first one is the oldest, and unused.
        for (int look = 1; look < SheetAllocator.FIRST_ROWS; look++) assertNotNull(allocator.get(new Key(COW, look)));
        int index = allocator.allocate(new Key(COW, 99));
        assertTrue(index >= 0);
        assertEquals(List.of(first), dropped);
        assertFalse(allocator.contains(first));
        assertEquals(SheetAllocator.FIRST_ROWS - 1, allocator.size());
    }

    @Test
    void anotherKindIsNeverThrownOutForRoom() {
        mobRows = SheetAllocator.FIRST_ROWS;
        for (int look = 0; look < SheetAllocator.FIRST_ROWS; look++) place(new Key(COW, look));
        now += SheetAllocator.IN_USE + 1;
        // The pig has no page and no room for one; the cows unused do not give theirs.
        assertEquals(-1, allocator.allocate(new Key(PIG, 0)));
        assertTrue(dropped.isEmpty());
    }

    @Test
    void playersAndMobsNeverTakeEachOthersRoom() {
        mobRows = SheetAllocator.FIRST_ROWS;
        playerRows = SheetAllocator.FIRST_ROWS;
        for (int look = 0; look < SheetAllocator.FIRST_ROWS; look++) place(new Key(COW, look));
        assertEquals(SheetAllocator.FIRST_ROWS, allocator.taken(false));
        assertEquals(0, allocator.taken(true));
        // The mobs are full; the players still have all of theirs.
        assertTrue(place(new Key(SheetAllocator.PLAYER, 0)) >= 0);
        assertEquals(SheetAllocator.FIRST_ROWS, allocator.taken(true));
        assertEquals(-1, allocator.allocate(new Key(PIG, 0)));
    }

    @Test
    void unusedPlayersAreThrownAwayAndTheirEmptyPagesGivenBack() {
        Key player = new Key(SheetAllocator.PLAYER, 0), cow = new Key(COW, 0);
        place(player);
        place(cow);
        Page playerPage = opened.get(0);

        now += SheetAllocator.PLAYER_KEEP + SheetAllocator.SWEEP_EVERY;
        allocator.sweep();
        assertEquals(List.of(player), dropped);
        assertNull(allocator.get(player));
        assertTrue(playerPage.released);
        // The mobs keep theirs, however long unused.
        assertNotNull(allocator.get(cow));
        assertFalse(opened.get(1).released);
        assertEquals(0, allocator.pageWidth(0));
        assertEquals(0, allocator.taken(true));

        // The page number given back is taken again first.
        assertEquals(0, place(new Key(SheetAllocator.PLAYER, 1)) / SheetAllocator.ROWS);
    }

    @Test
    void aPlayerSeenLatelyIsKept() {
        Key player = new Key(SheetAllocator.PLAYER, 0);
        place(player);
        now += SheetAllocator.PLAYER_KEEP - 1;
        allocator.sweep();
        assertNotNull(allocator.get(player));
        assertFalse(opened.get(0).released);
    }

    @Test
    void aRowGivenBackIsHandedOutAgain() {
        int index = allocator.allocate(new Key(COW, 0));
        allocator.free(index);
        assertEquals(index, allocator.allocate(new Key(COW, 1)));
    }

    @Test
    void clearingGivesEveryPageBack() {
        place(new Key(COW, 0));
        place(new Key(SheetAllocator.PLAYER, 0));
        allocator.clear();
        assertTrue(opened.stream().allMatch(page -> page.released));
        assertEquals(0, allocator.pages());
        assertEquals(0, allocator.size());
    }
}
