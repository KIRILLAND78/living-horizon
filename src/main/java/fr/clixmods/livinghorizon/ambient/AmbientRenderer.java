package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.render.DepthFar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorThreshold;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Draws the pixel birds, from {@code textures/misc/birds.png}: a 64 by 32 sheet.
 *
 * <ul>
 *   <li>Flying, top row: a 16 by 8 bird seen from above per species, cut in two planes
 *       hinged along the body, one per wing, which tilt to beat.</li>
 *   <li>Perched, bottom row: an 8 by 8 bird seen from the side, on one upright plane that
 *       always faces the camera, mirrored to look the way the bird faces.</li>
 * </ul>
 *
 * <p>The plain sprite is white and tinted (dark for most birds, white for gulls); the
 * others are in their own colours.
 *
 * <p>With {@code birdStyle} set to {@code 3d}, the same birds are drawn as small box models
 * instead ({@link BirdModels}), coloured box by box over a white feather grain,
 * {@code textures/misc/birds_3d.png}: the wings beat and bend, perched birds stand on their
 * legs. Past the impostor distance a 3D bird is a picture of itself instead, baked from the
 * same model ({@link BirdFigure}). Either way they are submitted with the entities, so
 * shader packs and Voxy's depth treat them like any entity.
 */
public final class AmbientRenderer {
    private static final RenderType TYPE = RenderTypes.entityCutout(
            Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/misc/birds.png"));
    private static final RenderType MODEL = RenderTypes.entitySolid(
            Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/misc/birds_3d.png"));
    /** Side of the feather grain texture, in pixels. */
    private static final int GRAIN = 64;
    private static final int LIGHT = LightTexture.pack(0, 15);

    private AmbientRenderer() {
    }

    public static void submit(PoseStack pose, Vec3 camera, Sink collector) {
        long started = Profile.begin(Profile.Section.SUBMIT_BIRDS);
        try {
            submitBirds(pose, camera, collector);
        } finally {
            Profile.end(Profile.Section.SUBMIT_BIRDS, started);
        }
    }

    private static void submitBirds(PoseStack pose, Vec3 camera, Sink collector) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double reach = DepthFar.of(minecraft) * 0.95;
        double pixelsPerRadian = DepthFar.pixelsPerRadian(minecraft);

        if (config.easterEggs) {
            Ambience ambience = FarPlayerTracker.get().ambience();
            ambience.ufo().submit(pose, camera, collector, partial);
            ambience.pass().submit(pose, camera, collector, partial);
            ambience.herobrine().submit(pose, camera, collector, partial);
            ambience.waver().submit(pose, camera, collector, partial);
            ambience.bigfoot().submit(pose, camera, collector, partial);
            ambience.ghosts().submit(pose, camera, collector, partial);
            ambience.ghostBoat().submit(pose, camera, collector, partial);
            ambience.ghostVillager().submit(pose, camera, collector, partial);
            for (Egg egg : ambience.more()) egg.submit(pose, camera, collector, partial);
        }
        boolean models = config.birdModels();
        Arrays.fill(ASKED, false);
        for (Ambience.Flyer bird : FarPlayerTracker.get().ambience().flyers()) {
            if (!config.skyBirds || bird.kind != Ambience.Kind.SILHOUETTE) continue;
            double x = Mth.lerp(partial, bird.xo, bird.x) - camera.x;
            double y = Mth.lerp(partial, bird.yo, bird.y) - camera.y;
            double z = Mth.lerp(partial, bird.zo, bird.z) - camera.z;
            // Behind the camera, or less than a pixel: the mobs' own tests.
            if (GhostRenderer.outOfSight(x, y, z, bird.span * bird.scale)) continue;
            double distance = Math.sqrt(x * x + y * y + z * z);
            GhostRenderer.noteDistance(distance);
            // Past the far plane: closer and smaller in proportion, the same on screen.
            double pull = distance > reach ? reach / distance : 1.0;
            float yaw = Mth.rotLerp(partial, bird.yawO, bird.yaw);
            float wingDegrees = Mth.lerp(partial, bird.wingO, bird.wing);
            int tint = bird.shade;
            int species = bird.species.ordinal();
            if (models && impostor(bird, x, y, z, distance, pull, yaw, wingDegrees, config, pixelsPerRadian)) continue;
            if (models) bird.view = -1;

            pose.pushPose();
            pose.translate((float) (x * pull), (float) (y * pull), (float) (z * pull));
            if (models) {
                float bank = bird.perched ? 0f : Mth.lerp(partial, bird.bankO, bird.bank);
                model(pose, collector, BirdModels.of(bird.species), (float) (bird.span * bird.scale * pull), bird.perched,
                        yaw, bank, wingDegrees * Mth.DEG_TO_RAD, tone(bird), LIGHT);
            } else if (bird.perched) {
                // Upright, turned to the camera; which way it looks decides the mirror.
                float toCamera = (float) Math.toDegrees(Math.atan2(-x, -z));
                pose.mulPose(Axis.YP.rotationDegrees(toCamera));
                double facing = Math.toRadians(yaw), across = Math.toRadians(toCamera);
                boolean mirrored = -Math.sin(facing) * Math.cos(across) + Math.cos(facing) * -Math.sin(across) < 0;
                float height = (float) (bird.span * bird.scale * 0.55 * pull);
                pose.scale(height, height, height);
                collector.draw(pose, TYPE, (at, out) -> perched(at, out, species, mirrored, tint));
            } else {
                float bank = Mth.lerp(partial, bird.bankO, bird.bank);
                float wing = Mth.lerp(partial, bird.wingO, bird.wing);
                float scale = (float) (bird.span * bird.scale * pull);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(bank));
                pose.scale(scale, scale, scale);
                collector.draw(pose, TYPE, (at, out) -> flying(at, out, species, wing, tint));
            }
            pose.popPose();
        }
    }

