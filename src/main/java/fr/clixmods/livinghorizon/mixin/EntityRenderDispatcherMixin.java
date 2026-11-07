package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a mob the server sends again out of the frame while its copy walks up to it:
 * the copy stands for it until both are in the same place, so that it never jumps. And
 * another player small or far enough to be a picture, with their mount: the mod draws them.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    //? if >=26.3 {
    /*private void livinghorizon$hiddenWhileJoined(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                 float partialTicks, CallbackInfoReturnable<Boolean> cir) {
    *///?} else {
    private void livinghorizon$hiddenWhileJoined(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                 CallbackInfoReturnable<Boolean> cir) {
    //?}
        if (FarPlayerTracker.get().mobs().hides(entity) || GhostRenderer.pictureInstead(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
