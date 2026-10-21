package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.CombatActions;
import akyrion.thepredict.engine.EngineFixtures;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Duel-pack choices and the release port of a skirmish. */
final class SkirmishKit {
    static final CombatActions ACTIONS = new Port();
    private static final TacticalProfile[] PROFILES = new TacticalProfile[5];

    private SkirmishKit() {
    }

    static TacticalProfile profile(int weight) {
        var cached = PROFILES[weight];
        if (cached != null) {
            return cached;
        }
        synchronized (PROFILES) {
            if (PROFILES[weight] == null) {
                PROFILES[weight] = build(weight);
            }
            return PROFILES[weight];
        }
    }

    static SkillChoice choice(DuelSkill skill) {
        return profile(1).choices().get(id(skill));
    }

    private static TacticalProfile build(int weight) {
        var base = EngineFixtures.profile("Guardian");
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>(base.choices());
        choices.put(id(DuelSkill.STRIKE), typed(DuelSkill.STRIKE, SkillRole.DAMAGE, Delivery.MELEE, DamageTier.HIGH));
        choices.put(id(DuelSkill.BOLT), typed(DuelSkill.BOLT, SkillRole.DAMAGE, Delivery.MELEE, DamageTier.MEDIUM));
        choices.put(id(DuelSkill.LEAP), typed(DuelSkill.LEAP, SkillRole.GAP_CLOSE, Delivery.MELEE, DamageTier.LOW));
        choices.put(id(DuelSkill.GUARD), typed(DuelSkill.GUARD, SkillRole.DEFENSE, Delivery.SELF, DamageTier.NONE));
        choices.put(id(DuelSkill.HEAL), typed(DuelSkill.HEAL, SkillRole.SUSTAIN, Delivery.SELF, DamageTier.NONE));
        var group = new GroupSettings(Optional.of(GroupMode.FAIR), "", 1, weight, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FAIR, Optional.empty(), PlanQuestion.ON_CHANGE, RoleCapabilities.NONE);
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), choices,
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(), base.zone(),
                group);
    }

    private static ChoiceId id(DuelSkill skill) {
        return new ChoiceId(skill.id.toLowerCase(java.util.Locale.ROOT));
    }

    private static SkillChoice typed(DuelSkill skill, SkillRole role, Delivery delivery, DamageTier damage) {
        var purpose = new SkillPurpose(role, Set.of(role), delivery, reach(skill), java.util.OptionalDouble.empty(),
                skill.delayTicks, CooldownClass.SHORT, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(id(skill), skill.id, Set.of(), true, 0, purpose);
    }

    private static java.util.OptionalDouble reach(DuelSkill skill) {
        return skill.withinBlocks > 0 ? java.util.OptionalDouble.of(skill.withinBlocks) : java.util.OptionalDouble.empty();
    }

    private static final class Port implements CombatActions {
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        }

        public boolean ready(MobKey mob, SkillChoice choice) {
            return false;
        }

        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }

        public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                      akyrion.thepredict.engine.CastInterrupt interrupt) {
            return ReleaseOutcome.RELEASED;
        }
    }
}
