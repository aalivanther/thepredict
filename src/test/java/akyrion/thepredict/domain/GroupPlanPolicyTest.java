package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plan templates of M25.9: which are possible, their utility, family diversity and adoption. */
class GroupPlanPolicyTest {
    @Test
    void eachPlanIsPossibleOnlyPastItsBoundary() {
        assertEquals(List.of(), GroupPlanPolicy.possible(1, true, true, true, true));
        assertEquals(List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET),
                GroupPlanPolicy.possible(2, false, false, false, false));
        assertTrue(GroupPlanPolicy.possible(3, false, false, false, false).contains(GroupPlan.SPLIT_TARGETS));
        assertEquals(List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET, GroupPlan.SCREEN_THE_LEADER,
                GroupPlan.FALL_BACK_AND_REGROUP), GroupPlanPolicy.possible(2, true, false, false, false));
        assertTrue(GroupPlanPolicy.possible(2, false, true, false, false).contains(GroupPlan.HARASS_FROM_RANGE));
        assertTrue(GroupPlanPolicy.possible(2, false, false, true, false).contains(GroupPlan.FALL_BACK_AND_REGROUP));
        assertTrue(GroupPlanPolicy.possible(2, false, false, false, true).contains(GroupPlan.SEARCH_LAST_KNOWN));
        assertEquals(GroupPlan.values().length, GroupPlanPolicy.possible(3, true, true, true, true).size());
    }

    @Test
    void theIncumbentKeepsFifteenPercentRoundedDown() {
        for (var plan : GroupPlan.values()) {
            var base = GroupPlanPolicy.utility(plan, false, 4);
            assertEquals(base * 115 / 100, GroupPlanPolicy.utility(plan, true, 4), plan.name());
        }
        assertEquals(115, GroupPlanPolicy.utility(GroupPlan.PRESS_MAIN_TARGET, true, 4), "100 × 1.15 is 115");
        assertEquals(84, GroupPlanPolicy.utility(GroupPlan.SPLIT_TARGETS, false, 4));
    }

    @Test
    void candidatesKeepOnePlanPerFamilyAndTheLocalPlanFirst() {
        var all = GroupPlanPolicy.possible(4, true, true, true, true);
        assertEquals(List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SCREEN_THE_LEADER, GroupPlan.FALL_BACK_AND_REGROUP,
                GroupPlan.SEARCH_LAST_KNOWN), GroupPlanPolicy.candidates(all, null, 4, 4));
        assertEquals(List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SCREEN_THE_LEADER, GroupPlan.FALL_BACK_AND_REGROUP),
                GroupPlanPolicy.candidates(all, null, 4, 3));
        assertEquals(List.of(GroupPlan.PRESS_MAIN_TARGET, GroupPlan.SURROUND_TARGET, GroupPlan.SCREEN_THE_LEADER,
                GroupPlan.FALL_BACK_AND_REGROUP), GroupPlanPolicy.candidates(all, GroupPlan.SURROUND_TARGET, 4, 4),
                "an incumbent of the local plan's family still comes after the forced local plan");
        assertEquals(List.of(), GroupPlanPolicy.candidates(List.of(GroupPlan.PRESS_MAIN_TARGET), null, 4, 4));
    }

    @Test
    void aCaptureMayAdoptOnlyWhenThePlanIsOpenOrItAsks() {
        assertThrows(NullPointerException.class, () -> GroupPlanPolicy.mayAdopt(false, null, 0, 100, true, false),
                "without a plan in force the caller adopts without asking");
        assertTrue(GroupPlanPolicy.mayAdopt(false, GroupPlan.SURROUND_TARGET, 0, 100, true, true));
        assertTrue(GroupPlanPolicy.mayAdopt(false, GroupPlan.SURROUND_TARGET, 0, 100, false, false));
        assertTrue(GroupPlanPolicy.mayAdopt(false, GroupPlan.SURROUND_TARGET, 100, 100, true, false));
        assertFalse(GroupPlanPolicy.mayAdopt(false, GroupPlan.SURROUND_TARGET, 99, 100, true, false));
        assertTrue(GroupPlanPolicy.mayAdopt(true, GroupPlan.SURROUND_TARGET, 99, 100, true, false));
    }
}
