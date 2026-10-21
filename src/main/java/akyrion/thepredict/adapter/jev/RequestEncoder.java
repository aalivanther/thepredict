package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.CriterionRenderer.RenderedCriterion;
import akyrion.thepredict.domain.ActiveUseConditions;
import akyrion.thepredict.domain.DecisionRequest;
import akyrion.thepredict.domain.FitCheck;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.OptionContext;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.Preparation;
import akyrion.thepredict.engine.PreparedRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepares the complete {@code POST /v1/systemone} body (M08.3, M16.1, M16.3): {@code {"model", "state",
 * "questions"}}, with one question per {@code QuestionSpec} and one {@code noul} per fit check under their opaque keys
 * ({@code q0}, {@code q1}…, rule I3). Pure and thread-safe: the dispatcher calls it through
 * {@code DecisionClient.prepare} on the server thread (rule T2), before any budget is reserved.
 *
 * <p>In this order: the options of each question are rendered at the detail of the mob's profile and deduplicated by
 * their final text ({@link OptionTextDedup}); a question left with fewer than two options is dropped, and a dropped
 * {@code NOW} drops the contingencies of its mob, whose mob is then resolved locally; the fit checks of merged options
 * or dropped questions are removed; the state is encoded and trimmed first, so the instructions can point at the final
 * position of each mob and target; then the questions are written and the size guard checks the whole body.
 */
final class RequestEncoder {
    private final String model;
    private final SemanticEncoder stateEncoder;

    RequestEncoder(String model, SemanticEncoder stateEncoder) {
        this.model = Objects.requireNonNull(model, "model");
        this.stateEncoder = Objects.requireNonNull(stateEncoder, "stateEncoder");
    }

    /**
     * Prepares a request as it will be sent.
     *
     * @param request immutable request of one encounter, as the engine planned it
     * @return the prepared request, nothing to ask, or why it cannot be sent
     */
    Preparation prepare(DecisionRequest request) {
        try {
            return deduplicated(request);
        } catch (SystemOneRequestException exception) {
            return new Preparation.Invalid(exception.getMessage());
        }
    }

    /**
     * Encodes a request as compact JSON, for the tools and tests that need only the body.
     *
     * @param request immutable request of one encounter
     * @return the request body
     * @throws SystemOneRequestException if the request is invalid, does not fit or has nothing left to ask
     */
    String encode(DecisionRequest request) {
        return switch (prepare(request)) {
            case Preparation.Ready ready -> ready.prepared().body();
            case Preparation.NothingToAsk ignored -> throw new SystemOneRequestException("nothing left to ask");
            case Preparation.Invalid invalid -> throw new SystemOneRequestException(invalid.detail());
        };
    }

    /**
     * Builds the request body as a JSON object.
     *
     * @param request immutable request of one encounter
     * @return the request body
     * @throws SystemOneRequestException if the request is invalid, does not fit or has nothing left to ask
     */
    JsonObject body(DecisionRequest request) {
        return JsonParser.parseString(encode(request)).getAsJsonObject();
    }

    private Preparation deduplicated(DecisionRequest request) {
        var texts = new HashMap<QuestionKey, Map<TacticId, RenderedCriterion>>();
        var kept = new ArrayList<QuestionSpec>();
        var dropped = new LinkedHashSet<QuestionKey>();
        var localMobs = new LinkedHashSet<MobKey>();
        var refs = SemanticEncoder.mobRefs(request.facts());
        for (var spec : request.questions()) {
            if (spec.situation() != Situation.NOW && localMobs.contains(spec.mob())) {
                dropped.add(spec.key());
                continue;
            }
            var result = deduplicate(spec, request, refs.get(spec.mob()));
            if (result.sent().size() < QuestionSpec.MIN_OPTIONS) {
                dropped.add(spec.key());
                if (spec.situation() == Situation.NOW) {
                    localMobs.add(spec.mob());
                }
                continue;
            }
            texts.put(spec.key(), result.texts());
            kept.add(spec.withOptions(result.sent()));
        }
        if (kept.isEmpty() && !request.facts().brief().asksPlan()) {
            return new Preparation.NothingToAsk(localMobs);
        }
        var sent = sent(request, kept);
        var body = body(sent, texts);
        return new Preparation.Ready(new PreparedRequest(sent, body, dropped, localMobs, body.length()));
    }

