package akyrion.thepredict.domain;

import java.util.Collection;
import java.util.Objects;

final class DomainChecks {
    private DomainChecks() {
    }

    static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static double requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite, was " + value);
        }
        return value;
    }

    static double requirePositive(double value, String field) {
        if (!(Double.isFinite(value) && value > 0.0)) {
            throw new IllegalArgumentException(field + " must be finite and > 0, was " + value);
        }
        return value;
    }

    static double requireNonNegative(double value, String field) {
        if (!(Double.isFinite(value) && value >= 0.0)) {
            throw new IllegalArgumentException(field + " must be finite and >= 0, was " + value);
        }
        return value;
    }

    static double requireUnit(double value, String field) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(field + " must be within [0, 1], was " + value);
        }
        return value;
    }

    static double requireHalfOpenUnit(double value, String field) {
        if (!(value >= 0.0 && value < 1.0)) {
            throw new IllegalArgumentException(field + " must be within [0, 1), was " + value);
        }
        return value;
    }

    static int requireRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be within [" + minimum + ", " + maximum
                    + "], was " + value);
        }
        return value;
    }

    static void requireMaximumSize(Collection<?> values, int maximum, String field) {
        if (values.size() > maximum) {
            throw new IllegalArgumentException(field + " must have at most " + maximum + " entries, had "
                    + values.size());
        }
    }
}
