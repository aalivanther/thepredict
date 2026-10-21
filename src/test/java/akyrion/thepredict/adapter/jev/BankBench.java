package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.engine.PreparedRequest;
import akyrion.thepredict.engine.ServerClock;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The re-bench of M16.10 on the labelled bank: every selected scenario encoded again at the level of every arm (never a
 * recorded body), repeated, one call at a time on one client, with the estimate printed before the first call (rule W7)
 * and the raw answers kept in {@code answers.jsonl} without key or headers, so the run can be analysed again without
 * paying. The tests run it against the loopback contract server; the paid run needs {@code -PliveJev=true}, the key in
 * the environment and the approval of the user.
 */
final class BankBench {
    /** Price of {@code jev-1.13.0}: USD per million input tokens (output is free). */
    static final double USD_PER_MILLION = 0.042;
    /** Spending ceiling of M16 in USD. */
    static final double DEFAULT_MAX_USD = 0.10;
    /** Default cap of calls of one run of M16.10. */
    static final int DEFAULT_MAX_CALLS = 780;

    private BankBench() {
    }

    /**
     * One call to make: a scenario at an arm.
     *
     * @param scenario bank scenario
     * @param arm arm of the call
     * @param prepared body and questions the arm sends
     */
    record Planned(BankScenario scenario, BenchArm arm, PreparedRequest prepared) {
        /** Characters of the {@code state} of the body. */
        int stateChars() {
            return StrictJson.parseObject(prepared.body(), "body").getAsJsonObject("state").toString().length();
        }
    }

    /**
     * Everything a run will send.
     *
     * @param round the calls of one repetition, scenarios in order with their arms interleaved
     * @param repetitions repetitions of the round
     */
    record Plan(List<Planned> round, int repetitions) {
        Plan {
            round = List.copyOf(round);
        }

        /** Calls of the whole run. */
        long calls() {
            return (long) round.size() * repetitions;
        }

        /** Input tokens at 1.5 characters per token, an upper bound (the guard ratio, below every measure). */
        long tokenBound() {
            return round.stream().mapToLong(planned -> RequestSizeGuard.tokens(planned.prepared().bodyChars())).sum()
                    * repetitions;
        }

        /** Cost of the bound in USD. */
        double usd() {
            return tokenBound() * USD_PER_MILLION / 1_000_000;
        }

        /** The line printed before any call. */
        String estimate() {
            return "Estimate: " + calls() + " calls, at most " + tokenBound() + " input tokens, about "
                    + MeasurementReport.number(usd(), 4) + " USD.";
        }

        /** Calls, mean characters and token bound of each arm. */
        List<List<String>> byArm() {
            var rows = new ArrayList<List<String>>();
            var arms = new EnumMap<BenchArm, long[]>(BenchArm.class);
            for (var planned : round) {
                var sums = arms.computeIfAbsent(planned.arm(), arm -> new long[3]);
                sums[0]++;
                sums[1] += planned.prepared().bodyChars();
                sums[2] += RequestSizeGuard.tokens(planned.prepared().bodyChars());
            }
            arms.forEach((arm, sums) -> rows.add(List.of(arm.name(), String.valueOf(sums[0] * repetitions),
                    String.valueOf(sums[1] / sums[0]), String.valueOf(sums[2] / sums[0]),
                    String.valueOf(sums[2] * repetitions), MeasurementReport.number(sums[2] * repetitions
                            * USD_PER_MILLION / 1_000_000, 4))));
            return rows;
        }
    }

    /**
     * One call made.
     *
     * @param planned what was sent
     * @param repetition repetition index, from 0
     * @param nanos latency of the call
     * @param result answer or failure
     */
    record Call(Planned planned, int repetition, long nanos, DecisionResult result) {
    }

    /** Plans a run over scenario identifiers and arms. */
    static Plan plan(List<String> ids, Set<BenchArm> arms, int repetitions, String model) {
        var round = new ArrayList<Planned>();
        for (var id : ids) {
            var scenario = ScenarioBank.scenario(id);
            for (var arm : arms) {
                round.add(new Planned(scenario, arm, arm.prepare(scenario, model)));
            }
        }
        return new Plan(round, repetitions);
    }

