package akyrion.thepredict.adapter.jev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live tools of M13 against the loopback contract server instead of the account: the same code path, no network
 * and no cost. It proves the tools work before the user pays for a real run.
 */
class LiveToolsLoopbackTest {
    private final SystemOneContractServer server = SystemOneContractServer.start();

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void theCheckToolListsTheModelsAtTheGivenSpacingWithoutTheKeyInItsReport() {
        var pauses = new ArrayList<Long>();
        var report = LiveJevCheck.run(JevFixtures.settings(server.baseUrl()), 3, 10_000, pauses::add).text();
        assertEquals(List.of(10_000L, 10_000L), pauses);
        assertEquals(3, server.received().stream().filter(request -> request.path().equals("/v1/models")).count());
        assertTrue(report.contains("READY=3"), report);
        assertFalse(report.contains(JevFixtures.KEY));
    }

    @Test
    void theBenchReplaysTheSeedBankBodiesAsRecordedAndReportsEverySection(@TempDir Path folder) throws IOException {
        server.onDecide((exchange, request, released) -> {
            var bytes = answerFirstOption(request.body()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        var documents = ScenarioDocument.readAll(Path.of(LiveScenarioBench.SEED_BANK));
        assertEquals(5, documents.size(), "seed-low-health was retired for the family lowhp of the bank (M16.9)");
        var answers = folder.resolve("replay").resolve("answers.jsonl");
        var report = LiveScenarioBench.run(JevFixtures.settings(server.baseUrl()), documents, 2,
                ScenarioFacts.example(), answers).text();
        var posts = server.received().stream().filter(request -> request.method().equals("POST")).toList();
        assertEquals(documents.size() * 2, posts.size());
        assertEquals(documents.getFirst().bodyFor("jev-1.13.0"), posts.getFirst().body());
        assertEquals(documents.getLast().bodyFor("jev-1.13.0"), posts.getLast().body());
        for (var section : List.of("Latency, failures and tokens", "Reliability of the confidence",
                "tacticSwitchMargin", "Contingencies")) {
            assertTrue(report.contains(section), section);
        }
        assertTrue(report.contains("{}"), "no failures: " + report);
        assertTrue(LiveScenarioBench.estimate(documents, 200).startsWith("Estimate: " + documents.size() * 200
                + " calls"));
        var lines = Files.readAllLines(answers);
        assertEquals(documents.size() * 2, lines.size(), "replay mode keeps every raw answer too");
        assertTrue(lines.stream().noneMatch(line -> line.contains(JevFixtures.KEY) || line.contains("Bearer")
                || line.contains("\"state\"") || line.contains("\"questions\"")), "no key, header or body");
        var first = StrictJson.parseObject(lines.getFirst(), "answer");
        for (var field : List.of("scenario", "arm", "repetition", "situation", "bodyChars", "stateChars",
                "latencyMicros", "model", "inputTokens", "best", "confidence", "probabilities", "fits")) {
            assertTrue(first.has(field), field);
        }
        assertEquals(documents.getFirst().name(), first.get("scenario").getAsString());
        assertEquals("REPLAY", first.get("arm").getAsString());
        assertEquals(documents.getFirst().bodyFor("jev-1.13.0").length(), first.get("bodyChars").getAsInt());
    }

    @Test
    void theBankBenchEncodesEveryArmAgainKeepsRawAnswersAndReportsEveryTable(@TempDir Path folder) throws IOException {
        server.onDecide(this::answer);
        var plan = BankBench.plan(ScenarioBank.set("core12"), BenchArm.parse("A,B,C,D,E"), 1, "jev-1.13.0");
        assertEquals(60, plan.calls());
        var answers = folder.resolve("answers.jsonl");
        var report = LiveScenarioBench.bank(JevFixtures.settings(server.baseUrl()), plan, answers).text();
        var posts = server.received().stream().filter(request -> request.method().equals("POST")).toList();
        assertEquals(60, posts.size());
        for (var index = 0; index < posts.size(); index++) {
            assertEquals(plan.round().get(index).prepared().body(), posts.get(index).body());
        }
        var lines = Files.readAllLines(answers);
        assertEquals(60, lines.size());
        assertTrue(lines.stream().noneMatch(line -> line.contains(JevFixtures.KEY) || line.contains("Bearer")));
        var fitted = lines.stream().filter(line -> line.contains("\"arm\":\"E\"") && line.contains("\"NOW\""))
                .findFirst().orElseThrow();
        assertTrue(fitted.contains("\"fits\":{\""), fitted);
        for (var section : List.of("Tokens, cost and failures per arm", "Per scenario", "Calibration by confidence band",
                "Kept at minimumConfidence 0.5", "Contingencies by situation", "Fit checks of arm E",
                "Rule of M16.7 on arm B, NOW", "chosen threshold", "Objectives of M16.10")) {
            assertTrue(report.contains(section), section);
        }
        assertTrue(BankStatistics.wholeBankReviewed(), "the user reviewed all 60 labels (2026-09-29 and 2026-09-30)");
        assertFalse(report.contains("PRELIMINARY"), "with the whole bank reviewed the rule of M16.7 is final");
        assertTrue(report.startsWith("# M16 re-bench"), report.substring(0, 40));
    }

    @Test
    void thePrintedEstimateCoversEveryArmAndTheCapsRefuseALargerRun() {
        var plan = BankBench.plan(ScenarioBank.set("all"), BenchArm.parse("A,B,C,D,E"), 2, "jev-1.13.0");
        assertEquals(600, plan.calls());
        assertTrue(plan.estimate().startsWith("Estimate: 600 calls, at most "), plan.estimate());
        assertEquals(5, plan.byArm().size());
        assertTrue(BankBench.refusal(plan, 780, 0.10).isEmpty(), plan.estimate());
        assertTrue(BankBench.refusal(plan, 599, 0.10).orElseThrow().contains("exceed thepredict.live.maxCalls 599"));
        assertTrue(BankBench.refusal(plan, 780, 0.001).orElseThrow().contains("thepredict.live.maxUsd"));
        var pilot = BankBench.plan(ScenarioBank.set("core12"), BenchArm.parse("B"), 10, "jev-1.13.0");
        assertEquals(120, pilot.calls());
        assertTrue(BankBench.refusal(pilot, 130, 0.10).isEmpty());
    }

    private void answer(HttpExchange exchange, SystemOneContractServer.Received request,
                        CountDownLatch released) throws IOException {
        var bytes = answerFirstOption(request.body()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String answerFirstOption(String body) {
        var request = JsonParser.parseString(body).getAsJsonObject();
        var answers = new JsonObject();
        for (var entry : request.getAsJsonObject("questions").entrySet()) {
            var question = entry.getValue().getAsJsonObject();
            if ("noul".equals(question.get("type").getAsString())) {
                var fit = new JsonObject();
                fit.addProperty("type", "noul");
                fit.addProperty("noul", 0.6);
                answers.add(entry.getKey(), fit);
                continue;
            }
            var criteria = question.getAsJsonObject("criteria").keySet().stream().toList();
            var answer = new JsonObject();
            answer.addProperty("type", "choice");
            answer.addProperty("choice", criteria.getFirst());
            answer.addProperty("confidence", 0.6);
            var probabilities = new JsonObject();
            for (var index = 0; index < criteria.size(); index++) {
                probabilities.addProperty(criteria.get(index), index == 0 ? 0.6 : 0.4 / (criteria.size() - 1));
            }
            answer.add("probabilities", probabilities);
            answers.add(entry.getKey(), answer);
        }
        var response = new JsonObject();
        response.addProperty("model", "jev-1.13.0");
        response.add("answers", answers);
        var usage = new JsonObject();
        usage.addProperty("input_tokens", 700);
        usage.addProperty("output_tokens", 1);
        response.add("usage", usage);
        return response.toString();
    }
}
