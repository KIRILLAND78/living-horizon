package fr.clixmods.livinghorizon.render.impostor;

//? if >=1.21.5 && <1.21.6 {
/*import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import fr.clixmods.livinghorizon.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/^*
 * Bakes on 1.21.5 ({@link BakeBackend}): the game's GPU device, at the start of the frame. This
 * version has no output override to point the drawing elsewhere, so a target of ours stands in
 * for the main one while a figure is drawn ({@link ImpostorAtlas#drawing()}). The views are
 * copied into a buffer one by one and read back a frame or two later.
 ^/
final class DeviceBake implements BakeBackend {
    /^* The format of the pages. ^/
    private static final TextureFormat COLOR = TextureFormat.RGBA8;

    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label, int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, COLOR, width, height, ImpostorAtlas.MIPS);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, FilterMode.NEAREST, true);
            CommandEncoder encoder = device.createCommandEncoder();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < ImpostorAtlas.MIPS; level++) {
                int wide = width >> level, tall = height >> level;
                try (NativeImage empty = new NativeImage(wide, tall, true)) {
                    encoder.writeToTexture(texture, empty, level, 0, 0, wide, tall, 0, 0);
                }
            }
        }
    }

    /^* The target views are drawn in. ^/
    private @Nullable RenderTarget target;

    @Override
    public AbstractTexture create(String label, int width, int height) {
        return new PageTexture(label, width, height);
    }

    private RenderTarget target() {
        if (target == null) target = new TextureTarget("living horizon impostor", ImpostorAtlas.RENDER, ImpostorAtlas.RENDER, true);
        return target;
    }

    /^*
     * Draws the views one by one into our target, which the game takes for its main one
     * meanwhile: this version has no output override to point the drawing elsewhere.
     ^/
    @Override
    public void drawViews(Minecraft minecraft, ImpostorAtlas.Request request, double size, ReadBack readBack) {
        int render = request.render();
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * ImpostorAtlas.PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back", BufferType.PIXEL_PACK,
                BufferUsage.STATIC_READ, bytes * ImpostorViews.VIEWS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        FogParameters fog = RenderSystem.getShaderFog();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix().identity();
        ImpostorAtlas.drawing(target);
        boolean sent = false;
        try {
            CommandEncoder encoder = device.createCommandEncoder();
            RenderSystem.setShaderFog(FogParameters.NO_FOG);
            RenderSystem.setShaderLights(ImpostorAtlas.LIGHT_0, ImpostorAtlas.LIGHT_1);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f),
                    ProjectionType.ORTHOGRAPHIC);
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
                ImpostorAtlas.draw(minecraft, request, view, ImpostorAtlas.pose(request, size, render), buffers);
                buffers.endBatch();
                boolean last = view == ImpostorViews.VIEWS - 1;
                encoder.copyTextureToBuffer(target.getColorTexture(), buffer, bytes * view,
                        last ? () -> readBack(request, buffer, readBack) : () -> {
                        }, 0);
            }
            sent = true;
        } finally {
            ImpostorAtlas.drawing(null);
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFog(fog);
            Lighting.setupFor3DItems();
            if (!sent) buffer.close();
        }
    }

    /^* The views are read back: their pixels go on to {@code readBack}. ^/
    private static void readBack(ImpostorAtlas.Request request, GpuBuffer buffer, ReadBack readBack) {
        int[][] pictures = null;
        Throwable failure = null;
        long started = Profile.begin(Profile.Section.BAKE_READ_BACK);
        try {
            if (readBack.wanted()) {
                try (GpuBuffer.ReadView mapped = RenderSystem.getDevice().createCommandEncoder().readBuffer(buffer)) {
                    pictures = ImpostorPixels.views(mapped.data(), ImpostorViews.VIEWS, request.render());
                }
            }
        } catch (RuntimeException | LinkageError error) {
            failure = error;
        } finally {
            buffer.close();
            Profile.end(Profile.Section.BAKE_READ_BACK, started);
        }
        readBack.done(pictures, failure);
    }

    @Override
    public void write(AbstractTexture page, int level, int x, int y, int width, int height, int[] pixels) {
        try (NativeImage strip = new NativeImage(width, height, false)) {
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) strip.setPixelABGR(column, row, pixels[row * width + column]);
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.getTexture(), strip, level, x, y, width,
                    height, 0, 0);
        }
    }
}
*///?}
