package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastPreferenceAnswerBitsTest {
    @Test
    void anUndeclaredAnswerDoesNotFitAndABlankMaskDoes() {
        var mask = CastPreference.answer(TellAnswer.DODGE);
        assertTrue(CastPreference.answerFits(mask, TellAnswer.DODGE.ordinal()));
        assertFalse(CastPreference.answerFits(mask, TellAnswer.BLOCK.ordinal()));
        assertFalse(CastPreference.answerFits(mask, -1));
        assertTrue(CastPreference.answerFits(0L, -1));
    }
}
