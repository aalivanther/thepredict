package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobProbeTest {
    private static final UUID WORLD = new UUID(0xA11L, 1);

    @Test
    void distanceRequiresATarget() {
        assertThrows(IllegalArgumentException.class, () -> new MobProbe(DomainFixtures.MOB, WORLD, HealthBand.HEALTHY,
                Optional.empty(), Optional.of(DistanceBand.MELEE)));
    }

    @Test
    void theWorldIsRequired() {
        assertThrows(NullPointerException.class, () -> new MobProbe(DomainFixtures.MOB, null, HealthBand.HEALTHY,
                Optional.empty(), Optional.empty()));
    }

    @Test
    void targetWithoutDistanceIsAccepted() {
        var target = UUID.randomUUID();
        assertEquals(Optional.of(target), new MobProbe(DomainFixtures.MOB, WORLD, HealthBand.WOUNDED, Optional.of(target),
                Optional.empty()).targetId());
    }
}
