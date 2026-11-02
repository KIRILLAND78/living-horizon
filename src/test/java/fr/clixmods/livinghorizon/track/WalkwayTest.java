package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player who logged off walks only where a player could. */
class WalkwayTest {
    /** Full blocks; a player stands on top of one with room for two above. */
    private static final class Blocks implements Walkway.Ground {
        final Set<Long> solid = new HashSet<>();

        Blocks floor(int y, int from, int to) {
            for (int x = from; x <= to; x++) for (int z = from; z <= to; z++) solid.add(Walkway.key(x, y, z));
            return this;
        }

        Blocks block(int x, int y, int z) {
            solid.add(Walkway.key(x, y, z));
            return this;
        }

        boolean at(int x, int y, int z) {
            return solid.contains(Walkway.key(x, y, z));
        }

        @Override
        public double stand(int x, int y, int z) {
            return at(x, y - 1, z) && !at(x, y, z) && !at(x, y + 1, z) ? y : Double.NaN;
        }
    }

    @Test
    void walksAcrossAFloor() {
        Blocks ground = new Blocks().floor(0, -10, 10);
        Walkway.Survey map = Walkway.around(ground, 0, 1, 0, 0, 0, 5, 1000);
        assertNotNull(map);
        Walkway.Step far = map.reached(4, 1, 3);
        assertNotNull(far);
        List<Walkway.Step> path = map.pathTo(far);
        // Diagonals first: four steps, never more than one block at a time.
        assertEquals(4, path.size());
        assertEquals(far, path.get(path.size() - 1));
    }

    @Test
    void keepsWithinItsRadius() {
        Walkway.Survey map = Walkway.around(new Blocks().floor(0, -20, 20), 0, 1, 0, 0, 0, 3, 1000);
        assertNotNull(map);
        assertNull(map.reached(5, 1, 0));
        assertTrue(map.spots().stream().allMatch(s -> s.x() * s.x() + s.z() * s.z() <= 3.5 * 3.5));
    }

    @Test
    void goesRoundAWallNotThroughIt() {
        Blocks ground = new Blocks().floor(0, -6, 6);
        for (int z = -6; z <= 3; z++) ground.block(2, 1, z).block(2, 2, z);
        Walkway.Survey map = Walkway.around(ground, 0, 1, 0, 0, 0, 6, 2000);
        assertNotNull(map);
        Walkway.Step behind = map.reached(4, 1, 0);
        assertNotNull(behind);
        for (Walkway.Step step : map.pathTo(behind)) assertTrue(step.x() != 2 || step.z() > 3, "through the wall at " + step);
        assertTrue(map.pathTo(behind).size() > 4);
    }

    @Test
    void climbsOneBlockNotTwo() {
        Blocks ground = new Blocks().floor(0, -6, 6);
        // A step of one block east, a cliff of two blocks west.
        for (int z = -6; z <= 6; z++) {
            for (int x = 3; x <= 6; x++) ground.block(x, 1, z);
            for (int x = -6; x <= -3; x++) ground.block(x, 1, z).block(x, 2, z);
        }
        Walkway.Survey map = Walkway.around(ground, 0, 1, 0, 0, 0, 6, 2000);
        assertNotNull(map);
        assertNotNull(map.reached(4, 2, 0));
        assertNull(map.reached(-4, 3, 0));
    }

    @Test
    void dropsTwoBlocksNotThree() {
        Blocks ground = new Blocks().floor(0, -6, 6).floor(-3, -6, 6);
        for (int x = 2; x <= 6; x++) for (int z = -6; z <= 6; z++) ground.solid.remove(Walkway.key(x, 0, z));
        Blocks shallow = new Blocks().floor(0, -6, 6).floor(-2, -6, 6);
        for (int x = 2; x <= 6; x++) for (int z = -6; z <= 6; z++) shallow.solid.remove(Walkway.key(x, 0, z));
        assertNull(Walkway.around(ground, 0, 1, 0, 0, 0, 6, 2000).reached(3, -2, 0));
        assertNotNull(Walkway.around(shallow, 0, 1, 0, 0, 0, 6, 2000).reached(3, -1, 0));
    }

