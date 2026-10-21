package akyrion.thepredict.engine;

import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static akyrion.thepredict.engine.EngineFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLifecycleTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final SessionLifecycle lifecycle = fixture.lifecycle;
    private final SessionRegistry registry = fixture.registry;

    private MobSession session(int value) {
        return registry.find(id(value)).orElseThrow();
    }

    private static Optional<TacticalProfile> guardianOnly(String mobId) {
        return mobId.equals("Guardian") ? Optional.of(profile("Guardian")) : Optional.empty();
    }

    @Test
    void aSpawnOpensAnActiveSessionWithAPendingInstallation() {
        lifecycle.discovered(id(1), profile("Guardian"));
        assertTrue(session(1).installPending());
        assertEquals(IntentView.base(0), fixture.board.current(id(1)));
        assertEquals(1, fixture.metrics.openedSessions());
    }

    @Test
    void anUnloadSuspendsAndALoadResumesWithTheNextGenerationAndAnotherInstallation() {
        lifecycle.discovered(id(1), profile("Guardian"));
        session(1).recordInstall(new InstallResult.Installed(9), 1);
        lifecycle.unloaded(id(1));
        lifecycle.unloaded(id(1));
        assertEquals(SessionState.SUSPENDED, session(1).state());
        lifecycle.discovered(id(1), profile("Guardian"));
        assertEquals(1, session(1).key().generation());
        assertTrue(session(1).installPending());
        lifecycle.discovered(id(1), profile("Guardian"));
        assertEquals(1, session(1).key().generation());
        assertEquals(1, fixture.metrics.openedSessions());
    }

    @Test
    void aWorldChangeAdvancesTheGenerationLeavesTheEncounterAndAsksForTheGoalsAgain() {
        lifecycle.discovered(id(1), profile("Guardian"));
        var encounter = fixture.encounters.assign(session(1), WORLD, player(1), near());
        session(1).recordInstall(new InstallResult.Installed(9), 1);
        lifecycle.changedWorld(id(1));
        assertEquals(1, session(1).key().generation());
        assertTrue(session(1).encounter().isEmpty());
        assertTrue(encounter.isClosed());
        assertTrue(session(1).installPending());
        assertEquals(IntentView.base(1), fixture.board.current(id(1)));
    }

    @Test
    void deathDespawnAndRemovalCloseWithoutAnyReleaseAndCountTheirCause() {
        for (var mob = 1; mob <= 3; mob++) {
            lifecycle.discovered(id(mob), profile("Guardian"));
        }
        lifecycle.ended(id(1), SessionMetrics.SessionEnd.DIED);
        lifecycle.ended(id(1), SessionMetrics.SessionEnd.REMOVED);
        lifecycle.ended(id(2), SessionMetrics.SessionEnd.DESPAWNED);
        lifecycle.ended(id(3), SessionMetrics.SessionEnd.REMOVED);
        fixture.clock.tick = 5;
        lifecycle.tick();
        assertTrue(registry.all().isEmpty());
        assertTrue(fixture.released.isEmpty());
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.DIED));
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.DESPAWNED));
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.REMOVED));
    }

    @Test
    void aReloadAdvancesStillBoundSessionsAndUnbindsTheOthersClearingTheViewBeforeTheRelease() {
        lifecycle.discovered(id(1), profile("Guardian"));
        lifecycle.discovered(id(2), profile("Retired"));
        lifecycle.unloaded(id(1));
        var releasedWithView = new ArrayList<IntentView>();
        fixture.release = key -> releasedWithView.add(fixture.board.current(key.entityId()));
        fixture.clock.tick = 100;
        lifecycle.reloaded(SessionLifecycleTest::guardianOnly);
        assertEquals(1, session(1).key().generation());
        assertEquals(SessionState.SUSPENDED, session(1).state());
        assertTrue(registry.find(id(2)).isEmpty());
        assertSame(IntentView.ABSENT, fixture.board.current(id(2)));
        lifecycle.tick();
        assertTrue(releasedWithView.isEmpty());
        fixture.clock.tick = 101;
        lifecycle.tick();
        assertEquals(List.of(IntentView.ABSENT), releasedWithView);
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.UNBOUND));
    }

    @Test
    void aReloadGivesAnUnsupportedSessionAnotherInstallationAttempt() {
        lifecycle.discovered(id(1), profile("Guardian"));
        session(1).recordInstall(new InstallResult.Unsupported("species without MythicMobs movement goals"), 1);
        assertFalse(session(1).installPending());
        lifecycle.reloaded(SessionLifecycleTest::guardianOnly);
        assertTrue(session(1).installPending());
        assertEquals(IntentView.base(1), fixture.board.current(id(1)));
    }

    @Test
    void unbindingASuspendedMobQueuesNoRelease() {
        lifecycle.discovered(id(1), profile("Retired"));
        lifecycle.unloaded(id(1));
        lifecycle.reloaded(SessionLifecycleTest::guardianOnly);
        assertTrue(registry.find(id(1)).isEmpty());
        assertEquals(0, lifecycle.pendingReleases());
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.UNBOUND));
    }

    @Test
    void aReleaseIsSkippedWhenTheMobHasAnActiveSessionAgain() {
        lifecycle.discovered(id(1), profile("Retired"));
        lifecycle.unbind(id(1));
        lifecycle.discovered(id(1), profile("Guardian"));
        fixture.clock.tick = 1;
        lifecycle.tick();
        assertTrue(fixture.released.isEmpty());
        assertEquals(0, lifecycle.pendingReleases());
    }

    @Test
    void releasesAfterAMassUnbindAreBoundedPerTick() {
        for (var mob = 1; mob <= 40; mob++) {
            lifecycle.discovered(id(mob), profile("Retired"));
        }
        lifecycle.reloaded(SessionLifecycleTest::guardianOnly);
        assertEquals(0, fixture.board.size());
        fixture.clock.tick = 1;
        lifecycle.tick();
        assertEquals(SessionLifecycle.MAX_RELEASES_PER_TICK, fixture.released.size());
        fixture.clock.tick = 2;
        lifecycle.tick();
        assertEquals(40, fixture.released.size());
        assertEquals(0, lifecycle.pendingReleases());
    }

    @Test
    void shutdownWithTwoHundredFiftySessionsClearsEveryViewFirstAndReleasesExactlyTwoHundred() {
        for (var mob = 1; mob <= 250; mob++) {
            lifecycle.discovered(id(mob), profile("Guardian"));
        }
        lifecycle.unloaded(id(1));
        var boardSizes = new ArrayList<Integer>();
        fixture.release = key -> boardSizes.add(fixture.board.size());
        assertEquals(SessionLifecycle.MAX_RELEASES_AT_SHUTDOWN, lifecycle.shutdown());
        assertEquals(SessionLifecycle.MAX_RELEASES_AT_SHUTDOWN, boardSizes.size());
        assertTrue(boardSizes.stream().allMatch(size -> size == 0));
        assertEquals(0, fixture.board.size());
    }

    @Test
    void shutdownReleasesTheUnboundMobsStillWaitingBeforeTheActiveSessions() {
        lifecycle.discovered(id(1), profile("Guardian"));
        lifecycle.discovered(id(2), profile("Retired"));
        lifecycle.reloaded(SessionLifecycleTest::guardianOnly);
        assertEquals(2, lifecycle.shutdown());
        assertEquals(List.of(new MobKey(id(2), 0), new MobKey(id(1), 1)), fixture.released);
        assertEquals(0, lifecycle.pendingReleases());
    }

    @Test
    void aFailingReleaseIsCountedAndTheOthersStillRun() {
        for (var mob = 1; mob <= 3; mob++) {
            lifecycle.discovered(id(mob), profile("Guardian"));
        }
        var attempts = new ArrayList<MobKey>();
        fixture.release = key -> {
            attempts.add(key);
            if (attempts.size() == 1) {
                throw new IllegalStateException("boom");
            }
        };
        assertEquals(3, lifecycle.shutdown());
        assertEquals(3, attempts.size());
        assertEquals(1, fixture.metrics.failedReleases());
    }

    @Test
    void theTickAlsoRunsTheReconciliation() {
        lifecycle.discovered(id(1), profile("Guardian"));
        fixture.inactive.add(id(1));
        lifecycle.tick();
        assertTrue(registry.find(id(1)).isEmpty());
        assertEquals(1, fixture.metrics.endedSessions(SessionMetrics.SessionEnd.RECONCILED));
    }
}
