package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.adapter.jev.CalibrationRule.Repetition;
import akyrion.thepredict.domain.Answer;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

import static akyrion.thepredict.MeasurementReport.number;

/**
 * The tables of the re-bench of M16.10, where each distinct scenario weighs one: tokens per arm, one row per scenario
 * (modal tactic, mean confidence, changes between repetitions: the check of D3 and D4), the curve by confidence band
 * with the margin between the two best and the normalized entropy (G2), accuracy of best, acceptable and forbidden
 * split by {@code REVIEWED} and {@code PROPOSED}, contingencies by situation, the fits of arm {@code E} against the
 * labels, the rule of M16.7 and the objectives of M16.10. Pure; the tests feed it the loopback contract server.
 */
final class BankStatistics {
    private static final double[] BANDS = {0.0, 0.3, 0.5, 0.7, 1.0001};
    private static final double KEEP_AT = 0.5;
    private static final int PRODUCTION_OPTIONS_NOTE = 7;

    private BankStatistics() {
    }

    /** One scenario of one arm, reduced over its repetitions. */
    record Distinct(BankScenario scenario, BenchArm arm, ScenarioDocument.Label label,
                    CalibrationRule.Aggregate aggregate, double margin, double entropy, List<Map<TacticId, Double>> fits) {
    }

    static void write(MeasurementReport report, List<BankBench.Call> calls) {
        tokens(report, calls);
        var distinct = distinct(calls);
        scenarios(report, distinct);
        curve(report, distinct);
        accuracy(report, distinct);
        contingencies(report, distinct);
        fits(report, distinct);
        rule(report, distinct, BenchArm.B);
        objectives(report, distinct, calls);
    }

    private static void tokens(MeasurementReport report, List<BankBench.Call> calls) {
        report.section("Tokens, cost and failures per arm");
        var rows = new ArrayList<List<String>>();
        for (var arm : BenchArm.values()) {
            var own = calls.stream().filter(call -> call.planned().arm() == arm).toList();
            if (own.isEmpty()) {
                continue;
            }
            var tokens = 0L;
            var chars = 0L;
            var failures = 0;
            for (var call : own) {
                if (call.result() instanceof DecisionResult.Answered answered) {
                    tokens += answered.inputTokens();
                    chars += call.planned().prepared().bodyChars();
                } else {
                    failures++;
                }
            }
            var answered = own.size() - failures;
            rows.add(List.of(arm.name(), String.valueOf(own.size()), String.valueOf(failures),
                    answered == 0 ? "-" : String.valueOf(chars / answered), answered == 0 ? "-"
                            : String.valueOf(tokens / answered), tokens == 0 ? "-" : number((double) chars / tokens, 2),
                    String.valueOf(tokens), number(tokens * BankBench.USD_PER_MILLION / 1_000_000, 6)));
        }
        report.table(List.of("Arm", "Calls", "Failures", "Body chars per call", "Input tokens per call",
                "Characters per token", "Input tokens", "USD"), rows);
        report.line("");
        report.line("Production request (1 mob, 1 participant, NOW plus one contingency, " + PRODUCTION_OPTIONS_NOTE
                + " options of the example) per arm: the mean tokens of a NOW call plus those of a contingency call,"
                + " minus one state, which a production request carries once.");
        var production = new ArrayList<List<String>>();
        for (var arm : BenchArm.values()) {
            productionTokens(calls, arm).ifPresent(value -> production.add(List.of(arm.name(), String.valueOf(value))));
        }
        report.table(List.of("Arm", "Estimated real tokens of the production request"), production);
    }

    private static OptionalLong productionTokens(List<BankBench.Call> calls, BenchArm arm) {
        var now = mean(calls, arm, true);
        var contingency = mean(calls, arm, false);
        if (now[0] == 0 || contingency[0] == 0) {
            return OptionalLong.empty();
        }
        var charsPerToken = (now[1] + contingency[1]) / (now[2] + contingency[2]);
        return OptionalLong.of(Math.round(now[2] / now[0] + contingency[2] / contingency[0]
                - contingency[3] / contingency[0] / charsPerToken));
    }

    private static double[] mean(List<BankBench.Call> calls, BenchArm arm, boolean now) {
        var sums = new double[4];
        for (var call : calls) {
            if (call.planned().arm() == arm && (call.planned().scenario().situation() == Situation.NOW) == now
                    && call.result() instanceof DecisionResult.Answered answered) {
                sums[0]++;
                sums[1] += call.planned().prepared().bodyChars();
                sums[2] += answered.inputTokens();
                sums[3] += call.planned().stateChars();
            }
        }
        return sums;
    }

