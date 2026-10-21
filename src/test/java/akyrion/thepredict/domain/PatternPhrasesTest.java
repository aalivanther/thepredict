package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPhrasesTest {
    @Test
    void phrasesArePlainEnglishAndCadenceHasNone() {
        assertEquals("", EnemyPatternFact.phrase(EnemyPattern.FIXED_CADENCE));
        for (var pattern : EnemyPattern.values()) {
            var phrase = EnemyPatternFact.phrase(pattern);
            if (pattern == EnemyPattern.FIXED_CADENCE) {
                continue;
            }
            assertFalse(phrase.isBlank());
            assertFalse(phrase.contains("-"));
            var lower = phrase.toLowerCase(Locale.ROOT);
            assertEquals(lower, phrase);
            assertTrue(phrase.chars().noneMatch(Character::isDigit));
            for (var other : EnemyPattern.values()) {
                assertFalse(phrase.contains(other.name()));
            }
        }
    }
}
