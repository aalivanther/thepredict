package akyrion.thepredict.adapter.server.goals;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldGoalTest {
    private static IntentView view(int generation, Movement movement) {
        return new IntentView(generation, ControlLevel.LOCAL, movement, new TacticId("debug_" + movement.name().toLowerCase(Locale.ROOT)), 1f, 100L);
    }

    @Test
    void holdsWhileTheViewOfItsGenerationControlsWhateverTheMovement() {
        assertTrue(HoldGoal.shouldHold(view(0, Movement.HOLD), 0, 0));
        assertTrue(HoldGoal.shouldHold(view(0, Movement.RETREAT), 0, 0));
    }

    @Test
    void doesNotHoldForAnotherGenerationAnExpiredViewOrNoView() {
        assertFalse(HoldGoal.shouldHold(view(1, Movement.HOLD), 0, 0));
        assertFalse(HoldGoal.shouldHold(view(0, Movement.HOLD), 0, 100));
        assertFalse(HoldGoal.shouldHold(IntentView.ABSENT, 0, 0));
        assertFalse(HoldGoal.shouldHold(IntentView.base(0), 0, 0));
    }
}
