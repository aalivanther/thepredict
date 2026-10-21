package akyrion.thepredict.domain.learn;

import java.util.Optional;

/**
 * Platt scaling {@code P = σ(a·logit(c)+b)} with a Gaussian prior at {@code (1, 0)} (M28.4). This is not the X2 fit:
 * X2 uses L2 0.01 and starts at zero. A slope of 0.05 or below becomes the identity (errata E28-5 applied).
 * Pure. {@link StrictMath} only.
 */
public final class PlattMath {
    /** Newton steps. */
    public static final int ITERATIONS = 50;
    /** Logit clip. */
    public static final double EPS = 1.0e-4;
    /** Slope at or below this is not monotone enough. */
    public static final double SLOPE_FLOOR = 0.05;

    private PlattMath() {
    }

    /**
     * One fit.
     *
     * @param slope {@code a}
     * @param bias {@code b}
     * @param identity whether the result was replaced by the identity
     * @param cause why, or empty when the fit is usable
     */
    public record Fit(double slope, double bias, boolean identity, Optional<LearnCause> cause) {
        /** The identity map. */
        public static final Fit IDENTITY = new Fit(1.0, 0.0, true, Optional.empty());

        /**
         * Copies the cause.
         */
        public Fit {
            cause = cause == null ? Optional.empty() : cause;
        }
    }

    /**
     * MAP fit. Zero rows return the identity before any iteration.
     *
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param priorStrength pseudo-observations
     * @param minDecisions minimum rows
     * @param minScenarios minimum distinct scenarios, already checked by the caller via {@code scenarios}
     * @param scenarios distinct scenarios represented
     * @return the fit
     */
    public static Fit map(double[] confidence, int[] success, int priorStrength, int minDecisions, int minScenarios,
                          int scenarios) {
        if (confidence.length == 0) {
            return Fit.IDENTITY;
        }
        if (confidence.length < minDecisions || scenarios < minScenarios) {
            return new Fit(1.0, 0.0, true, Optional.of(LearnCause.CALIBRATION_INSUFFICIENT));
        }
        var slope = 1.0;
        var bias = 0.0;
        for (var iteration = 0; iteration < ITERATIONS; iteration++) {
            var step = step(confidence, success, slope, bias, priorStrength);
            slope -= step[0];
            bias -= step[1];
        }
        if (slope <= SLOPE_FLOOR) {
            return new Fit(1.0, 0.0, true, Optional.of(LearnCause.CALIBRATION_NON_MONOTONE));
        }
        return new Fit(slope, bias, false, Optional.empty());
    }

    /**
     * Keeps a fit only when its Brier is strictly better than the identity. Otherwise the identity is stored.
     *
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param scenario scenario id of each row; the distinct count is the scenario gate
     * @param priorStrength pseudo-observations
     * @param minDecisions minimum rows
     * @param minScenarios minimum distinct scenarios
     * @return the fit, or the identity when five-fold Brier does not beat the identity
     */
    public static Fit fit(double[] confidence, int[] success, int[] scenario, int priorStrength, int minDecisions,
                          int minScenarios) {
        var distinct = 0;
        var seen = new java.util.HashSet<Integer>();
        for (var id : scenario) {
            if (seen.add(id)) {
                distinct++;
            }
        }
        var mapped = map(confidence, success, priorStrength, minDecisions, minScenarios, distinct);
        return retain(mapped, confidence, success, scenario, priorStrength);
    }

    /**
     * Stores the identity when out-of-sample Brier does not beat the identity. Each row is its own scenario, and the
     * folds are refit with the default {@code learning.calibration.priorStrength}, the prior the full fit uses.
     *
     * @param fit the candidate
     * @param confidence raw confidences
     * @param success 1 or 0
     * @return the fit, or the identity
     */
    public static Fit retain(Fit fit, double[] confidence, int[] success) {
        var scenario = new int[confidence.length];
        for (var index = 0; index < confidence.length; index++) {
            scenario[index] = index;
        }
        return retain(fit, confidence, success, scenario,
                akyrion.thepredict.domain.LearningSettings.DEFAULT.priorStrength());
    }

