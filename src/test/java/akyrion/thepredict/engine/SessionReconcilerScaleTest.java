package akyrion.thepredict.engine;

import akyrion.thepredict.domain.SessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconciliation with many sessions and the suspended sessions without a safety net (pendings of M06 for M13): the
 * checks per tick stay at 32 whatever the number of sessions, a ghost closes within one interval plus one round, and
 * a suspended session whose mob vanished while unloaded stays, as DT-20 records, until a server measurement decides.
 */
class SessionReconcilerScaleTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 7000})
    void aRoundChecksAtMostThirtyTwoSessionsPerTickAndAGhostClosesWithinOneIntervalPlusOneRound(int count) {
        var sessions = new EngineFixtures.Sessions();
        for (var index = 1; index <= count; index++) {
            sessions.open(index);
        }
        sessions.reconciler.tick(0);
        var ghost = EngineFixtures.id(count / 2);
        sessions.inactive.add(ghost);
        var round = (count + SessionReconciler.SESSIONS_PER_TICK - 1) / SessionReconciler.SESSIONS_PER_TICK;
        var closedAt = -1L;
        for (var tick = 1L; tick <= SessionReconciler.ROUND_INTERVAL_TICKS + 2L * round && closedAt < 0; tick++) {
            assertTrue(sessions.reconciler.tick(tick) <= SessionReconciler.SESSIONS_PER_TICK);
            if (sessions.registry.find(ghost).isEmpty()) {
                closedAt = tick;
            }
        }
        assertTrue(closedAt > 0 && closedAt <= SessionReconciler.ROUND_INTERVAL_TICKS + round,
                "closed at tick " + closedAt + " with rounds of " + round + " ticks");
    }

    @Test
    void suspendedSessionsWhoseMobVanishedWhileUnloadedStayUntilTheNextStart() {
        var sessions = new EngineFixtures.Sessions();
        for (var index = 1; index <= 100; index++) {
            sessions.open(index);
        }
        for (var index = 1; index <= 40; index++) {
            sessions.lifecycle.unloaded(EngineFixtures.id(index));
            sessions.inactive.add(EngineFixtures.id(index));
        }
        for (var tick = 0L; tick < 72_000; tick++) {
            sessions.clock.tick = tick;
            sessions.lifecycle.tick();
        }
        assertEquals(40, sessions.registry.count(SessionState.SUSPENDED), "one hour later, still in memory");
        assertEquals(60, sessions.registry.count(SessionState.ACTIVE));
    }
}