    private static List<Distinct> distinct(List<BankBench.Call> calls) {
        var grouped = new LinkedHashMap<String, List<BankBench.Call>>();
        for (var call : calls) {
            if (call.result() instanceof DecisionResult.Answered) {
                grouped.computeIfAbsent(call.planned().scenario().id() + "/" + call.planned().arm(),
                        key -> new ArrayList<>()).add(call);
            }
        }
        var distinct = new ArrayList<Distinct>();
        for (var group : grouped.values()) {
            var planned = group.getFirst().planned();
            var label = label(planned.scenario());
            var repetitions = new ArrayList<Repetition>();
            var margin = 0.0;
            var entropy = 0.0;
            var fits = new ArrayList<Map<TacticId, Double>>();
            for (var call : group) {
                var answered = (DecisionResult.Answered) call.result();
                var answer = answered.answers().values().iterator().next();
                repetitions.add(new Repetition(answer.best(), answer.confidence(), answer.probabilities()));
                margin += margin(answer);
                entropy += entropy(answer);
                var byTactic = new LinkedHashMap<TacticId, Double>();
                planned.prepared().sent().fitChecks().forEach(check -> {
                    var fit = answered.fits().get(check.key());
                    if (fit != null) {
                        byTactic.put(check.tactic(), fit);
                    }
                });
                fits.add(byTactic);
            }
            var aggregate = CalibrationRule.aggregate(new CalibrationRule.Scenario(planned.scenario().id(),
                    label.reviewed(), label.uninformative(), label.acceptable(), label.forbidden(), repetitions));
            distinct.add(new Distinct(planned.scenario(), planned.arm(), label, aggregate, margin / group.size(),
                    entropy / group.size(), fits));
        }
        return distinct;
    }

    private static ScenarioDocument.Label label(BankScenario scenario) {
        var options = ScenarioBank.options(scenario);
        return ScenarioDocument.label(ScenarioBank.stored(scenario).orElseGet(() -> ScenarioBank.proposed(scenario,
                options)), options);
    }

    private static void scenarios(MeasurementReport report, List<Distinct> distinct) {
        report.section("Per scenario (each distinct scenario weighs one)");
        var rows = new ArrayList<List<String>>();
        for (var one : distinct) {
            rows.add(List.of(one.scenario().id(), one.arm().name(), one.label().status(),
                    one.aggregate().modal().value(), number(one.aggregate().meanConfidence(), 3),
                    String.valueOf(one.aggregate().changes()), verdict(one)));
        }
        report.table(List.of("Scenario", "Arm", "Label", "Modal best", "Mean confidence", "Changes between repetitions",
                "Judged"), rows);
    }

    private static String verdict(Distinct one) {
        if (one.label().uninformative()) {
            return "uninformative";
        }
        if (one.aggregate().forbidden()) {
            return "FORBIDDEN";
        }
        if (one.label().best().contains(one.aggregate().modal())) {
            return "best";
        }
        return one.aggregate().acceptable() ? "acceptable" : "not acceptable";
    }

    private static void curve(MeasurementReport report, List<Distinct> distinct) {
        report.section("Calibration by confidence band, per arm (informative scenarios, NOW)");
        var rows = new ArrayList<List<String>>();
        for (var arm : BenchArm.values()) {
            for (var band = 0; band < BANDS.length - 1; band++) {
                var low = BANDS[band];
                var high = BANDS[band + 1];
                var inBand = informative(distinct, arm, true).stream().filter(one -> one.aggregate().meanConfidence()
                        >= low && one.aggregate().meanConfidence() < high).toList();
                if (inBand.isEmpty()) {
                    continue;
                }
                rows.add(List.of(arm.name(), number(low, 1) + "-" + number(Math.min(1.0, high), 1),
                        String.valueOf(inBand.size()), share(inBand.stream().filter(one -> one.aggregate().acceptable())
                                .count(), inBand.size()),
                        String.valueOf(inBand.stream().filter(one -> one.aggregate().forbidden()).count()),
                        number(inBand.stream().mapToDouble(Distinct::margin).average().orElse(0), 3),
                        number(inBand.stream().mapToDouble(Distinct::entropy).average().orElse(0), 3)));
            }
        }
        report.table(List.of("Arm", "Confidence", "Scenarios", "Acceptable", "Forbidden", "Margin top two",
                "Normalized entropy"), rows);
    }

