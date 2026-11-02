package fr.clixmods.livinghorizon.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Where the mod's own geometry goes (birds, the saucer, impostors), with the world's
 * entities. From 1.21.9 the game collects what is drawn and draws it later, in order;
 * before, it is written straight into the buffers the entities are drawn from.
 */
public final class Sink {
    /**
     * Writes the vertices of one piece, at the pose it is given. From 1.21.9 it is the game's
     * own kind of geometry too, handed over as it is, with nothing wrapped round it each time.
     */
    @FunctionalInterface
    //? if >=1.21.9 {
    public interface Geometry extends SubmitNodeCollector.CustomGeometryRenderer {
        void draw(PoseStack.Pose at, VertexConsumer out);

        @Override
        default void render(PoseStack.Pose at, VertexConsumer out) {
            draw(at, out);
        }
    }
    //?} else {
    /*public interface Geometry {
        void draw(PoseStack.Pose at, VertexConsumer out);
    }
    *///?}

    //? if >=1.21.9 {
    private final SubmitNodeCollector collector;

    public Sink(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    public void draw(PoseStack pose, RenderType type, Geometry geometry) {
        collector.submitCustomGeometry(pose, type, geometry);
    }
    //?} else {
    /*private final MultiBufferSource buffers;

    public Sink(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public void draw(PoseStack pose, RenderType type, Geometry geometry) {
        geometry.draw(pose.last(), buffers.getBuffer(type));
    }
    *///?}
}
