package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GoalSummary;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.InstallResult;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TargetRelation;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.Trend;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.EngineFixtures.OTHER_WORLD;
import static akyrion.thepredict.engine.EngineFixtures.WORLD;
import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.EngineFixtures.near;
import static akyrion.thepredict.engine.EngineFixtures.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M11.2 to M11.4 in the engine: facts, windows, decay and the port, with doubles and a controllable tick (P2). */
class ThreatEnrichmentTest {
    private static final ThreatWeights DEFAULTS = new ThreatWeights(1.5, 1.0, 0.25, 2.0, 20.0);

    private final EngineFixtures.Sessions fixture = new EngineFixtures.Sessions();
    private final List<Change> changes = new ArrayList<>();
    private final Set<UUID> failing = new HashSet<>();
    private final List<String> log = new ArrayList<>();
    private final Paths paths = new Paths();
    private final ThreatEnrichment threat = threat(true);

    private ThreatEnrichment threat(boolean logDecisions) {
        var enrichment = new ThreatEnrichment(fixture.encounters, fixture.registry, new Actions(), fixture.clock);
        enrichment.logTo(new ExecutionLog(log::add, logDecisions));
        return enrichment;
    }

    private MobSession open(int mob, ThreatWeights weights) {
        return fixture.registry.open(id(mob), EngineFixtures.profile("Guardian", weights));
    }

    private Encounter engage(MobSession session, int target) {
        return fixture.encounters.assign(session, WORLD, player(target), near());
    }

    private void join(Encounter encounter, int... players) {
        for (var value : players) {
            fixture.encounters.addParticipant(encounter.id(), player(value));
        }
    }

