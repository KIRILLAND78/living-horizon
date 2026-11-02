package fr.clixmods.livinghorizon.compat;

// The game's fog before 1.21.6, one value for whatever is drawn until it is set again: start,
// end, shape (0 in every direction, 1 across the ground) and colour, red, green, blue, alpha.
// From 1.21.6 it is a buffer of its own, which FogRendererMixin writes.
//? if >=1.21.6 {
final class GameFog {
    private GameFog() {
    }

    static float[] read() {
        throw new UnsupportedOperationException("the fog is a buffer from 1.21.6");
    }

    static void set(float[] fog) {
        throw new UnsupportedOperationException("the fog is a buffer from 1.21.6");
    }
}
//?} elif >=1.21.2 {
/*import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogParameters;

final class GameFog {
    private GameFog() {
    }

    static float[] read() {
        FogParameters fog = RenderSystem.getShaderFog();
        return new float[]{fog.start(), fog.end(), fog.shape() == FogShape.SPHERE ? 0 : 1,
                fog.red(), fog.green(), fog.blue(), fog.alpha()};
    }

    static void set(float[] fog) {
        RenderSystem.setShaderFog(new FogParameters(fog[0], fog[1], fog[2] == 0 ? FogShape.SPHERE : FogShape.CYLINDER,
                fog[3], fog[4], fog[5], fog[6]));
    }
}
*///?} else {
/*import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

final class GameFog {
    private GameFog() {
    }

    static float[] read() {
        float[] colour = RenderSystem.getShaderFogColor();
        return new float[]{RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                RenderSystem.getShaderFogShape() == FogShape.SPHERE ? 0 : 1, colour[0], colour[1], colour[2], colour[3]};
    }

    static void set(float[] fog) {
        RenderSystem.setShaderFogStart(fog[0]);
        RenderSystem.setShaderFogEnd(fog[1]);
        RenderSystem.setShaderFogShape(fog[2] == 0 ? FogShape.SPHERE : FogShape.CYLINDER);
        RenderSystem.setShaderFogColor(fog[3], fog[4], fog[5], fog[6]);
    }
}
*///?}
