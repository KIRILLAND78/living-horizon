package fr.clixmods.livinghorizon.compat;

//? if >=1.21.6 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Voxy's fog on the world's entities, without a shader pack (Lodistance's too: see
 * {@link #lodistance}). Voxy fogs its terrain the way the
 * game fogs the world (the environmental fog: none at its start, full at its end, 1024 blocks in
 * a clear overworld), but stops it growing past a distance of its own: the render distance, at
 * least 320 blocks, times the square root of 3 (1.5 render distances before 0.2.6). The game's
 * fog on entities keeps growing: a mob past that distance was paler than the hill it stood on,
 * down to a flat patch of fog colour near the fog's end.
 *
 * <p>The game's shaders mix an entity's colour with the fog colour by the fog times that
 * colour's alpha, the fog going linearly from its start to its end. Ending it at Voxy's
 * distance and scaling the alpha by the fog Voxy reaches there gives Voxy's curve exactly: the
 * same fog up to that distance, all the way from the camera, and Voxy's from there on. The
 * game writes that fog next to its own ({@code FogRendererMixin}), and it is bound while the
 * world's entities are drawn, nowhere else: the sky, the clouds and the terrain keep the game's.
 * Only the enchantment glint and lightning, which read the fog without its alpha, fade a little
 * sooner.
 *
 * <p>Before 1.21.6 the game's fog is one value, set for whatever is drawn next ({@link GameFog}):
 * Distant Horizons' is set while the world's entities are drawn ({@link DhFog#fitSmooth}), and
 * the game's put back after. Voxy is not there before 1.21.6.
 */
public final class FarFog {
    /** Where our fog sits in the game's fog buffer, after its own: a multiple of any uniform offset alignment. */
    public static final int OFFSET = 256;
    /** A render distance fog starting past this is off: Voxy moves it to a billion blocks. */
    private static final float NO_RENDER_DISTANCE_FOG = 1e8f;

    private FarFog() {
    }

    /**
     * The fog the entities get, from the game's this frame: Voxy's changes in it, or Distant
     * Horizons' fog where it took the game's away ({@link DhFog}). Null where it is the game's:
     * switched off ({@code optVoxyFog}, {@code optDhFog}), neither mod's fog, a shader pack, the
     * game's render distance fog on, or Voxy's limit past the fog's end.
     */
    public static DhFog.@Nullable Ramps capped(float environmentalStart, float environmentalEnd,
                                              float renderDistanceStart, float renderDistanceEnd) {
        DhFog.Ramps ramps = choose(environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd);
        written = ramps == null ? "" : String.format(java.util.Locale.ROOT, "; every way %.0f..%.0f, across %.0f..%.0f blocks, at most %.2f",
                ramps.environmentalStart(), ramps.environmentalEnd(), ramps.renderDistanceStart(),
                ramps.renderDistanceEnd(), ramps.alpha());
        return ramps;
    }

    /**
     * The fog the entities get before 1.21.6, from the game's start this frame: Distant
     * Horizons' where it took the game's away. Null where it is the game's.
     */
    static DhFog.@Nullable Smooth cappedSmooth(float start) {
        DhFog.Smooth fog = chooseSmooth(start);
        written = fog == null ? "" : String.format(java.util.Locale.ROOT, "; %s %.0f..%.0f blocks, at most %.2f",
                fog.spherical() ? "every way" : "across", fog.start(), fog.end(), fog.alpha());
        return fog;
    }

    private static DhFog.@Nullable Smooth chooseSmooth(float start) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant()) return game("no distant figure shown");
        if (IrisLink.shaderPackOn()) return game("a shader pack fogs them");
        DhFog.Smooth ecstatic = EcstaticLink.fog(start);
        if (ecstatic != null) {
            source = "Ecstatic's";
            return ecstatic;
        }
        // A fog that ends close (water, lava, blindness), or Distant Horizons not drawing.
        if (start < NO_RENDER_DISTANCE_FOG) return game("its own fog is on");
        if (!config.optDhFog) return game("DH Fog Fix off, no fog at all");
        DhFog.Smooth fog = DhFog.nowSmooth();
        source = fog == null ? "the game's: none, Distant Horizons' fog unread or off (" + DhFog.why() + ")"
                : "Distant Horizons' (" + DhFog.describe() + ")";
        return fog;
    }

    private static DhFog.@Nullable Ramps choose(float environmentalStart, float environmentalEnd,
                                               float renderDistanceStart, float renderDistanceEnd) {
        FarConfig config = FarConfig.get();
        if (!Float.isNaN(limitForTests)) {
            if (!config.optVoxyFog) return game("Voxy Fog Fix off");
            float[] far = environmentalEnd - environmentalStart > 1 ? limited(environmentalStart, environmentalEnd, limitForTests) : null;
            source = "a limit for the tests";
            return far == null ? null : new DhFog.Ramps(environmentalStart, far[0], renderDistanceStart, renderDistanceEnd, far[1]);
        }
        if (!config.anyDistant()) return game("no distant figure shown");
        if (IrisLink.shaderPackOn()) return game("a shader pack fogs them");
        // Distant Horizons takes the game's fog away altogether, both ramps; Voxy only the render distance's.
        if (environmentalStart >= NO_RENDER_DISTANCE_FOG && renderDistanceStart >= NO_RENDER_DISTANCE_FOG) {
            if (!config.optDhFog) return game("DH Fog Fix off, no fog at all");
            DhFog.Ramps ramps = DhFog.now();
            source = ramps == null ? "the game's: none, Distant Horizons' fog unread or off (" + DhFog.why() + ")"
                    : "Distant Horizons' (" + DhFog.describe() + ")";
            return ramps;
        }
        DhFog.Ramps lodistance = lodistance(environmentalEnd, renderDistanceStart, renderDistanceEnd);
        if (lodistance != null) return lodistance;
        // A fog that ends close (water, lava, blindness) is left alone, as Voxy leaves it.
        if (!config.optVoxyFog) return game("Voxy Fog Fix off");
        if (renderDistanceStart < NO_RENDER_DISTANCE_FOG || environmentalEnd < 10
                || environmentalEnd - environmentalStart <= 1) return game("its own fog is on");
        float[] far = limited(environmentalStart, environmentalEnd, voxyLimit());
        if (far == null) return game("neither Voxy's nor Distant Horizons' fog");
        source = "Voxy's, stopping at " + Math.round(far[0]) + " blocks";
        return new DhFog.Ramps(environmentalStart, far[0], renderDistanceStart, renderDistanceEnd, far[1]);
    }

    /**
     * Lodistance's fog on the entities. It pushes the game's render distance fog out to where its
     * own terrain's fog runs, but leaves the game's other ramp, the atmospheric fog (1024 blocks in
     * a clear overworld), which its terrain does not have: a mob a thousand blocks away was a flat
     * patch of fog colour on clear hills. The entities get its ramp alone. Within the game's own
     * render distance they are then a little clearer than the game's terrain behind them, at most
     * by what that fog reaches at the render distance: the far ones matter more. Where the
     * atmospheric fog ends before the render distance (the Nether, thick weather, water), and with
     * Lodistance not drawing or not extending the fog, the game's is left. Null then.
     */
    private static DhFog.@Nullable Ramps lodistance(float environmentalEnd, float renderDistanceStart,
                                                   float renderDistanceEnd) {
        if (!LodistanceLink.linked() || LodistanceLink.drawing() != null || !LodistanceLink.extendsFog()) return null;
        float view = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
        if (environmentalEnd <= view || !(renderDistanceEnd > renderDistanceStart)) return null;
        source = "Lodistance's, " + Math.round(renderDistanceStart) + ".." + Math.round(renderDistanceEnd) + " blocks";
        return new DhFog.Ramps(NO_FOG, NO_FOG, renderDistanceStart, renderDistanceEnd, 1f);
    }

    /** A ramp that never starts: the shaders' fog is 0 short of its start. */
    private static final float NO_FOG = 1e9f;

    private static <T> @Nullable T game(String why) {
        source = "the game's: " + why;
        return null;
    }

    // --- What /livinghorizon lod says --------------------------------------------------------

    /** Whose fog the figures got last, and why. */
    private static volatile String source = "not written yet";
    /** The ramps the figures got last, in words. */
    private static volatile String written = "";
    /** Frames ours was bound, and frames it was not though written, and why the last one. */
    private static volatile long bound, missed;
    private static volatile String missedWhy = "";

    /** The fog the distant figures get, for {@code /livinghorizon lod}. */
    public static String status() {
        StringBuilder text = new StringBuilder(source).append(written);
        text.append("; bound ").append(bound).append(" frames");
        if (missed > 0) text.append(", not ").append(missed).append(" (").append(missedWhy).append(')');
        return text.toString();
    }

    /**
     * A fog from {@code start} to {@code end} that stops growing at {@code limit} blocks: its
     * end, then the factor on its colour's alpha. Null where the limit changes nothing.
     */
    static float @Nullable [] limited(float start, float end, float limit) {
        if (!(limit < end)) return null; // NaN too: no limit
        float reached = Math.max(0, (limit - start) / (end - start));
        return new float[]{Math.max(limit, start + 1), reached};
    }

    /** A limit in place of Voxy's, NaN for none. */
    private static float limitForTests = Float.NaN;

    /**
     * Makes the fog stop growing at {@code blocks}, as Voxy's does, with or without Voxy; NaN
     * for Voxy's again. For the client game tests, where Voxy cannot run.
     */
    public static void limitForTests(float blocks) {
        limitForTests = blocks;
    }

    // --- Voxy's settings ----------------------------------------------------------------------

    private static boolean linked, broken;
    private static @Nullable Field config, environmentalFog, hasFog;
    private static @Nullable Method renderingEnabled, fogMode;
    /** A Voxy before 0.2.6, which stops its fog at 1.5 render distances. */
    private static boolean early;

    /** Blocks past which Voxy's fog stops growing, or NaN where Voxy draws no fog. */
    private static float voxyLimit() {
        if (!linked) link();
        if (broken || config == null) return Float.NaN;
        try {
            Object settings = config.get(null);
            if (settings == null) return Float.NaN;
            if (renderingEnabled != null && !(boolean) renderingEnabled.invoke(settings)) return Float.NaN;
            boolean fog = fogMode != null && hasFog != null
                    ? hasFog.getBoolean(fogMode.invoke(settings))
                    : environmentalFog != null && environmentalFog.getBoolean(settings);
            if (!fog) return Float.NaN;
            float view = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            return early ? view * 1.5f : Math.max(view, 320f) * (float) Math.sqrt(3);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LivingHorizonClient.LOGGER.warn("Voxy's fog settings could not be read: distant mobs keep the game's fog", e);
            return Float.NaN;
        }
    }

    private static void link() {
        linked = true;
        if (!Platform.isModLoaded("voxy")) return;
        try {
            Class<?> type = Class.forName("me.cortex.voxy.client.config.VoxyConfig");
            config = type.getField("CONFIG");
            try {
                renderingEnabled = type.getMethod("isRenderingEnabled");
            } catch (NoSuchMethodException e) {
                renderingEnabled = null;
            }
            try {
                // From 0.2.19: fog, fade, both or neither.
                fogMode = type.getMethod("getFogMode");
                hasFog = Class.forName("me.cortex.voxy.client.core.NormalRenderPipeline$FogMode").getField("hasFog");
            } catch (NoSuchMethodException | ClassNotFoundException | NoSuchFieldException e) {
                fogMode = null;
                hasFog = null;
                environmentalFog = type.getField("useEnvironmentalFog");
            }
            try {
                type.getField("renderVanillaFog");
                early = true;
            } catch (NoSuchFieldException e) {
                early = false;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            LivingHorizonClient.LOGGER.warn("Voxy found, but not its fog settings: distant mobs keep the game's fog", e);
        }
    }

    // --- While the entities are drawn ---------------------------------------------------------

    /** The world's entities are submitted: the next drawing of features is theirs, not the hand's. */
    private static boolean armed;
    /** What drew the solid part of the world's entities: its translucent step follows, from 26.1. */
    private static @Nullable Object owner;

    /** Just before the world's entities are drawn; see {@code FarDepth.arm()}. */
    public static void arm() {
        armed = true;
    }

    //? if >=1.21.6 {
    /** The game's fog this frame, and ours next to it, or null where the game's stays. */
    private static @Nullable GpuBufferSlice world, far;
    /** The fog put back after the entities, while ours is bound. */
    private static @Nullable GpuBufferSlice saved;

    /** Where the game wrote this frame's fog, and ours if it wrote it. */
    public static void written(GpuBufferSlice game, @Nullable GpuBufferSlice ours) {
        world = game;
        far = ours;
    }

    /**
     * Just before the world's entities are drawn, by {@code by}: ours is bound in place of the
     * game's, and returned. Null where the game's stays.
     */
    public static @Nullable GpuBufferSlice begin(@Nullable Object by) {
        if (!armed) return null;
        armed = false;
        owner = by;
        return swap();
    }

    /** Just before the translucent parts of the world's entities are drawn, from 26.1: see {@link #begin}. */
    public static @Nullable GpuBufferSlice beginTranslucent(Object by) {
        return by == owner ? swap() : null;
    }

    private static @Nullable GpuBufferSlice swap() {
        if (far == null || saved != null) return null;
        GpuBufferSlice current = RenderSystem.getShaderFog();
        // The fog turned off (F3 + F), or this frame's fog not written yet.
        if (current == null || !current.equals(world)) {
            missed++;
            missedWhy = current == null ? "no fog bound" : "another fog bound than the game's";
            return null;
        }
        bound++;
        saved = current;
        RenderSystem.setShaderFog(far);
        return far;
    }

    /** Whether ours is bound: what is left of the entities in the buffers is drawn before {@link #end}. */
    public static boolean active() {
        return saved != null;
    }

    /** Just after the world's entities (or their translucent parts) are drawn: the game's fog is bound again, and returned. */
    public static @Nullable GpuBufferSlice end() {
        if (saved == null) return null;
        GpuBufferSlice back = saved;
        saved = null;
        RenderSystem.setShaderFog(back);
        return back;
    }

    /** Just after the translucent parts of the world's entities are drawn. */
    public static @Nullable GpuBufferSlice endTranslucent() {
        owner = null;
        return end();
    }
    //?} else {
    /*// Before 1.21.6: the game's fog, put back after the entities while Distant Horizons' is set.
    private static float @Nullable [] saved;

    // Just before the world's entities are drawn: Distant Horizons' fog is set in place of the game's.
    public static void begin(@Nullable Object by) {
        if (!armed) return;
        armed = false;
        owner = by;
        if (saved != null) return;
        float[] game = GameFog.read();
        DhFog.Smooth fog = cappedSmooth(game[0]);
        if (fog == null) return;
        bound++;
        saved = game;
        GameFog.set(new float[]{fog.start(), fog.end(), fog.spherical() ? 0 : 1, game[3], game[4], game[5], game[6] * fog.alpha()});
    }

    public static boolean active() {
        return saved != null;
    }

    // Just after the world's entities are drawn: the game's fog is set again.
    public static void end() {
        float[] back = saved;
        if (back == null) return;
        saved = null;
        GameFog.set(back);
    }
    *///?}
}
