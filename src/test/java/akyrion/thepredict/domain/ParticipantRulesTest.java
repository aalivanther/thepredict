package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The participant rules of a profile (M17.2): opaque selectors, the game modes and the defaults of 0.0.1. */
class ParticipantRulesTest {
    @Test
    void theDefaultIsThe001BehaviorPlayersInSurvivalOrAdventureAndNoEntity() {
        var rules = ParticipantRules.DEFAULT;
        assertEquals(List.of("SURVIVAL", "ADVENTURE"), List.copyOf(rules.playerGameModes()));
        assertEquals(List.of(), rules.entityAllow());
        assertEquals(List.of(), rules.entityDeny());
        assertFalse(rules.perceiveNonTargets());
        assertFalse(rules.admitsEntities());
    }

    @Test
    void theSelectorsStayOpaqueTextInTheirOrder() {
        var rules = new ParticipantRules(Set.of(), List.of("faction:duel_blue", "type:zombie"), List.of("kind:ANIMAL"),
                true);
        assertEquals(List.of("faction:duel_blue", "type:zombie"), rules.entityAllow());
        assertTrue(rules.admitsEntities());
        assertTrue(rules.playerGameModes().isEmpty());
    }

    @Test
    void unknownGameModesBlankSelectorsAndLongListsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ParticipantRules(Set.of("HARDCORE"), List.of(),
                List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new ParticipantRules(Set.of(), List.of(" "), List.of(),
                false));
        var seventeen = new ArrayList<String>();
        for (var index = 0; index <= ParticipantRules.MAX_SELECTORS; index++) {
            seventeen.add("tagged:t" + index);
        }
        assertThrows(IllegalArgumentException.class, () -> new ParticipantRules(Set.of(), List.of(), seventeen,
                false));
        assertEquals(16, new ParticipantRules(Set.of(), seventeen.subList(0, 16), List.of(), false).entityAllow()
                .size());
    }
}
