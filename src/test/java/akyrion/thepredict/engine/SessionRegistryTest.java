package akyrion.thepredict.engine;

import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.intent;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static akyrion.thepredict.engine.EngineFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {
    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final SessionRegistry registry = fixture.registry;
    private final IntentBoard board = fixture.board;

    @Test
    void openStartsAtGenerationZeroActiveWithAPendingInstallationAndTheBaseView() {
        var session = fixture.open(1);
        assertEquals(new MobKey(id(1), 0), session.key());
        assertEquals(SessionState.ACTIVE, session.state());
        assertTrue(session.installPending());
        assertEquals(IntentView.base(0), board.current(id(1)));
    }

    @Test
    void twoInstancesOfTheSameTypeKeepIndependentState() {
        var first = fixture.open(1);
        var second = fixture.open(2);
        first.adoptIntent(intent(first.key()));
        first.recordInstall(new InstallResult.Installed(9), 3);
        registry.suspend(id(1));
        assertTrue(second.intent().isEmpty());
        assertTrue(second.installPending());
        assertEquals(SessionState.ACTIVE, second.state());
        assertEquals(IntentView.base(0), board.current(id(2)));
        registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertSame(second, registry.find(id(2)).orElseThrow());
    }

    @Test
    void anAnswerForAnOlderGenerationFindsNoSession() {
        fixture.open(1);
        registry.suspend(id(1));
        registry.resume(id(1));
        assertTrue(registry.find(new MobKey(id(1), 0)).isEmpty());
        assertTrue(registry.find(new MobKey(id(1), 1)).isPresent());
        assertTrue(registry.find(new MobKey(id(1), 2)).isEmpty());
    }

    @Test
    void suspendingRemovesTheViewAndResumingAdvancesTheGenerationWithANewBaseView() {
        var session = fixture.open(1);
        session.recordInstall(new InstallResult.Installed(9), 1);
        registry.suspend(id(1));
        assertEquals(SessionState.SUSPENDED, session.state());
        assertSame(IntentView.ABSENT, board.current(id(1)));
        var resumed = registry.resume(id(1));
        assertEquals(1, resumed.key().generation());
        assertEquals(SessionState.ACTIVE, resumed.state());
        assertTrue(resumed.installPending());
        assertTrue(resumed.installedRevision().isEmpty());
        assertEquals(IntentView.base(1), board.current(id(1)));
    }

    @Test
    void openingASuspendedMobResumesItWithTheGivenProfile() {
        fixture.open(1);
        registry.suspend(id(1));
        var reopened = registry.open(id(1), profile("Other"));
        assertEquals(1, reopened.key().generation());
        assertEquals("Other", reopened.profile().mythicMobId());
        assertEquals(IntentView.base(1), board.current(id(1)));
    }

    @Test
    void openingAnActiveMobAgainChangesNothing() {
        var session = fixture.open(1);
        session.recordInstall(new InstallResult.Installed(9), 1);
        assertSame(session, registry.open(id(1), profile("Other")));
        assertEquals(0, session.key().generation());
        assertEquals("Guardian", session.profile().mythicMobId());
        assertFalse(session.installPending());
    }

    @Test
    void onlyASuspendedSessionResumes() {
        fixture.open(1);
        assertThrows(IllegalStateException.class, () -> registry.resume(id(1)));
        assertThrows(NoSuchElementException.class, () -> registry.resume(id(2)));
        registry.suspend(id(2));
        assertTrue(registry.find(id(2)).isEmpty());
    }

    @Test
    void advancingDropsWhatBelongedToTheOldGenerationAndKeepsTheState() {
        var session = fixture.open(1);
        session.adoptIntent(intent(session.key()));
        session.recordTarget(player(1), 4.0);
        session.streaks(2, 1);
        session.recordInstall(new InstallResult.Installed(9), 1);
        var advanced = registry.advanceGeneration(id(1), profile("Guardian")).orElseThrow();
        assertEquals(1, advanced.key().generation());
        assertEquals(SessionState.ACTIVE, advanced.state());
        assertTrue(advanced.intent().isEmpty() && advanced.lastTargetId().isEmpty());
        assertEquals(0, advanced.lowConfidenceStreak() + advanced.noProgressStreak());
        assertTrue(advanced.installPending());
        assertEquals(IntentView.base(1), board.current(id(1)));
        assertTrue(registry.advanceGeneration(id(9)).isEmpty());
    }

    @Test
    void advancingAlsoDropsTheMicroTheReactionSlotsAndTheirCooldowns() {
        var session = fixture.open(1);
        session.micro(akyrion.thepredict.domain.MicroBehaviour.ORBIT, akyrion.thepredict.domain.MicroSide.RIGHT);
        var evade = akyrion.thepredict.domain.Reaction.EVADE_CRITICAL;
        session.armReaction(evade.ordinal(), 100, 140);
        session.reactionArmedSince()[akyrion.thepredict.domain.Reaction.FINISH.ordinal()] = 5L;
        var advanced = registry.advanceGeneration(id(1), profile("Guardian")).orElseThrow();
        assertEquals(akyrion.thepredict.domain.MicroBehaviour.NATIVE, advanced.micro(),
                "a micro of the old generation is not kept for the new view (M20.9)");
        assertEquals(akyrion.thepredict.domain.ReactionPolicy.NONE,
                advanced.slot(akyrion.thepredict.domain.ReactionBand.SURVIVAL));
        assertEquals(0L, advanced.reactionCooldown()[evade.ordinal()]);
        assertEquals(-1L, advanced.reactionArmedSince()[akyrion.thepredict.domain.Reaction.FINISH.ordinal()]);
    }

    @Test
    void advancingASuspendedSessionPublishesNoView() {
        fixture.open(1);
        registry.suspend(id(1));
        registry.advanceGeneration(id(1));
        assertEquals(SessionState.SUSPENDED, registry.find(id(1)).orElseThrow().state());
        assertSame(IntentView.ABSENT, board.current(id(1)));
        assertEquals(2, registry.resume(id(1)).key().generation());
    }

    @Test
    void closingRemovesEveryReferenceInEngine() {
        var session = fixture.open(1);
        var encounter = fixture.encounters.assign(session, WORLD, player(1), near());
        var key = registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertEquals(Optional.of(new MobKey(id(1), 0)), key);
        assertEquals(SessionState.CLOSED, session.state());
        assertEquals(Optional.of(FallbackReason.SESSION_INVALID), session.lastFallbackReason());
        assertTrue(registry.find(id(1)).isEmpty());
        assertTrue(session.encounter().isEmpty());
        assertTrue(encounter.isClosed());
        assertTrue(fixture.encounters.ofParticipant(WORLD, player(1)).isEmpty());
        assertEquals(0, fixture.encounters.participantKeys());
        assertSame(IntentView.ABSENT, board.current(id(1)));
        assertTrue(fixture.agenda.dueProbes(0, 10).isEmpty() && fixture.agenda.dueProbes(1, 10).isEmpty());
        assertTrue(registry.close(id(1), FallbackReason.SESSION_INVALID).isEmpty());
    }

    @Test
    void theBoardReflectsEveryTransition() {
        var views = new ArrayList<IntentView>();
        fixture.open(1);
        views.add(board.current(id(1)));
        registry.suspend(id(1));
        views.add(board.current(id(1)));
        registry.resume(id(1));
        views.add(board.current(id(1)));
        registry.advanceGeneration(id(1));
        views.add(board.current(id(1)));
        registry.close(id(1), FallbackReason.SESSION_INVALID);
        views.add(board.current(id(1)));
        assertEquals(List.of(IntentView.base(0), IntentView.ABSENT, IntentView.base(1), IntentView.base(2),
                IntentView.ABSENT), views);
    }

    @Test
    void aMobOpenedAgainAfterItsSessionClosedStartsAtGenerationZero() {
        fixture.open(1);
        registry.close(id(1), FallbackReason.SESSION_INVALID);
        assertEquals(0, fixture.open(1).key().generation());
    }

    @Test
    void countsAndCopiesSeparateActiveAndSuspendedSessions() {
        fixture.open(1);
        fixture.open(2);
        fixture.open(3);
        registry.suspend(id(2));
        assertEquals(2, registry.count(SessionState.ACTIVE));
        assertEquals(1, registry.count(SessionState.SUSPENDED));
        assertEquals(List.of(id(1), id(3)), registry.active().stream().map(session -> session.key().entityId()).toList());
        assertEquals(3, registry.all().size());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }
}
