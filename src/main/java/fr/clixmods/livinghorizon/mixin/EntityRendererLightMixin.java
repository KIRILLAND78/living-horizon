package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The light a figure the mod draws is extracted with: where the client has no light (past
 * the render distance), the game reads none, and a renderer that keeps its own copy of it
 * (Immersive Aircraft's, from 26.1) draws the figure black whatever the mod sets on the state
 * after. While {@link GhostRenderer#extract} runs, the light is the mod's: the world's where
 * it has some, open sky elsewhere, full in a picture being baked. A renderer that lights its
 * figure itself (a blaze) still does.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererLightMixin {
    @Inject(method = "getSkyLightLevel", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$farSky(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int sky = GhostRenderer.extractedSky();
        if (sky >= 0) cir.setReturnValue(sky);
    }

    @Inject(method = "getBlockLightLevel", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$farBlock(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int block = GhostRenderer.extractedBlock();
        if (block >= 0) cir.setReturnValue(entity.isOnFire() ? 15 : block);
    }
}
