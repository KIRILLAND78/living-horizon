//? if >=1.21.6 {
package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import fr.clixmods.livinghorizon.compat.DhFog;
import fr.clixmods.livinghorizon.compat.FarFog;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.ByteBuffer;

/**
 * Writes the fog the world's entities get with Voxy or Distant Horizons ({@link FarFog}) next to
 * the game's own, in the same buffer: room is made for it after the game's, and it is written
 * whenever the game's is, from the same values, that mod's changes in them.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Shadow
    @Final
    private MappableRingBuffer regularBuffer;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;<init>(Ljava/util/function/Supplier;II)V"), index = 2)
    private int livinghorizon$roomForFarFog(int size) {
        return FarFog.OFFSET + size;
    }

    // The game's fog for the world is written by setupFog up to 1.21.11, by updateBuffer from 26.1.
    //? if >=26.1 {
    /*@WrapOperation(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    *///?} else {
    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    //?}
    private void livinghorizon$writeFarFog(FogRenderer self, ByteBuffer buffer, int offset, Vector4f color,
                                          float environmentalStart, float environmentalEnd,
                                          float renderDistanceStart, float renderDistanceEnd,
                                          float skyEnd, float cloudEnd, Operation<Void> original) {
        original.call(self, buffer, offset, color, environmentalStart, environmentalEnd,
                renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd);
        GpuBuffer written = regularBuffer.currentBuffer();
        // Only where the room was made: a loader that builds the buffer its own way leaves the game's fog.
        DhFog.Ramps far = buffer.capacity() >= offset + FarFog.OFFSET + FogRenderer.FOG_UBO_SIZE
                ? FarFog.capped(environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd) : null;
        if (far != null) {
            Vector4f fogColor = new Vector4f(color.x(), color.y(), color.z(), color.w() * far.alpha());
            original.call(self, buffer, offset + FarFog.OFFSET, fogColor, far.environmentalStart(), far.environmentalEnd(),
                    far.renderDistanceStart(), far.renderDistanceEnd(), skyEnd, cloudEnd);
        }
        FarFog.written(written.slice(offset, FogRenderer.FOG_UBO_SIZE),
                far == null ? null : written.slice(offset + FarFog.OFFSET, FogRenderer.FOG_UBO_SIZE));
    }
}
//?}
