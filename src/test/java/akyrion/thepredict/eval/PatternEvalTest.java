package akyrion.thepredict.eval;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.PressureLadder;
import akyrion.thepredict.domain.RestStreak;
import akyrion.thepredict.engine.ExploitBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternEvalTest {
    @Test
    void thePlanGridReadsTwentyFourPatternsAtThreeRatesAndFourSkills() {
        var figures = PatternFamily.read();
        System.out.println(figures.text());
        assertEquals(24 * 3 * 4 * 200, figures.encounters(), figures.text());
        assertEquals(24, figures.patterns(), figures.text());
        assertEquals(25548, figures.distinctEntryTrials(), figures.text());
        assertEquals(0.920625, figures.recall90(), 1.0E-9, figures.text());
        assertEquals(0.5084895833333334, figures.recall75(), 1.0E-9, figures.text());
        assertEquals(0.10005208333333333, figures.recall60(), 1.0E-9, figures.text());
        assertEquals(0.9973842443183748, figures.precision(), 1.0E-9, figures.text());
        assertEquals(0.085, figures.falseRate(), 1.0E-9, figures.text());
        assertEquals(14, figures.medianDetect(), figures.text());
        assertEquals(37, figures.medianAdapt(), figures.text());
        assertEquals("ACCEPT_H1", figures.gsprt(), figures.text());
        assertEquals(DifficultyGlobals.LADDER_K, figures.ladderK(), figures.text());
        assertTrue(figures.ladderMatches(), figures.text());
    }

    @Test
    void ladderExploitRisesAndDelayFallsWithoutTouchingK() {
        var previousShare = -1.0;
        var previousDelay = Integer.MAX_VALUE;
        var previousGap = Integer.MAX_VALUE;
        for (var rung = 0; rung <= 8; rung++) {
            var levers = PressureLadder.levers(rung, 0, 0, true);
            assertTrue(levers.exploitShare() >= previousShare, "share " + rung);
            assertTrue(levers.reactionDelayTicks() <= previousDelay, "delay " + rung);
            assertTrue(PressureLadder.groupGap(rung) <= previousGap, "gap " + rung);
            previousShare = levers.exploitShare();
            previousDelay = levers.reactionDelayTicks();
            previousGap = PressureLadder.groupGap(rung);
        }
        assertEquals(0.0, PressureLadder.levers(0, 0, 0, true).exploitShare());
        assertEquals(0.50, PressureLadder.levers(8, 0, 0, true).exploitShare());
        assertEquals(0.12, DifficultyGlobals.LADDER_K);
        assertEquals("c4665ef7e89d59e032ae3294681338a5dbc9b2b5ef2c3d2faef3dd73f1d7bdb0",
                PressureLadder.LADDER_SHA256);
        assertTrue(PressureLadder.bundledMatches());
    }

    @Test
    void motivatedCastsStayInsideTheQuotaAndTheStreakOpensAtThree() {
        for (var rung : new int[] {8, 4, 0}) {
            var quota = PressureLadder.levers(rung, 0, 0, true).exploitShare();
            var budget = new ExploitBudget();
            var motivated = 0;
            var ticks = new int[16];
            var flags = new boolean[16];
            for (var cast = 0; cast < 16; cast++) {
                var tick = cast * 30;
                var allowed = budget.allows(tick, 400, quota);
                budget.record(tick, allowed);
                ticks[cast] = tick;
                flags[cast] = allowed;
                if (allowed) {
                    motivated++;
                }
                var windowCasts = 0;
                var windowMotivated = 0;
                for (var older = 0; older <= cast; older++) {
                    if (tick - ticks[older] <= 400) {
                        windowCasts++;
                        if (flags[older]) {
                            windowMotivated++;
                        }
                    }
                }
                assertEquals(windowMotivated, budget.motivated(tick, 400));
                assertTrue(windowMotivated <= quota * windowCasts + 1.0E-9,
                        "rung " + rung + " share " + windowMotivated + "/" + windowCasts);
            }
            assertEquals(quota == 0.0, motivated == 0, "rung " + rung + " motivated " + motivated);
            if (quota >= 0.2) {
                assertTrue(motivated > 0, "rung " + rung);
            }
        }
        assertFalse(RestStreak.opens(2, 3));
        var streak = RestStreak.next(0, true);
        streak = RestStreak.next(streak, true);
        assertFalse(RestStreak.opens(streak, 3));
        streak = RestStreak.next(streak, true);
        assertEquals(3, streak);
        assertTrue(RestStreak.opens(streak, 3));
        assertEquals(0, RestStreak.next(0, false));
    }
}
