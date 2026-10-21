package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.GroupRole;
import akyrion.thepredict.domain.GroupSettings;
import akyrion.thepredict.domain.LeaderDeath;
import akyrion.thepredict.domain.PlanQuestion;
import akyrion.thepredict.domain.RoleCapabilities;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.CueChannels;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.HitShape;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.StanceNativeMode;
import akyrion.thepredict.domain.StanceSettings;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;
import akyrion.thepredict.domain.ThreatWeights;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each telegraph warning and its opposite (M23.3). */
class BindingWarningsTest {
    @Test
    void writeModeWarnsWhenTheMobSetsItsOwnStance() {
        var choice = authored("slam", TellAnswer.BLOCK, HitShape.UNKNOWN, 0, 0, 0, false);
        var warned = check(profile(StanceNativeMode.WRITE, choice), "Guardian", true, Map.of(), Map.of());
        assertTrue(has(warned, "sets its own stance"));
        assertFalse(has(check(profile(StanceNativeMode.WRITE, choice), "Guardian", false, Map.of(), Map.of()),
                "sets its own stance"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, choice), "Guardian", true, Map.of(), Map.of()),
                "sets its own stance"));
    }

    @Test
    void aMissingCueWarnsAndBothChannelsDoNot() {
        var choice = authored("slam", TellAnswer.BACK_OFF, HitShape.UNKNOWN, 0, 0, 0, false);
        assertTrue(has(check(profile(StanceNativeMode.HYBRID, choice), "Guardian", false, Map.of(), Map.of()),
                "telegraph has no visual (or audio) cue"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, choice), "Guardian", false,
                Map.of("slam", CueChannels.AUDIO | CueChannels.VISUAL), Map.of()), "no visual"));
    }

    @Test
    void theSameOpeningWithTwoAnswersWarns() {
        var block = authored("block", TellAnswer.BLOCK, HitShape.UNKNOWN, 0, 9, 0, false);
        var dodge = authored("dodge", TellAnswer.DODGE, HitShape.UNKNOWN, 0, 9, 0, false);
        var same = authored("also", TellAnswer.BLOCK, HitShape.UNKNOWN, 0, 9, 0, false);
        assertTrue(has(check(profile(StanceNativeMode.HYBRID, block, dodge), "Guardian", false, Map.of(),
                Map.of("block", 9, "dodge", 9)), "start with the same effects"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, block, same), "Guardian", false, Map.of(),
                Map.of("block", 9, "also", 9)), "start with the same effects"));
    }

    @Test
    void aDelayedSingleTargetWarnsAndAProjectileDoesNot() {
        var single = authored("slam", TellAnswer.BACK_OFF, HitShape.SINGLE_TARGET, 10, 0, 0, false);
        var projectile = authored("bolt", TellAnswer.BACK_OFF, HitShape.PROJECTILE, 10, 0, 0, false);
        assertTrue(has(check(profile(StanceNativeMode.HYBRID, single), "Guardian", false, both("slam"), Map.of()),
                "hits @target after a delay"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, projectile), "Guardian", false, both("bolt"), Map.of()),
                "hits @target after a delay"));
    }

    @Test
    void anExposedCasterWarnsUntilRecoveryIsDeclared() {
        var exposed = authored("slam", TellAnswer.BLOCK, HitShape.UNKNOWN, 0, 0, 0, true);
        var declared = exposed.withTelegraph(new ChoiceTelegraph(Optional.of(DangerClass.SEVERE),
                Optional.of(TellAnswer.BLOCK), OptionalInt.of(12)));
        assertTrue(has(check(profile(StanceNativeMode.HYBRID, exposed), "Guardian", false, both("slam"), Map.of()),
                "leaves the caster exposed"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, declared), "Guardian", false, both("slam"), Map.of()),
                "leaves the caster exposed"));
    }

    @Test
    void selfDamageWarnsAndMeleeDoesNot() {
        var self = selfDamage("burst");
        var melee = authored("slam", TellAnswer.BLOCK, HitShape.UNKNOWN, 0, 0, 0, false);
        assertTrue(has(check(profile(StanceNativeMode.HYBRID, self), "Guardian", false, both("burst"), Map.of()),
                "deals damage on SELF"));
        assertFalse(has(check(profile(StanceNativeMode.HYBRID, melee), "Guardian", false, both("slam"), Map.of()),
                "deals damage on SELF"));
    }

    @Test
    void anUntypedChoiceIsNotWarned() {
        var choice = SkillChoice.untyped(new ChoiceId("melee"), "Melee", Set.of(), true, 0);
        assertTrue(check(profile(StanceNativeMode.HYBRID, choice), "Guardian", false, Map.of(), Map.of()).isEmpty());
    }

    @Test
    void groupAuthorshipWarnsOnNativeOverrideSharedTableAndHurtByTarget() {
        var base = profile(StanceNativeMode.HYBRID);
        var group = new GroupSettings(Optional.of(GroupMode.COORDINATE), "", 10, 1, Set.of(), LeaderDeath.SUCCEED,
                BenchPolicy.FREE, Optional.empty(), PlanQuestion.ON_CHANGE, RoleCapabilities.NONE);
        var authored = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                base.choices(), base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(),
                base.timing(), base.participants(), base.difficulty(), base.stances(), base.telegraph(), base.poise(),
                base.zone(), group);
        var problems = new ArrayList<BindingProblem>();
        BindingWarnings.group(authored, List.of("rally", "settarget", "inheritthreattable=true"),
                List.of("hurtbytarget"), problems);
        assertTrue(has(problems, "native targeting may override the group assignment"));
        assertTrue(has(problems, "shared threat table; ThePredict applies one change per table"));
        assertTrue(has(problems, "MythicMobs hurtByTarget does not alert allies"));
        var thrown = assertThrows(IllegalArgumentException.class, () -> new GroupSettings(Optional.of(GroupMode.FAIR),
                "", 0, 1, Set.of(GroupRole.SUPPORT), LeaderDeath.SUCCEED, BenchPolicy.FREE, Optional.empty(),
                PlanQuestion.ON_CHANGE, RoleCapabilities.NONE));
        assertTrue(thrown.getMessage().contains("Group.Roles names SUPPORT but the profile has no capacity"));
    }

    private static List<BindingProblem> check(TacticalProfile profile, String mob, boolean setsStance,
                                              Map<String, Integer> channels, Map<String, Integer> signatures) {
        var problems = new ArrayList<BindingProblem>();
        BindingWarnings.telegraphCoherence(profile, mob, setsStance, channels, signatures, problems);
        return problems;
    }

    private static boolean has(List<BindingProblem> problems, String fragment) {
        for (var problem : problems) {
            if (problem.message().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> both(String skill) {
        return Map.of(skill, CueChannels.AUDIO | CueChannels.VISUAL);
    }

    private static SkillChoice authored(String name, TellAnswer answer, HitShape shape, int windUp, int signature,
                                        int channels, boolean exposed) {
        var id = new ChoiceId(name);
        var risks = exposed ? Set.of(SkillRisk.LEAVES_CASTER_EXPOSED) : Set.<SkillRisk>of();
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), windUp, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), risks, false,
                PurposeOrigin.DECLARED);
        var facts = new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.of(windUp),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false, false, false, true,
                OptionalInt.empty(), signature, channels, shape);
        return SkillChoice.typed(id, name, Set.of(), true, 0, purpose).withNativeFacts(facts)
                .withTelegraph(new ChoiceTelegraph(Optional.of(DangerClass.SEVERE), Optional.of(answer),
                        OptionalInt.empty()));
    }

    private static SkillChoice selfDamage(String name) {
        var id = new ChoiceId(name);
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, name, Set.of(), false, 0, purpose)
                .withTelegraph(new ChoiceTelegraph(Optional.of(DangerClass.HEAVY), Optional.of(TellAnswer.BACK_OFF),
                        OptionalInt.empty()));
    }

    private static TacticalProfile profile(StanceNativeMode mode, SkillChoice... choices) {
        var hold = new TacticId("hold_ground");
        var listed = new LinkedHashMap<ChoiceId, SkillChoice>();
        for (var choice : choices) {
            listed.put(choice.id(), choice);
        }
        return new TacticalProfile("guardian_ai", "Guardian", "Defeat the target",
                Map.of(hold, new Tactic(hold, Movement.HOLD, Optional.empty(), Optional.empty(), 50)), listed,
                List.of(), MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6),
                new ThreatWeights(1, 1, 1, 0.5, 10), List.of(), new DecisionTiming(40, 20, 100, 0.5, 0.2, 1),
                ParticipantRules.DEFAULT, DifficultySettings.DEFAULT,
                new StanceSettings(mode, 20, 2, Map.of(), Map.of(), List.of(), List.of(), false),
                TelegraphSettings.DEFAULT, Optional.empty());
    }
}
