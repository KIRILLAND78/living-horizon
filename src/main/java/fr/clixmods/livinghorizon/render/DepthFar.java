package fr.clixmods.livinghorizon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;

/**
 * The far clipping plane the game uses this frame, mixin included, and how its depth buffer
 * is laid out: what the code that reads that buffer directly with OpenGL needs to know.
 */
public final class DepthFar {
    /**
     * Whether the game's own depth runs from 1 at the near plane to 0 at the far one, the sky
     * being 0: its layout from 26.2. Before, 0 near and 1 far. What Blaze3D draws in, whose
     * comparisons Iris turns around itself; OpenGL calls follow {@link #reversed()}.
     */
    //? if >=26.2 {
    /*public static final boolean REVERSED = true;

    private static volatile boolean reversed = true;
    *///?} else {
    public static final boolean REVERSED = false;
    //?}

    private DepthFar() {
    }

    public static float of(Minecraft minecraft) {
        //? if >=26.1 {
        /*return minecraft.gameRenderer.getMainCamera().depthFar;
        *///?} else {
        return minecraft.gameRenderer.getDepthFar();
        //?}
    }

    /**
     * Whether the world's depth is reversed this frame. Iris 1.11.2 and older undo 26.2's
     * reversed depth while a shader pack is on, newer ones keep it: the projection says.
     */
    public static boolean reversed() {
        //? if >=26.2 {
        /*return reversed;
        *///?} else {
        return false;
        //?}
    }

    /** The projection's vertical scale, {@code 1 / tan(fov / 2)}: the zoom of a spyglass included. 0 until a frame is seen. */
    private static volatile float focal;

    /**
     * How many pixels a radian is across the screen, as this frame is projected: the field of
     * view the game really draws with, which the option does not say while a spyglass is up.
     */
    public static double pixelsPerRadian(Minecraft minecraft) {
        double height = Math.max(1, minecraft.getWindow().getHeight());
        if (focal > 0f) return height * 0.5 * focal;
        return height / (2.0 * Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0));
    }

    /** The game's depth terms, read from this frame's projection: see {@link #terms(Minecraft)}. */
    private static volatile float @Nullable [] terms;

    /** This frame's projection: reversed when a nearer point gets a greater depth. */
    public static void noteProjection(Matrix4fc projection) {
        if (projection.m23() != 0f && projection.m11() > 0f) focal = projection.m11();
        if (projection.m23() != 0f) {
            float[] read = DepthMath.fromProjection(projection);
            if (read != null) terms = read;
        }
        //? if >=26.2 {
        /*if (projection.m23() != 0f) reversed = projection.m32() > 0f;
        *///?}
    }

    /** Whether the projection maps depth to 0..1 rather than OpenGL's -1..1. */
    public static boolean zeroToOne() {
        //? if >=26.2 {
        /*return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        *///?} elif >=26.1 {
        /*return RenderSystem.getDevice().isZZeroToOne();
        *///?} else {
        return false;
        //?}
    }

    /** {@link #openGl()} as worked out at the start of this frame: 1 yes, 0 no, -1 before the first frame. */
    private static volatile int openGl = -1;

    /**
     * Whether the game draws with OpenGL. From 26.2 it can draw with Vulkan instead, and then
     * nothing that talks to OpenGL directly may run. Worked out once a frame ({@link #noteFrame}):
     * with Vulkan, asking OpenGL throws, and an exception for every distant mob of every frame
     * cost more than the rest of their occlusion test.
     */
    public static boolean openGl() {
        int known = openGl;
        return known >= 0 ? known == 1 : onThisThread();
    }

    /** A new frame starts, on the render thread: whether it draws with OpenGL is worked out again. */
    public static void noteFrame() {
        openGl = onThisThread() ? 1 : 0;
    }

    private static boolean onThisThread() {
        try {
            GL.getCapabilities();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * The two terms of the game's projection that turn a distance in front of the camera into a
     * depth, and back: see {@link DepthMath}. Read from the projection the world is drawn with
     * this frame, so its planes are the ones the depth was made with, the pushed far plane
     * included; before the first frame, rebuilt from the game's near plane and {@link #of}.
     */
    public static float[] terms(Minecraft minecraft) {
        float[] read = terms;
        return read != null ? read.clone() : DepthMath.terms(0.05f, of(minecraft), reversed(), zeroToOne());
    }

    /** {@code {near, far}} of the game's projection this frame, from {@link #terms(Minecraft)}. */
    public static float[] planes(Minecraft minecraft) {
        float[] t = terms(minecraft);
        return DepthMath.planes(t[0], t[1], reversed(), zeroToOne());
    }
}
