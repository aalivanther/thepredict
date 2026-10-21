package akyrion.thepredict.domain;

import akyrion.thepredict.domain.FitGate.FitThresholds;
import akyrion.thepredict.domain.FitGate.FitVerdict;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.answer;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FitGateTest {
    private static final FitThresholds T = new FitThresholds(0.2, 0.7);

    private static FitVerdict judge(double confidence, double press, double keep, double hold) {
        return FitGate.judge(answer(confidence, "press", 0.5, "keep", 0.3, "hold", 0.2),
                Map.of(tactic("press"), press, tactic("keep"), keep, tactic("hold"), hold), 0.5, T);
    }

    @Test
    void theMostProbableTacticWithEnoughConfidenceAndNoVetoIsAdoptedAtJev() {
        assertEquals(new FitVerdict.Adopt(tactic("press"), ControlLevel.JEV, false), judge(0.6, 0.5, 0.5, 0.5));
    }

    @Test
    void aVetoOfTheFirstAdoptsTheNextSurvivorAsAnAlternative() {
        assertEquals(new FitVerdict.Adopt(tactic("keep"), ControlLevel.ALTERNATIVE, false), judge(0.6, 0.1, 0.5, 0.5));
    }

    @Test
    void anExcludedTacticNeverSurvivesEvenWithAVetoOfZero() {
        var answer = answer(0.6, "press", 0.5, "keep", 0.3, "hold", 0.2);
        var fits = Map.of(tactic("press"), 0.0, tactic("keep"), 0.0, tactic("hold"), 0.9);
        var zero = new FitThresholds(0.0, 0.7);
        assertEquals(new FitVerdict.Adopt(tactic("press"), ControlLevel.JEV, false), FitGate.judge(answer, fits, 0.5,
                zero), "a fit of 0 is not a veto of 0");
        assertEquals(new FitVerdict.Adopt(tactic("keep"), ControlLevel.ALTERNATIVE, false), FitGate.judge(answer, fits,
                Set.of(tactic("press")), 0.5, zero));
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE), FitGate.judge(answer, fits,
                Set.of(tactic("press"), tactic("keep"), tactic("hold")), 0.5, zero));
    }

    @Test
    void aVetoOfEveryOptionIsALowConfidenceRejection() {
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE), judge(0.9, 0.1, 0.19, 0.0));
    }

    @Test
    void aSingleRescueOfTheFirstSurvivorAdoptsItAtItsLevel() {
        assertEquals(new FitVerdict.Adopt(tactic("press"), ControlLevel.JEV, true), judge(0.3, 0.8, 0.5, 0.5));
        assertEquals(new FitVerdict.Adopt(tactic("keep"), ControlLevel.ALTERNATIVE, true), judge(0.3, 0.1, 0.7, 0.5));
    }

    @Test
    void aDoubleRescueOrARescueOfAnotherOptionIsRejected() {
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE), judge(0.3, 0.8, 0.9, 0.5));
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE), judge(0.3, 0.5, 0.9, 0.5));
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE), judge(0.3, 0.5, 0.5, 0.5));
    }

    @Test
    void anOptionWithoutAFitIsNeverVetoedNorRescues() {
        var verdict = FitGate.judge(answer(0.6, "press", 0.5, "keep", 0.5), Map.of(tactic("keep"), 0.9), 0.5, T);
        assertEquals(new FitVerdict.Adopt(tactic("keep"), ControlLevel.JEV, false), verdict);
        assertEquals(new FitVerdict.Reject(FallbackReason.LOW_CONFIDENCE),
                FitGate.judge(answer(0.3, "press", 0.6, "keep", 0.4), Map.of(), 0.5, T));
    }

    @Test
    void anAdoptionIsOnlyAtJevOrAlternativeAndTheThresholdsAreOrdered() {
        assertThrows(IllegalArgumentException.class, () -> new FitVerdict.Adopt(tactic("press"), ControlLevel.LOCAL,
                false));
        assertThrows(IllegalArgumentException.class, () -> new FitVerdict.Adopt(tactic("press"), ControlLevel.BASE,
                true));
        assertThrows(IllegalArgumentException.class, () -> new FitThresholds(0.7, 0.7));
        assertThrows(IllegalArgumentException.class, () -> new FitThresholds(-0.1, 0.7));
        assertThrows(IllegalArgumentException.class, () -> new FitThresholds(0.2, 1.1));
        assertEquals(new FitThresholds(0.2, 0.7), FitThresholds.DEFAULT);
        new FitThresholds(0.0, 1.0);
    }
}
