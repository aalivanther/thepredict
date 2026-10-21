package akyrion.thepredict.admin;

import akyrion.thepredict.domain.TacticId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCommandParserTest {
    private static final String UUID_TEXT = "123e4567-e89b-12d3-a456-426614174000";
    private final DebugCommandParser parser = new DebugCommandParser();

    private DebugRequest accepted(String... arguments) {
        return assertInstanceOf(DebugParseResult.Accepted.class, parser.parse(List.of(arguments))).request();
    }

    private String rejected(String... arguments) {
        return assertInstanceOf(DebugParseResult.Rejected.class, parser.parse(List.of(arguments))).reason();
    }

    @Test
    void intentWithNearestUsesTheValidityOfTheProfile() {
        assertEquals(new DebugRequest.SetIntent(new MobSelector.Nearest(), new TacticId("keep_distance"),
                OptionalInt.empty()), accepted("intent", "nearest", "keep_distance"));
    }

    @Test
    void intentWithUuidAndTicksIsAccepted() {
        assertEquals(new DebugRequest.SetIntent(new MobSelector.ById(UUID.fromString(UUID_TEXT)),
                new TacticId("press_and_strike"), OptionalInt.of(6000)),
                accepted("INTENT", UUID_TEXT, "press_and_strike", "6000"));
    }

    @Test
    void aTacticMustBeASnakeCaseIdAndNeverAReservedOne() {
        assertTrue(rejected("intent", "nearest", "Hold").contains("snake_case tactic ID"));
        assertTrue(rejected("intent", "nearest", "none").contains("snake_case tactic ID"));
        assertTrue(rejected("intent", "nearest", "debug_hold").contains("snake_case tactic ID"));
        assertTrue(rejected("intent", "nearest", "x").contains("snake_case tactic ID"));
    }

    @Test
    void ticksOutsideOneToSixThousandAreRejected() {
        assertTrue(rejected("intent", "nearest", "hold", "0").contains("between 1 and 6000"));
        assertTrue(rejected("intent", "nearest", "hold", "6001").contains("between 1 and 6000"));
        assertTrue(rejected("intent", "nearest", "hold", "ten").contains("between 1 and 6000"));
    }

    @Test
    void malformedUuidIsRejected() {
        assertTrue(rejected("release", "1-2-3-4-5").contains("Mob must be"));
        assertTrue(rejected("goals", UUID_TEXT + "0").contains("Mob must be"));
        assertTrue(rejected("intent", "near", "hold").contains("Mob must be"));
    }

    @Test
    void mobOnlyActionsNeedExactlyOneMob() {
        assertEquals(new DebugRequest.Release(new MobSelector.Nearest()), accepted("release", "nearest"));
        assertEquals(new DebugRequest.ListGoals(new MobSelector.ById(UUID.fromString(UUID_TEXT))),
                accepted("goals", UUID_TEXT));
        assertTrue(rejected("release").contains("Usage"));
        assertTrue(rejected("goals", "nearest", "extra").contains("Usage"));
    }

    @Test
    void retiredDebugActionsAreUnknownNow() {
        for (var retired : List.of("install", "unbind", "capture", "reapply", "signal", "cast", "trace", "signals",
                "autoinstall")) {
            assertTrue(rejected(retired, "nearest").startsWith("Unknown debug action"), retired);
        }
        assertTrue(rejected().contains("Missing"));
    }

    @Test
    void theUsageListsTheThreeActionsOfM12() {
        assertEquals(3, DebugCommandParser.USAGE.size());
        assertEquals(List.of("intent", "release", "goals"), DebugCommandParser.ACTIONS);
        assertTrue(DebugCommandParser.USAGE.getFirst().contains("<tactic> [ticks]"));
    }

    @Test
    void operatorInputIsEchoedAtMostFortyCharactersLong() {
        var longText = "x".repeat(40) + "SECRET-TAIL";
        for (var reason : List.of(rejected(longText), rejected("goals", longText),
                rejected("intent", "nearest", longText))) {
            assertTrue(reason.contains("x".repeat(40) + "…"), reason);
            assertTrue(!reason.contains("SECRET-TAIL"), reason);
        }
    }
}
