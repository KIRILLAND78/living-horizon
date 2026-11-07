package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.FarDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which framebuffer Voxy draws its terrain into: the one bound while Sodium draws
 * the world's terrain, which is where the entities' depth goes too. Between render passes
 * Minecraft leaves nothing bound, so this is the moment to ask. Voxy is optional: without
 * it, this mixin applies to nothing. A Voxy that renames {@code renderOpaque} leaves it with
 * nothing to hook, silently: {@code /livinghorizon lod} says how often it ran.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
abstract class VoxyRenderSystemMixin {
    @Inject(method = "renderOpaque", at = @At("HEAD"), require = 0)
    private void livinghorizon$noteTarget(CallbackInfo ci) {
        FarDepth.noteTarget();
    }
}
