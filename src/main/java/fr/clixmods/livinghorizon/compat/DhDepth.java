package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.render.DepthMath;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * What {@link FarDepth} needs to know about Distant Horizons to use its depth: the GL
 * texture it draws its terrain's depth into, and the projection that depth is made with.
 * The texture and the depth range come from its public API; the projection from the
 * parameters of its last render, which only its internals keep. All reached by reflection:
 * anything missing turns this off for the session, and the mobs show through the terrain
 * as they did before.
 */
final class DhDepth {
    private static boolean ready, broken, started, linked;
    private static String reason = "not started";

    private static MethodHandle renderProxy;
    private static Field projection, m22, m23, m32;
    private static Method depthTexture, name;
    /** All sixteen numbers of a matrix of Distant Horizons', row by row; null where it is missing. */
    private static @Nullable Method valuesAsArray;
    /**
     * From Distant Horizons 3.3.2: the layout of its depth. Before, null: OpenGL's usual one,
     * -1 to 1 with nothing at 1.
     */
    private static @Nullable Method depthRange, depthDirection;
    private static @Nullable Field farDepth;
    /** From 3.2: the parameters of its last render. Before, null: its projection is rebuilt from its planes. */
    private static @Nullable Field renderParams;
    /** Before 3.2: where its near and far planes come from, the override that changes the near one. */
    private static @Nullable Method nearPlane, farPlane, nearOverride;
    /** From API 7.1, Distant Horizons may draw through Blaze3D, without a shader pack: its depth is reached through a wrapper. */
    private static @Nullable Method blazeDepthTexture, wrappedObject;

    private DhDepth() {
    }

    static boolean started() {
        return started;
    }

    /** Why {@link #read()} had nothing, for {@code /livinghorizon lod}. */
    static String reason() {
        return reason;
    }

    static synchronized boolean available() {
        if (broken) return false;
        if (!ready) {
            ready = true;
            if (!Platform.isModLoaded("distanthorizons")) {
                broken = true;
                return false;
            }
            try {
                if (!DhLink.linked()) throw new IllegalStateException("its API is not the one this mod knows");
                link();
                linked = true;
            } catch (Throwable e) {
                broken = true;
                reason = "Distant Horizons found, but too old or unknown (its depth needs 2.3 or newer): " + e;
                LivingHorizonClient.LOGGER.warn("Distant Horizons found, but its depth cannot be used: distant mobs show through its terrain", e);
                return false;
            }
        }
        started = true;
        return true;
    }

    /**
     * Distant Horizons' depth this frame as an OpenGL texture, and the projection it is made
     * with; null with the reason in {@link #reason()} when there is none this frame.
     */
    static @Nullable DhFrame read() {
        try {
            Object proxy = (Object) renderProxy.invokeExact();
            if (proxy == null) return none("Distant Horizons has not started");
            Object result = depthTexture.invoke(proxy);
            int texture = DhLink.success(result) && DhLink.payload(result) instanceof Integer id ? id : blazeTexture(proxy);
            if (texture <= 0) return none("Distant Horizons has no depth texture");
            return frame(proxy, texture, null);
        } catch (Throwable e) {
            return failed(e);
        }
    }

    /**
     * As {@link #read()}, but with the game's own texture view of Distant Horizons' depth
     * instead of an OpenGL name: when it draws through Blaze3D, which works with Vulkan as with
     * OpenGL. Null with the reason when there is none.
     */
    static @Nullable DhFrame readView() {
        try {
            Object proxy = (Object) renderProxy.invokeExact();
            if (proxy == null) return none("Distant Horizons has not started");
            if (blazeDepthTexture == null || wrappedObject == null) return none("Distant Horizons is too old to draw with Vulkan");
            Object result = blazeDepthTexture.invoke(proxy);
            if (!DhLink.success(result)) return none("Distant Horizons has no depth texture");
            Object wrapper = DhLink.payload(result);
            // The texture, its view and its sampler.
            if (wrapper == null || !(wrappedObject.invoke(wrapper) instanceof Object[] objects) || objects.length < 2
                    || objects[1] == null) {
                return none("Distant Horizons has no depth texture");
            }
            return frame(proxy, 0, objects[1]);
        } catch (Throwable e) {
            return failed(e);
        }
    }

