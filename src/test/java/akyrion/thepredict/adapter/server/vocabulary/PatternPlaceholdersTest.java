package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.IntentView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternPlaceholdersTest {
    @Test
    void patternAndForecastReadTheIntentView() {
        var view = IntentView.base(2).withPatterns(EnemyPattern.SPAMS_CLICKS.bit() | (1 << 30),
                (byte) EnemyAction.STRIKE.ordinal());
        assertEquals("spams_clicks", PredictorPlaceholders.format(PredictorPlaceholders.Field.PATTERN, view, 0L));
        assertEquals("strike", PredictorPlaceholders.format(PredictorPlaceholders.Field.FORECAST, view, 0L));
        assertEquals("none", PredictorPlaceholders.format(PredictorPlaceholders.Field.PATTERN, IntentView.ABSENT, 0L));
        assertEquals("none", PredictorPlaceholders.format(PredictorPlaceholders.Field.FORECAST, IntentView.base(1), 0L));
    }
}
