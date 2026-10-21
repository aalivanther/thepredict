package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.OptionContext.FitReason;
import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillRisk;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.Trend;
import akyrion.thepredict.domain.UseCondition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticPhrasesTest {
    @Test
    void everyValueOfEveryEncodedEnumHasItsOwnPhrase() {
        assertDistinct(HealthBand.values(), band -> SemanticPhrases.health(band, "mob_1"));
        assertDistinct(DistanceBand.values(), band -> SemanticPhrases.distance(band, "target"));
        assertDistinct(Trend.values(), trend -> SemanticPhrases.trend(trend, "target"));
        assertDistinct(ArmorBand.values(), band -> SemanticPhrases.armor(band, "player_1"));
        assertDistinct(MobCue.values(), cue -> SemanticPhrases.mobCue(cue, "mob_1"));
        assertDistinct(TargetCue.values(), cue -> SemanticPhrases.targetCue(cue, "player_1"));
        assertDistinct(EventType.values(), type -> SemanticPhrases.event(type, "player_1", "mob_1", false));
        assertDistinct(Movement.values(), SemanticPhrases::movement);
        assertDistinct(Delivery.values(), SemanticPhrases::delivery);
        assertDistinct(SkillRole.values(), SemanticPhrases::role);
        assertDistinct(UseCondition.values(), SemanticPhrases::useCondition);
        assertDistinct(SkillRisk.values(), SemanticPhrases::risk);
        assertDistinct(Situation.values(), situation -> ControlArmQuestions.instructions(situation, "mob_1"));
    }

    @Test
    void phrasesUseThePlanWordingWhenTheSubjectIsTheTarget() {
        assertEquals("target is inside melee reach", SemanticPhrases.distance(DistanceBand.MELEE, "target"));
        assertEquals("target is beyond every attack range", SemanticPhrases.distance(DistanceBand.BEYOND, "target"));
        assertEquals("target is backing away", SemanticPhrases.trend(Trend.BACKING_AWAY, "target"));
        assertEquals("target is closing in", SemanticPhrases.trend(Trend.CLOSING, "target"));
        assertEquals("target is raising a shield but not blocking yet",
                SemanticPhrases.targetCue(TargetCue.RAISING_SHIELD, "target"));
        assertEquals("target's shield is disabled", SemanticPhrases.targetCue(TargetCue.SHIELD_DISABLED, "target"));
        assertEquals("target is heavily armored", SemanticPhrases.armor(ArmorBand.HEAVY, "target"));
        assertEquals("stay between the inner and outer distance band", SemanticPhrases.movement(Movement.KEEP_DISTANCE));
        assertEquals("target cannot be reached on foot", SemanticPhrases.unreachable());
        assertEquals("no line of sight to target", SemanticPhrases.notVisible());
    }

    @Test
    void headcountsAreWordsNotNumbers() {
        assertEquals("one player and one mob are involved", SemanticPhrases.headcount(1, 0, 1));
        assertEquals("no players and four mobs are involved", SemanticPhrases.headcount(0, 0, 4));
        assertEquals("several players and many mobs are involved", SemanticPhrases.headcount(8, 0, 32));
        assertEquals("many players and several mobs are involved", SemanticPhrases.headcount(16, 0, 5));
    }

    @Test
    void threatRanksAreWords() {
        assertEquals("player_1 is the highest threat", SemanticPhrases.threat(1, "player_1"));
        assertEquals("player_4 is the third highest threat", SemanticPhrases.threat(3, "player_4"));
        assertEquals("player_9 is a lesser threat", SemanticPhrases.threat(16, "player_9"));
    }

    private static <E> void assertDistinct(E[] values, Function<E, String> phrase) {
        var seen = new HashSet<String>();
        for (var value : values) {
            var text = phrase.apply(value);
            assertFalse(text.isBlank(), value + " has no phrase");
            assertTrue(seen.add(text), value + " repeats a phrase");
            assertFalse(text.replaceAll("(mob|player)_1", "").matches(".*[0-9].*"), value + " contains a number");
        }
    }

    @Test
    void reachBandsUseTheDistanceThresholdsOfTheProfile() {
        var defaults = MovementSettings.DEFAULTS;
        assertEquals("only inside melee reach", SemanticPhrases.reach(3.0, defaults));
        assertEquals("a short distance beyond melee reach", SemanticPhrases.reach(3.0001, defaults));
        assertEquals("a short distance beyond melee reach", SemanticPhrases.reach(9, defaults));
        assertEquals("within ranged reach", SemanticPhrases.reach(9.0001, defaults));
        assertEquals("within ranged reach", SemanticPhrases.reach(16, defaults));
        assertEquals("beyond ranged reach", SemanticPhrases.reach(16.0001, defaults));
        var own = new MovementSettings(4, 6, 12, 20, 10, 1.2, 1.15, OptionalDouble.empty());
        assertEquals("only inside melee reach", SemanticPhrases.reach(4.5, own));
        assertEquals("a short distance beyond melee reach", SemanticPhrases.reach(12, own));
        assertEquals("within ranged reach", SemanticPhrases.reach(20, own));
        assertEquals("beyond ranged reach", SemanticPhrases.reach(20.5, own));
    }

    @Test
    void areaWindUpCooldownAndDamageAreWordsAndSayNothingWhenUnknownOrNone() {
        assertEquals("in a small area", SemanticPhrases.area(3));
        assertEquals("in a medium area", SemanticPhrases.area(3.0001));
        assertEquals("in a medium area", SemanticPhrases.area(6));
        assertEquals("in a large area", SemanticPhrases.area(6.0001));
        assertEquals("at once", SemanticPhrases.windUp(0));
        assertEquals("after a short wind-up", SemanticPhrases.windUp(10));
        assertEquals("after a long, visible wind-up", SemanticPhrases.windUp(11));
        assertEquals("can be used again soon", SemanticPhrases.cooldown(CooldownClass.NONE));
        assertEquals("can be used again soon", SemanticPhrases.cooldown(CooldownClass.SHORT));
        assertEquals("has a moderate cooldown", SemanticPhrases.cooldown(CooldownClass.MEDIUM));
        assertEquals("has a long cooldown", SemanticPhrases.cooldown(CooldownClass.LONG));
        assertEquals("", SemanticPhrases.cooldown(CooldownClass.UNKNOWN));
        assertEquals("", SemanticPhrases.damage(DamageTier.NONE));
        assertEquals("deals damage", SemanticPhrases.damage(DamageTier.UNRATED));
        assertEquals("deals high damage", SemanticPhrases.damage(DamageTier.HIGH));
        assertEquals("goes through shields", SemanticPhrases.ignoresShield());
        assertEquals("target stands close to other enemies", SemanticPhrases.grouped());
    }

    @Test
    void everyShortFormAndEveryReasonHasItsOwnPhrase() {
        assertDistinct(Movement.values(), SemanticPhrases::movementShort);
        assertDistinct(Delivery.values(), SemanticPhrases::deliveryShort);
        assertDistinct(Arrays.stream(SkillRole.values()).filter(role -> role != SkillRole.DAMAGE)
                .toArray(SkillRole[]::new), SemanticPhrases::roleShort);
        assertDistinct(UseCondition.values(), SemanticPhrases::useConditionShort);
        assertDistinct(FitReason.values(), SemanticPhrases::fitReason);
        assertDistinct(Situation.values(), situation -> SemanticPhrases.compactInstructions(situation, "mob_1"));
        assertDistinct(Situation.values(), situation -> SemanticPhrases.question(situation, "mob_1"));
        for (var reason : FitReason.values()) {
            var poor = SemanticPhrases.poorFitShort(reason);
            assertEquals(reason.ordinal() >= FitReason.OUT_OF_REACH.ordinal(), !poor.isEmpty(), reason.name());
        }
        assertEquals("the target is inside melee reach", SemanticPhrases.fitReason(FitReason.BEST_WHEN_TARGET_IN_MELEE));
        assertEquals("", SemanticPhrases.assume(Situation.NOW, "mob_1"));
        assertEquals("mob_1 is in worse shape than its target", SemanticPhrases.relativeHealth(false, "mob_1"));
    }

    @Test
    void withEntitiesTheHeadcountNamesTheOtherCreaturesAndWithoutThemItIsThatOf001() {
        assertEquals("one player and one mob are involved", SemanticPhrases.headcount(1, 0, 1));
        assertEquals("one player, one other creature and one mob are involved", SemanticPhrases.headcount(1, 1, 1));
        assertEquals("no players, two other creatures and two mobs are involved", SemanticPhrases.headcount(0, 2, 2));
    }

    @Test
    void theSpeciesOfAnEntityIsItsKeyWithSpacesAndTheRightArticle() {
        assertEquals("entity_1 is a zombie", SemanticPhrases.species("entity_1", "zombie"));
        assertEquals("entity_2 is a wither skeleton", SemanticPhrases.species("entity_2", "wither_skeleton"));
        assertEquals("target is an enderman", SemanticPhrases.species("target", "enderman"));
        assertEquals("entity_3 is an iron golem", SemanticPhrases.species("entity_3", "iron_golem"));
    }

    @Test
    void anAssistOfAnEntityIsNotCalledAHelpToAnotherPlayer() {
        assertEquals("player_1 helped another player", SemanticPhrases.event(EventType.ALLY_ASSISTED, "player_1",
                "mob_1", false));
        assertEquals("entity_1 helped an ally", SemanticPhrases.event(EventType.ALLY_ASSISTED, "entity_1", "mob_1",
                true));
    }
}