    private static void accuracy(MeasurementReport report, List<Distinct> distinct) {
        report.section("Kept at minimumConfidence 0.5 and their accuracy, REVIEWED apart from PROPOSED (NOW)");
        var rows = new ArrayList<List<String>>();
        for (var arm : BenchArm.values()) {
            for (var reviewed : new boolean[] {true, false}) {
                var all = informative(distinct, arm, true).stream().filter(one -> one.label().reviewed() == reviewed)
                        .toList();
                if (all.isEmpty()) {
                    continue;
                }
                var kept = all.stream().filter(one -> one.aggregate().meanConfidence() >= KEEP_AT).toList();
                var acceptable = kept.stream().filter(one -> one.aggregate().acceptable()).count();
                rows.add(List.of(arm.name(), reviewed ? "REVIEWED" : "PROPOSED", String.valueOf(all.size()),
                        share(kept.size(), all.size()), share(acceptable, kept.size()),
                        number(CalibrationRule.wilsonLower((int) acceptable, kept.size()), 3),
                        share(kept.stream().filter(one -> one.label().best().contains(one.aggregate().modal())).count(),
                                kept.size()), String.valueOf(kept.stream().filter(one -> one.aggregate().forbidden())
                                .count())));
            }
        }
        report.table(List.of("Arm", "Labels", "Scenarios", "Kept", "Acceptable among kept", "Wilson lower",
                "Best among kept", "Forbidden kept"), rows);
    }

    private static void contingencies(MeasurementReport report, List<Distinct> distinct) {
        report.section("Contingencies by situation (informative scenarios, kept at 0.5)");
        var rows = new ArrayList<List<String>>();
        for (var arm : BenchArm.values()) {
            var bySituation = new EnumMap<Situation, List<Distinct>>(Situation.class);
            informative(distinct, arm, false).forEach(one -> bySituation.computeIfAbsent(one.scenario().situation(),
                    situation -> new ArrayList<>()).add(one));
            bySituation.forEach((situation, list) -> {
                var kept = list.stream().filter(one -> one.aggregate().meanConfidence() >= KEEP_AT).toList();
                rows.add(List.of(arm.name(), situation.name(), String.valueOf(list.size()), share(kept.size(),
                        list.size()), share(kept.stream().filter(one -> one.aggregate().acceptable()).count(),
                        kept.size())));
            });
        }
        report.table(List.of("Arm", "Situation", "Scenarios", "Kept", "Acceptable among kept"), rows);
    }

    private static void fits(MeasurementReport report, List<Distinct> distinct) {
        report.section("Fit checks of arm E against the labels");
        var classes = new TreeMap<String, double[]>();
        for (var one : distinct) {
            for (var fits : one.fits()) {
                fits.forEach((tactic, fit) -> {
                    var kind = one.label().forbidden().contains(tactic) ? "forbidden"
                            : one.label().acceptable().contains(tactic) ? "acceptable" : "neither";
                    var sums = classes.computeIfAbsent(kind, key -> new double[4]);
                    sums[0]++;
                    sums[1] += fit;
                    sums[2] += fit < 0.2 ? 1 : 0;
                    sums[3] += fit >= 0.7 ? 1 : 0;
                });
            }
        }
        var rows = new ArrayList<List<String>>();
        classes.forEach((kind, sums) -> rows.add(List.of(kind, String.valueOf((long) sums[0]),
                number(sums[1] / sums[0], 3), share((long) sums[2], (long) sums[0]), share((long) sums[3],
                        (long) sums[0]))));
        report.table(List.of("Label of the option", "Fits", "Mean fit", "Below veto 0.2", "At or above rescue 0.7"),
                rows);
    }

    /**
     * Whether every label of the whole bank, not only of the scenarios of this run, is {@code REVIEWED} or
     * uninformative: until then the rule of M16.7 is preliminary and no default changes.
     */
    static boolean wholeBankReviewed() {
        return ScenarioBank.scenarios().stream().map(BankStatistics::label)
                .allMatch(label -> label.reviewed() || label.uninformative());
    }

    /**
     * The rule of M16.7 on other arms, for the report recomputed from recorded answers (M16.10).
     *
     * @param report report to extend
     * @param calls recorded calls
     * @param arms arms to judge, in order
     */
    static void rules(MeasurementReport report, List<BankBench.Call> calls, List<BenchArm> arms) {
        var distinct = distinct(calls);
        arms.forEach(arm -> rule(report, distinct, arm));
    }

