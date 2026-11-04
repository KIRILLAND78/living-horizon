package fr.clixmods.livinghorizon.render.impostor;

//? if >=1.21.6 {
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=26.2
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;*/
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
*///?}
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.renderer.Projection;
import org.joml.Vector4f;
*///?} else {
import com.mojang.blaze3d.textures.TextureFormat;
//?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
*///?} else {
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//?} else
//import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;

/**
 * Bakes from 1.21.6 ({@link BakeBackend}): the game's GPU device, pointed at a target of ours by
 * its output overrides (from 26.3, a render pass on it), in the GUI pass, where the game itself
 * renders entities to textures. The views are copied into a buffer one by one and read back a
 * frame or two later.
 */
final class OverrideBake implements BakeBackend {
    /** The formats of the pages and of the target a view is drawn in. */
    //? if >=26.2 {
    /*private static final GpuFormat COLOR = GpuFormat.RGBA8_UNORM, DEPTH = GpuFormat.D32_FLOAT;
    *///?} else {
    private static final TextureFormat COLOR = TextureFormat.RGBA8, DEPTH = TextureFormat.DEPTH32;
    //?}

    /**
     * Whether the depth of a view is read straight from OpenGL as it is drawn, not copied out
     * through the game's encoder: from 26.3 its copy of a depth texture reads a colour buffer
     * that is not there, an OpenGL error that left the screen black under a shader pack.
     */
    //? if >=26.3 {
    /*private static final boolean DIRECT_DEPTH = true;
    *///?} else {
    private static final boolean DIRECT_DEPTH = false;
    //?}

    /** Whether the depth runs from 1 near to 0 far, as it does from 26.2. */
    //? if >=26.2 {
    /*private static final boolean REVERSED = true;
    *///?} else {
    private static final boolean REVERSED = false;
    //?}

