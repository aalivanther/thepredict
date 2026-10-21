package akyrion.thepredict.adapter.storage;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.OptionPruning;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.Preparation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The JSON values of the decision lines of M21.1 that repeat M18 ({@code planned}, {@code result} and
 * {@code pruned}): the questions planned and sent, the dropped questions, the mobs resolved locally, the answers with
 * their probabilities and the fits, so a record can be replayed without paying for Jev again (M21.10, W7). Keys are
 * written in a fixed order: {@code q2} before {@code q10}. Pure.
 */
final class DecisionLines {
    private static final Comparator<String> KEY_ORDER = Comparator.comparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());

    private DecisionLines() {
    }

    /**
     * The planned questions: key, mob, situation and options.
     *
     * @param planned the questions as the engine planned them
     * @return a JSON array
     */
    static String questions(List<QuestionSpec> planned) {
        var values = new ArrayList<String>(planned.size());
        for (var question : planned) {
            var fields = new LinkedHashMap<String, String>();
            fields.put("key", JsonText.quote(question.key().value()));
            fields.put("mob", JsonText.quote(question.mob().entityId().toString()));
            fields.put("situation", JsonText.quote(question.situation().name()));
            fields.put("options", options(question.options()));
            values.add(JsonText.object(fields));
        }
        return array(values);
    }

    /**
     * The options sent per question, after deduplication. Empty unless the request was ready.
     *
     * @param preparation what {@code prepare} returned, or {@code null} when it is not known
     * @return a JSON object
     */
    static String sentOptions(Preparation preparation) {
        var sent = new LinkedHashMap<String, String>();
        if (preparation instanceof Preparation.Ready ready) {
            for (var question : ready.prepared().sent().questions()) {
                sent.put(question.key().value(), options(question.options()));
            }
        }
        return JsonText.object(sent);
    }

    /**
     * The questions dropped before sending.
     *
     * @param planned the planned questions
     * @param preparation what {@code prepare} returned, or {@code null} when it is not known
     * @return a JSON array of keys
     */
    static String dropped(List<QuestionSpec> planned, Preparation preparation) {
        var keys = new TreeSet<>(KEY_ORDER);
        switch (preparation) {
            case Preparation.Ready ready -> ready.prepared().droppedQuestions().forEach(key -> keys.add(key.value()));
            case Preparation.NothingToAsk ignored -> planned.forEach(question -> keys.add(question.key().value()));
            case null, default -> {
                return "[]";
            }
        }
        return texts(keys);
    }

    /**
     * The mobs the request left to their local plan.
     *
     * @param planned the planned questions
     * @param preparation what {@code prepare} returned, or {@code null} when it is not known
     * @return a JSON array of UUIDs
     */
    static String localMobs(List<QuestionSpec> planned, Preparation preparation) {
        var mobs = new TreeSet<String>();
        switch (preparation) {
            case Preparation.Ready ready -> ready.prepared().localMobs().forEach(mob -> mobs.add(uuid(mob)));
            case Preparation.NothingToAsk nothing -> nothing.localMobs().forEach(mob -> mobs.add(uuid(mob)));
            case Preparation.Invalid ignored -> planned.stream().filter(question -> question.situation()
                    == Situation.NOW).forEach(question -> mobs.add(uuid(question.mob())));
            case null -> {
                return "[]";
            }
        }
        return texts(mobs);
    }

    /**
     * The answers by question: the chosen option, the probabilities in rank order and the confidence.
     *
     * @param answered the answer
     * @return a JSON object
     */
    static String answers(DecisionResult.Answered answered) {
        var byKey = new LinkedHashMap<String, String>();
        for (var key : sorted(answered.answers())) {
            var answer = answered.answers().get(key);
            byKey.put(key.value(), answer(answer));
        }
        return JsonText.object(byKey);
    }

    /**
     * The fit of each fit check.
     *
     * @param answered the answer
     * @return a JSON object
     */
    static String fits(DecisionResult.Answered answered) {
        var byKey = new LinkedHashMap<String, String>();
        for (var key : sorted(answered.fits())) {
            byKey.put(key.value(), JsonText.number(answered.fits().get(key)));
        }
        return JsonText.object(byKey);
    }

    /**
     * The tactics a prune removed, with their reason.
     *
     * @param pruning the result
     * @return a JSON object
     */
    static String removed(OptionPruning.PruneResult pruning) {
        var byTactic = new LinkedHashMap<String, String>();
        var tactics = new TreeSet<String>();
        pruning.dropped().keySet().forEach(tactic -> tactics.add(tactic.value()));
        for (var tactic : tactics) {
            byTactic.put(tactic, JsonText.quote(pruning.dropped().get(new TacticId(tactic)).name()));
        }
        return JsonText.object(byTactic);
    }

    private static String answer(Answer answer) {
        var probabilities = new LinkedHashMap<String, String>();
        for (var tactic : answer.ranked()) {
            probabilities.put(tactic.value(), JsonText.number(answer.probabilityOf(tactic)));
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("choice", JsonText.quote(answer.best().value()));
        fields.put("probabilities", JsonText.object(probabilities));
        fields.put("confidence", JsonText.number(answer.confidence()));
        return JsonText.object(fields);
    }

    private static <T> List<QuestionKey> sorted(Map<QuestionKey, T> map) {
        var keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.comparing(QuestionKey::value, KEY_ORDER));
        return keys;
    }

    private static String options(List<TacticId> options) {
        var values = new ArrayList<String>(options.size());
        for (var option : options) {
            values.add(JsonText.quote(option.value()));
        }
        return array(values);
    }

    private static String texts(Iterable<String> values) {
        var quoted = new ArrayList<String>();
        for (var value : values) {
            quoted.add(JsonText.quote(value));
        }
        return array(quoted);
    }

    private static String uuid(MobKey mob) {
        return mob.entityId().toString();
    }

    private static String array(List<String> values) {
        return "[" + String.join(",", values) + "]";
    }
}
