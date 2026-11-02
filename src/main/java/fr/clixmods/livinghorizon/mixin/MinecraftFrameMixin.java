package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.debug.Benchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame, at the top of the game's loop: the frame times {@link Benchmark} measures. */
@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void livinghorizon$frame(boolean advanceGameTime, CallbackInfo ci) {
        Benchmark.frame((Minecraft) (Object) this);
    }
}
