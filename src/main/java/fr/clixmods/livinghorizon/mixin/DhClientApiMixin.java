package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.FarDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets Distant Horizons' two fades of the game's picture into its terrain, which run
 * after the entities are drawn: {@link FarDepth} keeps the distant figures out of them.
 * Distant Horizons is optional: without it, this mixin applies to nothing. A Distant Horizons
 * that renames its fades leaves it with nothing to hook, silently: {@code /livinghorizon lod}
 * says how often it ran.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
abstract class DhClientApiMixin {
    @Inject(method = {"renderFadeOpaque", "renderFadeTransparent"}, at = @At("HEAD"), require = 0)
    private void livinghorizon$fadeIn(CallbackInfo ci) {
        FarDepth.beforeFade();
        // With Vulkan, through the game's device instead.
        //? if >=26.3
        /*fr.clixmods.livinghorizon.compat.FarDepthGpu.beforeFade();*/
    }

    @Inject(method = {"renderFadeOpaque", "renderFadeTransparent"}, at = @At("RETURN"), require = 0)
    private void livinghorizon$fadeOut(CallbackInfo ci) {
        FarDepth.afterFade();
        //? if >=26.3
        /*fr.clixmods.livinghorizon.compat.FarDepthGpu.afterFade();*/
    }
}
