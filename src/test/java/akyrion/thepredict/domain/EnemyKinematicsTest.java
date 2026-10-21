package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyKinematicsTest {
    @Test
    void readersSeeOnlyThePublishedFields() {
        var word = (1L << EnemyKinematics.VALID) | (1L << EnemyKinematics.LATERAL_SHIFT)
                | (2L << EnemyKinematics.RADIAL_SHIFT) | (1L << EnemyKinematics.AIRBORNE)
                | (1L << EnemyKinematics.SPRINTING) | (1L << EnemyKinematics.INVISIBLE);
        assertTrue(EnemyKinematics.valid(word));
        assertEquals(EnemyKinematics.LEFT_OR_CLOSING, EnemyKinematics.lateral(word));
        assertEquals(EnemyKinematics.RIGHT_OR_AWAY, EnemyKinematics.radial(word));
        assertTrue(EnemyKinematics.airborne(word));
        assertTrue(EnemyKinematics.sprinting(word));
        assertTrue(EnemyKinematics.invisible(word));
        assertFalse(EnemyKinematics.valid(0L));
    }
}
