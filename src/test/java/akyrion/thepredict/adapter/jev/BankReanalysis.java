package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.TacticId;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recomputes the report of a paid run of M16.10 from its {@code answers.jsonl} with the labels the bank holds now
 * ({@code ./gradlew reanalyseBank}, outside {@code check}): no network, no key and no cost (rule W7). Each line becomes
 * the call it recorded: the request is prepared again from the bank with the arm of the line (the report counts how
 * many prepared bodies have the recorded size), and the answer, the fits or the failure are the recorded ones.
 */
final class BankReanalysis {
    private BankReanalysis() {
    }

    /**
     * Writes the recomputed report.
     *
     * @param arguments the {@code answers.jsonl} of a run and the report to write
     * @throws IOException if a file cannot be read or written
     */
    public static void main(String[] arguments) throws IOException {
        var answers = Path.of(arguments[0]);
        var output = Path.of(arguments[1]);
        var report = report(Files.readAllLines(answers), answers.toString());
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report.text());
        System.out.println(report.text());
    }

    /**
     * The report of recorded lines.
     *
     * @param lines lines of {@code answers.jsonl}
     * @param source name of the file, for the header
     * @return the report of {@link BankStatistics} over the calls the lines record
     */
    static MeasurementReport report(List<String> lines, String source) {
        var prepared = new HashMap<String, BankBench.Planned>();
        var calls = new ArrayList<BankBench.Call>();
        var sameSize = 0;
        for (var text : lines) {
            if (text.isBlank()) {
                continue;
            }
            var line = StrictJson.parseObject(text, "answers line");
            var scenario = ScenarioBank.scenario(line.get("scenario").getAsString());
            var arm = BenchArm.valueOf(line.get("arm").getAsString());
            var planned = prepared.computeIfAbsent(scenario.id() + "/" + arm, key -> new BankBench.Planned(scenario,
                    arm, arm.prepare(scenario, LiveJevGate.MODEL)));
            if (planned.prepared().bodyChars() == line.get("bodyChars").getAsInt()) {
                sameSize++;
            }
            calls.add(new BankBench.Call(planned, line.get("repetition").getAsInt(),
                    line.get("latencyMicros").getAsLong() * 1_000L, result(line, planned)));
        }
        var report = new MeasurementReport();
        report.line("# M16 re-bench on the labelled bank, recomputed with the current labels");
        report.line("");
        report.line("Source " + source + ": " + calls.size() + " recorded calls, no new call. Prepared again from the"
                + " bank, " + sameSize + " of " + calls.size() + " bodies have the recorded size.");
        BankStatistics.write(report, calls);
        BankStatistics.rules(report, calls, List.of(BenchArm.A, BenchArm.C, BenchArm.D, BenchArm.E));
        return report;
    }

    private static DecisionResult result(JsonObject line, BankBench.Planned planned) {
        if (line.has("failure")) {
            return new DecisionResult.Failed(FailureKind.valueOf(line.get("failure").getAsString()
                    .toUpperCase(Locale.ROOT)), Optional.empty(), "recorded");
        }
        var probabilities = new LinkedHashMap<TacticId, Double>();
        line.getAsJsonObject("probabilities").entrySet().forEach(entry -> probabilities.put(
                new TacticId(entry.getKey()), entry.getValue().getAsDouble()));
        var sent = planned.prepared().sent();
        var answers = Map.of(sent.questions().getFirst().key(), new Answer(probabilities,
                line.get("confidence").getAsDouble()));
        var fits = new HashMap<QuestionKey, Double>();
        var recorded = line.getAsJsonObject("fits");
        sent.fitChecks().forEach(check -> {
            if (recorded.has(check.tactic().value())) {
                fits.put(check.key(), recorded.get(check.tactic().value()).getAsDouble());
            }
        });
        return new DecisionResult.Answered(answers, line.get("model").getAsString(),
                line.get("inputTokens").getAsInt(), fits);
    }
}
