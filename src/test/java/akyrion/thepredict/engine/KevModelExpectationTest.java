package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Kev model change marks coverage stale. kev-latest is an alias. Errata E29-13 applied. */
class KevModelExpectationTest {
    @Test
    void aVersionedKevMismatchIsStaleAndAnAliasLearnsOnce() {
        var versioned = new ModelExpectation("kev-0.8b");
        assertTrue(versioned.versioned());
        assertTrue(versioned.accepts("kev-0.8b", line -> {
        }));
        assertFalse(versioned.takeCoverageStale());
        assertFalse(versioned.accepts("kev-4b", line -> {
        }));
        assertTrue(versioned.takeCoverageStale());
        assertFalse(versioned.takeCoverageStale());
        var alias = new ModelExpectation("kev-latest");
        assertFalse(alias.versioned());
        var lines = new ArrayList<String>();
        assertTrue(alias.accepts("kev-0.8b", lines::add));
        assertFalse(alias.takeCoverageStale());
        assertFalse(alias.accepts("kev-4b", lines::add));
        assertTrue(alias.takeCoverageStale());
        assertTrue(alias.accepts("kev-4b", lines::add));
    }
}
