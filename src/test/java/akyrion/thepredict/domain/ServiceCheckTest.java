package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceCheckTest {
    @Test
    void checkKeepsItsStateAndRejectsMissingParts() {
        assertEquals(ServiceState.READY, new ServiceCheck(ServiceState.READY, "").state());
        assertThrows(NullPointerException.class, () -> new ServiceCheck(null, "missing"));
        assertThrows(NullPointerException.class, () -> new ServiceCheck(ServiceState.PAUSED, null));
    }
}
