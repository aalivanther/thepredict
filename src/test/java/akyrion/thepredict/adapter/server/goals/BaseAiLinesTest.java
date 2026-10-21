package akyrion.thepredict.adapter.server.goals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAiLinesTest {
    @Test
    void explicitPrioritiesAreKeptAndLinesWithoutOneTakeTheirPositionInTheListLikeMythicMobs() {
        assertEquals(List.of(new BaseAiLines.Line(1, "float"), new BaseAiLines.Line(4, "meleeAttack"),
                        new BaseAiLines.Line(3, "randomStroll{speed=1}"), new BaseAiLines.Line(0, "lookAtTarget"),
                        new BaseAiLines.Line(5, "leapAtTarget")),
                BaseAiLines.parse(List.of("float", "4 meleeAttack", "randomStroll{speed=1}", "0 lookAtTarget", "leapAtTarget")));
    }

    @Test
    void aMixedListKeepsTheNativeRelativeOrder() {
        assertEquals(List.of(new BaseAiLines.Line(5, "meleeAttack"), new BaseAiLines.Line(2, "randomStroll")),
                BaseAiLines.parse(List.of("5 meleeAttack", "randomStroll")));
        assertEquals(List.of(new BaseAiLines.Line(3, "randomStroll")), BaseAiLines.parse(List.of("", "  ", "randomStroll")));
    }

    @Test
    void blankLinesNumbersWithoutGoalAndOversizedOrSignedPrioritiesAreLeftOut() {
        assertEquals(List.of(new BaseAiLines.Line(999_999, "meleeAttack")),
                BaseAiLines.parse(List.of("", "   ", "7", "1234567 randomStroll", "999999 meleeAttack", "-1 leapAtTarget",
                        "+2 float")));
    }

    @Test
    void spacesInsideArgumentsStayWithTheGoal() {
        assertEquals(List.of(new BaseAiLines.Line(3, "fleeConditional{mc=[ - predictormove{m=RETREAT} true ]}")),
                BaseAiLines.parse(List.of("  3   fleeConditional{mc=[ - predictormove{m=RETREAT} true ]}")));
    }

    @Test
    void clearIsRecognizedWithOrWithoutPriorityInAnyCase() {
        for (var line : List.of("clear", "0 clear", "12 CLEAR", "Clear{}", " 5 cLeAr ")) {
            assertTrue(BaseAiLines.isClear(line), line);
        }
        for (var line : List.of("clearTarget", "0 meleeAttack", "", "0", "unclear")) {
            assertFalse(BaseAiLines.isClear(line), line);
        }
    }

    @Test
    void goalNamesAreComparedBeforeTheArgumentBlockWithoutCase() {
        assertTrue(BaseAiLines.hasGoal("12 lookattarget", "lookAtTarget"));
        assertTrue(BaseAiLines.hasGoal("LookAtTarget{x=1}", "lookAtTarget"));
        assertFalse(BaseAiLines.hasGoal("12 lookAtTargetConditional", "lookAtTarget"));
        assertFalse(BaseAiLines.hasGoal("12 lookAtPlayers", "lookAtTarget"));
    }
}