    /**
     * Keeps the full-data fit only when five-fold Brier, by scenario, is strictly better than the identity. A slope at
     * or below the floor is {@link LearnCause#CALIBRATION_NON_MONOTONE}; a monotone slope that does not beat the
     * identity out of sample is {@link LearnCause#CALIBRATION_NOT_BETTER}.
     *
     * @param fit the full-data candidate
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param scenario scenario id of each row
     * @param priorStrength pseudo-observations used when a fold is refit
     * @return the fit, or the identity
     */
    public static Fit retain(Fit fit, double[] confidence, int[] success, int[] scenario, int priorStrength) {
        if (fit.identity()) {
            return fit;
        }
        if (fit.slope() <= SLOPE_FLOOR) {
            return new Fit(1.0, 0.0, true, Optional.of(LearnCause.CALIBRATION_NON_MONOTONE));
        }
        if (!(PlattFolds.trained(confidence, success, scenario, priorStrength)
                < PlattFolds.identity(confidence, success, scenario))) {
            return new Fit(1.0, 0.0, true, Optional.of(LearnCause.CALIBRATION_NOT_BETTER));
        }
        return fit;
    }

    /**
     * Mean squared error of a fit.
     *
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param fit the fit
     * @return the Brier score
     */
    public static double brier(double[] confidence, int[] success, Fit fit) {
        if (confidence.length == 0) {
            return 0.0;
        }
        var sum = 0.0;
        for (var index = 0; index < confidence.length; index++) {
            var predicted = apply(confidence[index], fit.slope(), fit.bias());
            var gap = predicted - success[index];
            sum += gap * gap;
        }
        return sum / confidence.length;
    }

    /**
     * Calibrated probability.
     *
     * @param confidence raw confidence
     * @param slope {@code a}
     * @param bias {@code b}
     * @return the probability
     */
    public static double apply(double confidence, double slope, double bias) {
        return sigmoid(slope * logit(confidence) + bias);
    }

    /**
     * Raw threshold {@code c* = σ((logit(p*)-b)/a)}. Within 1e-9 of the profile floor, the floor is returned.
     *
     * @param probability target calibrated probability
     * @param slope {@code a}
     * @param bias {@code b}
     * @param minimumConfidence profile floor
     * @return the raw confidence to compare
     */
    public static double rawThreshold(double probability, double slope, double bias, double minimumConfidence) {
        var raw = sigmoid((logit(probability) - bias) / slope);
        if (Math.abs(raw - minimumConfidence) < 1.0e-9) {
            return minimumConfidence;
        }
        return raw;
    }

    /**
     * Logit clipped to {@code [1e-4, 1-1e-4]}.
     *
     * @param probability the probability
     * @return the logit
     */
    public static double logit(double probability) {
        var clipped = Math.min(1.0 - EPS, Math.max(EPS, probability));
        return StrictMath.log(clipped / (1.0 - clipped));
    }

    /**
     * Logistic sigmoid.
     *
     * @param value the logit
     * @return the probability
     */
    public static double sigmoid(double value) {
        if (value >= 0.0) {
            var exp = StrictMath.exp(-value);
            return 1.0 / (1.0 + exp);
        }
        var exp = StrictMath.exp(value);
        return exp / (1.0 + exp);
    }

    private static double[] step(double[] confidence, int[] success, double slope, double bias, int prior) {
        var gradientSlope = prior * (slope - 1.0);
        var gradientBias = prior * (double) bias;
        var hessianSlope = (double) prior;
        var hessianBias = (double) prior;
        var hessianCross = 0.0;
        for (var index = 0; index < confidence.length; index++) {
            var x = logit(confidence[index]);
            var prediction = sigmoid(slope * x + bias);
            var weight = prediction * (1.0 - prediction);
            var residual = prediction - success[index];
            gradientSlope += residual * x;
            gradientBias += residual;
            hessianSlope += weight * x * x;
            hessianBias += weight;
            hessianCross += weight * x;
        }
        var determinant = hessianSlope * hessianBias - hessianCross * hessianCross;
        if (determinant == 0.0) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {
                (hessianBias * gradientSlope - hessianCross * gradientBias) / determinant,
                (hessianSlope * gradientBias - hessianCross * gradientSlope) / determinant
        };
    }
}
