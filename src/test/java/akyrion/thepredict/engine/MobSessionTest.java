package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.intent;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static akyrion.thepredict.engine.EngineFixtures.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobSessionTest {
    private final MobSession session = new MobSession(new MobKey(id(1), 0), profile("Guardian"));

    @Test
    void anInstalledSetClearsThePendingMarkAndKeepsItsRevision() {
        session.recordInstall(new InstallResult.Installed(12), 7);
        assertFalse(session.installPending());
        assertEquals(OptionalLong.of(7), session.installedRevision());
    }

    @Test
    void aDeferredInstallationStaysPendingToBeRetried() {
        session.recordInstall(new InstallResult.Deferred("entity unavailable"), 7);
        assertTrue(session.installPending());
        assertTrue(session.installedRevision().isEmpty());
    }

    @Test
    void anUnsupportedInstallationIsNotRetriedUntilTheGenerationChanges() {
        session.recordInstall(new InstallResult.Installed(12), 6);
        session.recordInstall(new InstallResult.Unsupported("brain species"), 7);
        assertFalse(session.installPending());
        assertTrue(session.installedRevision().isEmpty());
        session.nextGeneration(profile("Guardian"));
        assertTrue(session.installPending());
    }

    @Test
    void theNextGenerationDropsEverythingOfThePreviousIncarnation() {
        session.adoptIntent(intent(session.key()));
        session.recordTarget(player(1), 3.5);
        session.streaks(2, 3);
        session.outcome(ExecutionOutcome.CAST);
        session.nextProbeTick(40);
        session.nextGeneration(profile("Other"));
        assertEquals(new MobKey(id(1), 1), session.key());
        assertEquals("Other", session.profile().mythicMobId());
        assertTrue(session.intent().isEmpty());
        assertTrue(session.lastTargetId().isEmpty() && session.lastTargetDistanceBlocks().isEmpty());
        assertEquals(0, session.lowConfidenceStreak());
        assertEquals(0, session.noProgressStreak());
        assertEquals(Optional.empty(), session.lastOutcome());
        assertEquals(40, session.nextProbeTick());
    }

    @Test
    void aGenerationChangeDropsTheMicroTheReactionsAndTheZoneCountsOfTheOldIncarnation() {
        var reaction = akyrion.thepredict.domain.Reaction.values()[0];
        session.micro(akyrion.thepredict.domain.MicroBehaviour.HOVER, akyrion.thepredict.domain.MicroSide.RIGHT);
        session.noteMicro(5);
        session.sidestepped(true);
        session.mismatchOpen(true);
        session.armReaction(reaction.ordinal(), 50, 90);
        session.reactionArmedSince()[reaction.ordinal()] = 12;
        session.armChoice(0);
        session.notePushDefer();
        session.noteAnswerWait();
        session.armChoice(-1);
        assertTrue(session.pushDeferredTicks() > 0 && session.answerWaitedTicks() > 0);
        session.nextGeneration(profile("Guardian"));
        assertEquals(akyrion.thepredict.domain.MicroBehaviour.NATIVE, session.micro(), "the new view is native too");
        assertEquals(akyrion.thepredict.domain.MicroSide.LEFT, session.microSide());
        assertEquals(-1, session.microSince());
        assertFalse(session.sidestepped());
        assertFalse(session.mismatchOpen());
        assertEquals(akyrion.thepredict.domain.ReactionPolicy.NONE, session.slot(reaction.band()));
        assertEquals(0L, session.slotUntil(reaction.band()));
        for (var ordinal = 0; ordinal < session.reactionCooldown().length; ordinal++) {
            assertEquals(0L, session.reactionCooldown()[ordinal]);
            assertEquals(-1L, session.reactionArmedSince()[ordinal]);
        }
        assertEquals(0, session.pushDeferredTicks());
        assertEquals(0, session.answerWaitedTicks());
        session.armChoice(0);
        assertEquals(0, session.pushDeferredTicks(), "no deferral of an old choice survives");
        assertEquals(0, session.answerWaitedTicks());
        assertEquals(MobSession.NEVER, session.nextReadyTick());
        assertTrue(session.candidates().isEmpty());
    }

    @Test
    void anIntentOfAnotherGenerationIsRejected() {
        var old = session.key();
        session.nextGeneration(profile("Guardian"));
        assertThrows(IllegalArgumentException.class, () -> session.adoptIntent(intent(old)));
        session.adoptIntent(intent(session.key()));
        session.dropIntent();
        assertTrue(session.intent().isEmpty());
    }
}
