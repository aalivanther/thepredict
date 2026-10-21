package akyrion.thepredict.engine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static akyrion.thepredict.engine.LoadFixture.WORLD_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placement of a session by its target (M06.3, M09.1), found by the load suite of M13: the probe places a mob by its
 * current target, and the upkeep before a decision must not send it back by the target of an older capture.
 */
class SessionUpkeepTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 201, 210, 211})
    void aMobThatSwitchesToAPlayerWithoutEncounterJoinsANewEncounterOnceAndIsCapturedThere(int switchTick) {
        try (var fixture = new LoadFixture()) {
            fixture.player(1, WORLD_A, 0, 0);
            fixture.player(2, WORLD_A, 20, 0);
            fixture.spawn(1, WORLD_A, 4, 0, 1);
            fixture.spawn(2, WORLD_A, 5, 0, 1);
            fixture.ready();
            fixture.run(switchTick - 1);
            var opened = fixture.sessions.metrics.openedEncounters();

            fixture.target(1, 2);
            fixture.run(3 * fixture.profile.timing().heartbeatTicks());

            var session = fixture.sessions.registry.find(LoadFixture.mob(1)).orElseThrow();
            var encounter = session.encounter().flatMap(fixture.sessions.encounters::find).orElseThrow();
            assertEquals(opened + 1, fixture.sessions.metrics.openedEncounters(), "one new encounter, not one per probe");
            assertEquals(LoadFixture.player(2), session.lastTargetId().orElseThrow(), "captured in the new encounter");
            assertTrue(encounter.isParticipant(LoadFixture.player(2)));
            assertTrue(fixture.peaks.baseRun <= fixture.profile.timing().minimumDecisionIntervalTicks() + 2);
        }
    }
}
