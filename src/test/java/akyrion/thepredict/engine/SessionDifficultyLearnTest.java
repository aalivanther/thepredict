package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyMode;
import akyrion.thepredict.domain.DifficultyResolution;
import akyrion.thepredict.domain.FightEventKind;
import akyrion.thepredict.domain.PlayMode;
import akyrion.thepredict.domain.PressureCause;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionDifficultyLearnTest {
    @Test
    void closeAsSkipsADeathWithoutAValidHitAndLearnsATargetDeath() {
        var missed = player();
        var target = UUID.randomUUID();
        missed.clock().dealt(0, target, target, 0.2, 0.8);
        missed.closeAs(FightEventKind.MOB_DIED, 10, 0, 8, true);
        assertEquals(1, missed.noEvidence());
        assertEquals(8.0, missed.prior());

        var early = player();
        early.clock().dealt(0, target, target, 0.2, 0.8);
        early.noteHit(10, 0.2);
        assertEquals(0.0, early.rateAtLastHit());
        early.closeAs(FightEventKind.MOB_DIED, 10, 0, 8, true);
        assertEquals(1, early.noEvidence());
        assertEquals(8.0, early.prior());

        var killed = player();
        killed.clock().dealt(0, target, target, 0.2, 0.8);
        killed.closeAs(FightEventKind.TARGET_DIED, 20, 0, 8, true);
        assertEquals(0, killed.noEvidence());
        assertTrue(killed.prior() < 8.0);
    }

    @Test
    void aValidHitFreezesTheRateAndAMobDeathLearnsTheCensoredTime() {
        var difficulty = player();
        var target = UUID.randomUUID();
        difficulty.clock().dealt(0, target, target, 0.9, 0.1);
        difficulty.noteHit(60, 0.9);
        assertTrue(difficulty.rateAtLastHit() > 0.0);
        var before = difficulty.prior();
        difficulty.closeAs(FightEventKind.MOB_DIED, 60, 0, 8, true);
        assertEquals(0, difficulty.noEvidence());
        assertTrue(difficulty.prior() < before);
    }

    @Test
    void fourSignChangesDampTheNextFight() {
        var difficulty = player();
        var target = UUID.randomUUID();
        long cursor = 0;
        for (var fight = 0; fight < 6; fight++) {
            var slow = fight % 2 == 0;
            var span = slow ? 50_000L : 40L;
            difficulty.clock().dealt(cursor, target, target, 0.2, 0.8);
            cursor += span;
            difficulty.closeAs(FightEventKind.TARGET_DIED, cursor, 0, 8, true);
        }
        assertEquals(PressureCause.DAMPED, difficulty.slowCause());
        assertEquals(0, difficulty.spawnDelta());
    }

    @Test
    void fiveFightsStuckAtTheFloorAskForALowerSpawnLevel() {
        var difficulty = player();
        var target = UUID.randomUUID();
        long cursor = 0;
        for (var fight = 0; fight < 14; fight++) {
            difficulty.clock().dealt(cursor, target, target, 0.2, 0.8);
            cursor += 40;
            difficulty.closeAs(FightEventKind.TARGET_DIED, cursor, 0, 8, true);
        }
        assertEquals(-1, difficulty.spawnDelta());
        assertEquals(PressureCause.TARGET_UNREACHABLE_TOO_STRONG, difficulty.slowCause());
        assertEquals(0, difficulty.noEvidence());
    }

    private static SessionDifficulty player() {
        var difficulty = new SessionDifficulty();
        difficulty.resolve(0, true, 0, 0, 8, DifficultyMode.PLAYER, DifficultyResolution.ABSENT, null, null, null,
                null, false, DifficultyResolution.ABSENT);
        assertEquals(PlayMode.PLAYER, difficulty.play());
        return difficulty;
    }
}
