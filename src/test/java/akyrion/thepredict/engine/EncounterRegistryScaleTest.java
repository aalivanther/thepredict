package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encounters with many scattered mobs (pendings of M06 for M13): the cost of {@code assign} stays within k·16
 * follow-range predicates, and the fragmentation left by the absence of a global merge is bounded and visible. The
 * numbers themselves are published by {@code M13Measurements}; these tests keep the bounds.
 */
class EncounterRegistryScaleTest {
    @ParameterizedTest
    @CsvSource({"200, 1", "200, 10"})
    void assignEvaluatesAtMostSixteenFollowRangePredicatesPerOpenEncounterOfTheTarget(int mobs, int players) {
        var scale = EncounterScale.run(mobs, players, 320, 3, 13L);
        for (var call : scale.calls()) {
            assertTrue(call.checks() <= (long) EncounterRegistry.MAX_MEMBERS * call.candidates(),
                    call + " exceeds k·16");
        }
        assertTrue(scale.maxCandidates() > 1, "scattered mobs leave several encounters per target");
    }

    @ParameterizedTest
    @CsvSource({"200, 1", "200, 10"})
    void scatteredMobsFormNoFewerEncountersThanTheirFollowRangeGroupsAndStayWithinTheLimits(int mobs, int players) {
        try (var fixture = LoadScenarios.scattered(mobs, players, 320, 13L)) {
            fixture.run(20 * fixture.profile.timing().heartbeatTicks());
            var minimum = LoadScenarios.minimumEncounters(fixture);
            assertTrue(fixture.sessions.encounters.size() >= minimum);
            assertTrue(fixture.sessions.encounters.size() <= mobs);
            assertTrue(fixture.peaks.members <= EncounterRegistry.MAX_MEMBERS);
            assertTrue(fixture.peaks.capturesPerTick <= fixture.settings.maxEncounterEvaluationsPerTick());
        }
    }

    @Test
    void twoGroupsThatMeetStayTwoEncountersBecauseThereIsNoGlobalMerge() {
        try (var fixture = new LoadFixture()) {
            fixture.player(1, WORLD_A, 0, 0);
            for (var index = 1; index <= 5; index++) {
                fixture.spawn(index, WORLD_A, -60 - index, 0, 1);
                fixture.spawn(index + 5, WORLD_A, 60 + index, 0, 1);
            }
            fixture.ready();
            fixture.run(40);
            assertEquals(2, fixture.sessions.encounters.size());
            for (var index = 1; index <= 10; index++) {
                fixture.move(index, index - 5.5, 1);
            }
            fixture.run(400);
            assertEquals(2, fixture.sessions.encounters.size(), "the known limit of M06.3, measured in M13");
        }
    }
}
