package akyrion.thepredict.engine;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The model whose answers may be applied (M10.1 step 3, rule I11). Package-private; server thread only.
 *
 * <p>A configured versioned ID ({@code name-major.minor…}, such as {@code jev-1.13.0}) is expected as is and any
 * other effective model is refused. A configured alias ({@code jev-latest}) expects the first effective model
 * received; a later change refuses that answer, is logged once and becomes the expected model, because thresholds are
 * calibrated per model version (plan §6). A versioned ID that another model answers for gives one warning per
 * distinct model. The log names only a model reported by the service that looks like a versioned ID or a known alias,
 * never the configured value.
 */
final class ModelExpectation {
    private static final Pattern VERSIONED = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(-[A-Za-z0-9_]+)*-\\d+(\\.\\d+)+");
    private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9]{0,15}-(latest|preview)");
    private static final Pattern KEV = Pattern.compile("kev-[A-Za-z0-9][A-Za-z0-9._-]{0,62}");

    private final boolean versioned;
    private Optional<String> expected;
    private Optional<String> reportedMismatch = Optional.empty();
    private boolean coverageStale;

    ModelExpectation(String configured) {
        Objects.requireNonNull(configured, "configured");
        versioned = VERSIONED.matcher(configured).matches()
                || (KEV.matcher(configured).matches() && !ALIAS.matcher(configured).matches());
        expected = versioned ? Optional.of(configured) : Optional.empty();
    }

    /**
     * Whether a model change has marked distilled coverage stale (errata E29-13 applied).
     *
     * @return {@code true} once, then the flag clears
     */
    boolean takeCoverageStale() {
        var found = coverageStale;
        coverageStale = false;
        return found;
    }

    /**
     * Tells whether an answer of the effective model may be applied, and learns the model of an alias.
     *
     * @param effective model reported by the answer
     * @param changed receiver of the one line that reports a new model behind an alias
     * @return {@code true} if the answer may be applied
     */
    boolean accepts(String effective, Consumer<String> changed) {
        if (expected.isEmpty()) {
            expected = Optional.of(effective);
            return true;
        }
        if (expected.get().equals(effective)) {
            return true;
        }
        coverageStale = true;
        if (!versioned) {
            changed.accept("Jev model behind the configured alias changed from " + label(expected.get()) + " to "
                    + label(effective) + "; answers of the new model apply from the next request");
            expected = Optional.of(effective);
        } else if (!reportedMismatch.equals(Optional.of(effective))) {
            changed.accept("Jev answered with the model " + label(effective)
                    + " instead of the configured versioned model; its answers are discarded");
            reportedMismatch = Optional.of(effective);
        }
        return false;
    }

    /** Tells whether the configured model is a versioned ID rather than an alias. */
    boolean versioned() {
        return versioned;
    }

    static String label(String model) {
        return model.length() <= 64 && (VERSIONED.matcher(model).matches() || ALIAS.matcher(model).matches()
                || KEV.matcher(model).matches()) ? model : "<unrecognized model>";
    }
}
