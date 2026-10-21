package akyrion.thepredict.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One question of a request: which tactic a mob should use in a situation. Immutable.
 *
 * <p>A question with a single option is never sent: it is resolved locally. {@code keep} is the tactic of the current
 * intent of the mob when it is among the options: the deduplication and the cap of M16.1 never remove it, so the
 * hysteresis of M10.5 still compares against it.
 *
 * @param key opaque key of the question
 * @param mob mob asked about
 * @param situation situation of the question
 * @param options eligible tactics, from {@value #MIN_OPTIONS} to {@value #MAX_OPTIONS}, without repetitions
 * @param keep the current tactic of the mob, which belongs to {@code options}; empty when it is not offered
 */
public record QuestionSpec(QuestionKey key, MobKey mob, Situation situation, List<TacticId> options,
                           Optional<TacticId> keep) {
    /** Fewest options a question may carry. */
    public static final int MIN_OPTIONS = 2;
    /** Most options a question may carry. */
    public static final int MAX_OPTIONS = 255;

    /**
     * Validates the question and copies its options.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if the options are too few, too many or repeated, or {@code keep} is not one of
     *         them
     */
    public QuestionSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(situation, "situation");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        Objects.requireNonNull(keep, "keep");
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("options must have between " + MIN_OPTIONS + " and "
                    + MAX_OPTIONS + " entries, had " + options.size());
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("options must not repeat a tactic in question " + key);
        }
        if (keep.isPresent() && !options.contains(keep.orElseThrow())) {
            throw new IllegalArgumentException("keep " + keep.orElseThrow() + " is not an option of question " + key);
        }
    }

    /**
     * Creates a question without a tactic to keep (rule W5: the form of every question before M16). Production code
     * passes the tactic to keep; this constructor stays only while tests use it.
     *
     * @param key opaque key of the question
     * @param mob mob asked about
     * @param situation situation of the question
     * @param options eligible tactics
     * @throws IllegalArgumentException if the options are too few, too many or repeated
     */
    public QuestionSpec(QuestionKey key, MobKey mob, Situation situation, List<TacticId> options) {
        this(key, mob, situation, options, Optional.empty());
    }

    /**
     * Returns the same question with other options, keeping {@code keep} only when it is still one of them; the
     * preparation of M16.1 uses it for the options that are really sent.
     *
     * @param sent the options sent
     * @return the copy
     * @throws IllegalArgumentException if the options are invalid
     */
    public QuestionSpec withOptions(List<TacticId> sent) {
        return new QuestionSpec(key, mob, situation, sent, keep.filter(sent::contains));
    }
}
