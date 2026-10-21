package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemoryFact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Capping the facts of an admission keeps the facts already chosen, in their places, up to the limit, and empties the
 * rest without moving any (EFO-28).
 */
class MemoryFactStabilityTest {
    @Test
    void cappingKeepsTheFirstChosenFactsInPlaceAndEmptiesTheRest() {
        var facts = List.of(Optional.of(MemoryFact.BEAT_BEFORE), Optional.<MemoryFact>empty(),
                Optional.of(MemoryFact.LOST_LAST), Optional.of(MemoryFact.BEAT_MANY));
        var capped = MemoryAdmissionPolicy.cap(facts, 2);
        assertEquals(List.of(Optional.of(MemoryFact.BEAT_BEFORE), Optional.<MemoryFact>empty(),
                Optional.of(MemoryFact.LOST_LAST), Optional.<MemoryFact>empty()), capped);
        assertEquals(facts, MemoryAdmissionPolicy.cap(facts, 3), "a cap above the chosen facts changes nothing");
        assertEquals(List.of(Optional.<MemoryFact>empty(), Optional.<MemoryFact>empty(), Optional.<MemoryFact>empty(),
                Optional.<MemoryFact>empty()), MemoryAdmissionPolicy.cap(facts, 0));
    }
}