    private OptionTextDedup.Result deduplicate(QuestionSpec spec, DecisionRequest request, String mobRef) {
        var profile = request.profiles().get(spec.mob());
        var mob = request.facts().mob(spec.mob()).orElseThrow();
        var now = ActiveUseConditions.of(mob, request.facts().participants());
        var detail = profile.timing().questionDetail();
        return OptionTextDedup.apply(spec.options(), spec.keep(), (option, discriminators) -> {
            var tactic = profile.tactic(option).orElseThrow(() -> new SystemOneRequestException(
                    "option " + option.value() + " is not declared by the profile of " + mobRef));
            return CriterionRenderer.render(tactic, profile, detail,
                    OptionContext.forSituation(tactic, profile, mob, now, spec.situation()), discriminators);
        });
    }

    /** The request as sent: only the questions kept, the profiles of their mobs and the fit checks of options sent. */
    private static DecisionRequest sent(DecisionRequest request, List<QuestionSpec> kept) {
        var profiles = new LinkedHashMap<MobKey, TacticalProfile>();
        kept.forEach(question -> profiles.put(question.mob(), request.profiles().get(question.mob())));
        var checks = new ArrayList<FitCheck>();
        for (var check : request.fitChecks()) {
            var named = kept.stream().anyMatch(question -> question.situation() == Situation.NOW
                    && question.mob().equals(check.mob()) && question.options().contains(check.tactic()));
            if (named) {
                checks.add(check);
            }
        }
        return new DecisionRequest(request.encounter(), request.sequence(), request.facts(), kept, profiles, checks);
    }

    private String body(DecisionRequest sent, Map<QuestionKey, Map<TacticId, RenderedCriterion>> texts) {
        var refs = SemanticEncoder.mobRefs(sent.facts());
        var askedMobs = new HashSet<MobKey>();
        sent.questions().forEach(question -> askedMobs.add(question.mob()));
        var draft = stateEncoder.trimmed(sent.facts(), sent.profiles(), askedMobs);
        var state = draft.render();
        var questions = new JsonObject();
        var longestQuestion = choices(sent, texts, draft, refs, questions);
        if (sent.facts().brief().asksPlan()) {
            var group = QuestionBuilder.groupPlan(sent.facts().brief());
            longestQuestion = Math.max(longestQuestion, group.toString().length());
            questions.add("g0", group);
        }
        for (var check : sent.fitChecks()) {
            var profile = sent.profiles().get(check.mob());
            var mobRef = refs.get(check.mob());
            var question = QuestionBuilder.fitCheck(profile.tactic(check.tactic()).orElseThrow(), profile, mobRef,
                    paths(draft, mobRef, sent.facts().mob(check.mob()).orElseThrow()));
            longestQuestion = Math.max(longestQuestion, question.toString().length());
            questions.add(check.key().value(), question);
        }
        var body = new JsonObject();
        body.addProperty("model", model);
        body.add("state", state);
        body.add("questions", questions);
        var text = body.toString();
        RequestSizeGuard.check(state.toString().length(), longestQuestion, text.length());
        return text;
    }

    /** Writes the choice questions and returns the length of the longest. */
    private static int choices(DecisionRequest sent, Map<QuestionKey, Map<TacticId, RenderedCriterion>> texts,
                               StateDraft draft, Map<MobKey, String> refs, JsonObject questions) {
        var longest = 0;
        for (var spec : sent.questions()) {
            var mobRef = refs.get(spec.mob());
            var detail = sent.profiles().get(spec.mob()).timing().questionDetail();
            var instructions = QuestionBuilder.instructions(spec.situation(), mobRef, detail,
                    paths(draft, mobRef, sent.facts().mob(spec.mob()).orElseThrow()));
            var question = QuestionBuilder.choice(spec, texts.get(spec.key()), instructions);
            longest = Math.max(longest, question.toString().length());
            questions.add(spec.key().value(), question);
        }
        return longest;
    }

    private static StatePaths paths(StateDraft draft, String mobRef, MobFacts mob) {
        var mobIndex = draft.indexOfMob(mobRef);
        var participant = mob.target().map(relation -> draft.indexOfParticipant(relation.target().text())).orElse(-1);
        return new StatePaths(mobIndex, draft.mobHasTarget(mobIndex), participant,
                draft.participantHasCues(participant));
    }
}
