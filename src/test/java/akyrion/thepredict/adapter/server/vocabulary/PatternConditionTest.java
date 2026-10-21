package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternConditionTest {
    @Test
    void theConditionReadsOnlyThePublishedView() {
        var condition = new PredictorPatternCondition("spams_clicks", new IntentBoard(), VocabularyFixtures.clockAt(1L));
        var view = IntentView.base(1).withPatterns(EnemyPattern.SPAMS_CLICKS.bit(), (byte) -1);
        assertTrue(condition.matches(view));
        assertFalse(condition.matches(IntentView.base(1)));
        assertFalse(new PredictorPatternCondition("not_a_pattern", new IntentBoard(), VocabularyFixtures.clockAt(1L))
                .matches(view));
        assertTrue(PredictorPatternCondition.NAME.equals("predictorpattern"));
    }
}
