package fr.clixmods.livinghorizon.mixin;

// The feature renderers came in 1.21.9; before, LevelRendererMixin brackets the entities itself.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.FarFog;
//? if <26.2 {
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//?}
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the drawing of the world's entities with Voxy's depth, so that its terrain
 * hides them pixel by pixel. Only the call that follows the world's entity submission is
 * concerned; {@link FarDepth} is armed by {@code LevelRendererMixin} for that one.
 */
//? if >=26.2 {
/*// From 26.2 the world's features are prepared once and drawn in steps through the frame
// graph: the entities are the solid step, then the translucent one, in the main pass. The
// depth is given back after the solid step: Iris copies it for the pack just before the
// translucent one, and that copy must be the game's own. It goes back in for the translucent
// step itself, after the copy.
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
abstract class FeatureRenderDispatcherMixin {
    //? if >=26.3 {
    /^@Inject(method = "executeSolid", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        FarDepth.before();
        // With Vulkan, Distant Horizons' depth is drawn in the world's pass itself, through the game's device.
        fr.clixmods.livinghorizon.compat.FarDepthGpu.merge(pass);
        // The pass keeps the fog it was given last, whatever draws in it next.
        com.mojang.renderpearl.api.buffers.GpuBufferSlice fog = FarFog.begin(this);
        if (fog != null) pass.setUniform("Fog", fog);
    }
    ^///?} else {
    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(CallbackInfo ci) {
        FarDepth.before();
        FarFog.begin(this);
    }
    //?}

    //? if >=26.3 {
    /^@Inject(method = "executeSolid", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        FarDepth.after(this);
        com.mojang.renderpearl.api.buffers.GpuBufferSlice fog = FarFog.end();
        if (fog != null) pass.setUniform("Fog", fog);
    }

    @Inject(method = "executeTranslucent", at = @At("HEAD"))
    private void livinghorizon$lateDepthIn(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        FarDepth.beforeTranslucent(this);
        com.mojang.renderpearl.api.buffers.GpuBufferSlice fog = FarFog.beginTranslucent(this);
        if (fog != null) pass.setUniform("Fog", fog);
    }

    @Inject(method = "executeTranslucent", at = @At("TAIL"))
    private void livinghorizon$lateDepthOut(com.mojang.renderpearl.api.commands.RenderPass pass, CallbackInfo ci) {
        FarDepth.afterTranslucent(this);
        com.mojang.renderpearl.api.buffers.GpuBufferSlice fog = FarFog.endTranslucent();
        if (fog != null) pass.setUniform("Fog", fog);
    }
    ^///?} else {
    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(CallbackInfo ci) {
        FarDepth.after(this);
        FarFog.end();
    }

    @Inject(method = "executeTranslucent", at = @At("HEAD"))
    private void livinghorizon$lateDepthIn(CallbackInfo ci) {
        FarDepth.beforeTranslucent(this);
        FarFog.beginTranslucent(this);
    }

    @Inject(method = "executeTranslucent", at = @At("TAIL"))
    private void livinghorizon$lateDepthOut(CallbackInfo ci) {
        FarDepth.afterTranslucent(this);
        FarFog.endTranslucent();
    }
    //?}
}
*///?} elif >=26.1 {
/*// On 26.1 the world's features are drawn in two steps, solid then translucent;
// renderAllFeatures is left to the hand, drawn after the depth is cleared. The depth is
// given back after the solid step: Iris copies it for the pack just before the translucent
// one, and that copy must be the game's own. It goes back in for the translucent step itself,
// after the copy. Iris draws the hand with a dispatcher of its own, in between: left alone.
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Inject(method = "renderSolidFeatures", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(CallbackInfo ci) {
        FarDepth.before();
        FarFog.begin(this);
    }

    @Inject(method = "renderSolidFeatures", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(CallbackInfo ci) {
        // The game draws what is left in the buffers right after this call: drawn first.
        if (FarDepth.givesBack() || FarFog.active()) bufferSource.endBatch();
        FarDepth.after(this);
        FarFog.end();
    }

    @Inject(method = "renderTranslucentFeatures", at = @At("HEAD"))
    private void livinghorizon$lateDepthIn(CallbackInfo ci) {
        FarDepth.beforeTranslucent(this);
        FarFog.beginTranslucent(this);
    }

    @Inject(method = "renderTranslucentFeatures", at = @At("TAIL"))
    private void livinghorizon$lateDepthOut(CallbackInfo ci) {
        // The same: a slime's outer layer is drawn by the endBatch after this call.
        if (FarDepth.givesBackTranslucent(this) || FarFog.active()) bufferSource.endBatch();
        FarDepth.afterTranslucent(this);
        FarFog.endTranslucent();
        // The models' outlines are written; the outline batch ends after this call.
        ImpostorRenderer.drawOutlines();
    }
}
*///?} else {
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Inject(method = "renderAllFeatures", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(CallbackInfo ci) {
        FarDepth.before();
        FarFog.begin(this);
    }

    @Inject(method = "renderAllFeatures", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(CallbackInfo ci) {
        // The game draws what is left in the buffers after this call, a few kinds at a time
        // (items and glints after the block outline): all of it drawn first, in the buffers' order.
        if (FarDepth.givesBack() || FarFog.active()) bufferSource.endBatch();
        FarDepth.after();
        FarFog.end();
        // The models' outlines are written; the outline batch ends after this call.
        ImpostorRenderer.drawOutlines();
    }
}
//?}
//?}
