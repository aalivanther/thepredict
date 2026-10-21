package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobSighting;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ThreatTableState;
import akyrion.thepredict.domain.ParticipantFacts;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static akyrion.thepredict.adapter.server.context.CaptureDoubles.OTHER_WORLD_ID;
import static akyrion.thepredict.adapter.server.context.CaptureDoubles.WORLD_ID;
import static akyrion.thepredict.adapter.server.context.CaptureDoubles.mobId;
import static akyrion.thepredict.adapter.server.context.CaptureDoubles.playerId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCaptureTest {
    private static final EncounterId ENCOUNTER = new EncounterId(1);

    private final CaptureDoubles doubles = new CaptureDoubles();

    private static MobKey key(int value) {
        return new MobKey(mobId(value), 0);
    }

    private CombatFacts capture(MobKey... keys) {
        return doubles.capture().capture(ENCOUNTER, List.of(keys), doubles.context()).orElseThrow();
    }

    @Test
    void aMobAndItsTargetGiveFactsWithAliasesAndNoPlayerIdentity() {
        doubles.mob(1, 0, 0);
        var target = doubles.player(1, 2, 0);
        doubles.source.targets.put(mobId(1), target.player);
        var facts = capture(key(1));
        assertEquals(1, facts.mobs().size());
        var relation = facts.mobs().getFirst().target().orElseThrow();
        assertEquals(new ParticipantAlias(1), relation.target());
        assertEquals(DistanceBand.MELEE, relation.distance());
        assertEquals(List.of(new ParticipantFacts(new ParticipantAlias(1), HealthBand.HEALTHY,
                ArmorBand.LIGHT, Set.of(), 1)), facts.participants());
        assertEquals(100, facts.captureTick());
        assertEquals(5_000, facts.captureNanos());
        assertFalse(facts.toString().contains(playerId(1).toString()));
        assertEquals(List.of(new MobSighting(key(1), WORLD_ID, Optional.of(playerId(1)), OptionalDouble.of(2.0))),
                doubles.sightings);
    }

    @Test
    void aMissingTargetLeavesNoRelationButNearbyPlayersArePerceived() {
        doubles.mob(1, 0, 0);
        doubles.player(1, 4, 0);
        var facts = capture(key(1));
        assertTrue(facts.mobs().getFirst().target().isEmpty());
        assertEquals(1, facts.participants().size());
        assertEquals(List.of(new MobSighting(key(1), WORLD_ID, Optional.empty(), OptionalDouble.empty())),
                doubles.sightings);
    }

    @Test
    void aMemberInAnotherWorldIsReportedAndLeftOut() {
        var mob = doubles.mob(1, 0, 0);
        mob.world = new CaptureDoubles.FakeWorld(OTHER_WORLD_ID);
        doubles.mob(2, 0, 0);
        var facts = capture(key(1), key(2));
        assertEquals(List.of(key(2)), facts.mobs().stream().map(mobFacts -> mobFacts.mob()).toList());
        assertEquals(OTHER_WORLD_ID, doubles.sightings.getFirst().worldId());
        assertTrue(doubles.capture().capture(ENCOUNTER, List.of(key(1)), doubles.context()).isEmpty());
    }

    @Test
    void anInactiveDeadOrUnboundMemberAndAnInvalidMaximumHealthAreSkippedWithoutException() {
        doubles.mob(1, 0, 0);
        doubles.mob(3, 0, 0);
        doubles.reads.maxHealth.put(mobId(3), Double.NaN);
        var facts = capture(key(1), key(2), key(3));
        assertEquals(List.of(key(1)), facts.mobs().stream().map(mobFacts -> mobFacts.mob()).toList());
        assertTrue(doubles.capture().capture(ENCOUNTER, List.of(key(2)), doubles.context()).isEmpty());
        assertTrue(doubles.capture().capture(ENCOUNTER, List.of(), doubles.context()).isEmpty());
    }

    @Test
    void atMostThirtyTwoMembersAreRead() {
        var keys = new ArrayList<MobKey>();
        for (var index = 1; index <= CombatFacts.MAX_MOBS + 8; index++) {
            doubles.mob(index, 0, 0);
            keys.add(key(index));
        }
        var facts = capture(keys.toArray(MobKey[]::new));
        assertEquals(CombatFacts.MAX_MOBS, facts.mobs().size());
        assertEquals(CombatFacts.MAX_MOBS, doubles.source.memberCalls);
    }

    @Test
    void atMostSixteenParticipantsAndOnlyEligiblePlayersInsideTheSphere() {
        doubles.mob(1, 0, 0);
        for (var index = 1; index <= 20; index++) {
            doubles.player(index, index * 0.7, 0);
        }
        doubles.player(30, 12, 12);
        doubles.player(31, 1, 1).mode = GameMode.SPECTATOR;
        doubles.player(32, 1, 0).mode = GameMode.CREATIVE;
        doubles.player(33, 1, 0).dead = true;
        var facts = capture(key(1));
        assertEquals(CombatFacts.MAX_PARTICIPANTS, facts.participants().size());
        assertEquals(CombatFacts.MAX_PARTICIPANTS, doubles.aliases.size());
        assertTrue(doubles.aliases.keySet().stream().allMatch(id -> id.getLeastSignificantBits() <= 16));
    }

    @Test
    void thePerceptionRadiusIsTheFollowRangeCappedBySearchRadius() {
        doubles.searchRadiusBlocks = 24;
        doubles.reads.followRange = 40;
        doubles.mob(1, 0, 0);
        doubles.player(1, 24, 0);
        doubles.player(2, Math.nextUp(24.0), 0);
        capture(key(1));
        assertEquals(List.of(playerId(1)), List.copyOf(doubles.aliases.keySet()));
    }

    @Test
    void aTargetBeyondThePerceptionRadiusIsNotPerceivedButTheEngineStillLearnsIt() {
        doubles.mob(1, 0, 0);
        var far = doubles.player(1, Math.nextUp(16.0), 0);
        doubles.source.targets.put(mobId(1), far.player);
        var facts = capture(key(1));
        assertTrue(facts.mobs().getFirst().target().isEmpty());
        assertTrue(facts.participants().isEmpty());
        assertEquals(Optional.of(playerId(1)), doubles.sightings.getFirst().targetId());
    }

    @Test
    void aTargetOnThePerceptionSphereIsTheFirstParticipantEvenWhenOthersAreNearer() {
        doubles.mob(1, 0, 0);
        var edge = doubles.player(1, 16, 0);
        for (var index = 2; index <= 20; index++) {
            doubles.player(index, 1, 0);
        }
        doubles.source.targets.put(mobId(1), edge.player);
        var facts = capture(key(1));
        assertEquals(DistanceBand.RANGED, facts.mobs().getFirst().target().orElseThrow().distance());
        assertEquals(new ParticipantAlias(1), facts.participants().getFirst().alias());
        assertEquals(CombatFacts.MAX_PARTICIPANTS, facts.participants().size());
    }

    @Test
    void aTargetInCreativeModeIsNoTarget() {
        doubles.mob(1, 0, 0);
        var creative = doubles.player(1, 2, 0);
        creative.mode = GameMode.CREATIVE;
        doubles.source.targets.put(mobId(1), creative.player);
        var facts = capture(key(1));
        assertTrue(facts.mobs().getFirst().target().isEmpty());
        assertTrue(facts.participants().isEmpty());
    }

    @Test
    void theProbeReadsHealthTargetAndBandWithoutSightPathsOrParticipants() {
        var mob = doubles.mob(1, 0, 0);
        mob.health = 6;
        doubles.source.targets.put(mobId(1), doubles.player(1, 5, 0).player);
        var probe = doubles.capture().probe(key(1)).orElseThrow();
        assertEquals(HealthBand.CRITICAL, probe.health());
        assertEquals(Optional.of(playerId(1)), probe.targetId());
        assertEquals(Optional.of(DistanceBand.RANGED), probe.targetDistance());
        assertEquals(0, mob.sightChecks);
        assertEquals(0, doubles.reads.findPathCalls);
        assertTrue(doubles.aliases.isEmpty());
    }

    @Test
    void theProbeOfAMobWithoutTargetOrWithAnInvalidEntityIsEmptyWhereItShouldBe() {
        var mob = doubles.mob(1, 0, 0);
        mob.health = Math.nextUp(6.0);
        var probe = doubles.capture().probe(key(1)).orElseThrow();
        assertEquals(HealthBand.WOUNDED, probe.health());
        assertTrue(probe.targetId().isEmpty() && probe.targetDistance().isEmpty());
        assertTrue(doubles.capture().probe(key(2)).isEmpty());
        doubles.reads.maxHealth.put(mobId(1), 0.0);
        assertTrue(doubles.capture().probe(key(1)).isEmpty());
    }

    @Test
    void theProbeReportsTheWorldTheMobIsIn() {
        var mob = doubles.mob(1, 0, 0);
        assertEquals(WORLD_ID, doubles.capture().probe(key(1)).orElseThrow().worldId());
        mob.world = new CaptureDoubles.FakeWorld(OTHER_WORLD_ID);
        assertEquals(OTHER_WORLD_ID, doubles.capture().probe(key(1)).orElseThrow().worldId());
    }

    @Test
    void theFollowRangeIsTheCappedFollowRangeOfTheMobInItsOwnWorld() {
        doubles.mob(1, 0, 0);
        doubles.mob(2, 16, 0);
        doubles.mob(3, 16.5, 0);
        var capture = doubles.capture();
        assertTrue(capture.withinFollowRange(key(1), key(2)));
        assertFalse(capture.withinFollowRange(key(1), key(3)));
        assertTrue(capture.withinFollowRange(key(1), key(1)));
        doubles.reads.followRange = 64;
        assertTrue(capture.withinFollowRange(key(1), key(3)));
        var far = doubles.mob(4, 32.5, 0);
        assertFalse(capture.withinFollowRange(key(1), key(4)));
        far.x = 1;
        far.world = new CaptureDoubles.FakeWorld(OTHER_WORLD_ID);
        assertFalse(capture.withinFollowRange(key(1), key(4)));
        assertFalse(capture.withinFollowRange(key(1), key(9)));
        assertFalse(capture.withinFollowRange(key(9), key(1)));
    }

    @Test
    void inCombatReadsTheVanillaTrackerOrTheThreatTable() {
        doubles.mob(1, 0, 0);
        var capture = doubles.capture();
        assertFalse(capture.inCombat(mobId(1)));
        doubles.source.threatCombat.add(mobId(1));
        assertTrue(capture.inCombat(mobId(1)));
        doubles.source.threatCombat.clear();
        doubles.reads.trackerCombat = true;
        assertTrue(capture.inCombat(mobId(1)));
        assertFalse(capture.inCombat(mobId(9)));
        var item = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> null);
        assertFalse(new CombatCapture(doubles.source, doubles.reads, id -> Optional.of(item), doubles.contexts,
                (condition, entity) -> false, doubles.clock, () -> true, 32, ParticipantLookup.NONE).inCombat(mobId(8)));
        assertTrue(capture.isActive(mobId(1)));
        assertFalse(capture.isActive(mobId(9)));
    }

    @Test
    void theSearchRadiusMustBePositiveAndFinite() {
        doubles.searchRadiusBlocks = 0;
        assertThrows(IllegalArgumentException.class, doubles::capture);
        doubles.searchRadiusBlocks = Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, doubles::capture);
    }

    @Test
    void everyPortMethodRefusesToRunOffTheServerThread() {
        doubles.mob(1, 0, 0);
        doubles.serverThread = false;
        var capture = doubles.capture();
        assertThrows(IllegalStateException.class, () -> capture.capture(ENCOUNTER, List.of(key(1)), doubles.context()));
        assertThrows(IllegalStateException.class, () -> capture.probe(key(1)));
        assertThrows(IllegalStateException.class, () -> capture.inCombat(mobId(1)));
        assertThrows(IllegalStateException.class, () -> capture.isActive(mobId(1)));
        assertThrows(IllegalStateException.class, () -> capture.threatTable(key(1)));
        assertThrows(IllegalStateException.class, () -> capture.withinFollowRange(key(1), key(1)));
        assertEquals(0, doubles.source.memberCalls);
    }

    @Test
    void theThreatTableIsReadThroughTheMemberSourceWithoutAnyPlayerIdentity() {
        doubles.mob(1, 0, 0);
        doubles.source.threatTables.put(mobId(1), new ThreatTableState(true, 2, 35.0, 50.0));
        assertEquals(Optional.of(new ThreatTableState(true, 2, 35.0, 50.0)), doubles.capture().threatTable(key(1)));
        doubles.mob(2, 0, 0);
        assertEquals(Optional.of(ThreatTableState.DISABLED), doubles.capture().threatTable(key(2)));
        assertTrue(doubles.capture().threatTable(key(3)).isEmpty());
    }
}
