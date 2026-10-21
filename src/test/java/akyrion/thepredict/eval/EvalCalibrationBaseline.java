package akyrion.thepredict.eval;

import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * ECE and Brier of arm D on the frozen M16 bank (M22.0). Reads the bank and {@code answers.jsonl}. Does not rewrite
 * them.
 */
public final class EvalCalibrationBaseline {
    /** Bank of scenarios. */
    public static final Path BANK = Path.of("src/test/resources/scenarios/bank");
    /** Answers of the M16 re-bank. */
    public static final Path ANSWERS = Path.of(
            "DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/m16-rebanco-2026-09-30/P2/answers.jsonl");

    private EvalCalibrationBaseline() {
    }

    /**
     * ECE10 and top-label Brier of one arm on reviewed, informative NOW rows.
     *
     * @param arm the arm letter
     * @return ECE10 and Brier
     * @throws Exception when a file cannot be read
     */
    public static double[] arm(String arm) throws Exception {
        var acceptable = new HashMap<String, HashSet<String>>();
        try (var files = Files.list(BANK)) {
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
        var confidence = new ArrayList<Double>();
        var success = new ArrayList<Integer>();
        for (var line : Files.readAllLines(ANSWERS)) {
            if (line.isBlank()) {
                continue;
            }
            var row = JsonParser.parseString(line).getAsJsonObject();
            if (!arm.equals(row.get("arm").getAsString()) || !"NOW".equals(row.get("situation").getAsString())) {
                continue;
            }
            if (!row.has("best") || row.get("best").isJsonNull()) {
                continue;
            }
            var names = acceptable.get(row.get("scenario").getAsString());
            if (names == null) {
                continue;
            }
            confidence.add(row.get("confidence").getAsDouble());
            success.add(names.contains(row.get("best").getAsString()) ? 1 : 0);
        }
        var conf = new double[confidence.size()];
        var hit = new int[success.size()];
        for (var index = 0; index < conf.length; index++) {
            conf[index] = confidence.get(index);
            hit[index] = success.get(index);
        }
        return new double[] {Calibration.ece(conf, hit, 10), Calibration.brier(conf, hit)};
    }
}
