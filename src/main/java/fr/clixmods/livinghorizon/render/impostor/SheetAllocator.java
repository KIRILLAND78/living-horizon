package fr.clixmods.livinghorizon.render.impostor;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Where the sheets go: the pages of each kind of figure, their rows, and which sheet gives way
 * when the pages hold all the settings allow. It asks the game nothing: the clock, the budgets
 * and the page textures are handed in, so that the unit tests run it alone.
 *
 * <p>A row is named by an index, {@code page * ROWS + row}. Each kind of figure has
 * pages of its own, each new one twice as tall as its last up to {@link #ROWS}. The mobs and
 * the players each have a budget of rows ({@link Budget}): neither takes the other's room.
 *
 * @param <K> a figure's key
 * @param <T> a page's texture
 */
final class SheetAllocator<K, T> {
    /** Sheets down a kind of figure's first page, and down its largest. */
    static final int FIRST_ROWS = 4, ROWS = 32;
    /** How many times a kind's pages double, from {@link #FIRST_ROWS} to {@link #ROWS}: 4, 8, 16, 32. */
    static final int DOUBLINGS = 3;

    /**
     * Milliseconds a sheet is kept after it was last drawn or asked for, however full the
     * pages: longer than the second between two rounds of asking for every figure on hand.
     * Giving way sooner, a crowd larger than the pages would bake its sheets over and over,
     * each new one throwing out another still in use.
     */
    static final long IN_USE = 10_000L;
    /** Milliseconds a player's sheet is kept once nobody draws or asks for it. */
    static final long PLAYER_KEEP = 60_000L;
    /** Milliseconds between two looks for players' sheets to throw away. */
    static final long SWEEP_EVERY = 5_000L;

    /** The kind of the players' sheets (players, riders with what they ride). */
    static final String PLAYER = ImpostorKey.PLAYER;

    /** Makes and gives back the textures of the pages. */
    interface Textures<T> {
        /** A new, empty page texture, filed under its number. */
        T open(int number, int width, int height);

        /** A page given back: its texture goes. */
        void release(int number, T texture);
    }

    /** Rows the pages of the players, or of the mobs, may hold in all. */
    interface Budget {
        int rows(boolean players);
    }

    /** The pages of one kind of figure, each with the rows not yet taken. */
    private final class Page {
        final String type;
        final int rows;
        final T texture;
        final Deque<Integer> free = new ArrayDeque<>();

        Page(String type, int rows, T texture) {
            this.type = type;
            this.rows = rows;
            this.texture = texture;
            for (int row = 0; row < rows; row++) free.add(row);
        }
    }

    private final LongSupplier clock;
    private final Function<K, String> kind;
    private final Budget budget;
    private final Textures<T> textures;
    private final Consumer<K> dropped;

    /** Least recently drawn or asked for first. */
    private final Map<K, ImpostorAtlas.Sheet> sheets = new LinkedHashMap<>(64, 0.75f, true);
    /** When each sheet was last drawn or asked for, in milliseconds: written over in place. */
    private final Map<K, long[]> used = new HashMap<>();
    /** The pages by number, null where a player page was given back. */
    private final List<@Nullable Page> pages = new ArrayList<>();
    private long lastSweep;

    /**
     * @param clock    milliseconds, for how long a sheet has gone unused
     * @param kind     the kind of figure a key names
     * @param budget   the rows the settings allow
     * @param textures makes and gives back the pages' textures
     * @param dropped  told of every sheet thrown away to make room, or unused
     */
    SheetAllocator(LongSupplier clock, Function<K, String> kind, Budget budget, Textures<T> textures, Consumer<K> dropped) {
        this.clock = clock;
        this.kind = kind;
        this.budget = budget;
        this.textures = textures;
        this.dropped = dropped;
    }

    /** Whether a kind of figure is filed with the players'. */
    static boolean player(String type) {
        return PLAYER.equals(type);
    }

    /** The sheet of a figure, if placed, counted as a use. */
    ImpostorAtlas.@Nullable Sheet get(K key) {
        ImpostorAtlas.Sheet sheet = sheets.get(key);
        if (sheet != null) touch(key);
        return sheet;
    }

    boolean contains(K key) {
        return sheets.containsKey(key);
    }

    /** The sheet of {@code key} is drawn or asked for now. */
    void touch(K key) {
        long[] when = used.get(key);
        if (when == null) used.put(key, when = new long[1]);
        when[0] = clock.getAsLong();
    }

    int size() {
        return sheets.size();
    }

    /** Every sheet, without counting as a use. */
    void forEach(BiConsumer<K, ImpostorAtlas.Sheet> action) {
        sheets.forEach(action);
    }

    /**
     * A free row on a page of the figure's kind, as an index: a new page when its pages are
     * full, twice as tall as its last. When the pages of the mobs or of the players hold all
     * they may, the sheet of the same kind unused for longest gives way, unless it is still in use ({@link #IN_USE}): then -1, and
     * the figure waits, drawn as a model.
     */
    int allocate(K key) {
        String type = kind.apply(key);
        int pagesOfType = 0;
        for (int number = 0; number < pages.size(); number++) {
            Page page = pages.get(number);
            if (page == null || !page.type.equals(type)) continue;
            pagesOfType++;
            if (!page.free.isEmpty()) return number * ROWS + page.free.pop();
        }
        int rows = Math.min(ROWS, FIRST_ROWS << Math.min(pagesOfType, DOUBLINGS));
        boolean player = player(type);
        if (taken(player) + rows <= budget.rows(player)) {
            int number = open(type, rows);
            return number * ROWS + pages.get(number).free.pop();
        }
        Iterator<Map.Entry<K, ImpostorAtlas.Sheet>> oldest = sheets.entrySet().iterator();
        while (oldest.hasNext()) {
            Map.Entry<K, ImpostorAtlas.Sheet> entry = oldest.next();
            if (!kind.apply(entry.getKey()).equals(type)) continue;
            // Least recently used first: the ones after it are in use too.
            if (inUse(entry.getKey(), IN_USE)) return -1;
            oldest.remove();
            drop(entry.getKey());
            return entry.getValue().index();
        }
        return -1;
    }

    /** A row back to its page, its sheet not placed, thrown away or shared with another. */
    void free(int index) {
        Page page = pages.get(index / ROWS);
        if (page != null) page.free.push(index % ROWS);
    }

    /** The sheet of a figure, written into its row: null when its page was given back meanwhile. */
    ImpostorAtlas.@Nullable Sheet put(K key, int index, double worldSize, float feet) {
        Page page = page(index / ROWS);
        if (page == null) return null;
        ImpostorAtlas.Sheet sheet = new ImpostorAtlas.Sheet(index, page.rows, worldSize, feet);
        sheets.put(key, sheet);
        touch(key);
        return sheet;
    }

    /** The texture of the page a row is on, null when it was given back. */
    @Nullable T texture(int index) {
        Page page = page(index / ROWS);
        return page == null ? null : page.texture;
    }

    /** Page numbers in use run below this. */
    int pages() {
        return pages.size();
    }

    /** Pixels across a page, 0 for a number not in use. */
    int pageWidth(int number) {
        Page page = page(number);
        return page == null ? 0 : ImpostorViews.TILE * ImpostorViews.VIEWS;
    }

    /** Pixels down a page, 0 for a number not in use. */
    int pageHeight(int number) {
        Page page = page(number);
        return page == null ? 0 : page.rows * ImpostorViews.TILE;
    }

    /** Rows of the pages of the players, or of the mobs. */
    int taken(boolean players) {
        int rows = 0;
        for (Page page : pages) {
            if (page != null && player(page.type) == players) rows += page.rows;
        }
        return rows;
    }

    /**
     * Every {@link #SWEEP_EVERY}, throws away the players' sheets unused for {@link #PLAYER_KEEP},
     * and gives back the players' pages left empty: they come and go, and wear other skins.
     */
    void sweep() {
        long now = clock.getAsLong();
        if (now - lastSweep < SWEEP_EVERY) return;
        lastSweep = now;
        Iterator<Map.Entry<K, ImpostorAtlas.Sheet>> all = sheets.entrySet().iterator();
        while (all.hasNext()) {
            Map.Entry<K, ImpostorAtlas.Sheet> entry = all.next();
            if (!player(kind.apply(entry.getKey())) || inUse(entry.getKey(), PLAYER_KEEP)) continue;
            all.remove();
            drop(entry.getKey());
            free(entry.getValue().index());
        }
        for (int number = 0; number < pages.size(); number++) {
            Page page = pages.get(number);
            if (page == null || !player(page.type) || page.free.size() < page.rows) continue;
            textures.release(number, page.texture);
            pages.set(number, null);
        }
    }

    /** Gives every page back and forgets every sheet. */
    void clear() {
        for (int number = 0; number < pages.size(); number++) {
            Page page = pages.get(number);
            if (page != null) textures.release(number, page.texture);
        }
        pages.clear();
        sheets.clear();
        used.clear();
    }

    private @Nullable Page page(int number) {
        return number >= 0 && number < pages.size() ? pages.get(number) : null;
    }

    private boolean inUse(K key, long within) {
        long[] when = used.get(key);
        return when != null && clock.getAsLong() - when[0] < within;
    }

    private void drop(K key) {
        used.remove(key);
        dropped.accept(key);
    }

    /** A new page for a kind of figure, in the first number given back or after the last. */
    private int open(String type, int rows) {
        int number = pages.indexOf(null);
        if (number < 0) {
            number = pages.size();
            pages.add(null);
        }
        int tile = ImpostorViews.TILE;
        pages.set(number, new Page(type, rows, textures.open(number, tile * ImpostorViews.VIEWS, rows * tile)));
        return number;
    }
}
