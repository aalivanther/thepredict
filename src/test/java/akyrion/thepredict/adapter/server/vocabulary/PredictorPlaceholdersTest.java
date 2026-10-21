package akyrion.thepredict.adapter.server.vocabulary;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.adapter.server.vocabulary.PredictorPlaceholders.Field;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentView;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.IntentBoard;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.caster;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.clockAt;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.entity;
import static akyrion.thepredict.adapter.server.vocabulary.VocabularyFixtures.placeholderMeta;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorPlaceholdersTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final IntentView HOLDING = new IntentView(0, ControlLevel.LOCAL, Movement.KEEP_DISTANCE,
            new TacticId("debug_keep_distance"), 0.756f, 100L);

    private static IntentView withConfidence(float confidence) {
        return new IntentView(0, ControlLevel.JEV, Movement.HOLD, new TacticId("debug_hold"), confidence, 100L);
    }

    @Test
    void namesUseTheCasterPredictorPrefix() {
        assertEquals("caster.predictor.tactic", Field.TACTIC.placeholderName());
        assertEquals("caster.predictor.movement", Field.MOVEMENT.placeholderName());
        assertEquals("caster.predictor.level", Field.LEVEL.placeholderName());
        assertEquals("caster.predictor.confidence", Field.CONFIDENCE.placeholderName());
        assertEquals("caster.predictor.state", Field.STATE.placeholderName());
        assertEquals("caster.predictor.pressure", Field.PRESSURE.placeholderName());
    }

    @Test
    void controllingViewIsFormattedInLowerCase() {
        assertEquals("debug_keep_distance", PredictorPlaceholders.format(Field.TACTIC, HOLDING, 0));
        assertEquals("keep_distance", PredictorPlaceholders.format(Field.MOVEMENT, HOLDING, 0));
        assertEquals("local", PredictorPlaceholders.format(Field.LEVEL, HOLDING, 0));
        assertEquals("0.76", PredictorPlaceholders.format(Field.CONFIDENCE, HOLDING, 0));
        assertEquals("controlled", PredictorPlaceholders.format(Field.STATE, HOLDING, 0));
    }

    @Test
    void everyLevelHasItsDocumentedText() {
        assertEquals("jev", PredictorPlaceholders.format(Field.LEVEL, withConfidence(1f), 0));
        assertEquals("alternative", PredictorPlaceholders.format(Field.LEVEL,
                new IntentView(0, ControlLevel.ALTERNATIVE, Movement.FLANK, new TacticId("flank_left"), 0.5f, 1), 0));
        assertEquals("base", PredictorPlaceholders.format(Field.LEVEL, IntentView.base(4), 0));
        assertEquals("base", PredictorPlaceholders.format(Field.STATE, IntentView.base(4), 0));
    }

    @Test
    void expiredViewReportsBaseState() {
        assertEquals("base", PredictorPlaceholders.format(Field.STATE, HOLDING, 100));
    }

    @Test
    void absentViewUsesTheDocumentedDefaults() {
        assertEquals("none", PredictorPlaceholders.format(Field.TACTIC, IntentView.ABSENT, 0));
        assertEquals("none", PredictorPlaceholders.format(Field.MOVEMENT, IntentView.ABSENT, 0));
        assertEquals("none", PredictorPlaceholders.format(Field.LEVEL, IntentView.ABSENT, 0));
        assertEquals("none", PredictorPlaceholders.format(Field.CONFIDENCE, IntentView.ABSENT, 0));
        assertEquals("none", PredictorPlaceholders.format(Field.STATE, IntentView.ABSENT, 0));
        assertEquals("none", PredictorPlaceholders.format(Field.PRESSURE, IntentView.ABSENT, 0));
        assertEquals("4", PredictorPlaceholders.format(Field.PRESSURE, HOLDING.withPressure(4), 0));
    }

    @Test
    void confidenceBoundariesRoundHalfUp() {
        assertEquals("0.00", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(0f), 0));
        assertEquals("0.01", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(0.01f), 0));
        assertEquals("0.13", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(0.125f), 0));
        assertEquals("0.10", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(0.1f), 0));
        assertEquals("1.00", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(0.995f), 0));
        assertEquals("1.00", PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(1f), 0));
    }

    @Test
    void negativeZeroConfidenceShowsTheSameTextAsFormattingTheStoredValue() {
        var view = withConfidence(-0.0f);
        assertEquals("0.00", PredictorPlaceholders.format(Field.CONFIDENCE, view, 0));
        assertEquals(String.format(Locale.ROOT, "%.2f", view.confidence()),
                PredictorPlaceholders.format(Field.CONFIDENCE, view, 0));
    }

    @Test
    void confidenceTextMatchesTwoDecimalFormattingForEveryTestedFloat() {
        for (var step = 0; step <= 100_000; step++) {
            assertSameAsFormat(step / 100_000f);
        }
        for (var hundredths = 0; hundredths < 100; hundredths++) {
            var half = (hundredths + 0.5f) / 100f;
            assertSameAsFormat(half);
            assertSameAsFormat(Math.nextUp(half));
            assertSameAsFormat(Math.nextDown(half));
        }
    }

    @Test
    void resolveReadsTheCasterViewAndTreatsMissingPartsAsNoSession() {
        var board = new IntentBoard();
        var placeholders = new PredictorPlaceholders(board, clockAt(0));
        board.publish(new MobKey(ENTITY, 0), HOLDING);
        assertEquals("debug_keep_distance", placeholders.resolve(Field.TACTIC, placeholderMeta(caster(entity(ENTITY)))));
        assertEquals("controlled", placeholders.resolve(Field.STATE, placeholderMeta(caster(entity(ENTITY)))));
        assertEquals("none", placeholders.resolve(Field.STATE, placeholderMeta(null)));
        assertEquals("none", placeholders.resolve(Field.TACTIC, placeholderMeta(caster(null))));
        assertEquals("none", placeholders.resolve(Field.CONFIDENCE, placeholderMeta(caster(entity(null)))));
        assertEquals("none", placeholders.resolve(Field.LEVEL, null));
    }

    @Test
    void everyFieldWithoutASessionShowsNoneWhileABaseSessionStillShowsItsLevelAndConfidence() {
        for (var field : Field.values()) {
            assertEquals(PredictorPlaceholders.NONE, PredictorPlaceholders.format(field, IntentView.ABSENT, 0, 7L),
                    field.name());
        }
        assertEquals("base", PredictorPlaceholders.format(Field.LEVEL, IntentView.base(2), 0));
        assertEquals("0.00", PredictorPlaceholders.format(Field.CONFIDENCE, IntentView.base(2), 0));
    }

    @Test
    void formattingAllocatesNothing() {
        var fields = Field.values();
        var bytes = AllocationProbe.bytesAllocatedBy(() -> {
            for (var field : fields) {
                PredictorPlaceholders.format(field, HOLDING, 0);
            }
        });
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }

    private static void assertSameAsFormat(float confidence) {
        var expected = String.format(Locale.ROOT, "%.2f", confidence);
        assertEquals(expected, PredictorPlaceholders.format(Field.CONFIDENCE, withConfidence(confidence), 0),
                () -> "confidence " + confidence);
    }
}
