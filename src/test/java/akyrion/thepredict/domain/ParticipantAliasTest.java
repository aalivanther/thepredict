package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticipantAliasTest {
    @Test
    void textIsPlayerFollowedByTheIndex() {
        assertEquals("player_1", new ParticipantAlias(1).text());
        assertEquals("player_16", new ParticipantAlias(16).toString());
    }

    @Test
    void indexZeroAndNegativeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ParticipantAlias(0));
        assertThrows(IllegalArgumentException.class, () -> new ParticipantAlias(-1));
    }

    @Test
    void equalByValue() {
        assertEquals(new ParticipantAlias(3), new ParticipantAlias(3));
    }

    @Test
    void eachKindHasItsPrefixAndTheOneArgumentConstructorIsAPlayer() {
        assertEquals("entity_1", new ParticipantAlias(ParticipantKind.ENTITY, 1).text());
        assertEquals("player_2", new ParticipantAlias(ParticipantKind.PLAYER, 2).toString());
        assertEquals(new ParticipantAlias(ParticipantKind.PLAYER, 4), new ParticipantAlias(4));
        assertNotEquals(new ParticipantAlias(ParticipantKind.PLAYER, 1), new ParticipantAlias(ParticipantKind.ENTITY, 1));
        assertEquals("player_", ParticipantAlias.PLAYER_PREFIX);
        assertEquals("entity_", ParticipantAlias.ENTITY_PREFIX);
    }

    @Test
    void anEntityAliasNeedsAKindAndAPositiveIndex() {
        assertThrows(NullPointerException.class, () -> new ParticipantAlias(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ParticipantAlias(ParticipantKind.ENTITY, 0));
    }
}