    /** The species and poses whose pictures were asked for this frame: two per species, flying then perched. */
    private static final boolean[] ASKED = new boolean[Ambience.Species.values().length * 2];

    /**
     * Draws a 3D bird as a picture of itself ({@link BirdFigure}) when it is past the impostor
     * distance or smaller on screen than the impostor size ({@link ImpostorThreshold}), and its
     * pictures are baked; they are asked for otherwise, every height and beat of its pose at
     * once, and the model is drawn meanwhile. Like a mob, a bird keeps its picture until it is
     * a little inside both, and until it has clearly turned to the next view.
     */
    private static boolean impostor(Ambience.Flyer bird, double x, double y, double z, double distance, double pull,
                                    float yaw, float wingDegrees, FarConfig config, double pixelsPerRadian) {
        // Birds are creatures: pictured under Simplified Mobs.
        if (!ImpostorAtlas.usable() || !config.impostors) return false;
        double apparent = distance < 1e-3 ? Double.MAX_VALUE : bird.span * bird.scale / distance * pixelsPerRadian;
        if (!ImpostorThreshold.pictured(distance, apparent, bird.view >= 0, config.impostorDistance, config.impostorPixels)) {
            return false;
        }
        // Degrees the camera looks down on the bird from.
        double elevation = Math.toDegrees(Math.atan2(-y, Math.sqrt(x * x + z * z)));
        ImpostorAtlas.Sheet sheet = ImpostorAtlas.sheet(BirdFigure.keyOf(bird.species, bird.perched, wingDegrees, elevation));
        if (sheet == null) {
            int pose = bird.species.ordinal() * 2 + (bird.perched ? 1 : 0);
            if (!ASKED[pose]) {
                ASKED[pose] = true;
                for (BirdFigure each : BirdFigure.all(bird.species, bird.perched)) ImpostorAtlas.request(each.key(), each);
            }
            return false;
        }
        bird.view = ImpostorViews.view(yaw, -x, -z, bird.view);
        ImpostorRenderer.add(x * pull, y * pull, z * pull,
                sheet.worldSize() * bird.span * bird.scale * pull, sheet, bird.view, LIGHT, 0, true);
        return true;
    }