    private static void rule(MeasurementReport report, List<Distinct> distinct, BenchArm arm) {
        var reviewedAll = wholeBankReviewed();
        for (var now : new boolean[] {true, false}) {
            report.section("Rule of M16.7 on arm " + arm + ", " + (now ? "NOW" : "contingencies"));
            var scenarios = new ArrayList<CalibrationRule.Scenario>();
            for (var one : distinct) {
                if (one.arm() == arm && (one.scenario().situation() == Situation.NOW) == now) {
                    scenarios.add(new CalibrationRule.Scenario(one.scenario().id(), one.label().reviewed(),
                            one.label().uninformative(), one.label().acceptable(), one.label().forbidden(),
                            List.of(new Repetition(one.aggregate().modal(), one.aggregate().meanConfidence(),
                                    Map.of()))));
                }
            }
            var result = CalibrationRule.apply(scenarios);
            var rows = new ArrayList<List<String>>();
            for (var row : result.rows()) {
                rows.add(List.of(number(row.threshold(), 2), String.valueOf(row.kept()), share(row.kept(),
                        result.eligible()), share(row.acceptable(), row.kept()), number(row.wilsonLower(), 3),
                        String.valueOf(row.forbidden()), row.meets() ? "yes" : "no"));
            }
            report.table(List.of("Threshold", "n(t)", "Kept", "Acceptable", "Wilson lower", "Forbidden", "Meets"), rows);
            report.line("");
            report.line((reviewedAll && !scenarios.isEmpty() ? "" : "PRELIMINARY (not every label of the bank is REVIEWED; no"
                    + " default changes). ") + "Eligible scenarios " + result.eligible() + "; chosen threshold "
                    + (result.chosen().isPresent() ? number(result.chosen().getAsDouble(), 2) : "none (0.5 stays)")
                    + ".");
        }
    }

    private static void objectives(MeasurementReport report, List<Distinct> distinct, List<BankBench.Call> calls) {
        report.section("Objectives of M16.10 (arm B, NOW, REVIEWED)");
        var reviewed = informative(distinct, BenchArm.B, true).stream().filter(one -> one.label().reviewed()).toList();
        var kept = reviewed.stream().filter(one -> one.aggregate().meanConfidence() >= KEEP_AT).toList();
        var acceptable = kept.stream().filter(one -> one.aggregate().acceptable()).count();
        var control = informative(distinct, BenchArm.A, true).stream().filter(one -> one.label().reviewed()).toList();
        var keptControl = control.stream().filter(one -> one.aggregate().meanConfidence() >= KEEP_AT).count();
        report.table(List.of("Measure", "Objective", "Result"), List.of(
                List.of("Kept at 0.5", ">= 70 %", share(kept.size(), reviewed.size())),
                List.of("Acceptable among kept", ">= 90 % and Wilson lower >= 0.75 with n >= 30", share(acceptable,
                        kept.size()) + ", Wilson " + number(CalibrationRule.wilsonLower((int) acceptable, kept.size()), 3)
                        + ", n " + kept.size()),
                List.of("Forbidden among kept", "<= 1 (2 % from n >= 50)", String.valueOf(kept.stream()
                        .filter(one -> one.aggregate().forbidden()).count())),
                List.of("Best among kept", ">= 60 %", share(kept.stream().filter(one -> one.label().best()
                        .contains(one.aggregate().modal())).count(), kept.size())),
                List.of("Improvement over A in kept at 0.5", "+30 points", control.isEmpty() ? "-"
                        : number(100.0 * kept.size() / Math.max(1, reviewed.size()) - 100.0 * keptControl
                        / control.size(), 1) + " points"),
                List.of("Real tokens of the production request", "<= 1 200", productionTokens(calls, BenchArm.B)
                        .stream().mapToObj(String::valueOf).findFirst().orElse("-"))));
    }

    private static List<Distinct> informative(List<Distinct> distinct, BenchArm arm, boolean now) {
        return distinct.stream().filter(one -> one.arm() == arm && !one.label().uninformative()
                && (one.scenario().situation() == Situation.NOW) == now).toList();
    }

    private static double margin(Answer answer) {
        var ranked = answer.ranked();
        return ranked.size() < 2 ? 1.0 : answer.probabilityOf(ranked.get(0)) - answer.probabilityOf(ranked.get(1));
    }

    private static double entropy(Answer answer) {
        var values = answer.probabilities().values();
        if (values.size() < 2) {
            return 0.0;
        }
        var entropy = 0.0;
        for (var probability : values) {
            if (probability > 0) {
                entropy -= probability * Math.log(probability);
            }
        }
        return entropy / Math.log(values.size());
    }

    private static String share(long part, long whole) {
        return whole == 0 ? "-" : number(100.0 * part / whole, 1) + " %";
    }
}