    /** The target one view is drawn in before it is read back. */
    private static final class Target {
        GpuTexture color, depth;
        GpuTextureView colorView, depthView;
        //? if >=26.1 {
        /*ProjectionMatrixBuffer projection;
        *///?} else {
        CachedOrthoProjectionMatrixBuffer projection;
        //?}
        //? if >=26.2 {
        /*// The game's feature renderer no longer keeps a storage of its own to borrow.
        final SubmitNodeStorage storage = new SubmitNodeStorage();
        final Projection ortho = new Projection();
        *///?}
    }

    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label, int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    COLOR, width, height, 1, ImpostorAtlas.MIPS);
            textureView = device.createTextureView(texture);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            //? if >=1.21.11 {
            sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.NEAREST, true);
            //?} else {
            /*texture.setAddressMode(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, FilterMode.NEAREST, true);
            *///?}
            CommandEncoder encoder = device.createCommandEncoder();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < ImpostorAtlas.MIPS; level++) {
                int wide = width >> level, tall = height >> level;
                try (NativeImage empty = new NativeImage(wide, tall, true)) {
                    //? if >=26.2 {
                    /*encoder.writeToTexture(texture, empty, level, 0, 0, 0);
                    *///?} else {
                    encoder.writeToTexture(texture, empty, level, 0, 0, 0, wide, tall, 0, 0);
                    //?}
                }
            }
        }
    }

    /** The target views are drawn in. */
    private @Nullable Target target;
    private @Nullable Lighting lighting;

    @Override
    public AbstractTexture create(String label, int width, int height) {
        return new PageTexture(label, width, height);
    }

    private Target target() {
        if (target != null) return target;
        int render = ImpostorAtlas.RENDER;
        GpuDevice device = RenderSystem.getDevice();
        Target created = new Target();
        created.color = device.createTexture("living horizon impostor",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                COLOR, render, render, 1, 1);
        created.colorView = device.createTextureView(created.color);
        // Copied out too: the pictures' normal maps are made from it (ImpostorAtlas.relief).
        created.depth = device.createTexture("living horizon impostor depth",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
                DEPTH, render, render, 1, 1);
        created.depthView = device.createTextureView(created.depth);
        //? if >=26.1 {
        /*created.projection = new ProjectionMatrixBuffer("living horizon impostor");
        *///?} else {
        created.projection = new CachedOrthoProjectionMatrixBuffer("living horizon impostor", -1000f, 1000f, true);
        //?}
        target = created;
        return created;
    }

    private Lighting lighting() {
        boolean even = ImpostorAtlas.relief();
        if (lighting == null || evenLighting != even) {
            if (lighting == null) lighting = new Lighting();
            evenLighting = even;
            lighting.updateBuffer(Lighting.Entry.LEVEL, even ? ImpostorAtlas.EVEN_0 : ImpostorAtlas.LIGHT_0,
                    even ? ImpostorAtlas.EVEN_1 : ImpostorAtlas.LIGHT_1);
        }
        return lighting;
    }

    /** Whether {@link #lighting} holds the even light of pictures a shader pack lights. */
    private boolean evenLighting;

    @Override
    public boolean reliefs() {
        return true;
    }

    @Override
    public AbstractTexture borrowed(AbstractTexture texture) {
        return new Borrowed(texture);
    }

    /** Another texture's picture, which closing does not free. */
    private static final class Borrowed extends AbstractTexture {
        Borrowed(AbstractTexture of) {
            texture = of.getTexture();
            textureView = of.getTextureView();
            //? if >=1.21.11
            sampler = of.getSampler();
        }

        @Override
        public void close() {
            // Not ours: the page's normal map is closed with its page.
        }
    }

    @Override
    public void drawViews(Minecraft minecraft, ImpostorAtlas.Request request, double size, ReadBack readBack) {
        int render = request.render();
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * ImpostorAtlas.PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * ImpostorViews.VIEWS);
        // The depth, one float a pixel, for the normal maps.
        int depthBytes = render * render * Float.BYTES;
        GpuBuffer depths = !ImpostorAtlas.relief() || DIRECT_DEPTH ? null : device.createBuffer(() -> "living horizon impostor depth read back",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ depthBytes * ImpostorViews.VIEWS);
        // Read straight from OpenGL, view by view (DIRECT_DEPTH).
        float[][] direct = ImpostorAtlas.relief() && DIRECT_DEPTH ? new float[ImpostorViews.VIEWS][] : null;
        boolean sent = false;
        try {
            Target target = target();
            //? if >=1.21.9 {
            FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
            //?} else
            /*MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();*/
            CommandEncoder encoder = device.createCommandEncoder();
            // From 26.3 the drawing goes through a render pass on the target, opened below.
            //? if <26.3 {
            RenderSystem.outputColorTextureOverride = target.colorView;
            RenderSystem.outputDepthTextureOverride = target.depthView;
            //?}
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                //? if >=26.2 {
                /*// Depth runs from 1 near to 0 far from 26.2.
                encoder.clearColorAndDepthTextures(target.color, new Vector4f(0f), target.depth, 0.0);
                target.ortho.setupOrtho(-1000f, 1000f, render, render, true);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(target.ortho), ProjectionType.ORTHOGRAPHIC);
                *///?} elif >=26.1 {
                /*encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(
                        target.projection.getBuffer(new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f)),
                        ProjectionType.ORTHOGRAPHIC);
                *///?} else {
                encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(render, render), ProjectionType.ORTHOGRAPHIC);
                //?}
                PoseStack pose = ImpostorAtlas.pose(request, size, render);
                lighting().setupFor(Lighting.Entry.LEVEL);
                //? if >=26.3 {
                /*ImpostorAtlas.draw(minecraft, request, view, pose, target.storage);
                try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(target.storage);
                     RenderPass pass = encoder.createRenderPass(() -> "living horizon impostor", target.colorView,
                             Optional.empty(), target.depthView, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
                *///?} elif >=26.2 {
                /*ImpostorAtlas.draw(minecraft, request, view, pose, target.storage);
                features.renderAllFeatures(target.storage);
                *///?} elif >=1.21.9 {
                ImpostorAtlas.draw(minecraft, request, view, pose, features.getSubmitNodeStorage());
                features.renderAllFeatures();
                minecraft.renderBuffers().bufferSource().endBatch();
                //?} else {
                /*ImpostorAtlas.draw(minecraft, request, view, pose, buffers);
                buffers.endBatch();
                *///?}
                if (direct != null) direct[view] = readDepth(target.depth, render);
                boolean last = view == ImpostorViews.VIEWS - 1;
                // Before the colour: done by the time the last colour copy says it is.
                if (depths != null) {
                    encoder.copyTextureToBuffer(target.depth, depths, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ depthBytes * view,
                            () -> {
                            }, 0);
                }
                encoder.copyTextureToBuffer(target.color, buffer, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * view,
                        last ? () -> readBack(request, buffer, depths, direct, readBack) : () -> {
                        }, 0);
            }
            sent = true;
        } finally {
            //? if <26.3 {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            //?}
            // What the GUI pass draws next expects the game's own light.
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            if (!sent) {
                buffer.close();
                if (depths != null) depths.close();
            }
        }
    }

    /**
     * The depth of a view just drawn, bottom row first, straight from OpenGL; null under
     * another renderer. The pack state OpenGL reads it with is put back as it was found.
     */
    private static float @Nullable [] readDepth(GpuTexture depth, int render) {
        //? if >=26.3 {
        /*if (!(depth instanceof com.mojang.blaze3d.opengl.GlTexture gl)) return null;
        float[] out = new float[render * render];
        int pack = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT);
        int rowLength = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_PACK_ROW_LENGTH);
        int skipPixels = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_PIXELS);
        int skipRows = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_ROWS);
        try {
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER, 0);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT, 4);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ROW_LENGTH, 0);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_PIXELS, 0);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_ROWS, 0);
            org.lwjgl.opengl.GL45C.glGetTextureImage(gl.glId(), 0, org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT,
                    org.lwjgl.opengl.GL11C.GL_FLOAT, out);
        } finally {
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT, alignment);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ROW_LENGTH, rowLength);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_PIXELS, skipPixels);
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_SKIP_ROWS, skipRows);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER, pack);
        }
        return out;
        *///?} else {
        return null;
        //?}
    }

    /** The views are read back: their pixels go on to {@code readBack}. */
    private static void readBack(ImpostorAtlas.Request request, GpuBuffer buffer, @Nullable GpuBuffer depthBuffer,
                                 float @Nullable [][] direct, ReadBack readBack) {
        int[][] pictures = null;
        float[][] depths = null;
        Throwable failure = null;
        long started = Profile.begin(Profile.Section.BAKE_READ_BACK);
        try {
            if (readBack.wanted()) {
                //? if >=26.2 {
                /*try (GpuBufferSlice.MappedView mapped = buffer.map(true, false)) {
                *///?} else {
                try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
                //?}
                    pictures = ImpostorPixels.views(mapped.data(), ImpostorViews.VIEWS, request.render());
                }
                if (depthBuffer != null) {
                    //? if >=26.2 {
                    /*try (GpuBufferSlice.MappedView mapped = depthBuffer.map(true, false)) {
                    *///?} else {
                    try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(depthBuffer, true, false)) {
                    //?}
                        depths = ImpostorPixels.depths(mapped.data(), ImpostorViews.VIEWS, request.render(), REVERSED);
                    }
                } else if (direct != null) {
                    depths = new float[direct.length][];
                    for (int view = 0; view < direct.length; view++) {
                        // A view whose depth could not be read is lit as a flat picture.
                        depths[view] = direct[view] == null ? ImpostorPixels.noDepth(request.render())
                                : ImpostorPixels.heights(direct[view], REVERSED);
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            failure = error;
        } finally {
            buffer.close();
            if (depthBuffer != null) depthBuffer.close();
            Profile.end(Profile.Section.BAKE_READ_BACK, started);
        }
        readBack.done(pictures, depths, failure);
    }

    @Override
    public void write(AbstractTexture page, int level, int x, int y, int width, int height, int[] pixels) {
        try (NativeImage strip = new NativeImage(width, height, false)) {
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) strip.setPixelABGR(column, row, pixels[row * width + column]);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            //? if >=26.2 {
            /*encoder.writeToTexture(page.getTexture(), strip, level, 0, x, y);
            *///?} else {
            encoder.writeToTexture(page.getTexture(), strip, level, 0, x, y, width, height, 0, 0);
            //?}
        }
    }
}
//?}
