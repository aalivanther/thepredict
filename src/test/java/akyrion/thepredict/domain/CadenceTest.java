package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CadenceTest {
    @Test
    void reactiveIsTheDefaultAndStandardIgnoresTheHorizonInTheRecordRange() {
        assertEquals(DecisionCadence.REACTIVE, DecisionCadence.DEFAULT);
        var standard = new DecisionTiming(40, 20, 100, 0.5, 0.2, 1);
        assertEquals(DecisionCadence.STANDARD, standard.cadence());
        assertEquals(100, standard.intentHorizonTicks());
        var reactive = standard.withLayer(TickLayerMode.REACTIONS, ReactionScope.ALL, ReactionBands.DEFAULT_AHEAD, 0,
                DecisionCadence.REACTIVE, 6, 10);
        assertEquals(10, reactive.intentHorizonTicks());
        assertEquals(ReactionBands.DEFAULT_AHEAD, reactive.reactionsAhead());
    }
}
