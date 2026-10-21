package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/**
 * The control arm A of the re-bench (M16.10): the text criterion of M15.7 and the text instruction of 0.0.1, without
 * deduplication. Production never sends it, so it lives with the bench tools that compare against it (JS-15).
 */
final class ControlArmQuestions {
    private ControlArmQuestions() {
    }

    /**
     * Builds a question of the control arm A, validating again what the domain already enforces, so the protocol limit
     * holds even if the domain rule changes.
     *
     * @param situation situation of the question
     * @param options options in the order they are offered
     * @param profile profile of the asked mob
     * @param mobRef reference of the mob in the state
     * @return the {@code choice} question
     * @throws SystemOneRequestException if an option is not declared, repeated or the count is out of range
     */
    static JsonObject build(Situation situation, List<TacticId> options, TacticalProfile profile, String mobRef) {
        if (options.size() < QuestionBuilder.MIN_OPTIONS || options.size() > QuestionBuilder.MAX_OPTIONS) {
            throw new SystemOneRequestException("a choice question needs between " + QuestionBuilder.MIN_OPTIONS
                    + " and " + QuestionBuilder.MAX_OPTIONS + " options, had " + options.size());
        }
        if (new HashSet<>(options).size() != options.size()) {
            throw new SystemOneRequestException("a choice question repeats an option");
        }
        var criteria = new JsonObject();
        for (var option : options) {
            var tactic = profile.tactic(option).orElseThrow(() -> new SystemOneRequestException(
                    "option " + option.value() + " is not declared by the profile of " + mobRef));
            criteria.addProperty(option.value(), criterion(tactic, profile));
        }
        var question = new JsonObject();
        question.addProperty("type", "choice");
        question.addProperty("instructions", instructions(situation, mobRef));
        question.add("criteria", criteria);
        return question;
    }

    /**
     * The text instruction of 0.0.1 before M16.
     *
     * @param situation situation of the question
     * @param mobRef reference of the mob
     * @return the instruction
     */
    static String instructions(Situation situation, String mobRef) {
        return switch (situation) {
            case NOW -> "Which tactic should " + mobRef + " use now against its current target?";
            case IF_TARGET_IN_MELEE -> "Which tactic should " + mobRef + " use if its target enters melee reach?";
            case IF_OWN_HEALTH_CRITICAL -> "Which tactic should " + mobRef + " use if its own health becomes critical?";
        };
    }

    /**
     * Describes one option (M15.7), in this order and joined by {@code "; "}: the phrase of its movement; the skill;
     * {@code best when} its conditions joined by {@code " or "}; {@code risk:} its risks joined by {@code ", "}; the
     * author description; and the stance when the profile describes stances. Only fixed phrases reach the model.
     *
     * @param tactic declared tactic
     * @param profile profile that declares it
     * @return the criterion text
     */
    static String criterion(Tactic tactic, TacticalProfile profile) {
        var parts = new ArrayList<String>(5);
        parts.add(SemanticPhrases.movement(tactic.movement()));
        tactic.choice().map(profile.choices()::get).flatMap(SkillChoice::purpose).ifPresent(purpose -> {
            parts.add(skill(purpose, profile.movement()));
            if (!purpose.bestWhen().isEmpty()) {
                var conditions = new ArrayList<String>(purpose.bestWhen().size());
                EnumSet.copyOf(purpose.bestWhen()).forEach(condition -> conditions.add(
                        SemanticPhrases.useCondition(condition)));
                parts.add("best when " + String.join(" or ", conditions));
            }
            if (!purpose.risks().isEmpty()) {
                var risks = new ArrayList<String>(purpose.risks().size());
                EnumSet.copyOf(purpose.risks()).forEach(risk -> risks.add(SemanticPhrases.risk(risk)));
                parts.add("risk: " + String.join(", ", risks));
            }
        });
        tactic.description().ifPresent(parts::add);
        if (profile.stances().describe()) {
            parts.add(SemanticPhrases.stance(tactic.stance().orElseGet(() -> profile.stances().baseOf(tactic.movement()))));
        }
        return String.join("; ", parts);
    }

    private static String skill(SkillPurpose purpose, MovementSettings movement) {
        var roles = new ArrayList<String>(purpose.roles().size());
        purpose.orderedRoles().forEach(role -> roles.add(SemanticPhrases.role(role)));
        var pieces = new ArrayList<String>(7);
        pieces.add("uses a skill that " + SemanticPhrases.delivery(purpose.delivery()) + " " + String.join(" and ", roles));
        purpose.reachBlocks().ifPresent(reach -> pieces.add("reaching " + SemanticPhrases.reach(reach, movement)));
        purpose.radiusBlocks().ifPresent(radius -> pieces.add(SemanticPhrases.area(radius)));
        if (purpose.authored()) {
            pieces.add(SemanticPhrases.windUp(purpose.windUpTicks()));
        }
        pieces.add(SemanticPhrases.damage(purpose.damage()));
        pieces.add(SemanticPhrases.cooldown(purpose.cooldown()));
        if (purpose.ignoresShield()) {
            pieces.add(SemanticPhrases.ignoresShield());
        }
        pieces.removeIf(String::isEmpty);
        return String.join(", ", pieces);
    }
}
