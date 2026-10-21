package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.SituationSignature;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bits 17 and 18 are written only while the zone mode is full (M24.7). */
class SituationSignatureV2Test {
    @Test
    void edgeAndRetreatBitsAreSetOnlyInFull() {
        var sessions = new EngineFixtures.Sessions();
        var session = sessions.open(1);
        session.zoneLimits(10, 4, 1, 1, 3, 10);
        session.cadence(akyrion.thepredict.domain.TickTier.HOT);
        session.zoneReading(ZoneMode.FULL, PushDanger.UNKNOWN, Feasibility.UNKNOWN);
        var bits = (1L << ZoneFact.EDGE_BEHIND.ordinal()) | (1L << ZoneFact.NO_SAFE_RETREAT.ordinal());
        session.noteZoneBits(bits);
        var facts = new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(),
                Set.of(), Map.of(), Set.of(), Set.of()).withZone(Set.of(), true, bits);
        session.facts(facts, 0L);
        var full = LiveSignature.of(session);
        assertTrue(SituationSignature.bit(full, 17));
        assertTrue(SituationSignature.bit(full, 18));
        session.zoneReading(ZoneMode.OBSERVE, PushDanger.UNKNOWN, Feasibility.UNKNOWN);
        session.facts(facts, 20L);
        var observe = LiveSignature.of(session);
        assertFalse(SituationSignature.bit(observe, 17));
        assertFalse(SituationSignature.bit(observe, 18));
        assertEquals(full & ((1 << 17) - 1), observe);
    }
}
