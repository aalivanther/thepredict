package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code predictorstance} matches a published stance, enraged, and never an unknown name (M23.8). */
class PredictorStanceConditionTest {
    @Test
    void matchesTheNamedStanceInAnyCase() {
        var view = view(CombatStance.WINDUP, false);
        var mask = 1 << CombatStance.WINDUP.ordinal();
        assertTrue(PredictorStanceCondition.matches(view, mask, false, true));
        assertFalse(PredictorStanceCondition.matches(view(CombatStance.GUARDED, false), mask, false, true));
        var condition = new PredictorStanceCondition("WindUp,guarded", new IntentBoard(), clockAt(0));
        assertTrue(condition.isValid());
    }

    @Test
    void enragedMatchesEvenWhenTheStanceDoesNot() {
        var view = view(CombatStance.NEUTRAL, true);
        assertTrue(PredictorStanceCondition.matches(view, 0, true, true));
        assertFalse(PredictorStanceCondition.matches(view(CombatStance.NEUTRAL, false), 0, true, true));
        assertTrue(new PredictorStanceCondition("enraged", new IntentBoard(), clockAt(0)).isValid());
    }

    @Test
    void anUnknownNameIsAlwaysFalse() {
        var view = view(CombatStance.WINDUP, true);
        assertFalse(PredictorStanceCondition.matches(view, 1 << CombatStance.WINDUP.ordinal(), true, false));
        var condition = new PredictorStanceCondition("flying", new IntentBoard(), clockAt(0));
        assertFalse(condition.isValid());
        assertFalse(PredictorStanceCondition.matches(view, 0, false, false));
    }

    private static IntentView view(CombatStance stance, boolean enraged) {
        return new IntentView(0, ControlLevel.JEV, Movement.HOLD, new TacticId("hold_ground"), 0.8f, 100L,
                MicroBehaviour.NATIVE, MicroSide.LEFT, -1, stance, enraged, (byte) -1, Optional.empty(), -1L);
    }
}
