//? if >=26.3 {
/*package fr.clixmods.livinghorizon.compat;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.DepthFar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/^*
 * What {@link FarDepth} does with OpenGL, done through the game's own GPU device (26.3): with
 * Vulkan, and with OpenGL over Distant Horizons (see {@link FarDepth#gpuPath()}). Distant
 * Horizons' depth merged before the entities, the distant figures kept out of its fade, and
 * which distant mobs the depth hides, in place of OpenGL's occlusion queries.
 *
 * <p>Nothing may be copied in the middle of the world's render pass there, so neither step
 * takes anything back the way {@link FarDepth} does. The merge is a draw in the world's pass:
 * Distant Horizons' depth stays in the game's for the rest of the frame, which its own fade
 * handles the same way as the sky. The fade, outside any pass, works on a copy of the depth:
 * whatever lies outside the game's own chunks is emptied, which can only be the distant
 * figures or that merged depth; inside them, whatever stands clearly in front of Distant
 * Horizons' terrain is too, which is an entity, its terrain matching the game's there. The copy
 * is put back once the fade is done.
 ^/
public final class FarDepthGpu {
    private static final String NAMESPACE = "livinghorizon";

    private static final RenderPipeline MERGE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_depth_merge"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_depth_merge"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("DhDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhFarDepth", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            // Drawn in the world's pass, with its colour target: written to depth only.
            .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.DEFAULT.format(),
                    ColorTargetState.WRITE_NONE))
            // Only where Distant Horizons' terrain is nearer than what the game drew.
            .withDepthStencilState(new DepthStencilState(
                    DepthFar.REVERSED ? CompareOp.GREATER_THAN : CompareOp.LESS_THAN, true))
            .build();

    private static final RenderPipeline FADE_MASK = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_fade_mask"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_fade_mask"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DhDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhFadeMask", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .build();

    /^* The depth view of the debug screen, in a corner of the picture. ^/
    private static final RenderPipeline VIEW = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_depth_view"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_depth_view"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhDepthView", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    /^*
     * Which distant mobs the depth hides, in place of OpenGL's occlusion queries, which the
     * game's device does not have: one pixel per mob, its box projected on the screen and a grid
     * of the depth inside it tested against the box's nearest point. Read back a frame or two later.
     ^/
    private static final RenderPipeline VISIBILITY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_visibility"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_visibility"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhVisibility", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
            .build();
    /^* Mobs tested per frame: the boxes fit the 16 KiB every device gives a uniform buffer. The others wait a frame. ^/
    private static final int VISIBILITY_MAX = 480;
    private static final int VISIBILITY_SIZE = new Std140SizeCalculator().putMat4f().putVec4().get() + VISIBILITY_MAX * 32;
    /^* Read-backs waiting for the GPU: past this, a frame asks nothing. ^/
    private static final int READBACKS = 4;

    private static final int VIEW_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();
    private static final int MERGE_SIZE = new Std140SizeCalculator().putVec4().putVec4().putMat4f().putVec4().get();
    private static final int FADE_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4()
            .putMat4f().putVec4().get();
    /^* Frames a uniform buffer is kept before it is written again: the GPU may still be reading it. ^/
    private static final int RING = 8;

    private static @Nullable MappableRingBuffer mergeUniforms, fadeUniforms, viewUniforms;
    /^* The depth of the last frame once the world is drawn, for the depth view. ^/
    private static @Nullable GpuTexture viewDepth;
    private static @Nullable GpuTextureView viewDepthView;
    /^* Written before the world's pass for the merge in it: Distant Horizons' depth and the terms. ^/
    private static @Nullable GpuBuffer preparedUniforms;
    private static @Nullable GpuTextureView preparedDepth;
    /^* Distant Horizons' projection terms of this frame, and the game's, for the fade mask. ^/
    private static final float[] PREPARED_DH = new float[4], PREPARED_GAME = new float[2];
    /^* The inverse of Distant Horizons' whole projection this frame, when known: see {@link DhFrame#inverse()}. ^/
    private static final Matrix4f PREPARED_INVERSE = new Matrix4f();
    private static boolean preparedExact;
    private static String preparedReason = "no frame yet";
    /^* The game's depth as it was before the fade, to give back after it. ^/
    private static @Nullable GpuTexture saved;
    private static @Nullable GpuTextureView savedView;
    private static boolean fading, broken;

    private static @Nullable MappableRingBuffer visibilityUniforms;
    private static @Nullable GpuTexture visibility;
    private static @Nullable GpuTextureView visibilityView;
    /^* One read-back per frame in flight: the buffer, the keys it answers for, and their frame. ^/
    private static final GpuBuffer[] READBACK_BUFFERS = new GpuBuffer[READBACKS];
    private static final Object[][] READBACK_KEYS = new Object[READBACKS][];
    private static final long[] READBACK_FRAMES = new long[READBACKS];
    private static final int[] READBACK_COUNTS = new int[READBACKS];
    private static final boolean[] READBACK_BUSY = new boolean[READBACKS];
    private static final Object[] KEYS = new Object[VISIBILITY_MAX];
    private static final float[] BOXES = new float[VISIBILITY_MAX * 6];

    private FarDepthGpu() {
    }

    /^*
     * Before the world's pass opens, where buffers may still be written: Distant Horizons'
     * depth of this frame - drawn by then - and the terms of both projections, for {@link #merge}.
     ^/
    public static void prepare() {
        preparedUniforms = null;
        preparedDepth = null;
        FarDepth.gpuAvailable();
        if (broken || !FarDepth.gpuPath() || !DhDepth.available() || DhDepth.shaderPackOn()) return;
        try {
            DhFrame dh = DhDepth.readView();
            if (dh == null || !(dh.view() instanceof GpuTextureView dhView)) {
                preparedReason = DhDepth.reason();
                return;
            }
            float[] game = DepthFar.terms(Minecraft.getInstance());
            org.joml.Matrix4fc inverse = dh.inverse();
            preparedExact = inverse != null;
            if (inverse != null) PREPARED_INVERSE.set(inverse);
            else PREPARED_INVERSE.identity();
            if (mergeUniforms == null) {
                mergeUniforms = new MappableRingBuffer(() -> "Living Horizon far depth", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, MERGE_SIZE);
            }
            GpuBuffer uniforms = next(mergeUniforms);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4(dh.a(), dh.b(), dh.signedFlag(), dh.nothing())
                        .putVec4(game[0], game[1], DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f)
                        .putMat4f(PREPARED_INVERSE)
                        .putVec4(preparedExact ? 1f : 0f, 0f, 0f, 0f);
            }
            preparedUniforms = uniforms;
            preparedDepth = dhView;
            PREPARED_DH[0] = dh.a();
            PREPARED_DH[1] = dh.b();
            PREPARED_DH[2] = dh.signedFlag();
            PREPARED_DH[3] = dh.nothing();
            PREPARED_GAME[0] = game[0];
            PREPARED_GAME[1] = game[1];
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* In the world's pass, before the entities are drawn: Distant Horizons' terrain into the depth. ^/
    public static void merge(RenderPass pass) {
        if (broken || !FarDepth.gpuMerge()) return;
        try {
            if (preparedUniforms == null || preparedDepth == null) {
                FarDepth.gpuResult(false, preparedReason, 0, 0);
                return;
            }
            CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(MERGE);
            if (pipeline == null) {
                FarDepth.gpuResult(false, "the depth merge pipeline did not compile", 0, 0);
                return;
            }
            pass.setPipeline(pipeline);
            pass.setUniform("DhDepth", preparedDepth, nearest());
            pass.setUniform("LhFarDepth", preparedUniforms);
            pass.draw(3, 1, 0, 0);
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            FarDepth.gpuResult(true, "", main.width, main.height);
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* Distant Horizons is about to fade the game's picture into its terrain. ^/
    public static void beforeFade() {
        if (broken || fading || !FarDepth.gpuFade()) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            GpuTexture depth = main.getDepthTexture();
            GpuTextureView depthView = main.getDepthTextureView();
            if (depth == null || depthView == null) return;
            CompiledRenderPipeline mask = RenderSystem.getCompiledPipelineNullable(FADE_MASK);
            CompiledRenderPipeline blit = RenderSystem.getCompiledPipelineNullable(RenderPipelines.BLIT_DEPTH);
            if (mask == null || blit == null) return;
            GpuTextureView copy = savedView(depth);

            if (fadeUniforms == null) {
                fadeUniforms = new MappableRingBuffer(() -> "Living Horizon fade mask", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, FADE_SIZE);
            }
            GpuBuffer uniforms = next(fadeUniforms);
            Vec3 camera = minecraft.gameRenderer.mainCamera().position();
            int chunks = minecraft.options.getEffectiveRenderDistance();
            int chunkX = Math.floorDiv((int) Math.floor(camera.x), 16), chunkZ = Math.floorDiv((int) Math.floor(camera.z), 16);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putMat4f(new Matrix4f(FarDepth.viewProjection()).invert())
                        .putVec4((float) ((chunkX - chunks) * 16 - camera.x), (float) ((chunkZ - chunks) * 16 - camera.z),
                                (float) ((chunkX + chunks + 1) * 16 - camera.x), (float) ((chunkZ + chunks + 1) * 16 - camera.z))
                        .putVec4(DepthFar.zeroToOne() ? 1f : 0f, DepthFar.REVERSED ? 0f : 1f, preparedDepth != null ? 1f : 0f, 0f)
                        .putVec4(PREPARED_DH[0], PREPARED_DH[1], PREPARED_DH[2], PREPARED_DH[3])
                        .putVec4(PREPARED_GAME[0], PREPARED_GAME[1], 0f, 0f)
                        .putMat4f(PREPARED_INVERSE)
                        .putVec4(preparedExact && preparedDepth != null ? 1f : 0f, 0f, 0f, 0f);
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            blitDepth(encoder, blit, depthView, copy, "Living Horizon: depth before the fade");
            try (RenderPass pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> "Living Horizon: fade mask")
                    .withDepthAttachment(depthView).build())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(mask);
                pass.setUniform("InSampler", copy, nearest());
                // Without Distant Horizons' depth this frame, the copy stands in and goes unread.
                pass.setUniform("DhDepth", preparedDepth != null ? preparedDepth : copy, nearest());
                pass.setUniform("LhFadeMask", uniforms);
                pass.draw(3, 1, 0, 0);
            }
            fading = true;
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* Distant Horizons' fade is done: the depth comes back as it was. ^/
    public static void afterFade() {
        if (!fading) return;
        fading = false;
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            CompiledRenderPipeline blit = RenderSystem.getCompiledPipelineNullable(RenderPipelines.BLIT_DEPTH);
            if (blit == null || savedView == null || main.getDepthTextureView() == null) return;
            blitDepth(RenderSystem.getDevice().createCommandEncoder(), blit, savedView, main.getDepthTextureView(),
                    "Living Horizon: depth after the fade");
        } catch (Throwable e) {
            fail(e);
        }
    }

    // --- Once the world is drawn -------------------------------------------------------------

    /^* The world's pass is closed: the mobs asked about are tested, and the depth view kept. ^/
    public static void afterWorld() {
        testVisibility();
        captureView();
    }

    private static void testVisibility() {
        if (broken || !FarDepth.gpuPath()) {
            OcclusionQueries.gpuReady(false);
            return;
        }
        OcclusionQueries.gpuReady(true);
        FarConfig config = FarConfig.get();
        if (!config.hideOccludedMobs || !config.optOcclusionQueries) return;
        try {
            int slot = -1;
            for (int i = 0; i < READBACKS; i++) {
                if (!READBACK_BUSY[i]) {
                    slot = i;
                    break;
                }
            }
            // The GPU is behind: these mobs are asked again next frame.
            if (slot < 0) return;
            int n = OcclusionQueries.takeAsked(KEYS, BOXES);
            if (n == 0) return;
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            GpuTextureView depth = main.getDepthTextureView();
            CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(VISIBILITY);
            if (depth == null || pipeline == null) return;
            if (visibility == null || visibility.isClosed()) {
                visibility = RenderSystem.getDevice().createTexture(() -> "Living Horizon visibility",
                        GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, VISIBILITY_MAX, 1, 1, 1);
                visibilityView = RenderSystem.getDevice().createTextureView(visibility);
            }
            if (READBACK_BUFFERS[slot] == null) {
                READBACK_BUFFERS[slot] = RenderSystem.getDevice().createBuffer(() -> "Living Horizon visibility read-back",
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) VISIBILITY_MAX * 4);
            }
            if (visibilityUniforms == null) {
                visibilityUniforms = new MappableRingBuffer(() -> "Living Horizon visibility", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, VISIBILITY_SIZE);
            }
            GpuBuffer uniforms = next(visibilityUniforms);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder builder = Std140Builder.intoBuffer(view.data())
                        .putMat4f(new Matrix4f(FarDepth.viewProjection()))
                        .putVec4(n, DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f, 0f);
                for (int i = 0; i < n; i++) {
                    builder.putVec4(BOXES[6 * i], BOXES[6 * i + 1], BOXES[6 * i + 2], 0f)
                            .putVec4(BOXES[6 * i + 3], BOXES[6 * i + 4], BOXES[6 * i + 5], 0f);
                }
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> "Living Horizon: visibility")
                    .withColorAttachment(visibilityView).build())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(pipeline);
                pass.setUniform("InSampler", depth, nearest());
                pass.setUniform("LhVisibility", uniforms);
                pass.draw(3, 1, 0, 0);
            }
            Object[] keys = new Object[n];
            System.arraycopy(KEYS, 0, keys, 0, n);
            int index = slot;
            READBACK_KEYS[index] = keys;
            READBACK_COUNTS[index] = n;
            READBACK_FRAMES[index] = OcclusionQueries.frame();
            READBACK_BUSY[index] = true;
            encoder.copyTextureToBuffer(visibility, READBACK_BUFFERS[index], 0, () -> readBack(index), 0);
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* The GPU has answered: one pixel per mob, its number in red and green, visible in blue. ^/
    private static void readBack(int slot) {
        try {
            GpuBuffer buffer = READBACK_BUFFERS[slot];
            Object[] keys = READBACK_KEYS[slot];
            if (buffer == null || keys == null) return;
            int n = READBACK_COUNTS[slot];
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                java.nio.ByteBuffer data = view.data();
                for (int i = 0; i < n; i++) {
                    int index = (data.get(4 * i) & 0xFF) | (data.get(4 * i + 1) & 0xFF) << 8;
                    if (index >= n) continue;
                    OcclusionQueries.answer(keys[index], (data.get(4 * i + 2) & 0xFF) >= 128, READBACK_FRAMES[slot]);
                }
            }
        } catch (Throwable e) {
            fail(e);
        } finally {
            READBACK_KEYS[slot] = null;
            READBACK_BUSY[slot] = false;
        }
    }

    // --- Depth view ----------------------------------------------------------------------

    /^*
     * Once the world is drawn, its depth kept for the depth view. With Vulkan the view has one
     * picture whatever its mode: the depth after the entities, Distant Horizons' merged in, as
     * nothing can be copied before that in the middle of the world's pass.
     ^/
    private static void captureView() {
        if (broken || !FarDepth.gpuPath() || FarConfig.get().debugDepthView == 0) return;
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            GpuTexture depth = main.getDepthTexture();
            GpuTextureView depthView = main.getDepthTextureView();
            CompiledRenderPipeline blit = RenderSystem.getCompiledPipelineNullable(RenderPipelines.BLIT_DEPTH);
            if (depth == null || depthView == null || blit == null) return;
            int w = depth.getWidth(0), h = depth.getHeight(0);
            if (viewDepth == null || viewDepth.isClosed() || viewDepth.getWidth(0) != w || viewDepth.getHeight(0) != h
                    || viewDepth.getFormat() != depth.getFormat()) {
                if (viewDepthView != null) viewDepthView.close();
                if (viewDepth != null) viewDepth.close();
                viewDepth = RenderSystem.getDevice().createTexture(() -> "Living Horizon depth view",
                        GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, depth.getFormat(), w, h, 1, 1);
                viewDepthView = RenderSystem.getDevice().createTextureView(viewDepth);
            }
            blitDepth(RenderSystem.getDevice().createCommandEncoder(), blit, depthView, viewDepthView,
                    "Living Horizon: depth for the depth view");
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* Draws the kept depth in the bottom right corner, near white to far black, the sky in dark blue. ^/
    public static void drawView(int mode) {
        if (broken || mode == 0 || !FarDepth.gpuPath() || viewDepthView == null) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            GpuTextureView color = main.getColorTextureView();
            CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(VIEW);
            if (color == null || pipeline == null) return;
            if (viewUniforms == null) {
                viewUniforms = new MappableRingBuffer(() -> "Living Horizon depth view", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, VIEW_SIZE);
            }
            GpuBuffer uniforms = next(viewUniforms);
            float[] terms = DepthFar.terms(minecraft), planes = DepthFar.planes(minecraft);
            float margin = 8f / Math.max(1, main.width);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4(1f - 0.4f - margin, 8f / Math.max(1, main.height), 0.4f, 0.4f)
                        .putVec4(terms[0], terms[1], planes[0], FarDepth.VIEW_FAR)
                        .putVec4(DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f, 0f, 0f);
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    RenderPassDescriptor.builder(() -> "Living Horizon: depth view").withColorAttachment(color).build())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(pipeline);
                pass.setUniform("InSampler", viewDepthView, nearest());
                pass.setUniform("LhDepthView", uniforms);
                pass.draw(3, 1, 0, 0);
            }
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* One depth texture drawn into another, as the game does it. ^/
    private static void blitDepth(CommandEncoder encoder, CompiledRenderPipeline blit, GpuTextureView from,
                                  GpuTextureView to, String label) {
        try (RenderPass pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> label)
                .withDepthAttachment(to).build())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(blit);
            pass.setUniform("InSampler", from, nearest());
            pass.draw(3, 1, 0, 0);
        }
    }

    /^* A depth texture like the game's, to keep its depth in while the fade runs. ^/
    private static GpuTextureView savedView(GpuTexture depth) {
        int w = depth.getWidth(0), h = depth.getHeight(0);
        if (saved == null || saved.isClosed() || saved.getWidth(0) != w || saved.getHeight(0) != h
                || saved.getFormat() != depth.getFormat()) {
            if (savedView != null) savedView.close();
            if (saved != null) saved.close();
            saved = RenderSystem.getDevice().createTexture(() -> "Living Horizon saved depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, depth.getFormat(), w, h, 1, 1);
            savedView = RenderSystem.getDevice().createTextureView(saved);
        }
        return savedView;
    }

    private static GpuBuffer next(MappableRingBuffer ring) {
        ring.rotate();
        return ring.currentBuffer();
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    /^*
     * Compiles the four pipelines here, through the game's own shader loader, and throws at the
     * first that does not: for the game tests, which run without Distant Horizons.
     ^/
    public static void compileAll() {
        for (RenderPipeline pipeline : new RenderPipeline[]{MERGE, FADE_MASK, VIEW, VISIBILITY}) {
            if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) {
                throw new IllegalStateException(pipeline.getLocation() + " did not compile");
            }
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        OcclusionQueries.gpuReady(false);
        fading = false;
        FarDepth.gpuResult(false, "off: " + e, 0, 0);
        LivingHorizonClient.LOGGER.warn("Using Distant Horizons' depth with the game's GPU device failed: distant mobs show through its terrain", e);
    }
}
*///?}
