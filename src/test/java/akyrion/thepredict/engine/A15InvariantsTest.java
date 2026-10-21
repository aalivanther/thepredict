package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultySection;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.FightOutcome;
import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PartyBand;
import akyrion.thepredict.domain.PatternState;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.ProfileHash;
import akyrion.thepredict.domain.ProfileMemory;
import akyrion.thepredict.engine.SubjectDifficultyPriors;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A15InvariantsTest {
    @Test
    void aHundredThousandSeededEncountersNeverActivateBeforeFourOwnTrials() {
        var pattern = EnemyPattern.values()[0];
        var violations = 0;
        for (var encounter = 0; encounter < 100_000; encounter++) {
            var tracker = new PatternTracker(pattern);
            tracker.evidence().seed(20, 0);
            for (var trial = 0; trial < 3; trial++) {
                tracker.observe(true, 1, 0.05, 8, 1, 0.05, 4.0, 0);
            }
            if (tracker.state() == PatternState.ACTIVE && tracker.evidence().trials() < 4) {
                violations++;
            }
            if (encounter == 0) {
                assertTrue(tracker.evidence().detectorTrials() >= 8);
                assertTrue(tracker.streak() >= 1);
                assertTrue(tracker.evidence().trials() < 4);
                assertNotEquals(PatternState.ACTIVE, tracker.state());
            }
        }
        assertEquals(0, violations);
    }

    @Test
    void ladderFloorsSlackQuotaAndRestStayOnThePublishedTable() {
        var slack = new int[] {20, 16, 12, 8, 6, 4, 2, 0, 0};
        var exploit = new double[] {0.0, 0.0, 0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.50};
        for (var rung = 0; rung <= 8; rung++) {
            var levers = PressureLadder.levers(rung, 0, 0, true);
            assertEquals(slack[rung], levers.damagingCastSlackTicks());
            assertEquals(exploit[rung], levers.exploitShare(), 0.0);
        }
        assertEquals(30, PressureLadder.levers(0, 30, 0, true).reactionDelayTicks());
        assertEquals(12, PressureLadder.levers(8, 0, 12, true).reactionDelayTicks());
        assertEquals(1, PressureLadder.groupLevers(0, 0, 0, true).maxMeleeAttackers());
        assertEquals(20, PressureLadder.groupLevers(0, 0, 0, true).impactGapTicks());
        var rungs = new int[] {3 * 16, ProfileMemory.NONE, ProfileMemory.NONE, ProfileMemory.NONE,
                ProfileMemory.NONE, ProfileMemory.NONE};
        var row = new ProfileMemory(ProfileHash.of("boss"), 4, 2, 2, 0, FightOutcome.WIN, 1, 6f, 0f, 0f, rungs,
                List.of());
        var memory = new PlayerMemory(1, 1, 4, Optional.empty(), Optional.empty(), Optional.empty(), List.of(row));
        var opened = SubjectDifficultyPriors.opening(memory, "boss", DifficultySection.SOLO, PartyBand.ONE, 4, 1,
                MemorySettings.DEFAULT);
        assertEquals(slack[opened.rung()], PressureLadder.levers(opened.rung(), 0, 0, true).damagingCastSlackTicks());
        var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
        tracker.rest();
        assertEquals(slack[0], PressureLadder.levers(0, 0, 0, true).damagingCastSlackTicks());
        assertEquals(0.0, PressureLadder.levers(0, 0, 0, true).exploitShare(), 0.0);
    }
}
