package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static akyrion.thepredict.domain.DomainFixtures.MOB;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobSightingTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);
    private static final UUID PLAYER = new UUID(0x9L, 1);

    @Test
    void aSightingWithoutTargetHasNoDistanceAndOneWithTargetHasOne() {
        assertDoesNotThrow(() -> new MobSighting(MOB, WORLD, Optional.empty(), OptionalDouble.empty()));
        assertDoesNotThrow(() -> new MobSighting(MOB, WORLD, Optional.of(PLAYER), OptionalDouble.of(0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new MobSighting(MOB, WORLD, Optional.of(PLAYER), OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new MobSighting(MOB, WORLD, Optional.empty(), OptionalDouble.of(3.0)));
    }

    @Test
    void negativeOrNonFiniteDistancesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobSighting(MOB, WORLD, Optional.of(PLAYER), OptionalDouble.of(-0.001)));
        assertThrows(IllegalArgumentException.class,
                () -> new MobSighting(MOB, WORLD, Optional.of(PLAYER), OptionalDouble.of(Double.POSITIVE_INFINITY)));
        assertThrows(NullPointerException.class,
                () -> new MobSighting(MOB, null, Optional.empty(), OptionalDouble.empty()));
    }
}
