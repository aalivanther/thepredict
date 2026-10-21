package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AE-12 (C4): no public entry of the dispatcher takes {@code null} as a value. The inbox path is the constructor
 * without an intake, and every installer requires its section.
 */
class DecisionDispatcherNullsTest {
    private final CoordinatorFixture fixture = new CoordinatorFixture();

    @Test
    void theConstructorsWithAnIntakeRequireOne() {
        assertThrows(NullPointerException.class, () -> new DecisionDispatcher(fixture.capture, fixture.sessions.registry,
                fixture.budget, fixture.tracker, fixture.inFlight, fixture.client, fixture.inbox, fixture.metrics,
                fixture.clock, () -> 1L, fixture.settings, line -> {
                }, DecisionTap.NONE, null));
        assertDoesNotThrow(() -> new DecisionDispatcher(fixture.capture, fixture.sessions.registry, fixture.budget,
                fixture.tracker, fixture.inFlight, fixture.client, fixture.inbox, fixture.metrics, fixture.clock,
                () -> 1L, fixture.settings, line -> {
                }, DecisionTap.NONE), "the inbox path has its own constructor");
    }

    @Test
    void theInstallersRequireTheirSection() {
        var dispatcher = fixture.dispatcher;
        assertThrows(NullPointerException.class, () -> dispatcher.useLearning(null));
        assertThrows(NullPointerException.class,
                () -> dispatcher.useDistill(null, akyrion.thepredict.engine.distill.TeachingBoard.NONE));
        assertThrows(NullPointerException.class, () -> dispatcher.useControl(null));
    }
}
