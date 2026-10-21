package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.ContextId;
import akyrion.thepredict.domain.ContextRule;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PredictValue;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** Profiles shared by the context tests: every movement, a skill choice, a vanilla choice and contexts. */
final class ContextFixtures {
    static final ChoiceId STRIKE = new ChoiceId("strike");
    static final ChoiceId PUNCH = new ChoiceId("punch");
    static final ChoiceId GUARD = new ChoiceId("guard");

    private ContextFixtures() {
    }

    static TacticId tactic(String value) {
        return new TacticId(value);
    }

    static ContextId context(String value) {
        return new ContextId(value);
    }

    static ContextRule rule(String id, List<String> conditions, int priority, String... tactics) {
        var allowed = new LinkedHashSet<TacticId>();
        for (var name : tactics) {
            allowed.add(tactic(name));
        }
        return new ContextRule(context(id), conditions, priority, allowed, Optional.empty());
    }

    static TacticalProfile profile(List<ContextRule> contexts) {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        add(tactics, "approach", Movement.APPROACH, Optional.empty(), 50);
        add(tactics, "strike_close", Movement.APPROACH, Optional.of(STRIKE), 70);
        add(tactics, "keep_distance", Movement.KEEP_DISTANCE, Optional.empty(), 40);
        add(tactics, "retreat_and_guard", Movement.RETREAT, Optional.of(GUARD), 90);
        add(tactics, "flank_and_strike", Movement.FLANK, Optional.of(STRIKE), 60);
        add(tactics, "punch_in_place", Movement.HOLD, Optional.of(PUNCH), 30);
        add(tactics, "hold", Movement.HOLD, Optional.empty(), 10);
        add(tactics, "stand", Movement.NONE, Optional.empty(), 5);
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(STRIKE, new SkillChoice(STRIKE, ChoiceKind.MYTHIC_SKILL, Optional.of("Strike"),
                Set.of(PredictValue.MELEE_ATTACK), true, 0));
        choices.put(GUARD, new SkillChoice(GUARD, ChoiceKind.MYTHIC_SKILL, Optional.of("Guard"),
                Set.of(PredictValue.DEFENSE), false, 0));
        var punch = new SkillChoice(PUNCH, ChoiceKind.VANILLA_MELEE, Optional.empty(),
                Set.of(PredictValue.MELEE_ATTACK), true, 0);
        choices.put(PUNCH, punch.withPurpose(punch.typedPurpose().withCooldown(CooldownClass.SHORT)));
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target", tactics, choices, contexts,
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    static final ChoiceId BOLT = new ChoiceId("bolt");
    static final ChoiceId SLAM = new ChoiceId("slam");

    /**
     * A profile whose skills declare their purposes (M15): {@code strike} a melee hit of 2.5 blocks, {@code bolt} a
     * hitscan of 12 blocks, {@code slam} an area of 3 blocks around the caster and {@code guard} a skill on itself.
     */
    static TacticalProfile declaredProfile() {
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        add(tactics, "strike_close", Movement.APPROACH, Optional.of(STRIKE), 70);
        add(tactics, "keep_and_strike", Movement.KEEP_DISTANCE, Optional.of(STRIKE), 65);
        add(tactics, "keep_and_shoot", Movement.KEEP_DISTANCE, Optional.of(BOLT), 60);
        add(tactics, "slam_close", Movement.APPROACH, Optional.of(SLAM), 55);
        add(tactics, "hold_and_strike", Movement.HOLD, Optional.of(STRIKE), 50);
        add(tactics, "retreat_and_guard", Movement.RETREAT, Optional.of(GUARD), 40);
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(STRIKE, declared(STRIKE, "Strike", Delivery.MELEE, OptionalDouble.of(2.5), OptionalDouble.empty()));
        choices.put(BOLT, declared(BOLT, "Bolt", Delivery.HITSCAN, OptionalDouble.of(12), OptionalDouble.empty()));
        choices.put(SLAM, declared(SLAM, "Slam", Delivery.AREA_AROUND_SELF, OptionalDouble.empty(),
                OptionalDouble.of(3)));
        choices.put(GUARD, new SkillChoice(GUARD, ChoiceKind.MYTHIC_SKILL, Optional.of("Guard"), Set.of(), false, 0,
                new SkillPurpose(SkillRole.DEFENSE, Set.of(SkillRole.DEFENSE), Delivery.SELF, OptionalDouble.empty(),
                        OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE, Set.of(), Set.of(), false,
                        PurposeOrigin.DECLARED)));
        return new TacticalProfile("declared_ai", "Declared", "Defeat the target", tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                new DecisionTiming(40, 20, 100, 0.5, 0.2, 1));
    }

    private static SkillChoice declared(ChoiceId id, String skill, Delivery delivery, OptionalDouble reach,
                                        OptionalDouble radius) {
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of(skill), Set.of(), true, 0, new SkillPurpose(
                SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), delivery, reach, radius, 0, CooldownClass.UNKNOWN,
                DamageTier.MEDIUM, Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
    }

    private static void add(LinkedHashMap<TacticId, Tactic> tactics, String id, Movement movement,
                            Optional<ChoiceId> choice, int priority) {
        tactics.put(tactic(id), new Tactic(tactic(id), movement, choice, Optional.empty(), priority));
    }
}
