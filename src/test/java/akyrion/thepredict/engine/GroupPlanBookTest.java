package akyrion.thepredict.engine;

import akyrion.thepredict.domain.GroupPlan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adoption of a group plan (M25, B1, B4, B5, N-1). */
class GroupPlanBookTest {
    @Test
    void theSameCaptureAdoptsWithoutWaitingAndTheSamePlanDoesNotCount() {
        var book = new GroupPlanBook();
        assertTrue(book.noteCapture(0, 1, true, List.of(GroupPlan.PRESS_MAIN_TARGET), 40, 100, true));
        assertTrue(book.provisional());
        assertTrue(book.acceptAnswer(GroupPlan.SURROUND_TARGET, 1, 5, 40));
        assertEquals(GroupPlan.SURROUND_TARGET, book.plan());
        assertFalse(book.provisional());
        assertFalse(book.acceptAnswer(GroupPlan.SURROUND_TARGET, 3, 50, 40));
        assertEquals(GroupPlan.SURROUND_TARGET, book.plan());
    }

    @Test
    void aCaptureWithoutG0DoesNotReplaceALivePlan() {
        var book = opened();
        assertFalse(book.noteCapture(20, 2, false,
                List.of(GroupPlan.SCREEN_THE_LEADER, GroupPlan.SURROUND_TARGET), 40, 100, true));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, book.plan());
        assertFalse(book.release(80, 40));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, book.plan());
    }

    @Test
    void aLaterAnswerWaitsUntilMinPlanTicks() {
        var book = opened();
        assertFalse(book.noteCapture(20, 2, true, List.of(GroupPlan.SCREEN_THE_LEADER), 40, 100, true));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, book.plan());
        assertFalse(book.acceptAnswer(GroupPlan.SURROUND_TARGET, 2, 25, 40));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, book.plan());
        assertFalse(book.release(44, 40));
        assertTrue(book.release(45, 40));
        assertEquals(GroupPlan.SURROUND_TARGET, book.plan());
        assertFalse(book.provisional());
    }

    @Test
    void aDeadPlanTakesTheBestStoredCandidateThatCanStillRun() {
        var book = screened();
        book.noteCapture(20, 2, false, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET,
                GroupPlan.SCREEN_THE_LEADER), 40, 100, true);
        assertEquals(GroupPlan.SCREEN_THE_LEADER, book.plan());
        assertTrue(book.die(31, plan -> plan != GroupPlan.SCREEN_THE_LEADER, 2));
        assertEquals(GroupPlan.PRESS_MAIN_TARGET, book.plan());
        assertTrue(book.provisional());
    }

    @Test
    void aDeadPlanWithNothingPossibleClears() {
        var book = screened();
        book.noteCapture(20, 2, false, List.of(GroupPlan.SCREEN_THE_LEADER), 40, 100, true);
        assertTrue(book.die(31, plan -> false, 2));
        assertNull(book.plan());
    }

    @Test
    void theOriginOfThePlanSurvivesAHoldAndADeath() {
        var book = new GroupPlanBook();
        book.noteCapture(0, 1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SCREEN_THE_LEADER), 40, 100,
                true);
        assertFalse(book.answered(), "candidate 0 of a capture is a local plan");
        book.acceptAnswer(GroupPlan.SURROUND_TARGET, 1, 5, 40);
        assertTrue(book.answered(), "the g0 answer is the plan of Jev");
        assertFalse(book.noteCapture(20, 2, true, List.of(GroupPlan.SCREEN_THE_LEADER), 40, 100, true));
        assertTrue(book.release(45, 40));
        assertEquals(GroupPlan.SCREEN_THE_LEADER, book.plan());
        assertFalse(book.answered(), "a held candidate of a capture stays local once adopted");
        var waited = opened();
        assertFalse(waited.acceptAnswer(GroupPlan.SURROUND_TARGET, 2, 25, 40));
        assertTrue(waited.release(45, 40));
        assertTrue(waited.answered(), "a held answer is still Jev's once adopted");
        assertTrue(waited.die(60, plan -> plan != GroupPlan.SURROUND_TARGET, 2));
        assertFalse(waited.answered(), "the replacement of a dead plan is local");
        waited.clear();
        assertFalse(waited.answered());
    }

    private static GroupPlanBook screened() {
        var book = new GroupPlanBook();
        book.noteCapture(0, 1, true, List.of(GroupPlan.SCREEN_THE_LEADER), 40, 100, true);
        book.acceptAnswer(GroupPlan.SCREEN_THE_LEADER, 1, 5, 40);
        return book;
    }

    private static GroupPlanBook opened() {
        var book = new GroupPlanBook();
        book.noteCapture(0, 1, true, List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SCREEN_THE_LEADER), 40, 100, true);
        book.acceptAnswer(GroupPlan.PRESS_MAIN_TARGET, 1, 5, 40);
        return book;
    }
}
