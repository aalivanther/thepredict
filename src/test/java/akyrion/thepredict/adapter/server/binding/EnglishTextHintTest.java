package akyrion.thepredict.adapter.server.binding;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnglishTextHintTest {
    @Test
    void englishTextHasNoReason() {
        assertEquals(Optional.empty(), EnglishTextHint.reason("Survive while defeating the current target"));
        assertEquals(Optional.empty(), EnglishTextHint.reason("Stay outside melee reach and shoot; don't die"));
    }

    @Test
    void nonAsciiCharactersAndForeignWordsAreFlagged() {
        assertTrue(EnglishTextHint.reason("Atacar al jugador más cercano").orElseThrow().contains("non-ASCII"));
        assertTrue(EnglishTextHint.reason("Survive para ganar").orElseThrow().contains("'para'"));
        assertTrue(EnglishTextHint.reason("Kämpfe gegen das Ziel").isPresent());
        assertTrue(EnglishTextHint.reason("Protege les alliés").isPresent());
    }
}
