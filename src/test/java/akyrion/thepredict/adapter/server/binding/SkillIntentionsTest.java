package akyrion.thepredict.adapter.server.binding;

import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.IntentionParse;
import akyrion.thepredict.domain.IntentionVocabulary;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.NativeSkillFacts;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.PurposeRules;
import akyrion.thepredict.domain.ReachMeasure;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.UseCondition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.2: an Intention line completed with the native facts, cooldown still unknown. */
class SkillIntentionsTest {
    @Test
    void theExampleLinesCompleteFromTheFactsAReaderWouldReturn() {
        var melee = complete(facts(5, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true), "DAMAGE", "MELEE", "DAMAGE_MEDIUM", "WHEN_TARGET_IN_MELEE");
        assertEquals(Delivery.MELEE, melee.delivery());
        assertEquals(ReachMeasure.BOX_GAP, melee.reachMeasure());
        assertEquals(OptionalDouble.of(2.5), melee.reachBlocks());
        assertEquals(5, melee.windUpTicks());
        assertEquals(DamageTier.MEDIUM, melee.damage());
        assertEquals(Set.of(UseCondition.TARGET_IN_MELEE), melee.bestWhen());
        assertEquals(PurposeOrigin.SKILL, melee.origin());
        assertEquals(CooldownClass.UNKNOWN, melee.cooldown());

        var ranged = complete(facts(10, OptionalDouble.of(16), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true), "DAMAGE", "HITSCAN", "DAMAGE_LOW", "WHEN_TARGET_AT_RANGE", "WHEN_TARGET_FLEEING",
                "RISK_NEEDS_LINE_OF_SIGHT");
        assertEquals(ReachMeasure.POSITIONS_STRICT, ranged.reachMeasure());
        assertEquals(OptionalDouble.of(16), ranged.reachBlocks());
        assertEquals(10, ranged.windUpTicks());
        assertEquals(DamageTier.LOW, ranged.damage());
        assertEquals(Set.of(SkillRisk.NEEDS_LINE_OF_SIGHT), ranged.risks());

        var defense = complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false,
                false), "SUSTAIN", "SELF", "WHEN_SELF_LOW_HEALTH");
        assertEquals(SkillRole.SUSTAIN, defense.primary());
        assertEquals(Delivery.SELF, defense.delivery());
        assertEquals(DamageTier.NONE, defense.damage());
        assertEquals(0, defense.windUpTicks());
        assertTrue(defense.reachBlocks().isEmpty());

        var slam = complete(facts(15, OptionalDouble.empty(), OptionalDouble.of(3), OptionalDouble.empty(), false,
                true), "CONTROL", "DAMAGE", "AREA_AROUND_SELF", "DAMAGE_MEDIUM", "WHEN_ENEMIES_GROUPED",
                "WHEN_TARGET_IN_MELEE", "RISK_ROOTS_CASTER");
        assertEquals(SkillRole.CONTROL, slam.primary());
        assertEquals(Set.of(SkillRole.CONTROL, SkillRole.DAMAGE), slam.roles());
        assertEquals(Delivery.AREA_AROUND_SELF, slam.delivery());
        assertEquals(OptionalDouble.of(3), slam.radiusBlocks());
        assertTrue(slam.reachBlocks().isEmpty());
        assertEquals(ReachMeasure.POSITIONS_STRICT, slam.reachMeasure());
        assertEquals(15, slam.windUpTicks());
        assertEquals(DamageTier.MEDIUM, slam.damage());
        assertEquals(Set.of(SkillRisk.ROOTS_CASTER), slam.risks());
    }

    @Test
    void aConeOrAnAreaThatTheFactsCannotMeasureStaysIncomplete() {
        var cone = finish(NativeSkillFacts.NONE, "DAMAGE", "CONE");
        assertEquals(Optional.of("CONE needs @EntitiesInCone{range} or targetwithin{d}"), cone.problem());
        assertTrue(cone.purpose().isEmpty());
        var area = finish(NativeSkillFacts.NONE, "DAMAGE", "AREA_AROUND_SELF");
        assertEquals(Optional.of("AREA_AROUND_SELF needs a radius targeter such as @PlayersInRadius{r}"),
                area.problem());
        assertTrue(area.purpose().isEmpty());
    }

    @Test
    void nativeFactsFillReachMeasureRadiusShieldAndDamage() {
        var cone = complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.of(8), false, true),
                "DAMAGE", "CONE");
        assertEquals(ReachMeasure.POSITIONS, cone.reachMeasure());
        assertEquals(OptionalDouble.of(8), cone.reachBlocks());
        var strict = complete(facts(0, OptionalDouble.of(12), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true), "DAMAGE", "CONE");
        assertEquals(ReachMeasure.POSITIONS_STRICT, strict.reachMeasure());
        assertEquals(OptionalDouble.of(12), strict.reachBlocks());
        var melee = complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true), "DAMAGE", "MELEE");
        assertEquals(ReachMeasure.BOX_GAP, melee.reachMeasure());
        assertEquals(OptionalDouble.of(MovementSettings.DEFAULTS.meleeReachBlocks()), melee.reachBlocks());
        assertTrue(complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), true,
                true), "DAMAGE", "MELEE").ignoresShield());
        assertFalse(complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), true,
                true), "DAMAGE", "MELEE", "RISK_BLOCKED_BY_SHIELD").ignoresShield());
        var area = complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), false,
                true), "DAMAGE", "AREA_AT_TARGET");
        assertEquals(OptionalDouble.of(PurposeRules.DEFAULT_AREA_RADIUS_BLOCKS), area.radiusBlocks());
        assertEquals(DamageTier.UNRATED, area.damage());
        var projectile = complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                false, true), "DAMAGE", "PROJECTILE");
        assertTrue(projectile.reachBlocks().isEmpty());
        assertEquals(ReachMeasure.CENTRES, projectile.reachMeasure());
        assertEquals(DamageTier.UNRATED, projectile.damage());
        assertEquals(DamageTier.NONE, complete(facts(0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), false, false), "SUSTAIN", "SELF").damage());
    }

    private static SkillPurpose complete(NativeSkillFacts facts, String... tokens) {
        var completion = finish(facts, tokens);
        assertTrue(completion.problem().isEmpty(), String.valueOf(completion.problem()));
        return completion.purpose().orElseThrow();
    }

    private static SkillIntentions.Completion finish(NativeSkillFacts facts, String... tokens) {
        var parsed = IntentionVocabulary.parse(Arrays.asList(tokens));
        assertInstanceOf(IntentionParse.Parsed.class, parsed);
        return SkillIntentions.complete((IntentionParse.Parsed) parsed, facts, MovementSettings.DEFAULTS);
    }

    private static NativeSkillFacts facts(int windUp, OptionalDouble within, OptionalDouble radius, OptionalDouble cone,
                                          boolean ignoresShield, boolean damaging) {
        return new NativeSkillFacts(OptionalDouble.empty(), 1, NativeSkillFacts.SEQUENTIAL, OptionalInt.of(windUp),
                within, radius, cone, ignoresShield, false, false, damaging);
    }
}
