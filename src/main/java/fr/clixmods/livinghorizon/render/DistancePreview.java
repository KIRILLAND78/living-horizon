package fr.clixmods.livinghorizon.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.compat.FarDepth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a distance setting means, drawn in the world behind the mod's settings screen while
 * that setting is hovered or changed: the screen keeps the world in view on the right.
 *
 * <ul>
 *   <li>{@link Shape#AROUND}, blocks from the player: a sphere of that radius round the camera,
 *       from its bottom up to a fence's height above the camera, with a bright rail along the
 *       top. Where it meets the ground is that distance, the far terrain's ground included;
 *       the fence looks lower the farther it stands.</li>
 *   <li>{@link Shape#CHUNKS}, chunks round the player's: the same fence along the edge of the
 *       chunks a scan reads, chunk by chunk, as {@code MobScan} chooses them.</li>
 *   <li>{@link Shape#ABOVE}, blocks above the player or the sea, whichever is higher: a
 *       ceiling at that height, as wide as the birds fly, with the chunk lines on it.</li>
 * </ul>
 *
 * <p>Submitted with the entities, like the birds, so that the terrain in front of it, the far
 * terrain's included, hides it. Its render type writes no depth ({@link #type}): the water,
 * the clouds and whatever another mod draws after it are drawn exactly as without it.
 */
public final class DistancePreview {
    /** What a setting's value measures, and so how it is drawn. */
    public enum Shape {
        /** Blocks from the player, every way: a sphere. */
        AROUND,
        /** Chunks round the player's chunk: the edge of those within that many. */
        CHUNKS,
        /** Blocks above the player or the sea, whichever is higher: a ceiling. */
        ABOVE
    }

    /**
     * Plain colour, see-through, writing no depth and never fogged: the game's fog would hide a
     * fence past a hundred blocks or so. The game's debug quads are all that from 1.21.11; before,
     * they write depth, which would hide the water behind.
     */
    //? if >=1.21.11
    private static final RenderType PLAIN = RenderTypes.debugQuads();
    /**
     * A glowing entity's, which writes no depth either but is fogged as entities are. Iris gives
     * it a program of the shader pack, as it gives none to the debug quads, which Photon's passes
     * then paint over: under a pack, this one. Before 1.21.11, the only one.
     */
    private static final RenderType GLOWING = RenderTypes.entityTranslucentEmissive(
            Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/misc/preview.png"));
    private static final int LIGHT = LightTexture.pack(15, 15);

    /** The mod's orange, as on Sodium's page. */
    private static final int RED = 255, GREEN = 140, BLUE = 20;
    /** Opacity of the fence at its foot and up to the horizon, 0 to 1... */
    private static final float BODY = 0.3f;
    /** ...under its rail... */
    private static final float BODY_TOP = 0.12f;
    /** ...and of the rail along its top. */
    private static final float RAIL = 0.9f;
    /** How thick the rail looks, in radians from the camera: two or three pixels. */
    private static final double RAIL_ANGLE = 0.005;
    /** Opacity of the ceiling, and of the chunk lines on it. */
    private static final float CEILING = 0.25f, LINE = 0.7f;

    /** Rows of the sphere under the horizon, in degrees: it stays round wherever the ground meets it. */
    private static final float[] BELOW = {-90, -80, -70, -60, -50, -40, -30, -20, -12, -6, -2, 0};
    /** The highest the fence goes, in degrees above the horizon, when it is taller than far. */
    private static final double HIGHEST = 80;
    /** Columns of the sphere and of the ceiling. */
    private static final int AROUND_STEPS = 128;

    /** Rings of the ceiling, as parts of its radius, and how opaque each is: it fades out at its edge. */
    private static final float[] CEILING_RINGS = {0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f};
    private static final float[] CEILING_ALPHA = {1f, 1f, 1f, 1f, 0.5f, 0f};
    /** The widest the chunk lines on the ceiling reach, in blocks. */
    private static final double GRID_REACH = 256;
    /** Width of a chunk line, as a part of its distance from the camera: about two pixels. */
    private static final double LINE_WIDTH = 0.004;

    /** How long a preview takes to fade out once the screen stops asking for it, in ns. */
    private static final long FADE = 250_000_000L;

    private static @Nullable Shape shape;
    private static int value;
    /** Asked for since the world was last drawn: the screen asks every frame, after the world. */
    private static boolean asked;
    /** When it was last drawn whole, for the fade out. */
    private static long drawnAt;

    private DistancePreview() {
    }

    /**
     * Draws this distance in the world: asked again every frame it should stay, it fades out
     * once nobody asks. Counted in frames rather than time, so that it never blinks however
     * slowly the game draws.
     */
    public static void show(Shape what, int blocksOrChunks) {
        shape = what;
        value = blocksOrChunks;
        asked = true;
    }

    /** Whether a preview is on screen, fading out included. */
    public static boolean showing() {
        return shape != null && (asked || fadeOut(System.nanoTime()) > 0f);
    }

    private static float fadeOut(long now) {
        return Math.max(0f, 1f - (float) (now - drawnAt) / FADE);
    }

    /**
     * How tall the fence stands above the camera at this distance, in blocks: a little taller
     * the farther it is, so that it never shrinks to nothing.
     */
    static double fenceHeight(double distance) {
        return 16 + distance * 0.02;
    }

    /** With the entities, the camera at the origin of {@code pose}. */
    public static void submit(PoseStack pose, Vec3 camera, Sink sink) {
        Shape what = shape;
        if (what == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        float fade;
        if (asked) {
            asked = false;
            drawnAt = now;
            fade = 1f;
        } else {
            fade = fadeOut(now);
        }
        // Gone with the screen: back in the game, nothing is left in the world.
        if (fade <= 0f || minecraft.screen == null) {
            shape = null;
            return;
        }
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || FarDepth.irisShadowPass()) return;
        double reach = DepthFar.of(minecraft) * 0.95;
        boolean plain = plain();
        switch (what) {
            case AROUND -> {
                // 0 is no limit: nothing to show.
                if (value <= 0) return;
                double radius = value;
                GhostRenderer.noteDistance(radius);
                // Past the far plane: closer by the same ratio everywhere, the same on screen.
                double pull = radius > reach ? reach / radius : 1.0;
                double top = fenceHeight(radius);
                sink.draw(pose, type(plain), (at, out) -> sphere(new Pen(at, out, plain), radius * pull, top * pull,
                        fade));
            }
            case CHUNKS -> {
                int chunks = Math.max(1, value);
                int cx = Mth.floor(player.getX()) >> 4, cz = Mth.floor(player.getZ()) >> 4;
                double far = (chunks + 1.5) * 16 * Math.sqrt(2);
                GhostRenderer.noteDistance(far);
                double pull = far > reach ? reach / far : 1.0;
                double bottom = level.getMinY() - camera.y;
                double top = fenceHeight(chunks * 16.0);
                sink.draw(pose, type(plain), (at, out) -> chunks(new Pen(at, out, plain), chunks, cx, cz, camera,
                        bottom, top, pull, fade));
            }
            case ABOVE -> {
                double height = Math.max(player.getY(), level.getSeaLevel()) + Math.max(0, value) - camera.y;
                double radius = Math.max(100, FarConfig.get().birdMaxDistance);
                double far = Math.sqrt(radius * radius + height * height);
                GhostRenderer.noteDistance(far);
                double pull = far > reach ? reach / far : 1.0;
                sink.draw(pose, type(plain), (at, out) -> ceiling(new Pen(at, out, plain), camera, height, radius,
                        pull, fade));
            }
        }
    }

    // --- Around: a sphere ------------------------------------------------------------------

    /** The sphere, {@code radius} round the camera, up to {@code top} above it, then its rail. */
    private static void sphere(Pen pen, double radius, double top, float fade) {
        for (int row = 0; row + 1 < BELOW.length; row++) {
            band(pen, radius, BELOW[row], BELOW[row + 1], BODY * fade, BODY * fade);
        }
        double highest = top >= radius ? HIGHEST : Math.min(HIGHEST, Math.toDegrees(Math.asin(top / radius)));
        // Rows no more than 8 degrees apart, the sphere round up to there.
        int rows = Math.max(1, (int) Math.ceil(highest / 8));
        for (int row = 0; row < rows; row++) {
            double low = highest * row / rows, high = highest * (row + 1) / rows;
            band(pen, radius, low, high, Mth.lerp((float) row / rows, BODY, BODY_TOP) * fade,
                    Mth.lerp((float) (row + 1) / rows, BODY, BODY_TOP) * fade);
        }
        double rail = Math.toDegrees(RAIL_ANGLE);
        band(pen, radius, highest, highest + rail, RAIL * fade, RAIL * fade);
    }

    /** One ring of the sphere between two elevations, in degrees, at these opacities. */
    private static void band(Pen pen, double radius, double low, double high,
                             float lowAlpha, float highAlpha) {
        double lowRad = Math.toRadians(low), highRad = Math.toRadians(high);
        float lowY = (float) (Math.sin(lowRad) * radius), highY = (float) (Math.sin(highRad) * radius);
        float lowRing = (float) (Math.cos(lowRad) * radius), highRing = (float) (Math.cos(highRad) * radius);
        for (int step = 0; step < AROUND_STEPS; step++) {
            double a = step * Math.PI * 2 / AROUND_STEPS, b = (step + 1) * Math.PI * 2 / AROUND_STEPS;
            float sa = (float) Math.sin(a), ca = (float) Math.cos(a), sb = (float) Math.sin(b), cb = (float) Math.cos(b);
            vertex(pen, sa * lowRing, lowY, ca * lowRing, lowAlpha);
            vertex(pen, sb * lowRing, lowY, cb * lowRing, lowAlpha);
            vertex(pen, sb * highRing, highY, cb * highRing, highAlpha);
            vertex(pen, sa * highRing, highY, ca * highRing, highAlpha);
        }
    }

    // --- Chunks: a fence along the edge of the chunks within the radius ----------------------

    /**
     * How far a column of chunks {@code dx} from the centre reaches either way, in chunks, within
     * {@code radius} as a scan counts it ({@code dx² + dz² <= radius²}); -1 for no chunk at all.
     */
    static int reach(int radius, int dx) {
        long left = (long) radius * radius - (long) dx * dx;
        if (left < 0) return -1;
        int dz = (int) Math.sqrt(left);
        while ((long) dz * dz > left) dz--;
        while ((long) (dz + 1) * (dz + 1) <= left) dz++;
        return dz;
    }

    private static void chunks(Pen pen, int radius, int cx, int cz, Vec3 camera,
                               double bottom, double top, double pull, float fade) {
        // The north and south edges of each run of columns that reach as far.
        for (int dx = -radius; dx <= radius; ) {
            int dz = reach(radius, dx), end = dx;
            while (end + 1 <= radius && reach(radius, end + 1) == dz) end++;
            double x0 = (cx + dx) * 16.0, x1 = (cx + end + 1) * 16.0;
            fence(pen, x0, (cz + dz + 1) * 16.0, x1, (cz + dz + 1) * 16.0, camera, bottom, top, pull, fade);
            fence(pen, x0, (cz - dz) * 16.0, x1, (cz - dz) * 16.0, camera, bottom, top, pull, fade);
            dx = end + 1;
        }
        // The east and west edges, where a column reaches farther than the one beside it.
        for (int dx = -radius; dx <= radius + 1; dx++) {
            int left = reach(radius, dx - 1), right = reach(radius, dx);
            if (left == right) continue;
            int low = Math.min(left, right), high = Math.max(left, right);
            double x = (cx + dx) * 16.0;
            if (low < 0) {
                fence(pen, x, (cz - high) * 16.0, x, (cz + high + 1) * 16.0, camera, bottom, top, pull, fade);
            } else {
                fence(pen, x, (cz + low + 1) * 16.0, x, (cz + high + 1) * 16.0, camera, bottom, top, pull, fade);
                fence(pen, x, (cz - high) * 16.0, x, (cz - low) * 16.0, camera, bottom, top, pull, fade);
            }
        }
    }

    /**
     * A fence from the bottom of the world to {@code top} above the camera, along a chunk edge
     * between two points (world coordinates), with its rail.
     */
    private static void fence(Pen pen, double x0, double z0, double x1, double z1,
                              Vec3 camera, double bottom, double top, double pull, float fade) {
        double ax = x0 - camera.x, az = z0 - camera.z, bx = x1 - camera.x, bz = z1 - camera.z;
        float pax = (float) (ax * pull), paz = (float) (az * pull), pbx = (float) (bx * pull), pbz = (float) (bz * pull);
        float low = (float) (bottom * pull), high = (float) (top * pull);
        float horizon = Math.max(low, Math.min(0f, high));
        // The rail as thick as on the sphere: thicker where the fence is farther.
        float aRail = high + (float) (Math.sqrt(ax * ax + az * az) * RAIL_ANGLE * pull);
        float bRail = high + (float) (Math.sqrt(bx * bx + bz * bz) * RAIL_ANGLE * pull);
        float body = BODY * fade, under = BODY_TOP * fade, rail = RAIL * fade;
        quad(pen, pax, paz, pbx, pbz, low, low, horizon, horizon, body, body);
        quad(pen, pax, paz, pbx, pbz, horizon, horizon, high, high, body, under);
        quad(pen, pax, paz, pbx, pbz, high, high, aRail, bRail, rail, rail);
    }

    /** An upright quad between two points of the ground, each with its own bottom and top. */
    private static void quad(Pen pen, float ax, float az, float bx, float bz,
                             float aLow, float bLow, float aHigh, float bHigh, float lowAlpha, float highAlpha) {
        vertex(pen, ax, aLow, az, lowAlpha);
        vertex(pen, bx, bLow, bz, lowAlpha);
        vertex(pen, bx, bHigh, bz, highAlpha);
        vertex(pen, ax, aHigh, az, highAlpha);
    }

    // --- Above: a ceiling --------------------------------------------------------------------

    private static void ceiling(Pen pen, Vec3 camera, double height, double radius,
                                double pull, float fade) {
        float y = (float) (height * pull);
        for (int ring = 0; ring + 1 < CEILING_RINGS.length; ring++) {
            float inner = (float) (CEILING_RINGS[ring] * radius * pull), outer = (float) (CEILING_RINGS[ring + 1] * radius * pull);
            float innerAlpha = CEILING_ALPHA[ring] * CEILING * fade, outerAlpha = CEILING_ALPHA[ring + 1] * CEILING * fade;
            for (int step = 0; step < AROUND_STEPS; step++) {
                double a = step * Math.PI * 2 / AROUND_STEPS, b = (step + 1) * Math.PI * 2 / AROUND_STEPS;
                float sa = (float) Math.sin(a), ca = (float) Math.cos(a), sb = (float) Math.sin(b), cb = (float) Math.cos(b);
                vertex(pen, sa * inner, y, ca * inner, innerAlpha);
                vertex(pen, sb * inner, y, cb * inner, innerAlpha);
                vertex(pen, sb * outer, y, cb * outer, outerAlpha);
                vertex(pen, sa * outer, y, ca * outer, outerAlpha);
            }
        }
        // The chunk lines, so that the ceiling reads as flat and its height can be judged.
        double grid = Math.min(radius, GRID_REACH);
        int first = Mth.floor((camera.x - grid) / 16), last = Mth.floor((camera.x + grid) / 16) + 1;
        for (int line = first; line <= last; line++) gridLine(pen, line * 16.0 - camera.x, height, grid, pull, fade, true);
        first = Mth.floor((camera.z - grid) / 16);
        last = Mth.floor((camera.z + grid) / 16) + 1;
        for (int line = first; line <= last; line++) gridLine(pen, line * 16.0 - camera.z, height, grid, pull, fade, false);
    }

    /**
     * One chunk line across the ceiling, {@code across} blocks from the camera, cut in pieces a
     * chunk long; as wide as a couple of pixels wherever it is, fading out with the ceiling.
     */
    private static void gridLine(Pen pen, double across, double height, double grid,
                                 double pull, float fade, boolean alongZ) {
        double half = grid * grid - across * across;
        if (half <= 0) return;
        double length = Math.sqrt(half);
        int pieces = Math.max(1, (int) Math.ceil(length * 2 / 16));
        float y = (float) (height * pull);
        for (int piece = 0; piece < pieces; piece++) {
            double from = -length + piece * length * 2 / pieces, to = -length + (piece + 1) * length * 2 / pieces;
            double fromWidth = width(across, height, from), toWidth = width(across, height, to);
            float fromAlpha = ceilingAlpha(Math.hypot(across, from) / grid) * LINE * fade;
            float toAlpha = ceilingAlpha(Math.hypot(across, to) / grid) * LINE * fade;
            if (fromAlpha <= 0f && toAlpha <= 0f) continue;
            if (alongZ) {
                vertex(pen, (float) ((across - fromWidth) * pull), y, (float) (from * pull), fromAlpha);
                vertex(pen, (float) ((across + fromWidth) * pull), y, (float) (from * pull), fromAlpha);
                vertex(pen, (float) ((across + toWidth) * pull), y, (float) (to * pull), toAlpha);
                vertex(pen, (float) ((across - toWidth) * pull), y, (float) (to * pull), toAlpha);
            } else {
                vertex(pen, (float) (from * pull), y, (float) ((across - fromWidth) * pull), fromAlpha);
                vertex(pen, (float) (from * pull), y, (float) ((across + fromWidth) * pull), fromAlpha);
                vertex(pen, (float) (to * pull), y, (float) ((across + toWidth) * pull), toAlpha);
                vertex(pen, (float) (to * pull), y, (float) ((across - toWidth) * pull), toAlpha);
            }
        }
    }

    /** Half the width of a chunk line at this point of the ceiling, from how far it is from the camera. */
    private static double width(double across, double height, double along) {
        return Math.max(0.05, Math.sqrt(across * across + height * height + along * along) * LINE_WIDTH / 2);
    }

    /** How opaque the ceiling is this part of its radius out: whole, then fading to nothing at its edge. */
    static float ceilingAlpha(double part) {
        if (part >= 1) return 0f;
        for (int ring = 0; ring + 1 < CEILING_RINGS.length; ring++) {
            if (part <= CEILING_RINGS[ring + 1]) {
                float t = (float) ((part - CEILING_RINGS[ring]) / (CEILING_RINGS[ring + 1] - CEILING_RINGS[ring]));
                return Mth.lerp(t, CEILING_ALPHA[ring], CEILING_ALPHA[ring + 1]);
            }
        }
        return 0f;
    }

    // --- Vertices ------------------------------------------------------------------------------

    /** Whether this frame draws with {@code PLAIN}: from 1.21.11, without a shader pack. */
    private static boolean plain() {
        //? if >=1.21.11 {
        return !FarDepth.shaderPack();
        //?} else {
        /*return false;
        *///?}
    }

    /** {@code PLAIN} or {@link #GLOWING}. */
    private static RenderType type(boolean plain) {
        //? if >=1.21.11
        if (plain) return PLAIN;
        return GLOWING;
    }

    /** Where the vertices go, and whether they are {@code PLAIN}'s, a position and a colour only. */
    private record Pen(PoseStack.Pose at, VertexConsumer out, boolean plain) {
    }

    /** A vertex of the preview's colour at this opacity, 0 to 1. */
    private static void vertex(Pen pen, float x, float y, float z, float alpha) {
        PoseStack.Pose at = pen.at();
        VertexConsumer out = pen.out();
        int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255);
        //? if >=1.21.11 {
        if (pen.plain()) {
            out.addVertex(at, x, y, z).setColor(RED, GREEN, BLUE, a);
            return;
        }
        //?}
        out.addVertex(at, x, y, z)
                .setColor(RED, GREEN, BLUE, a)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(at, 0f, 1f, 0f);
    }
}
