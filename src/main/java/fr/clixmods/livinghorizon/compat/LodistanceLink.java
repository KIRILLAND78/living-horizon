package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.render.DepthMath;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Lodistance (Fabric, 26.3), reached by reflection: it has no API and no source, so this reads
 * the fields its own classes keep, never writes one, and turns itself off for the session when
 * one is missing ("Lodistance found, but not the version this mod knows").
 *
 * <p>It draws its terrain one of two ways:
 * <ul>
 *   <li>with Sodium ({@code PearlLodRenderer}, its default), inside Sodium's opaque terrain
 *       pass, into the game's own depth, with a depth of its own making: each point at half the
 *       depth its distance would have through the game's projection, so that the game's terrain
 *       always wins. A distant mob behind one of its hills is hidden only past twice the hill's
 *       distance, and shows through it otherwise. {@link #intoGameDepth()}, {@link #terms()};</li>
 *   <li>without Sodium ({@code LodRenderer}), into a framebuffer of its own, laid over the
 *       picture where the game drew nothing: none of it reaches the game's depth, as with Distant
 *       Horizons. {@link #ownDepth()}, {@link #ownProjection}.</li>
 * </ul>
 * {@link FarDepth} turns either into the game's depth around the entities, and puts it back.
 * Lodistance and Voxy or Distant Horizons refuse each other ({@code conflicts}): one at a time.
 */
final class LodistanceLink {
    private static final String ROOT = "nl.lodistance.";
    private static final Links.Status STATUS = new Links.Status();
    private static boolean ready, linked;

    private static Method usePearl, config, world, lodDistance;
    private static Field enabled, fogStart, lodsWithShaders, extendFog;
    private static Matrix4fc projection;
    private static Object pearl, own;
    private static Field pearlStatus, ownStatus, ownDepth, ownWidth, ownHeight;
    private static Matrix4fc ownMatrix;
    /** Its world's regions, and what a region holds: see {@link LodistanceSource}. */
    static Field slots, slotData, count, top, bottom, block, waterTop, chunkKnown, palette;
    static Method paletteName;
    static short empty;

    private LodistanceLink() {
    }

    /** Whether Lodistance is there, in a version whose classes this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("lodistance")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            Class<?> main = Class.forName(ROOT + "Lodistance");
            usePearl = main.getMethod("usePearlRenderer");
            config = main.getMethod("config");
            world = main.getMethod("world");
            Class<?> settings = Class.forName(ROOT + "HpConfig");
            lodDistance = settings.getMethod("lodDistanceBlocks");
            enabled = settings.getField("enabled");
            fogStart = settings.getField("fogStart");
            lodsWithShaders = settings.getField("lodsWithShaders");
            extendFog = settings.getField("extendVanillaFog");
            projection = (Matrix4fc) Class.forName(ROOT + "render.RenderContext").getField("PROJECTION").get(null);

            Class<?> pearlType = Class.forName(ROOT + "render.PearlLodRenderer");
            pearl = pearlType.getField("INSTANCE").get(null);
            pearlStatus = pearlType.getField("status");
            Class<?> ownType = Class.forName(ROOT + "render.LodRenderer");
            own = ownType.getField("INSTANCE").get(null);
            ownStatus = ownType.getField("status");
            ownDepth = hidden(ownType, "lodDepth");
            ownWidth = hidden(ownType, "fboW");
            ownHeight = hidden(ownType, "fboH");
            ownMatrix = (Matrix4fc) hidden(ownType, "proj").get(own);

            Class<?> worldType = Class.forName(ROOT + "lod.LodWorld");
            slots = hidden(worldType, "slots");
            palette = worldType.getField("palette");
            slotData = hidden(Class.forName(ROOT + "lod.LodWorld$Slot"), "data");
            Class<?> region = Class.forName(ROOT + "lod.LodRegion");
            count = hidden(region, "count");
            top = hidden(region, "top");
            bottom = hidden(region, "bottom");
            block = hidden(region, "block");
            waterTop = hidden(region, "waterTop");
            chunkKnown = hidden(region, "chunkKnown");
            empty = region.getField("EMPTY").getShort(null);
            paletteName = Class.forName(ROOT + "lod.BlockPalette").getMethod("name", int.class);
            linked = true;
            STATUS.set("linked");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Lodistance found, but not the version this mod knows", e);
        }
        return linked;
    }

    private static Field hidden(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** A read that failed: off for the session, said once. */
    static synchronized void fail(String what, Throwable e) {
        if (!linked) return;
        linked = false;
        STATUS.set("off: " + what + ": " + e);
        LivingHorizonClient.LOGGER.warn("Lodistance stopped answering as expected ({}): its terrain no longer hides distant mobs", what, e);
    }

    private static @Nullable Object settings() throws ReflectiveOperationException {
        return config.invoke(null);
    }

    /** Its world for the player's level, or null while it has none open. */
    static @Nullable Object world() {
        if (!linked()) return null;
        try {
            return world.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its world", e);
            return null;
        }
    }

    /** Blocks across to which it draws its terrain, from its settings; 0 when it is off. */
    static double drawnDistance() {
        if (!linked()) return 0;
        try {
            Object settings = settings();
            if (settings == null || !enabled.getBoolean(settings)) return 0;
            return ((Number) lodDistance.invoke(settings)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its settings", e);
            return 0;
        }
    }

    /** Where its fog starts, as a fraction of {@link #drawnDistance()}. */
    static double fogStart() {
        if (!linked()) return 1;
        try {
            Object settings = settings();
            return settings == null ? 1 : fogStart.getDouble(settings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its settings", e);
            return 1;
        }
    }

    /** Whether it draws its terrain under a shader pack (its own switch, on by default). */
    static boolean withShaders() {
        if (!linked()) return false;
        try {
            Object settings = settings();
            return settings != null && lodsWithShaders.getBoolean(settings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its settings", e);
            return false;
        }
    }

    /**
     * Whether it pushes the game's render distance fog out to its own distance (its
     * {@code extendVanillaFog}, on by default): the start and end the game's fog then holds are
     * where its own terrain's fog runs, from {@code fogStart} times its distance to its distance.
     */
    static boolean extendsFog() {
        if (!linked()) return false;
        try {
            Object settings = settings();
            return settings != null && enabled.getBoolean(settings) && extendFog.getBoolean(settings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its settings", e);
            return false;
        }
    }

    /** Whether it draws into the game's own depth this session (with Sodium): see the class. */
    static boolean intoGameDepth() throws ReflectiveOperationException {
        return (Boolean) usePearl.invoke(null);
    }

    /**
     * Whether the renderer it uses said, at its last frame, that it drew its terrain: its own
     * word, {@code "rendering"}, or why not (no meshes yet, under water, blindness, a pack).
     */
    static @Nullable String drawing() {
        if (!linked()) return "Lodistance is not usable";
        try {
            Object settings = settings();
            if (settings == null || !enabled.getBoolean(settings)) return "Lodistance is turned off";
            Object status = intoGameDepth() ? pearlStatus.get(pearl) : ownStatus.get(own);
            return "rendering".equals(status) ? null : "Lodistance: " + status;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("its renderer", e);
            return "Lodistance is not usable";
        }
    }

    /** The two terms of the depth it writes into the game's, from the projection it noted this frame: see {@link DepthMath#lodistance}. */
    static float[] terms() {
        return DepthMath.lodistance(projection.m32());
    }

    /** Without Sodium, the OpenGL name of its depth texture, 0 while it has none. */
    static int ownDepth() throws ReflectiveOperationException {
        return ownDepth.getInt(own);
    }

    /** Its depth texture's size, {@code {width, height}}. */
    static int[] ownSize() throws ReflectiveOperationException {
        return new int[]{ownWidth.getInt(own), ownHeight.getInt(own)};
    }

    /** The projection its own depth texture was drawn with, into {@code dest}: the game's, its planes 16 blocks and past its distance. */
    static Matrix4f ownProjection(Matrix4f dest) {
        return dest.set(ownMatrix);
    }

    /** Its regions held in memory, by key; none when the field holds something else. */
    @SuppressWarnings("unchecked")
    static Map<Long, Object> slots(Object world) throws IllegalAccessException {
        Object held = slots.get(world);
        return held instanceof Map<?, ?> map ? (Map<Long, Object>) map : Map.of();
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        linked();
        if (!linked) return STATUS.describe();
        String why = drawing();
        try {
            return STATUS.describe() + (intoGameDepth() ? ", with Sodium" : ", its own OpenGL renderer")
                    + (why == null ? ", drawing" : "; " + why);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return STATUS.describe();
        }
    }
}
