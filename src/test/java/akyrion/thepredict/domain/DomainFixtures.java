package akyrion.thepredict.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

final class DomainFixtures {
    static final MobKey MOB = new MobKey(UUID.fromString("00000000-0000-0000-0000-00000000000a"), 2);
    static final EncounterId ENCOUNTER = new EncounterId(1);

    private DomainFixtures() {
    }

    static TacticId tactic(String value) {
        return new TacticId(value);
    }

    static ChoiceId choice(String value) {
        return new ChoiceId(value);
    }

    static ContextId context(String value) {
        return new ContextId(value);
    }

    static MovementSettings movement() {
        return new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty());
    }

    static HealthThresholds health() {
        return new HealthThresholds(0.3, 0.6);
    }

    static ThreatWeights threat() {
        return new ThreatWeights(1, 1, 1, 0.5, 10);
    }

    static DecisionTiming timing() {
        return new DecisionTiming(40, 20, 100, 0.5, 0.2, 2);
    }

    static SkillChoice skill(String id) {
        return new SkillChoice(choice(id), ChoiceKind.MYTHIC_SKILL, Optional.of("Skill_" + id),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
    }

    static SkillPurpose purpose(Delivery delivery, OptionalDouble reach, OptionalDouble radius, UseCondition... bestWhen) {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, reach, radius, 0,
                CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(bestWhen), Set.of(), false, PurposeOrigin.DECLARED);
    }

    static SkillPurpose meleePurpose(double reachBlocks, UseCondition... bestWhen) {
        return purpose(Delivery.MELEE, OptionalDouble.of(reachBlocks), OptionalDouble.empty(), bestWhen);
    }

    static SkillChoice declared(String id, SkillPurpose purpose) {
        return new SkillChoice(choice(id), ChoiceKind.MYTHIC_SKILL, Optional.of("Skill_" + id), Set.of(), true, 0, purpose);
    }

    static Tactic tacticOf(String id, Movement movement, String choiceId, int priority) {
        return new Tactic(tactic(id), movement, Optional.ofNullable(choiceId).map(ChoiceId::new), Optional.empty(), priority);
    }

    static TacticalProfile profile(List<Tactic> tactics, List<SkillChoice> choices, List<ContextRule> contexts,
                                   DecisionTiming timing) {
        var tacticMap = new LinkedHashMap<TacticId, Tactic>();
        tactics.forEach(tactic -> tacticMap.put(tactic.id(), tactic));
        var choiceMap = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.forEach(skill -> choiceMap.put(skill.id(), skill));
        return new TacticalProfile("guardian_ai", "Guardian", "Survive and defeat the target", tacticMap, choiceMap,
                contexts, movement(), health(), threat(), List.of(), timing);
    }

    static TacticalProfile guardian() {
        return profile(List.of(
                        tacticOf("press_attack", Movement.APPROACH, "melee", 20),
                        tacticOf("keep_range", Movement.KEEP_DISTANCE, "ranged", 20),
                        tacticOf("defend_self", Movement.RETREAT, "defense", 40),
                        tacticOf("hold_ground", Movement.HOLD, null, 10)),
                List.of(skill("melee"), skill("ranged"), skill("defense")),
                List.of(new ContextRule(context("low_health"), List.of("healthpercent{p=<30%}"), 30,
                                Set.of(tactic("defend_self")), Optional.of(tactic("defend_self"))),
                        new ContextRule(context("attacking"), List.of("targetwithin{d=6}"), 20,
                                Set.of(tactic("press_attack"), tactic("keep_range")), Optional.empty()),
                        new ContextRule(context("tracking"), List.of("targetnotwithin{d=6}"), 10,
                                Set.of(tactic("keep_range")), Optional.of(tactic("keep_range")))),
                timing());
    }

    static Answer answer(double confidence, Object... tacticsAndProbabilities) {
        var probabilities = new LinkedHashMap<TacticId, Double>();
        for (var index = 0; index < tacticsAndProbabilities.length; index += 2) {
            probabilities.put(tactic((String) tacticsAndProbabilities[index]),
                    ((Number) tacticsAndProbabilities[index + 1]).doubleValue());
        }
        return new Answer(probabilities, confidence);
    }
}
