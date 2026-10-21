package akyrion.thepredict.domain;

import akyrion.thepredict.config.DecisionSettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group question capacity under the delivered M09 caps. STANDARD is fixed; the caps do not move. */
class GroupCapacityTest {
    private static final int TICKS_PER_MINUTE = 20 * 60;

    @Test
    void standardFitsThreeAndReactiveFitsLessThanOne() {
        assertEquals(600, DecisionSettings.standard().maxRequestsPerMinute());
        assertEquals(300, DecisionSettings.standard().maxRequestsPerMinutePerWorld());
        assertEquals(600_000, DecisionSettings.standard().maxInputTokensPerMinute());
        assertEquals(GroupCapacity.TOKENS_PER_MINUTE, DecisionSettings.standard().maxInputTokensPerMinute());
        var standard = GroupCapacity.fits(GroupCapacity.STANDARD_ASKS_PER_MINUTE, GroupCapacity.PLAN_AND_LEADER_TOKENS,
                1);
        var reactive = GroupCapacity.fits(GroupCapacity.REACTIVE_ASKS_PER_MINUTE, GroupCapacity.PLAN_AND_LEADER_TOKENS,
                1);
        assertTrue(standard >= 3.0 && standard < 4.0, String.valueOf(standard));
        assertTrue(reactive < 1.0 && reactive > 0.9, String.valueOf(reactive));
    }

    @Test
    void theRatesAreAsksPerMinuteOfTheTwoCadences() {
        assertEquals(TICKS_PER_MINUTE / 20, GroupCapacity.STANDARD_ASKS_PER_MINUTE);
        assertEquals(TICKS_PER_MINUTE / 6, GroupCapacity.REACTIVE_ASKS_PER_MINUTE);
        var asks = GroupCapacity.STANDARD_ASKS_PER_MINUTE;
        assertEquals((double) GroupCapacity.TOKENS_PER_MINUTE / (asks * GroupCapacity.PLAN_AND_LEADER_TOKENS),
                GroupCapacity.fits(asks, GroupCapacity.PLAN_AND_LEADER_TOKENS, 1), 1e-12, "the token budget binds");
        assertEquals((double) GroupCapacity.SERVER_PER_MINUTE / asks, GroupCapacity.fits(asks, 1, 4), 1e-12,
                "with tiny asks and four worlds the server cap binds");
    }
}
