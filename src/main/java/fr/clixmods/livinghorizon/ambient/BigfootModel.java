package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.render.Sink;

/**
 * The figure of easter egg 6 ({@link Bigfoot}): a big, hairy, stooping walker of boxes, its
 * long arms swinging against its legs, its knees and elbows bending as it goes. Facing +z, its
 * origin at its feet, as the game's models have it.
 */
final class BigfootModel {
    /** Its fur: its body, its darker under-fur, its lighter tips; its bare skin, and darker. */
    private static final int FUR = 0x6B4A2E, UNDER = 0x4A321F, TIPS = 0x8C6A47, SKIN = 0x9C7A5C, CREASE = 0x6E5240;
    private static final int EYE = 0x120B06, GLINT = 0xD8C8A0;

    /**
     * Draws it at the pose's origin: knees and elbows that bend as it walks, shaggy fringes
     * under its forearms and behind its thighs, a mantle of fur on its shoulders, a chest
     * broader than its belly, a big head with ears, cheek fur and a glint in its eyes.
     * {@code swing}, in degrees: one leg and the other arm forwards as it walks; {@code headYaw}:
     * its head turned from its body, the game's way round.
     */
    static void draw(PoseStack pose, Sink collector, float swing, float headYaw, int light) {
        float thigh = 0.5f, shin = 0.5f, hip = 1.08f, lw = 0.2f;
        for (int side = -1; side <= 1; side += 2) {
            float legSwing = side < 0 ? swing : -swing;
            // The knee bends more as the leg comes forwards, less as it pushes back.
            float knee = 12f + Math.max(0f, -legSwing) * 0.9f;
            pose.pushPose();
            pose.translate(side * 0.29f, hip, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(legSwing - 8f));
            collector.draw(pose, Ufo.SOLID, (at, out) -> {
                box(at, out, -lw, -thigh, -lw * 1.05f, lw, 0.05f, lw * 1.05f, FUR, light);
                // The fur behind the thigh, hanging.
                box(at, out, -lw * 1.1f, -thigh * 0.95f, -lw * 1.35f, lw * 1.1f, -thigh * 0.2f, -lw * 0.9f, UNDER, light);
            });
            pose.translate(0f, -thigh + 0.04f, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(knee));
            collector.draw(pose, Ufo.SOLID, (at, out) -> {
                box(at, out, -lw * 0.95f, -shin, -lw, lw * 0.95f, 0.02f, lw, FUR, light);
                box(at, out, -lw * 1.05f, -shin * 0.55f, -lw * 1.12f, lw * 1.05f, -shin * 0.1f, lw * 0.4f, UNDER, light);
            });
            // The foot flat on the ground, long, its sole and toes bare.
            pose.translate(0f, -shin + 0.03f, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(-(knee + legSwing - 8f)));
            collector.draw(pose, Ufo.SOLID, (at, out) -> {
                box(at, out, -lw * 1.05f, -0.14f, -lw * 0.9f, lw * 1.05f, 0f, lw * 2.1f, UNDER, light);
                box(at, out, -lw * 0.95f, -0.13f, lw * 2.1f, lw * 0.95f, -0.04f, lw * 2.25f, CREASE, light);
            });
            pose.popPose();
        }

        // The body, stooping, broader at the chest than at the belly.
        float hunch = 16f;
        pose.pushPose();
        pose.translate(0f, hip, 0f);
        pose.mulPose(Axis.XP.rotationDegrees(hunch));
        collector.draw(pose, Ufo.SOLID, (at, out) -> {
            box(at, out, -0.45f, -0.05f, -0.3f, 0.45f, 0.5f, 0.3f, FUR, light);
            box(at, out, -0.55f, 0.45f, -0.34f, 0.55f, 1.1f, 0.34f, FUR, light);
            // The hump over its shoulders and back, and the mantle of fur round it.
            box(at, out, -0.58f, 0.85f, -0.4f, 0.58f, 1.16f, 0.18f, UNDER, light);
            box(at, out, -0.62f, 0.95f, -0.3f, 0.62f, 1.06f, 0.36f, TIPS, light);
            // The chest, barer and lighter; the belly, its fur parted.
            box(at, out, -0.36f, 0.55f, 0.34f, 0.36f, 0.95f, 0.37f, SKIN, light);
            box(at, out, -0.2f, 0.1f, 0.3f, 0.2f, 0.42f, 0.32f, CREASE, light);
            // Tufts down its back.
            box(at, out, -0.3f, 0.2f, -0.36f, 0.3f, 0.75f, -0.3f, UNDER, light);
        });

        // The head, big, low and forwards, held up against the stoop, turned on its own.
        float h = 0.32f;
        pose.pushPose();
        pose.translate(0f, 1.08f, 0.16f);
        pose.mulPose(Axis.XP.rotationDegrees(-hunch * 0.85f));
        pose.mulPose(Axis.YP.rotationDegrees(headYaw));
        collector.draw(pose, Ufo.SOLID, (at, out) -> {
            box(at, out, -h, 0f, -h, h, h * 2f, h * 0.95f, FUR, light);
            // The crest, longer at the back; the ears; the fur round the cheeks.
            box(at, out, -h * 0.35f, h * 2f, -h * 1.0f, h * 0.35f, h * 2.35f, h * 0.2f, UNDER, light);
            box(at, out, -h * 1.12f, h * 0.95f, -h * 0.15f, -h, h * 1.3f, h * 0.15f, CREASE, light);
            box(at, out, h, h * 0.95f, -h * 0.15f, h * 1.12f, h * 1.3f, h * 0.15f, CREASE, light);
            box(at, out, -h * 1.08f, h * 0.1f, h * 0.3f, -h * 0.8f, h * 1.0f, h * 1.02f, TIPS, light);
            box(at, out, h * 0.8f, h * 0.1f, h * 0.3f, h * 1.08f, h * 1.0f, h * 1.02f, TIPS, light);
            // The face: bare, under a heavy brow, the eyes deep with a glint, a broad flat nose, the mouth.
            box(at, out, -h * 0.78f, h * 0.15f, h * 0.95f, h * 0.78f, h * 1.4f, h * 0.97f, SKIN, light);
            box(at, out, -h * 0.85f, h * 1.25f, h * 0.95f, h * 0.85f, h * 1.55f, h * 1.12f, UNDER, light);
            box(at, out, -h * 0.58f, h * 0.98f, h * 0.97f, -h * 0.18f, h * 1.22f, h * 0.98f, EYE, light);
            box(at, out, h * 0.18f, h * 0.98f, h * 0.97f, h * 0.58f, h * 1.22f, h * 0.98f, EYE, light);
            box(at, out, -h * 0.4f, h * 1.12f, h * 0.98f, -h * 0.3f, h * 1.2f, h * 0.99f, GLINT, light);
            box(at, out, h * 0.3f, h * 1.12f, h * 0.98f, h * 0.4f, h * 1.2f, h * 0.99f, GLINT, light);
            box(at, out, -h * 0.5f, h * 0.2f, h * 0.97f, h * 0.5f, h * 0.95f, h * 1.15f, SKIN, light);
            box(at, out, -h * 0.28f, h * 0.62f, h * 1.15f, h * 0.28f, h * 0.82f, h * 1.2f, CREASE, light);
            box(at, out, -h * 0.32f, h * 0.32f, h * 1.15f, h * 0.32f, h * 0.38f, h * 1.16f, EYE, light);
        });
        pose.popPose();

        // The arms, from the shoulders: long, a little bent at the elbow, the hands big and bare.
        float aw = 0.17f, upper = 0.68f, fore = 0.66f;
        for (int side = -1; side <= 1; side += 2) {
            int s = side;
            float armSwing = side < 0 ? -swing : swing;
            pose.pushPose();
            pose.translate(side * 0.66f, 0.98f, 0.02f);
            pose.mulPose(Axis.XP.rotationDegrees(-hunch + armSwing - 4f));
            pose.mulPose(Axis.ZP.rotationDegrees(side * 7f));
            collector.draw(pose, Ufo.SOLID, (at, out) -> {
                box(at, out, -aw, -upper, -aw, aw, 0.08f, aw, FUR, light);
                // The mantle over the shoulder.
                box(at, out, -aw * 1.25f, -upper * 0.3f, -aw * 1.25f, aw * 1.25f, 0.12f, aw * 1.25f, TIPS, light);
            });
            pose.translate(0f, -upper + 0.04f, 0f);
            // The elbow bends forwards, more as the arm swings ahead.
            pose.mulPose(Axis.XP.rotationDegrees(-14f - Math.max(0f, -armSwing) * 0.5f));
            collector.draw(pose, Ufo.SOLID, (at, out) -> {
                box(at, out, -aw * 1.08f, -fore, -aw * 1.08f, aw * 1.08f, 0.02f, aw * 1.08f, FUR, light);
                // The fringe hanging under the forearm, out on its outer side.
                box(at, out, s * aw * 0.2f - aw * 0.9f, -fore * 0.85f, -aw * 1.4f, s * aw * 0.2f + aw * 0.9f, -fore * 0.15f, -aw * 0.8f, UNDER, light);
                // The hand: palm, fingers curled, a thumb.
                box(at, out, -aw * 1.05f, -fore - 0.24f, -aw * 0.9f, aw * 1.05f, -fore + 0.02f, aw * 1.2f, SKIN, light);
                box(at, out, -aw * 0.9f, -fore - 0.3f, aw * 0.2f, aw * 0.9f, -fore - 0.22f, aw * 1.15f, CREASE, light);
                box(at, out, -s * aw * 1.25f - aw * 0.25f, -fore - 0.12f, aw * 0.5f, -s * aw * 1.25f + aw * 0.25f, -fore + 0.02f, aw * 1.1f, SKIN, light);
            });
            pose.popPose();
        }
        pose.popPose();
    }

    private BigfootModel() {
    }

    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int light) {
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, light);
    }
}
