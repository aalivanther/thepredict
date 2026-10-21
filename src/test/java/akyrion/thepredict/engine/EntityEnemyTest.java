package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityEnemyTest {
    @Test
    void aSkeletonThatShootsAndKeepsRangeIsAKite() {
        assertTrue(PatternRules.kitesSkeleton(true, true, true));
        assertEquals(EnemyPattern.KITES_AT_RANGE, EnemyPattern.valueOf("KITES_AT_RANGE"));
        assertFalse(PatternRules.kitesSkeleton(false, true, true));
        assertFalse(PatternRules.kitesSkeleton(true, true, false));
        assertFalse(PatternRules.kitesSkeleton(true, false, true));
    }
}
