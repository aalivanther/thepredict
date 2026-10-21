package akyrion.thepredict.engine;

import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.CalibrationTarget;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.OptionsBand;
import akyrion.thepredict.domain.learn.QuestionKind;
import akyrion.thepredict.engine.learn.ConfidenceCalibrator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A live model that is not the artifact model keeps the raw floor. */
class KevCalibrationRequiredTest {
    @Test
    void aDifferentLiveModelIsCalibrationStale() {
        var calibrator = ConfidenceCalibrator.identity("kev-0.8b");
        var key = new CalibrationKey("kev-0.8b", QuestionDetail.FULL, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE,
                OptionsBand.TWO);
        var result = calibrator.threshold(key, "kev-4b", 0.5, true, 0.5);
        assertEquals(0.5, result.rawThreshold());
        assertEquals(Optional.of(LearnCause.CALIBRATION_STALE), result.cause());
        var same = calibrator.threshold(key, "kev-0.8b", 0.5, false, 0.4);
        assertTrue(same.cause().isEmpty() || same.cause().get() != LearnCause.CALIBRATION_STALE);
    }
}
