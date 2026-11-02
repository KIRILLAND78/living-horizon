package fr.clixmods.livinghorizon.mixin;

//? if >=1.21.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.render.DistancePreview;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the distant players in the frame. There is no public way to add a render state
 * to the world's entity list, and drawing them in a pass of our own would lose what
 * the entity pass gives: outlines, shader packs, the right order against translucency.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /** The matrices and the place of the camera, for the depth grid that hides distant mobs. */
    //? if >=26.2 {
    /*@Shadow
    @Final
    private LevelRenderState levelRenderState;

    // From 26.2 the frame is extracted before it is drawn: the camera of this frame is in the state.
    @Inject(method = "render", at = @At("HEAD"))
    private void livinghorizon$noteView(CallbackInfo ci) {
        CameraRenderState camera = levelRenderState.cameraRenderState;
        FarDepth.noteView(new Matrix4f(camera.viewRotationMatrix), camera.projectionMatrix);
    }
    *///?} elif >=26.1 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void livinghorizon$noteView(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                       boolean renderBlockOutline, CameraRenderState camera, Matrix4fc modelView,
                                       GpuBufferSlice fog, Vector4f fogColor, boolean renderSky,
                                       ChunkSectionsToRender chunks, CallbackInfo ci) {
        FarDepth.noteView(new Matrix4f(modelView), camera.projectionMatrix);
    }
    *///?} else {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void livinghorizon$noteView(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                       boolean renderBlockOutline, Camera camera, Matrix4f modelView,
                                       Matrix4f projection, Matrix4f cullingProjection, GpuBufferSlice fog,
                                       Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        FarDepth.noteView(modelView, projection);
    }
    //?}

    /** The world is drawn, a far terrain drawn after the entities (Ecstatic) included: the depth view's last picture. */
    //? if >=26.2 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void livinghorizon$worldDrawn(CallbackInfo ci) {
        FarDepth.worldDrawn();
    }
    *///?} else {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void livinghorizon$worldDrawn(CallbackInfo ci) {
        FarDepth.worldDrawn();
    }
    //?}

    //? if >=26.3 {
    /*// With Vulkan, what Distant Horizons' depth merge needs is written before the world's pass
    // opens: nothing may be written to a buffer while a pass is open.
    @Inject(method = "prepareTranslucents", at = @At("TAIL"))
    private void livinghorizon$prepareGpuDepth(CallbackInfo ci) {
        fr.clixmods.livinghorizon.compat.FarDepthGpu.prepare();
    }

    // The world's pass is closed: the distant mobs are tested against its depth, which the depth view keeps.
    @Inject(method = "executeOutline", at = @At("HEAD"))
    private void livinghorizon$afterGpuWorld(CallbackInfo ci) {
        fr.clixmods.livinghorizon.compat.FarDepthGpu.afterWorld();
    }
    *///?}

    // The extraction is LevelExtractorMixin's from 26.2, where it has a class of its own.
    //? if <26.2 {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void livinghorizon$beginFrame(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                         LevelRenderState frame, CallbackInfo ci) {
        GhostRenderer.beginFrame();
    }

    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void livinghorizon$extracted(Entity entity, float partialTick,
                                        CallbackInfoReturnable<EntityRenderState> cir) {
        GhostRenderer.extracted(entity);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void livinghorizon$outline(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        GhostRenderer.extracted(entity, cir.getReturnValue());
    }

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void livinghorizon$addDistantPlayers(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                                LevelRenderState frame, CallbackInfo ci) {
        GhostRenderer.extract(camera, deltaTracker.getGameTimeDeltaPartialTick(true), frame.entityRenderStates);
        // From 26.2 the outlines are drawn whenever there is a player to see them.
        //? if <26.2
        if (GhostRenderer.glowing()) frame.haveGlowingEntities = true;
    }
    //?}

    /** The world's entities are all submitted: the next drawing of features is theirs. */
    @Inject(method = "submitBlockEntities", at = @At("TAIL"))
    private void livinghorizon$armFarDepth(CallbackInfo ci) {
        FarDepth.arm();
        FarFog.arm();
    }

    /** The silhouette birds, the impostors and a distance being set, with the entities so that shader packs treat them alike. */
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void livinghorizon$submitFlat(PoseStack pose, LevelRenderState frame, SubmitNodeCollector collector,
                                         CallbackInfo ci) {
        Sink sink = new Sink(collector);
        AmbientRenderer.submit(pose, frame.cameraRenderState.pos, sink);
        ImpostorRenderer.submit(pose, sink);
        DistancePreview.submit(pose, frame.cameraRenderState.pos, sink);
    }

    /** Moves a distant player's state closer along the line of sight, and scales it to match. */
    @WrapOperation(method = "submitEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void livinghorizon$submit(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
                                     double x, double y, double z, PoseStack pose, SubmitNodeCollector collector,
                                     Operation<Void> original) {
        GhostRenderer.Transform t = GhostRenderer.transform(state);
        if (t == null) {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
            return;
        }
        // The anchor (the feet of the player) slides in by `pull`; everything around it,
        // the mount under them, keeps its place relative to it at the new scale.
        pose.pushPose();
        pose.translate(
                (float) (t.anchorX() * t.pull() + (x - t.anchorX()) * t.scale()),
                (float) (t.anchorY() * t.pull() + (y - t.anchorY()) * t.scale()),
                (float) (t.anchorZ() * t.pull() + (z - t.anchorZ()) * t.scale()));
        pose.scale((float) t.scale(), (float) t.scale(), (float) t.scale());
        original.call(dispatcher, state, camera, 0.0, 0.0, 0.0, pose, collector);
        pose.popPose();
    }
}
//?} elif >=1.21.2 {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.render.DistancePreview;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/^*
 * Puts the distant players in the frame, before 1.21.9: the game draws each entity it has
 * chosen straight into the world's buffers, so the figures are drawn right after them, into
 * the same buffers, and the whole batch is drawn before the far terrain's depth is taken back.
 ^/
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /^* The matrices of this frame, for the depth grid that hides distant mobs. ^/
    // The two matrices are the method's first two, whatever else it takes on that version.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void livinghorizon$noteView(CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) Matrix4f modelView,
                                       @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        FarDepth.noteView(modelView, projection);
    }

    /^* The world is drawn, a far terrain drawn after the entities (Ecstatic) included: the depth view's last picture. ^/
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void livinghorizon$worldDrawn(CallbackInfo ci) {
        FarDepth.worldDrawn();
    }

    @Inject(method = "collectVisibleEntities", at = @At("HEAD"))
    private void livinghorizon$beginFrame(Camera camera, Frustum frustum, List<Entity> entities,
                                         CallbackInfoReturnable<Boolean> cir) {
        GhostRenderer.beginFrame();
    }

    /^* The game has chosen what it draws: the figures are whatever it left out. ^/
    @Inject(method = "collectVisibleEntities", at = @At("TAIL"))
    private void livinghorizon$addDistantPlayers(Camera camera, Frustum frustum, List<Entity> entities,
                                                CallbackInfoReturnable<Boolean> cir) {
        for (Entity entity : entities) GhostRenderer.extracted(entity);
        GhostRenderer.extract(camera, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera,
                                          DeltaTracker deltaTracker, List<Entity> entities, CallbackInfo ci) {
        FarDepth.arm();
        FarDepth.before();
        FarFog.arm();
        FarFog.begin(null);
    }

    /^* The figures, the silhouette birds, the impostors and a distance being set, with the entities; then all of them drawn. ^/
    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void livinghorizon$drawDistant(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera,
                                          DeltaTracker deltaTracker, List<Entity> entities, CallbackInfo ci) {
        GhostRenderer.draw(pose, buffers, camera.position());
        Sink sink = new Sink(buffers);
        AmbientRenderer.submit(pose, camera.position(), sink);
        ImpostorRenderer.submit(pose, sink);
        DistancePreview.submit(pose, camera.position(), sink);
        buffers.endBatch();
        FarDepth.after();
        FarFog.end();
    }
}
*///?} else {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarFog;
import fr.clixmods.livinghorizon.render.DistancePreview;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Puts the distant players in the frame, before 1.21.2: the world's entities are drawn in
 * one loop of renderLevel, each straight into the world's buffers. The figures are drawn
 * right after them, into the same buffers, before the batch is drawn.
 ^/
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /^* The matrices of this frame, for the depth grid that hides distant mobs. ^/
    @Inject(method = "renderLevel", at = @At("HEAD"))
    //? if >=1.20.5 {
    private void livinghorizon$noteView(CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) Matrix4f modelView,
                                       @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        FarDepth.noteView(modelView, projection);
    }
    //?} else {
    /^// Before 1.20.5 the camera's turn is in the pose stack the level is drawn with.
    private void livinghorizon$noteView(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
                                       @Local(argsOnly = true) Matrix4f projection) {
        FarDepth.noteView(pose.last().pose(), projection);
    }
    ^///?}

    /^* The world is drawn, Ecstatic's terrain included, drawn after the entities: the depth view's last picture. ^/
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void livinghorizon$worldDrawn(CallbackInfo ci) {
        FarDepth.worldDrawn();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=entities"))
    private void livinghorizon$beginFrame(CallbackInfo ci) {
        GhostRenderer.beginFrame();
        FarDepth.arm();
        FarDepth.before();
        FarFog.arm();
        FarFog.begin(null);
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void livinghorizon$extracted(Entity entity, double x, double y, double z, float partialTick, PoseStack pose,
                                        MultiBufferSource buffers, CallbackInfo ci) {
        GhostRenderer.extracted(entity);
    }

    /^* The game has drawn what it chose: the figures, the silhouette birds, the impostors and a distance being set, then all of them. ^/
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void livinghorizon$drawDistant(CallbackInfo ci, @Local(argsOnly = true) Camera camera
                                          //? if <1.20.5
                                          /^, @Local(argsOnly = true) PoseStack level^/
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        GhostRenderer.extract(camera, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        //? if >=1.20.5 {
        PoseStack pose = new PoseStack();
        //?} else
        /^PoseStack pose = level;^/
        GhostRenderer.draw(pose, buffers, camera.position());
        Sink sink = new Sink(buffers);
        AmbientRenderer.submit(pose, camera.position(), sink);
        ImpostorRenderer.submit(pose, sink);
        DistancePreview.submit(pose, camera.position(), sink);
        buffers.endBatch();
        FarDepth.after();
        FarFog.end();
    }
}
*///?}
