package akyrion.thepredict.domain;

import java.util.Objects;

/**
 * Result of offering an answer to {@link IntentPolicy}. A rejection never invalidates the current
 * intent: it keeps running until it expires. Immutable.
 */
public sealed interface IntentOutcome permits IntentOutcome.Adopted, IntentOutcome.Renewed,
        IntentOutcome.Rejected {
    /**
     * A new tactic replaces the current one, or there was none.
     *
     * @param intent the new intent
     */
    record Adopted(TacticalIntent intent) implements IntentOutcome {
        /**
         * Validates the outcome.
         *
         * @throws NullPointerException if {@code intent} is {@code null}
         */
        public Adopted {
            Objects.requireNonNull(intent, "intent");
        }
    }

    /**
     * The current tactic continues with a new deadline, alternatives and contingencies.
     *
     * @param intent the renewed intent
     */
    record Renewed(TacticalIntent intent) implements IntentOutcome {
        /**
         * Validates the outcome.
         *
         * @throws NullPointerException if {@code intent} is {@code null}
         */
        public Renewed {
            Objects.requireNonNull(intent, "intent");
        }
    }

    /**
     * The answer is not applied.
     *
     * @param reason why the answer was rejected
     */
    record Rejected(FallbackReason reason) implements IntentOutcome {
        /**
         * Validates the outcome.
         *
         * @throws NullPointerException if {@code reason} is {@code null}
         */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
