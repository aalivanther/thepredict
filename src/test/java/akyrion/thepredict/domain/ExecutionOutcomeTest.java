package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOutcomeTest {
    @Test
    void onlyNotEligibleAndDeferredInvulnerableAreNotFinal() {
        for (var outcome : ExecutionOutcome.values()) {
            var retryable = outcome == ExecutionOutcome.NOT_ELIGIBLE || outcome == ExecutionOutcome.DEFERRED_INVULNERABLE;
            if (retryable) {
                assertFalse(outcome.isFinal(), outcome.name());
            } else {
                assertTrue(outcome.isFinal(), outcome.name());
            }
        }
    }
}