    /**
     * A bird model at the pose's origin, turned to {@code yaw} and banked: flying, its middle
     * there; perched, its feet. {@code span} is its wingspan in blocks.
     */
    static void model(PoseStack pose, Sink sink, BirdModels.Model model, float span, boolean perched, float yaw,
                      float bank, float wing, float tone, int light) {
        float size = span / model.span();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (perched) {
            pose.scale(size, size, size);
            pose.translate(0f, model.lift(), 0f);
            sink.draw(pose, MODEL, (at, out) -> boxes(at, out, model, BirdModels.Part.PERCH, false, tone, light));
        } else {
            pose.mulPose(Axis.ZP.rotationDegrees(bank));
            pose.scale(size, size, size);
            sink.draw(pose, MODEL, (at, out) -> boxes(at, out, model, BirdModels.Part.FLY, false, tone, light));
            // Each wing turns on its hinge by the beat, and its tip half as much again:
            // the wing bends as it beats. The -X wing is the mirror of the +X one.
            for (int side = 1; side >= -1; side -= 2) {
                boolean mirrored = side < 0;
                pose.pushPose();
                hinge(pose, side * model.hingeX(), side * wing);
                sink.draw(pose, MODEL, (at, out) -> boxes(at, out, model, BirdModels.Part.WING, mirrored, tone, light));
                hinge(pose, side * model.tipX(), side * wing * 0.5f);
                sink.draw(pose, MODEL, (at, out) -> boxes(at, out, model, BirdModels.Part.TIP, mirrored, tone, light));
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** A little lighter or darker from one bird to the next, the same bird always the same. */
    private static float tone(Ambience.Flyer bird) {
        return 0.9f + 0.2f * ((System.identityHashCode(bird) & 255) / 255f);
    }

    /**
     * The boxes of one part. The body is drawn with the boxes of each pose: {@code FLY} or
     * {@code PERCH} brings the body along with it.
     */
    private static void boxes(PoseStack.Pose at, VertexConsumer out, BirdModels.Model model, BirdModels.Part part,
                              boolean mirrored, float tone, int light) {
        boolean withBody = part == BirdModels.Part.FLY || part == BirdModels.Part.PERCH;
        for (BirdModels.Cube cube : model.cubes()) {
            if (cube.part() == part || withBody && cube.part() == BirdModels.Part.BODY) {
                cube(at, out, cube, mirrored, tone, light);
            }
        }
    }

    /** Turns what follows by {@code radians} about the line along Z through {@code x}. */
    private static void hinge(PoseStack pose, float x, float radians) {
        pose.translate(x, 0f, 0f);
        pose.mulPose(Axis.ZP.rotation(radians));
        pose.translate(-x, 0f, 0f);
    }

    /**
     * One box, its six faces turned outwards (the render type culls back faces). Each face
     * takes a patch of the feather grain as large as itself, one pixel per model pixel, at a
     * place of its own, so that no two faces look alike.
     */
    private static void cube(PoseStack.Pose at, VertexConsumer out, BirdModels.Cube cube, boolean mirrored, float tone,
                             int light) {
        float x0 = mirrored ? -cube.x1() : cube.x0(), x1 = mirrored ? -cube.x0() : cube.x1();
        float y0 = cube.y0(), y1 = cube.y1(), z0 = cube.z0(), z1 = cube.z1();
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int r = Math.min(255, (int) (((cube.rgb() >> 16) & 255) * tone));
        int g = Math.min(255, (int) (((cube.rgb() >> 8) & 255) * tone));
        int b = Math.min(255, (int) ((cube.rgb() & 255) * tone));
        int seed = cube.hashCode();
        face(at, out, r, g, b, light, seed, dz, dy, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        face(at, out, r, g, b, light, seed + 1, dy, dz, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(at, out, r, g, b, light, seed + 2, dx, dz, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(at, out, r, g, b, light, seed + 3, dz, dx, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(at, out, r, g, b, light, seed + 4, dy, dx, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(at, out, r, g, b, light, seed + 5, dx, dy, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
    }

    /**
     * Four corners counter-clockwise seen from outside, on a {@code width} by {@code height}
     * patch of grain: from the first corner to the second runs down the patch, from the
     * second to the third across.
     */
    private static void face(PoseStack.Pose at, VertexConsumer out, int r, int g, int b, int light, int seed,
                             float width, float height, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        int hash = Mth.murmurHash3Mixer(seed);
        float u0 = Math.floorMod(hash, Math.max(1, GRAIN - Mth.ceil(width))) / (float) GRAIN;
        float v0 = Math.floorMod(hash >> 8, Math.max(1, GRAIN - Mth.ceil(height))) / (float) GRAIN;
        float u1 = u0 + Math.min(width, GRAIN) / GRAIN, v1 = v0 + Math.min(height, GRAIN) / GRAIN;
        coloured(at, out, r, g, b, light, x1, y1, z1, u0, v0, nx, ny, nz);
        coloured(at, out, r, g, b, light, x2, y2, z2, u0, v1, nx, ny, nz);
        coloured(at, out, r, g, b, light, x3, y3, z3, u1, v1, nx, ny, nz);
        coloured(at, out, r, g, b, light, x4, y4, z4, u1, v0, nx, ny, nz);
    }

    private static void coloured(PoseStack.Pose at, VertexConsumer out, int r, int g, int b, int light, float x, float y,
                                 float z, float u, float v, float nx, float ny, float nz) {
        out.addVertex(at, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, nx, ny, nz);
    }

    /** Which flying sprite: the gull flies on the plain one, tinted white. */
    private static int flyingColumn(int species) {
        return switch (Ambience.Species.values()[species]) {
            case PIGEON -> 1;
            case ROBIN -> 2;
            case TIT -> 3;
            default -> 0;
        };
    }

    /** Which perched sprite. */
    private static int perchedColumn(int species) {
        return switch (Ambience.Species.values()[species]) {
            case PIGEON -> 1;
            case ROBIN -> 2;
            case TIT -> 3;
            case GULL -> 4;
            case DUCK -> 5;
            default -> 0;
        };
    }

    private static void flying(PoseStack.Pose at, VertexConsumer out, int species, float wingDegrees, int tint) {
        float u0 = flyingColumn(species) * 16 / 64f, um = u0 + 8 / 64f, u1 = u0 + 16 / 64f;
        // Flying sprites: the top row, the duck alone on the third.
        float v0 = Ambience.Species.values()[species] == Ambience.Species.DUCK ? 16 / 32f : 0f, v1 = v0 + 8 / 32f;
        float tipX = 0.5f * Mth.cos(wingDegrees * Mth.DEG_TO_RAD);
        float tipY = 0.5f * Mth.sin(wingDegrees * Mth.DEG_TO_RAD);
        // Left half of the sprite on the +X wing, its tip at the sprite's left edge.
        quad(at, out, tint,
                0f, 0f, 0.25f, um, v0,
                tipX, tipY, 0.25f, u0, v0,
                tipX, tipY, -0.25f, u0, v1,
                0f, 0f, -0.25f, um, v1);
        // Right half on the -X wing.
        quad(at, out, tint,
                0f, 0f, 0.25f, um, v0,
                -tipX, tipY, 0.25f, u1, v0,
                -tipX, tipY, -0.25f, u1, v1,
                0f, 0f, -0.25f, um, v1);
    }

    private static void perched(PoseStack.Pose at, VertexConsumer out, int species, boolean mirrored, int tint) {
        float u0 = perchedColumn(species) * 8 / 64f, u1 = u0 + 8 / 64f;
        float left = mirrored ? u1 : u0, right = mirrored ? u0 : u1;
        // One unit tall, standing on its feet: the sprite's bottom row is empty, so sink it by one texel.
        float sink = -1 / 8f;
        quad(at, out, tint,
                -0.5f, sink, 0f, left, 16 / 32f,
                0.5f, sink, 0f, right, 16 / 32f,
                0.5f, 1f + sink, 0f, right, 8 / 32f,
                -0.5f, 1f + sink, 0f, left, 8 / 32f);
    }

    /**
     * A plane with two real sides: once in the order given, facing where its own normal
     * says, and once the other way round, facing the other way. The render type culls
     * back faces, so each pixel sees exactly one side, lit as the side it is - a single
     * double-sided plane would be lit from the wrong side half the time, which shader packs
     * show as one wing dark and the other pale.
     */
    private static void quad(PoseStack.Pose at, VertexConsumer out, int tint,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        // Normal of the face as given: (2 - 1) x (3 - 1).
        float ax = x2 - x1, ay = y2 - y1, az = z2 - z1, bx = x3 - x1, by = y3 - y1, bz = z3 - z1;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        vertex(at, out, tint, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(at, out, tint, x2, y2, z2, u2, v2, nx, ny, nz);
        vertex(at, out, tint, x3, y3, z3, u3, v3, nx, ny, nz);
        vertex(at, out, tint, x4, y4, z4, u4, v4, nx, ny, nz);
        vertex(at, out, tint, x1, y1, z1, u1, v1, -nx, -ny, -nz);
        vertex(at, out, tint, x4, y4, z4, u4, v4, -nx, -ny, -nz);
        vertex(at, out, tint, x3, y3, z3, u3, v3, -nx, -ny, -nz);
        vertex(at, out, tint, x2, y2, z2, u2, v2, -nx, -ny, -nz);
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, int tint, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        out.addVertex(at, x, y, z)
                .setColor(tint, tint, tint, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(at, nx, ny, nz);
    }
}
