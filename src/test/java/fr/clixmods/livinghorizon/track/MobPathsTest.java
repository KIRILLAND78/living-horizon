package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cow at (10.5, 64, 20.5), on different grounds: the animation it gets never leaves its ground. */
class MobPathsTest {
    private static final double X = 10.5, Y = 64, Z = 20.5, WIDTH = 0.9;
    private static final int BX = 10, BZ = 20, R = MobPaths.RADIUS, N = MobPaths.SIZE;

    @BeforeAll
    static void boot() {
        GameBoot.start();
        STONE = Blocks.STONE.defaultBlockState();
        DIRT = Blocks.DIRT.defaultBlockState();
        GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static double[][] ground(double fill) {
        double[][] ground = new double[N][N];
        for (double[] row : ground) Arrays.fill(row, fill);
        return ground;
    }

    private static MobPaths.Choice choose(double[][] ground, double width, long seed) {
        return MobPaths.choose(new MobPaths.Ground(ground, X, Y, Z), width, seed);
    }

    private static double widest() {
        return MobAnimations.ALL.stream().mapToDouble(a -> a.extent).max().orElseThrow();
    }

    /** Every tick of the animation it plays: where it stands, as wide as it, is ground it can step on. */
    private static void staysOnGround(MobPaths.Choice choice, double width) {
        MobAnimations.Animation animation = choice.animation();
        if (animation == null) return;
        double last = Y;
        for (int t = 0; t < animation.duration; t++) {
            MobAnimations.Pose pose = animation.sample(t);
            double x = X + MobAnimations.worldX(pose.x(), pose.z(), choice.angle(), choice.mirror());
            double z = Z + MobAnimations.worldZ(pose.x(), pose.z(), choice.angle(), choice.mirror());
            double h = MobPaths.walkable(choice.ground(), x, z, width);
            assertFalse(Double.isNaN(h), animation + " leaves the ground at tick " + t);
            assertTrue(Math.abs(h - last) <= MobPaths.STEP, animation + " climbs too high at tick " + t);
            last = h;
        }
    }

    @Test
    void everyAnimationLoopsWithoutAJump() {
        for (MobAnimations.Animation animation : MobAnimations.ALL) {
            MobAnimations.Pose end = animation.sample(animation.duration - 1);
            assertEquals(0, end.x(), 1e-9, animation.toString());
            assertEquals(0, end.z(), 1e-9, animation.toString());
            MobAnimations.Pose before = end;
            for (int t = 0; t <= animation.duration; t++) {
                MobAnimations.Pose now = animation.sample(t);
                assertTrue(Math.hypot(now.x() - before.x(), now.z() - before.z()) <= MobAnimations.WALK + 1e-9,
                        animation + " jumps at tick " + t);
                before = now;
            }
            assertTrue(animation.extent < R - 1, animation + " goes past the ground read");
        }
    }

    @Test
    void openGroundGetsAWideAnimation() {
        for (long seed = 0; seed < 16; seed++) {
            MobPaths.Choice choice = choose(ground(64), WIDTH, seed);
            assertNotNull(choice.animation());
            assertTrue(choice.animation().extent >= widest() * 0.6, choice.animation().toString());
            staysOnGround(choice, WIDTH);
        }
    }

    @Test
    void cliffEdgeKeepsItOnTheTop() {
        double[][] ground = ground(64);
        // Everything east of the cow's own block drops by four blocks.
        for (int i = R + 1; i < N; i++) Arrays.fill(ground[i], 60);
        for (long seed = 0; seed < 16; seed++) {
            MobPaths.Choice choice = choose(ground, WIDTH, seed);
            assertNotNull(choice.animation(), "room enough to move on the cliff top");
            staysOnGround(choice, WIDTH);
        }
    }

    @Test
    void hillOfOneBlockStepsIsWalked() {
        double[][] ground = ground(64);
        // A hill: one block higher every two blocks east.
        for (int i = R + 1; i < N; i++) Arrays.fill(ground[i], 64 + (i - R + 1) / 2);
        MobPaths.Choice choice = choose(ground, WIDTH, 3);
        assertNotNull(choice.animation());
        assertTrue(choice.animation().extent >= widest() * 0.6);
        staysOnGround(choice, WIDTH);
    }

    @Test
    void wallAndFenceAreNeverCrossed() {
        for (double obstacle : new double[]{67, 65.5}) {
            double[][] ground = ground(64);
            // A wall three blocks high, or a fence, two blocks north of the cow, and open ground past it.
            for (int i = 0; i < N; i++) ground[i][R + 2] = obstacle;
            for (long seed = 0; seed < 16; seed++) {
                MobPaths.Choice choice = choose(ground, WIDTH, seed);
                staysOnGround(choice, WIDTH);
            }
        }
    }

    @Test
    void narrowLedgeGetsAStraightWalk() {
        double[][] ground = ground(Double.NaN);
        // One block wide, running north-south through the cow.
        Arrays.fill(ground[R], 64);
        MobPaths.Choice choice = choose(ground, WIDTH, 3);
        assertNotNull(choice.animation());
        for (MobAnimations.Step step : choice.animation().steps) assertEquals(0, step.x(), 1e-9);
        staysOnGround(choice, WIDTH);
    }

    @Test
    void pillarLeavesItStanding() {
        double[][] ground = ground(Double.NaN);
        ground[R][R] = 64;
        assertNull(choose(ground, WIDTH, 5).animation());
        assertNull(MobPaths.stand(X, Y, Z).animation());
    }

    @Test
    void crampedPlayerPacesWhereNothingElseFits() {
        // A player who logged off in a 1x2 nook: no mob animation fits, a step there and back does.
        double[][] ground = ground(Double.NaN);
        ground[R][R] = 64;
        ground[R][R + 1] = 64;
        MobPaths.Choice none = choose(ground, 0.6, 9);
        assertNull(none.animation());
        MobPaths.Choice cramped = MobPaths.orCramped(none, 0.6, 9);
        assertNotNull(cramped.animation());
        assertTrue(MobAnimations.CRAMPED.contains(cramped.animation()));
        staysOnGround(cramped, 0.6);
        // Where a mob animation fits, it is kept.
        MobPaths.Choice open = choose(ground(64), 0.6, 9);
        assertEquals(open.animation(), MobPaths.orCramped(open, 0.6, 9).animation());
    }

    @Test
    void neverPastItsRoam() {
        // Open ground all round, but its spot 3 blocks to the west and a roam of 4: nothing of the
        // animation it plays goes more than 4 blocks from that spot.
        double cx = X - 3, cz = Z, roam = 4;
        MobPaths.Choice open = choose(ground(64), 0.6, 9);
        MobPaths.Choice kept = MobPaths.within(open, cx, cz, roam, 0.6, 9);
        MobAnimations.Animation animation = kept.animation();
        assertNotNull(animation);
        for (int t = 0; t < animation.duration; t++) {
            MobAnimations.Pose pose = animation.sample(t);
            double x = X + MobAnimations.worldX(pose.x(), pose.z(), kept.angle(), kept.mirror());
            double z = Z + MobAnimations.worldZ(pose.x(), pose.z(), kept.angle(), kept.mirror());
            assertTrue(Math.hypot(x - cx, z - cz) <= roam + 1, animation + " goes " + Math.hypot(x - cx, z - cz) + " blocks from its spot");
        }
        staysOnGround(kept, 0.6);
        // Well inside its roam, nothing changes.
        assertSame(open, MobPaths.within(open, X, Z, 50, 0.6, 9));
    }

    @Test
    void groundNotReadYetIsReadAgain() {
        // The far world not open yet: nothing read around the cow, which stands for now.
        assertFalse(MobPaths.stand(X, Y, Z).complete());
        double[][] ground = ground(Double.NaN);
        ground[R][R] = 64;
        MobPaths.Choice partial = MobPaths.choose(new MobPaths.Ground(ground, X, Y, Z, false), WIDTH, 5);
        assertNull(partial.animation());
        assertFalse(partial.complete());
        // Read again once it is there: the cow walks.
        MobPaths.Choice read = choose(ground(64), WIDTH, 5);
        assertTrue(read.complete());
        assertNotNull(read.animation());
        assertFalse(read.same(partial));
        assertTrue(read.same(choose(ground(64), WIDTH, 5)));
        assertTrue(MobPaths.free(X, Y, Z, 9).complete());
    }

    @Test
    void savedGroundComesBackTheSame() {
        double[][] ground = ground(64);
        ground[R + 2][R] = 64.5;
        ground[R][R + 3] = 64.9375;
        ground[R - 1][R - 1] = 63;
        ground[0][0] = Double.NaN;
        MobPaths.Ground read = new MobPaths.Ground(ground, X, Y, Z);
        MobPaths.Ground saved = MobPaths.Ground.load(read.save());
        assertNotNull(saved);
        assertTrue(saved.complete);
        assertTrue(saved.around(X, Z));
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double x = BX + i - R + 0.5, z = BZ + j - R + 0.5;
                assertEquals(read.height(x, z), saved.height(x, z), 1e-9);
            }
        }
        // The same ground, the same walk.
        assertTrue(MobPaths.choose(read, WIDTH, 5).same(MobPaths.choose(saved, WIDTH, 5)));
        assertNull(MobPaths.Ground.load("not a ground"));
        assertNull(MobPaths.Ground.load("1 2 3 AAAA"));
    }

    @Test
    void flyersStayAFewBlocksAround() {
        MobPaths.Choice choice = MobPaths.free(X, Y, Z, 9);
        assertNotNull(choice.animation());
        assertTrue(choice.animation().extent <= 3);
    }

    // --- Reading a column ---------------------------------------------------------------

    /** A column from {@code Y - BELOW} up: the blocks given, then air. */
    private static BlockState[] column(BlockState... blocks) {
        BlockState[] column = new BlockState[MobPaths.BELOW + MobPaths.ABOVE + 1];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        System.arraycopy(blocks, 0, column, 0, blocks.length);
        return column;
    }

    private static final int BOTTOM = (int) Y - MobPaths.BELOW;
    /** Set once the game is booted: before, its blocks do not exist. */
    private static BlockState STONE, DIRT, GRASS;

    @Test
    void grassFlowersAndSnowAreAir() {
        for (BlockState plant : new BlockState[]{Blocks.SHORT_GRASS.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState(), Blocks.SNOW.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState()}) {
            assertEquals(64, MobPaths.floor(column(STONE, STONE, DIRT, GRASS, plant), BOTTOM, Y), 1e-9, plant.toString());
        }
    }

    @Test
    void dugPathIsGround() {
        double path = MobPaths.floor(column(STONE, STONE, DIRT, Blocks.DIRT_PATH.defaultBlockState()), BOTTOM, Y);
        assertEquals(64, path, 0.1);
        assertTrue(Math.abs(path - 64) <= MobPaths.STEP);
    }

    @Test
    void waterLeavesAndFencesAreNotGround() {
        assertTrue(Double.isNaN(MobPaths.floor(column(STONE, STONE, DIRT, Blocks.WATER.defaultBlockState()), BOTTOM, Y)));
        assertTrue(Double.isNaN(MobPaths.floor(column(STONE, STONE, DIRT, Blocks.OAK_LEAVES.defaultBlockState()), BOTTOM, Y)));
        double fence = MobPaths.floor(column(STONE, STONE, DIRT, GRASS, Blocks.OAK_FENCE.defaultBlockState()), BOTTOM, Y);
        assertEquals(65.5, fence, 1e-9);
        assertFalse(Math.abs(fence - 64) <= MobPaths.STEP);
    }

    @Test
    void floorUnderARoofIsTheOneItStandsOn() {
        BlockState air = Blocks.AIR.defaultBlockState(), planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState[] barn = column(STONE, STONE, DIRT, planks, air, air, air, planks);
        assertEquals(64, MobPaths.floor(barn, BOTTOM, Y), 1e-9);
        // A roof too low to stand under: the roof is the only floor.
        BlockState[] low = column(STONE, STONE, DIRT, planks, air, planks);
        assertEquals(66, MobPaths.floor(low, BOTTOM, Y), 1e-9);
    }
}
