package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Ecstatic, a mod that draws the overworld past the render distance from the world's seed
 * (NeoForge 1.21.1), in single player only. It has no API: it is reached by reflection, like
 * Voxy, and {@link EcstaticSource} reads its world.
 *
 * <p>Ecstatic draws its terrain after the entities, tested against the game's depth and written
 * into it, but with a projection of its own: the game's near plane and a far plane of at least
 * 16,800 blocks. The two depths agree only when the two far planes do. With the game's nearer
 * (four render distances, or the farthest figure), a distant mob had a greater depth than
 * Ecstatic's hill a hundred blocks behind it, and the hill was painted over the mob.
 * {@link #farPlane} gives Ecstatic's plane, which the game's projection takes while Ecstatic
 * draws ({@code GhostRenderer.farPlane}).
 */
public final class EcstaticLink {
    static final String PACKAGE = "com.angryalchemist.ecstatic.";
    private static final Links.Status STATUS = new Links.Status();
    private static boolean ready, linked;
    /** {@code LodRenderer.extendedFarPlaneBlocks(int)}: its far plane for a render distance; null once it failed. */
    private static volatile @Nullable MethodHandle farPlane;
    private static volatile float lastFarPlane;
    /** {@code Ecstatic.currentRingConfig(int)} then {@code RingConfig.outerBoundary(int)}: null when missing. */
    private static @Nullable MethodHandle rings, outerBoundary;
    /** {@code LodSettingsConfig.get()} then its {@code fogFalloffScale()}: null when missing, its default then. */
    private static @Nullable MethodHandle settings, falloff;
    /** Ecstatic's default fog falloff. */
    private static final float DEFAULT_FALLOFF = 0.95f;
    /** At the edge of its terrain, its fog has reached this far along its ramp, before the falloff. */
    private static final float FOG_AT_EDGE = 0.45f;

    private EcstaticLink() {
    }

    /** Whether Ecstatic is there, in a version whose classes this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("ecstatic")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            Class<?> mod = Class.forName(PACKAGE + "Ecstatic");
            Class<?> renderer = Class.forName(PACKAGE + "render.LodRenderer");
            linked = true;
            STATUS.set("linked");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try {
                Class<?> ring = Class.forName(PACKAGE + "lod.RingConfig");
                rings = lookup.findStatic(mod, "currentRingConfig", MethodType.methodType(ring, int.class))
                        .asType(MethodType.methodType(Object.class, int.class));
                outerBoundary = lookup.findVirtual(ring, "outerBoundary", MethodType.methodType(int.class, int.class))
                        .asType(MethodType.methodType(int.class, Object.class, int.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                rings = null;
                outerBoundary = null;
                STATUS.fallback("how far it draws unknown, distant mobs without its fog");
            }
            try {
                Class<?> config = Class.forName(PACKAGE + "render.LodSettingsConfig");
                settings = lookup.findStatic(config, "get", MethodType.methodType(config))
                        .asType(MethodType.methodType(Object.class));
                // Not public: read like its far plane.
                falloff = MethodHandles.privateLookupIn(config, MethodHandles.lookup())
                        .findVirtual(config, "fogFalloffScale", MethodType.methodType(float.class))
                        .asType(MethodType.methodType(float.class, Object.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                settings = null;
                falloff = null;
                STATUS.fallback("its fog falloff unknown, its default taken");
            }
            try {
                farPlane = MethodHandles.privateLookupIn(renderer, MethodHandles.lookup())
                        .findStatic(renderer, "extendedFarPlaneBlocks", MethodType.methodType(float.class, int.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                STATUS.fallback("its far plane unknown, its terrain may be drawn over distant mobs");
                LivingHorizonClient.LOGGER.warn("Ecstatic found, but not its far plane: its terrain may be drawn over distant mobs", e);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Ecstatic found, but not the version this mod knows", e);
        }
        return linked;
    }

    /**
     * Whether Ecstatic draws its terrain here: it only does in single player, in the overworld
     * (and not with the camera below y 35, which does not matter to the far plane).
     */
    static boolean draws(Minecraft minecraft) {
        Level level = minecraft.level;
        return linked() && minecraft.getSingleplayerServer() != null
                && level != null && level.dimension().equals(Level.OVERWORLD);
    }

    /**
     * Ecstatic's far plane this frame, in blocks, for the game's projection to take; 0 when it
     * draws nothing here or its plane cannot be read. On the game's thread.
     */
    public static float farPlane() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!draws(minecraft)) return 0f;
        MethodHandle handle = farPlane;
        if (handle == null) return 0f;
        try {
            float far = (float) handle.invokeExact(minecraft.options.getEffectiveRenderDistance());
            lastFarPlane = far;
            return far > 0f ? far : 0f;
        } catch (Throwable e) {
            farPlane = null;
            STATUS.fallback("its far plane unknown, its terrain may be drawn over distant mobs");
            LivingHorizonClient.LOGGER.warn("Ecstatic's far plane could not be read: its terrain may be drawn over distant mobs", e);
            return 0f;
        }
    }

    /**
     * Blocks across to which Ecstatic draws its terrain, its outermost ring, for this render
     * distance; 0 when it cannot say.
     */
    static double drawnDistance(int renderDistance) {
        MethodHandle config = rings, boundary = outerBoundary;
        if (config == null || boundary == null) return 0;
        try {
            Object ring = (Object) config.invokeExact(renderDistance);
            return (int) boundary.invokeExact(ring, 5) * 16.0;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Ecstatic's fog for the world's entities this frame, from where the game's starts; null
     * where it is not Ecstatic's to give. Ecstatic takes the game's fog away (it moves its start to
     * a hundred times the far distance, the entities' included) and fogs its own terrain, without a
     * shader pack, with the game's linear fog in its own shaders: every way round, from the render
     * distance to an end past its outermost ring, which its fog reaches at
     * {@link #FOG_AT_EDGE} times its falloff, in the game's fog colour. The entities' shaders fog
     * the same way, given the same start, end and shape.
     */
    static DhFog.@Nullable Smooth fog(float gameStart) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!draws(minecraft)) return null;
        int renderDistance = minecraft.options.getEffectiveRenderDistance();
        // Its own fog is not on: water, lava, blindness, a foggy biome or a boss keep the game's.
        if (!(gameStart > renderDistance * 16f * 50f)) return null;
        float[] ramp = fogRamp(renderDistance);
        return ramp == null ? null : new DhFog.Smooth(ramp[0], ramp[1], true, 1f);
    }

    /** Where Ecstatic's fog on its terrain starts and ends, {@code {start, end}}; null when it cannot say. */
    static float @Nullable [] fogRamp(int renderDistance) {
        float view = renderDistance * 16f;
        double edge = drawnDistance(renderDistance);
        if (edge <= view) return null;
        return new float[]{view, (float) (view + (edge - view) / FOG_AT_EDGE * falloff())};
    }

    /** Ecstatic's fog falloff setting, or its default. */
    private static float falloff() {
        MethodHandle get = settings, scale = falloff;
        if (get == null || scale == null) return DEFAULT_FALLOFF;
        try {
            float value = (float) scale.invokeExact((Object) get.invokeExact());
            return value > 0f ? value : DEFAULT_FALLOFF;
        } catch (Throwable e) {
            return DEFAULT_FALLOFF;
        }
    }

    /** Something {@link EcstaticSource} does without on this version. */
    static void fallback(String what) {
        STATUS.fallback(what);
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        linked();
        if (!linked) return STATUS.describe();
        if (!draws(Minecraft.getInstance())) return STATUS.describe() + "; draws nothing here (single player, overworld only)";
        float far = lastFarPlane;
        return far > 0f ? STATUS.describe() + "; far plane matched at " + Math.round(far) + " blocks" : STATUS.describe();
    }
}
