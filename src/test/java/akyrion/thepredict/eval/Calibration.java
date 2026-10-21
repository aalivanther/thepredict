package akyrion.thepredict.eval;

/**
 * ECE, Brier and a reliability diagram skeleton (M22.6). Pure. The plugin bins live in {@code CalibrationBins}.
 */
public final class Calibration {
    private Calibration() {
    }

    /**
     * Equal-width ECE.
     *
     * @param confidence confidence of each sample
     * @param success 1 or 0
     * @param bins the number of bins
     * @return the ECE
     */
    public static double ece(double[] confidence, int[] success, int bins) {
        var n = confidence.length;
        var counts = new int[bins];
        var conf = new double[bins];
        var hit = new double[bins];
        for (var index = 0; index < n; index++) {
            var bin = (int) Math.floor(confidence[index] * bins);
            if (bin >= bins) {
                bin = bins - 1;
            }
            if (bin < 0) {
                bin = 0;
            }
            counts[bin]++;
            conf[bin] += confidence[index];
            hit[bin] += success[index];
        }
        var total = 0.0;
        for (var bin = 0; bin < bins; bin++) {
            if (counts[bin] == 0) {
                continue;
            }
            var mean = conf[bin] / counts[bin];
            var rate = hit[bin] / counts[bin];
            total += counts[bin] / (double) n * Math.abs(mean - rate);
        }
        return total;
    }

    /**
     * Brier score.
     *
     * @param confidence the probabilities
     * @param success the outcomes
     * @return the mean squared error
     */
    public static double brier(double[] confidence, int[] success) {
        var sum = 0.0;
        for (var index = 0; index < confidence.length; index++) {
            var gap = confidence[index] - success[index];
            sum += gap * gap;
        }
        return sum / confidence.length;
    }

    /**
     * Murphy decomposition check: reliability − resolution + uncertainty equals the Brier score.
     *
     * @param confidence the probabilities
     * @param success the outcomes
     * @return the three terms and the Brier score
     */
    public static double[] murphy(double[] confidence, int[] success) {
        var n = confidence.length;
        var base = 0.0;
        for (var value : success) {
            base += value;
        }
        base /= n;
        var uncertainty = base * (1.0 - base);
        var bins = 10;
        var counts = new int[bins];
        var conf = new double[bins];
        var hit = new double[bins];
        for (var index = 0; index < n; index++) {
            var bin = Math.min(bins - 1, Math.max(0, (int) Math.floor(confidence[index] * bins)));
            counts[bin]++;
            conf[bin] += confidence[index];
            hit[bin] += success[index];
        }
        var reliability = 0.0;
        var resolution = 0.0;
        for (var bin = 0; bin < bins; bin++) {
            if (counts[bin] == 0) {
                continue;
            }
            var weight = counts[bin] / (double) n;
            var mean = conf[bin] / counts[bin];
            var rate = hit[bin] / counts[bin];
            reliability += weight * (mean - rate) * (mean - rate);
            resolution += weight * (rate - base) * (rate - base);
        }
        return new double[] {reliability, resolution, uncertainty, reliability - resolution + uncertainty};
    }

    /**
     * A one-line SVG so a report can embed the diagram without a toolkit.
     *
     * @param points confidence, rate, count triples
     * @return the SVG
     */
    public static String svg(double[][] points) {
        var body = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">");
        for (var point : points) {
            body.append("<circle cx=\"").append(point[0] * 100).append("\" cy=\"").append(100 - point[1] * 100)
                    .append("\" r=\"1\"/>");
        }
        body.append("</svg>");
        return body.toString();
    }
}
