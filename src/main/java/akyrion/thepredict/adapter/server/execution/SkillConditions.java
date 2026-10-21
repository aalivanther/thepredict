package akyrion.thepredict.adapter.server.execution;

import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.core.skills.AbstractSkill;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.conditions.ConditionAction;
import io.lumine.mythic.core.skills.conditions.all.ChanceCondition;

import java.util.List;
import java.util.function.Predicate;

/**
 * Side-effect-free evaluation of the conditions that gate a skill, before it is cast (M10.3). Package-private; server
 * thread only. The evidence is in {@link MythicSkillExecutor}.
 *
 * <p>Only the conditions whose action is {@code REQUIRED} (the default) or {@code CANCEL} are evaluated, in the order
 * of {@code MetaSkill.isUsable}: target ({@code getConditionsTarget}, {@code evaluateTargets}), trigger
 * ({@code getConditionsTrigger}, {@code evaluateTrigger}) and caster ({@code getConditions},
 * {@code evaluateCaster}). A failing target condition counts as failing even when the skill does not stop without
 * targets: a damaging skill waits until its target qualifies. A random condition ({@code chance}) is never evaluated
 * here (see {@link #gating}). A skill that is not an {@code AbstractSkill} declares no conditions here.
 */
final class SkillConditions {
    private SkillConditions() {
    }

    /** Tells whether every gating condition of the skill passes on this metadata, which the evaluation may change. */
    static boolean hold(Skill skill, SkillMetadata metadata) {
        if (!(skill instanceof AbstractSkill conditioned)) {
            return true;
        }
        return all(conditioned.getConditionsTarget(), condition -> condition.evaluateTargets(metadata))
                && all(conditioned.getConditionsTrigger(), condition -> condition.evaluateTrigger(metadata))
                && all(conditioned.getConditions(), condition -> condition.evaluateCaster(metadata));
    }

    /**
     * Tells whether a condition gates the skill without running another skill or changing its power. A {@code chance}
     * condition is left out: {@code ChanceCondition.check} rolls {@code ThreadLocalRandom} (javap), so rolling it here
     * and again in {@code executeIfUsable} would end half of the passing intents in {@code REJECTED_BY_MYTHICMOBS};
     * MythicMobs rolls it once, at the cast.
     */
    static boolean gating(SkillCondition condition) {
        if (condition instanceof ChanceCondition || "chance".equalsIgnoreCase(condition.getTypeName())) {
            return false;
        }
        var action = condition.getACTION();
        return action == null || action == ConditionAction.REQUIRED || action == ConditionAction.CANCEL;
    }

    private static boolean all(List<SkillCondition> conditions, Predicate<SkillCondition> passes) {
        if (conditions == null) {
            return true;
        }
        for (var condition : conditions) {
            if (gating(condition) && !passes.test(condition)) {
                return false;
            }
        }
        return true;
    }
}
