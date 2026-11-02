package fr.clixmods.livinghorizon.render.impostor;

//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.CameraRenderState;
//?} else
//import net.minecraft.client.renderer.MultiBufferSource;
//? if <1.21.6
//import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Posture;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The pictures of the impostors, baked by the game's own renderers.
 *
 * <p>A <em>sheet</em> is one figure turned through {@link ImpostorViews#VIEWS} angles. Each
 * view is drawn the way the inventory draws a player (an orthographic picture of the
 * entity through its own renderer, so skins, armour, resource packs and other mods'
 * models are all followed), twice as large as a tile, into a small target.
 *
 * <p>The pictures are then read back and their mip levels worked out on the CPU
 * ({@link ImpostorPixels}), and written into a page that has real mip levels: the
 * graphics card picks and blends them as it does for any texture, so a figure a few pixels
 * tall is an average of its picture instead of a few pixels picked out of it, which would
 * flicker as it moves. The game's texture-to-texture copy cannot be used: it only copies
 * right at the corner of a texture, and stretches anything written anywhere else.
 *
 * <p>Baking is queued and done a little each frame: from 1.21.6 in the GUI pass, where the
 * game itself renders entities to textures, and the read back lands a frame or two later;
 * before, at the start of the frame, into a target of our own that the game takes for its
 * main one while a figure is drawn, and before 1.21.5 the eight views are drawn side by side
 * and read back at once ({@link BakeBackend}, one per range of versions). Everything here runs
 * on the render thread. Resource reloads throw the sheets away; they are read back from disk
 * when the packs are the same, baked again from the figures at hand otherwise.
 *
 * <p>Each kind of figure has pages of its own, one sheet per row, sized to the looks it
 * has: a cow with two looks takes a small page, villagers a few larger ones
 * ({@link SheetAllocator}). The mobs keep their sheets, from one world to the next, and on disk
 * from one game to the next ({@link ImpostorDisk}); the players' are thrown away once unused.
 *
 * <p>Besides entities, the mod's own figures are baked the same way ({@link ImpostorFigure}:
 * the 3D birds), drawn by the mod instead of an entity renderer, seen from the height the
 * figure asks for, and baked again each game rather than kept on disk.
 */
public final class ImpostorAtlas {
    /**
     * Mip levels of a page: tiles of 64, 32, 16 and 8 pixels. A smaller level would blend
     * a figure with the views next to it in the page; smaller than that, a figure is a
     * couple of pixels anyway.
     */
    public static final int MIPS = 4;

    /**
     * The feet stand this far down a tile, as a fraction of its height: above the strip
     * {@link ImpostorRenderer} leaves out at the bottom of a tile, so they are not cut off.
     */
    public static final float FEET = 0.9f;

    /**
     * A flying mob pictured from a height ({@link ImpostorKey#seenFrom}) is turned round its
     * middle, which stands in the middle of its tiles: half its height above its feet.
     */
    public static final float MIDDLE = 0.5f;

    /** Blocks from a flying mob's feet to the middle it is pictured round, from a height. */
    public static double middle(Entity entity) {
        return entity.getBbHeight() / 2;
    }

    /** Blocks from a mount's feet to the middle it and its rider are pictured round, from a height. */
    public static double middle(Entity mount, @Nullable Rider rider) {
        if (rider == null) return middle(mount);
        return Math.max(mount.getBbHeight(), rider.seat().y + rider.entity().getBbHeight()) / 2;
    }

    /** How far down a tile the origin of an entity's pictures is: its feet, or its middle when seen from a height. */
    private static float feet(ImpostorKey key) {
        return Float.isNaN(ImpostorKey.elevation(key)) ? FEET : MIDDLE;
    }

    /** Each view is drawn this many times larger than a tile, then averaged down. */
    private static final int SUPERSAMPLE = 2;
    /** Pixels along a side of the target a view is drawn in. */
    static final int RENDER = ImpostorViews.TILE * SUPERSAMPLE;

    /**
     * Pixels across a page: one sheet, its views side by side. Every tile is
     * {@link ImpostorViews#TILE} pixels: a figure larger than its box (a horse's muzzle) is drawn
     * smaller in its tile instead (see {@link Request}). At a hundred blocks a figure is a dozen
     * pixels tall, and tiles twice as large took four times the memory for nothing to see.
     */
    public static final int PAGE_WIDTH = ImpostorViews.TILE * ImpostorViews.VIEWS;

    /** The bytes of a pixel, in the pages and in the pictures read back. */
    static final int PIXEL_BYTES = 4;
    /** Bytes of video memory a row takes: a strip of 512 by 64 pixels and its mip levels. */
    private static final long ROW_BYTES = rowBytes();
    /**
     * Rows the pages of the mobs may hold, set by the game tests in place of what the settings
     * allow; 0 for the settings.
     */
    private static int mobRowsForTests;

