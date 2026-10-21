package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.EnemyPatternFact;
import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.MemoryFact;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ZoneFact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static akyrion.thepredict.adapter.jev.JevFixtures.ENCOUNTER;
import static akyrion.thepredict.adapter.jev.JevFixtures.MOB_A;
import static akyrion.thepredict.adapter.jev.JevFixtures.PLAYER_1;
import static akyrion.thepredict.adapter.jev.JevFixtures.guardian;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JS-29: with the real encoder, a state that carries zone facts, a memory phrase and a pattern phrase is trimmed in
 * the order of M24, M27 and M26: the zone facts first, then the memory, then the events, and the pattern phrase of the
 * protected target stays with its facts.
 */
class MemoryPhrasesTest {
    @Test
    void zoneFactsThenTheMemoryThenTheEventsAreTrimmedAndThePatternStays() {
        var memory = MemoryFact.LOST_LAST.phrase("player_1");
        assertEquals("player_1 lost to this foe last time", memory);
        var pattern = "player_1 " + EnemyPatternFact.phrase(EnemyPattern.SPAMS_CLICKS);
        var steps = steps(facts(), Map.of(MOB_A, guardian()));
        assertEquals(4, steps.size(), "the full state, then a zone fact, the memory and the event: " + steps);
        assertTrue(steps.get(0).contains("zone_facts") && steps.get(0).contains(memory));
        assertFalse(steps.get(1).contains("zone_facts"), "the zone fact goes first");
        assertTrue(steps.get(1).contains(memory) && steps.get(1).contains("hit player_1"));
        assertFalse(steps.get(2).contains(memory), "the memory goes second");
        assertTrue(steps.get(2).contains("hit player_1"));
        assertFalse(steps.get(3).contains("hit player_1"), "the event goes third");
        steps.forEach(state -> assertTrue(state.contains(pattern), "the pattern phrase stays with the target"));
        steps.forEach(state -> assertFalse(state.contains("pattern_facts") || state.contains("group_facts")));
    }

    private static CombatFacts facts() {
        var mob = JevFixtures.meleeMob(MOB_A, PLAYER_1).withZone(Set.of(ZoneFact.EDGE_BEHIND), false, 1L);
        var player = new ParticipantFacts(PLAYER_1, HealthBand.WOUNDED, ArmorBand.MEDIUM, Set.of(), 1,
                Optional.empty(), Set.of(EnemyPattern.SPAMS_CLICKS), Optional.of(MemoryFact.LOST_LAST));
        return new CombatFacts(ENCOUNTER, 1200, 9_000_000L, List.of(mob), List.of(player),
                List.of(JevFixtures.event(1100, EventType.MELEE_HIT, PLAYER_1, MOB_A)), 12);
    }

    private static List<String> steps(CombatFacts facts, Map<MobKey, TacticalProfile> profiles) {
        var full = new SemanticEncoder(100_000).encodeState(facts, profiles, profiles.keySet()).toString().length();
        var steps = new ArrayList<String>();
        for (var limit = full; limit > 0; limit--) {
            String state;
            try {
                state = new SemanticEncoder(limit).encodeState(facts, profiles, profiles.keySet()).toString();
            } catch (SystemOneRequestException exception) {
                break;
            }
            if (steps.isEmpty() || !steps.getLast().equals(state)) {
                steps.add(state);
            }
        }
        return steps;
    }
}
