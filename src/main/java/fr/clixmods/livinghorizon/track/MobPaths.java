package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Which of the {@link MobAnimations} a remembered mob plays, from the blocks around it.
 *
 * <p>The ground around the mob is read once, when it is remembered: in every column
 * within {@link #RADIUS} blocks, the floor nearest the mob's own height with room above
 * it, from the chunks loaded here or else from the far world (Voxy or Distant Horizons).
 * What has no collision - grass, flowers, crops, snow, torches - is air to a mob, and a
 * path dug with a shovel is ground like any other. Water, leaves and what stands too tall to step on (a fence, a
 * wall) are not.
 *
 * <p>Then every animation is laid on that ground, turned eight ways and mirrored: one
 * fits when all along it the ground is there and never climbs nor drops more than a
 * block at a time. The widest that fit are kept and one of them is chosen, by the mob's
 * UUID; with none, it stands and turns on the spot.
 *
 * <p>The far world may not be readable yet - Voxy and Distant Horizons open theirs a moment
 * after the game's, and have nothing of ground they never saw. A choice made with columns
 * missing says so ({@link Choice#complete()}), and the ground is read again later. A ground
 * read whole is saved with the mob ({@link Ground#save()}): the next time the world is
 * entered, the mob walks on it at once, whatever the far world knows.
 */
public final class MobPaths {
    /** Blocks around the mob that are read. */
    static final int RADIUS = 8;
    static final int SIZE = RADIUS * 2 + 1;
    /** The highest step, up or down, between two neighbouring points of the ground. */
    static final double STEP = 1.05;
    /** Blocks of difference with the mob's own height that still count as its own ground. */
    private static final double LEVEL = 0.6;
    /** The window of each column that is read, below and above the mob's feet. */
    static final int BELOW = 4, ABOVE = 6;
    /** Air above a floor for a mob to stand on it. */
    private static final int ROOM = 2;
    /** Blocks around a flying or swimming mob it moves within: nothing to fall from. */
    private static final double FREE = 3.0;
    /** Animations kept, out of those that fit: at least this share of the widest one. */
    private static final double WIDE_ENOUGH = 0.6;

    private MobPaths() {
    }

    /** The animation a mob plays, laid on the ground it was chosen for; no animation: it stands. */
    public record Choice(Ground ground, MobAnimations.@Nullable Animation animation, double angle, boolean mirror) {
        /** Every column around was read: false when some of the far world could not be, worth reading again. */
        public boolean complete() {
            return ground.complete;
        }

        /** Plays the same as another: the same animation, turned and mirrored the same way. */
        public boolean same(Choice other) {
            return animation == other.animation && angle == other.angle && mirror == other.mirror;
        }
    }

    /**
     * Reads the ground around a mob and chooses its animation. Loaded columns are read at
     * once; the others come from the far world, off the thread, so the answer may come later.
     */
    public static CompletableFuture<Choice> plan(ClientLevel level, double x, double y, double z, double width, long seed) {
        int bx = Mth.floor(x), bz = Mth.floor(z), bottom = Mth.floor(y) - BELOW, height = BELOW + ABOVE + 1;
        double[][] ground = new double[SIZE][SIZE];
        int[] farX = new int[SIZE * SIZE], farZ = new int[SIZE * SIZE];
        int far = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState[] column = new BlockState[height];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int cx = bx + i - RADIUS, cz = bz + j - RADIUS;
                if (level.hasChunk(cx >> 4, cz >> 4)) {
                    for (int k = 0; k < height; k++) column[k] = level.getBlockState(pos.set(cx, bottom + k, cz));
                    ground[i][j] = floor(column, bottom, y);
                } else {
                    ground[i][j] = Double.NaN;
                    farX[far] = cx;
                    farZ[far] = cz;
                    far++;
                }
            }
        }
        if (far == 0) return CompletableFuture.completedFuture(choose(new Ground(ground, x, y, z), width, seed));
        return LodWorld.columns(Arrays.copyOf(farX, far), Arrays.copyOf(farZ, far), bottom, height).thenApply(read -> {
            boolean complete = true;
            for (int k = 0; k < read.length; k++) {
                if (read[k] != null) ground[farX[k] - bx + RADIUS][farZ[k] - bz + RADIUS] = floor(read[k], bottom, y);
                else complete = false;
            }
            return choose(new Ground(ground, x, y, z, complete), width, seed);
        });
    }

    /** Around a flying or swimming mob: a few blocks every way, at its own height. */
    public static Choice free(double x, double y, double z, long seed) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double[][] ground = new double[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                double dx = bx + i - RADIUS + 0.5 - x, dz = bz + j - RADIUS + 0.5 - z;
                ground[i][j] = dx * dx + dz * dz <= FREE * FREE ? y : Double.NaN;
            }
        }
        return choose(new Ground(ground, x, y, z), 0, seed);
    }

    /** Standing where it is: the ground around could not be read, so it is read again later. */
    public static Choice stand(double x, double y, double z) {
        double[][] ground = new double[SIZE][SIZE];
        for (double[] row : ground) Arrays.fill(row, Double.NaN);
        return new Choice(new Ground(ground, x, y, z, false), null, 0, false);
    }

    /**
     * The animation for this ground: of all that fit, turned and mirrored, the widest ones,
     * and one of them by the seed so that a herd does not all do the same.
     */
    static Choice choose(Ground ground, double width, long seed) {
        return choose(ground, width, seed, MobAnimations.ALL);
    }

    /**
     * For a player who logged off: the choice made, or, where none of the animations fits, one
     * of the {@link MobAnimations#CRAMPED} ones, a step or two on the same ground.
     */
    static Choice orCramped(Choice choice, double width, long seed) {
        if (choice.animation() != null) return choice;
        return choose(choice.ground(), width, seed, MobAnimations.CRAMPED);
    }

    private static Choice choose(Ground ground, double width, long seed, List<MobAnimations.Animation> animations) {
        int bias = (int) Math.floorMod(seed, 8L);
        boolean mirrorFirst = ((seed >> 7) & 1) == 1;
        List<Choice> fit = new ArrayList<>();
        double widest = 0;
        for (MobAnimations.Animation animation : animations) {
            search:
            for (int k = 0; k < 8; k++) {
                for (int m = 0; m < 2; m++) {
                    double angle = (k + bias) * Math.PI / 4;
                    boolean mirror = (m == 1) != mirrorFirst;
                    if (fits(ground, animation, angle, mirror, width)) {
                        fit.add(new Choice(ground, animation, angle, mirror));
                        widest = Math.max(widest, animation.extent);
                        break search;
                    }
                }
            }
        }
        double least = widest * WIDE_ENOUGH;
        fit.removeIf(choice -> choice.animation().extent < least);
        if (fit.isEmpty()) return new Choice(ground, null, 0, false);
        return fit.get((int) Math.floorMod(seed >> 11, (long) fit.size()));
    }

    /** All along the animation, ground under the mob, as wide as it, never a step higher than a block. */
    static boolean fits(Ground ground, MobAnimations.Animation animation, double angle, boolean mirror, double width) {
        double[] corners = animation.corners();
        double last = ground.height(ground.x, ground.z);
        for (int k = 0; k + 3 < corners.length; k += 2) {
            double ax = corners[k], az = corners[k + 1], bx = corners[k + 2], bz = corners[k + 3];
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) / 0.25));
            for (int s = 1; s <= samples; s++) {
                double u = s / (double) samples;
                double lx = ax + (bx - ax) * u, lz = az + (bz - az) * u;
                double h = walkable(ground, ground.x + MobAnimations.worldX(lx, lz, angle, mirror),
                        ground.z + MobAnimations.worldZ(lx, lz, angle, mirror), width);
                if (Double.isNaN(h) || Math.abs(h - last) > STEP) return false;
                last = h;
            }
        }
        return true;
    }

    /** The ground under a point, NaN unless there is ground as wide as the mob around it, with no wall nor drop. */
    static double walkable(Ground ground, double x, double z, double width) {
        double h = ground.height(x, z);
        if (Double.isNaN(h)) return h;
        double half = Math.max(0.2, width / 2 - 0.1);
        for (int side = 0; side < 4; side++) {
            double sx = x + (side == 0 ? -half : side == 1 ? half : 0), sz = z + (side == 2 ? -half : side == 3 ? half : 0);
            double s = ground.height(sx, sz);
            if (Double.isNaN(s) || Math.abs(s - h) > STEP) return Double.NaN;
        }
        return h;
    }

    /**
     * The height a mob stands at in one column: the top of the floor nearest {@code y}
     * with {@link #ROOM} blocks of air above it, NaN where that floor is water or leaves,
     * or where there is none. {@code column[k]} is the block at {@code bottom + k}, null
     * where unknown; above the column counts as air.
     */
    static double floor(BlockState[] column, int bottom, double y) {
        double best = Double.NaN, nearest = Double.POSITIVE_INFINITY;
        int open = ROOM;
        for (int k = column.length - 1; k >= 0; k--) {
            BlockState state = column[k];
            if (state == null) {
                open = 0;
                continue;
            }
            if (passable(state)) {
                open++;
                continue;
            }
            if (open >= ROOM && !(state.getBlock() instanceof LeavesBlock)) {
                boolean wet = !state.getFluidState().isEmpty();
                double top = bottom + k + (wet ? 1 : top(state));
                double gap = Math.abs(top - y);
                if (gap < nearest) {
                    nearest = gap;
                    best = wet ? Double.NaN : top;
                }
            }
            open = 0;
        }
        return best;
    }

    /** Nothing a mob bumps into: air, grass, flowers, crops, a thin layer of snow. */
    static boolean passable(BlockState state) {
        //? if >=26.3 {
        /*// 26.3 dropped blocksMotion; this is what it answered.
        boolean blocks = state.isSolid() && !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING);
        return !blocks && state.getFluidState().isEmpty();
        *///?} else {
        return !state.blocksMotion() && state.getFluidState().isEmpty();
        //?}
    }

    /** How high a mob stands on a block: a slab half, a path or farmland a little less than one, a fence one and a half. */
    private static double top(BlockState state) {
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** The height of the ground around a mob, as read: NaN where it cannot stand. */
    public static final class Ground {
        final double x, y, z;
        /** Every column was read; false when the far world had nothing for some of them. */
        final boolean complete;
        private final int bx, bz;
        private final double[][] height;

        /**
         * {@code ground[i][j]} is the height of the column {@code (floor(x) + i - RADIUS,
         * floor(z) + j - RADIUS)}; the mob's own block is where it stands, whatever was read there.
         */
        Ground(double[][] ground, double x, double y, double z) {
            this(ground, x, y, z, true);
        }

        Ground(double[][] ground, double x, double y, double z, boolean complete) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.complete = complete;
            bx = Mth.floor(x);
            bz = Mth.floor(z);
            height = new double[SIZE][];
            for (int i = 0; i < SIZE; i++) height[i] = ground[i].clone();
            double own = height[RADIUS][RADIUS];
            if (Double.isNaN(own) || Math.abs(own - y) > LEVEL) height[RADIUS][RADIUS] = y;
        }

        /** Where nothing could stand, in a saved ground. */
        private static final byte NONE = Byte.MIN_VALUE;

        /**
         * This ground in a few hundred characters, for the memory file: where it was read, then
         * every column's height in sixteenths of a block from the mob's own block.
         */
        public String save() {
            byte[] heights = new byte[SIZE * SIZE];
            int base = Mth.floor(y);
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    double h = height[i][j];
                    heights[i * SIZE + j] = Double.isNaN(h) ? NONE : (byte) Mth.clamp(Math.round((h - base) * 16), -127, 127);
                }
            }
            return x + " " + y + " " + z + " " + Base64.getEncoder().encodeToString(heights);
        }

        /** A ground written by {@link #save()}; null if it cannot be read. */
        public static @Nullable Ground load(String saved) {
            try {
                String[] parts = saved.split(" ");
                if (parts.length != 4) return null;
                double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
                byte[] heights = Base64.getDecoder().decode(parts[3]);
                if (heights.length != SIZE * SIZE) return null;
                int base = Mth.floor(y);
                double[][] ground = new double[SIZE][SIZE];
                for (int i = 0; i < SIZE; i++) {
                    for (int j = 0; j < SIZE; j++) {
                        byte h = heights[i * SIZE + j];
                        ground[i][j] = h == NONE ? Double.NaN : base + h / 16.0;
                    }
                }
                return new Ground(ground, x, y, z, true);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** Read around this very spot. */
        public boolean around(double x, double z) {
            return this.x == x && this.z == z;
        }

        /** The height of the ground at a point, NaN where unknown or not walkable. */
        public double height(double x, double z) {
            int i = Mth.floor(x) - bx + RADIUS, j = Mth.floor(z) - bz + RADIUS;
            if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) return Double.NaN;
            return height[i][j];
        }
    }
}
