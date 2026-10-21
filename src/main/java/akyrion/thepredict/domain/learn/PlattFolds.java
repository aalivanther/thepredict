package akyrion.thepredict.domain.learn;

import java.util.TreeSet;

/**
 * Five-fold cross-validation by scenario (M28.4). A scenario stays in one fold, including both repetitions.
 */
final class PlattFolds {
    private static final int FOLDS = 5;

    private PlattFolds() {
    }

    static double trained(double[] confidence, int[] success, int[] scenario, int prior) {
        return average(confidence, success, scenario, prior, true);
    }

    static double identity(double[] confidence, int[] success, int[] scenario) {
        return average(confidence, success, scenario, 0, false);
    }

    private static double average(double[] confidence, int[] success, int[] scenario, int prior, boolean refit) {
        var fold = folds(scenario);
        var sum = 0.0;
        var used = 0;
        for (var held = 0; held < FOLDS; held++) {
            var test = slice(confidence, success, scenario, fold, held, false);
            if (test.confidence().length == 0) {
                continue;
            }
            var model = PlattMath.Fit.IDENTITY;
            if (refit) {
                var train = slice(confidence, success, scenario, fold, held, true);
                model = PlattMath.map(train.confidence(), train.success(), prior, 0, 0, train.scenarios());
            }
            sum += PlattMath.brier(test.confidence(), test.success(), model);
            used++;
        }
        return used == 0 ? Double.POSITIVE_INFINITY : sum / used;
    }

    /**
     * Fold of each row: scenarios in ascending id order, dealt round robin into five folds.
     *
     * @param scenario scenario id of each row
     * @return the fold of each row
     */
    static int[] folds(int[] scenario) {
        var ids = new TreeSet<Integer>();
        for (var id : scenario) {
            ids.add(id);
        }
        var order = new int[ids.size()];
        var cursor = 0;
        for (var id : ids) {
            order[cursor++] = id;
        }
        var fold = new int[scenario.length];
        for (var index = 0; index < scenario.length; index++) {
            var place = 0;
            while (place < order.length && order[place] != scenario[index]) {
                place++;
            }
            fold[index] = place % FOLDS;
        }
        return fold;
    }

    /**
     * The rows of one fold, or of the other four. {@code scenarios} counts the distinct scenarios of those rows.
     *
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param scenario scenario id of each row
     * @param fold fold of each row
     * @param held the fold
     * @param complement whether to keep the other folds instead
     * @return the rows
     */
    static Slice slice(double[] confidence, int[] success, int[] scenario, int[] fold, int held, boolean complement) {
        var count = 0;
        for (var index = 0; index < fold.length; index++) {
            if (complement != (fold[index] == held)) {
                count++;
            }
        }
        var keptConfidence = new double[count];
        var keptSuccess = new int[count];
        var scenarios = new java.util.HashSet<Integer>();
        var cursor = 0;
        for (var index = 0; index < fold.length; index++) {
            if (complement == (fold[index] == held)) {
                continue;
            }
            keptConfidence[cursor] = confidence[index];
            keptSuccess[cursor] = success[index];
            scenarios.add(scenario[index]);
            cursor++;
        }
        return new Slice(keptConfidence, keptSuccess, scenarios.size());
    }

    /**
     * Rows of a slice.
     *
     * @param confidence raw confidences
     * @param success 1 or 0
     * @param scenarios distinct scenarios of these rows
     */
    record Slice(double[] confidence, int[] success, int scenarios) {
    }
}
