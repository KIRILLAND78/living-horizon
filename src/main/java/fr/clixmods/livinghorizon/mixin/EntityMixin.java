package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a mount carrying another player as far as the player on it.
 *
 * <p>The game culls every entity past a distance that grows with its size, and makes
 * an exception for other players only ({@code RemotePlayer} draws to ten times the
 * usual distance). A boat is small, so at 100 blocks a friend in one sits on nothing.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$mountsOfPlayers(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (GhostRenderer.alwaysRender((Entity) (Object) this)) cir.setReturnValue(true);
    }

    //? if <1.21.2 {
    /*/^*
     * A figure the mod draws seated rides, for as long as it is drawn, for what asks the entity
     * then: an Entity Model Features pack poses the legs itself, and stretched them out under
     * an offline player seated on nothing the game knows of.
     ^/
    @Inject(method = "isPassenger", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$drawnSeated(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof net.minecraft.world.entity.LivingEntity living
                && fr.clixmods.livinghorizon.render.state.HumanoidRenderState.seated(living)) {
            cir.setReturnValue(true);
        }
    }
    *///?}
}
