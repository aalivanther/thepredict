package akyrion.thepredict.adapter.server.patterns;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.PatternSettings;
import akyrion.thepredict.engine.EnemyModel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrikeClassifierTest {
    @Test
    void aCriticalIsACriticalWithOrWithoutSprint() {
        assertEquals(EnemyAction.CRIT_STRIKE, StrikeClassifier.classify(true, false, 1.0, false));
        assertEquals(EnemyAction.CRIT_STRIKE, StrikeClassifier.classify(true, true, 1.0, false));
    }

    @Test
    void chargeAtTheBoundaryAndSprintNeedMoreThanNineTenths() {
        assertEquals(EnemyAction.STRIKE_EARLY, StrikeClassifier.classify(false, false, 0.9, false));
        assertEquals(EnemyAction.STRIKE, StrikeClassifier.classify(false, false, 0.91, false));
        assertEquals(EnemyAction.STRIKE_EARLY, StrikeClassifier.classify(false, true, 0.85, false));
        assertEquals(EnemyAction.SPRINT_STRIKE, StrikeClassifier.classify(false, true, 0.95, false));
    }

    @Test
    void aSpearNeverCountsAsACritical() {
        assertEquals(EnemyAction.STRIKE, StrikeClassifier.classify(true, true, 1.0, true));
        assertEquals(EnemyAction.STRIKE_EARLY, StrikeClassifier.classify(true, true, 0.2, true));
    }

    @Test
    void learnDoesNotCountASpearOfChargeAtMostNineTenths() {
        var spear = StrikeClassifier.classify(false, false, StrikeClassifier.FULL_CHARGE, true);
        assertEquals(EnemyAction.STRIKE_EARLY, spear);
        var model = new EnemyModel(new UUID(0, 26), 32, 2);
        model.learn(spear, PatternSettings.DEFAULT, true);
        assertEquals(0, model.tracker(EnemyPattern.SPAMS_CLICKS).evidence().trials());
        model.learn(EnemyAction.CRIT_STRIKE, PatternSettings.DEFAULT, true);
        assertEquals(0, model.tracker(EnemyPattern.CRIT_JUMPER).evidence().trials());
        model.learn(spear, PatternSettings.DEFAULT, false);
        assertEquals(1, model.tracker(EnemyPattern.SPAMS_CLICKS).evidence().trials());
        assertEquals(1, model.tracker(EnemyPattern.CRIT_JUMPER).evidence().trials(),
                "an early hit without a spear is a failed trial of the critical habit (M26.3)");
        assertEquals(0, model.tracker(EnemyPattern.PREDICTABLE_STRAFE_LEFT).evidence().trials(),
                "a hit is no trial of a movement habit");
    }
}
