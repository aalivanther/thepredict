package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Feasibility;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.PushDanger;
import akyrion.thepredict.domain.TickTier;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Raw zone facts are sampled once per fact period, not on every capture (M24.7). */
class ZoneFactPeriodTest {
    @Test
    void capturesInsideOnePeriodDoNotAdvanceTheFilter() {
        var sessions = new EngineFixtures.Sessions();
        var session = sessions.open(2);
        session.zoneLimits(10, 4, 2, 2, 3, 10);
        session.cadence(TickTier.HOT);
        session.zoneReading(ZoneMode.FULL, PushDanger.UNKNOWN, Feasibility.UNKNOWN);
        var bits = 1L << ZoneFact.EDGE_BEHIND.ordinal();
        session.noteZoneBits(bits);
        var facts = new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), Set.of(), Set.of(),
                Set.of(), Map.of(), Set.of(), Set.of());
        session.facts(facts, 0L);
        session.facts(facts, 5L);
        session.facts(facts, 9L);
        assertFalse(session.lastMobFacts().orElseThrow().zoneFacts().contains(ZoneFact.EDGE_BEHIND));
        session.facts(facts, 10L);
        assertTrue(session.lastMobFacts().orElseThrow().zoneFacts().contains(ZoneFact.EDGE_BEHIND));
    }
}
