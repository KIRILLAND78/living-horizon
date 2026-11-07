package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the mod reaches of Voxy's renderer, in one place: its render system and pipeline, the
 * viewport it draws with, its depth textures and the depth blit it runs when a pack allows it.
 * {@link FarDepth} goes through it; {@link VoxySource} reads Voxy's world itself, but finds the
 * render system through {@link #renderSystemGetter()} and {@link #RENDER_SYSTEM} as here. Voxy
 * has no API: a class renamed turns the depth off for the session, said in the log and in
 * {@code /livinghorizon lod}.
 */
final class VoxyLink {
    static final String RENDER_SYSTEM = "me.cortex.voxy.client.core.VoxyRenderSystem";
    private static final Links.Status STATUS = new Links.Status();
    /** How often {@code VoxyRenderSystemMixin} ran: never, with Voxy drawing, means {@code renderOpaque} was renamed. */
    private static final AtomicLong TARGET_HOOK = new AtomicLong();
    private static boolean ready, linked;

    private static Method getNullable, getViewport, getDepthTex, transformBlitDepth;
    private static Field pipelineField, dataField, toVanillaField, depthBlitField, fbField, fbTranslucentField, textureIdField;
    private static Field projectionField, modelViewField, viewportWidthField, viewportHeightField, voxyProjectionField;
    private static Class<?> irisPipeline;
    /** Null in a Voxy that does not know {@code useViewportDims}. */
    private static @Nullable Field useViewportDimsField;
    /** Null in a Voxy without {@code RenderProperties}, whose depth is OpenGL's usual one. */
    private static @Nullable Field propertiesField;
    private static @Nullable Method zeroToOneMethod, reverseZMethod;

    private VoxyLink() {
    }

    /**
     * The class whose static {@code getNullable()} gives Voxy's render system: renamed
     * {@code IVoxyRenderSystemHolder} in 0.2.18, {@code IGetVoxyRenderSystem} before.
     */
    static Class<?> renderSystemGetter() throws ClassNotFoundException {
        try {
            return Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder");
        } catch (ClassNotFoundException e) {
            return Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
        }
    }

    /** Whether Voxy is there and its renderer is the one this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("voxy")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            link();
            linked = true;
            STATUS.set("linked");
        } catch (Throwable e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Voxy found, but its depth cannot be used: distant mobs show through its terrain", e);
        }
        return linked;
    }

    private static void link() throws ReflectiveOperationException {
        Class<?> getter = renderSystemGetter();
        Class<?> system = Class.forName(RENDER_SYSTEM);
        Class<?> pipeline = Class.forName("me.cortex.voxy.client.core.AbstractRenderPipeline");
        Class<?> depthBuffer = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer");
        Class<?> glTexture = Class.forName("me.cortex.voxy.client.core.gl.GlTexture");
        Class<?> viewport = Class.forName("me.cortex.voxy.client.core.rendering.Viewport");
        Class<?> blit = Class.forName("me.cortex.voxy.client.core.rendering.post.FullscreenBlit");
        Class<?> data = Class.forName("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData");
        irisPipeline = Class.forName("me.cortex.voxy.client.core.IrisVoxyRenderPipeline");
        getNullable = getter.getMethod("getNullable");
        getViewport = system.getMethod("getViewport");
        pipelineField = system.getDeclaredField("pipeline");
        pipelineField.setAccessible(true);
        dataField = irisPipeline.getDeclaredField("data");
        dataField.setAccessible(true);
        toVanillaField = data.getField("renderToVanillaDepth");
        depthBlitField = irisPipeline.getDeclaredField("depthBlit");
        depthBlitField.setAccessible(true);
        fbTranslucentField = irisPipeline.getField("fbTranslucent");
        getDepthTex = depthBuffer.getMethod("getDepthTex");
        textureIdField = glTexture.getField("id");
        projectionField = viewport.getField("vanillaProjection");
        modelViewField = viewport.getField("modelView");
        viewportWidthField = viewport.getField("width");
        viewportHeightField = viewport.getField("height");
        voxyProjectionField = viewport.getField("projection");
        fbField = pipeline.getField("fb");
        Class<?> properties = Links.optionalClass("me.cortex.voxy.client.core.RenderProperties");
        propertiesField = Links.optionalField(properties == null ? null : pipeline, "properties");
        zeroToOneMethod = Links.optionalMethod(properties, "isZero2One");
        reverseZMethod = Links.optionalMethod(properties, "isReverseZ");
        if (propertiesField == null || zeroToOneMethod == null || reverseZMethod == null) {
            propertiesField = null;
            STATUS.fallback("no render properties, OpenGL's usual depth");
        }
        useViewportDimsField = Links.optionalField(data, "useViewportDims");
        if (useViewportDimsField == null) STATUS.fallback("no useViewportDims, the whole screen");
        transformBlitDepth = pipeline.getDeclaredMethod("transformBlitDepth", blit, int.class, int.class, viewport, Matrix4f.class);
        transformBlitDepth.setAccessible(true);
    }

    // --- Its renderer, frame by frame --------------------------------------------------------

    /** Voxy's render system, or null while it is not rendering. */
    static @Nullable Object renderSystem() throws ReflectiveOperationException {
        return getNullable.invoke(null);
    }

    static Object pipeline(Object system) throws ReflectiveOperationException {
        return pipelineField.get(system);
    }

    /** Whether Voxy draws through a shader pack's pipeline. */
    static boolean underShaders(Object pipeline) {
        return irisPipeline.isInstance(pipeline);
    }

    /** Under a shader pack: whether the pack lets Voxy write its depth into the game's itself. */
    static boolean toVanillaDepth(Object pipeline) throws ReflectiveOperationException {
        return toVanillaField.getBoolean(dataField.get(pipeline));
    }

    /** Under a shader pack: whether it tells Voxy that it draws the world in a corner, smaller. */
    static boolean useViewportDims(Object pipeline) throws ReflectiveOperationException {
        return useViewportDimsField != null && useViewportDimsField.getBoolean(dataField.get(pipeline));
    }

    /** Whether a shader pack keeps Voxy's terrain out of the game's depth, and reads Voxy's own. */
    static boolean keepsOwnDepth(Object pipeline) throws ReflectiveOperationException {
        return underShaders(pipeline) && !toVanillaDepth(pipeline);
    }

    static @Nullable Object viewport(Object system) throws ReflectiveOperationException {
        return getViewport.invoke(system);
    }

    static int viewportWidth(Object viewport) throws ReflectiveOperationException {
        return viewportWidthField.getInt(viewport);
    }

    static int viewportHeight(Object viewport) throws ReflectiveOperationException {
        return viewportHeightField.getInt(viewport);
    }

    /** The game's projection, as Voxy was handed it this frame. */
    static Matrix4f gameProjection(Object viewport) throws ReflectiveOperationException {
        return (Matrix4f) projectionField.get(viewport);
    }

    static Matrix4f modelView(Object viewport) throws ReflectiveOperationException {
        return (Matrix4f) modelViewField.get(viewport);
    }

    /** Voxy's own projection, its planes farther out. */
    static Matrix4f voxyProjection(Object viewport) throws ReflectiveOperationException {
        return (Matrix4f) voxyProjectionField.get(viewport);
    }

    /** The OpenGL name of Voxy's opaque depth texture; 0 when it has none. */
    static int opaqueDepth(Object pipeline) throws ReflectiveOperationException {
        return depthTexture(fbField.get(pipeline));
    }

    /** The OpenGL name of Voxy's depth texture with its translucent terrain in it; 0 when it has none (shader pipeline only). */
    static int translucentDepth(Object pipeline) throws ReflectiveOperationException {
        return depthTexture(fbTranslucentField.get(pipeline));
    }

    private static int depthTexture(@Nullable Object framebuffer) throws ReflectiveOperationException {
        Object texture = framebuffer == null ? null : getDepthTex.invoke(framebuffer);
        return texture == null ? 0 : textureIdField.getInt(texture);
    }

    /** {@code {reversed, zeroToOne}} of Voxy's own depth; OpenGL's usual one where it does not say. */
    static boolean[] depthLayout(Object pipeline) throws ReflectiveOperationException {
        Object properties = propertiesField == null ? null : propertiesField.get(pipeline);
        if (properties == null) return new boolean[]{false, false};
        return new boolean[]{(boolean) reverseZMethod.invoke(properties), (boolean) zeroToOneMethod.invoke(properties)};
    }

    /**
     * Voxy's own depth blit, the one it runs when a pack allows it: its terrain's depth from
     * {@code texture} written into {@code target}, through {@code transform}.
     */
    static void blitDepth(Object pipeline, int texture, int target, Object viewport, Matrix4f transform)
            throws ReflectiveOperationException {
        transformBlitDepth.invoke(null, depthBlitField.get(pipeline), texture, target, viewport, transform);
    }

    /** Called by {@code VoxyRenderSystemMixin}: its hook still finds {@code renderOpaque}. */
    static void targetHooked() {
        TARGET_HOOK.incrementAndGet();
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        linked();
        if (!linked) return STATUS.describe();
        return STATUS.describe() + "; renderOpaque hook called " + TARGET_HOOK.get() + " times";
    }
}
