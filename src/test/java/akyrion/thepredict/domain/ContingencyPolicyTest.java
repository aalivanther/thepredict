package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static akyrion.thepredict.domain.DomainFixtures.guardian;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContingencyPolicyTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);

    private static final Set<TacticId> ALL = guardian().tactics().keySet();

    private static TacticalIntent intent(Map<Situation, TacticId> contingencies) {
        return new TacticalIntent(MOB, tactic("keep_range"), Movement.KEEP_DISTANCE, Optional.of(new ChoiceId("ranged")),
                0.8, List.of(tactic("press_attack"), tactic("hold_ground")), contingencies, ControlLevel.JEV, 0, 500, true);
    }

    private static MobProbe probe(HealthBand health) {
        return new MobProbe(MOB, WORLD, health, Optional.empty(), Optional.empty());
    }

    private static MobProbe probe(HealthBand health, DistanceBand distance) {
        return new MobProbe(MOB, WORLD, health, Optional.of(new UUID(1, 1)), Optional.of(distance));
    }

    @Test
    void targetReachingMeleeTriggersItsContingency() {
        var intent = intent(Map.of(Situation.IF_TARGET_IN_MELEE, tactic("press_attack")));
        assertEquals(Optional.of(tactic("press_attack")),
                ContingencyPolicy.triggered(intent, probe(HealthBand.HEALTHY, DistanceBand.MELEE)));
        assertEquals(Optional.empty(),
                ContingencyPolicy.triggered(intent, probe(HealthBand.HEALTHY, DistanceBand.RANGED)));
        assertEquals(Optional.empty(), ContingencyPolicy.triggered(intent, probe(HealthBand.HEALTHY)));
    }

    @Test
    void criticalHealthTakesPrecedenceOverMelee() {
        var intent = intent(Map.of(Situation.IF_TARGET_IN_MELEE, tactic("press_attack"),
                Situation.IF_OWN_HEALTH_CRITICAL, tactic("defend_self")));
        assertEquals(Optional.of(tactic("defend_self")),
                ContingencyPolicy.triggered(intent, probe(HealthBand.CRITICAL, DistanceBand.MELEE)));
        assertEquals(Optional.empty(), ContingencyPolicy.triggered(intent(Map.of()),
                probe(HealthBand.CRITICAL, DistanceBand.MELEE)));
    }

    @Test
    void firedContingencyIsUsedOnlyOnce() {
        var intent = intent(Map.of(Situation.IF_OWN_HEALTH_CRITICAL, tactic("defend_self")));
        var critical = probe(HealthBand.CRITICAL);
        var fired = ContingencyPolicy.fire(intent, critical, ALL, guardian(), 40).orElseThrow();
        assertEquals(tactic("defend_self"), fired.tactic());
        assertEquals(Movement.RETREAT, fired.movement());
        assertEquals(ControlLevel.ALTERNATIVE, fired.level());
        assertEquals(500, fired.expiresAtNanos());
        assertEquals(40, fired.startTick());
        assertEquals(false, fired.skillConsumed());
        assertEquals(Map.of(), fired.contingencies());
        assertEquals(Optional.empty(), ContingencyPolicy.triggered(fired, critical));
        assertEquals(Optional.empty(), ContingencyPolicy.fire(fired, critical, ALL, guardian(), 41));
    }

    @Test
    void ineligibleContingencyDoesNotFireAndStaysPrepared() {
        var intent = intent(Map.of(Situation.IF_TARGET_IN_MELEE, tactic("press_attack")));
        var melee = probe(HealthBand.HEALTHY, DistanceBand.MELEE);
        assertEquals(Optional.empty(), ContingencyPolicy.fire(intent, melee, Set.of(tactic("keep_range")), guardian(), 1));
        assertEquals(Optional.of(tactic("press_attack")), ContingencyPolicy.triggered(intent, melee));
        var fired = ContingencyPolicy.fire(intent, melee, ALL, guardian(), 2).orElseThrow();
        assertEquals(List.of(tactic("hold_ground")), fired.alternatives());
    }

    @Test
    void probeOfAnotherSessionIsRejected() {
        var other = new MobProbe(new MobKey(MOB.entityId(), MOB.generation() + 1), WORLD, HealthBand.CRITICAL,
                Optional.empty(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> ContingencyPolicy.triggered(intent(Map.of()), other));
    }
}
