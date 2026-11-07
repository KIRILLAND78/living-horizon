package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

/**
 * A minecart that was still rolling when the server stopped sending it: its copy rolls on
 * along the rails, the way the server moves it.
 *
 * <p>The server sends a minecart 128 blocks away at most (8 chunks, two less than an
 * animal), but keeps moving it in every chunk within its simulation distance of a player,
 * 10 chunks by default. A minecart sent off along powered rails leaves the client while
 * the server still runs it: left where it was last seen, its copy would stand on the line
 * while the real one rolled on to the station.
 *
 * <p>This is the server's rail physics (the one every version uses unless the experimental
 * minecart changes are on), for an empty cart or one carrying a mob: slopes, curves,
 * powered rails, friction. Nothing collides and nothing is pushed. Off its rails, or out of
 * the blocks this client has, it stops.
 */
public final class MinecartCoast {
    /** The blocks the cart rolls on: the client's world, or a test's. Null where nothing is loaded. */
    public interface Ground {
        @Nullable BlockState at(int x, int y, int z);
    }

    /** What gravity adds per tick on a slope. */
    private static final double SLOPE = 0.0078125;
    /** Blocks per tick: 8 blocks a second. */
    private static final double MAX_SPEED = 0.4;
    /** What a powered rail adds per tick. */
    private static final double BOOST = 0.06;
    /** Slower than this, a cart on flat rails is standing still. */
    private static final double STILL = 0.001;

