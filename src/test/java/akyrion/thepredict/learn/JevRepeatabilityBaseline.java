package akyrion.thepredict.learn;

import akyrion.thepredict.eval.EvalCalibrationBaseline;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.util.HashMap;

/**
 * X1 on the frozen re-bank: pairs of repetitions of the same scenario and situation. Arm D has 60 complete pairs
 * and 59 share {@code best}.
 */
public final class JevRepeatabilityBaseline {
    private JevRepeatabilityBaseline() {
    }

    /**
     * Pair counts for one arm.
     *
     * @param arm the arm letter
     * @return total pairs, then pairs with the same {@code best}
     * @throws Exception when the answers file cannot be read
     */
    public static int[] pairs(String arm) throws Exception {
        var best = new HashMap<String, String[]>();
        for (var line : Files.readAllLines(EvalCalibrationBaseline.ANSWERS)) {
            if (line.isBlank()) {
                continue;
            }
            var row = JsonParser.parseString(line).getAsJsonObject();
            if (!arm.equals(row.get("arm").getAsString()) || !row.has("best") || row.get("best").isJsonNull()) {
                continue;
            }
            var repetition = row.get("repetition").getAsInt();
            if (repetition != 0 && repetition != 1) {
                continue;
            }
            var key = row.get("scenario").getAsString() + "\t" + row.get("situation").getAsString();
            best.computeIfAbsent(key, ignored -> new String[2])[repetition] = row.get("best").getAsString();
        }
        var total = 0;
        var same = 0;
        for (var pair : best.values()) {
            if (pair[0] == null || pair[1] == null) {
                continue;
            }
            total++;
            if (pair[0].equals(pair[1])) {
                same++;
            }
        }
        return new int[] {total, same};
    }
}