    /**
     * Rows the pages of the mobs, or of the players, may hold in all: the video memory the
     * settings allow each
     * ({@link FarConfig#impostorMobMemory}, 1 GB, and {@link FarConfig#impostorPlayerMemory},
     * 512 MB), taken only as pages are needed. Each has its own: the players never take the mobs'
     * room, nor the mobs the players'. The mobs keep theirs: a world has only so many looks, met
     * again from one world to the next, and a server of endless variations stays bounded. The
     * players come and go: theirs are thrown away once unused for {@link SheetAllocator#PLAYER_KEEP},
     * or when their pages are full. Every player has a look of its own, and a rider one more with
     * what it rides: a crowded server, or hundreds of players who logged off, need them all.
     */
    private static int rows(boolean players) {
        if (!players && mobRowsForTests > 0) return mobRowsForTests;
        FarConfig config = FarConfig.get();
        long megabytes = players ? config.impostorPlayerMemory : config.impostorMobMemory;
        return (int) Math.max(1, (megabytes << 20) / ROW_BYTES);
    }

    /** Bytes of video memory the pages of the players, or of the mobs, take now. */
    public static long memory(boolean players) {
        return (long) ALLOCATOR.taken(players) * ROW_BYTES;
    }

    /** Bytes of video memory the pages of the players, or of the mobs, may take at most. */
    public static long memoryLimit(boolean players) {
        return (long) rows(players) * ROW_BYTES;
    }

    /** Whether a sheet is filed with the players' (players, riders with what they ride), not the mobs'. */
    public static boolean player(ImpostorKey key) {
        return ImpostorKey.PLAYER.equals(key.type());
    }

    /** Full block and sky light: the pictures carry their shading, the world adds its light. */
    private static final int FULL_BRIGHT = 15728880;

    /** Nanoseconds a frame may spend baking. One sheet is always started. */
    private static final long BUDGET = 3_000_000L;

    /**
     * The game's light in the world, as the picture is drawn: the drawing turns the figure
     * over (see {@link #pose}), and its normals with it, so the light is turned over too.
     * The pictures are then shaded like a model in the world, not like one in the inventory.
     */
    static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize().negate();
    static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize().negate();

    /**
     * Where a figure's pictures are: which page and row of it, how many rows the page has,
     * how big a tile is in blocks, and how far down a tile the figure's origin is ({@link #FEET}
     * for an entity's feet).
     */
    public record Sheet(int index, int rows, double worldSize, float feet) {
        public int page() {
            return index / SheetAllocator.ROWS;
        }

        public int row() {
            return index % SheetAllocator.ROWS;
        }

        /** Pixels along a side of one of its tiles. */
        public int tile() {
            return ImpostorViews.TILE;
        }

        /** The left edge of a view's tile, as a fraction of the page's width. */
        public float u(int view) {
            return view / (float) ImpostorViews.VIEWS;
        }

        /** The top edge of its tiles, as a fraction of the page's height. */
        public float v() {
            return row() / (float) rows;
        }

        /** A tile's width, as a fraction of the page's. */
        public float uSpan() {
            return 1f / ImpostorViews.VIEWS;
        }

        /** A tile's height, as a fraction of the page's. */
        public float vSpan() {
            return 1f / rows;
        }

        /** Pixels across its page. */
        public int pageWidth() {
            return tile() * ImpostorViews.VIEWS;
        }

        /** Pixels down its page. */
        public int pageHeight() {
            return rows * tile();
        }
    }

    /**
     * A figure on a mount, baked on it in one picture: its feet {@code seat} blocks from the
     * mount's in the mount's own frame, turned {@code yaw} degrees from it.
     */
    public record Rider(Entity entity, Vec3 seat, float yaw) {
    }

    /** A sheet read from disk, to upload; no sheet when it could not be read, to bake instead. */
    private record Loaded(ImpostorKey key, Entity puppet, ImpostorDisk.@Nullable Stored stored, int generation) {
    }

