package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.1 step 3: versioned IDs are fixed, aliases learn their first model and report a change once. */
class ModelExpectationTest {
    private final List<String> lines = new ArrayList<>();

    @Test
    void versionedIdsAndAliasesAreTold() {
        assertTrue(new ModelExpectation("jev-1.13.0").versioned());
        assertTrue(new ModelExpectation("kev-qwen-3.8").versioned());
        assertFalse(new ModelExpectation("jev-latest").versioned());
        assertFalse(new ModelExpectation("jev-preview").versioned());
    }

    @Test
    void aVersionedIdRefusesEveryOtherModelWithOneVisibleWarningPerModel() {
        var expectation = new ModelExpectation("jev-1.13.0");
        assertFalse(expectation.accepts("jev-1.14.0", lines::add));
        assertFalse(expectation.accepts("jev-1.14.0", lines::add));
        assertTrue(expectation.accepts("jev-1.13.0", lines::add));
        assertFalse(expectation.accepts("sk-live-0123456789", lines::add));
        assertEquals(List.of("Jev answered with the model jev-1.14.0 instead of the configured versioned model; its"
                + " answers are discarded", "Jev answered with the model <unrecognized model> instead of the configured"
                + " versioned model; its answers are discarded"), lines, "one warning per model, never a raw value");
    }

    @Test
    void anAliasReportsEachChangeOnceWithoutEchoingAnUnrecognizedModel() {
        var expectation = new ModelExpectation("jev-latest");
        assertTrue(expectation.accepts("jev-1.13.0", lines::add));
        assertFalse(expectation.accepts("jev 1.14 <script>", lines::add));
        assertTrue(expectation.accepts("jev 1.14 <script>", lines::add));
        assertEquals(List.of("Jev model behind the configured alias changed from jev-1.13.0 to <unrecognized model>;"
                + " answers of the new model apply from the next request"), lines);
        assertEquals("<unrecognized model>", ModelExpectation.label("x".repeat(65)));
    }
}
