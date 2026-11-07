package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.Profile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.21.9 && <26.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
//?}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the impostors: one picture per figure, always turned to the camera, submitted with
 * the entities so that shader packs and Voxy's depth treat it like one. A figure standing
 * on the ground is upright and only turns round; a bird, baked as seen from below or above,
 * faces the camera entirely.
 */
public final class ImpostorRenderer {
    /**
     * One figure to draw this frame. Kept and filled again from one frame to the next: a
     * frame allocates nothing once there are enough of them.
     */
    private static final class Billboard {
        /** Where the figure's origin is (the feet of an entity), from the camera, already pulled in past the far plane. */
        double x, y, z;
        /** Blocks along a side of its picture, scaled the same way. */
        double size;
        ImpostorAtlas.Sheet sheet;
        int view, light;
        /** The colour of its outline, 0 for none. */
        int outline;
        /** Turned to the camera up and down too, not only round: for a picture baked as seen from that height. */
        boolean facing;
        /** The camera's right along the ground, and the picture's up, as it is submitted. */
        float rightX, rightZ;
        final float[] up = new float[3];
    }

    /**
     * The billboards of one atlas page, handed to the game in one piece at the camera's pose,
     * each drawn at its own place from it. The game sorts what it is handed by render type,
     * one per page, so the order they are drawn in is the same as one piece per billboard;
     * one piece per billboard made the game copy the pose for each.
     */
    private static final class PageBatch implements Sink.Geometry {
        final List<Billboard> boards = new ArrayList<>();

        @Override
        public void draw(PoseStack.Pose at, VertexConsumer out) {
            for (Billboard board : boards) quad(at, out, board);
        }
    }

    /**
     * Whether impostors can have an outline in this version: drawn into the game's outline
     * buffer next to the models', which only this range of versions lets in by the side.
     */
    //? if >=1.21.9 && <26.2 {
    public static final boolean OUTLINES = true;
    //?} else {
    /*public static final boolean OUTLINES = false;
    *///?}

    /** An outlined impostor as it was submitted, drawn again into the outline buffer, at the camera's pose. */
    private record Outlined(PoseStack.Pose at, Billboard board) {
    }

    /**
     * Fraction of a tile left out on each side: half a pixel of the smallest level, which
     * the filter would otherwise blend with the tile next to it, a speck beside the figure.
     * Above and below too: the tile above ends with another figure's feet, and the one
     * below starts empty and black. The quad is shrunk to match, so the picture keeps its size.
     */
    public static final float INSET = 0.5f / (ImpostorViews.TILE >> (ImpostorAtlas.MIPS - 1));

    /** This frame's billboards, the first {@link #count}; the others wait for a later frame. */
    private static final List<Billboard> FRAME = new ArrayList<>();
    private static int count;
    private static final List<Outlined> OUTLINED = new ArrayList<>();
    /**
     * The batches handed to the game this frame, the first {@link #batches}: the game draws
     * them later in the frame, so none is filled again before the next one.
     */
    private static final List<PageBatch> BATCHES = new ArrayList<>();
    private static int batches;
    /** The batch of each page in the submission going on, null for a page with none yet. */
    private static PageBatch[] byPage = new PageBatch[16];

    private ImpostorRenderer() {
    }

    public static void beginFrame() {
        for (int i = 0; i < count; i++) FRAME.get(i).sheet = null;
        count = 0;
        for (int i = 0; i < batches; i++) BATCHES.get(i).boards.clear();
        batches = 0;
        OUTLINED.clear();
    }

    /**
     * One figure to draw this frame.
     *
     * @param x       where the figure's origin is (the feet of an entity), from the camera,
     *                already pulled in when past the far plane
     * @param size    blocks along a side of its picture, scaled the same way
     * @param outline the colour of its outline, 0 for none
     * @param facing  turned to the camera up and down too, not only round: for a picture baked
     *                as seen from that height
     */
    public static void add(double x, double y, double z, double size, ImpostorAtlas.Sheet sheet, int view, int light,
                           int outline, boolean facing) {
        if (count == FRAME.size()) FRAME.add(new Billboard());
        Billboard board = FRAME.get(count++);
        board.x = x;
        board.y = y;
        board.z = z;
        board.size = size;
        board.sheet = sheet;
        board.view = view;
        board.light = light;
        board.outline = outline;
        board.facing = facing;
    }

    /** From the entity submission, with the pose at the camera. */
    public static void submit(PoseStack pose, Sink collector) {
        long started = Profile.begin(Profile.Section.SUBMIT_IMPOSTORS);
        try {
            submitImpostors(pose, collector);
        } finally {
            Profile.end(Profile.Section.SUBMIT_IMPOSTORS, started);
        }
    }

