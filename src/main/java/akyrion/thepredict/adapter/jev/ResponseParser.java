package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.GroupAnswer;
import akyrion.thepredict.domain.GroupBrief;
import akyrion.thepredict.domain.GroupPlan;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.TacticId;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strict parser of a {@code SystemOneResponse} (M08.4, M16.5). Accepts only complete answers that match what was sent
 * ({@code PreparedRequest.sent()}: the questions with their sent options, so an answer can only name an option sent,
 * and the fit checks) and are numerically valid; ignores undocumented fields. A fit check needs {@code type: "noul"}
 * and a finite {@code noul} within {@code [0, 1]}. Does not compare {@code model} with the configured one (M10.1 does).
 * Pure and thread-safe.
 */
final class ResponseParser {
    private ResponseParser() {
    }

    /**
     * Parses and validates a response body without fit checks.
     *
     * @param body response body
     * @param questions questions that were sent
     * @return the validated answers, the effective model and the input tokens
     * @throws SystemOneProtocolException naming the violated rule and question key, never the body
     */
    static DecisionResult.Answered parse(String body, List<QuestionSpec> questions) {
        return parse(body, questions, List.of());
    }

    /**
     * Parses and validates a response body in the order of M08.4.
     *
     * @param body response body
     * @param questions questions that were sent, with the options sent
     * @param fitChecks fit checks that were sent
     * @return the validated answers, the fits, the effective model and the input tokens
     * @throws SystemOneProtocolException naming the violated rule and question key, never the body
     */
    static DecisionResult.Answered parse(String body, List<QuestionSpec> questions, List<FitCheck> fitChecks) {
        return parse(body, questions, fitChecks, GroupBrief.NONE);
    }

    /**
     * Parses a response. When the brief the request was built from asks a plan, {@code g0} is a choice over its
     * candidates and is not a tactic (M25.10): it follows the rules of every choice (the options of the brief, a
     * confidence and probabilities within {@code [0, 1]} that sum to 1, the most probable option chosen) and is attached
     * with the membership epoch of the brief, never one read from the text. A missing or broken {@code g0} is dropped
     * alone: the tactic answers stay.
     *
     * @param body response body
     * @param questions questions that were sent
     * @param fitChecks fit checks that were sent
     * @param brief the group brief of the facts the request was built from
     * @return the validated answers
     */
    static DecisionResult.Answered parse(String body, List<QuestionSpec> questions, List<FitCheck> fitChecks,
                                         GroupBrief brief) {
        var root = StrictJson.parseObject(body, "response");
        var model = StrictJson.text(root, "model", "model");
        if (model.isBlank()) {
            throw new SystemOneProtocolException("model must not be blank");
        }
        var answers = StrictJson.object(root.get("answers"), "answers");
        var usage = StrictJson.object(root.get("usage"), "usage");
        var inputTokens = StrictJson.nonNegativeInt(usage.get("input_tokens"), "usage.input_tokens");
        requireSent(answers, questions, fitChecks, brief.asksPlan());
        var answered = new DecisionResult.Answered(choices(answers, questions), model, inputTokens,
                fits(answers, fitChecks));
        if (!brief.asksPlan()) {
            return answered;
        }
        var plan = groupPlan(answers.get("g0"), brief);
        return plan.isPresent() ? answered.withGroup(new GroupAnswer(plan.get(), brief.membershipEpoch())) : answered;
    }

    /** Refuses an answer under a key that was not sent: a question, a fit check, or {@code g0} when it was asked. */
    private static void requireSent(JsonObject answers, List<QuestionSpec> questions, List<FitCheck> fitChecks,
                                    boolean groupAsked) {
        var sent = new HashSet<String>();
        questions.forEach(question -> sent.add(question.key().value()));
        fitChecks.forEach(check -> sent.add(check.key().value()));
        if (groupAsked) {
            sent.add("g0");
        }
        for (var key : answers.keySet()) {
            if (!sent.contains(key)) {
                throw new SystemOneProtocolException("answers contains a question key that was not sent");
            }
        }
    }

    private static Map<QuestionKey, Answer> choices(JsonObject answers, List<QuestionSpec> questions) {
        var parsed = new LinkedHashMap<QuestionKey, Answer>();
        for (var question : questions) {
            var what = "answers." + question.key().value();
            parsed.put(question.key(), answer(StrictJson.object(answers.get(question.key().value()), what),
                    question, what));
        }
        return parsed;
    }