    /**
     * A figure to bake, its picture covering {@code grow} times what {@link #size} gives: a
     * figure longer than its box (a horse's muzzle, a sniffer) is baked again with more round
     * it, drawn smaller in a tile of the same pixels.
     */
    record Request(ImpostorKey key, @Nullable Entity puppet, @Nullable ImpostorFigure figure, double grow,
                   @Nullable Rider rider) {
        static Request of(ImpostorKey key, Entity puppet, double grow) {
            return of(key, puppet, grow, null);
        }

        static Request of(ImpostorKey key, Entity puppet, double grow, @Nullable Rider rider) {
            return new Request(key, puppet, null, grow, rider);
        }

        /** One of the mod's own figures: its tile is the size it asks for, never grown. */
        static Request of(ImpostorKey key, ImpostorFigure figure) {
            return new Request(key, null, figure, 1.0, null);
        }

        /** The same figure, baked again with its picture covering {@code grow} times its box. */
        Request grown(double grow) {
            return of(key, puppet, grow, rider);
        }

        /** Blocks along a side of its tiles: a mount's, with its rider on top. */
        double size() {
            if (figure != null) return figure.worldSize();
            if (rider == null) return ImpostorAtlas.size(puppet) * grow;
            return ImpostorViews.worldSize(Math.max(puppet.getBbHeight(), rider.seat.y + rider.entity.getBbHeight()),
                    Math.max(puppet.getBbWidth(), rider.entity.getBbWidth())) * grow;
        }

        /** How far down a tile its origin is. */
        float feet() {
            return figure != null ? figure.feet() : ImpostorAtlas.feet(key);
        }

        /** Degrees the camera looks down on it from: 0 for a figure pictured level with it. */
        float elevation() {
            if (figure != null) return figure.elevation();
            float elevation = ImpostorKey.elevation(key);
            return Float.isNaN(elevation) ? 0f : elevation;
        }

        /** Pixels along a side of the target a view is drawn in. */
        int render() {
            return RENDER;
        }
    }

    /** Milliseconds a figure the pages had no room for waits before it is asked for again. */
    private static final long REFUSED_FOR = 30_000L;

    /**
     * How much larger a figure cut off by the very edge of its picture is baked again at
     * least, and how much larger than {@link #size} a picture can get: an Ice and Fire dragon's
     * tail reaches more than three times past its box.
     */
    private static final double GROW = 1.3, MAX_GROW = 5;

    /** How this game version draws and reads back the pictures, and writes the pages. */
    private static final BakeBackend BACKEND = BakeBackend.of();

    /** The pages and their rows, each a texture of the game's, filed under {@link #id}. */
    private static final SheetAllocator<ImpostorKey, AbstractTexture> ALLOCATOR = new SheetAllocator<>(
            System::currentTimeMillis, ImpostorKey::type, ImpostorAtlas::rows,
            new SheetAllocator.Textures<>() {
                @Override
                public AbstractTexture open(int number, int width, int height) {
                    AbstractTexture texture = BACKEND.create("living horizon impostors " + number, width, height);
                    Minecraft.getInstance().getTextureManager().register(id(number), texture);
                    return texture;
                }

                @Override
                public void release(int number, AbstractTexture texture) {
                    Minecraft.getInstance().getTextureManager().release(id(number));
                }
            },
            ImpostorAtlas::forget);

    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    /** Queued, or drawn and waiting for the read back. */
    private static final Set<ImpostorKey> QUEUED = new HashSet<>();
    /** Where each sheet's pictures come from, for the impostor screen. */
    private static final Map<ImpostorKey, Origin> ORIGINS = new HashMap<>();
    /** Mobs asked for before the sheets on disk were listed: asked for again once they are. */
    private static final Map<ImpostorKey, Entity> UNLISTED = new LinkedHashMap<>();
    /** Sheets read from disk, handed over by the disk's thread. */
    private static final Queue<Loaded> LOADED = new ConcurrentLinkedQueue<>();
    /** Sheets read from disk rather than baked, and views drawn, since the game started. */
    private static int loads, bakes;
    /**
     * Figures whose pictures came out the same as another's, pixel for pixel: they share its
     * sheet. Two looks that differ only in something the picture does not show (a value
     * never drawn, one learnt to be restless after the first bake) take one row, one line of
     * the impostor screen.
     */
    private static final Map<ImpostorKey, ImpostorKey> ALIASES = new HashMap<>();
    /** The figure each picture was first baked for, by its kind and a hash of its pixels. */
    private static final Map<Look, ImpostorKey> LOOKS = new HashMap<>();

    private record Look(String type, long pixels) {
    }

    /** How much larger each figure was last baked: asked for again, it starts there. */
    private static final Map<ImpostorKey, Double> GROWN = new HashMap<>();
    /** Figures the pages had no room for, and when: not asked for again for a while. */
    private static final Map<ImpostorKey, Long> REFUSED = new HashMap<>();
    /**
     * Figures whose renderer gives nothing to draw, or whose model still reaches past its picture
     * at {@link #MAX_GROW}: never asked for again, drawn as models.
     */
    private static final Set<ImpostorKey> UNDRAWABLE = new HashSet<>();
    /** Kinds of figure left out for not fitting their picture, each logged once. */
    private static final Set<String> TOO_LARGE = new HashSet<>();
    /** Rows being baked: not free, not yet a sheet. */
    private static final Set<Integer> BAKING = new HashSet<>();
    //? if <1.21.6 {
    /*/^* Our target while a figure is drawn, which the game then takes for its main one. ^/
    private static @Nullable RenderTarget drawing;
    *///?}
    /** Bumped by every reset, so that a read back for pages thrown away is dropped. */
    private static int generation;
    private static boolean dirty, failed;

