package akyrion.thepredict.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the decision client needs to ask about one encounter, independent of HTTP.
 * Immutable, so it may travel to the HTTP threads (rule A4).
 *
 * <p>Invariants: {@code facts} belong to {@code encounter}; {@code sequence >= 0}; at least one
 * question, unless the facts carry a plan question, which {@code plan_only} sends alone (M25.10, DOC-02); unique
 * question keys, shared with the fit checks (rule I3); every asked mob is part of {@code facts} and
 * has a profile; every fit check names one option of the {@code NOW} question of its mob (M16.5).
 *
 * @param encounter encounter asked about
 * @param sequence request sequence within the encounter
 * @param facts semantic state of the encounter
 * @param questions questions of the request
 * @param profiles profile of each asked mob, for the option texts
 * @param fitChecks optional {@code noul} fit checks of the {@code NOW} options (M16.5); empty by default
 */
public record DecisionRequest(EncounterId encounter, long sequence, CombatFacts facts,
                              List<QuestionSpec> questions, Map<MobKey, TacticalProfile> profiles,
                              List<FitCheck> fitChecks) {
    /**
     * Validates the request and copies its collections.
     *
     * @throws NullPointerException if a component or element is {@code null}
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public DecisionRequest {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(facts, "facts");
        questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
        profiles = Map.copyOf(Objects.requireNonNull(profiles, "profiles"));
        fitChecks = List.copyOf(Objects.requireNonNull(fitChecks, "fitChecks"));
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0, was " + sequence);
        }
        if (!facts.encounter().equals(encounter)) {
            throw new IllegalArgumentException("facts belong to encounter " + facts.encounter().value()
                    + ", not " + encounter.value());
        }
        if (questions.isEmpty() && !facts.brief().asksPlan()) {
            throw new IllegalArgumentException("questions must not be empty without a plan question");
        }
        var keys = validateQuestions(questions, facts, profiles);
        validateFitChecks(fitChecks, questions, keys);
    }

    /**
     * Creates a request without fit checks (rule W5: the form of every request before M16). Production code passes the
     * fit checks of the plan; this constructor stays only while tests use it.
     *
     * @param encounter encounter asked about
     * @param sequence request sequence within the encounter
     * @param facts semantic state of the encounter
     * @param questions questions of the request
     * @param profiles profile of each asked mob
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public DecisionRequest(EncounterId encounter, long sequence, CombatFacts facts, List<QuestionSpec> questions,
                           Map<MobKey, TacticalProfile> profiles) {
        this(encounter, sequence, facts, questions, profiles, List.of());
    }

    private static HashSet<QuestionKey> validateQuestions(List<QuestionSpec> questions, CombatFacts facts,
                                                          Map<MobKey, TacticalProfile> profiles) {
        var keys = new HashSet<QuestionKey>();
        for (var question : questions) {
            if (!keys.add(question.key())) {
                throw new IllegalArgumentException("questions repeats key " + question.key());
            }
            if (facts.mob(question.mob()).isEmpty()) {
                throw new IllegalArgumentException("mob " + question.mob() + " of question " + question.key()
                        + " is not part of facts");
            }
            if (!profiles.containsKey(question.mob())) {
                throw new IllegalArgumentException("profiles has no profile for mob " + question.mob());
            }
        }
        return keys;
    }

    private static void validateFitChecks(List<FitCheck> fitChecks, List<QuestionSpec> questions,
                                          HashSet<QuestionKey> keys) {
        for (var check : fitChecks) {
            if (!keys.add(check.key())) {
                throw new IllegalArgumentException("fit check repeats key " + check.key());
            }
            var named = questions.stream().anyMatch(question -> question.situation() == Situation.NOW
                    && question.mob().equals(check.mob()) && question.options().contains(check.tactic()));
            if (!named) {
                throw new IllegalArgumentException("fit check " + check.key() + " does not name an option of the NOW"
                        + " question of its mob");
            }
        }
    }
}
