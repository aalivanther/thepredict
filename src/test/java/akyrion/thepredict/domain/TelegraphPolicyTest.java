package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Minima, lead and when a tell opens (M23.4). Entropy never applies a minimum. */
class TelegraphPolicyTest {
    @Test
    void theMinimumTableIsBasePlusAnswerPlusMargin() {
        var settings = TelegraphSettings.DEFAULT;
        assertEquals(0, settings.minimumTicks(DangerClass.NONE, TellAnswer.BLOCK));
        assertEquals(0, settings.minimumTicks(DangerClass.LIGHT, TellAnswer.INTERRUPT));
        assertEquals(9, settings.minimumTicks(DangerClass.MEDIUM, TellAnswer.BLOCK));
        assertEquals(9, settings.minimumTicks(DangerClass.MEDIUM, TellAnswer.DODGE));
        assertEquals(12, settings.minimumTicks(DangerClass.MEDIUM, TellAnswer.BACK_OFF));
        assertEquals(12, settings.minimumTicks(DangerClass.MEDIUM, TellAnswer.BREAK_SIGHT));
        assertEquals(12, settings.minimumTicks(DangerClass.MEDIUM, TellAnswer.INTERRUPT));
        assertEquals(17, settings.minimumTicks(DangerClass.HEAVY, TellAnswer.DODGE));
        assertEquals(20, settings.minimumTicks(DangerClass.HEAVY, TellAnswer.BACK_OFF));
        assertEquals(25, settings.minimumTicks(DangerClass.SEVERE, TellAnswer.BLOCK));
        assertEquals(28, settings.minimumTicks(DangerClass.SEVERE, TellAnswer.BACK_OFF));
        assertTrue(settings.heavy() >= 10);
    }

    @Test
    void leadIsTheShownLengthMinusTheAuthorWindUp() {
        var settings = TelegraphSettings.DEFAULT;
        int shown = TelegraphPolicy.telegraphTicks(10, DangerClass.SEVERE, TellAnswer.BACK_OFF, true, settings);
        assertEquals(28, shown);
        assertEquals(18, TelegraphPolicy.leadTicks(shown, 10));
        assertEquals(10, TelegraphPolicy.telegraphTicks(10, DangerClass.SEVERE, TellAnswer.BACK_OFF, false, settings));
        assertEquals(0, TelegraphPolicy.leadTicks(10, 10));
        assertFalse(TelegraphPolicy.minimumApplies(true, true, settings));
        assertFalse(TelegraphPolicy.minimumApplies(false, false, settings));
        assertTrue(TelegraphPolicy.minimumApplies(true, false, settings));
    }

    @Test
    void whiffRecoveryUsesTheGlobalFloor() {
        assertEquals(10, TelegraphPolicy.whiffRecovery(0, TelegraphSettings.DEFAULT));
        assertEquals(20, TelegraphPolicy.whiffRecovery(20, TelegraphSettings.DEFAULT));
    }
}
