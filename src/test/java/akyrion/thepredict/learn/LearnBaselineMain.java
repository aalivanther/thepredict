package akyrion.thepredict.learn;

/**
 * Prints X1, X2 and the 200-seed alignment line. No network. The reward constants are not edited here.
 */
public final class LearnBaselineMain {
    private LearnBaselineMain() {
    }

    /**
     * Prints the two published figures.
     *
     * @param arguments ignored
     * @throws Exception when a bank file cannot be read
     */
    public static void main(String[] arguments) throws Exception {
        var fit = CalibrationFitBaseline.armDAcceptable();
        var pairs = JevRepeatabilityBaseline.pairs("D");
        System.out.println("X2 ECE10 " + fit[0] + " Brier " + fit[1]);
        System.out.println("X1 pairs " + pairs[0] + " same " + pairs[1]);
        System.out.println(RewardAlignmentBaseline.report());
        var report = new akyrion.thepredict.MeasurementReport();
        akyrion.thepredict.M28Measurements.write(report);
        System.out.print(report.text());
    }
}
