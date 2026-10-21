package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparison of M11.6 without and with enriched threat, over the model table of {@link ThreatAB}: the arm with
 * zero weights never reaches the port, the cap per window holds with the example weights, and the unreachable decay
 * hands the lead to the reachable player only when it is on. The measured leader changes are in the M13 report.
 */
class ThreatEnrichmentABTest {
    @ParameterizedTest
    @EnumSource(ThreatAB.Scenario.class)
    void theArmWithoutEnrichedThreatNeverWritesToTheTable(ThreatAB.Scenario scenario) {
        var result = ThreatAB.run(scenario, ThreatAB.OFF);
        assertEquals(0, result.portCalls());
        assertEquals(0.0, result.pointsAdded());
        assertEquals(0.0, result.pointsRemoved());
    }

    @Test
    void rangedHarassIsCappedAtTwentyPointsPerWindow() {
        var result = ThreatAB.run(ThreatAB.Scenario.RANGED_HARASS, ThreatAB.EXAMPLE);
        var windows = (result.ticks() + 99) / 100 + 1;
        assertTrue(result.limited() > 0, "the harass reached the cap");
        assertTrue(result.pointsAdded() <= ThreatAB.EXAMPLE.maxPerWindow() * windows, result.toString());
    }

    @Test
    void anUnreachableTargetLosesTheLeadOnlyWithEnrichedThreat() {
        var off = ThreatAB.run(ThreatAB.Scenario.PILLAR, ThreatAB.OFF);
        var on = ThreatAB.run(ThreatAB.Scenario.PILLAR, ThreatAB.EXAMPLE);
        assertEquals(-1, off.firstTickAnotherLeads());
        assertTrue(on.firstTickAnotherLeads() > 0);
        assertEquals(on.portCalls() * ThreatAB.EXAMPLE.unreachableDecayPerSecond(), on.pointsRemoved(), 1e-9,
                "one loss of two points per decision one second apart");
    }

    @Test
    void blockedHitsAndAssistsAddTheirWeightsAndChangeNoLeader() {
        var blocked = ThreatAB.run(ThreatAB.Scenario.BLOCKED_HITS, ThreatAB.EXAMPLE);
        assertEquals(blocked.portCalls() * ThreatAB.EXAMPLE.blockedHits(), blocked.pointsAdded(), 1e-9);
        assertEquals(0, blocked.leaderChanges());
        var assist = ThreatAB.run(ThreatAB.Scenario.ASSIST, ThreatAB.EXAMPLE);
        assertEquals(assist.portCalls() * ThreatAB.EXAMPLE.assistsOthers(), assist.pointsAdded(), 1e-9);
        assertEquals(0, assist.leaderChanges());
    }
}