    private ImpostorAtlas() {
    }

    /** The game's textures were reloaded: every picture may be out of date. */
    public static void invalidate() {
        dirty = true;
    }

    /**
     * Throws every picture away and tries again, even after a failed bake: the figures on
     * hand are baked anew within the next seconds. From the impostor screen.
     */
    public static void regenerate() {
        failed = false;
        dirty = true;
        settle();
        ImpostorDisk.clear();
    }

    /** One baked figure, for the impostor screen: where its pictures come from, if known. */
    public record Baked(ImpostorKey key, Sheet sheet, @Nullable Origin origin) {
    }

    /** Where a sheet's pictures come from. */
    public enum Source {
        /** Baked in an earlier game and read back from disk: shared between games and worlds. */
        DISK,
        /** Baked in this game and saved: the worlds opened later and the next games read it back. */
        SAVED,
        /** Baked in this game and not kept on disk: players, riders, the mod's own figures. */
        TEMPORARY
    }

    /**
     * Where a sheet's pictures come from, when they were baked (milliseconds since 1970, 0 when
     * not known: saved by an older version) and in which world or server (empty when not known).
     */
    public record Origin(Source source, long bakedAt, String world) {
    }

    /** Every figure baked, without counting as a use: a new list, the caller's to sort. */
    public static List<Baked> baked() {
        settle();
        List<Baked> baked = new ArrayList<>(ALLOCATOR.size());
        ALLOCATOR.forEach((key, sheet) -> baked.add(new Baked(key, sheet, ORIGINS.get(key))));
        return baked;
    }

