package akyrion.thepredict.eval;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternTracker;
import akyrion.thepredict.engine.duel.SkirmishSimulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * E3 of M27.11 against entities (SIM-05): the memory does not act against an entity rival, so the seed arm and the
 * record arm play the same fight, byte for byte, whatever the planted habit and its show rate.
 */
class MemoryTtkContrastTest {
    @Test
    void entityRivalsAreIdenticalAcrossArms() {
        var arena = new SkirmishSimulator.Fight("e3-test", 3, 1, false, 200);
        for (var rival : new String[] {"none", "classic", "classic-tuned", "local-best"}) {
            for (var seed = 1L; seed <= 12; seed++) {
                var tracker = new PatternTracker(EnemyPattern.SPAMS_CLICKS);
                tracker.evidence().seed(6, 0);
                var seeded = SkirmishSimulator.bank(arena, rival, seed, tracker, 0.9);
                var recorded = SkirmishSimulator.bank(arena, rival, seed);
                assertEquals(recorded.deathTick(), seeded.deathTick(), rival + " seed " + seed);
                assertEquals(recorded.impacts(), seeded.impacts(), rival + " seed " + seed);
                assertEquals(recorded.squadWounds(), seeded.squadWounds(), rival + " seed " + seed);
                assertEquals(recorded.transcript(), seeded.transcript(), rival + " seed " + seed);
            }
        }
    }
}
