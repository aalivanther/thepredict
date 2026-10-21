package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetRelationTest {
    @Test
    void relationKeepsBandsAndRejectsMissingParts() {
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STRAFING,
                true, false, true, false, false);
        assertEquals(DistanceBand.RANGED, relation.distance());
        assertThrows(NullPointerException.class, () -> new TargetRelation(new ParticipantAlias(1), null,
                Trend.STEADY, true, true, true, true, false));
    }

    @Test
    void groupedIsKeptAndTheGroupRadiusIsFourBlocks() {
        var relation = new TargetRelation(new ParticipantAlias(2), DistanceBand.MELEE, Trend.STEADY, true, true, false,
                false, true);
        assertTrue(relation.grouped());
        assertEquals(4.0, TargetRelation.GROUP_RADIUS_BLOCKS);
    }
}
