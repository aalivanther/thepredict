package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicroSidePriorityTest {
    @Test
    void terrainBeatsTheGroupThePatternAndTheSeed() {
        assertEquals(1, MicroSidePriority.choose(1, 0, 0, 0));
        assertEquals(0, MicroSidePriority.choose(-1, 0, 1, 1));
        assertEquals(1, MicroSidePriority.choose(-1, -1, 1, 0));
        assertEquals(0, MicroSidePriority.choose(-1, -1, -1, 0));
    }
}