    /** What the capture of this evaluation left in the session: its target and whether it was reachable. */
    private static void sees(MobSession session, int target, boolean reachable) {
        session.recordTarget(player(target), 6.0);
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(new TargetRelation(
                new ParticipantAlias(1), DistanceBand.RANGED, Trend.STEADY, reachable, true, false, false, false)), Set.of(),
                Set.of(), Set.of(), Set.of()));
    }

    /** One evaluation after a capture that saw the target of {@code session} as given. */
    private int evaluate(Encounter encounter, MobSession session, int target, boolean reachable, long tick) {
        sees(session, target, reachable);
        return evaluateWithoutCapture(encounter, tick);
    }

    /** One evaluation whose capture did not read the member: its facts are those of the previous capture. */
    private int evaluateWithoutCapture(Encounter encounter, long tick) {
        fixture.clock.tick = tick;
        return threat.evaluated(encounter, tick, paths);
    }

    @Test
    void aBlockedHitAddsTheBlockedHitWeightToTheVictimInTheAttackerTable() {
        var mob = open(1, DEFAULTS);
        engage(mob, 1);
        assertEquals(0.25, threat.blockedHit(id(1), player(1)));
        assertEquals(List.of(new Change(mob.key(), player(1), 0.25)), changes);
        assertEquals(1, threat.metrics().blockedHits());
        assertEquals(0.25, threat.metrics().pointsAdded());
    }

    @Test
    void rangedHarassAddsTheWeightTimesTheFinalDamageExceptToTheTarget() {
        var mob = open(1, new ThreatWeights(1.5, 2.0, 0.25, 2.0, 20.0));
        join(engage(mob, 1), 2);
        assertEquals(0.0, threat.rangedHit(id(1), player(1), 4.0, true), "the target already gains native threat");
        assertEquals(8.0, threat.rangedHit(id(1), player(2), 4.0, false));
        assertEquals(0.0, threat.rangedHit(id(1), player(2), -1.0, false));
        assertEquals(0.0, threat.rangedHit(id(1), player(2), Double.NaN, false));
        assertEquals(List.of(new Change(mob.key(), player(2), 8.0)), changes);
        assertEquals(1, threat.metrics().rangedHarass());
    }

    @Test
    void anAssistAddsTheWeightPerOtherParticipantInTheTableOfEveryActiveMember() {
        var first = open(1, DEFAULTS);
        var second = open(2, DEFAULTS);
        var encounter = engage(first, 1);
        engage(second, 1);
        join(encounter, 2, 3);
        var stranger = player(9);
        assertEquals(2, threat.assisted(WORLD, player(3), List.of(player(1), player(2), player(3), stranger,
                player(2))));
        assertEquals(List.of(new Change(first.key(), player(3), 3.0), new Change(second.key(), player(3), 3.0)),
                changes, "two other participants, each counted once; the source and a stranger do not count");
        assertEquals(2, threat.metrics().assists());
    }

    @Test
    void anAssistThatReachesNoOtherParticipantAddsNothing() {
        var encounter = engage(open(1, DEFAULTS), 1);
        join(encounter, 2);
        assertEquals(0, threat.assisted(WORLD, player(2), List.of(player(2), player(9))));
        assertEquals(0, threat.assisted(OTHER_WORLD, player(2), List.of(player(1))));
        assertTrue(changes.isEmpty());
        assertEquals(0, encounter.threat().windowCount());
    }

    @Test
    void eventsOutsideAnActiveEncounterOrFromOtherPlayersDoNoWork() {
        var encounter = engage(open(1, DEFAULTS), 1);
        open(2, DEFAULTS);
        assertEquals(0.0, threat.blockedHit(id(9), player(1)), "unknown mob");
        assertEquals(0.0, threat.blockedHit(id(2), player(1)), "bound mob outside every encounter");
        assertEquals(0.0, threat.rangedHit(id(1), player(7), 5.0, false), "shooter who is not a participant");
        fixture.registry.suspend(id(1));
        assertEquals(0.0, threat.blockedHit(id(1), player(1)), "suspended mob");
        assertTrue(changes.isEmpty());
        assertEquals(0, encounter.threat().windowCount());
        assertEquals(0, threat.metrics().limited());
    }

    @Test
    void aHugeOrNonFiniteDamageNeverThrowsAndIsCappedByTheWindow() {
        var mob = open(1, new ThreatWeights(1.5, 2.0, 0.25, 2.0, 20.0));
        join(engage(mob, 1), 2);
        assertEquals(20.0, threat.rangedHit(id(1), player(2), Double.MAX_VALUE, false));
        fixture.clock.tick = 100;
        assertEquals(0.0, threat.rangedHit(id(1), player(2), Double.POSITIVE_INFINITY, false));
        assertEquals(0.0, threat.rangedHit(id(1), player(2), Double.NaN, false));
        assertEquals(20.0, threat.rangedHit(id(1), player(2), 1e308, false));
        assertEquals(List.of(new Change(mob.key(), player(2), 20.0), new Change(mob.key(), player(2), 20.0)), changes);
        assertEquals(0, threat.metrics().failures());
    }

    @Test
    void repeatedFactsAreCappedPerWindowAndTheNextWindowOpensAtItsBoundary() {
        var mob = open(1, DEFAULTS);
        join(engage(mob, 1), 2);
        fixture.clock.tick = 0;
        assertEquals(12.0, threat.rangedHit(id(1), player(2), 12.0, false));
        fixture.clock.tick = 40;
        assertEquals(8.0, threat.rangedHit(id(1), player(2), 12.0, false));
        fixture.clock.tick = 99;
        assertEquals(0.0, threat.rangedHit(id(1), player(2), 12.0, false));
        assertEquals(1, threat.metrics().limited());
        fixture.clock.tick = 100;
        assertEquals(12.0, threat.rangedHit(id(1), player(2), 12.0, false));
        assertEquals(32.0, changes.stream().mapToDouble(Change::amount).sum());
        assertTrue(changes.stream().allMatch(change -> change.amount() <= DEFAULTS.maxPerWindow()));
    }

    @Test
    void aZeroWeightDisablesItsFactWithoutOpeningAWindow() {
        var encounter = engage(open(1, new ThreatWeights(0, 1.0, 0, 2.0, 20.0)), 1);
        join(encounter, 2);
        assertEquals(0.0, threat.blockedHit(id(1), player(1)));
        assertEquals(0, threat.assisted(WORLD, player(1), List.of(player(2))));
        assertEquals(3.0, threat.rangedHit(id(1), player(2), 3.0, false));
        assertEquals(1, encounter.threat().windowCount());
        assertEquals(0, threat.metrics().limited());
    }

    @Test
    void anUnreachableTargetLosesThreatFromTheSecondConsecutiveDecisionForTheSecondsBetweenThem() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        paths.reaches = false;
        assertEquals(0, evaluate(encounter, mob, 1, false, 0), "the first unreachable decision only marks it");
        assertEquals(1, evaluate(encounter, mob, 1, false, 20));
        assertEquals(1, evaluate(encounter, mob, 1, false, 60));
        assertEquals(List.of(new Change(mob.key(), player(1), -2.0), new Change(mob.key(), player(1), -4.0)), changes);
        assertEquals(0, evaluate(encounter, mob, 1, true, 80));
        assertEquals(0, evaluate(encounter, mob, 1, false, 100), "reachable in between: the count starts over");
        assertEquals(2, threat.metrics().decays());
        assertEquals(6.0, threat.metrics().pointsRemoved());
    }

    @Test
    void aStaleUnreachableValueWithoutALivePathNeverDecays() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        paths.hasPath = false;
        paths.reaches = false;
        for (var tick = 0; tick <= 200; tick += 20) {
            assertEquals(0, evaluate(encounter, mob, 1, false, tick));
        }
        assertTrue(changes.isEmpty(), "a mob next to its target has no path: nothing is subtracted");
        assertEquals(0, encounter.threat().unreachableCount());
    }

    @Test
    void aDecisionWithoutANewCaptureRestartsTheCount() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        paths.reaches = false;
        assertEquals(0, evaluate(encounter, mob, 1, false, 0));
        assertEquals(0, evaluateWithoutCapture(encounter, 20), "stale facts never decay");
        assertEquals(0, evaluate(encounter, mob, 1, false, 40), "the count restarts at the next capture");
        assertEquals(1, evaluate(encounter, mob, 1, false, 60));
        assertEquals(List.of(new Change(mob.key(), player(1), -2.0)), changes);
    }

    @Test
    void aTargetSeenReachableOrReachedByTheCurrentPathNeverDecays() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        evaluate(encounter, mob, 1, true, 0);
        evaluate(encounter, mob, 1, true, 20);
        assertEquals(0, paths.reads, "a target the capture saw reachable costs no reading");
        paths.reaches = true;
        evaluate(encounter, mob, 1, false, 40);
        evaluate(encounter, mob, 1, false, 60);
        assertEquals(2, paths.reads);
        mob.loseTarget();
        evaluateWithoutCapture(encounter, 80);
        assertTrue(changes.isEmpty());
        assertEquals(0, encounter.threat().unreachableCount());
    }

    @Test
    void theNativeUnreachableDecayReplacesOursWithoutAnyReading() {
        var mob = open(1, new ThreatWeights(1.5, 1.0, 0.25, 0.0, 20.0));
        var encounter = engage(mob, 1);
        paths.reaches = false;
        evaluate(encounter, mob, 1, false, 0);
        evaluate(encounter, mob, 1, false, 20);
        evaluate(encounter, mob, 1, false, 40);
        assertTrue(changes.isEmpty());
        assertEquals(0, paths.reads);
        assertEquals(0, encounter.threat().unreachableCount());
    }

    @Test
    void anotherTargetOrAStaleGenerationNeverReceivesTheDecayOfThePreviousOne() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        join(encounter, 2);
        paths.reaches = false;
        evaluate(encounter, mob, 1, false, 0);
        assertEquals(0, evaluate(encounter, mob, 2, false, 20), "the new target is only marked");
        fixture.registry.advanceGeneration(id(1));
        assertEquals(0, evaluate(encounter, mob, 2, false, 40), "the new generation is only marked");
        assertEquals(1, evaluate(encounter, mob, 2, false, 60));
        assertEquals(List.of(new Change(new MobKey(id(1), 1), player(2), -2.0)), changes);
    }

    @Test
    void aNewGenerationHasItsOwnTableAndEveryChangeCarriesTheCurrentKey() {
        var mob = open(1, DEFAULTS);
        join(engage(mob, 1), 2);
        threat.rangedHit(id(1), player(2), 30.0, false);
        fixture.registry.advanceGeneration(id(1));
        threat.rangedHit(id(1), player(2), 30.0, false);
        assertEquals(List.of(new Change(new MobKey(id(1), 0), player(2), 20.0),
                new Change(new MobKey(id(1), 1), player(2), 20.0)), changes);
    }

    @Test
    void theWindowsAndMarksDisappearWithTheEncounter() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        threat.blockedHit(id(1), player(1));
        paths.reaches = false;
        evaluate(encounter, mob, 1, false, 0);
        assertEquals(1, encounter.threat().windowCount());
        assertEquals(1, encounter.threat().unreachableCount());
        fixture.encounters.leave(mob);
        assertTrue(encounter.isClosed());
        assertEquals(0, encounter.threat().windowCount());
        assertEquals(0, encounter.threat().unreachableCount());
    }

    @Test
    void aFailingTableChangeIsCountedAndDoesNotStopTheOtherMembers() {
        var first = open(1, DEFAULTS);
        var second = open(2, DEFAULTS);
        var encounter = engage(first, 1);
        engage(second, 1);
        join(encounter, 2);
        failing.add(id(1));
        assertEquals(1, threat.assisted(WORLD, player(2), List.of(player(1))));
        assertEquals(List.of(new Change(second.key(), player(2), 1.5)), changes);
        assertEquals(1, threat.metrics().failures());
    }

    @Test
    void eachChangeIsLoggedWithTheAliasOnlyAndOnlyWithDecisionLogging() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        encounter.aliasFor(player(1), ParticipantKind.PLAYER);
        join(encounter, 2);
        threat.blockedHit(id(1), player(1));
        assertEquals(List.of("Threat +0.25 MELEE_BLOCKED for player_1 sent to the table of mob " + id(1)), log);
        assertFalse(log.getFirst().contains(player(1).toString()));
        threat.rangedHit(id(1), player(2), 2.0, false);
        assertEquals("Threat +2.00 RANGED_HIT_ON_MOB for a participant without alias sent to the table of mob " + id(1),
                log.get(1));
        assertTrue(encounter.aliases().find(player(2)).isEmpty(), "logging never assigns an alias");
        log.clear();
        var quiet = threat(false);
        var unwired = new ThreatEnrichment(fixture.encounters, fixture.registry, new Actions(), fixture.clock);
        fixture.clock.tick = 200;
        quiet.blockedHit(id(1), player(1));
        unwired.blockedHit(id(1), player(1));
        assertTrue(log.isEmpty(), "without decision logging, or before the log is shared, nothing is written");
        assertEquals(4, changes.size());
    }

    @Test
    void twoPlayersDoingTheSameGetTheSameThreatWhateverTheirOrderOfArrival() {
        var mob = open(1, DEFAULTS);
        var encounter = engage(mob, 1);
        join(encounter, 3, 2);
        threat.rangedHit(id(1), player(3), 5.0, false);
        threat.rangedHit(id(1), player(2), 5.0, false);
        threat.assisted(WORLD, player(2), List.of(player(1)));
        threat.assisted(WORLD, player(3), List.of(player(1)));
        assertEquals(totalFor(player(2)), totalFor(player(3)));
        assertEquals(6.5, totalFor(player(2)));
    }

    private double totalFor(UUID playerId) {
        return changes.stream().filter(change -> change.player().equals(playerId)).mapToDouble(Change::amount).sum();
    }

    /** One call to {@code addThreat}. */
    private record Change(MobKey mob, UUID player, double amount) {
    }

    /** Combat actions that record threat changes, throw for the mobs in {@link #failing} and cast nothing. */
    private final class Actions implements CombatActions {
        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            throw new UnsupportedOperationException("cast");
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            throw new UnsupportedOperationException("ready");
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
            throw new UnsupportedOperationException("signalTactic");
        }

        @Override
        public void addThreat(MobKey mob, UUID playerId, double amount) {
            if (failing.contains(mob.entityId())) {
                throw new IllegalStateException("threat table unavailable");
            }
            changes.add(new Change(mob, playerId, amount));
        }
    }

    /** Goal ownership whose current path reaches the target or not, counting the readings. */
    private static final class Paths implements MobControl {
        boolean hasPath = true;
        boolean reaches = true;
        int reads;

        @Override
        public InstallResult ensureInstalled(MobKey key, TacticalProfile profile) {
            throw new UnsupportedOperationException("ensureInstalled");
        }

        @Override
        public MovementProgress progress(MobKey key) {
            reads++;
            return new MovementProgress(true, hasPath, hasPath && reaches, OptionalDouble.of(6.0));
        }

        @Override
        public List<GoalSummary> describeGoals(MobKey key) {
            return List.of();
        }

        @Override
        public void release(MobKey key) {
        }
    }
}
