package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomingProjectilesTest {
    @Test
    void aShotInsideTheConeAndTheFlightCounts() {
        var shots = new IncomingProjectiles();
        var shooter = UUID.randomUUID();
        shots.launched(shooter, 10, 90f, 0);
        assertFalse(shots.recent(shooter, 10, 4, 90));
        shots.launched(shooter, 10, 90f, 2);
        assertTrue(shots.recent(shooter, 11, 4, 90));
        assertFalse(shots.recent(shooter, 11, 4, 120));
        assertFalse(shots.recent(shooter, 40, 4, 90));
    }

    /**
     * SP-19: the walk stops at the first shot too old for the slowest stored speed, and answers as the walk of the
     * whole ring would, also after the ring wrapped and evicted the slowest shot.
     */
    @Test
    void theCutByAgeAnswersAsTheWalkOfTheWholeRing() {
        var shots = new IncomingProjectiles();
        var shooters = new UUID[] {new UUID(7, 1), new UUID(7, 2), new UUID(7, 3)};
        var seed = 12345L;
        var launches = new ArrayList<Launch>();
        long tick = 0;
        var checked = 0;
        for (var step = 0; step < 900; step++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            var draw = (int) (seed >>> 33);
            tick += Math.floorMod(draw, 3);
            var shooter = shooters[Math.floorMod(draw >> 4, shooters.length)];
            var yaw = (float) Math.floorMod(draw >> 8, 360);
            var speed = 0.2 + Math.floorMod(draw >> 16, 30) / 10.0;
            shots.launched(shooter, tick, yaw, speed);
            launches.add(new Launch(shooter, tick, yaw, speed));
            for (var query = 0; query < 3; query++) {
                var who = shooters[query];
                var distance = 2.0 + Math.floorMod(draw >> (query + 2), 40);
                var toward = (double) Math.floorMod(draw >> (query + 5), 360);
                var now = tick + Math.floorMod(draw >> (query + 9), 60);
                assertEquals(reference(launches, who, now, distance, toward), shots.recent(who, now, distance, toward),
                        "step " + step + " query " + query);
                checked++;
            }
        }
        assertEquals(2700, checked);
    }

    private static boolean reference(List<Launch> launches, UUID shooter, long now, double distance, double toward) {
        var from = Math.max(0, launches.size() - IncomingProjectiles.CAPACITY);
        for (var index = launches.size() - 1; index >= from; index--) {
            var launch = launches.get(index);
            if (!launch.shooter().equals(shooter)) {
                continue;
            }
            var flight = (long) Math.ceil(distance / launch.speed()) + 2L;
            var age = now - launch.tick();
            if (age < 0 || age > flight) {
                continue;
            }
            var delta = StrictMath.IEEEremainder(launch.yaw() - toward, 360.0);
            if (delta <= IncomingProjectiles.CONE_DEGREES && delta >= -IncomingProjectiles.CONE_DEGREES) {
                return true;
            }
        }
        return false;
    }

    private record Launch(UUID shooter, long tick, float yaw, double speed) {
    }
}
