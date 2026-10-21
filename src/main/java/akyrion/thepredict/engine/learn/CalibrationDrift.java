package akyrion.thepredict.engine.learn;

import akyrion.thepredict.domain.Cusum;

/**
 * Drift of calibrated acceptances (M28.4). Reuses the Bernoulli {@link Cusum}. The reference is the first 500 windows.
 * An alarm is {@code DRIFT_ALARM} and never retrains. {@code h = 5σ} stays even if a probe would prefer another chart.
 */
public final class CalibrationDrift {
    private final Cusum cusum = new Cusum();
    private int seen;
    private int successes;
    private boolean armed;
    private double baseline = 0.5;

    /**
     * Creates a chart that has not armed yet.
     */
    public CalibrationDrift() {
    }

    /**
     * Observes one closed Jev window.
     *
     * @param success whether the effect was positive
     * @param referenceWindows windows gathered before the chart arms
     * @return {@code true} when the chart alarms
     */
    public boolean observe(boolean success, int referenceWindows) {
        if (!armed) {
            seen++;
            successes += success ? 1 : 0;
            if (seen >= referenceWindows) {
                baseline = successes / (double) seen;
                cusum.arm(baseline);
                armed = true;
            }
            return false;
        }
        var alternative = Math.max(0.0, baseline - 0.15);
        var sigma = Math.sqrt(Math.max(baseline * (1.0 - baseline), 1.0e-6));
        return cusum.observe(success, alternative, 5.0 * sigma);
    }
}
