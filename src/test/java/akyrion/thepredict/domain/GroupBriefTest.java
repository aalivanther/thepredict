package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DOC-02: the question set decides which member questions go with the request. */
class GroupBriefTest {
    private static final UUID LEADER = new UUID(1, 1);
    private static final UUID MINION = new UUID(1, 2);

    @Test
    void eachQuestionSetAsksTheMembersItNames() {
        assertTrue(brief(GroupQuestions.MEMBERS).asksMember(LEADER));
        assertTrue(brief(GroupQuestions.MEMBERS).asksMember(MINION));
        for (var leaderAndPlan : List.of(GroupQuestions.PLAN_AND_LEADER, GroupQuestions.LEADER_ONLY)) {
            assertTrue(brief(leaderAndPlan).asksMember(LEADER), leaderAndPlan.name());
            assertFalse(brief(leaderAndPlan).asksMember(MINION), leaderAndPlan.name());
        }
        assertFalse(brief(GroupQuestions.PLAN_ONLY).asksMember(LEADER), "plan_only asks only the group plan");
        assertFalse(brief(GroupQuestions.PLAN_ONLY).asksMember(MINION));
        assertTrue(GroupBrief.NONE.asksMember(MINION), "no brief keeps every member question");
    }

    @Test
    void aBriefWithoutAKnownLeaderKeepsEveryMemberQuestionButPlanOnlyAsksNone() {
        var unknown = new GroupBrief(GroupQuestions.PLAN_AND_LEADER, List.of("press_main_target", "surround_target"),
                1L, true);
        assertTrue(unknown.asksMember(MINION));
        assertTrue(new GroupBrief(GroupQuestions.LEADER_ONLY, List.of(), 1L, true).asksMember(MINION));
        assertFalse(new GroupBrief(GroupQuestions.PLAN_ONLY, List.of(), 1L, true).asksMember(MINION));
    }

    private static GroupBrief brief(GroupQuestions questions) {
        var candidates = questions.asksPlan() ? List.of("press_main_target", "surround_target") : List.<String>of();
        return new GroupBrief(questions, candidates, 1L, true, Optional.of(LEADER));
    }
}