    private static void submitImpostors(PoseStack pose, Sink collector) {
        int first = batches;
        PoseStack.Pose outlinePose = null;
        for (int i = 0; i < count; i++) {
            Billboard board = FRAME.get(i);
            double across = Math.sqrt(board.x * board.x + board.z * board.z);
            if (across < 1e-3) continue;
            // The camera's right along the ground: forward is (x, z), and right is forward x up.
            board.rightX = (float) (-board.z / across);
            board.rightZ = (float) (board.x / across);
            up(board, board.up);
            int page = board.sheet.page();
            if (page >= byPage.length) byPage = Arrays.copyOf(byPage, Math.max(page + 1, byPage.length * 2));
            PageBatch batch = byPage[page];
            if (batch == null) {
                if (batches == BATCHES.size()) BATCHES.add(new PageBatch());
                byPage[page] = batch = BATCHES.get(batches++);
            }
            batch.boards.add(board);
            //? if >=1.21.9 && <26.2 {
            if (board.outline != 0) {
                if (outlinePose == null) outlinePose = pose.last().copy();
                OUTLINED.add(new Outlined(outlinePose, board));
            }
            //?}
            PolygonStats.impostor();
        }
        for (int i = first; i < batches; i++) {
            PageBatch batch = BATCHES.get(i);
            int page = batch.boards.get(0).sheet.page();
            byPage[page] = null;
            collector.draw(pose, ImpostorAtlas.type(page), batch);
        }
    }

    /**
     * The outlines of this frame's impostors, once the world's models have written theirs:
     * the same quads, through the picture's cutout, into the outline buffer. Once a frame.
     */
    public static void drawOutlines() {
        //? if >=1.21.9 && <26.2 {
        if (OUTLINED.isEmpty()) return;
        OutlineBufferSource buffers = Minecraft.getInstance().renderBuffers().outlineBufferSource();
        for (Outlined outlined : OUTLINED) {
            Billboard board = outlined.board;
            buffers.setColor(board.outline);
            quad(outlined.at, buffers.getBuffer(ImpostorAtlas.type(board.sheet.page())), board);
        }
        OUTLINED.clear();
        //?}
    }

    /**
     * The picture's up: straight up for an upright one; for one facing the camera, straight
     * up as the camera sees it, square to its line of sight, the way the picture was baked.
     */
    private static void up(Billboard board, float[] up) {
        up[0] = 0f;
        up[1] = 1f;
        up[2] = 0f;
        if (!board.facing) return;
        double length = Math.sqrt(board.x * board.x + board.y * board.y + board.z * board.z);
        double fx = board.x / length, fy = board.y / length, fz = board.z / length;
        double ux = -fx * fy, uy = 1 - fy * fy, uz = -fz * fy;
        double u = Math.sqrt(ux * ux + uy * uy + uz * uz);
        // Straight above or below: the picture is seen edge-on whatever is up; keep it upright.
        if (u < 1e-6) return;
        up[0] = (float) (ux / u);
        up[1] = (float) (uy / u);
        up[2] = (float) (uz / u);
    }

    /** The corners of the quad being written, x, y, z each: one quad at a time, on the render thread. */
    private static final float[] CORNERS = new float[12];

    /** The quad of one billboard, at {@code at}, the camera's pose: the billboard is placed from it. */
    private static void quad(PoseStack.Pose at, VertexConsumer out, Billboard board) {
        float rightX = board.rightX, rightZ = board.rightZ;
        float[] up = board.up;
        float half = (float) board.size * (0.5f - INSET);
        float feet = board.sheet.feet();
        float bottom = -(1 - feet - INSET) * (float) board.size;
        float top = (feet - INSET) * (float) board.size;
        // A tile of the page, the top of the picture at the top.
        float uSpan = board.sheet.uSpan(), vSpan = board.sheet.vSpan();
        float u0 = board.sheet.u(board.view) + INSET * uSpan, u1 = u0 + (1f - 2 * INSET) * uSpan;
        float vTop = board.sheet.v() + INSET * vSpan, vBottom = board.sheet.v() + (1f - INSET) * vSpan;
        float lx = -rightX * half, lz = -rightZ * half, rx = rightX * half, rz = rightZ * half;
        float bx = up[0] * bottom, by = up[1] * bottom, bz = up[2] * bottom;
        float tx = up[0] * top, ty = up[1] * top, tz = up[2] * top;
        float[] corners = CORNERS;
        corners[0] = lx + bx;
        corners[1] = by;
        corners[2] = lz + bz;
        corners[3] = rx + bx;
        corners[4] = by;
        corners[5] = rz + bz;
        corners[6] = rx + tx;
        corners[7] = ty;
        corners[8] = rz + tz;
        corners[9] = lx + tx;
        corners[10] = ty;
        corners[11] = lz + tz;
        // Leant back round the middle of the figure, so that shader packs light it from above too:
        // half way up from the feet, or the middle of the picture for one facing the camera.
        float middle = board.facing ? (top + bottom) / 2 : top / 2;
        ImpostorLean.lean(board.x, board.y, board.z, board.x + up[0] * middle, board.y + up[1] * middle,
                board.z + up[2] * middle, corners);
        float ox = (float) board.x, oy = (float) board.y, oz = (float) board.z;
        vertex(at, out, board.light, ox + corners[0], oy + corners[1], oz + corners[2], u0, vBottom);
        vertex(at, out, board.light, ox + corners[3], oy + corners[4], oz + corners[5], u1, vBottom);
        vertex(at, out, board.light, ox + corners[6], oy + corners[7], oz + corners[8], u1, vTop);
        vertex(at, out, board.light, ox + corners[9], oy + corners[10], oz + corners[11], u0, vTop);
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, int light, float x, float y, float z,
                               float u, float v) {
        // Lit as a top face, which the game's light leaves at full brightness: the pictures
        // already carry the shading a model gets in the world. Only the light where the
        // figure stands (sky, torches, night) is applied on top. Shader packs never see this
        // normal: Iris takes the quad's own, which ImpostorLean turns up.
        out.addVertex(at, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, 0f, 1f, 0f);
    }
}
