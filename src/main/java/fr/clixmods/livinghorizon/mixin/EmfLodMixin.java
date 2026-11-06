package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.EntityModelFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Model Features' choice to skip an entity's animations this frame, by its distance:
 * never for a figure the mod draws ({@link EntityModelFeatures}). In {@code EMFLODHandler} from
 * 3.3, in {@code EMFAnimationEntityContext} before (with or without the model's name). EMF is
 * optional: without it, this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = {
        "traben.entity_model_features.utils.EMFLODHandler",
        "traben.entity_model_features.models.animation.EMFAnimationEntityContext"
}, remap = false)
abstract class EmfLodMixin {
    @Inject(method = "isLODSkippingThisFrame", at = @At("HEAD"), cancellable = true, require = 0)
    private static void livinghorizon$playFigures(CallbackInfoReturnable<Boolean> cir) {
        if (EntityModelFeatures.animatesFigure()) cir.setReturnValue(false);
    }
}
