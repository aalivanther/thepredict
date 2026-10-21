package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static akyrion.thepredict.MeasurementReport.number;

/**
 * Statistics of a scenario bench run (M13.6): latency, failures, tokens and characters per token, the reliability of
 * the confidence by band, the coverage and accuracy of each {@code minimumConfidence}, the switches each
 * {@code tacticSwitchMargin} would allow between repeated answers, and how often a contingency differs from the
 * answer for now. Pure; the tests feed it answers of the loopback contract server.
 */
final class BenchStatistics {
    private static final double[] BANDS = {0.0, 0.3, 0.5, 0.7, 1.0001};
    private static final double[] THRESHOLDS = {0.3, 0.4, 0.5, 0.6, 0.7};
    private static final double[] MARGINS = {0.0, 0.1, 0.15, 0.2, 0.3};

    private BenchStatistics() {
    }

    /** One call of the bench. */
    record Call(ScenarioDocument scenario, int repetition, long nanos, DecisionResult result) {
    }

    static void write(MeasurementReport report, List<Call> calls) {
        latencyAndCost(report, calls);
        reliability(report, calls);
        margins(report, calls);
        contingencies(report, calls);
    }

    private static void latencyAndCost(MeasurementReport report, List<Call> calls) {
        report.section("Latency, failures and tokens");
        var nanos = calls.stream().mapToLong(Call::nanos).toArray();
        var failures = new TreeMap<String, Integer>();
        var models = new TreeMap<String, Integer>();
        var tokens = 0L;
        var ratios = new ArrayList<Double>();
        for (var call : calls) {
            switch (call.result()) {
                case DecisionResult.Answered answered -> {
                    models.merge(answered.effectiveModel(), 1, Integer::sum);
                    tokens += answered.inputTokens();
                    if (answered.inputTokens() > 0) {
                        ratios.add(call.scenario().requestChars() / (double) answered.inputTokens());
                    }
                }
                case DecisionResult.Failed failed -> failures.merge(failed.kind().name(), 1, Integer::sum);
            }
        }
        report.table(List.of("Calls", "us p50 / p95 / p99 / max", "Failures", "Effective models", "Input tokens",
                "USD at 0.042 per million", "Characters per token min / mean / max"), List.of(List.of(
                String.valueOf(calls.size()), MeasurementReport.micros(nanos), failures.toString(), models.toString(),
                String.valueOf(tokens), number(tokens * 0.042 / 1_000_000, 6), ratios.isEmpty() ? "-"
                        : number(ratios.stream().mapToDouble(Double::doubleValue).min().orElse(0), 2) + " / "
                        + number(ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0), 2) + " / "
                        + number(ratios.stream().mapToDouble(Double::doubleValue).max().orElse(0), 2))));
    }

    private static void reliability(MeasurementReport report, List<Call> calls) {
        report.section("Reliability of the confidence (labelled questions)");
        var labelled = labelled(calls);
        var rows = new ArrayList<List<String>>();
        for (var band = 0; band < BANDS.length - 1; band++) {
            var low = BANDS[band];
            var high = BANDS[band + 1];
            var inBand = labelled.stream().filter(answer -> answer.confidence() >= low && answer.confidence() < high)
                    .toList();
            rows.add(List.of(number(low, 1) + "-" + number(Math.min(1.0, high), 1), String.valueOf(inBand.size()),
                    share(inBand.stream().filter(Judged::acceptable).count(), inBand.size()),
                    String.valueOf(inBand.stream().filter(Judged::forbidden).count())));
        }
        report.table(List.of("Confidence", "Answers", "Acceptable", "Forbidden chosen"), rows);
        var sweep = new ArrayList<List<String>>();
        for (var threshold : THRESHOLDS) {
            var kept = labelled.stream().filter(answer -> answer.confidence() >= threshold).toList();
            sweep.add(List.of(number(threshold, 2), share(kept.size(), labelled.size()),
                    share(kept.stream().filter(Judged::acceptable).count(), kept.size())));
        }
        report.table(List.of("minimumConfidence", "Answers kept", "Acceptable among kept"), sweep);
    }

    private static void margins(MeasurementReport report, List<Call> calls) {
        report.section("Switches allowed by tacticSwitchMargin between repeated answers of the same question");
        var series = new TreeMap<String, List<Answer>>();
        for (var call : calls) {
            if (call.result() instanceof DecisionResult.Answered answered) {
                answered.answers().forEach((key, answer) -> series.computeIfAbsent(call.scenario().name() + "/"
                        + key.value(), name -> new ArrayList<>()).add(answer));
            }
        }
        var rows = new ArrayList<List<String>>();
        for (var margin : MARGINS) {
            var switches = 0;
            var steps = 0;
            for (var answers : series.values()) {
                var current = answers.getFirst().best();
                for (var answer : answers.subList(1, answers.size())) {
                    steps++;
                    var best = answer.best();
                    if (!best.equals(current)
                            && answer.probabilityOf(best) - answer.probabilityOf(current) >= margin) {
                        switches++;
                        current = best;
                    }
                }
            }
            rows.add(List.of(number(margin, 2), String.valueOf(steps), share(switches, steps)));
        }
        report.table(List.of("Margin", "Repeated answers", "Switches"), rows);
    }

    private static void contingencies(MeasurementReport report, List<Call> calls) {
        report.section("Contingencies that differ from the answer for now");
        var differs = new TreeMap<String, int[]>();
        for (var call : calls) {
            if (call.result() instanceof DecisionResult.Answered answered) {
                var now = nowAnswer(call, answered);
                for (var question : call.scenario().questions()) {
                    var answer = Optional.ofNullable(answered.answers().get(question.key()));
                    if (question.situation() != Situation.NOW && answer.isPresent() && now.isPresent()) {
                        var counts = differs.computeIfAbsent(question.situation().name(), name -> new int[2]);
                        counts[0]++;
                        counts[1] += answer.get().best().equals(now.get()) ? 0 : 1;
                    }
                }
            }
        }
        var rows = new ArrayList<List<String>>();
        differs.forEach((situation, counts) -> rows.add(List.of(situation, String.valueOf(counts[0]),
                share(counts[1], counts[0]))));
        report.table(List.of("Situation", "Answers", "Different from NOW"), rows);
    }

    private static Optional<TacticId> nowAnswer(Call call, DecisionResult.Answered answered) {
        for (var question : call.scenario().questions()) {
            if (question.situation() == Situation.NOW && answered.answers().containsKey(question.key())) {
                return Optional.of(answered.answers().get(question.key()).best());
            }
        }
        return Optional.empty();
    }

    /** One labelled answer. */
    private record Judged(double confidence, boolean acceptable, boolean forbidden) {
    }

    private static List<Judged> labelled(List<Call> calls) {
        var judged = new ArrayList<Judged>();
        for (var call : calls) {
            if (call.result() instanceof DecisionResult.Answered answered) {
                for (Map.Entry<QuestionKey, ScenarioDocument.Label> entry : call.scenario().labels().entrySet()) {
                    Optional.ofNullable(answered.answers().get(entry.getKey())).ifPresent(answer -> judged.add(
                            new Judged(answer.confidence(), entry.getValue().acceptable().contains(answer.best()),
                                    entry.getValue().forbidden().contains(answer.best()))));
                }
            }
        }
        return judged;
    }

    private static String share(long part, long whole) {
        return whole == 0 ? "-" : number(100.0 * part / whole, 1) + " %";
    }
}
