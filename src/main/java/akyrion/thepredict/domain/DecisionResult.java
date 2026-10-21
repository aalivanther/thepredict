package akyrion.thepredict.domain;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Outcome of one remote decision: answers or a typed failure (rule C7). Immutable. */
public sealed interface DecisionResult permits DecisionResult.Answered, DecisionResult.Failed {
    /**
     * The service answered.
     *
     * @param answers answer per question key
     * @param effectiveModel model that produced the answers; not blank
     * @param inputTokens input tokens charged, {@code >= 0}
     * @param fits probability of {@code true} of each fit check sent (M16.5), by its key; finite within
     *        {@code [0, 1]}; empty without fit checks
     * @param source whether the answer was live or served from the cache (M21.7)
     * @param group the plan chosen for {@code g0}, empty when the request did not ask (M25)
     */
    record Answered(Map<QuestionKey, Answer> answers, String effectiveModel,
                    int inputTokens, Map<QuestionKey, Double> fits, AnswerSource source,
                    Optional<GroupAnswer> group) implements DecisionResult {
        /**
         * Validates the result and copies the answers.
         *
         * @throws NullPointerException if a component, key or value is {@code null}
         * @throws IllegalArgumentException if the model is blank, the token count negative or a fit out of range
         */
        public Answered {
            answers = Map.copyOf(Objects.requireNonNull(answers, "answers"));
            DomainChecks.requireText(effectiveModel, "effectiveModel");
            if (inputTokens < 0) {
                throw new IllegalArgumentException("inputTokens must be >= 0, was " + inputTokens);
            }
            fits = Map.copyOf(Objects.requireNonNull(fits, "fits"));
            fits.values().forEach(fit -> DomainChecks.requireUnit(fit, "fits"));
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(group, "group");
        }

        /**
         * An answer from before {@code g0} (rule W5). The group answer stays empty.
         *
         * @param answers answer per question key
         * @param effectiveModel model that produced the answers
         * @param inputTokens input tokens charged
         * @param fits fit of each question
         * @param source whether the answer was live or cached
         */
        public Answered(Map<QuestionKey, Answer> answers, String effectiveModel, int inputTokens,
                        Map<QuestionKey, Double> fits, AnswerSource source) {
            this(answers, effectiveModel, inputTokens, fits, source, Optional.empty());
        }

        /**
         * A copy served from the answer cache: no tokens and {@link AnswerSource#CACHE}.
         *
         * @return the cached copy
         */
        public Answered fromCache() {
            return new Answered(answers, effectiveModel, 0, fits, AnswerSource.CACHE, group);
        }

        /**
         * A copy that keeps the tactic answers and attaches the plan chosen for {@code g0} (rule C4: no
         * {@code Optional} parameter).
         *
         * @param group the plan answer
         * @return the copy
         */
        public Answered withGroup(GroupAnswer group) {
            return new Answered(answers, effectiveModel, inputTokens, fits, source,
                    Optional.of(Objects.requireNonNull(group, "group")));
        }

        /**
         * Creates an answer with the parsed fit of each question.
         *
         * @param answers answer per question key
         * @param effectiveModel model that produced the answers
         * @param inputTokens input tokens charged
         * @param fits fit of each question, from 0 to 1
         * @throws IllegalArgumentException if the model is blank or the token count negative
         */
        public Answered(Map<QuestionKey, Answer> answers, String effectiveModel, int inputTokens,
                        Map<QuestionKey, Double> fits) {
            this(answers, effectiveModel, inputTokens, fits, AnswerSource.LIVE);
        }

        /**
         * Creates an answer without fit checks (rule W5: the form of every answer before M16). Production code passes
         * the parsed fits; this constructor stays only while tests use it.
         *
         * @param answers answer per question key
         * @param effectiveModel model that produced the answers
         * @param inputTokens input tokens charged
         * @throws IllegalArgumentException if the model is blank or the token count negative
         */
        public Answered(Map<QuestionKey, Answer> answers, String effectiveModel, int inputTokens) {
            this(answers, effectiveModel, inputTokens, Map.of(), AnswerSource.LIVE);
        }
    }

    /**
     * The decision failed.
     *
     * @param kind failure cause
     * @param retryAfter delay the service asked for, if any; never negative
     * @param detail short explanation without keys, headers or bodies (rule C8)
     */
    record Failed(FailureKind kind, Optional<Duration> retryAfter, String detail) implements DecisionResult {
        /**
         * Validates the failure.
         *
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if {@code retryAfter} is negative
         */
        public Failed {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(retryAfter, "retryAfter");
            Objects.requireNonNull(detail, "detail");
            if (retryAfter.isPresent() && retryAfter.orElseThrow().isNegative()) {
                throw new IllegalArgumentException("retryAfter must not be negative, was " + retryAfter.orElseThrow());
            }
        }
    }
}
