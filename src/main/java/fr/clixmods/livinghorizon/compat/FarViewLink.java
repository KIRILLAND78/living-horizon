package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything the mod asks Far View (Forge and NeoForge, 1.20.1 to 1.21.4), in one place. Far View
 * has no API: its own classes are reached by reflection ({@code com.farview}), looked for by name,
 * so that the mod loads without it and a rename on its side turns this link off for the session.
 *
 * <p>Far View keeps a height grid of the terrain the client has seen ({@code FVWorld}: regions of
 * 128 by 128 columns, the top block of each, the ground under trees) and draws it into the game's
 * own picture and depth, before the entities, with the game's projection pushed out to its
 * distance. Our figures are hidden behind it like any mob behind any block, with one exception:
 * its <em>Seamless</em> option (off by default, never under a shader pack) draws it with a
 * projection of its own, squeezed into the back of the depth range, which our figures' depth
 * cannot be compared with ({@link #squeezesDepth}).
 *
 * <p>Its far mobs (on by default) replace the game's model of a live mob by a box of its own past
 * a distance ({@code FVFarMobs.suppressModel}), up to the render distance. The game then leaves
 * that mob out, and Living Horizon would draw it as well: {@link #drawsMob} says which it draws.
 */
public final class FarViewLink {
    private static final String PACKAGE = "com.farview.";
    private static final Links.Status STATUS = new Links.Status();
    /** Live mobs left to Far View's boxes: counted, for {@code /livinghorizon lod}. */
    private static final AtomicLong LEFT = new AtomicLong();
    private static boolean ready, linked;
    /** A call that threw: that part is off for the session, the rest goes on. */
    private static volatile boolean settingsBroken, mobsBroken;

    private static MethodHandle enabled, lodDistance, hazeStart;
    /** Before 0.1.7 it has no Seamless option: null there. */
    private static @Nullable MethodHandle seamless;
    private static MethodHandle regions, regionX, regionZ, heights;
    private static @Nullable MethodHandle blocks, ground, groundBlocks, canopy;
    private static @Nullable MethodHandle boxes, boxedChunks, suppressModel;

    private FarViewLink() {
    }

    /** Whether Far View's classes are there, whatever their version: looked for once. */
    private static final class Installed {
        static final boolean FOUND = Links.optionalClass(PACKAGE + "FVWorld") != null;
    }

    static boolean installed() {
        return Installed.FOUND;
    }

    /** Whether Far View is there, in a version whose classes this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!installed()) {
            STATUS.set("not installed");
            return false;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> world = Class.forName(PACKAGE + "FVWorld");
            Class<?> config = Class.forName(PACKAGE + "FVConfig");
            Class<?> region = Class.forName(PACKAGE + "FVWorld$FVRegion");
            enabled = lookup.findStatic(config, "enabled", MethodType.methodType(boolean.class));
            lodDistance = lookup.findStatic(config, "lodDistanceBlocks", MethodType.methodType(int.class));
            hazeStart = lookup.findStatic(config, "hazeStartFraction", MethodType.methodType(float.class));
            seamless = optionalStatic(lookup, config, "seamless", MethodType.methodType(boolean.class));
            if (seamless == null) STATUS.fallback("no Seamless option (before 0.1.7)");
            regions = lookup.findStatic(world, "regions", MethodType.methodType(Iterable.class));
            Class<?> objects = Object.class;
            regionX = lookup.findGetter(region, "regionX", int.class).asType(MethodType.methodType(int.class, objects));
            regionZ = lookup.findGetter(region, "regionZ", int.class).asType(MethodType.methodType(int.class, objects));
            heights = lookup.findGetter(region, "heights", short[].class).asType(MethodType.methodType(short[].class, objects));
            blocks = getter(lookup, region, "blocks", int[].class);
            if (blocks == null) STATUS.fallback("no blocks, only heights");
            ground = getter(lookup, region, "groundHeights", short[].class);
            groundBlocks = getter(lookup, region, "groundBlocks", int[].class);
            canopy = getter(lookup, region, "canopyBottoms", short[].class);
            if (ground == null || groundBlocks == null || canopy == null) STATUS.fallback("no ground under the trees");
            linked = true;
            STATUS.set("linked");
        } catch (ReflectiveOperationException | LinkageError e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Far View found, but not the version this mod knows: its terrain cannot be read", e);
            return false;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> config = Class.forName(PACKAGE + "FVConfig");
            Class<?> mobs = Class.forName(PACKAGE + "FVFarMobs");
            boxes = lookup.findStatic(config, "farMobsBoxes", MethodType.methodType(boolean.class));
            boxedChunks = lookup.findStatic(config, "farMobsChunks", MethodType.methodType(int.class, int.class));
            suppressModel = lookup.findStatic(mobs, "suppressModel",
                    MethodType.methodType(boolean.class, Entity.class, double.class, double.class, double.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            // Before 0.1.7 it has no far mobs: nothing it draws in our place.
            boxes = boxedChunks = suppressModel = null;
            STATUS.fallback("no far mobs of its own (before 0.1.7)");
        }
        return linked;
    }

    /** A static method some versions do not have: null there. */
    private static @Nullable MethodHandle optionalStatic(MethodHandles.Lookup lookup, Class<?> owner, String name,
                                                         MethodType type) throws IllegalAccessException {
        try {
            return lookup.findStatic(owner, name, type);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static @Nullable MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String name, Class<?> type)
            throws IllegalAccessException {
        try {
            return lookup.findGetter(owner, name, type).asType(MethodType.methodType(type, Object.class));
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** A call into its settings that threw: they read as off from then on. */
    private static void settingsFailed(Throwable e) {
        if (settingsBroken) return;
        settingsBroken = true;
        STATUS.set("off: its settings did not answer as expected: " + e);
        LivingHorizonClient.LOGGER.warn("Far View's settings did not answer as expected: its terrain is read as not drawn", e);
    }

    /**
     * Whether Far View draws its terrain in the level the player is in: switched on (Ctrl+H turns
     * it off), and a dimension without a ceiling, the only ones it draws. On the game's thread.
     */
    static boolean drawing(Minecraft minecraft) {
        if (!linked() || settingsBroken || minecraft.level == null || minecraft.level.dimensionType().hasCeiling()) return false;
        try {
            return (boolean) enabled.invokeExact();
        } catch (Throwable e) {
            // Its configuration is read before it is loaded: an exception, not an answer.
            if (e instanceof IllegalStateException) return false;
            settingsFailed(e);
            return false;
        }
    }

    /** Blocks across to which it draws its terrain; 0 when it draws nothing. */
    static double drawnDistance(Minecraft minecraft) {
        if (!drawing(minecraft)) return 0;
        try {
            return (int) lodDistance.invokeExact();
        } catch (Throwable e) {
            settingsFailed(e);
            return 0;
        }
    }

    /**
     * Blocks across to which its terrain is still clear of its haze: where its fog starts, at a
     * fraction of its distance, never before the edge of the game's own terrain (the way it works
     * it out itself), and never past 90% of its distance. 0 when it draws nothing.
     */
    static double clearDistance(Minecraft minecraft) {
        double drawn = drawnDistance(minecraft);
        if (drawn <= 0) return 0;
        try {
            double fraction = (float) hazeStart.invokeExact();
            double game = minecraft.options.getEffectiveRenderDistance() * 16.0 * Math.sqrt(2) + 16;
            return Math.min(drawn * 0.9, Math.max(drawn * fraction, game));
        } catch (Throwable e) {
            settingsFailed(e);
            return 0;
        }
    }

    /**
     * Whether its terrain's depth is squeezed into the back of the depth range, with a projection
     * of its own (its <em>Seamless</em> option, which it never uses under a shader pack): our
     * figures' depth cannot be compared with it, so the depth cannot say which mobs it hides.
     */
    public static boolean squeezesDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (seamless == null || !drawing(minecraft) || IrisLink.shaderPackOn()) return false;
        try {
            return (boolean) seamless.invokeExact();
        } catch (Throwable e) {
            settingsFailed(e);
            return false;
        }
    }

    /** What one region of its height grid holds; any array but {@code heights} may be null. */
    interface RegionReader {
        void region(int regionX, int regionZ, short[] heights, int @Nullable [] blocks, short @Nullable [] ground,
                    int @Nullable [] groundBlocks, short @Nullable [] canopy);
    }

    /**
     * Each region of its height grid that holds data, on the game's thread: the one Far View fills
     * it on. Its map of regions is a plain one, which another thread must never read.
     */
    static void regions(RegionReader reader) throws Throwable {
        for (Object region : (Iterable<?>) regions.invokeExact()) {
            short[] tops = (short[]) heights.invokeExact(region);
            if (tops == null) continue;
            reader.region((int) regionX.invokeExact(region), (int) regionZ.invokeExact(region), tops,
                    blocks == null ? null : (int[]) blocks.invokeExact(region),
                    ground == null ? null : (short[]) ground.invokeExact(region),
                    groundBlocks == null ? null : (int[]) groundBlocks.invokeExact(region),
                    canopy == null ? null : (short[]) canopy.invokeExact(region));
        }
    }

    /**
     * Whether Far View draws this live mob itself, as a box in place of the game's model: the game
     * leaves it out of the frame, and drawing it here too would show it twice. Past its boxes'
     * reach (its far mobs' distance, at most 32 chunks), the game culls the mob and Far View draws
     * nothing: it is ours again. On the game's thread, while the frame is drawn.
     */
    public static boolean drawsMob(Entity entity, Vec3 camera) {
        if (suppressModel == null || mobsBroken || !linked()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (!(boolean) boxes.invokeExact()) return false;
            double reach = (int) boxedChunks.invokeExact(minecraft.options.getEffectiveRenderDistance()) * 16.0 + 16;
            if (entity.distanceToSqr(camera) > reach * reach) return false;
            if (!(boolean) suppressModel.invokeExact(entity, camera.x, camera.y, camera.z)) return false;
        } catch (Throwable e) {
            mobsBroken = true;
            STATUS.fallback("its far mobs did not answer as expected, so mobs it may box are drawn here too (" + e + ")");
            LivingHorizonClient.LOGGER.warn("Far View's far mobs did not answer as expected: a mob may be drawn twice", e);
            return false;
        }
        LEFT.incrementAndGet();
        return true;
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        if (!linked()) return STATUS.describe();
        StringBuilder text = new StringBuilder(STATUS.describe());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            text.append(drawing(minecraft) ? "; drawing to " + Math.round(drawnDistance(minecraft)) + " blocks"
                    : "; not drawing in this dimension, or switched off");
            if (squeezesDepth()) text.append("; Seamless on: hidden mobs found by lines of sight, not the depth");
        }
        text.append("; ").append(LEFT.get()).append(" live mobs left to its boxes");
        return text.toString();
    }
}
