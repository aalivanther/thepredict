package akyrion.thepredict.domain.learn;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.PatternGroup;
import akyrion.thepredict.domain.ZoneFact;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextKeyTest {
    @Test
    void cardinalityAndTextStayInsideTheContract() {
        assertEquals(6048, ContextKey.CARDINALITY);
        var key = ContextKey.of(ContextKey.distanceCode(DistanceBand.MELEE), HealthBand3.HIGH, HealthBand3.LOW,
                false, ZoneBand.OPEN, GroupBand.SOLO, 0);
        assertEquals("v2:MELEE:HIGH:LOW:MOB:OPEN:SOLO:NONE", key.text());
        assertEquals("v2c:MELEE:MOB:NONE", key.coarse());
        assertTrue(key.text().length() <= ContextKey.MAX_CHARS);
        assertEquals(0, ContextKey.patternCode(PatternGroup.RHYTHM, false));
        assertEquals(PatternGroup.RHYTHM.ordinal() + 1, ContextKey.patternCode(PatternGroup.RHYTHM, true));
        assertEquals(HealthBand3.HIGH, HealthBand3.of(0.6));
        assertEquals(HealthBand3.LOW, HealthBand3.of(0.3));
        assertEquals(HealthBand3.MID, HealthBand3.of(0.4));
        assertEquals(GroupBand.SOLO, GroupBand.of(1));
        assertEquals(GroupBand.SMALL, GroupBand.of(4));
        assertEquals(GroupBand.LARGE, GroupBand.of(5));
        assertThrows(IllegalArgumentException.class, () -> new ContextKey(0, "near", "v2c:MELEE:MOB:NONE"));
    }

    @Test
    void publicSignaturesRefuseNullInsteadOfHidingIt() {
        assertThrows(NullPointerException.class, () -> ContextKey.patternCode(null, true));
        assertThrows(NullPointerException.class, () -> new CaptureDecision.Outcome(false, java.util.Optional.empty(),
                null, false));
    }

    @Test
    void theZoneBandFollowsTheFilteredFactsOnlyInFull() {
        assertEquals(ZoneBand.NA, ZoneBand.of(false, Set.of(ZoneFact.HAZARDOUS_ARENA)));
        assertEquals(ZoneBand.OPEN, ZoneBand.of(true, Set.of()));
        assertEquals(ZoneBand.OPEN, ZoneBand.of(true, Set.of(ZoneFact.COVER_NEAR)));
        assertEquals(ZoneBand.ENCLOSED, ZoneBand.of(true, Set.of(ZoneFact.ENCLOSED)));
        assertEquals(ZoneBand.HAZARDOUS, ZoneBand.of(true, Set.of(ZoneFact.ENCLOSED, ZoneFact.EDGE_BEHIND)));
        assertEquals(ZoneBand.HAZARDOUS, ZoneBand.of(true, Set.of(ZoneFact.NO_SAFE_RETREAT)));
        assertEquals(ZoneBand.HAZARDOUS, ZoneBand.of(true, Set.of(ZoneFact.HAZARDOUS_ARENA)));
    }

    @Test
    void theTargetHealthAndKindReachTheKeyThroughTheSameFold() {
        assertEquals(HealthBand3.LOW, HealthBand3.of(HealthBand.CRITICAL));
        assertEquals(HealthBand3.MID, HealthBand3.of(HealthBand.WOUNDED));
        assertEquals(HealthBand3.HIGH, HealthBand3.of(HealthBand.HEALTHY));
        var key = ContextKey.of(ContextKey.distanceCode(DistanceBand.MELEE), HealthBand3.of(HealthBand.HEALTHY),
                HealthBand3.of(HealthBand.CRITICAL), true, ZoneBand.of(true, Set.of()), GroupBand.of(1), 0);
        assertEquals("v2:MELEE:HIGH:LOW:PLAYER:OPEN:SOLO:NONE", key.text());
    }
}
