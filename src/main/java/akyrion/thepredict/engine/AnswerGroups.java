package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Groups the answers of one request by mob: the {@code NOW} answer with the contingency answers of the same mob
 * (M10.1). Pure; package-private.
 *
 * <p>Questions are read in key order ({@code q0}, {@code q1}…) and the groups are returned in encounter order, so
 * the coordination of M10.7 never depends on the order in which answers were written. A mob whose {@code NOW} answer
 * is missing gets no group and counts one {@code INVALID_RESPONSE}.
 */
final class AnswerGroups {
    private AnswerGroups() {
    }

    /**
     * The answers about one mob.
     *
     * @param mob mob asked about, with the generation of the request
     * @param now answer to its {@code NOW} question
     * @param contingencies answers to its contingency questions
     * @param fits probability of {@code true} of the fit check of each {@code NOW} option (M16.5); empty without them
     */
    record Group(MobKey mob, Answer now, Map<Situation, Answer> contingencies, Map<TacticId, Double> fits) {
        Group {
            Objects.requireNonNull(mob, "mob");
            Objects.requireNonNull(now, "now");
            contingencies = Map.copyOf(contingencies);
            fits = Map.copyOf(fits);
        }
    }

    /**
     * The key of the question of one mob in one situation, for the duel recorder (M18.4).
     *
     * @return the key, empty when the request did not ask it
     */
    static Optional<QuestionKey> questionOf(InFlightRequest request, MobKey mob, Situation situation) {
        for (var question : request.questions().values()) {
            if (question.mob().equals(mob) && question.situation() == situation) {
                return Optional.of(question.key());
            }
        }
        return Optional.empty();
    }

    /** The mobs of the questions, each once, in key order. */
    static List<MobKey> askedMobs(InFlightRequest request) {
        var mobs = new ArrayList<MobKey>();
        for (var question : ordered(request)) {
            if (!mobs.contains(question.mob())) {
                mobs.add(question.mob());
            }
        }
        return mobs;
    }

    /** The groups of an answered request in the order of {@code members}; mobs outside it come last in key order. */
    static List<Group> of(InFlightRequest request, DecisionResult.Answered answered, List<MobKey> members,
                          DecisionMetrics metrics) {
        var now = new LinkedHashMap<MobKey, Answer>();
        var contingencies = new LinkedHashMap<MobKey, Map<Situation, Answer>>();
        for (var question : ordered(request)) {
            var answer = answered.answers().get(question.key());
            if (answer == null) {
                continue;
            }
            if (question.situation() == Situation.NOW) {
                now.put(question.mob(), answer);
            } else {
                contingencies.computeIfAbsent(question.mob(), mob -> new EnumMap<>(Situation.class))
                        .put(question.situation(), answer);
            }
        }
        var groups = new ArrayList<Group>();
        for (var mob : askedMobs(request)) {
            if (now.containsKey(mob)) {
                groups.add(new Group(mob, now.get(mob), contingencies.getOrDefault(mob, Map.of()),
                        fits(request, answered, mob)));
            } else {
                metrics.increment(FallbackReason.INVALID_RESPONSE);
            }
        }
        groups.sort(Comparator.comparingInt(group -> position(members, group.mob())));
        return groups;
    }

    /** The fits of the fit checks sent about one mob, by option; a check without an answer is left out. */
    private static Map<TacticId, Double> fits(InFlightRequest request, DecisionResult.Answered answered, MobKey mob) {
        var fits = new LinkedHashMap<TacticId, Double>();
        request.fitChecks().forEach((key, check) -> {
            var fit = answered.fits().get(key);
            if (check.mob().equals(mob) && fit != null) {
                fits.put(check.tactic(), fit);
            }
        });
        return fits;
    }

    private static int position(List<MobKey> members, MobKey mob) {
        for (var index = 0; index < members.size(); index++) {
            if (members.get(index).entityId().equals(mob.entityId())) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static List<QuestionSpec> ordered(InFlightRequest request) {
        var questions = new ArrayList<>(request.questions().values());
        questions.sort(Comparator.comparingInt(question -> index(question.key())));
        return questions;
    }

    private static int index(QuestionKey key) {
        return Integer.parseInt(key.value().substring(1));
    }
}
