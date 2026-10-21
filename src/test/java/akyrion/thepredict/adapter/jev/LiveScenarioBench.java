package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.MeasurementReport;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.engine.PreparedRequest;
import akyrion.thepredict.engine.ServerClock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Live scenario bench of M13.6 and M16.10. With the labelled bank ({@code thepredict.live.scenarioDir} is
 * {@code src/test/resources/scenarios/bank}, the default) it encodes each scenario of {@code thepredict.live.scenarios}
 * ({@code core12}, {@code core24}, {@code all} or identifiers; {@code core12} by default) at each arm of
 * {@code thepredict.live.arms} ({@code A,B,C,D,E}; {@code B} by default), {@code thepredict.live.repetitions} times (1
 * by default), prints the calls, the tokens at 1.5 characters per token and the cost before the first call (rule W7),
 * refuses above {@code thepredict.live.maxCalls} (780 by default) or {@code thepredict.live.maxUsd} (0.10 by default),
 * writes every raw answer to {@code answers.jsonl} next to the report, and reports with {@link BankStatistics}. With any
 * other folder it replays the recorded bodies of that folder as M13 did ({@link BenchStatistics}) and writes its raw
 * answers to the same {@code answers.jsonl}, in the same format, with arm {@code REPLAY}. Each call is a paid
 * decision on the real account with the pinned model, one at a time on one client. Run only with
 * {@code ./gradlew liveScenarioBench -PliveJev=true} after the user approves the estimate; the build never runs it.
 */
public final class LiveScenarioBench {
    /** Folder of the labelled bank of M16.9, the default. */
    static final String BANK = "src/test/resources/scenarios/bank";
    /** Folder of the seed bank of M13.6, replayed as recorded. */
    static final String SEED_BANK = "src/test/resources/scenarios/seed";
    /** Default cap of calls in one replay of recorded bodies. */
    static final int MAX_CALLS = 1300;

    private LiveScenarioBench() {
    }

    /**
     * Runs the bench and writes the report to the first argument.
     *
     * @param arguments the report path
     * @throws IOException if a scenario cannot be read or the report cannot be written
     */
    public static void main(String[] arguments) throws IOException {
        var refusal = LiveJevGate.refusal(System::getProperty, System::getenv);
        if (refusal.isPresent()) {
            System.err.println(refusal.get());
            System.exit(2);
            return;
        }
        var output = Path.of(arguments.length > 0 ? arguments[0] : "build/reports/m16/live-scenario-bench.md");
        var folder = Path.of(System.getProperty("thepredict.live.scenarioDir", BANK));
        var repetitions = LiveJevGate.positive(System::getProperty, "thepredict.live.repetitions", 1);
        var report = folder.normalize().equals(Path.of(BANK).normalize())
                ? bank(output, repetitions) : replay(folder, repetitions, output.resolveSibling("answers.jsonl"));
        if (report == null) {
            System.exit(2);
            return;
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, report.text());
        System.out.println(report.text());
    }

    /** The paid run of M16.10 on the bank; {@code null} when the plan is refused. */
    private static MeasurementReport bank(Path output, int repetitions) throws IOException {
        var settings = LiveJevGate.settings(System::getProperty, System::getenv);
        var plan = BankBench.plan(ScenarioBank.set(System.getProperty("thepredict.live.scenarios", "core12")),
                BenchArm.parse(System.getProperty("thepredict.live.arms", "B")), repetitions, settings.model());
        System.out.println(plan.estimate());
        var maxCalls = LiveJevGate.positive(System::getProperty, "thepredict.live.maxCalls", BankBench.DEFAULT_MAX_CALLS);
        var maxUsd = Double.parseDouble(System.getProperty("thepredict.live.maxUsd",
                String.valueOf(BankBench.DEFAULT_MAX_USD)));
        var refused = BankBench.refusal(plan, maxCalls, maxUsd);
        if (refused.isPresent()) {
            System.err.println(refused.get());
            return null;
        }
        return bank(settings, plan, output.resolveSibling("answers.jsonl"));
    }

