package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.resources.language.I18n;
//? if >=1.21.11 {
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
//?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * What became of every distant mob and player this frame, for the debug view: a box of
 * its colour around it, seen through terrain, a label over it, and a count for the panel.
 * The boxes are the game's own debug gizmos, drawn at the end of the world.
 *
 * <p>The outlines use the same colours ({@code outline*} in {@link FarConfig}): each figure
 * drawn gets the colour of what draws it, set where it is drawn, impostors {@link #IMPOSTOR},
 * and a mob left out of the frame gets a cross where it stands, in the colour of why.
 */
public final class DebugMarks {
    /** An impostor, the flat picture standing in for a model, whatever it stands for. */
    public static final int IMPOSTOR = 0xFFFF55FF;
    /** Pixels from the centre of a cross to the end of an arm. */
    private static final double CROSS_PIXELS = 5;

    /** Why a mob is drawn, or not. */
    public enum Mark {
        /** A copy, drawn by the mod. */
        FAKE(0xFF55FF55),
        /** A copy of an animal foreseen from the seed, in land nobody explored: supposed, not seen. */
        FORESEEN(0xFFFF77AA),
        /** A real mob the server sends but the game does not draw, drawn by the mod. */
        LIVE(0xFF55FFFF),
        /** A distant player, drawn by the mod. */
        PLAYER(0xFF5599FF),
        /** A real mob the game draws itself. */
        GAME(0xFFFFFFFF),
        /** Not drawn: smaller than about half a pixel. */
        TINY(0xFFFFFF55, true),
        /** Not drawn: behind terrain. */
        HIDDEN(0xFFFFAA00, true),
        /** Not drawn: a copy this close to the player is gone from view, so it cannot be walked up to. */
        NEAR(0xFFAA7744),
        /** Not drawn: no terrain is drawn under it, neither the game's nor the far terrain. */
        VOID(0xFF5522AA, true),
        /** Not drawn: outside the view. */
        OUTSIDE(0xFFFF5555, true),
        /** Not drawn yet: its copy is being built. */
        WAITING(0xFFAA55FF, true),
        /** Not drawn: remembered, but past the most mobs shown at once. */
        SPARE(0xFF888888, true);

        public final int color;
        /** A mob the mod leaves out of the frame, marked by a cross; the real one is not near. */
        public final boolean leftOut;

        Mark(int color) {
            this(color, false);
        }

        Mark(int color, boolean leftOut) {
            this.color = color;
            this.leftOut = leftOut;
        }

        public String label() {
            return I18n.get("livinghorizon.debug.mark." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final int[] COUNTS = new int[Mark.values().length];
    private static final int[] LAST = new int[Mark.values().length];
    private static double eyeX, eyeY, eyeZ, pixelsPerRadian;
    private static boolean boxes, labels, crosses;
    /** Which kinds get a box and a label, read at the start of each frame. */
    private static final boolean[] BOXED = new boolean[Mark.values().length];

    private DebugMarks() {
    }

    /** Whether anything of the debug view is on: marks cost nothing otherwise. */
    public static boolean active() {
        FarConfig config = FarConfig.get();
        return config.debugBoxes || config.debugLabels || config.outlineLeftOut || config.debugHud
                || DebugHud.entryEnabled();
    }

    public static boolean gameMobs() {
        FarConfig config = FarConfig.get();
        return config.debugGameMobs && (config.debugBoxes || config.debugLabels);
    }

    /** Whether a kind of mark gets its box and label, as chosen in the Boxes screen. */
    static boolean boxed(FarConfig config, Mark mark) {
        return switch (mark) {
            case GAME -> config.debugGameMobs;
            case LIVE -> config.boxLiveMobs;
            case FAKE -> config.boxCopies;
            case FORESEEN -> config.boxForeseen;
            case PLAYER -> config.boxPlayers;
            default -> config.boxLeftOut;
        };
    }

    /** Wants the mobs left out of the frame altogether, like the spare ones: only for boxes, labels and crosses. */
    public static boolean drawing() {
        return boxes || labels || crosses;
    }

    /**
     * The outline colour (RGB) of a figure drawn as a model, from what draws it, or 0 when
     * that kind's outline is off.
     *
     * @param kind {@link Mark#GAME}, {@link Mark#LIVE}, {@link Mark#FAKE} or {@link Mark#PLAYER}
     */
    public static int outline(FarConfig config, Mark kind) {
        boolean on = switch (kind) {
            case GAME -> config.outlineGameMobs;
            case LIVE -> config.outlineLiveMobs;
            case FAKE -> config.outlineCopies;
            case FORESEEN -> config.outlineForeseen;
            case PLAYER -> config.outlinePlayers;
            default -> false;
        };
        return on ? kind.color & 0xFFFFFF : 0;
    }

    /** The outline colour (RGB) of an impostor, or 0 when off. */
    public static int impostorOutline(FarConfig config) {
        return config.outlineImpostors ? IMPOSTOR & 0xFFFFFF : 0;
    }

    public static void begin(Vec3 eye, double pixels) {
        FarConfig config = FarConfig.get();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        pixelsPerRadian = pixels;
        boxes = config.debugBoxes;
        labels = config.debugLabels;
        for (Mark mark : Mark.values()) BOXED[mark.ordinal()] = boxed(config, mark);
        crosses = config.outlineLeftOut;
        java.util.Arrays.fill(COUNTS, 0);
    }

    public static void end() {
        System.arraycopy(COUNTS, 0, LAST, 0, COUNTS.length);
    }

    /** Last frame's count of one mark, for the panel. */
    public static int count(Mark mark) {
        return LAST[mark.ordinal()];
    }

    public static void mark(Entity entity, Mark mark) {
        mark(entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight(), entity.getType(), mark);
    }

    public static void mark(double x, double y, double z, double width, double height, EntityType<?> type, Mark mark) {
        COUNTS[mark.ordinal()]++;
        boolean cross = crosses && mark.leftOut;
        boolean boxes = DebugMarks.boxes && BOXED[mark.ordinal()], labels = DebugMarks.labels && BOXED[mark.ordinal()];
        if (!boxes && !labels && !cross) return;
        double dx = x - eyeX, dy = y + height / 2 - eyeY, dz = z - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        //? if >=1.21.11 {
        try {
            if (cross) cross(x, y + height / 2, z, distance, mark.color);
            if (boxes) {
                double half = width / 2;
                Gizmos.cuboid(new AABB(x - half, y, z - half, x + half, y + height, z + half),
                        GizmoStyle.stroke(mark.color, 2f)).setAlwaysOnTop();
                // A dot as well: past a few hundred blocks the box is smaller than its lines.
                Gizmos.point(new Vec3(x, y + height / 2, z), mark.color, 5f).setAlwaysOnTop();
            }
            if (labels) {
                double pixels = Math.max(height, width) / Math.max(distance, 1e-3) * pixelsPerRadian;
                String text = String.format(Locale.ROOT, "%s · %s · %d m · %.1f px", mark.label(),
                        I18n.get(type.getDescriptionId()), Math.round(distance), pixels);
                // A size in the world that grows with the distance: the same few pixels on screen.
                float scale = (float) (16.0 * distance / pixelsPerRadian * 1.4);
                Gizmos.billboardText(text, new Vec3(x, y + height + distance / pixelsPerRadian * 12, z),
                        TextGizmo.Style.forColorAndCentered(mark.color).withScale(scale)).setAlwaysOnTop();
            }
        } catch (IllegalStateException ignored) {
            // No gizmo collector outside the frame: nothing to draw into.
        }
        //?}
    }

    //? if >=1.21.11 {
    /**
     * An X facing the camera, the same few pixels across at any distance, seen through
     * terrain. Only where the mob is: not what it is.
     */
    private static void cross(double x, double y, double z, double distance, int color) {
        double dx = x - eyeX, dy = y - eyeY, dz = z - eyeZ;
        double length = Math.max(distance, 1e-3);
        dx /= length;
        dy /= length;
        dz /= length;
        // Right is the line of sight across the vertical; straight up or down, any horizontal.
        double rx = -dz, rz = dx;
        double across = Math.sqrt(rx * rx + rz * rz);
        if (across < 1e-3) {
            rx = 1;
            rz = 0;
            across = 1;
        }
        rx /= across;
        rz /= across;
        // Up is right across the line of sight.
        double ux = -rz * dy, uy = rz * dx - rx * dz, uz = rx * dy;
        double arm = CROSS_PIXELS * length / pixelsPerRadian;
        double ax = (rx + ux) * arm, ay = uy * arm, az = (rz + uz) * arm;
        double bx = (rx - ux) * arm, by = -uy * arm, bz = (rz - uz) * arm;
        Gizmos.line(new Vec3(x - ax, y - ay, z - az), new Vec3(x + ax, y + ay, z + az), color, 2f).setAlwaysOnTop();
        Gizmos.line(new Vec3(x - bx, y - by, z - bz), new Vec3(x + bx, y + by, z + bz), color, 2f).setAlwaysOnTop();
    }
    //?}
}
