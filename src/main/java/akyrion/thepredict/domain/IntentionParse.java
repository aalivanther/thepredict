package akyrion.thepredict.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of reading an {@code Intention} line (M19.1): {@link IntentionVocabulary#parse} never throws and returns one
 * of these. Immutable.
 */
public sealed interface IntentionParse {
    /**
     * A usable line.
     *
     * @param draft what the line says
     * @param warnings ignored primitives and aliases, in line order
     * @param answer the {@code ANSWER_} primitive, empty when the line has none (M23.2)
     */
    record Parsed(IntentionDraft draft, List<IntentionIssue> warnings, Optional<TellAnswer> answer)
            implements IntentionParse {
        /**
         * Validates the result and copies the list.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Parsed {
            Objects.requireNonNull(draft, "draft");
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            Objects.requireNonNull(answer, "answer");
        }

        /**
         * A line with no answer (rule W5).
         *
         * @param draft what the line says
         * @param warnings ignored primitives
         */
        public Parsed(IntentionDraft draft, List<IntentionIssue> warnings) {
            this(draft, warnings, Optional.empty());
        }
    }

    /**
     * A line that cannot be used: the choice falls back to the {@code Purpose} of the profile or stays untyped.
     *
     * @param errors why the line is invalid, at least one
     * @param warnings ignored primitives and aliases, in line order
     */
    record Invalid(List<IntentionIssue> errors, List<IntentionIssue> warnings) implements IntentionParse {
        /**
         * Validates the result and copies the lists.
         *
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if there is no error
         */
        public Invalid {
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("an invalid line needs at least one error");
            }
        }
    }

    /** The skill has no {@code Intention} line, or it is empty. */
    record Absent() implements IntentionParse {
    }
}