    /** Why a plan may not run: over the call cap or the spending ceiling; empty when it may. */
    static Optional<String> refusal(Plan plan, int maxCalls, double maxUsd) {
        if (plan.calls() > maxCalls) {
            return Optional.of("Refused: " + plan.calls() + " calls exceed thepredict.live.maxCalls "
                    + maxCalls + ".");
        }
        if (plan.usd() > maxUsd) {
            return Optional.of("Refused: about " + MeasurementReport.number(plan.usd(), 4) + " USD exceed"
                    + " thepredict.live.maxUsd " + maxUsd + ".");
        }
        return Optional.empty();
    }

    /** Runs a plan, one call at a time on one client, writing each raw answer to {@code answers} as it arrives. */
    static List<Call> run(SystemOneSettings settings, Plan plan, Path answers) throws IOException {
        Files.createDirectories(answers.toAbsolutePath().getParent());
        var calls = new ArrayList<Call>();
        var client = new SystemOneClient(settings, ServerClock.of(() -> 0, System::nanoTime), line -> {
        });
        try (var writer = Files.newBufferedWriter(answers)) {
            for (var repetition = 0; repetition < plan.repetitions(); repetition++) {
                for (var planned : plan.round()) {
                    var started = System.nanoTime();
                    var result = await(client.decide(planned.prepared()));
                    var call = new Call(planned, repetition, System.nanoTime() - started, result);
                    calls.add(call);
                    writer.write(line(call));
                    writer.newLine();
                    writer.flush();
                }
            }
        } finally {
            client.close();
        }
        return calls;
    }

    /** One line of {@code answers.jsonl}: no key, no header and no body, only what analysis needs (rule W6). */
    static String line(Call call) {
        var planned = call.planned();
        return line(planned.scenario().id(), planned.arm().name(), call.repetition(), planned.scenario().situation(),
                planned.prepared(), planned.stateChars(), call.nanos(), call.result());
    }

    /**
     * One line of {@code answers.jsonl} for any call, shared by the bank and the replay of recorded bodies, so both
     * files have one format: no key, no header and no body (rule W6).
     *
     * @param scenario identifier of the scenario
     * @param arm arm of the call, or {@code REPLAY} for a recorded body
     * @param repetition repetition of the call
     * @param situation situation of the first question
     * @param prepared what was sent
     * @param stateChars characters of the {@code state} of the body
     * @param nanos time of the call
     * @param result result of the call
     * @return the line, without its line break
     */
    static String line(String scenario, String arm, int repetition, Situation situation, PreparedRequest prepared,
                       int stateChars, long nanos, DecisionResult result) {
        var line = new JsonObject();
        line.addProperty("scenario", scenario);
        line.addProperty("arm", arm);
        line.addProperty("repetition", repetition);
        line.addProperty("situation", situation.name());
        line.addProperty("bodyChars", prepared.bodyChars());
        line.addProperty("stateChars", stateChars);
        line.addProperty("latencyMicros", TimeUnit.NANOSECONDS.toMicros(nanos));
        switch (result) {
            case DecisionResult.Answered answered -> answered(line, answered, prepared);
            case DecisionResult.Failed failed -> line.addProperty("failure", failed.kind().name().toLowerCase(
                    Locale.ROOT));
        }
        return line.toString();
    }

    private static void answered(JsonObject line, DecisionResult.Answered answered, PreparedRequest prepared) {
        line.addProperty("model", answered.effectiveModel());
        line.addProperty("inputTokens", answered.inputTokens());
        var first = prepared.sent().questions().getFirst().key();
        var answer = answered.answers().getOrDefault(first, answered.answers().values().iterator().next());
        line.addProperty("best", answer.best().value());
        line.addProperty("confidence", answer.confidence());
        var probabilities = new JsonObject();
        answer.probabilities().forEach((tactic, value) -> probabilities.addProperty(tactic.value(), value));
        line.add("probabilities", probabilities);
        var fits = new JsonObject();
        prepared.sent().fitChecks().forEach(check -> {
            var fit = answered.fits().get(check.key());
            if (fit != null) {
                fits.addProperty(check.tactic().value(), fit);
            }
        });
        line.add("fits", fits);
    }

    private static DecisionResult await(CompletableFuture<DecisionResult> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("a decision did not complete", exception);
        }
    }
}
