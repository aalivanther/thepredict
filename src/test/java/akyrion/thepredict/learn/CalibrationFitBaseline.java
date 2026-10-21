package akyrion.thepredict.learn;

import akyrion.thepredict.eval.Calibration;
import akyrion.thepredict.eval.EvalCalibrationBaseline;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

/**
 * Leave-one-scenario-out Platt of arm D on the acceptable label (M28.0, X2). L2 is 0.01 and the weights start at
 * zero. This is not {@code PlattMath}: that fit uses a Gaussian prior at {@code (1, 0)}.
 */
public final class CalibrationFitBaseline {
    private static final double LAMBDA = 0.01;
    private static final double EPS = 1.0e-4;
    private static final int STEPS = 50;

    private CalibrationFitBaseline() {
    }

    /**
     * Confidences of the 96 arm-D {@code NOW} answers. The parity check reads these rows.
     *
     * @return one confidence per labelled answer
     * @throws Exception when a bank file cannot be read
     */
    public static double[] confidences() throws Exception {
        var rows = load();
        var values = new double[rows.size()];
        for (var index = 0; index < rows.size(); index++) {
            values[index] = rows.get(index).confidence;
        }
        return values;
    }

    /**
     * The 96 arm-D {@code NOW} answers, one row each.
     *
     * @return scenario, confidence and acceptable label
     * @throws Exception when a bank file cannot be read
     */
    public static java.util.List<Sample> samples() throws Exception {
        var rows = load();
        var samples = new java.util.ArrayList<Sample>(rows.size());
        for (var row : rows) {
            samples.add(new Sample(row.scenario, row.confidence, row.success));
        }
        return java.util.List.copyOf(samples);
    }

    /**
     * One labelled arm-D answer.
     *
     * @param scenario scenario id
     * @param confidence raw confidence
     * @param success 1 when the bank called the answer acceptable
     */
    public record Sample(String scenario, double confidence, int success) {
    }

    /**
     * Out-of-fold ECE10 and Brier for arm D, target acceptable. This leave-one-scenario estimator is not
     * {@code PlattMath.fit}.
     *
     * @return ECE10 then Brier
     * @throws Exception when a bank file cannot be read
     */
    public static double[] armDAcceptable() throws Exception {
        var rows = load();
        var predicted = new double[rows.size()];
        var success = new int[rows.size()];
        for (var scenario : scenarios(rows)) {
            predict(rows, scenario, predicted, success);
        }
        return new double[] {Calibration.ece(predicted, success, 10), Calibration.brier(predicted, success)};
    }

    private static void predict(ArrayList<Row> rows, String held, double[] predicted, int[] success) {
        var trainX = new ArrayList<Double>();
        var trainY = new ArrayList<Integer>();
        var test = new ArrayList<Integer>();
        split(rows, held, success, trainX, trainY, test);
        var weights = fit(trainX, trainY);
        for (var index : test) {
            var z = weights[0] + weights[1] * logit(rows.get(index).confidence);
            predicted[index] = sigmoid(z);
        }
    }

    private static void split(ArrayList<Row> rows, String held, int[] success, ArrayList<Double> trainX,
                              ArrayList<Integer> trainY, ArrayList<Integer> test) {
        for (var index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            success[index] = row.success;
            if (row.scenario.equals(held)) {
                test.add(index);
            } else {
                trainX.add(logit(row.confidence));
                trainY.add(row.success);
            }
        }
    }

    private static double[] fit(ArrayList<Double> x, ArrayList<Integer> y) {
        var bias = 0.0;
        var slope = 0.0;
        for (var step = 0; step < STEPS; step++) {
            var next = newton(x, y, bias, slope);
            bias = next[0];
            slope = next[1];
        }
        return new double[] {bias, slope};
    }

    private static double[] newton(ArrayList<Double> x, ArrayList<Integer> y, double bias, double slope) {
        var g0 = 0.0;
        var g1 = 0.0;
        var h00 = 1.0e-9;
        var h01 = 0.0;
        var h11 = LAMBDA + 1.0e-9;
        for (var index = 0; index < x.size(); index++) {
            var xi = x.get(index);
            var p = sigmoid(bias + slope * xi);
            var weight = p * (1.0 - p);
            var gap = p - y.get(index);
            g0 += gap;
            g1 += gap * xi;
            h00 += weight;
            h01 += weight * xi;
            h11 += weight * xi * xi;
        }
        g1 += LAMBDA * slope;
        var det = h00 * h11 - h01 * h01;
        return new double[] {bias - (h11 * g0 - h01 * g1) / det, slope - (h00 * g1 - h01 * g0) / det};
    }

    private static ArrayList<Row> load() throws Exception {
        var acceptable = labels();
        var rows = new ArrayList<Row>();
        for (var line : Files.readAllLines(EvalCalibrationBaseline.ANSWERS)) {
            if (line.isBlank()) {
                continue;
            }
            var row = JsonParser.parseString(line).getAsJsonObject();
            if (!"D".equals(row.get("arm").getAsString()) || !"NOW".equals(row.get("situation").getAsString())) {
                continue;
            }
            if (!row.has("best") || row.get("best").isJsonNull()) {
                continue;
            }
            var names = acceptable.get(row.get("scenario").getAsString());
            if (names == null) {
                continue;
            }
            var success = names.contains(row.get("best").getAsString()) ? 1 : 0;
            rows.add(new Row(row.get("scenario").getAsString(), row.get("confidence").getAsDouble(), success));
        }
        return rows;
    }

    private static HashMap<String, HashSet<String>> labels() throws Exception {
        var acceptable = new HashMap<String, HashSet<String>>();
        try (var files = Files.list(EvalCalibrationBaseline.BANK)) {
            for (var file : files.filter(path -> path.getFileName().toString().startsWith("bank-")).toList()) {
                var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var labels = root.getAsJsonObject("labels");
                if (labels == null || !labels.has("q0")) {
                    continue;
                }
                var question = labels.getAsJsonObject("q0");
                if (!"REVIEWED".equals(question.get("status").getAsString())) {
                    continue;
                }
                if (question.has("uninformative") && question.get("uninformative").getAsBoolean()) {
                    continue;
                }
                var names = new HashSet<String>();
                for (var name : question.getAsJsonArray("acceptable")) {
                    names.add(name.getAsString());
                }
                acceptable.put(root.get("id").getAsString(), names);
            }
        }
        return acceptable;
    }

    private static ArrayList<String> scenarios(ArrayList<Row> rows) {
        var seen = new LinkedHashSet<String>();
        for (var row : rows) {
            seen.add(row.scenario);
        }
        return new ArrayList<>(seen);
    }

    private static double logit(double confidence) {
        var clipped = Math.min(1.0 - EPS, Math.max(EPS, confidence));
        return StrictMath.log(clipped / (1.0 - clipped));
    }

    private static double sigmoid(double z) {
        if (z > 40.0) {
            return 1.0;
        }
        if (z < -40.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + StrictMath.exp(-z));
    }

    private record Row(String scenario, double confidence, int success) {
    }
}
