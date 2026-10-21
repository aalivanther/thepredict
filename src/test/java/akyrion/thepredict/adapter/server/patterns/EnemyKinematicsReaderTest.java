package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyKinematics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyKinematicsReaderTest {
    private static final double STRAFE = 0.15;

    /**
     * The stamp holds quarter-blocks, so the displacement the word can see is a quarter: a move inside the same
     * quarter reads nothing and a move of one quarter (0.25, above {@code strafeBlocks}) counts.
     */
    @Test
    void aMoveInsideAQuarterDoesNotCountAndAQuarterDoes() {
        var origin = reader(0.0, 0.0, 10.0, 0.0, true).read(0L, 2);
        assertEquals(0, EnemyKinematics.radial(reader(0.24, 0.0, 10.0, 0.0, true).read(origin, 2)));
        assertEquals(EnemyKinematics.LEFT_OR_CLOSING,
                EnemyKinematics.radial(reader(0.25, 0.0, 10.0, 0.0, true).read(origin, 2)));
    }

    /** SP-01: a still player reads no movement at a negative coordinate, a positive one, or across zero. */
    @Test
    void aStillPlayerReadsNoMovementAtAnyCoordinate() {
        for (var at : new double[][] {{-100.3, 40.0}, {10.2, 10.2}, {-0.1, 0.1}, {-262_000.5, 262_000.5}}) {
            var witnessX = at[0] + 3.0;
            var origin = reader(at[0], at[1], witnessX, at[1], true).read(0L, 2);
            var again = reader(at[0], at[1], witnessX, at[1], true).read(origin, 2);
            assertEquals(0, EnemyKinematics.lateral(again), "lateral at " + at[0] + ", " + at[1]);
            assertEquals(0, EnemyKinematics.radial(again), "radial at " + at[0] + ", " + at[1]);
            assertTrue(EnemyKinematics.valid(again));
        }
    }

    /** SP-01: one quarter towards a witness in the negative quadrant, and across zero, is a closing step. */
    @Test
    void aQuarterStepCountsInNegativeCoordinatesAndAcrossZero() {
        var origin = reader(-100.0, 40.0, -90.0, 40.0, true).read(0L, 2);
        var closing = reader(-99.75, 40.0, -90.0, 40.0, true).read(origin, 2);
        assertEquals(EnemyKinematics.LEFT_OR_CLOSING, EnemyKinematics.radial(closing));
        assertEquals(0, EnemyKinematics.lateral(closing));
        var before = reader(-0.25, 0.0, 10.0, 0.0, true).read(0L, 2);
        var crossed = reader(0.0, 0.0, 10.0, 0.0, true).read(before, 2);
        assertEquals(EnemyKinematics.LEFT_OR_CLOSING, EnemyKinematics.radial(crossed));
        var away = reader(-100.25, 40.0, -90.0, 40.0, true).read(origin, 2);
        assertEquals(EnemyKinematics.RIGHT_OR_AWAY, EnemyKinematics.radial(away));
    }

    /** SP-04: without a witness, or invisible, a moving player leaves no movement token; the stamp still advances. */
    @Test
    void movementNeedsAVisibleWitness() {
        var origin = reader(0.0, 0.0, 10.0, 0.0, true).read(0L, 2);
        var unseen = reader(0.0, 1.0, 0.0, 0.0, false).read(origin, 2);
        assertEquals(0, EnemyKinematics.lateral(unseen));
        assertEquals(0, EnemyKinematics.radial(unseen));
        assertEquals(KinematicStamp.zQuarters(reader(0.0, 1.0, 10.0, 0.0, true).read(origin, 2)),
                KinematicStamp.zQuarters(unseen));
        var invisible = new EnemyKinematicsReader(0.0, 1.0, 0f, 0.0, false, true, 10.0, 0.0, true, STRAFE)
                .read(origin, 2);
        assertEquals(0, EnemyKinematics.lateral(invisible));
        assertEquals(0, EnemyKinematics.radial(invisible));
        assertTrue(EnemyKinematics.invisible(invisible));
        var seen = reader(0.0, 1.0, 10.0, 0.0, true).read(origin, 2);
        assertEquals(EnemyKinematics.RIGHT_OR_AWAY, EnemyKinematics.lateral(seen));
    }

    @Test
    void aTeleportClearsMovementAndASprintWaitsElevenTicks() {
        var origin = reader(0.0, 0.0, 1.0, 0.0, true).read(0L, 2);
        var word = reader(10.0, 0.0, 11.0, 0.0, true).read(origin, 2);
        assertEquals(0, EnemyKinematics.lateral(word));
        assertEquals(0, EnemyKinematics.radial(word));
        assertTrue(KinematicStamp.teleported(origin, 10.0, 0.0, 2));
        assertFalse(EnemyKinematicsReader.sprintAgain(10, 0));
        assertTrue(EnemyKinematicsReader.sprintAgain(11, 0));
    }

    @Test
    void fallDistanceSetsAirborneAndReservedBitsStayClear() {
        var word = new EnemyKinematicsReader(1.0, 1.0, 90f, 0.2, true, false, 0.0, 0.0, true, STRAFE).read(0L, 2);
        assertTrue(EnemyKinematics.airborne(word));
        assertTrue(EnemyKinematics.sprinting(word));
        assertEquals(0L, word >>> 51);
    }

    @Test
    void theStampWrapsAtTwoToTheEighteenthBlocks() {
        var low = KinematicStamp.pack(0.0, 0.0, 0f, 0L, 0, 0, false, false, false, 2);
        var high = KinematicStamp.pack(1 << 18, 0.0, 0f, 0L, 0, 0, false, false, false, 2);
        assertEquals(KinematicStamp.xQuarters(low), KinematicStamp.xQuarters(high));
        assertEquals(1, KinematicStamp.deltaQuarters(0.25, KinematicStamp.xQuarters(high)));
        assertEquals(-1, KinematicStamp.deltaQuarters(-0.25, KinematicStamp.xQuarters(low)));
    }

    private static EnemyKinematicsReader reader(double x, double z, double witnessX, double witnessZ,
                                               boolean witness) {
        return new EnemyKinematicsReader(x, z, 0f, 0.0, false, false, witnessX, witnessZ, witness, STRAFE);
    }
}