    @Test
    void cutsNoCorner() {
        Blocks ground = new Blocks().floor(0, -3, 3).block(1, 1, 0).block(1, 2, 0);
        Walkway.Survey map = Walkway.around(ground, 0, 1, 0, 0, 0, 3, 100);
        assertNotNull(map);
        Walkway.Step diagonal = map.reached(1, 1, 1);
        assertNotNull(diagonal);
        // Not straight across the corner of the pillar: by way of (0, 1).
        assertEquals(2, map.pathTo(diagonal).size());
    }

    @Test
    void nowhereToStand() {
        assertNull(Walkway.around(new Blocks(), 0, 1, 0, 0, 0, 3, 100));
    }

    @Test
    void findsAWayAtOnce() {
        Blocks ground = new Blocks().floor(0, -6, 6);
        for (int z = -6; z <= 3; z++) ground.block(2, 1, z).block(2, 2, z);
        List<Walkway.Step> way = Walkway.way(ground, 0, 1, 0, new Walkway.Step(4, 1, 0, 1), 0, 0, 6, 2000);
        assertNotNull(way);
        assertEquals(new Walkway.Step(4, 1, 0, 1), way.get(way.size() - 1));
        for (Walkway.Step step : way) assertTrue(step.x() != 2 || step.z() > 3, "through the wall at " + step);
    }

    @Test
    void noWayOutOfAPit() {
        Blocks ground = new Blocks().floor(0, -6, 6);
        // Walls three blocks high all round the start: too high to climb.
        for (int d = -1; d <= 1; d++) {
            for (int y = 1; y <= 3; y++) ground.block(d, y, -2).block(d, y, 2).block(-2, y, d).block(2, y, d);
        }
        for (int y = 1; y <= 3; y++) ground.block(-2, y, -2).block(-2, y, 2).block(2, y, -2).block(2, y, 2);
        assertNull(Walkway.way(ground, 0, 1, 0, new Walkway.Step(5, 1, 0, 1), 0, 0, 6, 2000));
        assertNull(Walkway.way(new Blocks(), 0, 1, 0, new Walkway.Step(5, 1, 0, 1), 0, 0, 6, 2000));
    }

    @Test
    void readsEachSpotOnce() {
        Blocks floor = new Blocks().floor(0, -12, 12);
        Map<Long, Integer> reads = new HashMap<>();
        Walkway.Ground counted = (x, y, z) -> {
            reads.merge(Walkway.key(x, y, z), 1, Integer::sum);
            return floor.stand(x, y, z);
        };
        Walkway.Survey map = Walkway.around(counted, 0, 1, 0, 0, 0, 10, 5000);
        assertNotNull(map);
        assertTrue(map.spots().size() > 300);
        assertTrue(reads.values().stream().allMatch(n -> n == 1), "a spot read twice");
    }

    @Test
    void spotsComeNearestFirst() {
        Walkway.Survey map = Walkway.around(new Blocks().floor(0, -10, 10), 0, 1, 0, 0, 0, 8, 5000);
        assertNotNull(map);
        List<Walkway.Step> spots = map.spots();
        assertEquals(new Walkway.Step(0, 1, 0, 1), spots.get(0));
        // On flat ground the cost is the distance walked: straight steps, and diagonals.
        double last = 0;
        for (Walkway.Step spot : spots) {
            int dx = Math.abs(spot.x()), dz = Math.abs(spot.z());
            double walked = Math.abs(dx - dz) + Math.min(dx, dz) * 1.414;
            assertTrue(walked >= last - 1e-9, spot + " after a spot farther away");
            last = walked;
        }
    }
}