    private static Map<QuestionKey, Double> fits(JsonObject answers, List<FitCheck> fitChecks) {
        var fits = new LinkedHashMap<QuestionKey, Double>();
        for (var check : fitChecks) {
            var what = "answers." + check.key().value();
            fits.put(check.key(), fit(StrictJson.object(answers.get(check.key().value()), what), what));
        }
        return fits;
    }

    /** The plan of a valid {@code g0}, or empty when it is missing or breaks a rule of a choice. */
    private static Optional<GroupPlan> groupPlan(JsonElement element, GroupBrief brief) {
        try {
            var what = "answers.g0";
            var json = StrictJson.object(element, what);
            if (!"choice".equals(StrictJson.text(json, "type", what + ".type"))) {
                return Optional.empty();
            }
            var choice = StrictJson.text(json, "choice", what + ".choice");
            unit(StrictJson.number(json.get("confidence"), what + ".confidence"), what + ".confidence");
            var probabilities = StrictJson.object(json.get("probabilities"), what + ".probabilities");
            if (!probabilities.keySet().equals(new HashSet<>(brief.candidates()))
                    || !mostProbable(choice, probabilities, brief.candidates(), what)) {
                return Optional.empty();
            }
            return GroupPlan.parse(choice);
        } catch (SystemOneProtocolException exception) {
            return Optional.empty();
        }
    }

    private static boolean mostProbable(String choice, JsonObject probabilities, List<String> options, String what) {
        var sum = 0.0;
        var chosen = Double.NaN;
        var best = 0.0;
        for (var option : options) {
            var probability = unit(StrictJson.number(probabilities.get(option), what + ".probabilities"),
                    what + ".probabilities");
            sum += probability;
            best = Math.max(best, probability);
            if (option.equals(choice)) {
                chosen = probability;
            }
        }
        return Math.abs(sum - 1.0) <= Answer.SUM_TOLERANCE + Answer.ROUNDING_EPSILON && chosen >= best;
    }

    private static double fit(JsonObject json, String what) {
        if (!"noul".equals(StrictJson.text(json, "type", what + ".type"))) {
            throw new SystemOneProtocolException(what + ".type must be noul");
        }
        return unit(StrictJson.number(json.get("noul"), what + ".noul"), what + ".noul");
    }

    private static Answer answer(JsonObject json, QuestionSpec question, String what) {
        if (!"choice".equals(StrictJson.text(json, "type", what + ".type"))) {
            throw new SystemOneProtocolException(what + ".type must be choice");
        }
        var choice = StrictJson.text(json, "choice", what + ".choice");
        var confidence = unit(StrictJson.number(json.get("confidence"), what + ".confidence"), what + ".confidence");
        var probabilities = probabilities(StrictJson.object(json.get("probabilities"), what + ".probabilities"),
                question, what);
        requireBest(choice, probabilities, what);
        return new Answer(probabilities, confidence);
    }

    private static Map<TacticId, Double> probabilities(JsonObject json, QuestionSpec question, String what) {
        var options = new HashSet<String>();
        question.options().forEach(option -> options.add(option.value()));
        if (!json.keySet().equals(options)) {
            throw new SystemOneProtocolException(what + ".probabilities must name exactly the options sent");
        }
        var probabilities = new LinkedHashMap<TacticId, Double>();
        var sum = 0.0;
        for (var option : question.options()) {
            var probability = unit(StrictJson.number(json.get(option.value()), what + ".probabilities"),
                    what + ".probabilities");
            probabilities.put(option, probability);
            sum += probability;
        }
        if (Math.abs(sum - 1.0) > Answer.SUM_TOLERANCE + Answer.ROUNDING_EPSILON) {
            throw new SystemOneProtocolException(what + ".probabilities must sum to 1 within " + Answer.SUM_TOLERANCE);
        }
        return probabilities;
    }

    private static void requireBest(String choice, Map<TacticId, Double> probabilities, String what) {
        var chosen = probabilities.entrySet().stream().filter(entry -> entry.getKey().value().equals(choice))
                .findFirst().orElseThrow(() -> new SystemOneProtocolException(what + ".choice is not an option sent"));
        for (var probability : probabilities.values()) {
            if (probability > chosen.getValue()) {
                throw new SystemOneProtocolException(what + ".choice is not the most probable option");
            }
        }
    }

    private static double unit(double value, String what) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new SystemOneProtocolException(what + " must be finite and within [0, 1]");
        }
        return value;
    }
}
