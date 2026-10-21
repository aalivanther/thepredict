package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobKeyTest {
    private static final UUID ENTITY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void generationZeroIsAccepted() {
        assertEquals(0, new MobKey(ENTITY, 0).generation());
    }

    @Test
    void negativeGenerationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MobKey(ENTITY, -1));
    }

    @Test
    void missingEntityIsRejected() {
        assertThrows(NullPointerException.class, () -> new MobKey(null, 0));
    }

    @Test
    void differentGenerationsOfTheSameEntityAreDifferentKeys() {
        assertNotEquals(new MobKey(ENTITY, 3), new MobKey(ENTITY, 4));
        assertEquals(new MobKey(ENTITY, 3), new MobKey(ENTITY, 3));
    }
}