    /** The two ends of each shape of rail, as {x, y, z} steps from the rail's block: the server's table. */
    private static int[][] exits(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> new int[][] {{0, 0, -1}, {0, 0, 1}};
            case EAST_WEST -> new int[][] {{-1, 0, 0}, {1, 0, 0}};
            case ASCENDING_EAST -> new int[][] {{-1, -1, 0}, {1, 0, 0}};
            case ASCENDING_WEST -> new int[][] {{-1, 0, 0}, {1, -1, 0}};
            case ASCENDING_NORTH -> new int[][] {{0, 0, -1}, {0, -1, 1}};
            case ASCENDING_SOUTH -> new int[][] {{0, -1, -1}, {0, 0, 1}};
            case SOUTH_EAST -> new int[][] {{0, 0, 1}, {1, 0, 0}};
            case SOUTH_WEST -> new int[][] {{0, 0, 1}, {-1, 0, 0}};
            case NORTH_WEST -> new int[][] {{0, 0, -1}, {-1, 0, 0}};
            case NORTH_EAST -> new int[][] {{0, 0, -1}, {1, 0, 0}};
        };
    }

    private double vx, vz;
    /** Carrying a mob: it slows down far less, and moves a little less per tick. */
    private final boolean carrying;

    MinecartCoast(double vx, double vz, boolean carrying) {
        this.vx = vx;
        this.vz = vz;
        this.carrying = carrying;
    }

    /**
     * How a minecart was moving as it left: null if it was not, or is no minecart. Whether it
     * carried a mob is told apart: the game has its passengers get off before it says it left.
     */
    static @Nullable MinecartCoast of(Entity entity, boolean carrying) {
        if (!(entity instanceof AbstractMinecart)) return null;
        // What it moved during the last tick, as this client drew it.
        double vx = entity.getX() - entity.xo, vz = entity.getZ() - entity.zo;
        if (vx * vx + vz * vz < STILL * STILL) {
            vx = entity.getDeltaMovement().x;
            vz = entity.getDeltaMovement().z;
        }
        if (vx * vx + vz * vz < STILL * STILL) return null;
        return new MinecartCoast(vx, vz, carrying);
    }

    public double speed() {
        return Math.hypot(vx, vz);
    }

    /**
     * One server tick: moves the remembered cart along its rails. Returns false once it has
     * stopped for good; standing on blocks this client does not have, it waits.
     */
    boolean tick(Ground ground, MobMemory.Remembered cart) {
        double x = cart.x, y = cart.y, z = cart.z;
        int i = Mth.floor(x), j = Mth.floor(y), k = Mth.floor(z);
        if (rail(ground.at(i, j - 1, k))) j--;
        BlockState state = ground.at(i, j, k);
        if (state == null) return true;
        if (!(state.getBlock() instanceof BaseRailBlock rail)) return false;
        RailShape shape = state.getValue(rail.getShapeProperty());
        double[] before = railPoint(ground, x, y, z);

        boolean powered = false, braking = false;
        if (state.is(Blocks.POWERED_RAIL)) {
            powered = state.getValue(PoweredRailBlock.POWERED);
            braking = !powered;
        }
        double height = j;
        switch (shape) {
            case ASCENDING_EAST -> { vx -= SLOPE; height++; }
            case ASCENDING_WEST -> { vx += SLOPE; height++; }
            case ASCENDING_NORTH -> { vz += SLOPE; height++; }
            case ASCENDING_SOUTH -> { vz -= SLOPE; height++; }
            default -> { }
        }

        // Along the rail, whichever way it was going.
        int[][] ends = exits(shape);
        int[] a = ends[0], b = ends[1];
        double h = b[0] - a[0], n = b[2] - a[2];
        double length = Math.sqrt(h * h + n * n);
        if (vx * h + vz * n < 0) {
            h = -h;
            n = -n;
        }
        double speed = Math.min(2.0, Math.hypot(vx, vz));
        vx = speed * h / length;
        vz = speed * n / length;
        if (braking) {
            if (Math.hypot(vx, vz) < 0.03) {
                vx = vz = 0;
            } else {
                vx *= 0.5;
                vz *= 0.5;
            }
        }

        // Onto the rail's line, then one step.
        double ox = i + 0.5 + a[0] * 0.5, oz = k + 0.5 + a[2] * 0.5;
        double lx = (i + 0.5 + b[0] * 0.5) - ox, lz = (k + 0.5 + b[2] * 0.5) - oz;
        double along;
        if (lx == 0) {
            along = z - k;
        } else if (lz == 0) {
            along = x - i;
        } else {
            along = ((x - ox) * lx + (z - oz) * lz) * 2.0;
        }
        x = ox + lx * along;
        z = oz + lz * along;
        y = height;
        double share = carrying ? 0.75 : 1.0;
        x += Mth.clamp(share * vx, -MAX_SPEED, MAX_SPEED);
        z += Mth.clamp(share * vz, -MAX_SPEED, MAX_SPEED);
        if (a[1] != 0 && Mth.floor(x) - i == a[0] && Mth.floor(z) - k == a[2]) {
            y += a[1];
        } else if (b[1] != 0 && Mth.floor(x) - i == b[0] && Mth.floor(z) - k == b[2]) {
            y += b[1];
        }

        double friction = carrying ? 0.997 : 0.96;
        vx *= friction;
        vz *= friction;
        // Down a slope it gains what it loses in height, and the other way round.
        double[] after = railPoint(ground, x, y, z);
        if (after != null && before != null) {
            double gain = (before[1] - after[1]) * 0.05;
            double w = Math.hypot(vx, vz);
            if (w > 0) {
                vx *= (w + gain) / w;
                vz *= (w + gain) / w;
            }
            y = after[1];
        }
        int ni = Mth.floor(x), nk = Mth.floor(z);
        if (ni != i || nk != k) {
            double w = Math.hypot(vx, vz);
            vx = w * (ni - i);
            vz = w * (nk - k);
        }
        if (powered) {
            double w = Math.hypot(vx, vz);
            if (w > 0.01) {
                vx += vx / w * BOOST;
                vz += vz / w * BOOST;
            }
        }

        double dx = cart.x - x, dz = cart.z - z;
        if (dx * dx + dz * dz > 0.001) {
            // A cart looks the same both ways: it keeps the end it had in front.
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            if (Math.abs(Mth.wrapDegrees(yaw - cart.yaw)) > 90) yaw += 180;
            cart.yaw = Mth.wrapDegrees(yaw);
        }
        cart.x = x;
        cart.y = y;
        cart.z = z;
        // Standing on a slope, it rolls back down.
        return Math.hypot(vx, vz) > STILL || sloped(shape);
    }

    /** The server asks the rails tag, which holds the four rails, all of this class. */
    private static boolean rail(@Nullable BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    /** RailShape's own test was renamed in 1.21.2. */
    private static boolean sloped(RailShape shape) {
        return shape == RailShape.ASCENDING_EAST || shape == RailShape.ASCENDING_WEST
                || shape == RailShape.ASCENDING_NORTH || shape == RailShape.ASCENDING_SOUTH;
    }

    /** Where on its rail a cart at this place stands, {x, y, z}: null off the rails. The server's own sum. */
    private static double @Nullable [] railPoint(Ground ground, double x, double y, double z) {
        int i = Mth.floor(x), j = Mth.floor(y), k = Mth.floor(z);
        if (rail(ground.at(i, j - 1, k))) j--;
        BlockState state = ground.at(i, j, k);
        if (state == null || !(state.getBlock() instanceof BaseRailBlock rail)) return null;
        int[][] ends = exits(state.getValue(rail.getShapeProperty()));
        int[] a = ends[0], b = ends[1];
        double ax = i + 0.5 + a[0] * 0.5, ay = j + 0.0625 + a[1] * 0.5, az = k + 0.5 + a[2] * 0.5;
        double lx = (i + 0.5 + b[0] * 0.5) - ax, ly = ((j + 0.0625 + b[1] * 0.5) - ay) * 2.0, lz = (k + 0.5 + b[2] * 0.5) - az;
        double along;
        if (lx == 0) {
            along = z - k;
        } else if (lz == 0) {
            along = x - i;
        } else {
            along = ((x - ax) * lx + (z - az) * lz) * 2.0;
        }
        double py = ay + ly * along;
        if (ly < 0) {
            py++;
        } else if (ly > 0) {
            py += 0.5;
        }
        return new double[] {ax + lx * along, py, az + lz * along};
    }
}