    /** The world or server being played, as its list names it; empty in none. */
    private static String world() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.getSingleplayerServer() != null) return minecraft.getSingleplayerServer().getWorldData().getLevelName();
            if (minecraft.getCurrentServer() != null) return minecraft.getCurrentServer().name;
        } catch (RuntimeException ignored) {
            // Only a label for the impostor screen.
        }
        return "";
    }

    /** Whether impostors can be used at all: switched on, and the bake has not failed. */
    public static boolean usable() {
        FarConfig config = FarConfig.get();
        return config.anyDistant() && config.anyImpostors() && !failed;
    }

    /** The pictures of a figure, if baked. Counts as a use, so the sheets in use are kept. */
    public static @Nullable Sheet sheet(ImpostorKey key) {
        settle();
        return ALLOCATOR.get(ALIASES.getOrDefault(key, key));
    }

    /**
     * Asks for a figure's pictures to be baked, within the next few frames. Already baked,
     * it counts as a use: the figures on hand keep their sheets.
     */
    public static void request(ImpostorKey key, Entity puppet) {
        if (failed || sheet(key) != null || UNDRAWABLE.contains(key) || refused(key) || !QUEUED.add(key)) return;
        if (!player(key)) {
            // Until the sheets on disk are listed, a mob waits, to be read back rather than baked again.
            if (!ImpostorDisk.ready()) {
                UNLISTED.put(key, puppet);
                return;
            }
            // A mob met in an earlier game: read back rather than baked.
            if (ImpostorDisk.has(key)) {
                int asked = generation;
                ImpostorDisk.read(key).whenComplete((stored, error) ->
                        LOADED.add(new Loaded(key, puppet, error == null ? stored : null, asked)));
                return;
            }
        }
        QUEUE.add(Request.of(key, puppet, GROWN.getOrDefault(key, 1.0)));
    }

    /**
     * Asks for a mount and its rider to be baked together, within the next few frames. Never
     * kept on disk: a rider is a player, whose skin may change.
     */
    public static void request(ImpostorKey key, Entity mount, Rider rider) {
        if (failed || sheet(key) != null || UNDRAWABLE.contains(key) || refused(key) || !QUEUED.add(key)) return;
        QUEUE.add(Request.of(key, mount, GROWN.getOrDefault(key, 1.0), rider));
    }

    /** Asks for one of the mod's own figures to be baked, within the next few frames. */
    public static void request(ImpostorKey key, ImpostorFigure figure) {
        if (failed || sheet(key) != null || refused(key) || !QUEUED.add(key)) return;
        QUEUE.add(Request.of(key, figure));
    }

    private static boolean refused(ImpostorKey key) {
        Long when = REFUSED.get(key);
        if (when == null) return false;
        if (System.currentTimeMillis() - when < REFUSED_FOR) return true;
        REFUSED.remove(key);
        return false;
    }

    /** Sheets read from disk rather than baked, since the game started. */
    public static int loads() {
        return loads;
    }

    /** Figures drawn to be baked since the game started, read back or not. */
    public static int bakes() {
        return bakes;
    }

    public static int sheets() {
        return ALLOCATOR.size();
    }

    private static boolean baked(ImpostorKey key) {
        return ALLOCATOR.contains(key) || ALIASES.containsKey(key);
    }

    /** A sheet thrown away: the figures sharing it, and its pixels, go with it. */
    private static void forget(ImpostorKey key) {
        ImpostorKey.forget(key);
        ORIGINS.remove(key);
        ALIASES.values().removeIf(key::equals);
        LOOKS.values().removeIf(key::equals);
    }

    /** Page numbers in use run below this. */
    public static int pages() {
        return ALLOCATOR.pages();
    }

    /** Pixels across a page, 0 for a number not in use. */
    public static int pageWidth(int number) {
        return ALLOCATOR.pageWidth(number);
    }

    /** Pixels down a page, 0 for a number not in use. */
    public static int pageHeight(int number) {
        return ALLOCATOR.pageHeight(number);
    }

    public static int waiting() {
        return QUEUED.size();
    }

    public static boolean failed() {
        return failed;
    }

    //? if <1.21.6 {
    /*/^*
     * The target the game must draw into in place of its main one, while a figure is being
     * baked; null the rest of the time. Read by MinecraftMixin and LevelRendererMixin.
     ^/
    public static @Nullable RenderTarget drawing() {
        return drawing;
    }

    /^* Set by the backend around the drawing of a figure. ^/
    static void drawing(@Nullable RenderTarget target) {
        drawing = target;
    }
    *///?}

    /** The render type of each page, made once: the game hands back the same one every time. */
    private static RenderType[] types = new RenderType[16];

    public static RenderType type(int page) {
        if (page >= types.length) types = Arrays.copyOf(types, Math.max(page + 1, types.length * 2));
        RenderType type = types[page];
        if (type != null) return type;
        //? if >=26.1 {
        /*type = RenderTypes.entityCutout(id(page));
        *///?} else {
        type = RenderTypes.entityCutoutNoCull(id(page));
        //?}
        return types[page] = type;
    }

    /** The texture of one atlas page. */
    public static Identifier id(int page) {
        return Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "impostors/" + page);
    }

    /** Drops what a reload or a switch-off made stale. */
    private static void settle() {
        if (!dirty) return;
        dirty = false;
        generation++;
        ALLOCATOR.clear();
        ORIGINS.clear();
        ALIASES.clear();
        LOOKS.clear();
        QUEUE.clear();
        QUEUED.clear();
        UNLISTED.clear();
        BAKING.clear();
        LOADED.clear();
        GROWN.clear();
        REFUSED.clear();
        UNDRAWABLE.clear();
        ImpostorKey.forgetAll();
        // The packs or the mods may have changed: the sheets on disk are looked for again.
        ImpostorDisk.reset();
    }

    /**
     * Bakes what is queued, within a small budget. Called once a frame: from the GUI pass
     * from 1.21.6, at the start of the frame before.
     */
    public static void bakePending() {
        long started = Profile.begin(Profile.Section.BAKE);
        try {
            bakeQueued();
        } finally {
            Profile.end(Profile.Section.BAKE, started);
        }
    }

    private static void bakeQueued() {
        settle();
        if (!usable()) {
            for (Request request : QUEUE) QUEUED.remove(request.key);
            QUEUE.clear();
            for (Loaded loaded : LOADED) QUEUED.remove(loaded.key);
            LOADED.clear();
            QUEUED.removeAll(UNLISTED.keySet());
            UNLISTED.clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getOverlay() != null) return;
        ALLOCATOR.sweep();
        if (!UNLISTED.isEmpty() && ImpostorDisk.ready()) {
            List<Map.Entry<ImpostorKey, Entity>> listed = new ArrayList<>(UNLISTED.entrySet());
            UNLISTED.clear();
            for (Map.Entry<ImpostorKey, Entity> mob : listed) {
                QUEUED.remove(mob.getKey());
                request(mob.getKey(), mob.getValue());
            }
        }
        long end = System.nanoTime() + BUDGET;
        Loaded loaded;
        while ((loaded = LOADED.poll()) != null) {
            if (loaded.generation != generation) continue;
            if (loaded.stored == null || !ImpostorPixels.fits(loaded.stored.levels(), MIPS)) {
                ImpostorDisk.forget(loaded.key);
                QUEUE.add(Request.of(loaded.key, loaded.puppet, GROWN.getOrDefault(loaded.key, 1.0)));
                continue;
            }
            QUEUED.remove(loaded.key);
            if (baked(loaded.key)) continue;
            int index = ALLOCATOR.allocate(loaded.key);
            if (index < 0) {
                REFUSED.put(loaded.key, System.currentTimeMillis());
                continue;
            }
            try {
                place(loaded.key, index, loaded.stored.worldSize(), feet(loaded.key), loaded.stored.levels());
                if (ALLOCATOR.contains(loaded.key)) {
                    ORIGINS.put(loaded.key, new Origin(Source.DISK, loaded.stored.bakedAt(), loaded.stored.world()));
                }
                loads++;
            } catch (RuntimeException | LinkageError error) {
                ALLOCATOR.free(index);
                fail(error);
                return;
            }
        }
        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.poll();
            // Gliding since it was asked for: lying in the air, it is asked for again once it lands.
            if (request.puppet != null && request.puppet.level() != minecraft.level || baked(request.key)
                    || request.puppet instanceof LivingEntity glider && glider.isFallFlying()) {
                QUEUED.remove(request.key);
                continue;
            }
            Runnable back = request.puppet != null ? Puppets.atRest(request.puppet) : () -> {
            };
            Runnable turnedBack = request.puppet != null ? facingBack(request.puppet) : () -> {
            };
            try {
                bake(minecraft, request);
            } catch (LeftOut leftOut) {
                // That one figure only: its renderer gives the mod nothing to draw, now or later.
                UNDRAWABLE.add(request.key);
                continue;
            } catch (RuntimeException | LinkageError error) {
                fail(error);
                return;
            } finally {
                back.run();
                turnedBack.run();
            }
            if (System.nanoTime() > end) return;
        }
    }

    /** A figure whose renderer gives no state ({@link GhostRenderer#extract}): its bake is dropped, not the others. */
    private static final class LeftOut extends RuntimeException {
        LeftOut() {
            super(null, null, false, false);
        }
    }

    private static void fail(Throwable error) {
        failed = true;
        QUEUE.clear();
        QUEUED.clear();
        UNLISTED.clear();
        LivingHorizonClient.LOGGER.error("Impostors could not be baked, falling back to full models", error);
    }

    /**
     * A row for a figure. -1 for none: the figure is drawn as a model, and not asked for again
     * for {@link #REFUSED_FOR}, rather than baked again every second for nothing.
     */
    private static int room(Request request) {
        int index = ALLOCATOR.allocate(request.key);
        if (index >= 0) return index;
        QUEUED.remove(request.key);
        REFUSED.put(request.key, System.currentTimeMillis());
        return -1;
    }

    private static long rowBytes() {
        long bytes = 0;
        for (int level = 0; level < MIPS; level++) bytes += (long) (PAGE_WIDTH >> level) * (ImpostorViews.TILE >> level) * PIXEL_BYTES;
        return bytes;
    }

    /**
     * Rows the pages of the mobs may hold, in place of what the settings allow, 0 for that again:
     * for the game tests, which fill them with a few hundred figures.
     */
    public static void limitMobRows(int rows) {
        mobRowsForTests = Math.max(0, rows);
    }

    /** Blocks along a side of a figure's tiles. */
    private static double size(Entity puppet) {
        return ImpostorViews.worldSize(Math.max(0.3, puppet.getBbHeight()), Math.max(0.3, puppet.getBbWidth()));
    }

    /**
     * Draws a figure's views with this version's backend, into a row found for it. Nothing sent
     * (it threw): the row goes back to its page and the figure is no longer waiting.
     */
    private static void bake(Minecraft minecraft, Request request) {
        int index = room(request);
        if (index < 0) return;
        double size = request.size();
        BAKING.add(index);
        bakes++;
        int baking = generation;
        boolean sent = false;
        try {
            BACKEND.drawViews(minecraft, request, size, new BakeBackend.ReadBack() {
                @Override
                public boolean wanted() {
                    return baking == generation;
                }

                @Override
                public void done(int @Nullable [][] pictures, @Nullable Throwable error) {
                    if (error != null && wanted()) fail(error);
                    store(request, index, size, pictures, baking);
                }
            });
            sent = true;
        } finally {
            if (!sent) {
                BAKING.remove(index);
                ALLOCATOR.free(index);
                QUEUED.remove(request.key);
            }
        }
    }

    /** A figure's state for one view, still and lit evenly; seated if its key says so. */
    private static EntityRenderState staged(Minecraft minecraft, Request request, int view) {
        Entity puppet = request.puppet;
        if (!(puppet instanceof LivingEntity)) {
            // A boat, a minecart: no body to turn in its state, the entity itself is turned
            // for each view, the way stage turns a mob's body. Put back after the bake.
            float facing = ImpostorViews.facing(view);
            puppet.setYRot(facing);
            puppet.yRotO = facing;
            puppet.setXRot(0f);
            puppet.xRotO = 0f;
        }
        EntityRenderState state = GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), puppet, 1.0f, FULL_BRIGHT);
        if (state == null) throw new LeftOut();
        stage(state, view);
        Posture.baked(state, request.key.posture());
        // Water mobs were in water when last seen; a puppet is in no world.
        if (state instanceof LivingEntityRenderState living && GhostRenderer.livesInWater(puppet)) {
            living.isInWater = true;
        }
        return state;
    }

    /**
     * Blocks a figure is raised by in its picture: lying down, but not riding ({@link Posture#BAKED_LIFT});
     * lowered by half its height when it is a flyer seen from a height, its middle where the tile turns.
     */
    private static double lift(Request request) {
        if (request.puppet != null && !Float.isNaN(ImpostorKey.elevation(request.key))) {
            return -middle(request.puppet, request.rider);
        }
        return request.rider == null ? Posture.bakedLift(request.key.posture()) : 0;
    }

    /** What puts back the way an entity that is not a mob faces, turned view by view while baked. */
    private static Runnable facingBack(Entity puppet) {
        if (puppet instanceof LivingEntity) return () -> {
        };
        float yaw = puppet.getYRot(), yawO = puppet.yRotO, pitch = puppet.getXRot(), pitchO = puppet.xRotO;
        return () -> {
            puppet.setYRot(yaw);
            puppet.yRotO = yawO;
            puppet.setXRot(pitch);
            puppet.xRotO = pitchO;
        };
    }

    /** A rider's state for one view: on the mount, turned with it, in the riding pose. */
    private static EntityRenderState stagedRider(Minecraft minecraft, Rider rider, int view) {
        EntityRenderState state = GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), rider.entity, 1.0f, FULL_BRIGHT);
        if (state == null) throw new LeftOut();
        stage(state, view);
        if (state instanceof LivingEntityRenderState living) living.bodyRot += rider.yaw;
        Posture.seat(state);
        return state;
    }

    /** Where a rider's feet are drawn in a view: its seat, turned with the mount. */
    private static Vec3 seat(Rider rider, int view) {
        float mount = ImpostorViews.facing(view);
        return rider.seat.yRot((float) -Math.toRadians(mount));
    }

    /**
     * As the inventory does it: pixels for blocks, y down, so turned over to stand. A figure
     * of the mod's seen from above has its top tipped towards the camera, which looks along +z.
     */
    static PoseStack pose(Request request, double size, int render) {
        PoseStack pose = new PoseStack();
        float scale = (float) (render / size);
        pose.translate(render / 2f, render * request.feet(), 0f);
        pose.scale(scale, scale, -scale);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        if (request.elevation() != 0f) {
            pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-request.elevation())));
        }
        return pose;
    }

    /**
     * One view of a figure, handed to the game to draw: an entity through its own renderer,
     * a figure of the mod's drawn by itself, turned the same way an entity is by {@link #stage}.
     */
    //? if >=1.21.9 {
    static void draw(Minecraft minecraft, Request request, int view, PoseStack pose, SubmitNodeStorage storage) {
        if (request.figure != null) {
            request.figure.draw(pose, new Sink(storage), ImpostorViews.facing(view));
        } else {
            minecraft.getEntityRenderDispatcher().submit(staged(minecraft, request, view), new CameraRenderState(),
                    0.0, lift(request), 0.0, pose, storage);
            if (request.rider != null) {
                Vec3 seat = seat(request.rider, view);
                minecraft.getEntityRenderDispatcher().submit(stagedRider(minecraft, request.rider, view),
                        new CameraRenderState(), seat.x, seat.y + lift(request), seat.z, pose, storage);
            }
        }
    }
    //?} else {
    /*static void draw(Minecraft minecraft, Request request, int view, PoseStack pose, MultiBufferSource buffers) {
        if (request.figure != null) {
            request.figure.draw(pose, new Sink(buffers), ImpostorViews.facing(view));
        } else {
            GhostRenderer.drawState(staged(minecraft, request, view), 0.0, lift(request), 0.0, pose, buffers, FULL_BRIGHT);
            if (request.rider != null) {
                Vec3 seat = seat(request.rider, view);
                GhostRenderer.drawState(stagedRider(minecraft, request.rider, view), seat.x, seat.y + lift(request), seat.z,
                        pose, buffers, FULL_BRIGHT);
            }
        }
    }
    *///?}

    /**
     * The views are drawn and read back, bottom row first: their levels go into the page,
     * and the sheet can be used. No pictures when the read back failed. A figure cut off by
     * the edge of its tiles is queued again, larger.
     */
    private static void store(Request request, int index, double size, int @Nullable [][] pictures, int generation) {
        long started = Profile.begin(Profile.Section.BAKE_STORE);
        try {
            storeSheet(request, index, size, pictures, generation);
        } finally {
            Profile.end(Profile.Section.BAKE_STORE, started);
        }
    }

    private static void storeSheet(Request request, int index, double size, int @Nullable [][] pictures, int generation) {
        ImpostorKey key = request.key;
        boolean again = false;
        try {
            if (generation != ImpostorAtlas.generation || pictures == null) return;
            double growth = request.figure != null ? 1 : growth(pictures, request.render(), request.feet());
            if (growth > 1.01 && request.grow < MAX_GROW) {
                double grow = Math.min(MAX_GROW, request.grow * growth);
                GROWN.put(key, grow);
                QUEUE.add(request.grown(grow));
                again = true;
                return;
            }
            if (growth > 1.01) {
                // Still cut off with all the room it may have (Ice and Fire's sea serpent): a
                // picture of part of it would stand for it everywhere, its model is drawn instead.
                UNDRAWABLE.add(key);
                if (TOO_LARGE.add(key.type())) {
                    LivingHorizonClient.LOGGER.info("{} is drawn as a model far away: it does not fit its picture",
                            key.type());
                }
                return;
            }
            int[][] levels = ImpostorPixels.sheet(pictures, request.render(), ImpostorViews.TILE, MIPS);
            if (!place(key, index, size, request.feet(), levels)) return;
            BAKING.remove(index);
            // The mod's own figures are not kept: their sheets have no entity to be read for.
            boolean kept = !player(key) && request.figure == null;
            long now = System.currentTimeMillis();
            String world = world();
            if (ALLOCATOR.contains(key)) ORIGINS.put(key, new Origin(kept ? Source.SAVED : Source.TEMPORARY, now, world));
            if (kept) ImpostorDisk.write(key, size, levels, now, world);
        } catch (RuntimeException | LinkageError error) {
            if (generation == ImpostorAtlas.generation) fail(error);
        } finally {
            if (generation == ImpostorAtlas.generation) {
                if (!again) QUEUED.remove(key);
                if (BAKING.remove(index)) ALLOCATOR.free(index);
            }
        }
    }

    /**
     * Writes a sheet's levels, its views side by side, into its row: it can be used. The same
     * pixels as a sheet of its kind already baked: it shares that one, and its row is freed.
     */
    private static boolean place(ImpostorKey key, int index, double size, float feet, int[][] levels) {
        AbstractTexture page = ALLOCATOR.texture(index);
        if (page == null) return false;
        Look look = new Look(key.type(), ImpostorPixels.hash(levels[0]));
        ImpostorKey same = LOOKS.get(look);
        if (same != null && !same.equals(key) && ALLOCATOR.contains(same)) {
            ALIASES.put(key, same);
            ALLOCATOR.touch(same);
            ALLOCATOR.free(index);
            return true;
        }
        LOOKS.put(look, key);
        int row = index % SheetAllocator.ROWS;
        for (int level = 0; level < MIPS; level++) {
            int tile = ImpostorViews.TILE >> level;
            BACKEND.write(page, level, 0, row * tile, tile * ImpostorViews.VIEWS, tile, levels[level]);
        }
        ALLOCATOR.put(key, index, size, feet);
        return true;
    }

    /** How much larger the views must be drawn to stay within what their tiles show. */
    private static double growth(int[][] pictures, int render, float feet) {
        int hidden = (int) Math.ceil(ImpostorRenderer.INSET * render);
        double growth = 1;
        for (int[] picture : pictures) {
            growth = Math.max(growth, ImpostorPixels.growth(picture, render, hidden, render * (1 - feet), GROW));
        }
        return growth;
    }

    /**
     * Stands a figure still, lit evenly and turned for one view. The camera sits in front of
     * a figure whose body faces 180 degrees, as in the inventory; a camera that sees it
     * from {@code r} degrees round its front sees the figure turned by minus {@code r}.
     */
    private static void stage(EntityRenderState state, int view) {
        // Before 1.21.9 the light is handed over when the figure is drawn, and no shadow or
        // outline is drawn along with it.
        //? if >=1.21.9 {
        state.lightCoords = FULL_BRIGHT;
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.outlineColor = 0;
        //?}
        state.nameTag = null;
        state.ageInTicks = 0;
        state.displayFireAnimation = false;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = ImpostorViews.facing(view);
            living.yRot = 0;
            living.xRot = 0;
            living.walkAnimationPos = 0;
            living.walkAnimationSpeed = 0;
            living.deathTime = 0;
            living.hasRedOverlay = false;
            living.bedOrientation = null;
        }
    }
}
