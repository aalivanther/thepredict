package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionKeyTest {
    @Test
    void ofBuildsTheKeyAtBothEndsOfTheRange() {
        assertEquals("q0", QuestionKey.of(0).value());
        assertEquals("q9999", QuestionKey.of(9999).value());
        assertEquals(new QuestionKey("q12"), QuestionKey.of(12));
    }

    @Test
    void ofRejectsIndicesOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> QuestionKey.of(-1));
        assertThrows(IllegalArgumentException.class, () -> QuestionKey.of(10000));
    }

    @Test
    void malformedKeysAndNullAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new QuestionKey("q"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionKey("q12345"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionKey("Q1"));
        assertThrows(IllegalArgumentException.class, () -> new QuestionKey("question_1"));
        assertThrows(NullPointerException.class, () -> new QuestionKey(null));
    }

    @Test
    void toStringReturnsTheRawValue() {
        assertEquals("q3", QuestionKey.of(3).toString());
    }
}
