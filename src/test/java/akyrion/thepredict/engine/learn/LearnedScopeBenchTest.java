package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.LearnedScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules A16 and W8: a learned table acts against players only with a PASS verdict and the {@code all} scope. */
class LearnedScopeBenchTest {
    @Test
    void theRuleNeedsPassAndScopeAllWithPlayersAndAnyPassWithout() {
        assertTrue(LearnedScope.allows("bench", "PASS_SIM_ONLY", false));
        assertTrue(LearnedScope.allows("bench", "PASS", false));
        assertFalse(LearnedScope.allows("bench", "PASS", true));
        assertFalse(LearnedScope.allows("all", "PASS_SIM_ONLY", true));
        assertTrue(LearnedScope.allows("all", "PASS", true));
        assertFalse(LearnedScope.allows("all", "FAIL", false));
        assertFalse(LearnedScope.allows("all", "PENDING", false));
    }

    @Test
    void aTableStagedWithoutItsVerdictNeverActsWithAPlayerPresent() {
        var learning = new SessionLearning();
        learning.stage(ValueTable.empty(), LearnedArtifacts.State.ACTIVE, 1, 7L);
        learning.activateIfReady();
        assertTrue(learning.tableActive());
        learning.audience(true);
        assertFalse(learning.tableActive());
        learning.audience(false);
        assertTrue(learning.tableActive());
        var joined = new SessionLearning();
        joined.activate(ValueTable.empty(), 1, 7L);
        joined.playerJoined(true);
        assertFalse(joined.tableActive(), "a player who joined counts until the audience is reported");
    }

    @Test
    void theVerdictAndTheScopeDecideAgainstPlayers() {
        assertTrue(active("PASS", "all", true));
        assertFalse(active("PASS", "bench", true));
        assertFalse(active("PASS_SIM_ONLY", "all", true));
        assertTrue(active("PASS_SIM_ONLY", "bench", false));
        assertFalse(active("FAIL", "all", false));
        var pending = new SessionLearning();
        pending.stage(ValueTable.empty(), LearnedArtifacts.State.PENDING_VERDICT, "PASS", "all", 1, 7L);
        pending.activateIfReady();
        assertFalse(pending.tableActive());
    }

    @Test
    void anArmedExplorationIsDroppedWhileAPlayerTakesPartUnlessTraining() {
        var arm = new ExploreArm(9L, 0, 3, 0.1, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX, false);
        var learning = new SessionLearning();
        learning.armExplore(arm);
        assertEquals(arm, learning.explore());
        learning.audience(true);
        assertEquals(ExploreArm.NONE, learning.explore());
        learning.markTraining(true);
        assertEquals(arm, learning.explore());
        var joined = new SessionLearning();
        joined.armExplore(arm);
        joined.playerJoined(true);
        assertEquals(ExploreArm.NONE, joined.explore());
    }

    private static boolean active(String verdict, String scope, boolean players) {
        var learning = new SessionLearning();
        learning.stage(ValueTable.empty(), LearnedArtifacts.State.ACTIVE, verdict, scope, 1, 7L);
        learning.activateIfReady();
        learning.audience(players);
        return learning.tableActive();
    }
}
