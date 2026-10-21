package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.CriterionRenderer.RenderedCriterion;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * Builds the System One questions (M08.3, M16.2, M16.3, M16.5). The keys of {@code criteria} are the tactic IDs: the
 * only profile names that reach the model (rule I4). {@link #choice} writes a question at its detail from the texts of
 * {@link CriterionRenderer} and the instruction of {@link #instructions}; {@link #fitCheck} writes the optional
 * {@code noul} of one option. The control arm A of the re-bench (M16.10) is not sent by production and lives with
 * the bench tools. Pure and thread-safe.
 */
final class QuestionBuilder {
    /** Fewest options System One accepts in a choice question, as the domain allows. */
    static final int MIN_OPTIONS = 2;
    /** Most options System One accepts in a choice question (plan §6). */
    static final int MAX_OPTIONS = 255;

    private QuestionBuilder() {
    }

    /**
     * Builds a {@code choice} question from the final texts of its options.
     *
     * @param spec question sent, with the options sent
     * @param texts final text of each option, from {@link OptionTextDedup}
     * @param instructions the instruction of {@link #instructions}
     * @return the {@code choice} question
     * @throws SystemOneRequestException if the option count is out of range or an option has no text
     */
    static JsonObject choice(QuestionSpec spec, Map<TacticId, RenderedCriterion> texts, JsonElement instructions) {
        var options = spec.options();
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new SystemOneRequestException("a choice question needs between " + MIN_OPTIONS + " and "
                    + MAX_OPTIONS + " options, had " + options.size());
        }
        var criteria = new JsonObject();
        for (var option : options) {
            var text = texts.get(option);
            if (text == null) {
                throw new SystemOneRequestException("option " + option.value() + " has no criterion");
            }
            criteria.add(option.value(), text.json());
        }
        var question = new JsonObject();
        question.addProperty("type", "choice");
        question.add("instructions", instructions);
        question.add("criteria", criteria);
        return question;
    }

    /**
     * Builds {@code g0}. The instruction names the membership epoch so a stale plan can be dropped alone.
     *
     * @param brief candidates and the epoch of the capture
     * @return the choice question
     */
    static JsonObject groupPlan(akyrion.thepredict.domain.GroupBrief brief) {
        var criteria = new JsonObject();
        for (var option : brief.candidates()) {
            var text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", option.replace('_', ' '));
            criteria.add(option, text);
        }
        var question = new JsonObject();
        question.addProperty("type", "choice");
        question.addProperty("instructions", "membership " + brief.membershipEpoch() + ". Choose the group's plan.");
        question.add("criteria", criteria);
        return question;
    }

    /**
     * The instruction of a question at its detail (M16.3): a short text in {@code COMPACT}; in {@code STANDARD} and
     * {@code FULL} an object with the {@code question}, the {@code assume} of a contingency, the path of the
     * {@code objective} and the paths to {@code judge_by}, plus the fixed {@code focus} in {@code FULL}.
     *
     * @param situation situation of the question
     * @param mobRef reference of the mob, such as {@code mob_1}
     * @param detail detail of the question
     * @param paths where the mob and its target are in the state sent
     * @return the instruction
     */
    static JsonElement instructions(Situation situation, String mobRef, QuestionDetail detail, StatePaths paths) {
        if (detail == QuestionDetail.COMPACT) {
            return new JsonPrimitive(SemanticPhrases.compactInstructions(situation, mobRef));
        }
        var json = new JsonObject();
        json.addProperty("question", SemanticPhrases.question(situation, mobRef));
        var assume = SemanticPhrases.assume(situation, mobRef);
        if (!assume.isEmpty()) {
            json.addProperty("assume", assume);
        }
        json.addProperty("objective", paths.objective());
        json.add("judge_by", SemanticEncoder.array(paths.judgeBy()));
        if (detail == QuestionDetail.FULL) {
            json.addProperty("focus", SemanticPhrases.focus());
        }
        return json;
    }

    /**
     * Builds the {@code noul} fit check of one option of a {@code NOW} question (M16.5): only its movement and skill,
     * without what fits now, so the signal stays independent of the reasons of M16.2.
     *
     * @param tactic option checked
     * @param profile profile that declares it
     * @param mobRef reference of the mob
     * @param paths where the mob and its target are in the state sent
     * @return the {@code noul} question
     */
    static JsonObject fitCheck(Tactic tactic, TacticalProfile profile, String mobRef, StatePaths paths) {
        var instructions = new JsonObject();
        instructions.addProperty("question", SemanticPhrases.fitQuestion(tactic.id().value(), mobRef));
        instructions.add("tactic", CriterionRenderer.movementAndSkill(tactic, profile));
        instructions.add("judge_by", SemanticEncoder.array(paths.fitJudgeBy()));
        var criteria = new JsonObject();
        criteria.addProperty("true", SemanticPhrases.fitTrue(mobRef));
        criteria.addProperty("false", SemanticPhrases.fitFalse());
        var question = new JsonObject();
        question.addProperty("type", "noul");
        question.add("instructions", instructions);
        question.add("criteria", criteria);
        return question;
    }
}
