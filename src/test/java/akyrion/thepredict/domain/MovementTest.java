package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementTest {
    @Test
    void parseIgnoresCaseAndSurroundingSpaces() {
        assertEquals(Optional.of(Movement.KEEP_DISTANCE), Movement.parse("keep_distance"));
        assertEquals(Optional.of(Movement.HOLD), Movement.parse(" Hold "));
    }

    @Test
    void parseReturnsEmptyForUnknownText() {
        assertEquals(Optional.empty(), Movement.parse("keep-distance"));
        assertEquals(Optional.empty(), Movement.parse(""));
    }
}
