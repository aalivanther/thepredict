package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituationChangedTest {
    @Test
    void onlyALiveVetoOrAnExhaustedSkillDiscards() {
        assertEquals(DiscardReason.SITUATION_CHANGED, SituationRules.veto(true, HealthBand.HEALTHY, false,
                ReadyIn.in(0)).orElseThrow());
        assertEquals(DiscardReason.SITUATION_CHANGED, SituationRules.veto(false, HealthBand.WOUNDED, true,
                ReadyIn.UNKNOWN).orElseThrow());
        assertEquals(DiscardReason.SITUATION_CHANGED, SituationRules.veto(false, HealthBand.WOUNDED, false,
                ReadyIn.EXHAUSTED).orElseThrow());
        assertTrue(SituationRules.veto(false, HealthBand.HEALTHY, false, ReadyIn.in(60)).isEmpty());
        assertTrue(SituationRules.veto(false, HealthBand.HEALTHY, false, ReadyIn.UNKNOWN).isEmpty());
        assertTrue(SituationRules.veto(true, HealthBand.WOUNDED, false, ReadyIn.in(0)).isEmpty());
    }
}
