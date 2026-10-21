package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.guardian;
import static akyrion.thepredict.domain.DomainFixtures.tactic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.7: at most {@code max(1, members / 3)} simultaneous retreats, chosen deterministically. */
class CoordinationRuleTest {
    private static final TacticId RETREAT = tactic("defend_self");
    private static final TacticId PRESS = tactic("press_attack");
    private static final TacticId KEEP = tactic("keep_range");
    private static final Set<TacticId> ALL = Set.of(RETREAT, PRESS, KEEP, tactic("hold_ground"));

    private static TacticalIntent intent(int mob, TacticId tactic, Movement movement, double confidence,
                                         List<TacticId> alternatives) {
        return new TacticalIntent(new MobKey(new UUID(7, mob), 0), tactic, movement, Optional.empty(), confidence,
                alternatives, Map.of(), ControlLevel.JEV, 0, 1_000, false);
    }

    private static CoordinationPolicy.Candidate retreating(int mob, double confidence, List<TacticId> alternatives) {
        return new CoordinationPolicy.Candidate(intent(mob, RETREAT, Movement.RETREAT, confidence, alternatives),
                Optional.empty(), ALL, guardian());
    }

    private static long retreats(List<Optional<TacticalIntent>> result) {
        return result.stream().filter(intent -> intent.filter(found -> found.movement() == Movement.RETREAT).isPresent())
                .count();
    }

    private static List<CoordinationPolicy.Candidate> allRetreating(int members) {
        var candidates = new ArrayList<CoordinationPolicy.Candidate>();
        for (var mob = 0; mob < members; mob++) {
            candidates.add(retreating(mob, 0.5 + mob / 100.0, List.of(PRESS)));
        }
        return candidates;
    }

    @Test
    void theKeepMaskIsRequiredAndTheShortFormKeepsNothing() {
        var candidates = allRetreating(3);
        assertThrows(NullPointerException.class, () -> CoordinationPolicy.limitRetreats(candidates, 3, 0, 5, 0, null));
        assertEquals(CoordinationPolicy.limitRetreats(candidates, 3, 0, 5, 0, new boolean[0]),
                CoordinationPolicy.limitRetreats(candidates, 3, 0, 5, 0));
        assertEquals(CoordinationPolicy.limitRetreats(candidates, 3, 0, 5, 0, new boolean[3]),
                CoordinationPolicy.limitRetreats(candidates, 3, 0, 5, 0));
    }

    @Test
    void theCapIsOneThirdOfTheMembersAndNeverBelowOne() {
        assertEquals(1, CoordinationPolicy.maximumRetreats(1));
        assertEquals(1, CoordinationPolicy.maximumRetreats(3));
        assertEquals(2, CoordinationPolicy.maximumRetreats(6));
        assertEquals(2, CoordinationPolicy.maximumRetreats(7));
        for (var members : List.of(1, 3, 6, 7)) {
            var result = CoordinationPolicy.limitRetreats(allRetreating(members), members, 0, 5, 0);
            assertEquals(CoordinationPolicy.maximumRetreats(members), retreats(result), "members " + members);
            assertEquals(members, result.size());
        }
    }

    @Test
    void theRetreatsWithTheHighestConfidenceStayAndTheOthersSkipRetreatingAlternatives() {
        var twoRetreats = DomainFixtures.profile(List.of(DomainFixtures.tacticOf("defend_self", Movement.RETREAT, null, 40),
                DomainFixtures.tacticOf("fall_back", Movement.RETREAT, null, 30),
                DomainFixtures.tacticOf("press_attack", Movement.APPROACH, null, 20)), List.of(), List.of(),
                DomainFixtures.timing());
        var first = new CoordinationPolicy.Candidate(intent(1, RETREAT, Movement.RETREAT, 0.6,
                List.of(tactic("fall_back"), PRESS)), Optional.empty(), Set.of(RETREAT, tactic("fall_back"), PRESS),
                twoRetreats);
        var result = CoordinationPolicy.limitRetreats(List.of(first, retreating(2, 0.9, List.of(PRESS)),
                retreating(3, 0.7, List.of(PRESS))), 3, 0, 5, 0);
        assertEquals(PRESS, result.get(0).orElseThrow().tactic());
        assertEquals(ControlLevel.ALTERNATIVE, result.get(0).orElseThrow().level());
        assertEquals(5, result.get(0).orElseThrow().startTick());
        assertEquals(RETREAT, result.get(1).orElseThrow().tactic());
        assertEquals(PRESS, result.get(2).orElseThrow().tactic());
    }

    @Test
    void aTieIsBrokenByEncounterOrder() {
        var result = CoordinationPolicy.limitRetreats(List.of(retreating(1, 0.8, List.of(PRESS)),
                retreating(2, 0.8, List.of(PRESS))), 2, 0, 5, 0);
        assertEquals(RETREAT, result.get(0).orElseThrow().tactic());
        assertEquals(PRESS, result.get(1).orElseThrow().tactic());
    }

    @Test
    void anExcessRetreatWithoutAnAlternativeKeepsItsPreviousIntentOrPlansLocally() {
        var previous = intent(2, KEEP, Movement.KEEP_DISTANCE, 0.8, List.of());
        var withPrevious = new CoordinationPolicy.Candidate(intent(2, RETREAT, Movement.RETREAT, 0.5, List.of()),
                Optional.of(previous), ALL, guardian());
        var withoutAnything = retreating(3, 0.4, List.of());
        var result = CoordinationPolicy.limitRetreats(List.of(retreating(1, 0.9, List.of()), withPrevious,
                withoutAnything), 3, 0, 5, 0);
        assertEquals(previous, result.get(1).orElseThrow());
        assertTrue(result.get(2).isEmpty());
        var expired = CoordinationPolicy.limitRetreats(List.of(retreating(1, 0.9, List.of()), withPrevious), 3, 0, 5,
                2_000);
        assertTrue(expired.get(1).isEmpty(), "an expired previous intent is not kept");
    }

    @Test
    void retreatsAlreadyRunningElsewhereInTheEncounterCountAgainstTheCap() {
        var result = CoordinationPolicy.limitRetreats(List.of(retreating(1, 0.9, List.of(PRESS))), 3, 1, 5, 0);
        assertEquals(PRESS, result.getFirst().orElseThrow().tactic());
    }

    @Test
    void intentsThatDoNotRetreatAreNeverChanged() {
        var press = new CoordinationPolicy.Candidate(intent(1, PRESS, Movement.APPROACH, 0.3, List.of()),
                Optional.empty(), ALL, guardian());
        assertEquals(press.proposed(), CoordinationPolicy.limitRetreats(List.of(press), 1, 5, 5, 0).getFirst()
                .orElseThrow());
    }
}