    private static @Nullable DhFrame failed(Throwable e) {
        broken = true;
        reason = "Distant Horizons did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Using Distant Horizons' depth turned off: it did not answer as expected", e);
        return null;
    }

    /** The projection Distant Horizons' depth is made with, around its texture or view. */
    private static @Nullable DhFrame frame(Object proxy, int texture, @Nullable Object view) throws ReflectiveOperationException {
        float a, b;
        Matrix4f inverse = null;
        if (renderParams != null) {
            Object params = renderParams.get(null);
            Object matrix = params == null ? null : projection.get(params);
            if (matrix == null) return none("Distant Horizons has not drawn yet");
            float c = m23.getFloat(matrix);
            // The matrix is stored one way or the other: see DepthMath.rowByRow.
            float[] read = DepthMath.fromMatrix(m22.getFloat(matrix), c, m32.getFloat(matrix));
            a = read[0];
            b = read[1];
            inverse = invert(matrix, DepthMath.rowByRow(c));
        } else {
            float[] planes = planes(proxy);
            if (planes == null) return none("Distant Horizons has no clip planes yet");
            float[] read = DepthMath.terms(planes[0], planes[1], false, false);
            a = read[0];
            b = read[1];
        }
        boolean signed = depthRange == null
                || "NEG_ONE_TO_POS_ONE".equals(String.valueOf(name.invoke(depthRange.invoke(proxy))));
        float[] iris = IrisLink.dhPlanes();
        if (iris != null) {
            inverse = null;
            // A shader pack draws Distant Horizons' terrain with Iris's own projection, whose
            // near plane is not the one Distant Horizons clamps for itself: OpenGL's -1..1.
            //? if >=26.2 {
            /*// From 26.2 Iris draws Distant Horizons' terrain with Iris's planes but in Distant
            // Horizons' own depth layout - reversed and 0..1 as the game's, empty at 0 - which it
            // reports: Iris's planes, in the layout Distant Horizons gives (signed set above).
            boolean reversed = depthDirection != null && farDepth != null
                    && farDepth.getFloat(depthDirection.invoke(proxy)) < 0.5f;
            float[] read = DepthMath.terms(iris[0], iris[1], reversed, !signed);
            *///?} else {
            signed = true;
            float[] read = DepthMath.terms(iris[0], iris[1], false, false);
            //?}
            a = read[0];
            b = read[1];
        }
        if (Math.abs(b) < 1.0e-6f) return none("Distant Horizons has no projection yet");
        float nothing = depthDirection == null || farDepth == null ? 1.0f : farDepth.getFloat(depthDirection.invoke(proxy));
        return new DhFrame(texture, view, a, b, signed, nothing, inverse);
    }

    /**
     * The inverse of Distant Horizons' whole projection this frame, from its depth back to the
     * view: a point at {@code (x, y, depth)} in its clip space, through it, gives {@code p} with
     * {@code -1 / p.w} the view-space {@code z} the game's own (bobbed) projection divides by.
     * It is the game's - view bobbing, nausea and all - with only its depth terms replaced by
     * Distant Horizons' own planes: the bobbing then moves its depth in a way the two terms
     * alone do not say, and a step made its terrain come and go in front of the mobs. Null when
     * it could not be read whole.
     */
    private static @Nullable Matrix4f invert(Object matrix, boolean rowByRow) {
        if (valuesAsArray == null) return null;
        try {
            Matrix4f inverse = new Matrix4f();
            return valuesAsArray.invoke(matrix) instanceof float[] values && DepthMath.invert(values, rowByRow, inverse)
                    ? inverse : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            valuesAsArray = null;
            DhLink.fallback("its projection could not be read whole: its depth may move with view bobbing");
            LivingHorizonClient.LOGGER.warn("Distant Horizons' projection could not be read whole: its depth may move with view bobbing", e);
            return null;
        }
    }

    /**
     * {@code {near, far}} as Distant Horizons sets them on its projection before 3.2, which
     * keeps no copy of it: its near plane, held to 7.5 blocks unless the height-based
     * override is on, and its far plane. Null when they are not known yet.
     */
    private static float @Nullable [] planes(Object proxy) throws ReflectiveOperationException {
        if (nearPlane == null || farPlane == null) return null;
        float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float near = ((Number) nearPlane.invoke(proxy, partialTick)).floatValue();
        if (nearOverride == null || ((Number) nearOverride.invoke(null)).floatValue() == -1.0f) near = Math.min(near, 7.5f);
        float far = ((Number) farPlane.invoke(null)).floatValue();
        return near > 0.0f && far > near ? new float[]{near, far} : null;
    }

    /**
     * The OpenGL name of the depth texture Distant Horizons draws into through Blaze3D, which
     * it does without a shader pack from 26.1; 0 when there is none, or not on OpenGL.
     */
    private static int blazeTexture(Object proxy) throws Throwable {
        if (blazeDepthTexture == null || wrappedObject == null) return 0;
        Object result = blazeDepthTexture.invoke(proxy);
        if (!DhLink.success(result)) return 0;
        Object wrapper = DhLink.payload(result);
        // The texture, its view and its sampler; the texture is the game's own GpuTexture.
        if (wrapper == null || !(wrappedObject.invoke(wrapper) instanceof Object[] objects) || objects.length == 0
                || objects[0] == null) {
            return 0;
        }
        try {
            return (int) objects[0].getClass().getMethod("glId").invoke(objects[0]);
        } catch (NoSuchMethodException e) {
            return 0; // not an OpenGL texture: Vulkan
        }
    }

    /**
     * Whether Distant Horizons draws its terrain with OpenGL itself rather than through the
     * game's GPU device: its rendering engine setting, which it turns to OpenGL whenever Iris
     * is installed. Its depth is then an OpenGL texture, and no texture of the game's.
     */
    static boolean drawsWithOpenGl() {
        if (broken || !linked) return false;
        try {
            Object proxy = (Object) renderProxy.invokeExact();
            if (proxy == null) return false;
            Object result = depthTexture.invoke(proxy);
            return DhLink.success(result) && DhLink.payload(result) instanceof Integer id && id > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Whether Iris draws with a shader pack: Distant Horizons then does not fade the game's picture. */
    static boolean shaderPackOn() {
        return linked && IrisLink.shaderPackOn();
    }

    private static @Nullable DhFrame none(String why) {
        reason = why;
        return null;
    }

    private static void link() throws ReflectiveOperationException {
        String api = DhLink.API;
        Class<?> proxy = Class.forName(api + "interfaces.render.IDhApiRenderProxy");
        Class<?> param = Class.forName(api + "methods.events.sharedParameterObjects.DhApiRenderParam");
        Class<?> matrix = Class.forName(api + "objects.math.DhApiMat4f");
        renderProxy = DhLink.delayed("renderProxy", proxy);
        depthTexture = Links.optionalMethod(proxy, "getDhDepthTextureGlId");
        if (depthTexture == null) {
            depthTexture = proxy.getMethod("getDhDepthTextureId"); // before 3.3
            DhLink.fallback("depth texture by its older name (before 3.3)");
        }
        depthRange = Links.optionalMethod(proxy, "getDepthRange");
        depthDirection = Links.optionalMethod(proxy, "getDepthDirection");
        farDepth = Links.optionalField(Links.optionalClass(api + "enums.config.EDhApiDepthDirection"), "farDepth");
        if (depthRange == null || depthDirection == null || farDepth == null) {
            depthRange = depthDirection = null; // before 3.3.2: OpenGL's usual depth
            farDepth = null;
            DhLink.fallback("no depth layout, OpenGL's usual one (before 3.3.2)");
        }
        name = Enum.class.getMethod("name");
        projection = param.getField("dhProjectionMatrix");
        m22 = matrix.getField("m22");
        m23 = matrix.getField("m23");
        m32 = matrix.getField("m32");
        valuesAsArray = Links.optionalMethod(matrix, "getValuesAsArray");
        if (valuesAsArray == null) DhLink.fallback("its two depth terms only: its depth may move with view bobbing");
        // Not API: the last parameters Distant Horizons rendered with, from 3.2.
        try {
            renderParams = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi").getDeclaredField("RENDER_PARAMS");
            renderParams.setAccessible(true);
        } catch (NoSuchFieldException e) {
            renderParams = null;
            DhLink.fallback("no render parameters, projection rebuilt from its planes (before 3.2)");
            // Before 3.2, its planes: the near one through the API, the far one and the override not.
            nearPlane = proxy.getMethod("getNearClipPlaneDistanceInBlocks", float.class);
            Class<?> util = Class.forName("com.seibel.distanthorizons.core.util.RenderUtil");
            farPlane = util.getMethod("getFarClipPlaneDistanceInBlocks");
            nearOverride = Links.firstMethod(util, "getHeightBasedNearClipOverride", "getHeightBasedNearClipOverrideBlockDistance");
        }
        blazeDepthTexture = Links.optionalMethod(proxy, "getDhDepthTextureBlazeWrapper");
        wrappedObject = Links.optionalMethod(Links.optionalClass(api + "interfaces.IDhApiUnsafeWrapper"), "getWrappedMcObject");
        if (blazeDepthTexture == null || wrappedObject == null) {
            blazeDepthTexture = null; // before API 7.1: OpenGL only
            DhLink.fallback("OpenGL depth only (before API 7.1)");
        }
    }
}
