package fr.clixmods.livinghorizon.render.impostor;

//? if <1.21.5 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
//? if >=1.21.2 {
import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.renderer.FogParameters;
//?}
//? if >=1.20.5
import org.joml.Matrix4fStack;
//? if <1.21.2 {
/^import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.FogRenderer;
^///?}
//? if <1.21.4
//import net.minecraft.server.packs.resources.ResourceManager;

/^*
 * Bakes before 1.21.5 ({@link BakeBackend}): plain OpenGL, at the start of the frame. The eight
 * views are drawn side by side, each in a square of its own, into a target of ours that the game
 * takes for its main one meanwhile ({@link ImpostorAtlas#drawing()}), and read back at once.
 ^/
final class GlBake implements BakeBackend {
    /^* A page as a plain OpenGL texture, its mip levels allocated and cleared up front. ^/
    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label, int width, int height) {
            bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, ImpostorAtlas.MIPS - 1);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            unpack();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < ImpostorAtlas.MIPS; level++) {
                int wide = width >> level, tall = height >> level;
                ByteBuffer empty = MemoryUtil.memCalloc(wide * tall * ImpostorAtlas.PIXEL_BYTES);
                try {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, wide, tall, 0, GL11.GL_RGBA,
                            GL11.GL_UNSIGNED_BYTE, empty);
                } finally {
                    MemoryUtil.memFree(empty);
                }
            }
        }

        //? if <1.21.4 {
        /^@Override
        public void load(ResourceManager manager) {
        }
        ^///?}
    }

    /^* The target views are drawn in: the eight views side by side. ^/
    private @Nullable RenderTarget target;

    @Override
    public AbstractTexture create(String label, int width, int height) {
        return new PageTexture(label, width, height);
    }

    private RenderTarget target() {
        if (target != null) return target;
        int render = ImpostorAtlas.RENDER;
        //? if >=1.21.2 {
        RenderTarget created = new TextureTarget(render * ImpostorViews.VIEWS, render, true);
        //?} else
        //RenderTarget created = new TextureTarget(render * ImpostorViews.VIEWS, render, true, Minecraft.ON_OSX);
        created.setClearColor(0f, 0f, 0f, 0f);
        target = created;
        return created;
    }

    /^*
     * Draws the eight views side by side into our target, which the game takes for its main
     * one meanwhile, and reads them back at once.
     ^/
    @Override
    public void drawViews(Minecraft minecraft, ImpostorAtlas.Request request, double size, ReadBack readBack) {
        int render = request.render();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target();
        int[][] pictures = new int[ImpostorViews.VIEWS][];
        Matrix4f ortho = new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f);
        //? if >=1.20.5 {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        //?} else {
        /^PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        ^///?}
        //? if <1.21.2
        //RenderSystem.applyModelViewMatrix();
        //? if >=1.21.2 {
        FogParameters fog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);
        //?} else {
        /^float fogStart = RenderSystem.getShaderFogStart();
        FogRenderer.setupNoFog();
        ^///?}
        RenderSystem.backupProjectionMatrix();
        ImpostorAtlas.drawing(target);
        try {
            RenderSystem.setShaderLights(ImpostorAtlas.LIGHT_0, ImpostorAtlas.LIGHT_1);
            RenderSystem.depthMask(true);
            //? if >=1.21.2 {
            target.clear();
            RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);
            //?} else {
            /^target.clear(Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
            ^///?}
            target.bindWrite(true);
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                RenderSystem.viewport(view * render, 0, render, render);
                ImpostorAtlas.draw(minecraft, request, view, ImpostorAtlas.pose(request, size, render), buffers);
                buffers.endBatch();
            }
            download(target, pictures, render);
        } finally {
            ImpostorAtlas.drawing(null);
            RenderSystem.restoreProjectionMatrix();
            //? if >=1.20.5 {
            modelView.popMatrix();
            //?} else
            //modelView.popPose();
            //? if <1.21.2
            //RenderSystem.applyModelViewMatrix();
            //? if >=1.21.2 {
            RenderSystem.setShaderFog(fog);
            //?} else
            //RenderSystem.setShaderFogStart(fogStart);
            minecraft.getMainRenderTarget().bindWrite(true);
            Lighting.setupFor3DItems();
        }
        readBack.done(pictures, null);
    }

    /^* Reads the views back from our target: bottom row first, as the GPU device does. ^/
    private static void download(RenderTarget target, int[][] pictures, int render) {
        int width = render * ImpostorViews.VIEWS;
        IntBuffer all = MemoryUtil.memAllocInt(width * render);
        try {
            GlStateManager._bindTexture(target.getColorTextureId());
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, all);
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                int[] pixels = new int[render * render];
                for (int y = 0; y < render; y++) all.get(y * width + view * render, pixels, y * render, render);
                pictures[view] = pixels;
            }
        } finally {
            MemoryUtil.memFree(all);
        }
    }

    @Override
    public void write(AbstractTexture page, int level, int x, int y, int width, int height, int[] pixels) {
        IntBuffer strip = MemoryUtil.memAllocInt(pixels.length);
        try {
            strip.put(pixels).flip();
            page.bind();
            unpack();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, strip);
        } finally {
            MemoryUtil.memFree(strip);
        }
    }

    /^* What the game's own uploads may have left in the unpack state, reset to plain rows. ^/
    private static void unpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}
*///?}