    /**
     * Runs a plan of the bank and reports it; the tests call it against the loopback contract server.
     *
     * @return the report
     */
    static MeasurementReport bank(SystemOneSettings settings, BankBench.Plan plan, Path answers) throws IOException {
        var calls = BankBench.run(settings, plan, answers);
        var report = new MeasurementReport();
        report.line("# M16 re-bench on the labelled bank");
        report.line("");
        report.line("Model " + settings.model() + ", " + plan.round().size() / Math.max(1, plan.round().stream()
                .map(planned -> planned.arm()).distinct().count()) + " scenarios, arms " + plan.round().stream()
                .map(planned -> planned.arm().name()).distinct().toList() + ", " + plan.repetitions()
                + " repetitions, one call at a time on one client. " + plan.estimate());
        report.table(List.of("Arm", "Calls", "Body chars per call", "Tokens per call (1.5)", "Tokens (1.5)", "USD"),
                plan.byArm());
        BankStatistics.write(report, calls);
        return report;
    }

    /** The replay of recorded bodies of M13; {@code null} when refused. */
    private static MeasurementReport replay(Path folder, int repetitions, Path answers) throws IOException {
        var documents = ScenarioDocument.readAll(folder);
        var maxCalls = LiveJevGate.positive(System::getProperty, "thepredict.live.maxCalls", MAX_CALLS);
        System.out.println(estimate(documents, repetitions));
        if ((long) documents.size() * repetitions > maxCalls) {
            System.err.println("Refused: " + documents.size() * repetitions + " calls exceed thepredict.live.maxCalls "
                    + maxCalls + ".");
            return null;
        }
        return run(LiveJevGate.settings(System::getProperty, System::getenv), documents, repetitions,
                ScenarioFacts.example(), answers);
    }

    /**
     * Estimates the calls, tokens and cost of a run before it starts, at 1.5 characters per token (the ratio of
     * {@link RequestSizeGuard}, below the 1.59 to 1.82 measured with the real account) and 0.042 USD per million input
     * tokens, so the estimate is an upper bound.
     */
    static String estimate(List<ScenarioDocument> documents, int repetitions) {
        var tokens = 0L;
        for (var document : documents) {
            tokens += RequestSizeGuard.tokens(document.requestChars()) * repetitions;
        }
        return "Estimate: " + documents.size() * repetitions + " calls, at most " + tokens + " input tokens, about "
                + MeasurementReport.number(tokens * 0.042 / 1_000_000, 4) + " USD.";
    }

    /**
     * Replays the scenarios, writing each raw answer to {@code answers} as it arrives in the format of the bank
     * ({@link BankBench#line(String, String, int, Situation, PreparedRequest, int, long, DecisionResult)}, arm
     * {@code REPLAY}); the tests call it against the loopback contract server.
     *
     * @return the report
     */
    static MeasurementReport run(SystemOneSettings settings, List<ScenarioDocument> documents, int repetitions,
                                 TacticalProfile profile, Path answers) throws IOException {
        var requests = new ArrayList<PreparedRequest>();
        for (var document : documents) {
            var body = document.bodyFor(settings.model());
            requests.add(new PreparedRequest(document.request(profile), body, Set.of(), Set.of(), body.length()));
        }
        var calls = replayed(settings, documents, repetitions, requests, answers);
        var report = new MeasurementReport();
        report.line("# M13 live scenario bench");
        report.line("");
        report.line("Model " + settings.model() + ", " + documents.size() + " scenarios, " + repetitions
                + " repetitions, one call at a time on one client, so only the very first call includes the connection"
                + " and TLS handshake.");
        BenchStatistics.write(report, calls);
        return report;
    }

    private static List<BenchStatistics.Call> replayed(SystemOneSettings settings, List<ScenarioDocument> documents,
                                                       int repetitions, List<PreparedRequest> requests, Path answers)
            throws IOException {
        Files.createDirectories(answers.toAbsolutePath().getParent());
        var calls = new ArrayList<BenchStatistics.Call>();
        var client = new SystemOneClient(settings, ServerClock.of(() -> 0, System::nanoTime), line -> {
        });
        try (var writer = Files.newBufferedWriter(answers)) {
            for (var index = 0; index < documents.size(); index++) {
                var document = documents.get(index);
                var prepared = requests.get(index);
                var stateChars = StrictJson.parseObject(prepared.body(), document.name()).getAsJsonObject("state")
                        .toString().length();
                for (var repetition = 0; repetition < repetitions; repetition++) {
                    var started = System.nanoTime();
                    var result = await(client.decide(prepared));
                    var nanos = System.nanoTime() - started;
                    calls.add(new BenchStatistics.Call(document, repetition, nanos, result));
                    writer.write(BankBench.line(document.name(), "REPLAY", repetition, document.questions().getFirst()
                            .situation(), prepared, stateChars, nanos, result));
                    writer.newLine();
                    writer.flush();
                }
            }
        } finally {
            client.close();
        }
        return calls;
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
