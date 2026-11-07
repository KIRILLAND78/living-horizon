package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A remembered minecart rolling on, as the server would roll it. */
class MinecartCoastTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    /** Rails laid on a flat world at y 64; everything else is air. */
    private static final class Track implements MinecartCoast.Ground {
        final Map<Long, BlockState> blocks = new HashMap<>();

        Track rail(int x, int z, BlockState state) {
            blocks.put(BlockPos.asLong(x, 64, z), state);
            return this;
        }

        Track line(int fromX, int toX, int z, BlockState state) {
            for (int x = fromX; x <= toX; x++) rail(x, z, state);
            return this;
        }

        @Override
        public BlockState at(int x, int y, int z) {
            return blocks.getOrDefault(BlockPos.asLong(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockState rail(RailShape shape) {
        return Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
    }

    private static BlockState powered(boolean on) {
        return Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).setValue(PoweredRailBlock.POWERED, on);
    }

    private static MobMemory.Remembered cartAt(double x, double z) {
        MobMemory.Remembered cart = new MobMemory.Remembered();
        cart.x = x;
        cart.y = 64.0625;
        cart.z = z;
        return cart;
    }

    /** Ticks until it stops, at most this many ticks; returns how many it rolled. */
    private static int roll(MinecartCoast coast, MinecartCoast.Ground ground, MobMemory.Remembered cart, int most) {
        for (int tick = 1; tick <= most; tick++) {
            if (!coast.tick(ground, cart)) return tick;
        }
        return most;
    }

    @Test
    void anEmptyCartStopsAfterAFewBlocks() {
        Track track = new Track().line(0, 100, 0, rail(RailShape.EAST_WEST));
        MobMemory.Remembered cart = cartAt(10.5, 0.5);
        int ticks = roll(new MinecartCoast(0.4, 0, false), track, cart, 1000);
        assertTrue(ticks < 1000, "still rolling");
        // 0.4 blocks a tick, losing 4% a tick: about ten blocks.
        assertTrue(cart.x > 18 && cart.x < 22, "stopped at " + cart.x);
        assertEquals(0.5, cart.z, 1e-6);
        assertEquals(64.0625, cart.y, 1e-6);
    }

    @Test
    void poweredRailsKeepItGoing() {
        Track track = new Track().line(0, 400, 0, powered(true));
        MobMemory.Remembered cart = cartAt(10.5, 0.5);
        MinecartCoast coast = new MinecartCoast(0.1, 0, false);
        assertEquals(200, roll(coast, track, cart, 200));
        // At 8 blocks a second, the most the server lets it go.
        assertTrue(cart.x > 70, "only reached " + cart.x);
    }

    @Test
    void anUnpoweredPoweredRailBrakes() {
        Track track = new Track().line(0, 10, 0, rail(RailShape.EAST_WEST)).line(11, 100, 0, powered(false));
        MobMemory.Remembered cart = cartAt(5.5, 0.5);
        roll(new MinecartCoast(0.4, 0, false), track, cart, 1000);
        assertTrue(cart.x < 14, "braked only at " + cart.x);
    }

    @Test
    void itFollowsACurve() {
        // East along z 0, then a curve at x 20 down to the south.
        Track track = new Track().line(0, 19, 0, powered(true)).rail(20, 0, rail(RailShape.SOUTH_WEST));
        for (int z = 1; z <= 40; z++) track.rail(20, z, rail(RailShape.NORTH_SOUTH));
        MobMemory.Remembered cart = cartAt(5.5, 0.5);
        roll(new MinecartCoast(0.4, 0, false), track, cart, 400);
        assertEquals(20.5, cart.x, 1e-6);
        assertTrue(cart.z > 3, "did not turn: " + cart.z);
    }

    @Test
    void itWaitsWhereNothingIsLoaded() {
        MobMemory.Remembered cart = cartAt(5.5, 0.5);
        MinecartCoast coast = new MinecartCoast(0.4, 0, false);
        assertTrue(coast.tick((x, y, z) -> null, cart));
        assertEquals(5.5, cart.x, 1e-9);
        assertEquals(0.4, coast.speed(), 1e-9);
    }

    @Test
    void offItsRailsItStops() {
        MobMemory.Remembered cart = cartAt(5.5, 0.5);
        assertFalse(new MinecartCoast(0.4, 0, false).tick(new Track(), cart));
    }
}
