package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.context;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobFactsTest {
    @Test
    void setsAreCopied() {
        var contexts = new HashSet<>(Set.of(context("low_health")));
        var eligible = new HashSet<>(Set.of(tactic("hold_ground")));
        var cues = EnumSet.of(MobCue.IN_WATER);
        var facts = new MobFacts(MOB, HealthBand.CRITICAL, Optional.empty(), contexts, eligible, cues, Set.of());
        contexts.clear();
        eligible.clear();
        cues.add(MobCue.CLIMBING);
        assertEquals(Set.of(context("low_health")), facts.activeContexts());
        assertEquals(Set.of(tactic("hold_ground")), facts.eligibleTactics());
        assertEquals(Set.of(MobCue.IN_WATER), facts.cues());
    }

    @Test
    void theTacticsEligibleWhenReadyAreCopiedAndDefaultToTheTacticsEligibleNow() {
        var now = Set.of(tactic("keep_range"));
        var whenReady = new HashSet<>(Set.of(tactic("press_attack"), tactic("keep_range")));
        var facts = new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of(), Map.of(),
                whenReady);
        whenReady.clear();
        assertEquals(Set.of(tactic("press_attack"), tactic("keep_range")), facts.eligibleWhenReady());
        assertEquals(now, facts.eligibleTactics());
        assertEquals(now, new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of())
                .eligibleWhenReady());
        assertEquals(now, new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of(),
                Map.of()).eligibleWhenReady());
        assertThrows(NullPointerException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(),
                now, Set.of(), Set.of(), Map.of(), null));
    }

    @Test
    void aTacticEligibleNowMustAlsoBeEligibleWhenReady() {
        var error = assertThrows(IllegalArgumentException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), Set.of(tactic("keep_range")), Set.of(), Set.of(), Map.of(),
                Set.of(tactic("press_attack"))));
        assertEquals("eligibleWhenReady must contain every eligible tactic", error.getMessage());
    }

    @Test
    void horizonZeroCopiesTheTacticsEligibleNow() {
        var now = Set.of(tactic("keep_range"));
        var whenReady = Set.of(tactic("keep_range"), tactic("ready_in_twenty_one"));
        var facts = new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of(),
                Map.of(), whenReady);
        assertEquals(now, facts.eligibleSoon());
        assertEquals(now, facts.eligibleTactics());
    }

    @Test
    void eligibleSoonSitsBetweenTheTacticsEligibleNowAndThoseEligibleWhenReady() {
        var now = Set.of(tactic("keep_range"));
        var soon = Set.of(tactic("keep_range"), tactic("ready_in_twenty"));
        var whenReady = Set.of(tactic("keep_range"), tactic("ready_in_twenty"), tactic("ready_in_twenty_one"));
        var facts = new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of(),
                Map.of(), whenReady, soon);
        assertEquals(soon, facts.eligibleSoon());
        assertTrue(facts.eligibleSoon().containsAll(facts.eligibleTactics()));
        assertTrue(facts.eligibleWhenReady().containsAll(facts.eligibleSoon()));
        var missingNow = assertThrows(IllegalArgumentException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), now, Set.of(), Set.of(), Map.of(), whenReady,
                Set.of(tactic("ready_in_twenty"))));
        assertEquals("eligibleTactics must be inside eligibleSoon, inside eligibleWhenReady", missingNow.getMessage());
        var outside = assertThrows(IllegalArgumentException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), now, Set.of(), Set.of(), Map.of(), whenReady,
                Set.of(tactic("keep_range"), tactic("ready_in_twenty"), tactic("later"))));
        assertEquals("eligibleTactics must be inside eligibleSoon, inside eligibleWhenReady", outside.getMessage());
    }

    @Test
    void missingComponentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new MobFacts(MOB, null, Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of()));
    }

    @Test
    void choicesOutOfReachAreCopiedAndNeedATarget() {
        var relation = new TargetRelation(new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, true, true, false,
                false, false);
        var out = new HashSet<>(Set.of(new ChoiceId("melee")));
        var facts = new MobFacts(MOB, HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(), Set.of(), out);
        out.clear();
        assertEquals(Set.of(new ChoiceId("melee")), facts.choicesOutOfReach());
        var error = assertThrows(IllegalArgumentException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY,
                Optional.empty(), Set.of(), Set.of(), Set.of(), Set.of(new ChoiceId("melee"))));
        assertEquals("choicesOutOfReach must be empty without a target", error.getMessage());
    }

    @Test
    void theContingencyEligibilityIsCopiedHasNoNowAndDefaultsToTheTacticsEligibleNow() {
        var now = Set.of(tactic("press_attack"), tactic("keep_range"));
        var critical = Set.of(tactic("keep_range"));
        var facts = new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of(),
                Map.of(Situation.IF_OWN_HEALTH_CRITICAL, critical));
        assertEquals(critical, facts.eligibleIn(Situation.IF_OWN_HEALTH_CRITICAL));
        assertEquals(now, facts.eligibleIn(Situation.IF_TARGET_IN_MELEE));
        assertEquals(now, new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(), Set.of(), now, Set.of(), Set.of())
                .eligibleIn(Situation.IF_OWN_HEALTH_CRITICAL));
        assertThrows(IllegalArgumentException.class, () -> facts.eligibleIn(Situation.NOW));
        assertThrows(IllegalArgumentException.class, () -> new MobFacts(MOB, HealthBand.HEALTHY, Optional.empty(),
                Set.of(), now, Set.of(), Set.of(), Map.of(Situation.NOW, now)));
    }
}
