package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.CalibrationRule.Repetition;
import akyrion.thepredict.adapter.jev.CalibrationRule.Scenario;
import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationRuleTest {
    private static final TacticId GOOD = new TacticId("good_move");
    private static final TacticId BAD = new TacticId("bad_move");
    private static final TacticId OTHER = new TacticId("other_move");

    private static Scenario scenario(String id, double confidence, TacticId best) {
        return new Scenario(id, true, false, Set.of(GOOD), Set.of(BAD), List.of(new Repetition(best, confidence,
                Map.of(best, 0.7))));
    }

    private static List<Scenario> bank(int count, double confidence, TacticId best) {
        var scenarios = new ArrayList<Scenario>();
        for (var index = 0; index < count; index++) {
            scenarios.add(scenario(best.value() + "-" + confidence + "-" + index, confidence, best));
        }
        return scenarios;
    }

    @Test
    void theWilsonBoundsMatchTheExamplesOfTheDesign() {
        assertTrue(CalibrationRule.wilsonLower(28, 30) >= 0.75, "28/30 meets");
        assertEquals(0.786, CalibrationRule.wilsonLower(28, 30), 0.005);
        assertTrue(CalibrationRule.wilsonLower(27, 30) < 0.75, "27/30 does not");
        assertEquals(0.744, CalibrationRule.wilsonLower(27, 30), 0.005);
        assertEquals(0.77, CalibrationRule.wilsonLower(31, 34), 0.01);
        assertEquals(0.77, CalibrationRule.wilsonLower(36, 40), 0.01);
        assertEquals(0.85, CalibrationRule.wilsonLower(33, 34), 0.01);
        assertTrue(CalibrationRule.wilsonLower(22, 24) < 0.75);
        assertEquals(0.0, CalibrationRule.wilsonLower(0, 10), 1e-12);
        assertEquals(0.0, CalibrationRule.wilsonLower(0, 0), 1e-12);
        assertTrue(CalibrationRule.wilsonLower(10, 10) > 0.72 && CalibrationRule.wilsonLower(10, 10) < 1.0);
    }

    @Test
    void theLowestThresholdThatMeetsEveryConditionIsChosen() {
        var scenarios = new ArrayList<Scenario>();
        scenarios.addAll(bank(34, 0.8, GOOD));
        scenarios.addAll(bank(6, 0.3, BAD));
        var result = CalibrationRule.apply(scenarios);
        assertEquals(40, result.eligible());
        assertEquals(0.35, result.chosen().orElseThrow(), 1e-9);
        var at20 = result.rows().getFirst();
        assertEquals(40, at20.kept());
        assertEquals(6, at20.forbidden());
        assertFalse(at20.meets());
    }

    @Test
    void aNonMonotonicCurveStillChoosesTheLowestValidThreshold() {
        var scenarios = new ArrayList<Scenario>();
        scenarios.addAll(bank(30, 0.9, GOOD));
        scenarios.addAll(bank(4, 0.62, BAD));
        scenarios.addAll(bank(8, 0.45, GOOD));
        var result = CalibrationRule.apply(scenarios);
        assertFalse(result.rows().get(4).meets(), "0.40 keeps the four forbidden");
        assertEquals(0.65, result.chosen().orElseThrow(), 1e-9);
    }

    @Test
    void withoutAValidThresholdNothingIsChosenAndFewerThanThirtyScenariosNeverMeet() {
        assertTrue(CalibrationRule.apply(bank(29, 0.9, GOOD)).chosen().isEmpty(), "n < 30");
        var mixed = new ArrayList<Scenario>();
        mixed.addAll(bank(30, 0.9, GOOD));
        mixed.addAll(bank(5, 0.9, OTHER));
        assertTrue(CalibrationRule.apply(mixed).chosen().isEmpty(), "30/35 acceptable is below 90 %");
    }

    @Test
    void onlyReviewedAndInformativeScenariosCount() {
        var scenarios = new ArrayList<Scenario>(bank(30, 0.9, GOOD));
        scenarios.add(new Scenario("proposed", false, false, Set.of(GOOD), Set.of(BAD), List.of(
                new Repetition(BAD, 0.9, Map.of(BAD, 0.9)))));
        scenarios.add(new Scenario("uninformative", true, true, Set.of(GOOD, BAD), Set.of(), List.of(
                new Repetition(BAD, 0.9, Map.of(BAD, 0.9)))));
        var result = CalibrationRule.apply(scenarios);
        assertEquals(30, result.eligible());
        assertEquals(0.20, result.chosen().orElseThrow(), 1e-9);
    }

    @Test
    void repetitionsAreReducedToTheModeWithTheMeanConfidence() {
        var aggregate = CalibrationRule.aggregate(new Scenario("s", true, false, Set.of(GOOD), Set.of(BAD), List.of(
                new Repetition(GOOD, 0.6, Map.of(GOOD, 0.6, BAD, 0.4)),
                new Repetition(BAD, 0.2, Map.of(GOOD, 0.3, BAD, 0.7)),
                new Repetition(GOOD, 0.7, Map.of(GOOD, 0.8, BAD, 0.2)))));
        assertEquals(GOOD, aggregate.modal());
        assertEquals(0.5, aggregate.meanConfidence(), 1e-9);
        assertEquals(1, aggregate.changes());
        assertTrue(aggregate.acceptable());
        assertFalse(aggregate.forbidden());
    }

    @Test
    void aTieOfTheModeGoesToTheHigherMeanProbability() {
        var aggregate = CalibrationRule.aggregate(new Scenario("tie", true, false, Set.of(GOOD), Set.of(BAD), List.of(
                new Repetition(GOOD, 0.5, Map.of(GOOD, 0.51, BAD, 0.49)),
                new Repetition(BAD, 0.5, Map.of(GOOD, 0.2, BAD, 0.8)))));
        assertEquals(BAD, aggregate.modal());
        assertTrue(aggregate.forbidden());
    }

    @Test
    void theForbiddenLimitIsOneAndTwoPercentFromFiftyScenarios() {
        assertEquals(1, CalibrationRule.allowedForbidden(30));
        assertEquals(1, CalibrationRule.allowedForbidden(49));
        assertEquals(1, CalibrationRule.allowedForbidden(50));
        assertEquals(2, CalibrationRule.allowedForbidden(100));
    }
}
