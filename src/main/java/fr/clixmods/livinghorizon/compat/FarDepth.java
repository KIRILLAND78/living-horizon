package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.DepthMath;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.platform.Platform;
//? if >=1.21.5
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Voxy's terrain in the depth buffer while the entities are drawn, and only then: so that
 * a distant mob behind a Voxy tree is hidden by the tree, pixel by pixel, like any mob
 * behind any block.
 *
 * <p>Voxy writes its terrain's depth into the game's after drawing it - unless a shader
 * pack says not to, which Photon does ({@code excludeLodsFromVanillaDepth}): the pack
 * composites Voxy's terrain itself later, and finds it by where the game's depth is empty.
 * Leaving Voxy's depth there for the rest of the frame would break that. So, around the
 * one call that draws the entities:
 *
 * <ol>
 *   <li>before: the depth is saved, then Voxy's own depth blit - the one it runs when a
 *       pack allows it - writes its terrain into it, and that state is saved too;</li>
 *   <li>the entities are drawn, tested against Voxy's terrain like against any block;</li>
 *   <li>after: wherever the depth is still what Voxy wrote, the saved one comes back;
 *       wherever an entity was drawn, its depth stays.</li>
 * </ol>
 *
 * <p>From 26.1 the translucent parts of the entities have a step of their own, bracketed the
 * same way: see {@link #beforeTranslucent}.
 *
 * <p>Without a pack, Voxy already writes its depth for good and this does nothing.
 *
 * <p>Voxy's and Distant Horizons' own textures are read, never written, but for our own
 * figures' pixels: see {@link #intoVoxy()}. Voxy has no API for this: its classes are reached
 * by reflection, every GL state touched is put back, and any failure turns this off for the
 * session.
 */
public final class FarDepth {
    private static boolean ready, broken;

    /**
     * What this frame has done so far, step by step: everything here is forgotten as the level
     * starts to render ({@link #noteView}), and each step checks that the one before it ran
     * (see {@link #outOfOrder}). Only what lives longer - the textures, what the last frame
     * did, for the debug panel - is kept outside it.
     *
     * <p>The steps, in order: {@link #noteView} (the matrices); {@link #noteTarget} (Voxy draws
     * its terrain); {@link #arm} (the world's entities are submitted); {@link #before} and
     * {@link #after} around the solid step that draws them; from 26.1 {@link #beforeTranslucent}
     * and {@link #afterTranslucent} around their translucent step; {@link #beforeFade} and
     * {@link #afterFade} around each of Distant Horizons' fades; {@link #drawDebugView} with the
     * HUD. {@link #noteWorldProjection} comes before all of them, from the game renderer.
     */
    private static final class Frame {
        /** Armed between the entities being submitted and drawn, in the world's main pass. */
        boolean armed;
        /** {@link #before()} ran since the entities were armed. */
        boolean began;
        /** Whether {@link #before()} wrote the far terrain's depth, so that {@link #after()} must undo it. */
        boolean merged;
        /** The depth was copied without the far terrain's: for the debug view, or for Distant Horizons' fade. */
        boolean captured;
        /** Whether {@link #after()} writes our far figures into Voxy's own depth. */
        boolean intoVoxy;
        /** What {@code FarDepthGpu} is to do: see {@link #gpuMerge()}. */
        boolean gpuMerge, gpuFade;
        /** The entities of this frame are known: {@code written} before them, {@code drawn} after. */
        boolean fadeMask;
        /** Between {@link #beforeFade()} and {@link #afterFade()}: the depth to give back is in {@code faded}. */
        boolean fading;
        /** The translucent step: see {@link #beforeTranslucent}. */
        @Nullable Object lateOwner;
        boolean lateMerge, lateCopy, lateMerged, lateBegan;
        /** The depth view's "after the entities" is the copy after the translucent step this frame. */
        boolean lateShown;
        /**
         * Whether {@code lateWritten} and {@code lateDrawn} hold this frame's translucent step, before
         * and after it, for Distant Horizons' fade: it runs after that step and would paint its
         * terrain over the translucent figures (players, slimes) the solid step's copies miss.
         */
        boolean lateFade;

        void reset() {
            armed = began = merged = captured = intoVoxy = gpuMerge = gpuFade = fadeMask = fading = false;
            lateOwner = null;
            lateMerge = lateCopy = lateMerged = lateBegan = lateShown = lateFade = false;
        }
    }

    private static final Frame FRAME = new Frame();
    /** The first step found out of order this session, for {@code /livinghorizon lod}; null while none was. */
    private static @Nullable String outOfOrder;

    /**
     * A step that came where it should not: the hook before it is missing (renamed by the game
     * or by Voxy, Distant Horizons or Iris) or another mod changed the order. Said once, in the
     * log and in {@code /livinghorizon lod}: the far terrain's depth is likely wrong from then on.
     */
    private static void outOfOrder(String what) {
        if (outOfOrder != null) return;
        outOfOrder = what;
        LivingHorizonClient.LOGGER.warn("The far terrain's depth steps came out of order ({}): distant mobs may show through it", what);
    }

    /** The first step found out of order, for the game tests; null while none was. */
    public static @Nullable String outOfOrder() {
        return outOfOrder;
    }

    private static int target, depthTexture, width, height, format;
    private static int saved, written, drawn, faded;
    private static int program, vertexArray;
    private static int state;
    /** Why the last frame did nothing, for {@code /livinghorizon voxy}. */
    private static String reason = "no frame yet";

    private FarDepth() {
    }

    /** Where Voxy drew its terrain this frame, and over how much of it. */
    private static int voxyTarget, voxyWidth, voxyHeight;
    /** Our own framebuffer around the game's depth, when Voxy's could not be noted. */
    private static int ownTarget, ownTargetDepth;
    /**
     * How much of the depth texture the world is drawn to, from its bottom left corner: all of
     * it, or less under a shader pack's render scale (Photon's TAAU), where the pack draws the
     * world - entities included - in the corner and stretches it to the screen afterwards.
     */
    private static int pictureWidth, pictureHeight;

    /**
     * Voxy is about to draw its terrain into whatever is bound: the framebuffer the
     * entities' depth goes to as well. Nothing is bound between Minecraft's render passes,
     * so this is the moment to note it.
     */
    public static void noteTarget() {
        VoxyLink.targetHooked();
        if (!DepthFar.openGl()) return;
        // Only read by the merge, which runs under a pack that keeps Voxy's depth apart: asking
        // OpenGL costs a wait for the driver, so not on the frames that cannot use the answer.
        FarConfig config = FarConfig.get();
        try {
            if (!config.anyDistant() || !config.depthOcclusion || !keepsOwnDepth()) return;
        } catch (ReflectiveOperationException e) {
            return;
        }
        voxyTarget = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, VOXY_VIEWPORT);
        voxyWidth = VOXY_VIEWPORT[2];
        voxyHeight = VOXY_VIEWPORT[3];
    }

    private static final int[] VOXY_VIEWPORT = new int[4];

    /** Something is drawn past the render distance this frame, where Voxy's terrain could hide it. */
    private static boolean needed;

    /**
     * Called for anything drawn past the render distance. Without it the frame is left
     * alone: three copies of a full-screen depth texture are no small cost for nothing.
     */
    public static void needed() {
        needed = true;
    }

    /** The world's entities are submitted: the next drawing of features is theirs. */
    public static void arm() {
        FRAME.armed = true;
        FRAME.fadeMask = false;
        FRAME.lateFade = false;
        FRAME.gpuMerge = false;
        FRAME.gpuFade = false;
    }

    // --- Without OpenGL ---------------------------------------------------------------------

    /** This frame's view-projection, relative to the camera, as the world is drawn: {@link OcclusionQueries} reads it too. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    /** {@code FarDepthGpu} exists (26.3) and has run. */
    private static boolean gpuAvailable;

    public static void gpuAvailable() {
        if (gpuAvailable) return;
        gpuAvailable = true;
        gpuPath = workOutGpuPath() ? 1 : 0;
    }

    /** {@link #gpuPath()} as worked out at the start of this frame: 1 yes, 0 no, -1 before the first frame. */
    private static int gpuPath = -1;

    /**
     * Whether the far terrain's depth goes through the game's own GPU device this frame rather
     * than straight OpenGL: always with Vulkan; with OpenGL too over Distant Horizons without a
     * shader pack, where OpenGL calls in the middle of the world's pass left smears at the edge
     * of the game's chunks - unless Distant Horizons draws with OpenGL itself, as it does when
     * Iris is installed, and has no depth the game's device can read. Worked out once a frame
     * ({@link #noteView}): it asks Distant Horizons and Iris through reflection, and every
     * distant mob asks it.
     */
    public static boolean gpuPath() {
        int known = gpuPath;
        return known >= 0 ? known == 1 : workOutGpuPath();
    }

    private static boolean workOutGpuPath() {
        if (!DepthFar.openGl()) return true;
        return gpuAvailable && !available() && DhDepth.available() && !DhDepth.shaderPackOn()
                && !DhDepth.drawsWithOpenGl();
    }

    /**
     * What {@code FarDepthGpu} is to do this frame when the game draws with Vulkan, where
     * nothing here may talk to OpenGL: merge Distant Horizons' depth before the entities, keep
     * the figures out of its fade. It draws through the game's own GPU device instead.
     */
    public static boolean gpuMerge() {
        return FRAME.gpuMerge;
    }

    public static boolean gpuFade() {
        return FRAME.gpuFade;
    }

    public static Matrix4fc viewProjection() {
        return VIEW_PROJECTION;
    }

    /** What the GPU path did, for {@code /livinghorizon lod} and the debug panel. */
    public static void gpuResult(boolean done, String why, int w, int h) {
        mergedLastFrame = done;
        if (done) {
            state = 1;
            width = w;
            height = h;
        } else {
            state = 0;
            reason = why;
        }
    }

    /**
     * Whether Iris is drawing its shadow pass. From 26.2 that pass draws its entities through
     * the same steps as the world's, after the world's are submitted: left alone, it took the
     * bracket meant for the world's entities, and the world's were drawn without it.
     */
    public static boolean irisShadowPass() {
        return IrisLink.shadowPass();
    }

    /** Just before the entities are drawn. */
    public static void before() {
        long started = Profile.begin(Profile.Section.DEPTH_BEFORE);
        try {
            beforeEntities();
        } finally {
            Profile.end(Profile.Section.DEPTH_BEFORE, started);
        }
    }

    private static void beforeEntities() {
        if (!FRAME.armed || irisShadowPass()) return;
        FRAME.began = true;
        FRAME.merged = false;
        FRAME.captured = false;
        FRAME.intoVoxy = false;
        FRAME.lateShown = false;
        FarConfig config = FarConfig.get();
        // The depth view shows the far terrain merged even when nothing far is drawn this frame.
        boolean wanted = needed || !config.optLazyDepth || config.debugDepthView != 0;
        needed = false;
        try {
            if (gpuPath()) {
                // Distant Horizons alone: Voxy draws with OpenGL only. No shader pack either.
                boolean dh = wanted && config.anyDistant() && DhDepth.available() && !DhDepth.shaderPackOn();
                FRAME.gpuMerge = dh && config.depthOcclusion;
                FRAME.gpuFade = dh && config.optDhFade;
                if (!FRAME.gpuMerge) {
                    reason = wanted ? "the far terrain's depth is not usable" : "nothing far to hide this frame";
                    mergedLastFrame = false;
                }
                return;
            }
            boolean merging = wanted && config.anyDistant() && config.depthOcclusion;
            boolean queries = wanted && config.anyDistant() && config.hideOccludedMobs && config.optOcclusionQueries;
            // The merge and the test both draw: the GL state is saved once around the two.
            // What the merge touches: Voxy's blit everything, Distant Horizons' one texture unit.
            GlState gl = !merging || !queries ? null
                    : available() ? GlState.save(3, GlState.COLOUR | GlState.SCISSOR_BOX | GlState.READ_FRAMEBUFFER)
                    : GlState.save(1, 0);
            try {
                if (!wanted) {
                    reason = "nothing far to hide this frame";
                } else if (merging) {
                    if (available()) FRAME.merged = merge(false);
                    else if (!broken && DhDepth.available()) FRAME.merged = mergeDh(false);
                    else if (!broken && LodistanceLink.linked()) FRAME.merged = mergeLodistance(false);
                    // Why Distant Horizons is of no use (too old, most often), not what an earlier frame said.
                    else if (Platform.isModLoaded("distanthorizons")) reason = DhDepth.reason();
                    else if (Platform.isModLoaded("lodistance")) reason = "Lodistance is not the version this mod knows";
                    else if (FarViewLink.installed()) reason = "Far View draws into the game's depth itself: nothing to merge";
                    else reason = "neither Voxy, Distant Horizons nor Lodistance is usable";
                }
                // Distant Horizons' fade, without a shader pack, needs to know where the entities are.
                boolean dhFade = wanted && config.anyDistant() && config.optDhFade && DhDepth.available()
                        && !DhDepth.shaderPackOn();
                // A pack that keeps Voxy's depth apart reads it past the game's: our far figures go there too.
                boolean voxy = wanted && config.anyDistant() && config.optVoxyShadows && figures > 0 && keepsOwnDepth();
                // The depth view shows the game's depth even when there was nothing to merge.
                if (!FRAME.merged && (config.debugDepthView != 0 || dhFade || queries || voxy)) FRAME.captured = capture();
                FRAME.intoVoxy = voxy && (FRAME.merged || FRAME.captured);
                FRAME.fadeMask = dhFade && (FRAME.merged || FRAME.captured) && target == ownTarget;
                // The depth as it is now - far terrain in it, entities not yet - is what hides distant mobs.
                // Tested on its copy: the game's own texture, which Distant Horizons reads later in the
                // frame, is never attached anywhere else in the middle of the frame.
                if (queries && (FRAME.merged || FRAME.captured)) OcclusionQueries.run(written, width, height);
            } finally {
                if (gl != null) gl.restore();
            }
        } catch (Throwable e) {
            fail(e);
        }
        mergedLastFrame = FRAME.merged;
    }

    /** The projection the world is drawn with this frame, view bobbing in it, from 26.1. */
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    private static boolean worldProjection;

    /**
     * From 26.1 the level renderer is handed the camera's projection, and only the GPU the one
     * the world is drawn with: view bobbing, the hurt tilt and nausea multiplied in. Noted just
     * before, for {@link #noteView}.
     */
    public static void noteWorldProjection(Matrix4f projection) {
        WORLD_PROJECTION.set(projection);
        worldProjection = true;
    }

    /** The level starts to render: a new frame, and its matrices, for {@link OcclusionQueries} too. */
    public static void noteView(Matrix4f modelView, Matrix4f projection) {
        // What the last frame left open: a step after it never ran.
        if (FRAME.merged || FRAME.captured) outOfOrder("the entities' depth was not given back after them");
        else if (FRAME.lateMerged || FRAME.lateBegan) outOfOrder("the translucent step's depth was not given back after it");
        else if (FRAME.fading) outOfOrder("Distant Horizons' fade did not end");
        FRAME.reset();
        DepthFar.noteFrame();
        gpuPath = workOutGpuPath() ? 1 : 0;
        // Its field of view and depth layout come from the camera's own: the bobbing would sway them.
        DepthFar.noteProjection(projection);
        // Whatever is tested against the world's depth is projected the way the world was drawn.
        Matrix4f drawn = worldProjection ? WORLD_PROJECTION : projection;
        VIEW_PROJECTION.set(drawn).mul(modelView);
        OcclusionQueries.viewNoted();
    }

    /**
     * Whether {@link #after()} is about to give the depth back, or to read what the entities
     * drew. From 1.21.9 to 26.1 the call it follows only fills the world's buffers, and the
     * game draws what is left in them afterwards: drawn after the depth is given back, that
     * part of the entities showed through the far terrain (a villager's clothes without the
     * villager). The caller draws it first.
     */
    public static boolean givesBack() {
        return FRAME.armed && (FRAME.merged || FRAME.captured) && !irisShadowPass();
    }

    /** Just after the entities are drawn, where they have no translucent step of their own. */
    public static void after() {
        after(null);
    }

    /**
     * Just after the solid parts of the entities are drawn, by {@code owner}: its translucent
     * step follows, see {@link #beforeTranslucent}. Null where the call drew them all.
     */
    public static void after(@Nullable Object owner) {
        long started = Profile.begin(Profile.Section.DEPTH_AFTER);
        try {
            afterEntities(owner);
        } finally {
            Profile.end(Profile.Section.DEPTH_AFTER, started);
        }
    }

    private static void afterEntities(@Nullable Object owner) {
        if (!FRAME.armed || irisShadowPass()) return;
        if (!FRAME.began) outOfOrder("the entities were drawn without the step before them");
        FRAME.armed = false;
        FRAME.lateMerge = FRAME.lateCopy = FRAME.lateMerged = FRAME.lateFade = false;
        try {
            // Both draw: the GL state is saved once around the two.
            GlState gl = FRAME.merged && FRAME.intoVoxy
                    ? GlState.save(3, (target == ownTarget ? 0 : GlState.COLOUR) | GlState.SCISSOR_BOX) : null;
            try {
                if (FRAME.merged) restore(false);
                // Captured only: the copy after the entities is read by the debug view, Distant
                // Horizons' fade and our figures into Voxy's depth, and made for nothing otherwise.
                else if (FRAME.captured && (FRAME.intoVoxy || FRAME.fadeMask || FarConfig.get().debugDepthView != 0)) {
                    copy(depthTexture, drawn);
                }
                if (FRAME.intoVoxy) intoVoxy();
            } finally {
                if (gl != null) gl.restore();
            }
            FRAME.lateOwner = owner;
            FRAME.lateMerge = owner != null && FRAME.merged;
            // Distant Horizons' fade must know the translucent figures too (a player, a slime).
            FRAME.lateCopy = owner != null && !FRAME.merged && FRAME.captured
                    && (FarConfig.get().debugDepthView == 3 || FRAME.fadeMask);
        } catch (Throwable e) {
            fail(e);
        }
        FRAME.merged = false;
        FRAME.captured = false;
        FRAME.intoVoxy = false;
    }

    // --- The translucent step -----------------------------------------------------------------

    /** The translucent step's copies of the depth: before it, the far terrain's in it, after it. */
    private static int lateSaved, lateWritten, lateDrawn;

    private static boolean late(Object owner) {
        return owner == FRAME.lateOwner && (FRAME.lateMerge || FRAME.lateCopy) && !irisShadowPass();
    }

    /**
     * Just before the translucent parts of the entities are drawn, by {@code owner}. From 26.1
     * the translucent parts of the entities (a slime's outer layer) are drawn in a step of their
     * own, after the solid one. Iris copies the game's depth for the pack just before it, so the
     * far terrain's depth is out of it then: it goes back in at the start of that step, after
     * the copy, and out again at its end, the same way as for the solid step. Only for the one
     * whose solid step it follows: Iris draws the hand with another, in between.
     */
    public static void beforeTranslucent(Object owner) {
        long started = Profile.begin(Profile.Section.DEPTH_BEFORE_TRANSLUCENT);
        try {
            beforeLate(owner);
        } finally {
            Profile.end(Profile.Section.DEPTH_BEFORE_TRANSLUCENT, started);
        }
    }

    private static void beforeLate(Object owner) {
        if (!late(owner)) return;
        FRAME.lateBegan = true;
        try {
            if (FRAME.lateMerge) FRAME.lateMerged = mergeFar(true);
            else {
                prepareLate();
                copy(depthTexture, lateWritten);
            }
        } catch (Throwable e) {
            fail(e);
            FRAME.lateMerge = FRAME.lateCopy = FRAME.lateMerged = FRAME.lateBegan = false;
        }
    }

    /** Whether {@link #afterTranslucent} is about to give the depth back, or to copy it: see {@link #givesBack()}. */
    public static boolean givesBackTranslucent(Object owner) {
        return late(owner) && (FRAME.lateMerged || FRAME.lateCopy);
    }

    /** Just after the translucent parts of the entities are drawn, by {@code owner}. */
    public static void afterTranslucent(Object owner) {
        long started = Profile.begin(Profile.Section.DEPTH_AFTER_TRANSLUCENT);
        try {
            afterLate(owner);
        } finally {
            Profile.end(Profile.Section.DEPTH_AFTER_TRANSLUCENT, started);
        }
    }

    private static void afterLate(Object owner) {
        if (!late(owner)) return;
        if (!FRAME.lateBegan) outOfOrder("the translucent step was drawn without the step before it");
        try {
            if (FRAME.lateMerged) restore(true);
            else if (FRAME.lateCopy) copy(depthTexture, lateDrawn);
            FRAME.lateShown = FRAME.lateMerged || FRAME.lateCopy;
            FRAME.lateFade = FRAME.fadeMask && FRAME.lateShown;
        } catch (Throwable e) {
            fail(e);
        }
        FRAME.lateMerge = FRAME.lateCopy = FRAME.lateMerged = FRAME.lateBegan = false;
        FRAME.lateOwner = null;
    }

    /**
     * The game's depth once the whole world is drawn, for the depth view's fourth picture only:
     * made the first time that picture is chosen, copied only while it is.
     */
    private static int finished;

    /**
     * The world is drawn: the depth view's fourth picture is copied, with whatever was drawn after
     * the entities. Ecstatic draws its terrain then, tested against our figures' depth: this is
     * where its hills and our mobs can be seen at the same distances.
     */
    public static void worldDrawn() {
        if (FarConfig.get().debugDepthView != 4 || saved == 0 || depthTexture == 0 || !DepthFar.openGl() || gpuPath()) {
            return;
        }
        try {
            if (finished == 0) finished = texture();
            copy(depthTexture, finished);
        } catch (Throwable e) {
            LivingHorizonClient.LOGGER.warn("Copying the depth for the depth view failed", e);
            FarConfig.get().debugDepthView = 0;
        }
    }

    /** The translucent step's copies, made the first time they are needed. */
    private static void prepareLate() {
        if (lateSaved != 0) return;
        lateSaved = texture();
        lateWritten = texture();
        lateDrawn = texture();
    }

    private static boolean mergedLastFrame;

    /** Whether Voxy's depth was merged in the last frame, for the debug panel. */
    public static boolean mergedLastFrame() {
        return mergedLastFrame;
    }

    /**
     * The game's depth as it is, when there was nothing to merge (no Voxy, or Voxy writing into
     * it already): one copy, {@code written}, for the occlusion boxes, Distant Horizons' fade and
     * our figures into Voxy's depth. The copy before anything, {@code saved}, only the debug view
     * reads here: a full-screen copy is not made for nobody. Keep it that way: with the one-draw
     * occlusion ({@code OcclusionQueries}) this is part of the frame-rate gain the author measured
     * (70 to 100 FPS); a full-screen depth copy per frame for nothing is never to come back.
     */
    private static boolean capture() throws ReflectiveOperationException {
        target = ownTarget();
        if (target == 0) return false;
        depthTexture = ownTargetDepth;
        int w = GlCache.width(depthTexture), h = GlCache.height(depthTexture), f = GlCache.format(depthTexture);
        if (w <= 0 || h <= 0) return false;
        prepare(w, h, f);
        notePicture(w, h);
        if (FarConfig.get().debugDepthView != 0) copy(depthTexture, saved);
        copy(depthTexture, written);
        return true;
    }

    // --- Debug view ---------------------------------------------------------------------

    private static int viewProgram, viewTarget;
    /** Its uniforms, looked up once: each lookup waits for the driver. */
    private static int[] viewUniforms;

    /**
     * Draws one of the depth copies of the last frame in the bottom right corner of the
     * screen, near white to far black, the sky in dark blue: {@code 1} the game's depth,
     * {@code 2} with Voxy's terrain merged in, {@code 3} after the entities were drawn, {@code 4}
     * once the whole world is drawn (what a far terrain drawn after the entities added).
     * Called with the HUD, after the world, before the HUD itself is drawn over it.
     */
    public static void drawDebugView(int mode) {
        if (mode < 1 || mode > 4 || saved == 0 || mode == 4 && finished == 0 || !DepthFar.openGl() || gpuPath()) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            int colorId = mainTexture(false);
            if (colorId == 0) return;
            if (viewProgram == 0) {
                viewProgram = GlPrograms.fullScreen("Depth view", VIEW, "depth");
                viewUniforms = GlPrograms.uniforms(viewProgram, "area", "size", "planes", "terms", "depthLayout");
            }
            if (viewTarget == 0) viewTarget = GL45C.glCreateFramebuffers();
            // Every frame: see attachDepth.
            GL45C.glNamedFramebufferTexture(viewTarget, GL30C.GL_COLOR_ATTACHMENT0, colorId, 0);
            int screenW = minecraft.getMainRenderTarget().width, screenH = minecraft.getMainRenderTarget().height;
            int w = screenW * 2 / 5, h = screenH * 2 / 5, x = screenW - w - 8, y = 8;
            GlState gl = GlState.save(1, GlState.COLOUR);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, viewTarget);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthMask(false);
                GL11C.glColorMask(true, true, true, true);
                GL11C.glViewport(x, y, w, h);
                GL20C.glUseProgram(viewProgram);
                GL20C.glUniform4f(viewUniforms[0], x, y, w, h);
                // The part the world is drawn to, so that the view matches the screen under a render scale.
                GL20C.glUniform2f(viewUniforms[1], pictureWidth, pictureHeight);
                float[] terms = DepthFar.terms(minecraft), planes = DepthFar.planes(minecraft);
                GL20C.glUniform2f(viewUniforms[2], planes[0], VIEW_FAR);
                GL20C.glUniform2f(viewUniforms[3], terms[0], terms[1]);
                GL20C.glUniform2f(viewUniforms[4], DepthFar.reversed() ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f);
                GL30C.glBindVertexArray(vertexArray);
                GL33C.glBindSampler(0, 0);
                GL45C.glBindTextureUnit(0, mode == 1 ? saved : mode == 2 ? written : mode == 4 ? finished
                        : FRAME.lateShown ? lateDrawn : drawn);
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                gl.restore();
            }
        } catch (Throwable e) {
            LivingHorizonClient.LOGGER.warn("Drawing the depth view failed", e);
            FarConfig.get().debugDepthView = 0;
        }
    }

    /**
     * The distance the depth view shades black, the same every frame. The game's far plane moves
     * out to the farthest distant figure drawn ({@code GhostRenderer.farPlane}): shaded against
     * it, the view grew darker or lighter as far mobs were hidden or shown, its terrain unchanged.
     */
    static final float VIEW_FAR = 4096f;

    /** The depth view: the same as {@code far_depth_view.fsh} on 26.3, both from {@code far_depth.glsl}. */
    private static final String VIEW = """
            #version 330 core
            uniform sampler2D depth;
            uniform vec4 area;
            uniform vec2 size;
            uniform vec2 planes;
            uniform vec2 terms;  // the projection's two terms, see DepthFar.terms
            uniform vec2 depthLayout; // 1 when the depth is reversed, 1 when it is 0..1
            out vec4 color;
            """ + GlPrograms.DEPTH_MATH + """

            void main() {
                vec2 uv = (gl_FragCoord.xy - area.xy) / area.zw;
                float d = texelFetch(depth, ivec2(uv * size), 0).r;
                color = lhDepthShade(d, terms, planes, depthLayout.x > 0.5, depthLayout.y > 0.5);
            }
            """;

    /** Whether a shader pack is on, for the benchmark's report. */
    public static boolean shaderPack() {
        return IrisLink.shaderPackOn();
    }

    /** For {@code /livinghorizon voxy}. */
    public static String state() {
        String state = currentState();
        return outOfOrder == null ? state : state + " (steps out of order: " + outOfOrder + ")";
    }

    private static String currentState() {
        if (!ready && !DhDepth.started()) return "not started";
        if (broken) return "off";
        return switch (state) {
            // On in an earlier frame says nothing of this one: why this one did nothing, then.
            case 1 -> mergedLastFrame ? "on, " + width + "x" + height : "waiting: " + reason;
            case 2 -> "not needed (Voxy writes its own depth)";
            default -> "waiting: " + reason;
        };
    }

    // --- Before -------------------------------------------------------------------------

    /**
     * The OpenGL name of the main render target's depth or colour texture, 0 when it has
     * none OpenGL knows (Vulkan from 26.2). Before 1.21.5 the target held the names itself.
     */
    private static int mainTexture(boolean depth) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=1.21.5 {
        var texture = depth ? minecraft.getMainRenderTarget().getDepthTexture() : minecraft.getMainRenderTarget().getColorTexture();
        return texture instanceof GlTexture gl ? gl.glId() : 0;
        //?} else {
        /*return depth ? minecraft.getMainRenderTarget().getDepthTextureId() : minecraft.getMainRenderTarget().getColorTextureId();
        *///?}
    }

    /** A framebuffer of our own around the main render target's depth texture. */
    private static int ownTarget() {
        int depth = mainTexture(true);
        if (depth == 0) return 0;
        if (ownTargetDepth != depth) voxyWidth = voxyHeight = 0;
        ownTarget = attachDepth(ownTarget, depth);
        ownTargetDepth = depth;
        return ownTarget;
    }

    /**
     * Notes how much of the depth texture the world is drawn to, at most {@code w} by
     * {@code h}. A pack that renders smaller tells Voxy where with {@code useViewportDims}:
     * Voxy then reads the game's depth in the corner, at its own viewport's size, and so does
     * everything here - Voxy's depth written there, the depth view and the occlusion queries.
     */
    private static void notePicture(int w, int h) throws ReflectiveOperationException {
        pictureWidth = w;
        pictureHeight = h;
        if (!available()) {
            float fraction = IrisLink.packScale();
            pictureWidth = Math.round(w * fraction);
            pictureHeight = Math.round(h * fraction);
            return;
        }
        Object system = VoxyLink.renderSystem();
        if (system == null) return;
        Object pipeline = VoxyLink.pipeline(system);
        if (!VoxyLink.underShaders(pipeline) || !VoxyLink.useViewportDims(pipeline)) return;
        Object viewport = VoxyLink.viewport(system);
        if (viewport == null) return;
        int vw = VoxyLink.viewportWidth(viewport), vh = VoxyLink.viewportHeight(viewport);
        if (vw <= 0 || vh <= 0) return;
        pictureWidth = Math.min(vw, w);
        pictureHeight = Math.min(vh, h);
    }

    /** The width the world is drawn to in the depth this frame, for {@link OcclusionQueries}. */
    static int pictureWidth() {
        return pictureWidth;
    }

    /** The height the world is drawn to in the depth this frame, for {@link OcclusionQueries}. */
    static int pictureHeight() {
        return pictureHeight;
    }

    private static boolean skip(String why) {
        reason = why;
        return false;
    }

    /**
     * Writes Voxy's depth into the game's: {@code saved} before, {@code written} after, or the
     * late copies for the translucent step ({@code late}), in the framebuffer the solid step used.
     */
    private static boolean merge(boolean late) throws ReflectiveOperationException {
        Object system = VoxyLink.renderSystem();
        if (system == null) return skip("Voxy is not rendering");
        Object pipeline = VoxyLink.pipeline(system);
        if (!VoxyLink.keepsOwnDepth(pipeline)) {
            state = 2;
            return false;
        }
        Object viewport = VoxyLink.viewport(system);
        if (viewport == null) return skip("Voxy has no viewport");
        int voxyTexture = VoxyLink.translucentDepth(pipeline);
        if (voxyTexture == 0) return skip("Voxy has no depth texture");

        // The game's depth texture: the one Voxy drew into, or else the main one.
        if (!late) {
            target = voxyTarget != 0 ? voxyTarget : ownTarget();
            voxyTarget = 0;
        }
        if (target == 0) return skip("no framebuffer to draw into");
        if (target == ownTarget) {
            // Ours: the main depth texture was attached to it just now.
            depthTexture = ownTargetDepth;
        } else {
            if (GlCache.attachmentType(target, GL30C.GL_DEPTH_ATTACHMENT) != GL11C.GL_TEXTURE) {
                return skip("the depth is not a texture");
            }
            depthTexture = GlCache.attachmentName(target, GL30C.GL_DEPTH_ATTACHMENT);
        }
        int w = GlCache.width(depthTexture), h = GlCache.height(depthTexture), f = GlCache.format(depthTexture);
        if (w <= 0 || h <= 0) return skip("the depth texture has no size");
        prepare(w, h, f);
        notePicture(voxyWidth > 0 ? voxyWidth : w, voxyHeight > 0 ? voxyHeight : h);
        if (late) prepareLate();

        copy(depthTexture, late ? lateSaved : saved);
        // Voxy's blit is not ours, and draws into a framebuffer with colour: all it might touch is put back.
        GlState gl = GlState.save(3, GlState.COLOUR | GlState.SCISSOR_BOX | GlState.READ_FRAMEBUFFER);
        try {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glDepthMask(true);
            //? if >=26.3 {
            /*// Voxy's blit keeps the comparison it finds. From 26.3 the world's pass is already open
            // here, and what the game left set rejects Voxy's terrain: the game's own, nearer wins.
            GL11C.glDepthFunc(DepthFar.reversed() ? GL11C.GL_GEQUAL : GL11C.GL_LEQUAL);
            *///?}
            // Voxy's blit reads its picture in screen fractions: stretched over the part of the
            // texture the world is drawn to, it lands where the pack draws the entities.
            GL11C.glViewport(0, 0, pictureWidth, pictureHeight);
            Matrix4f transform = new Matrix4f(VoxyLink.gameProjection(viewport)).mul(VoxyLink.modelView(viewport));
            VoxyLink.blitDepth(pipeline, voxyTexture, target, viewport, transform);
        } finally {
            gl.restore();
        }
        copy(depthTexture, late ? lateWritten : written);
        state = 1;
        return true;
    }

    /**
     * Distant Horizons draws its terrain into a framebuffer of its own and never writes its
     * depth into the game's, so the entities would show through it. Its depth texture is
     * converted from its projection to the game's, and written wherever it is nearer than
     * what is there: the same merge as Voxy's, undone by {@link #restore} the same way.
     */
    private static boolean mergeDh(boolean late) throws ReflectiveOperationException {
        DhFrame dh = DhDepth.read();
        if (dh == null) return skip(DhDepth.reason());
        // Our own framebuffer, the main depth texture attached to it just now, and no colour.
        target = ownTarget();
        if (target == 0) return skip("no framebuffer to draw into");
        depthTexture = ownTargetDepth;
        int w = GlCache.width(depthTexture), h = GlCache.height(depthTexture), f = GlCache.format(depthTexture);
        if (w <= 0 || h <= 0) return skip("the depth texture has no size");
        int dhWidth = GlCache.width(dh.texture()), dhHeight = GlCache.height(dh.texture());
        if (dhWidth <= 0 || dhHeight <= 0) return skip("Distant Horizons has no depth yet");
        prepare(w, h, f);
        notePicture(w, h);
        if (late) prepareLate();
        prepareDhProgram();

        copy(depthTexture, late ? lateSaved : saved);
        // No colour attached: blending and the colour masks are left alone.
        GlState gl = GlState.save(1, 0);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(DepthFar.reversed() ? GL11C.GL_GREATER : GL11C.GL_LESS);
            GL11C.glDepthMask(true);
            GL11C.glViewport(0, 0, w, h);
            GL20C.glUseProgram(dhProgram);
            GL20C.glUniform2f(dhUniforms[0], w, h);
            GL20C.glUniform2f(dhUniforms[1], dhWidth, dhHeight);
            GL20C.glUniform4f(dhUniforms[2], dh.a(), dh.b(), dh.signedFlag(), dh.nothing());
            Minecraft minecraft = Minecraft.getInstance();
            float[] terms = DepthFar.terms(minecraft);
            GL20C.glUniform2f(dhUniforms[3], terms[0], terms[1]);
            GL20C.glUniform2f(dhUniforms[4], DepthFar.reversed() ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f);
            Matrix4fc inverse = dh.inverse();
            GL20C.glUniform1f(dhUniforms[5], inverse != null ? 1f : 0f);
            if (inverse != null) GL20C.glUniformMatrix4fv(dhUniforms[6], false, inverse.get(DH_INVERSE));
            GL30C.glBindVertexArray(vertexArray);
            GL33C.glBindSampler(0, dhSampler);
            GL45C.glBindTextureUnit(0, dh.texture());
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
        }
        copy(depthTexture, late ? lateWritten : written);
        state = 1;
        return true;
    }

    private static int dhProgram, dhSampler;
    /** Its uniforms, looked up once. */
    private static int[] dhUniforms;
    private static final float[] DH_INVERSE = new float[16];

    /** The program of {@link #DH_MERGE} and a sampler that reads a depth texture as it is, made once. */
    private static void prepareDhProgram() {
        if (dhProgram != 0) return;
        dhProgram = GlPrograms.fullScreen("Distant Horizons depth", DH_MERGE, "dhDepth");
        dhUniforms = GlPrograms.uniforms(dhProgram, "screen", "dhSize", "dh", "game", "depthLayout", "exact", "dhInverse");
        dhSampler = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(dhSampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(dhSampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(dhSampler, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
    }

    /** Merges the far terrain of whichever mod draws it: Voxy, Distant Horizons or Lodistance. */
    private static boolean mergeFar(boolean late) throws ReflectiveOperationException {
        if (available()) return merge(late);
        if (DhDepth.available()) return mergeDh(late);
        return mergeLodistance(late);
    }

    // --- Lodistance -----------------------------------------------------------------------

    private static int lodProgram;
    private static int[] lodUniforms;
    private static final Matrix4f LOD_MATRIX = new Matrix4f();
    private static final float[] LOD_INVERSE = new float[16];

    /**
     * Lodistance's terrain at the depth the game would give it, around the entities
     * ({@link LodistanceLink}). With Sodium its terrain is in the game's depth already, but
     * twice as far as it stands: the pixels that can only be its terrain are moved to their
     * real depth. Without Sodium it is in a texture of its own, written in like Distant
     * Horizons'. Either way {@link #restore} gives the game's depth back as it was.
     */
    private static boolean mergeLodistance(boolean late) throws ReflectiveOperationException {
        String why = LodistanceLink.drawing();
        if (why != null) return skip(why);
        // Not checked under a pack yet: what Iris does with its passes is unknown there.
        if (IrisLink.shaderPackOn()) return skip("Lodistance under a shader pack is not supported yet");
        boolean inGame = LodistanceLink.intoGameDepth();
        int lodTexture = inGame ? 0 : LodistanceLink.ownDepth();
        int[] lodSize = inGame ? null : LodistanceLink.ownSize();
        if (!inGame && (lodTexture == 0 || lodSize[0] <= 0 || lodSize[1] <= 0)) return skip("Lodistance has no depth yet");
        target = ownTarget();
        if (target == 0) return skip("no framebuffer to draw into");
        depthTexture = ownTargetDepth;
        int w = GlCache.width(depthTexture), h = GlCache.height(depthTexture), f = GlCache.format(depthTexture);
        if (w <= 0 || h <= 0) return skip("the depth texture has no size");
        prepare(w, h, f);
        notePicture(w, h);
        if (late) prepareLate();
        prepareDhProgram();
        if (lodProgram == 0) {
            lodProgram = GlPrograms.fullScreen("Lodistance depth", LOD_MERGE, "gameDepth");
            lodUniforms = GlPrograms.uniforms(lodProgram, "screen", "lod", "game", "depthLayout", "inverseView", "reach");
        }

        int before = late ? lateSaved : saved;
        copy(depthTexture, before);
        Minecraft minecraft = Minecraft.getInstance();
        float[] terms = DepthFar.terms(minecraft);
        boolean reversed = DepthFar.reversed(), zeroToOne = DepthFar.zeroToOne();
        // No colour attached: blending and the colour masks are left alone.
        GlState gl = GlState.save(1, 0);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthMask(true);
            GL11C.glViewport(0, 0, w, h);
            GL30C.glBindVertexArray(vertexArray);
            GL33C.glBindSampler(0, dhSampler);
            if (inGame) {
                // Its pixels only, which may move either way: nearer, or past the game's far plane.
                GL11C.glDepthFunc(GL11C.GL_ALWAYS);
                GL20C.glUseProgram(lodProgram);
                GL20C.glUniform2f(lodUniforms[0], w, h);
                float[] lod = LodistanceLink.terms();
                GL20C.glUniform2f(lodUniforms[1], lod[0], lod[1]);
                GL20C.glUniform2f(lodUniforms[2], terms[0], terms[1]);
                GL20C.glUniform2f(lodUniforms[3], reversed ? 1f : 0f, zeroToOne ? 1f : 0f);
                GL20C.glUniformMatrix4fv(lodUniforms[4], false, LOD_MATRIX.set(VIEW_PROJECTION).invert().get(LOD_INVERSE));
                // Farther than any of the game's own chunks reach: Lodistance leaves out those
                // within the render distance less a chunk, and the game draws none past it.
                GL20C.glUniform1f(lodUniforms[5], minecraft.options.getEffectiveRenderDistance() * 16f + 32f);
                GL45C.glBindTextureUnit(0, before);
            } else {
                // Nearer only: written over what the game drew where it is in front.
                GL11C.glDepthFunc(reversed ? GL11C.GL_GREATER : GL11C.GL_LESS);
                GL20C.glUseProgram(dhProgram);
                GL20C.glUniform2f(dhUniforms[0], w, h);
                GL20C.glUniform2f(dhUniforms[1], lodSize[0], lodSize[1]);
                // Its own depth: OpenGL's usual direction, empty at 1, laid out as the game's device is.
                GL20C.glUniform4f(dhUniforms[2], 0f, 0f, zeroToOne ? 0f : 1f, 1f);
                GL20C.glUniform2f(dhUniforms[3], terms[0], terms[1]);
                GL20C.glUniform2f(dhUniforms[4], reversed ? 1f : 0f, zeroToOne ? 1f : 0f);
                GL20C.glUniform1f(dhUniforms[5], 1f);
                GL20C.glUniformMatrix4fv(dhUniforms[6], false, LodistanceLink.ownProjection(LOD_MATRIX).invert().get(LOD_INVERSE));
                GL45C.glBindTextureUnit(0, lodTexture);
            }
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
        }
        copy(depthTexture, late ? lateWritten : written);
        state = 1;
        return true;
    }

    /**
     * Lodistance's depth, read from a copy of the game's, to the depth the game gives the same
     * distance. Its terrain was written with {@code lod} for the projection's two terms, which
     * puts it at twice its distance up to half the game's far plane, and in front of it past
     * that: past the far plane, it goes to the far plane, behind any figure. A pixel is taken for
     * its terrain only where, read as the game's own, it would lie farther across than any of
     * the game's chunks: everything nearer is left as it is.
     */
    private static final String LOD_MERGE = """
            #version 330 core
            uniform sampler2D gameDepth;
            uniform vec2 screen;
            uniform vec2 lod;         // the two terms Lodistance's depth was written with
            uniform vec2 game;        // the game's projection a and b, see DepthFar.terms
            uniform vec2 depthLayout; // 1 when the game's depth is reversed, 1 when it is 0..1
            uniform mat4 inverseView; // the view-projection's inverse, from the camera
            uniform float reach;      // blocks across: none of the game's chunks lies farther
            """ + GlPrograms.DEPTH_MATH + """

            void main() {
                float d = texelFetch(gameDepth, ivec2(gl_FragCoord.xy), 0).r;
                bool reversed = depthLayout.x > 0.5, zeroToOne = depthLayout.y > 0.5;
                if (reversed ? !(d > 0.0) : !(d < 1.0)) discard;
                // Where the pixel would be if it were the game's terrain.
                float ndc = zeroToOne ? d : d * 2.0 - 1.0;
                vec4 p = inverseView * vec4(gl_FragCoord.xy / screen * 2.0 - 1.0, ndc, 1.0);
                if (!(abs(p.w) > 0.0)) discard;
                if (!(length(p.xz / p.w) > reach)) discard;
                float w = lhDistance(d, lod, zeroToOne);
                if (!(w > 0.0)) discard;
                gl_FragDepth = clamp(lhGameDepth(-w, game, zeroToOne), 0.0, 1.0);
            }
            """;

    /**
     * Distant Horizons' depth to the game's: back to a distance in front of the camera
     * through Distant Horizons' own projection, then forward through the game's. The same as
     * {@code far_depth_merge.fsh} on 26.3, both from {@code far_depth.glsl}.
     */
    private static final String DH_MERGE = """
            #version 330 core
            uniform sampler2D dhDepth;
            uniform vec2 screen;
            uniform vec2 dhSize;
            uniform vec4 dh;   // projection a, projection b, 1 when the depth is -1..1, depth of nothing
            uniform vec2 game; // the game's projection a and b, see DepthFar.terms
            uniform vec2 depthLayout; // 1 when the game's depth is reversed, 1 when it is 0..1
            uniform float exact;      // 1 when Distant Horizons' whole projection is known
            uniform mat4 dhInverse;   // its inverse: view bobbing moves its depth too
            """ + GlPrograms.DEPTH_MATH + """

            void main() {
                float d = texelFetch(dhDepth, ivec2(gl_FragCoord.xy / screen * dhSize), 0).r;
                if (d == dh.w) discard;
                float z = lhDhViewZ(d, dh, exact > 0.5, dhInverse, gl_FragCoord.xy / screen);
                if (!(z < 0.0)) discard;
                float depth = lhGameDepth(z, game, depthLayout.y > 0.5);
                if (depthLayout.x > 0.5 ? !(depth > 0.0) : !(depth < 1.0)) discard;
                gl_FragDepth = clamp(depth, 0.0, 1.0);
            }
            """;

    // --- After --------------------------------------------------------------------------

    /** Gives the depth back after the solid step, or after the translucent one ({@code late}). */
    private static void restore(boolean late) {
        int before = late ? lateSaved : saved, withFar = late ? lateWritten : written, after = late ? lateDrawn : drawn;
        copy(depthTexture, after);
        // The framebuffer Voxy drew into has colour; ours has none, and needs neither blending
        // nor the colour masks touched.
        boolean colour = target != ownTarget;
        GlState gl = GlState.save(3, colour ? GlState.COLOUR : 0);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            if (colour) {
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glColorMask(false, false, false, false);
            }
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(GL11C.GL_ALWAYS);
            GL11C.glDepthMask(true);
            GL11C.glViewport(0, 0, width, height);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vertexArray);
            for (int unit = 0; unit < 3; unit++) {
                GL33C.glBindSampler(unit, 0);
                GL45C.glBindTextureUnit(unit, unit == 0 ? before : unit == 1 ? withFar : after);
            }
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
        }
    }

    // --- Into Voxy's depth -------------------------------------------------------------------

    /** Our figures drawn past the render distance this frame: a sphere each, around the camera. */
    private static final float[] FIGURES = new float[4 * 512];
    private static int figures;

    /** A new frame: the figures of the last one are forgotten. */
    public static void beginFigures() {
        figures = 0;
    }

    /**
     * A figure drawn past the render distance this frame: a sphere {@code radius} blocks wide
     * around {@code (x, y, z)} from the camera. Only there is Voxy's depth ever written.
     */
    public static void figure(double x, double y, double z, double radius) {
        if (figures >= FIGURES.length / 4) return;
        int i = figures++ * 4;
        FIGURES[i] = (float) x;
        FIGURES[i + 1] = (float) y;
        FIGURES[i + 2] = (float) z;
        FIGURES[i + 3] = (float) radius;
    }

    /** The rectangles of Voxy's picture our figures cover this frame: see {@link DepthMath#rectangles}. */
    private static final int[] RECTANGLES = new int[4 * 512];

    /** Our framebuffers around Voxy's opaque and translucent depth textures. */
    private static int voxyOpaqueTarget, voxyTranslucentTarget;
    private static int intoVoxyProgram;
    /** Its uniforms, looked up once. */
    private static int[] intoVoxyUniforms;
    private static final Matrix4f FROM_GAME = new Matrix4f();
    private static final float[] FROM_GAME_VALUES = new float[16], TO_VOXY_VALUES = new float[16];

    /** Voxy draws under a shader pack that keeps its terrain out of the game's depth. */
    private static boolean keepsOwnDepth() throws ReflectiveOperationException {
        if (!available()) return false;
        Object system = VoxyLink.renderSystem();
        return system != null && VoxyLink.keepsOwnDepth(VoxyLink.pipeline(system));
    }

    /**
     * Writes our far figures into Voxy's opaque and translucent depth, so that a pack that
     * shades past the render distance from Voxy's depth (Photon's shadows) finds the mob there,
     * not the terrain behind it. Only the pixels where one of our figures was just drawn, in
     * front of everything, inside the rectangles they cover and past the render distance:
     * anywhere else Voxy's depth, and so what Voxy draws, is left exactly as it was.
     * Voxy's pixels are the game's, one for one, from the bottom left corner; the depth goes
     * back to a distance in front of the camera through the game's projection, then forward
     * through Voxy's.
     */
    private static void intoVoxy() throws ReflectiveOperationException {
        Object system = VoxyLink.renderSystem();
        if (system == null) return;
        Object pipeline = VoxyLink.pipeline(system);
        Object viewport = VoxyLink.viewport(system);
        if (!VoxyLink.underShaders(pipeline) || viewport == null) return;
        int opaque = VoxyLink.opaqueDepth(pipeline), translucent = VoxyLink.translucentDepth(pipeline);
        int vw = VoxyLink.viewportWidth(viewport), vh = VoxyLink.viewportHeight(viewport);
        if (opaque == 0 || translucent == 0 || vw <= 0 || vh <= 0) return;
        int count = DepthMath.rectangles(VIEW_PROJECTION, FIGURES, figures, RECTANGLES, vw, vh);
        if (count <= 0) return;
        boolean[] layout = VoxyLink.depthLayout(pipeline);
        boolean reversed = layout[0], zeroToOne = layout[1];
        float[] fromGame = FROM_GAME.set(VoxyLink.gameProjection(viewport)).invert().get(FROM_GAME_VALUES);
        float[] toVoxy = VoxyLink.voxyProjection(viewport).get(TO_VOXY_VALUES);
        if (intoVoxyProgram == 0) {
            intoVoxyProgram = GlPrograms.fullScreen("Voxy depth", INTO_VOXY, "written", "drawn");
            intoVoxyUniforms = GlPrograms.uniforms(intoVoxyProgram, "size", "fromGame", "toVoxy", "beyond", "depthLayout");
        }
        voxyOpaqueTarget = attachDepth(voxyOpaqueTarget, opaque);
        voxyTranslucentTarget = attachDepth(voxyTranslucentTarget, translucent);
        // Our framebuffers, depth only: blending and the colour masks are left alone.
        GlState gl = GlState.save(2, GlState.SCISSOR_BOX);
        try {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(reversed ? GL11C.GL_GREATER : GL11C.GL_LESS);
            GL11C.glDepthMask(true);
            GL11C.glViewport(0, 0, vw, vh);
            GL20C.glUseProgram(intoVoxyProgram);
            GL20C.glUniform2f(intoVoxyUniforms[0], vw, vh);
            GL20C.glUniformMatrix4fv(intoVoxyUniforms[1], false, fromGame);
            GL20C.glUniformMatrix4fv(intoVoxyUniforms[2], false, toVoxy);
            // Nothing the game draws itself is past its render distance: only our figures are.
            float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f - 16f;
            GL20C.glUniform1f(intoVoxyUniforms[3], renderDistance);
            GL20C.glUniform4f(intoVoxyUniforms[4],
                    DepthFar.reversed() ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f, reversed ? 1f : 0f, zeroToOne ? 1f : 0f);
            GL30C.glBindVertexArray(vertexArray);
            for (int unit = 0; unit < 2; unit++) {
                GL33C.glBindSampler(unit, 0);
                GL45C.glBindTextureUnit(unit, unit == 0 ? written : drawn);
            }
            GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            for (int framebuffer : new int[]{voxyOpaqueTarget, voxyTranslucentTarget}) {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
                for (int i = 0; i < count; i++) {
                    GL11C.glScissor(RECTANGLES[i * 4], RECTANGLES[i * 4 + 1], RECTANGLES[i * 4 + 2], RECTANGLES[i * 4 + 3]);
                    GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
                }
            }
        } finally {
            gl.restore();
        }
    }

    /**
     * A framebuffer of our own around a depth texture: {@code framebuffer}, or a new one when
     * 0, with {@code depth} attached again on every call. A texture deleted and made again -
     * the window resized - often gets its old name back, and a framebuffer that was not bound
     * at that moment keeps the deleted texture: kept by its name only, everything drawn
     * through it went to a picture of the old size that nobody reads any more.
     */
    static int attachDepth(int framebuffer, int depth) {
        if (framebuffer == 0) framebuffer = GL45C.glCreateFramebuffers();
        int format = GlCache.format(depth);
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8
                || format == GL30C.GL_DEPTH_STENCIL;
        if (!stencil) GL45C.glNamedFramebufferTexture(framebuffer, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, 0, 0);
        GL45C.glNamedFramebufferTexture(framebuffer,
                stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT, depth, 0);
        return framebuffer;
    }

    /** A screen-filling triangle that writes, in Voxy's projection, wherever an entity was drawn. */
    private static final String INTO_VOXY = """
            #version 330 core
            uniform sampler2D written;
            uniform sampler2D drawn;
            uniform vec2 size;     // Voxy's viewport
            uniform mat4 fromGame; // the inverse of the game's projection
            uniform mat4 toVoxy;   // Voxy's projection
            uniform vec4 depthLayout; // reversed, 0..1: the game's, then Voxy's
            uniform float beyond;     // the game's render distance, in blocks
            void main() {
                ivec2 at = ivec2(gl_FragCoord.xy);
                float entity = texelFetch(drawn, at, 0).r;
                if (entity == texelFetch(written, at, 0).r) discard;
                if (depthLayout.x > 0.5 ? !(entity > 0.0) : !(entity < 1.0)) discard;
                float z = depthLayout.y > 0.5 ? entity : entity * 2.0 - 1.0;
                vec4 view = fromGame * vec4(gl_FragCoord.xy / size * 2.0 - 1.0, z, 1.0);
                if (length(view.xyz / view.w) < beyond) discard;
                vec4 clip = toVoxy * vec4(view.xyz / view.w, 1.0);
                float depth = clip.z / clip.w;
                if (depthLayout.w < 0.5) depth = depth * 0.5 + 0.5;
                // Nearer than Voxy's near plane, or past its far one: nothing to write.
                if (!(depth > 0.0 && depth < 1.0)) discard;
                gl_FragDepth = depth;
            }
            """;

    // --- Distant Horizons' fade -------------------------------------------------------------

    private static int unmaskProgram, unmaskEmpty;

    /**
     * Distant Horizons is about to fade the game's picture into its terrain. Without a
     * shader pack, it takes anything the game drew past the render distance for terrain
     * left over, and paints its own terrain over it - the distant mobs included. For the
     * fade, the pixels of the entities read as sky, which it leaves alone.
     */
    public static void beforeFade() {
        DhLink.fadeHooked();
        long started = Profile.begin(Profile.Section.DEPTH_BEFORE_FADE);
        try {
            beforeUnmask();
        } finally {
            Profile.end(Profile.Section.DEPTH_BEFORE_FADE, started);
        }
    }

    private static void beforeUnmask() {
        if (FRAME.armed && (FRAME.merged || FRAME.captured)) outOfOrder("Distant Horizons' fade ran while the entities were drawn");
        if (!FRAME.fadeMask || FRAME.fading || !DepthFar.openGl()) return;
        try {
            copy(depthTexture, faded);
            // Our own framebuffer (the mask is only made there), depth only: blending and the
            // colour masks are left alone.
            GlState gl = GlState.save(3, 0);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthFunc(GL11C.GL_ALWAYS);
                GL11C.glDepthMask(true);
                GL11C.glViewport(0, 0, width, height);
                if (unmaskProgram == 0) {
                    unmaskProgram = GlPrograms.fullScreen("Fade mask", UNMASK, "current", "written", "drawn");
                    unmaskEmpty = GL20C.glGetUniformLocation(unmaskProgram, "empty");
                }
                GL20C.glUseProgram(unmaskProgram);
                GL20C.glUniform1f(unmaskEmpty, DepthFar.reversed() ? 0f : 1f);
                GL30C.glBindVertexArray(vertexArray);
                for (int unit = 0; unit < 3; unit++) {
                    GL33C.glBindSampler(unit, 0);
                    GL45C.glBindTextureUnit(unit, unit == 0 ? faded : unit == 1 ? written : drawn);
                }
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
                // The translucent step's figures, drawn after the solid ones: tested against the same copy.
                if (FRAME.lateFade) {
                    GL45C.glBindTextureUnit(1, lateWritten);
                    GL45C.glBindTextureUnit(2, lateDrawn);
                    GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
                }
            } finally {
                gl.restore();
            }
            FRAME.fading = true;
        } catch (Throwable e) {
            FRAME.fadeMask = false;
            fail(e);
        }
    }

    /** Distant Horizons' fade is done: the entities' depth comes back. */
    public static void afterFade() {
        long started = Profile.begin(Profile.Section.DEPTH_AFTER_FADE);
        try {
            afterUnmask();
        } finally {
            Profile.end(Profile.Section.DEPTH_AFTER_FADE, started);
        }
    }

    private static void afterUnmask() {
        if (!FRAME.fading) return;
        FRAME.fading = false;
        try {
            copy(faded, depthTexture);
        } catch (Throwable e) {
            FRAME.fadeMask = false;
            fail(e);
        }
    }

    /**
     * A screen-filling triangle that empties the depth wherever an entity was drawn and
     * nothing was drawn over it since (translucent terrain, which the fade must still see).
     */
    private static final String UNMASK = """
            #version 330 core
            uniform sampler2D current;
            uniform sampler2D written;
            uniform sampler2D drawn;
            uniform float empty;
            void main() {
                ivec2 at = ivec2(gl_FragCoord.xy);
                float entity = texelFetch(drawn, at, 0).r;
                if (entity == texelFetch(written, at, 0).r || texelFetch(current, at, 0).r != entity) discard;
                gl_FragDepth = empty;
            }
            """;

    // --- GPU objects ----------------------------------------------------------------------

    private static void copy(int from, int to) {
        GL43C.glCopyImageSubData(from, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, to, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
    }

    private static void prepare(int w, int h, int f) {
        if (program == 0) {
            program = GlPrograms.fullScreen("Depth restore", RESTORE, "saved", "written", "drawn");
            vertexArray = GL45C.glCreateVertexArrays();
        }
        if (saved != 0 && w == width && h == height && f == format) return;
        for (int texture : new int[]{saved, written, drawn, faded, lateSaved, lateWritten, lateDrawn, finished}) {
            if (texture == 0) continue;
            GL11C.glDeleteTextures(texture);
            GlCache.deleted(texture);
        }
        lateSaved = lateWritten = lateDrawn = finished = 0;
        FRAME.lateShown = FRAME.lateFade = false;
        width = w;
        height = h;
        format = f;
        saved = texture();
        written = texture();
        drawn = texture();
        faded = texture();
    }

    private static int texture() {
        int texture = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        if (format == GL11C.GL_DEPTH_COMPONENT || format == GL30C.GL_DEPTH_STENCIL) {
            // Before 1.21.5 the game's depth has an unsized format, which storage refuses and
            // leaves the copy without a size: it is made the way the game makes its own, so
            // that copying between the two stays allowed.
            boolean stencil = format == GL30C.GL_DEPTH_STENCIL;
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, format, width, height, 0, format,
                    stencil ? GL30C.GL_UNSIGNED_INT_24_8 : GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        } else {
            GL45C.glTextureStorage2D(texture, 1, format, width, height);
        }
        GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL45C.glTextureParameteri(texture, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        GL45C.glTextureParameteri(texture, GL43C.GL_DEPTH_STENCIL_TEXTURE_MODE, GL11C.GL_DEPTH_COMPONENT);
        GlCache.made(texture, width, height, format);
        return texture;
    }

    /** A screen-filling triangle that puts back the saved depth wherever no entity was drawn. */
    private static final String RESTORE = """
            #version 330 core
            uniform sampler2D saved;
            uniform sampler2D written;
            uniform sampler2D drawn;
            void main() {
                ivec2 at = ivec2(gl_FragCoord.xy);
                float now = texelFetch(drawn, at, 0).r;
                // Still what Voxy wrote: no entity here, give the depth back as it was.
                gl_FragDepth = now == texelFetch(written, at, 0).r ? texelFetch(saved, at, 0).r : now;
            }
            """;

    /**
     * The GL state a step changes, as it was, so that the game, Iris and Voxy find it again. Each
     * value read waits for the driver, so only what the step touches is read: texture units 0 to
     * {@code units - 1}, and the extras it asks for:
     *
     * <ul>
     *   <li>{@link #COLOUR}: blending and the colour mask, per draw buffer. From 26.3 the game
     *       caches them per buffer and skips the call when its cache says the value is already
     *       set, so a state put back for every buffer at once, from the first one's value, leaves
     *       its cache wrong for the others. A shader pack draws into several at a time: the
     *       water's data was written with the wrong blending, and Photon no longer took it for
     *       water. A step drawing into a framebuffer of ours, with no colour, never touches them;</li>
     *   <li>{@link #SCISSOR_BOX}, for a step that moves it, not just the scissor test;</li>
     *   <li>{@link #READ_FRAMEBUFFER}: ours never bind one, Voxy's blit might.</li>
     * </ul>
     *
     * <p>A bracket that runs several steps opens one save around them all: a step's own save while
     * it is open reads and puts back nothing, and throws if it would change more than the bracket
     * saved. One object, reused: the render thread only.
     */
    static final class GlState {
        static final int COLOUR = 1, SCISSOR_BOX = 2, READ_FRAMEBUFFER = 4;
        /** Draw buffers whose blending and colour mask are kept: the GPU's, 16 at most. */
        private static int drawBuffers;

        private static final GlState OPEN = new GlState(), INSIDE = new GlState();
        private static final java.nio.ByteBuffer MASK = org.lwjgl.BufferUtils.createByteBuffer(4);

        private boolean open;
        private int units, extras;
        private int program, vertexArray, drawFramebuffer, readFramebuffer, activeTexture, depthFunc;
        private boolean depthTest, stencilTest, scissor, cull, depthMask;
        private final int[] textures = new int[3], samplers = new int[3], viewport = new int[4], scissorBox = new int[4];
        private final boolean[] blend = new boolean[16];
        private final boolean[][] colorMask = new boolean[16][4];

        private GlState() {
        }

        /** Saves the state a step changes: {@code units} texture units (0 to 3) and the {@code extras}. */
        static GlState save(int units, int extras) {
            if (OPEN.open) {
                if (units > OPEN.units || (extras & ~OPEN.extras) != 0) {
                    throw new IllegalStateException("a step changes GL state its bracket did not save");
                }
                return INSIDE;
            }
            OPEN.read(units, extras);
            OPEN.open = true;
            return OPEN;
        }

        private void read(int units, int extras) {
            this.units = units;
            this.extras = extras;
            if (units > 0) {
                activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
                for (int unit = 0; unit < units; unit++) {
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
                    textures[unit] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                    samplers[unit] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
                }
                GL13C.glActiveTexture(activeTexture);
            }
            if ((extras & COLOUR) != 0) {
                if (drawBuffers == 0) drawBuffers = Math.max(1, Math.min(16, GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS)));
                for (int buffer = 0; buffer < drawBuffers; buffer++) {
                    blend[buffer] = GL30C.glIsEnabledi(GL11C.GL_BLEND, buffer);
                    MASK.clear();
                    GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK, buffer, MASK);
                    for (int i = 0; i < 4; i++) colorMask[buffer][i] = MASK.get(i) != 0;
                }
            }
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            if ((extras & SCISSOR_BOX) != 0) GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);
            program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            vertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            drawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            if ((extras & READ_FRAMEBUFFER) != 0) readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            depthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
            stencilTest = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
            scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
            cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
            depthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
            depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        }

        void restore() {
            if (this != OPEN || !open) return;
            open = false;
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vertexArray);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            if ((extras & READ_FRAMEBUFFER) != 0) GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            if (units > 0) {
                for (int unit = 0; unit < units; unit++) {
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
                    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[unit]);
                    GL33C.glBindSampler(unit, samplers[unit]);
                }
                GL13C.glActiveTexture(activeTexture);
            }
            toggle(GL11C.GL_DEPTH_TEST, depthTest);
            toggle(GL11C.GL_STENCIL_TEST, stencilTest);
            toggle(GL11C.GL_SCISSOR_TEST, scissor);
            toggle(GL11C.GL_CULL_FACE, cull);
            if ((extras & COLOUR) != 0) {
                for (int buffer = 0; buffer < drawBuffers; buffer++) {
                    if (blend[buffer]) GL30C.glEnablei(GL11C.GL_BLEND, buffer);
                    else GL30C.glDisablei(GL11C.GL_BLEND, buffer);
                    boolean[] mask = colorMask[buffer];
                    GL30C.glColorMaski(buffer, mask[0], mask[1], mask[2], mask[3]);
                }
            }
            GL11C.glDepthFunc(depthFunc);
            GL11C.glDepthMask(depthMask);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if ((extras & SCISSOR_BOX) != 0) GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        }

        private static void toggle(int capability, boolean on) {
            if (on) GL11C.glEnable(capability);
            else GL11C.glDisable(capability);
        }
    }

    // --- Voxy ---------------------------------------------------------------------------

    private static boolean available() {
        if (broken) return false;
        ready = true;
        return VoxyLink.linked();
    }

    private static void fail(Throwable e) {
        broken = true;
        FRAME.merged = false;
        LivingHorizonClient.LOGGER.warn("Using the far terrain's depth failed: distant mobs show through it", e);
    }

    // --- For the game tests -------------------------------------------------------------------

    /**
     * Compiles every OpenGL program here, those only Voxy, Distant Horizons or Lodistance would ask
     * for too, and the fragment shaders of {@code FarDepthGpu} (26.3) as OpenGL reads them, and
     * throws at the first that fails: the game tests run without these mods, and on an older game.
     */
    public static void compileAll() {
        int[] programs = {
                GlPrograms.fullScreen("Depth view", VIEW, "depth"),
                GlPrograms.fullScreen("Distant Horizons depth", DH_MERGE, "dhDepth"),
                GlPrograms.fullScreen("Lodistance depth", LOD_MERGE, "gameDepth"),
                GlPrograms.fullScreen("Voxy depth", INTO_VOXY, "written", "drawn"),
                GlPrograms.fullScreen("Fade mask", UNMASK, "current", "written", "drawn"),
                GlPrograms.fullScreen("Depth restore", RESTORE, "saved", "written", "drawn"),
        };
        for (int program : programs) GL20C.glDeleteProgram(program);
        for (String name : new String[]{"far_depth_merge", "far_fade_mask", "far_depth_view", "far_visibility"}) {
            String file = "/assets/livinghorizon/shaders/core/" + name + ".fsh";
            try (InputStream in = FarDepth.class.getResourceAsStream(file)) {
                if (in == null) throw new IllegalStateException("missing " + file);
                String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                GL20C.glDeleteShader(GlPrograms.shader(name, GL20C.GL_FRAGMENT_SHADER, source));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
