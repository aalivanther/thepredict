package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EncounterEvent;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.MobCue;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import static akyrion.thepredict.adapter.server.context.CaptureDoubles.mobId;
import static akyrion.thepredict.adapter.server.context.CaptureDoubles.playerId;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.context;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.rule;
import static akyrion.thepredict.adapter.server.context.ContextFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactsAssemblerTest {
    private static final EncounterId ENCOUNTER = new EncounterId(1);
    private static final MobKey MOB = new MobKey(mobId(1), 0);

    private final CaptureDoubles doubles = new CaptureDoubles();

    private CombatFacts capture(Set<MobKey> allow, Map<MobKey, Double> previous, Set<MobKey> unreachable,
                                List<EncounterEvent> events) {
        return doubles.capture().capture(ENCOUNTER, List.of(MOB), doubles.context(allow, previous, unreachable, events))
                .orElseThrow();
    }

    private CombatFacts capture() {
        return capture(Set.of(), Map.of(), Set.of(), List.of());
    }

    private CaptureDoubles.FakePlayer targetAt(double x, double z) {
        var target = doubles.player(1, x, z);
        doubles.source.targets.put(mobId(1), target.player);
        return target;
    }

    private TargetRelation relation(CombatFacts facts) {
        return facts.mobs().getFirst().target().orElseThrow();
    }

    private static ParticipantReading reading(boolean blocking, boolean invisible) {
        return new ParticipantReading(20, 0, 20, 0, 0, blocking, blocking, false, 1.0f, false, 0, false, false, false,
                0, false, false, 0f, false, invisible);
    }

    @Test
    void anInvisibleTargetShowsOnlyInvisibleAndIsNotVisible() {
        var mob = doubles.mob(1, 0, 0);
        targetAt(2, 0);
        doubles.reads.readings.put(playerId(1), reading(true, true));
        var facts = capture();
        assertEquals(Set.of(TargetCue.INVISIBLE), facts.participants().getFirst().cues());
        assertFalse(relation(facts).visible());
        assertFalse(relation(facts).flankable());
        assertEquals(1, mob.sightChecks);
    }

    @Test
    void aPlayerBehindAWallShowsNoCueAndTheMobHasNoLineOfSight() {
        var mob = doubles.mob(1, 0, 0);
        targetAt(2, 0);
        doubles.reads.readings.put(playerId(1), reading(true, false));
        mob.sight = player -> false;
        var facts = capture();
        assertEquals(Set.of(), facts.participants().getFirst().cues());
        assertFalse(relation(facts).visible());
        assertEquals(Set.of(MobCue.NO_LINE_OF_SIGHT), facts.mobs().getFirst().cues());
    }

    @Test
    void eachMobPlayerLineOfSightIsComputedOnce() {
        var mob = doubles.mob(1, 0, 0);
        targetAt(2, 0);
        doubles.player(2, 3, 0);
        capture();
        assertEquals(2, mob.sightChecks);
    }

    @Test
    void aNewPathIsComputedOnlyWithPermissionAndOtherwiseTheLastValueIsKept() {
        doubles.mob(1, 0, 0);
        targetAt(4, 0);
        doubles.reads.pathReaches = false;
        assertTrue(relation(capture()).reachable());
        assertFalse(relation(capture(Set.of(), Map.of(), Set.of(MOB), List.of())).reachable());
        assertEquals(0, doubles.reads.findPathCalls);
        assertFalse(relation(capture(Set.of(MOB), Map.of(), Set.of(), List.of())).reachable());
        assertEquals(1, doubles.reads.findPathCalls);
        doubles.reads.path = EntityReads.CurrentPath.REACHES;
        assertTrue(relation(capture(Set.of(MOB), Map.of(), Set.of(MOB), List.of())).reachable());
        assertEquals(1, doubles.reads.findPathCalls);
    }

    @Test
    void theTrendIsSteadyWithoutAKinematicWordAndIsCorrectedByDistance() {
        doubles.mob(1, 0, 0);
        targetAt(0, -3);
        assertEquals(Trend.STEADY, relation(capture()).trend());
        assertEquals(Trend.STEADY, relation(capture(Set.of(), Map.of(MOB, 4.0), Set.of(), List.of())).trend());
        assertEquals(Trend.CLOSING, relation(capture(Set.of(), Map.of(MOB, Math.nextUp(4.0)), Set.of(), List.of())).trend());
        assertEquals(Trend.BACKING_AWAY, relation(capture(Set.of(), Map.of(MOB, 1.5), Set.of(), List.of())).trend());
    }

    @Test
    void aShieldFacingTheMobIsFlankableAndMakesFlankEligible() {
        doubles.mob(1, 0, 2);
        targetAt(0, 0);
        doubles.reads.readings.put(playerId(1), reading(true, false));
        doubles.reads.shieldAngle = OptionalDouble.of(90);
        var facts = capture();
        assertTrue(relation(facts).flankable());
        assertTrue(facts.mobs().getFirst().eligibleTactics().contains(tactic("flank_and_strike")));
    }

    @Test
    void theBlockingAngleOfNinetyDegreesDecidesFlankability() {
        doubles.reads.readings.put(playerId(1), reading(true, false));
        doubles.reads.shieldAngle = OptionalDouble.of(ShieldGeometry.DEFAULT_BLOCKING_ANGLE_DEGREES);
        var mob = doubles.mob(1, 0, 0);
        targetAt(0, 0);
        for (var angle : new double[] {0.0, 89.9, 90.0}) {
            place(mob, angle);
            assertTrue(relation(capture()).flankable(), "angle " + angle);
        }
        for (var angle : new double[] {90.1, 180.0}) {
            place(mob, angle);
            assertFalse(relation(capture()).flankable(), "angle " + angle);
            assertFalse(capture().mobs().getFirst().eligibleTactics().contains(tactic("flank_and_strike")));
        }
    }

    private static void place(CaptureDoubles.FakeMob mob, double angleDegrees) {
        var radians = Math.toRadians(angleDegrees);
        mob.x = -Math.sin(radians) * 2;
        mob.z = Math.cos(radians) * 2;
    }

    @Test
    void theMobIsOutsideTheTargetReachOnlyBeyondIt() {
        doubles.mob(1, 0, 0);
        targetAt(3, 0);
        assertFalse(relation(capture()).outsideTargetReach());
        doubles.reads.reach = TargetReach.player(Math.nextDown(3.0), false, 0.0f);
        assertTrue(relation(capture()).outsideTargetReach());
    }

    @Test
    void threatRanksAddTheThreatOfEveryMember() {
        doubles.mob(1, 0, 0);
        doubles.mob(2, 0, 0);
        doubles.player(1, 1, 0);
        doubles.player(2, 5, 0);
        doubles.source.threat.put(mobId(1), Map.of(playerId(1), 3.0, playerId(2), 2.0));
        doubles.source.threat.put(mobId(2), Map.of(playerId(2), 2.0));
        var facts = doubles.capture().capture(ENCOUNTER, List.of(MOB, new MobKey(mobId(2), 0)), doubles.context())
                .orElseThrow();
        var ranks = facts.participants().stream().collect(Collectors.toMap(ParticipantFacts::alias,
                ParticipantFacts::threatRank));
        assertEquals(Map.of(doubles.aliases.get(playerId(2)), 1, doubles.aliases.get(playerId(1)), 2), ranks);
    }

    @Test
    void theLeaderOfTheTableHasRankOneAndEveryOtherPlayerAUniqueLowerRankWithOrWithoutATable() {
        doubles.mob(1, 0, 0);
        doubles.player(1, 2, 0);
        doubles.player(2, 4, 0);
        doubles.player(3, 6, 0);
        var ranks = ranks(capture());
        assertEquals(Map.of(doubles.aliases.get(playerId(1)), 1, doubles.aliases.get(playerId(2)), 2,
                doubles.aliases.get(playerId(3)), 3), ranks, "without threat the nearest leads, never a shared rank");
        doubles.source.threat.put(mobId(1), Map.of(playerId(3), 6.0, playerId(1), 1.5));
        ranks = ranks(capture());
        assertEquals(Map.of(doubles.aliases.get(playerId(3)), 1, doubles.aliases.get(playerId(1)), 2,
                doubles.aliases.get(playerId(2)), 3), ranks, "an enriched gain that makes a player lead moves it to 1");
    }

    private Map<ParticipantAlias, Integer> ranks(CombatFacts facts) {
        return facts.participants().stream().collect(Collectors.toMap(ParticipantFacts::alias,
                ParticipantFacts::threatRank));
    }

    @Test
    void aFreshShieldDisableEventMarksTheShieldDisabledForTwentyTicks() {
        doubles.mob(1, 0, 0);
        targetAt(2, 0);
        var alias = new ParticipantAlias(1);
        var fresh = new EncounterEvent(doubles.tick - FactsAssembler.SHIELD_DISABLE_EVENT_TICKS,
                EventType.SHIELD_DISABLED, Optional.of(alias), Optional.empty());
        var stale = new EncounterEvent(doubles.tick - FactsAssembler.SHIELD_DISABLE_EVENT_TICKS - 1,
                EventType.SHIELD_DISABLED, Optional.of(alias), Optional.empty());
        assertTrue(capture(Set.of(), Map.of(), Set.of(), List.of(fresh)).participants().getFirst().cues()
                .contains(TargetCue.SHIELD_DISABLED));
        var facts = capture(Set.of(), Map.of(), Set.of(), List.of(stale));
        assertFalse(facts.participants().getFirst().cues().contains(TargetCue.SHIELD_DISABLED));
        assertEquals(List.of(stale), facts.recentEvents());
    }

    @Test
    void theMobHealthBandSwitchesAtThirtyPercentAndDrivesTheLowHealthShortcut() {
        var mob = doubles.mob(1, 0, 0);
        doubles.source.members.put(mobId(1), new MemberSource.Member(mob.entity, CaptureDoubles.bound(List.of(
                rule("low_health_phase", List.of(), 90, "retreat_and_guard")), false)));
        targetAt(2, 0);
        mob.health = 6;
        var critical = capture().mobs().getFirst();
        assertEquals(HealthBand.CRITICAL, critical.health());
        assertEquals(Set.of(context("low_health_phase")), critical.activeContexts());
        assertEquals(Set.of(tactic("retreat_and_guard")), critical.eligibleTactics());
        mob.health = Math.nextUp(6.0);
        assertEquals(Set.of(), capture().mobs().getFirst().activeContexts());
        mob.health = 4;
        mob.absorption = 8;
        assertEquals(HealthBand.WOUNDED, capture().mobs().getFirst().health());
    }

    @Test
    void aBrainSpeciesOnlyGetsTacticsWithoutMovement() {
        var mob = doubles.mob(1, 0, 0);
        doubles.source.members.put(mobId(1), new MemberSource.Member(mob.entity, CaptureDoubles.bound(List.of(), true)));
        targetAt(2, 0);
        assertEquals(Set.of(tactic("stand")), capture().mobs().getFirst().eligibleTactics());
    }

    @Test
    void aSkillOnItsNativeCooldownIsNotEligible() {
        doubles.mob(1, 0, 0);
        targetAt(2, 0);
        assertTrue(capture().mobs().getFirst().eligibleTactics().contains(tactic("strike_close")));
        doubles.source.cooling.add(doubles.source.members.get(mobId(1)).profile().skills().get(ContextFixtures.STRIKE));
        var cooling = capture().mobs().getFirst();
        assertFalse(cooling.eligibleTactics().contains(tactic("strike_close")));
        assertTrue(cooling.eligibleWhenReady().contains(tactic("strike_close")),
                "the tactic is eligible when its skill is ready again (M10, real-time readiness)");
        assertTrue(cooling.eligibleWhenReady().containsAll(cooling.eligibleTactics()));
    }

    @Test
    void theTacticsEligibleWhenReadyKeepTheReachOfTheCapture() {
        doubles.mob(1, 0, 0, CaptureDoubles.declaredBound());
        var target = targetAt(2, 0);
        target.x = 8;
        var facts = capture().mobs().getFirst();
        assertFalse(facts.eligibleWhenReady().contains(tactic("keep_and_strike")),
                "a skill out of reach is not ready, whatever its cooldown");
        assertEquals(facts.eligibleTactics(), facts.eligibleWhenReady(), "nothing cools down, so both sets agree");
    }

    @Test
    void mobCuesAndCombatSecondsComeFromTheEntityAndTheLongestTracker() {
        var mob = doubles.mob(1, 0, 0);
        doubles.mob(2, 0, 0);
        mob.water = true;
        mob.lava = true;
        mob.climbing = true;
        doubles.reads.combatTicks.put(mobId(1), 250);
        doubles.reads.combatTicks.put(mobId(2), 319);
        var facts = doubles.capture().capture(ENCOUNTER, List.of(MOB, new MobKey(mobId(2), 0)), doubles.context())
                .orElseThrow();
        assertEquals(Set.of(MobCue.IN_WATER, MobCue.IN_LAVA, MobCue.CLIMBING), facts.mobs().getFirst().cues());
        assertEquals(15, facts.combatSeconds());
    }

    @Test
    void aDeclaredSkillOutOfReachIsMarkedAndItsKeepDistanceTacticIsNotEligible() {
        var mob = doubles.mob(1, 0, 0, CaptureDoubles.declaredBound());
        var target = targetAt(2, 0);
        var near = capture().mobs().getFirst();
        assertEquals(Set.of(), near.choicesOutOfReach());
        assertTrue(near.eligibleTactics().contains(tactic("keep_and_strike")));
        target.x = 8;
        var facts = capture().mobs().getFirst();
        assertEquals(Set.of(ContextFixtures.STRIKE, ContextFixtures.SLAM), facts.choicesOutOfReach());
        assertFalse(facts.eligibleTactics().contains(tactic("keep_and_strike")));
        assertTrue(facts.eligibleTactics().containsAll(Set.of(tactic("strike_close"), tactic("hold_and_strike"),
                tactic("keep_and_shoot"), tactic("slam_close"))));
        assertEquals(2, mob.boxReads);
    }

    @Test
    void withoutADeclaredReachTheCaptureReadsNoBoxAndAddsNoSightNorPath() {
        var inferred = doubles.mob(1, 0, 0);
        targetAt(8, 0);
        var facts = capture();
        assertEquals(Set.of(), facts.mobs().getFirst().choicesOutOfReach());
        assertEquals(0, inferred.boxReads);
        var sight = inferred.sightChecks;
        var paths = doubles.reads.findPathCalls;
        var declared = new CaptureDoubles();
        var mob = declared.mob(1, 0, 0, CaptureDoubles.declaredBound());
        declared.source.targets.put(mobId(1), declared.player(1, 8, 0).player);
        declared.capture().capture(ENCOUNTER, List.of(MOB), declared.context()).orElseThrow();
        assertEquals(sight, mob.sightChecks);
        assertEquals(paths, declared.reads.findPathCalls);
    }

    @Test
    void withoutATargetNoChoiceIsOutOfReach() {
        doubles.mob(1, 0, 0, CaptureDoubles.declaredBound());
        var facts = capture().mobs().getFirst();
        assertTrue(facts.target().isEmpty());
        assertEquals(Set.of(), facts.choicesOutOfReach());
    }

    @Test
    void aTargetWithAnotherPerceivedPlayerWithinFourBlocksIsGrouped() {
        doubles.mob(1, 0, 0);
        targetAt(2, 0);
        assertFalse(relation(capture()).grouped());
        var other = doubles.player(2, 6.1, 0);
        assertFalse(relation(capture()).grouped());
        other.x = 5.9;
        assertTrue(relation(capture()).grouped());
    }

    @Test
    void thePatternFactsOfARequestStopAtMaxFactsPerRequestAfterTheCapOfEachParticipant() {
        doubles.mob(1, 0, 0);
        doubles.mob(2, 0, 4);
        var first = doubles.player(1, 2, 0);
        var second = doubles.player(2, 2, 4);
        doubles.source.targets.put(mobId(1), first.player);
        doubles.source.targets.put(mobId(2), second.player);
        var admitted = java.util.EnumSet.of(akyrion.thepredict.domain.EnemyPattern.SPAMS_CLICKS,
                akyrion.thepredict.domain.EnemyPattern.CRIT_JUMPER,
                akyrion.thepredict.domain.EnemyPattern.SHIELD_TURTLE);
        var capture = doubles.capture();
        capture.usePatternFacts(key -> admitted);
        capture.useFactCap(2);
        var mobs = List.of(MOB, new MobKey(mobId(2), 0));
        var uncapped = capture.capture(ENCOUNTER, mobs, doubles.context()).orElseThrow().participants().stream()
                .mapToInt(participant -> participant.patterns().size()).sum();
        assertEquals(4, uncapped, "two participants, two facts each, under the delivered request cap of four");
        capture.useRequestFactCap(3);
        var facts = capture.capture(ENCOUNTER, mobs, doubles.context()).orElseThrow().participants();
        assertEquals(List.of(2, 1), facts.stream().map(participant -> participant.patterns().size()).toList(),
                "DOC-09: the request keeps three facts, the first participant its two");
    }

    @Test
    void theCaptureComputesWhatEachContingencyCouldOfferOnlyWithATarget() {
        var mob = doubles.mob(1, 0, 0);
        doubles.source.members.put(mobId(1), new MemberSource.Member(mob.entity, CaptureDoubles.bound(List.of(
                rule("low_health_phase", List.of(), 90, "retreat_and_guard")), false)));
        targetAt(6, 0);
        var healthy = capture().mobs().getFirst();
        assertEquals(Set.of(Situation.IF_TARGET_IN_MELEE, Situation.IF_OWN_HEALTH_CRITICAL),
                healthy.contingencyEligible().keySet());
        assertEquals(Set.of(tactic("retreat_and_guard")), healthy.eligibleIn(Situation.IF_OWN_HEALTH_CRITICAL),
                "the shortcut low_health_phase is evaluated under the hypothesis");
        assertTrue(healthy.eligibleTactics().size() > 1);
        doubles.source.targets.clear();
        var alone = capture().mobs().getFirst();
        assertTrue(alone.contingencyEligible().isEmpty());
    }
}
