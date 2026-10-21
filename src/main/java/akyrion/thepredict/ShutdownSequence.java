package akyrion.thepredict;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The shutdown of rule T7, one named step after another (CAR-18). A step that throws is logged once, with its name and
 * the type of the failure, and the next step runs anyway: a failure in the credit of one odd session never leaves the
 * goals held, the writer open with {@code records/.lock} taken or the HTTP client alive. Server thread only.
 */
final class ShutdownSequence {
    private final Consumer<String> warnings;
    private int failed;

    /**
     * Creates a sequence.
     *
     * @param warnings receives one line per failed step
     */
    ShutdownSequence(Consumer<String> warnings) {
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Runs one step.
     *
     * @param name what the step closes, for the log
     * @param work the step
     */
    void step(String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | LinkageError exception) {
            failed++;
            warnings.accept("Shutdown step '" + name + "' failed (" + exception.getClass().getSimpleName()
                    + "); the next steps still run");
        }
    }

    /**
     * Counts the steps that failed.
     *
     * @return failed steps so far
     */
    int failed() {
        return failed;
    }
}
