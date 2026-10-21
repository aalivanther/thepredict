package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The effective 59–60 s window of {@link RequestBudget} (pending of M09 for M13), measured with the real budget and a
 * controllable time. A greedy sender gets at most twice {@code maxRequestsPerMinute} into a real 60 s window, which
 * with the default of 600 is exactly the published account limit of 1 200; a steady sender gets at most the limit
 * plus what it sends in one second, because the oldest bucket leaves the window up to one second early.
 */
class RequestBudgetWindowTest {
    private static final long MILLISECOND = 1_000_000L;
    private static final long FIVE_MINUTES = 300_000 * MILLISECOND;

    @Test
    void aGreedySenderGetsTwiceTheLimitIntoOneRealMinuteAndNeverMore() {
        var sends = RequestBudgetWindow.sender(600, true, 10 * MILLISECOND, FIVE_MINUTES);
        assertEquals(1200, RequestBudgetWindow.mostInAnyMinute(sends),
                "600 at 0.999 s and 600 more as soon as their bucket expires at 60 s");
        assertEquals(3000, sends.size(), "600 per minute over five minutes");
    }

    @Test
    void aSteadySenderGetsAtMostTheLimitPlusOneSecondOfItsOwnRate() {
        var perSecond = 40;
        var sends = RequestBudgetWindow.sender(600, false, 1000 * MILLISECOND / perSecond, FIVE_MINUTES);
        var most = RequestBudgetWindow.mostInAnyMinute(sends);
        assertTrue(most > 600, "the window is shorter than 60 s");
        assertTrue(most <= 600 + perSecond, "at most " + (600 + perSecond) + ", was " + most);
    }
}
