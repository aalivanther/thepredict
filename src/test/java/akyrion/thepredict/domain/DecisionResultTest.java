package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.answer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionResultTest {
    @Test
    void resultIsSealedWithExactlyTwoVariants() {
        assertTrue(DecisionResult.class.isSealed());
        assertEquals(Set.of(DecisionResult.Answered.class, DecisionResult.Failed.class),
                Set.of(DecisionResult.class.getPermittedSubclasses()));
    }

    @Test
    void answeredCopiesAnswersAndValidatesFields() {
        var answers = new HashMap<>(Map.of(QuestionKey.of(0), answer(0.8, "hold_ground", 1.0)));
        var answered = new DecisionResult.Answered(answers, "jev-1", 120);
        answers.clear();
        assertEquals(1, answered.answers().size());
        assertThrows(IllegalArgumentException.class, () -> new DecisionResult.Answered(Map.of(), " ", 0));
        assertThrows(IllegalArgumentException.class, () -> new DecisionResult.Answered(Map.of(), "jev-1", -1));
    }

    @Test
    void failedRejectsNegativeRetryAfter() {
        assertEquals(FailureKind.RATE_LIMITED, new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(3)), "retry later").kind());
        assertThrows(IllegalArgumentException.class, () -> new DecisionResult.Failed(FailureKind.TIMEOUT,
                Optional.of(Duration.ofSeconds(-1)), "late"));
    }

    @Test
    void outcomeUnionsAreSealed() {
        assertEquals(3, IntentOutcome.class.getPermittedSubclasses().length);
        assertEquals(3, InstallResult.class.getPermittedSubclasses().length);
    }

    @Test
    void fitsAreFiniteWithinTheUnitIntervalAndEmptyByDefault() {
        var answers = Map.of(QuestionKey.of(0), answer(0.8, "press_attack", 0.7, "keep_range", 0.3));
        assertEquals(Map.of(), new DecisionResult.Answered(answers, "jev-1.13.0", 10).fits());
        var fits = Map.of(QuestionKey.of(1), 0.0, QuestionKey.of(2), 1.0);
        assertEquals(fits, new DecisionResult.Answered(answers, "jev-1.13.0", 10, fits).fits());
        for (var invalid : new double[] {-0.01, 1.01, Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> new DecisionResult.Answered(answers, "jev-1.13.0", 10,
                    Map.of(QuestionKey.of(1), invalid)));
        }
    }

    @Test
    void theGroupAnswerIsAttachedWithoutAnOptionalAndANullIsRejected() {
        var answers = Map.of(QuestionKey.of(0), answer(0.8, "press_attack", 0.7, "keep_range", 0.3));
        var answered = new DecisionResult.Answered(answers, "jev-1.13.0", 10);
        var plan = new GroupAnswer(GroupPlan.SURROUND_TARGET, 1);
        assertEquals(java.util.Optional.of(plan), answered.withGroup(plan).group());
        assertEquals(1.0, plan.confidence(), 0.0, "an answer without a parsed confidence is fully trusted");
        assertThrows(IllegalArgumentException.class, () -> new GroupAnswer(GroupPlan.SURROUND_TARGET, 1, 1.5));
        assertThrows(NullPointerException.class, () -> new DecisionResult.Answered(answers, "jev-1.13.0", 10, Map.of(),
                AnswerSource.LIVE, null));
    }
}
