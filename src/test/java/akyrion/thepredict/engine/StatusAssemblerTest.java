package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** PZ-20: {@code status} shows the counters of the tick layer with the other totals (M20.3, M20.5). */
class StatusAssemblerTest {
    private final ExecutionFixture fixture = new ExecutionFixture();
    private final Diagnostics diagnostics = new Diagnostics(new Diagnostics.Sources(fixture.sessions.registry,
            fixture.sessions.encounters, fixture.sessions.metrics, fixture.sessions.lifecycle,
            new ContextEvents(fixture.sessions.encounters, fixture.sessions.registry, fixture.sessions.agenda,
                    fixture.clock), new ContextSignals(), fixture.metrics, fixture.threat, fixture.budget,
            ExecutionFixture.settings(2000), fixture.tracker, fixture.inFlight, fixture.intake, fixture.mobs,
            fixture.reader, fixture.clock, "jev-1.13.0", fixture.outcomes.metrics()));

    @Test
    void theTickLayerCountersAreTotalsOfTheStatus() {
        fixture.metrics.layer(DecisionMetrics.LayerCounter.TICK_LAYER_DEFERRED, 3);
        fixture.metrics.layer(DecisionMetrics.LayerCounter.INTENT_EXTENDED, 2);
        var totals = diagnostics.status().counters().stream().filter(group -> group.name().equals("totals"))
                .findFirst().map(StatusReport.CounterGroup::values).orElseThrow();
        assertEquals(3L, totals.get("TICK_LAYER_DEFERRED"));
        assertEquals(2L, totals.get("INTENT_EXTENDED"));
        assertFalse(totals.containsKey("MICRO_NO_PATH"), "a zero counter stays out, as every other total");
    }
}
